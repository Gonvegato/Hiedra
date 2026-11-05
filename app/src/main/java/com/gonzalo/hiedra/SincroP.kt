package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicBoolean

/**
 * La sincronización de Hiedra con tu cuenta de Atrio, como la de Marfil y Cobalto:
 *  - lo que cambias aquí (un juego, tus horas, la portada…) se sube en cuanto pasan [ESPERA] ms (por si llegan varios cambios seguidos);
 *  - mientras Hiedra se ve, se escucha tu cuenta en directo: lo que sube otro de tus Hiedras se baja y se mezcla al momento;
 *  - una sola subida o mezcla a la vez; sin conexión se reintenta solo.
 * Lo que se mezcla y cómo, en [Mezcla]. Lo que Azabache lee, en [subirTiempo] (docs/CONTRATO-DATOS.md).
 */
object SincroP {
    const val ESPERA = 600L
    private val candado = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var app: Context? = null
    private var programada: Job? = null
    private var escucha: FbStream? = null
    private var escuchaAzabache: FbStream? = null
    private val bajando = AtomicBoolean(false)
    @Volatile private var fallos = 0

    /** Qué está pasando (para Ajustes): "" sin vincular, «Sincronizado», o el último problema. */
    var estado by mutableStateOf("")
        private set
    /** Cuándo se sincronizó por última vez (milisegundos; 0 = todavía no). */
    var ultima by mutableStateOf(0L)
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("hiedra_sync", Context.MODE_PRIVATE)

    fun iniciar(c: Context) {
        app = c.applicationContext
        ultima = prefs(c).getLong("ultima", 0L)
    }

    private fun vistos(c: Context): JSONObject = runCatching { JSONObject(prefs(c).getString("vistos", "{}")!!) }.getOrDefault(JSONObject())
    private fun guardaVistos(c: Context, o: JSONObject) = prefs(c).edit().putString("vistos", o.toString()).apply()
    fun olvidarVistos() { app?.let { p -> prefs(p).edit().apply { remove("vistos"); remove("hash"); prefs(p).all.keys.filter { it.startsWith("tp:") }.forEach { remove(it) } }.apply() } }

    /** Algo ha cambiado aquí (Biblioteca.guardar lo avisa): se sube dentro de [ms]. */
    fun cambio(c: Context, ms: Long = ESPERA) {
        val a = app ?: run { iniciar(c); app }
        if (a == null || !Cuenta.vinculada) return
        synchronized(this) {
            programada?.cancel()
            programada = scope.launch {
                delay(ms)
                withContext(NonCancellable) { subir(a) }
            }
        }
    }

    /** Sube la biblioteca de este dispositivo si ha cambiado desde la última vez. Devuelve false si no ha podido (se reintenta solo). */
    suspend fun subir(c: Context, forzar: Boolean = false): Boolean = candado.withLock {
        if (!Cuenta.vinculada) return@withLock true
        try {
            val blob = Mezcla.aJson(Mezcla.aBlob(Cuenta.dispositivo, Biblioteca.juegos.toList(), HashMap(Biblioteca.borrados), if (Skin.ajustesAt > 0L) Skin.ajustesJson().toString() else "", Sagas.lista.toList(), HashMap(Sagas.borradas), if (Inicio.config.mod > 0L) Inicio.config.aJson().toString() else ""))
            val hash = blob.hashCode().toString() + ":" + blob.length
            val p = prefs(c)
            // (las portadas se miran siempre: elegir una no cambia la biblioteca que se sube)
            if (!forzar && p.getString("hash", "") == hash) { runCatching { portadas(c) }; return@withLock true }
            val nodo = JSONObject().put("name", Cuenta.nombreDispositivo).put("device", "hiedra").put("updated", JSONObject().put(".sv", "timestamp")).put("data", blob)
            Cuenta.db("PUT", "${Cuenta.raiz}/dev/${Cuenta.dispositivo}", nodo.toString(), "print=silent")
            val ts = Cuenta.db("GET", "${Cuenta.raiz}/dev/${Cuenta.dispositivo}/updated", null)?.toLongOrNull() ?: throw Cuenta.Fail("Firebase no ha guardado la copia.")
            // El nodo ligero «tiempo» va ANTES del sello: Azabache lo lee en cuanto oye el sello (ver docs/CONTRATO-DATOS.md)
            runCatching { subirTiempo(c) }
            Cuenta.db("PUT", "${Cuenta.raiz}/stamps/${Cuenta.dispositivo}", ts.toString(), "print=silent")
            p.edit().putString("hash", hash).apply()
            runCatching { portadas(c) }
            guardaVistos(c, vistos(c).put(Cuenta.dispositivo, ts))
            bien(c)
            true
        } catch (e: Exception) { mal(c, e); false }
    }

    /** El nodo «tiempo» de un juego para una fuente (docs/CONTRATO-DATOS.md). */
    fun nodoTiempo(j: Juego, h: Horas): JSONObject = JSONObject().put("t", j.titulo).put("k", JSONArray().also { a -> j.claves.forEach { a.put(it) } })
        .put("m", j.menu.name.lowercase()).put("total", h.ms).put("fin", j.finalizado)
        .put("days", JSONObject().also { d -> h.dias.entries.sortedByDescending { it.key }.take(400).forEach { (k, v) -> d.put(k, v) } })
        .apply { if (j.plataforma.isNotBlank()) put("p", j.plataforma) }

    /**
     * El nodo ligero para Azabache (el Diario y las barras): `tiempo/<id>/<fuente>` = {t, k, m, p, total, days, fin}. Este dispositivo escribe su
     * fuente (sus propias horas) y las externas que conoce (Steam, cada PC: un solo nodo por fuente, así nada se repite). Solo lo que ha cambiado
     * desde la última vez; lo de un juego que ya no está se borra.
     */
    fun subirTiempo(c: Context) {
        if (!Cuenta.vinculada) return
        val p = prefs(c)
        val dev = Cuenta.dispositivo
        val cambios = JSONObject()
        val vivos = HashSet<String>()
        for (j in Biblioteca.juegos.toList()) {
            val fuentes = (if (!j.propias.vacias || j.finalizado > 0L) mapOf(dev to j.propias) else emptyMap()) + j.externas.filterValues { !it.vacias }
            for ((f, h) in fuentes) {
                val clave = "${j.id}/$f"; vivos += clave
                val nodo = nodoTiempo(j, h)
                val hash = nodo.toString().hashCode().toString()
                if (p.getString("tp:$clave", "") == hash) continue
                cambios.put(clave, nodo)
                p.edit().putString("tp:$clave", hash).apply()
            }
        }
        for (k in p.all.keys.filter { it.startsWith("tp:") }) {
            val clave = k.removePrefix("tp:")
            if (clave in vivos) continue
            cambios.put(clave, JSONObject.NULL); p.edit().remove(k).apply()
        }
        if (cambios.length() > 0) Cuenta.db("PATCH", "${Cuenta.raiz}/tiempo", cambios.toString(), "print=silent")
    }

    /** Las portadas que elegiste y las ROMs: van a tu kDrive (si está conectado). Errores aparte: no estropean la sincronización. */
    private fun portadas(c: Context) { if (KDrive.on(c)) { runCatching { NubeP.portadas(c) }; runCatching { SubirRomsWorker.programar(c) } } }

    /** Baja y mezcla lo nuevo de tus otros dispositivos. Devuelve true si ha cambiado algo aquí. */
    suspend fun bajar(c: Context): Boolean = candado.withLock {
        if (!Cuenta.vinculada) return@withLock false
        try {
            val t = Cuenta.db("GET", "${Cuenta.raiz}/stamps", null)
            val sellos = t?.let { JSONObject(it) }
            val vistas = vistos(c)
            val ahora = System.currentTimeMillis()
            val nuevos = ArrayList<Blob>()
            val vistosNuevos = JSONObject(vistas.toString())
            if (sellos != null) for (dev in sellos.keys()) {
                val ts = sellos.optLong(dev, 0L)
                if (dev == Cuenta.dispositivo || ts <= vistas.optLong(dev, 0L) || ahora - ts > Cuenta.STALE) continue
                val raw = Cuenta.db("GET", "${Cuenta.raiz}/dev/$dev/data", null) ?: continue
                val datos = JSONTokener(raw).nextValue() as? String ?: continue
                Mezcla.deJson(datos)?.let { nuevos += Blob(dev, it.juegos, it.borrados, it.ajustes, it.sagas, it.sagasBorradas, it.inicio) }
                vistosNuevos.put(dev, ts)
            }
            var cambio = false
            nuevos.mapNotNull { b -> b.ajustes.takeIf { it.isNotBlank() }?.let { runCatching { JSONObject(it) }.getOrNull() } }.maxByOrNull { it.optLong("at") }
                ?.let { if (Skin.aplicarAjustes(c, it)) cambio = true }
            if (nuevos.isNotEmpty()) {
                val r = Mezcla.mezclar(Biblioteca.juegos.toList(), HashMap(Biblioteca.borrados), nuevos, Cuenta.dispositivo)
                if (r.cambio) { Biblioteca.aplicarMezcla(r.juegos, r.borrados); cambio = true }
                // Lo que otro móvil tenía con otro id (importaciones repetidas): se junta en uno
                if (Biblioteca.unirRepetidos() > 0) cambio = true
                val rs = Mezcla.mezclarSagas(Sagas.lista.toList(), HashMap(Sagas.borradas), nuevos, Cuenta.dispositivo)
                if (rs.cambio) { Sagas.aplicarMezcla(rs.sagas, rs.borradas); cambio = true }
                nuevos.map { it.inicio }.filter { it.isNotBlank() }.forEach { if (Inicio.aplicarRemoto(it)) cambio = true }
            }
            guardaVistos(c, vistosNuevos)
            // Las portadas: las que pusiste o quitaste en otro móvil llegan aquí (y las de aquí suben), también al abrir Hiedra
            if (KDrive.on(c) && runCatching { NubeP.portadas(c) }.getOrDefault(false)) cambio = true
            bien(c)
            cambio
        } catch (e: Exception) { mal(c, e); false }
    }

    /** Al vincular una cuenta (o a mano, «Sincronizar ahora»): baja lo de los demás, sube lo de este dispositivo y trae las claves de Atrio. */
    suspend fun sincronizar(c: Context, desdeCero: Boolean = false): Boolean {
        if (desdeCero) olvidarVistos()
        bajar(c)
        val ok = subir(c, forzar = true)
        // Las claves de servicios y el kDrive que Atrio dejó en tu cuenta (sin tocar las tuyas)
        runCatching { Servicios.refrescarDeAtrio(c) }
        portadas(c)
        return ok
    }

    /** Varios avisos de la escucha seguidos se juntan en una sola bajada. */
    fun bajarPronto() {
        val c = app ?: return
        if (!Cuenta.vinculada || !bajando.compareAndSet(false, true)) return
        scope.launch { try { delay(150); bajar(c) } finally { bajando.set(false) } }
    }

    /** Mientras Hiedra se ve: escucha tu cuenta en directo (lo que suba otro dispositivo se baja al momento). */
    fun escuchar(c: Context) {
        iniciar(c)
        if (!Cuenta.vinculada) { parar(); return }
        if (escucha != null) return
        bajarPronto()
        val f = FbStream("hiedra", { renovar -> Cuenta.urlFlujo("${Cuenta.raiz}/stamps", renovar) }, { _, _ -> bajarPronto() }, { })
        // Azabache: en cuanto valoras un juego allí (sella su «stamps»), la nota se ve aquí al momento
        val g = FbStream("hiedra-azabache", { renovar -> Cuenta.urlFlujo("/users/${Cuenta.uid}/stamps", renovar) }, { _, _ -> NotaAzabache.pronto(c) }, { })
        escuchaAzabache = g
        g.start(scope)
        escucha = f
        f.start(scope)
    }

    fun parar() { escucha?.stop(); escucha = null; escuchaAzabache?.stop(); escuchaAzabache = null }

    private fun bien(c: Context) {
        fallos = 0
        val ahora = System.currentTimeMillis()
        ultima = ahora; prefs(c).edit().putLong("ultima", ahora).apply()
        estado = "Sincronizado"
    }

    private fun mal(c: Context, e: Exception) {
        fallos++
        estado = (e.message ?: "sin conexión").let { if (e is java.io.IOException) "Sin conexión: se reintenta solo." else it }
        // Se reintenta solo: 5 s, 15 s, 45 s… hasta 5 minutos; y, por si la app se congela antes, un trabajo de fondo para cuando haya red
        if (fallos <= 8 && Cuenta.vinculada) cambio(c, minOf(300_000L, 5_000L * Math.pow(3.0, (fallos - 1).toDouble()).toLong()))
        if (Cuenta.vinculada) SubidaWorker.encolar(c)
    }

    /** ¿Queda una subida programada? Para dejar un trabajo de fondo al salir de la app. */
    fun hayPendiente(): Boolean = programada?.isActive == true
}
