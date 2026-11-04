package com.gonzalo.hiedra

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.util.Base64

/*
 * La suite: Hiedra y Atrio del PC (el lanzador del ordenador) conectados en tiempo real por tu cuenta. Copiado de Atrio (Suite.kt) y recortado
 * a lo que Hiedra necesita (sin apuntes, guías ni fotos entre dispositivos):
 *
 *   /users/<uid>/suite/live/<disp>       quién está conectado y a qué juega (cada dispositivo escribe el suyo)
 *   /users/<uid>/suite/lib/<disp>        la biblioteca de cada PC
 *   /users/<uid>/suite/cmd/<disp>/<id>   órdenes para un PC ({ op, a, t, by }); el PC responde en «res»
 *   /users/<uid>/suite/shots/<id>        capturas (los trozos en /users/<uid>/shotdata/<id>/<n>)
 *
 * Se escucha con la API REST de Firebase en streaming (EventSource): cada cambio llega al instante. La app del PC no cambia: habla el mismo protocolo.
 */

/** Un dispositivo de tu cuenta conectado (un PC con Atrio, un móvil…). */
data class LiveDev(
    val dev: String, val app: String, val name: String, val os: String, val online: Boolean, val at: Long,
    val game: LiveGame?, val canShot: Boolean,
    /** Minutos jugados hoy en ese PC. */
    val today: Int = 0,
    /** Su Steam (para jugar en streaming con Steam Link). */
    val st: StreamHost? = null
)

/** Lo que se está jugando en un PC. */
data class LiveGame(val key: String, val name: String, val sg: Long, val start: Long, val cover: String, val hero: String, val logo: String)

/** Un juego de la biblioteca de un PC. */
data class PcGame(val key: String, val name: String, val cover: String, val sg: Long, val fav: Boolean, val lastPlayed: Long, val src: String, val minutes: Int)

/** Una captura (en la nube o ya en el móvil). */
data class Shot(
    val id: String, val pc: String, val w: Int, val h: Int, val mime: String, val bytes: Long, val time: Long,
    val game: String, val sg: Long, val uri: String = "", val by: String = ""
) {
    val local: Boolean get() = uri.isNotEmpty()
}

/** El Steam de un PC (lo publica Atrio del PC): si está instalado, abierto, su nombre y, para encenderlo por red, sus direcciones y MAC. */
data class StreamHost(val on: Boolean, val running: Boolean, val name: String, val lan: List<String>, val macs: List<String>) {
    companion object {
        private fun list(a: JSONArray?) = a?.let { x -> (0 until x.length()).map { x.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
        fun from(o: JSONObject) = StreamHost(o.optBoolean("on"), o.optBoolean("run"), o.optString("name"), list(o.optJSONArray("lan")), list(o.optJSONArray("mac")))
    }
}

/** La lista negra de Atrio: juegos que no salen en ningún sitio (Wallpaper Engine y cosas así). Se lee de lo que Atrio sube a tu cuenta. */
object Negra {
    var nombres by mutableStateOf<Set<String>>(emptySet()); private set
    fun poner(l: Collection<String>) { nombres = l.map { Catalogo.norm(it) }.filter { it.isNotEmpty() }.toSet() }
    fun vetado(nombre: String): Boolean = nombres.isNotEmpty() && Catalogo.norm(nombre) in nombres
    /** Los nombres vetados que hay en los datos de Atrio. Sin red: se puede probar. */
    fun deBlobs(blobs: List<JSONObject>): List<String> = blobs.flatMap { b -> b.optJSONArray("negra")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("nombre")?.takeIf { n -> n.isNotBlank() } } } ?: emptyList() }.distinct()
}

/** Lee «suite» de tu cuenta en directo y manda órdenes al PC. */
object Suite {
    /** Tiempo sin latido para dar un dispositivo por desconectado. */
    private const val ONLINE_MS = 3 * 60_000L

    var devices by mutableStateOf<List<LiveDev>>(emptyList()); private set
    var libs by mutableStateOf<Map<String, List<PcGame>>>(emptyMap()); private set
    /** El nombre de cada PC (de su biblioteca). */
    var nombresPc by mutableStateOf<Map<String, String>>(emptyMap()); private set
    var cloudShots by mutableStateOf<List<Shot>>(emptyList()); private set
    var localShots by mutableStateOf<List<Shot>>(emptyList()); private set
    var state by mutableStateOf("desconectado"); private set
    /** Capturas bajándose ahora: id → progreso (0..1). */
    val downloading = mutableStateMapOf<String, Float>()
    /** Aviso para la interfaz (se borra al enseñarlo). */
    var notice by mutableStateOf<String?>(null)
    /** Captura recién llegada (la interfaz la puede abrir). */
    var arrived by mutableStateOf<Shot?>(null)
    /** Para que la interfaz se redibuje cada rato (cronómetro de la partida). */
    var tick by mutableStateOf(0L)
    /** El PC elegido (si hay varios). */
    var pcElegido by mutableStateOf(""); private set

    private var scope: CoroutineScope? = null
    private var stream: FbStream? = null
    private var uid = ""
    private var tree: JSONObject? = null
    private val waits = HashMap<String, CompletableDeferred<JSONObject>>()
    private val busy = HashSet<String>()
    private var appCtx: Context? = null
    private fun prefs() = appCtx!!.getSharedPreferences("hiedra_suite", Context.MODE_PRIVATE)

    fun online(): List<LiveDev> = devices.filter { it.online && it.app == "atrio" }
    fun pcs(): List<LiveDev> = devices.filter { it.app == "atrio" }

    /** El PC al que se mandan las órdenes: el elegido si está conectado, si no el que esté jugando o el primero. */
    fun target(): LiveDev? {
        val on = online()
        return on.firstOrNull { it.dev == pcElegido } ?: on.firstOrNull { it.game != null } ?: on.firstOrNull()
    }

    fun elegirPc(dev: String) { pcElegido = dev; appCtx?.let { prefs().edit().putString("pc", dev).apply() } }

    /** Las capturas van también a la galería del móvil (Imágenes/Hiedra). */
    fun aGaleria(): Boolean = appCtx?.let { prefs().getBoolean("galeria", true) } ?: true
    fun ponAGaleria(v: Boolean) { prefs().edit().putBoolean("galeria", v).apply() }

    private fun base() = "/users/${Cuenta.uid}"

    /** Empieza a escuchar (al abrir la app con la cuenta vinculada). */
    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        pcElegido = prefs().getString("pc", "").orEmpty()
        Negra.poner(prefs().getStringSet("negra", emptySet()).orEmpty())
        loadLocal()
        if (!Cuenta.vinculada) { stop(); return }
        if (scope != null && uid == Cuenta.uid) return
        stop()
        uid = Cuenta.uid
        val sc = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = sc
        stream = FbStream("suite", { renovar -> Cuenta.urlFlujo("/users/$uid/suite", renovar) }, { t, _ -> onSuite(t) }, { s -> state = s }).also { it.start(sc) }
        // Presencia: este móvil está conectado (el PC lo enseña) y latido para el cronómetro
        sc.launch {
            while (isActive) {
                presence(true)
                repeat(6) { delay(10_000); tick = System.currentTimeMillis() }
            }
        }
        // La lista negra y las claves de servicios de Atrio, de vez en cuando; y las horas de Steam, cada 6 h
        sc.launch {
            runCatching { ImportarAtrio.blobs()?.let { b ->
                val n = Negra.deBlobs(b); Negra.poner(n); prefs().edit().putStringSet("negra", n.toSet()).apply()
                val k = Servicios.deBlobs(b); ctx.getSharedPreferences("hiedra_servicios", Context.MODE_PRIVATE).edit().putString("a_sgdb", k.sgdb).putString("a_steamKey", k.steamKey).putString("a_steamUser", k.steamUser).apply()
            } }
            val c = appCtx ?: return@launch
            if (System.currentTimeMillis() - Servicios.ultimaSteam(c) > 6 * 3_600_000L && Servicios.claves(c).steamKey.isNotBlank()) runCatching { Servicios.traerHorasSteam(c) }
        }
    }

    /** Deja de escuchar (al salir de la app). */
    fun stop() {
        val sc = scope
        if (sc != null && Cuenta.vinculada) CoroutineScope(Dispatchers.IO).launch { presence(false) }
        stream?.stop(); stream = null
        sc?.coroutineContext?.get(Job)?.cancel()
        scope = null
        tree = null
        state = "desconectado"
    }

    private fun presence(on: Boolean) {
        try {
            val c = appCtx ?: return
            val dn = try { android.provider.Settings.Global.getString(c.contentResolver, "device_name") } catch (e: Exception) { null }
            val o = JSONObject().put("app", "hiedra").put("name", Cuenta.nombreDispositivo).put("dn", dn?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "Móvil")
                .put("os", "android").put("on", on).put("at", JSONObject().put(".sv", "timestamp"))
            Cuenta.db("PUT", base() + "/suite/live/${Cuenta.dispositivo}", o.toString(), "print=silent")
        } catch (e: Exception) { }
    }

    /** Lo que hay en el árbol «suite» (dispositivos, bibliotecas, respuestas, capturas). Sin red: se puede probar con [aplicar]. */
    class Vista(val devices: List<LiveDev>, val libs: Map<String, List<PcGame>>, val nombres: Map<String, String>)

    fun leer(t: JSONObject?, yo: String, now: Long = System.currentTimeMillis()): Vista {
        val live = t?.optJSONObject("live") ?: JSONObject()
        val devs = live.keys().asSequence().filter { it != yo }.mapNotNull { d ->
            val o = live.optJSONObject(d) ?: return@mapNotNull null
            val g = o.optJSONObject("game")
            LiveDev(
                d, o.optString("app"), o.optString("dn").ifBlank { o.optString("name", "?") }, o.optString("os"),
                o.optBoolean("on") && now - o.optLong("at") < ONLINE_MS, o.optLong("at"),
                g?.takeUnless { Negra.vetado(it.optString("name")) }?.let { LiveGame(it.optString("key"), it.optString("name"), it.optLong("sg"), it.optLong("s"), it.optString("cover"), it.optString("hero"), it.optString("logo")) },
                o.optBoolean("shot", true), o.optInt("today", 0), o.optJSONObject("st")?.let { StreamHost.from(it) }
            )
        }.sortedByDescending { it.at }.toList()
        val lib = t?.optJSONObject("lib") ?: JSONObject()
        val libs = lib.keys().asSequence().associateWith { d ->
            val arr = lib.optJSONObject(d)?.optJSONArray("games") ?: JSONArray()
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filterNot { Negra.vetado(it.optString("n")) }.map {
                PcGame(it.optString("k"), it.optString("n"), it.optString("c"), it.optLong("sg"), it.optInt("f") == 1, it.optLong("lp"), it.optString("s"), it.optInt("m"))
            }
        }
        val nombres = lib.keys().asSequence().associateWith { d -> lib.optJSONObject(d)?.optString("name").orEmpty() }
        return Vista(devs, libs, nombres)
    }

    /** Para las pruebas y las capturas: un árbol «suite» de mentira, sin red. */
    fun simular(t: JSONObject, yo: String = "yo") { val v = leer(t, yo); devices = v.devices; libs = v.libs; nombresPc = v.nombres }

    private fun onSuite(t: JSONObject?) {
        tree = t
        val v = leer(t, Cuenta.dispositivo)
        val antes = devices
        devices = v.devices; libs = v.libs; nombresPc = v.nombres
        // Los juegos del PC, dentro de Hiedra
        appCtx?.let { runCatching { JuegosPc.aplicar(it, v, antes) } }
        // Respuestas a mis órdenes
        val cmds = t?.optJSONObject("cmd") ?: JSONObject()
        synchronized(waits) {
            for ((id, w) in waits.toMap()) {
                cmds.keys().forEach { d ->
                    val res = cmds.optJSONObject(d)?.optJSONObject(id)?.optJSONObject("res")
                    if (res != null) { w.complete(res); waits.remove(id) }
                }
            }
        }
        // Órdenes para este dispositivo (el PC pregunta si estamos)
        cmds.optJSONObject(Cuenta.dispositivo)?.let { mine ->
            for (id in mine.keys().asSequence().toList()) {
                val c = mine.optJSONObject(id) ?: continue
                if (c.has("res") || id in busy) continue
                if (System.currentTimeMillis() - c.optLong("t") > 10 * 60_000L) continue
                busy.add(id)
                scope?.launch { answer(id, c) }
            }
        }
        // Capturas: las nuevas se bajan solas
        val shots = t?.optJSONObject("shots") ?: JSONObject()
        val list = shots.keys().asSequence().mapNotNull { id -> shots.optJSONObject(id)?.let { id to it } }.toList()
        cloudShots = list.filter { it.second.optBoolean("done") && !it.second.optBoolean("gone") }.map { (id, o) -> shotOf(id, o) }.sortedByDescending { it.time }
        for ((id, o) in list) {
            val mine = o.optJSONObject("got")?.has(Cuenta.dispositivo) == true
            if (o.optBoolean("done") && !o.optBoolean("gone") && !mine && localShots.none { it.id == id } && id !in busy) {
                busy.add(id)
                scope?.launch { download(id, o) }
            } else if (!o.optBoolean("done") && o.optInt("n") > 0) {
                downloading[id] = o.optInt("up") * 0.5f / o.optInt("n")
            }
        }
    }

    private fun shotOf(id: String, o: JSONObject, uri: String = ""): Shot {
        val g = o.optJSONObject("game")
        return Shot(id, o.optString("pc"), o.optInt("w"), o.optInt("h"), o.optString("mime", "image/png"), o.optLong("bytes"),
            o.optLong("t"), g?.optString("n") ?: "", g?.optLong("sg") ?: 0L, uri, o.optString("by"))
    }

    /** Baja una captura trozo a trozo, la guarda tal cual (sin recomprimir) y avisa al PC de que ya la tiene. */
    private suspend fun download(id: String, o: JSONObject) {
        val n = o.optInt("n")
        try {
            downloading[id] = 0.5f
            val out = java.io.ByteArrayOutputStream(o.optInt("bytes").coerceAtLeast(1024))
            val dec = Base64.getDecoder()
            for (i in 0 until n) {
                val raw = Cuenta.db("GET", base() + "/shotdata/$id/$i", null) ?: throw java.io.IOException("falta un trozo")
                val part = JSONTokener(raw).nextValue() as? String ?: throw java.io.IOException("trozo dañado")
                out.write(dec.decode(part))
                downloading[id] = 0.5f + 0.5f * (i + 1) / n
            }
            val bytes = out.toByteArray()
            if (bytes.size.toLong() != o.optLong("bytes")) throw java.io.IOException("tamaño distinto")
            val shot = shotOf(id, o)
            val done = shot.copy(uri = save(shot, bytes))
            addLocal(done)
            Cuenta.db("PATCH", base() + "/suite/shots/$id/got", JSONObject().put(Cuenta.dispositivo, JSONObject().put(".sv", "timestamp")).toString(), "print=silent")
            arrived = done
        } catch (e: Exception) {
            notice = "No se ha podido bajar la captura: ${e.message}"
            busy.remove(id)
        } finally {
            downloading.remove(id)
        }
    }

    /** Guarda en la app (siempre) y en la galería (si se quiere), con su formato y tamaño originales. */
    private fun save(s: Shot, bytes: ByteArray): String {
        val c = appCtx!!
        val ext = if (s.mime == "image/jpeg") "jpg" else "png"
        val dir = File(c.filesDir, "capturas").apply { mkdirs() }
        val f = File(dir, "${s.id}.$ext")
        f.writeBytes(bytes)
        if (aGaleria()) try {
            val safe = s.game.ifBlank { "Escritorio" }.replace(Regex("[\\\\/:*?\"<>|]+"), " ").trim().take(60)
            val v = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$safe ${java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.ROOT).format(java.util.Date(s.time))}.$ext")
                put(MediaStore.Images.Media.MIME_TYPE, s.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Hiedra/$safe")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val r = c.contentResolver
            val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
            if (uri != null) {
                r.openOutputStream(uri)?.use { it.write(bytes) }
                r.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
        } catch (e: Exception) { /* sin galería: queda en la app */ }
        return Uri.fromFile(f).toString()
    }

    private fun loadLocal() {
        localShots = try {
            val a = JSONArray(prefs().getString("shots", "[]"))
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                Shot(it.optString("id"), it.optString("pc"), it.optInt("w"), it.optInt("h"), it.optString("mime"), it.optLong("bytes"),
                    it.optLong("t"), it.optString("game"), it.optLong("sg"), it.optString("uri"), it.optString("by"))
            }.filter { s -> File(Uri.parse(s.uri).path ?: "").exists() }.sortedByDescending { it.time }
        } catch (e: Exception) { emptyList() }
    }

    private fun persist() {
        prefs().edit().putString("shots", JSONArray().apply {
            localShots.forEach {
                put(JSONObject().put("id", it.id).put("pc", it.pc).put("w", it.w).put("h", it.h).put("mime", it.mime).put("bytes", it.bytes)
                    .put("t", it.time).put("game", it.game).put("sg", it.sg).put("uri", it.uri).put("by", it.by))
            }
        }.toString()).apply()
    }

    private fun addLocal(s: Shot) {
        localShots = (listOf(s) + localShots.filter { it.id != s.id }).sortedByDescending { it.time }
        persist()
    }

    /** Borra una captura de este móvil (la de la galería se queda). */
    fun deleteLocal(s: Shot) {
        try { File(Uri.parse(s.uri).path ?: "").delete() } catch (e: Exception) { }
        localShots = localShots.filter { it.id != s.id }
        persist()
    }

    /** Las capturas de un juego (por su nombre, que es lo que el PC apunta en cada captura). */
    fun shotsDe(titulo: String): List<Shot> = Catalogo.norm(titulo).let { n -> if (n.isEmpty()) emptyList() else localShots.filter { Catalogo.norm(it.game) == n } }
    fun shotById(id: String): Shot? = localShots.find { it.id == id }

    /**
     * Manda una orden al PC y espera su respuesta. Devuelve (bien, mensaje).
     * op: "shot", "launch" (a.key), "stream" (a.key), "power" (a.action), "focus", "rescan", "toast" (a.text), "ping".
     */
    suspend fun send(op: String, args: JSONObject = JSONObject(), dev: String? = null, timeoutMs: Long = 45_000): Pair<Boolean, String> {
        val r = sendRaw(op, args, dev, timeoutMs)
        return r.optBoolean("ok") to r.optString("msg")
    }

    /** Igual que [send], pero con toda la respuesta del PC. */
    suspend fun sendRaw(op: String, args: JSONObject = JSONObject(), dev: String? = null, timeoutMs: Long = 45_000): JSONObject = withContext(Dispatchers.IO) {
        fun fail(m: String) = JSONObject().put("ok", false).put("msg", m)
        val to = dev ?: target()?.dev ?: return@withContext fail("No hay ningún PC con Atrio conectado.")
        val body = JSONObject().put("op", op).put("a", args).put("t", JSONObject().put(".sv", "timestamp")).put("by", "Hiedra · " + Cuenta.nombreDispositivo)
        val r = try { Cuenta.db("POST", base() + "/suite/cmd/$to", body.toString()) } catch (e: Exception) { return@withContext fail(e.message ?: "sin conexión") }
        val id = try { JSONObject(r ?: "{}").optString("name") } catch (e: Exception) { "" }
        if (id.isBlank()) return@withContext fail("Firebase no ha aceptado la orden.")
        val w = CompletableDeferred<JSONObject>()
        synchronized(waits) { waits[id] = w }
        tree?.let { onSuite(it) }
        val res = withTimeoutOrNull(timeoutMs) { w.await() }
        synchronized(waits) { waits.remove(id) }
        if (res != null) return@withContext res
        val acked = tree?.optJSONObject("cmd")?.optJSONObject(to)?.optJSONObject(id)?.optBoolean("ack") == true
        if (acked) JSONObject().put("ok", true).put("msg", "${target()?.name ?: "El PC"} ha dejado de contestar: se está apagando")
        else fail("El PC no responde (¿Atrio está abierto y con tu cuenta?).")
    }

    private suspend fun answer(id: String, c: JSONObject) {
        val res = when (c.optString("op")) {
            "ping" -> JSONObject().put("ok", true).put("msg", "Hiedra en ${Cuenta.nombreDispositivo}")
            else -> JSONObject().put("ok", false).put("msg", "Hiedra no atiende esa orden (${c.optString("op")}).")
        }
        try { Cuenta.db("PUT", base() + "/suite/cmd/${Cuenta.dispositivo}/$id/res", res.put("t", JSONObject().put(".sv", "timestamp")).toString(), "print=silent") } catch (e: Exception) { }
    }

    /** El juego del PC con esa clave (de la biblioteca de cualquier PC), y en qué PC está. */
    fun pcGame(key: String): Pair<String, PcGame>? = libs.entries.firstNotNullOfOrNull { (d, l) -> l.find { it.key == key }?.let { d to it } }

    /** Pide una captura al PC. Devuelve el id de la captura (o null) y el mensaje. */
    suspend fun shoot(dev: String? = null): Pair<String?, String> {
        val r = sendRaw("shot", JSONObject(), dev, 60_000)
        if (!r.optBoolean("ok")) return null to r.optString("msg")
        return r.optString("shot").ifBlank { null } to r.optString("msg")
    }

    /** Espera a que una captura esté ya en el móvil (hasta [ms]). */
    suspend fun awaitLocal(id: String, ms: Long = 120_000): Shot? = withTimeoutOrNull(ms) {
        var s: Shot? = null
        while (s == null) { s = shotById(id); if (s == null) delay(250) }
        s
    }
}

/**
 * Los juegos del PC dentro de Hiedra. La biblioteca del PC entera se ve en «Tu PC»; en Hiedra entran solos los que has jugado (con horas en el PC,
 * o que el PC esté ejecutando ahora) y los que ya tenías (una ficha de Atrio con el mismo nombre pasa a ser el juego del PC, con la clave antigua
 * como alias); el resto, cuando los añadas a mano. Las horas del PC cuentan como de un dispositivo más («pc:<PC>»), así no se suman dos veces.
 */
object JuegosPc {
    /** Un juego jugado hace menos de esto se considera «jugando». */
    const val RECIENTE = 14L * 86_400_000L

    private fun deLista(g: PcGame, dev: String, ahora: Long): Juego = Juego(
        Biblioteca.nuevoId(), g.name, portadaUrl = g.cover, plataforma = "PC", favorito = g.fav,
        anadido = ahora, ultima = g.lastPlayed, estado = if (g.lastPlayed > 0 && ahora - g.lastPlayed < RECIENTE) Estado.JUGANDO else Estado.PENDIENTE,
        lanzadores = listOf(Lanzador.pc(g.key, dev))
    )

    /** ¿Este de la biblioteca del PC entra solo en Hiedra? Con horas, o si ya tenías un juego que se llama igual. */
    fun entraSolo(g: PcGame): Boolean = g.minutes > 0 || Biblioteca.porTitulo(g.name) != null

    /** Mete (o actualiza) en Hiedra un juego de la biblioteca de un PC. Devuelve el juego de Hiedra. */
    fun meter(g: PcGame, dev: String, ahora: Long = System.currentTimeMillis()): Juego {
        val previo = Biblioteca.porClave("pc:" + g.key) ?: Biblioteca.porTitulo(g.name)
        val ms = g.minutes * 60_000L
        val n = when {
            previo == null -> deLista(g, dev, ahora)
            // Un juego que ya tenías (una ficha de Atrio, su ROM, su app…) gana el lanzador del PC
            previo.pc == null -> previo.conLanzador(Lanzador.pc(g.key, dev)).copy(portadaUrl = previo.portadaUrl.ifBlank { g.cover }, favorito = previo.favorito || g.fav, ultima = maxOf(previo.ultima, g.lastPlayed),
                estado = if (previo.estado == Estado.PENDIENTE && g.lastPlayed > 0 && ahora - g.lastPlayed < RECIENTE) Estado.JUGANDO else previo.estado)
            else -> previo.copy(portadaUrl = previo.portadaUrl.ifBlank { g.cover }, ultima = maxOf(previo.ultima, g.lastPlayed))
        }
        // Las horas del PC son una fuente externa (lo que Atrio del PC cuenta): nunca se suman dos veces
        val f = Fuentes.pc(dev)
        val fin = if (ms > 0) n.copy(externas = n.externas + (f to n.externa(f).copy(ms = ms))) else n
        if (fin != previo) Biblioteca.poner(fin)
        return fin
    }

    /** Lo que el PC está ejecutando ahora: entra (si no estaba) y pasa a «Jugando». */
    fun enMarcha(g: LiveGame, dev: String, ahora: Long = System.currentTimeMillis()): Juego? {
        if (g.key.isBlank() || g.name.isBlank()) return null
        val lib = Suite.libs[dev]?.firstOrNull { it.key == g.key } ?: PcGame(g.key, g.name, g.cover, g.sg, false, ahora, "", 0)
        val j = meter(lib, dev, ahora)
        if (j.estado == Estado.PENDIENTE || j.estado == Estado.ABANDONADO) Biblioteca.poner(j.conEstado(Estado.JUGANDO).copy(ultima = ahora))
        return Biblioteca.por(j.id)
    }

    /** Una partida en el PC que acaba de terminar (se vio empezar): sus minutos van a las barras de ese día. */
    fun partidaTerminada(g: LiveGame, ahora: Long = System.currentTimeMillis()) {
        val j = Biblioteca.porClave("pc:" + g.key) ?: return
        val ms = (ahora - g.start).coerceIn(0L, 12 * 3600_000L)
        if (ms < 60_000L) return
        val f = Fuentes.pc(Suite.pcGame(g.key)?.first ?: j.pcDev)
        val h = j.externa(f)
        Biblioteca.poner(j.copy(externas = j.externas + (f to h.copy(dias = Sesion.conDias(h.dias, ahora, ms))), ultima = ahora))
    }

    /** Al cambiar lo que hay en la cuenta: las bibliotecas entran (lo que toca) y las partidas en marcha se apuntan. */
    fun aplicar(c: Context, v: Suite.Vista, antes: List<LiveDev>, ahora: Long = System.currentTimeMillis()) {
        if (!Biblioteca.cargada) Biblioteca.cargar(c)
        for ((dev, lista) in v.libs) for (g in lista) {
            if (Biblioteca.porClave("pc:" + g.key) != null || entraSolo(g)) meter(g, dev, ahora)
        }
        for (d in v.devices) {
            if (d.app != "atrio") continue
            val g = d.game
            val previo = antes.firstOrNull { it.dev == d.dev }?.game
            if (g != null && d.online) enMarcha(g, d.dev, ahora)
            if (previo != null && (g == null || g.key != previo.key || g.start != previo.start)) partidaTerminada(previo, ahora)
        }
    }
}
