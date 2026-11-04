package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.security.MessageDigest

/**
 * Traer tu biblioteca de Carmesí (una sola vez, al pasarte a Hiedra). Carmesí sube la de cada dispositivo a tu cuenta
 * (/users/<uid>/portico/dev/<dispositivo>/data, el formato de su Mezcla.kt); Hiedra la LEE (en Carmesí no se cambia nada) y de cada juego
 * hace un Juego del modelo nuevo:
 *  - dos juegos de Carmesí con la misma clave en varios dispositivos son uno (gana el cambio más reciente, como en Carmesí);
 *  - su clave y sus alias pasan a las claves del juego; según su tipo gana un lanzador (ROM, app, web o PC) o ninguno (ficha);
 *  - estado, favorito, cuándo lo pasaste, plataforma, portada de internet, encuadre y lo que suele durar vienen tal cual;
 *  - las horas de cada dispositivo de Carmesí pasan a la fuente `carmesi:<dispositivo>` (viaja con la cuenta y ya no cambia). Las de Steam
 *    y del PC no están en lo que Carmesí sube: Hiedra las vuelve a leer de Steam y del PC;
 *  - lo que borraste en Carmesí no entra; la nota, los apuntes y la maqueta no vuelven (docs/MODELO.md);
 *  - las sagas y la portada de inicio (estantes, Mis elegidos, destacado) se traducen a los juegos de aquí; la apariencia, si es más nueva;
 *  - tus portadas (las de tu kDrive, en nube/portadas de Carmesí) se bajan para los juegos que aquí no tengan ninguna;
 *  - los guardados y las ROMs que Carmesí subió a tu kDrive pasan a ser los de Hiedra (se copia su índice de la cuenta, por el id del juego;
 *    los archivos de kDrive son los mismos), así no hay que volver a subir nada.
 * Lo que ya tienes en Hiedra con alguna de esas claves (o el mismo título) no se duplica: se completa. Se puede repetir.
 */
object ImportarCarmesi {
    const val RAIZ_CARMESI = "portico"

    /** Un juego de Carmesí ya traducido: el Juego nuevo, las claves de Carmesí (la suya y sus alias) y el kid de su portada en la nube de Carmesí. */
    class Candidato(val juego: Juego, val claves: List<String>, val kid: String)

    class Hallazgo(val candidatos: List<Candidato>, val sagas: List<Saga>, val inicio: JSONObject?, val ajustes: JSONObject?)

    private fun kidDe(clave: String) = MessageDigest.getInstance("SHA-1").digest(clave.toByteArray()).joinToString("") { "%02x".format(it) }.take(16)

    /** Los candidatos a partir de lo que subió cada dispositivo de Carmesí ([blobs]: dispositivo → su biblioteca). Sin red: se puede probar. */
    fun candidatos(blobs: Map<String, JSONObject>, ahora: Long = System.currentTimeMillis()): List<Candidato> {
        val borrados = HashMap<String, Long>()
        blobs.values.forEach { b -> b.optJSONObject("borrados")?.let { o -> for (k in o.keys()) borrados[k] = maxOf(borrados[k] ?: 0L, o.optLong(k)) } }
        // Por clave: el más reciente (la ficha) y las horas de cada dispositivo
        val fichas = LinkedHashMap<String, JSONObject>()
        val horas = HashMap<String, HashMap<String, Horas>>()
        for ((dev, b) in blobs) {
            val a = b.optJSONArray("juegos") ?: continue
            for (i in 0 until a.length()) {
                val g = a.optJSONObject(i) ?: continue
                val k = g.optString("k"); if (k.isBlank() || g.optString("t").isBlank()) continue
                if ((borrados[k] ?: 0L) > g.optLong("mod")) continue
                val cur = fichas[k]
                if (cur == null || g.optLong("mod") > cur.optLong("mod")) fichas[k] = g
                val h = Horas(g.optLong("msp"), g.optInt("vp"), g.optJSONObject("dd")?.let { d -> d.keys().asSequence().associateWith { d.optLong(it) } } ?: emptyMap())
                if (!h.vacias) horas.getOrPut(k) { HashMap() }.merge(dev, h) { x, y -> x.max(y) }
            }
        }
        return fichas.map { (k, g) ->
            val lanzador = when (g.optString("tp")) {
                "ROM" -> Lanzador.rom(g.optString("s"), g.optString("a").ifBlank { g.optString("t") })
                "APP" -> Lanzador.app(g.optString("pk"))
                "WEB" -> Lanzador.web(g.optString("u"), g.optInt("o"))
                "PC" -> Lanzador.pc(g.optString("pck"), g.optString("pcd"))
                else -> null
            }?.takeIf { l -> when (l.tipo) { TipoLanzador.ROM -> l.sistema.isNotBlank(); TipoLanzador.APP -> l.paquete.isNotBlank(); TipoLanzador.WEB -> l.url.isNotBlank(); TipoLanzador.PC -> l.pcKey.isNotBlank() } }
            val alias = g.optJSONArray("al")?.let { x -> (0 until x.length()).map { x.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
            val claves = (listOf(k) + alias).distinct()
            val estado = Estado.entries.firstOrNull { it.name == g.optString("es") } ?: if (g.optBoolean("te")) Estado.PASADO else Estado.PENDIENTE
            val anadido = g.optLong("an").takeIf { it > 0 } ?: g.optLong("mod").takeIf { it > 0 } ?: ahora
            val ex = horas[k].orEmpty().mapKeys { Fuentes.carmesi(it.key) }
            val juego = nuevoJuego(g.optString("t"), g.optString("pl"), lanzador, anadido).copy(
                estado = estado, finalizado = if (estado == Estado.PASADO) g.optLong("fi") else 0L, favorito = g.optBoolean("f"),
                ultima = maxOf(g.optLong("ul"), ex.values.maxOfOrNull { h -> h.dias.keys.maxOrNull()?.let { diaAMs(it) } ?: 0L } ?: 0L),
                portadaUrl = g.optString("pu"), zoom = g.optDouble("ez", 1.0).toFloat(), ox = g.optDouble("eox", 0.0).toFloat(), oy = g.optDouble("eoy", 0.0).toFloat(),
                durHistoria = g.optInt("dh"), durExtras = g.optInt("de"), durCompleto = g.optInt("dc"),
                otrasClaves = claves.filter { c -> lanzador == null || lanzador.clave != c }, externas = ex, mod = g.optLong("mod")
            )
            Candidato(juego, claves, kidDe(k))
        }.sortedBy { Catalogo.norm(it.juego.titulo) }
    }

    private fun diaAMs(d: String): Long = runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(d)?.time }.getOrNull() ?: 0L

    /** Las sagas de Carmesí (sus juegos por clave; al importar se cambian por los ids de aquí): de cada una, la más reciente que no esté borrada. */
    fun sagasDe(blobs: Map<String, JSONObject>): List<Pair<Saga, List<String>>> {
        val borradas = HashMap<String, Long>()
        blobs.values.forEach { b -> b.optJSONObject("sgb")?.let { o -> for (k in o.keys()) borradas[k] = maxOf(borradas[k] ?: 0L, o.optLong(k)) } }
        val por = LinkedHashMap<String, JSONObject>()
        for (b in blobs.values) {
            val a = b.optJSONArray("sg") ?: continue
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optString("id"); if (id.isBlank()) continue
                if ((borradas[id] ?: 0L) > o.optLong("mod")) continue
                val cur = por[id]; if (cur == null || o.optLong("mod") > cur.optLong("mod")) por[id] = o
            }
        }
        return por.values.mapNotNull { o ->
            val k = o.optJSONArray("k") ?: JSONArray()
            val claves = (0 until k.length()).map { k.optString(it) }.filter { it.isNotBlank() }.distinct()
            val nombre = o.optString("n").trim()
            if (claves.isEmpty() || nombre.isBlank()) null else Saga(o.optString("id"), nombre, emptyList(), o.optLong("mod")) to claves
        }.sortedBy { Catalogo.norm(it.first.nombre) }
    }

    /** La portada de inicio más reciente de Carmesí (sus juegos por clave), o null. */
    fun inicioDe(blobs: Map<String, JSONObject>): JSONObject? = blobs.values.mapNotNull { it.optJSONObject("ini") }.maxByOrNull { it.optLong("mod") }

    /** La apariencia más reciente de Carmesí, o null. */
    fun ajustesDe(blobs: Map<String, JSONObject>): JSONObject? = blobs.values.mapNotNull { it.optJSONObject("aj") }.maxByOrNull { it.optLong("at") }

    /** Lo último que Carmesí subió a tu cuenta (todos sus dispositivos). Bloqueante (red). */
    fun leer(): Hallazgo? {
        val b = blobs() ?: return null
        return Hallazgo(candidatos(b), sagasDe(b).map { (s, claves) -> s.copy(juegos = claves) }, inicioDe(b), ajustesDe(b))
    }

    /** Las bibliotecas que subieron los dispositivos de Carmesí (dispositivo → su biblioteca), o null si no hay. Bloqueante (red). */
    fun blobs(): Map<String, JSONObject>? {
        if (!Cuenta.vinculada) return null
        val st = Cuenta.db("GET", "/users/${Cuenta.uid}/$RAIZ_CARMESI/stamps", null)?.let { JSONObject(it) } ?: return null
        val out = LinkedHashMap<String, JSONObject>()
        for (dev in st.keys().asSequence().sortedByDescending { st.optLong(it) }.take(8)) {
            val raw = Cuenta.db("GET", "/users/${Cuenta.uid}/$RAIZ_CARMESI/dev/$dev/data", null) ?: continue
            val datos = (JSONTokener(raw).nextValue() as? String) ?: continue
            runCatching { JSONObject(datos) }.getOrNull()?.let { out[dev] = it }
        }
        return out.ifEmpty { null }
    }

    /** El juego de Hiedra que ya es este candidato: por alguna de sus claves de Carmesí o porque se llama igual. */
    fun existente(c: Candidato): Juego? = c.claves.firstNotNullOfOrNull { Biblioteca.porClave(it) } ?: Biblioteca.porTitulo(c.juego.titulo)

    /** Completa un juego que ya tenías con lo que trae Carmesí (sin pisar lo que ya tenga). Devuelve el mismo si nada cambia. */
    fun completar(previo: Juego, c: Candidato): Juego {
        var n = previo
        val a = c.juego
        val peso = mapOf(Estado.PENDIENTE to 0, Estado.ABANDONADO to 1, Estado.JUGANDO to 2, Estado.PASADO to 3)
        if ((peso[a.estado] ?: 0) > (peso[previo.estado] ?: 0)) n = n.copy(estado = a.estado, finalizado = if (a.estado == Estado.PASADO) a.finalizado else 0L)
        else if (n.estado == Estado.PASADO && n.finalizado == 0L && a.finalizado > 0) n = n.copy(finalizado = a.finalizado)
        if (previo.plataforma.isBlank() && a.plataforma.isNotBlank()) n = n.copy(plataforma = a.plataforma)
        if (previo.portadaUrl.isBlank() && a.portadaUrl.isNotBlank()) n = n.copy(portadaUrl = a.portadaUrl)
        if (a.favorito && !previo.favorito) n = n.copy(favorito = true)
        if (previo.durHistoria == 0 && a.durHistoria > 0) n = n.copy(durHistoria = a.durHistoria, durExtras = a.durExtras, durCompleto = a.durCompleto)
        if (previo.portada.isBlank() && previo.zoom == 1f && previo.ox == 0f && previo.oy == 0f) n = n.copy(zoom = a.zoom, ox = a.ox, oy = a.oy)
        a.lanzadores.forEach { n = n.conLanzador(it) }
        val externas = HashMap(n.externas)
        a.externas.forEach { (k, h) -> externas[k] = (externas[k] ?: Horas()).max(h) }
        n = n.copy(externas = externas, ultima = maxOf(n.ultima, a.ultima))
        val nuevas = c.claves.filter { it !in n.claves }
        if (nuevas.isNotEmpty()) n = n.copy(otrasClaves = n.otrasClaves + nuevas)
        if (previo.anadido == 0L || (a.anadido in 1 until previo.anadido)) n = n.copy(anadido = a.anadido)
        return n
    }

    class Resultado(val nuevos: Int, val yaEstaban: Int, val portadas: Int, val sagas: Int = 0, val inicio: Boolean = false, val ajustes: Boolean = false, val nube: Int = 0)

    /**
     * Los guardados y las ROMs de Carmesí en tu kDrive: su índice (`nube/saves/<kid>` y `nube/roms/<kid>` de Carmesí) se copia al de Hiedra
     * (`nube/saves/<id>`, `nube/roms/<id>`) para los juegos que aquí aún no tengan nada. Devuelve cuántos juegos han ganado algo. Bloqueante (red).
     */
    fun traerNube(idDeKid: Map<String, String>): Int {
        var n = 0
        for (tipo in listOf("saves", "roms")) {
            val de = runCatching { Cuenta.db("GET", "/users/${Cuenta.uid}/$RAIZ_CARMESI/nube/$tipo", null)?.let { JSONObject(it) } }.getOrNull() ?: continue
            val aqui = runCatching { Cuenta.db("GET", "${Cuenta.raiz}/nube/$tipo", null)?.let { JSONObject(it) } }.getOrNull()
            val cambios = JSONObject()
            for ((kid, id) in idDeKid) {
                val nodo = de.optJSONObject(kid) ?: continue
                if (aqui?.optJSONObject(id)?.optJSONObject("f")?.length() ?: 0 > 0) continue
                cambios.put(id, nodo)
            }
            if (cambios.length() > 0) { Cuenta.db("PATCH", "${Cuenta.raiz}/nube/$tipo", cambios.toString(), "print=silent"); n += cambios.length() }
        }
        return n
    }

    /** Pone lo hallado en Hiedra. Bloqueante (red: las portadas). Se puede repetir sin duplicar. */
    fun importar(ctx: Context, h: Hallazgo, avance: (Int, Int) -> Unit = { _, _ -> }): Resultado {
        var nuevos = 0; var ya = 0; var portadas = 0
        val cands = h.candidatos
        // La clave de Carmesí → el id del juego que se queda (el nuevo o el que ya tenías); y el kid de Carmesí → ese id (para la nube)
        val idDe = HashMap<String, String>()
        val idDeKid = HashMap<String, String>()
        val indice = runCatching { Cuenta.db("GET", "/users/${Cuenta.uid}/$RAIZ_CARMESI/nube/portadas", null)?.let { JSONObject(it) } }.getOrNull()
        cands.forEachIndexed { i, c ->
            avance(i, cands.size)
            val previo = existente(c)
            val juego = if (previo == null) { nuevos++; c.juego.also { Biblioteca.poner(it) } } else { ya++; completar(previo, c).also { if (it != previo) Biblioteca.poner(it) } }
            c.claves.forEach { idDe[it] = juego.id }; idDeKid[c.kid] = juego.id
            if (juego.portada.isBlank() || !File(juego.portada).exists()) {
                val f = indice?.optJSONObject(c.kid)?.optJSONObject("f")?.optJSONObject("img")
                val id = f?.optLong("id") ?: 0L
                if (id > 0L && KDrive.on(ctx)) runCatching { bajarPortada(ctx, juego, id) }.getOrNull()?.let { ruta ->
                    Biblioteca.por(juego.id)?.let { Biblioteca.poner(it.copy(portada = ruta)); portadas++ }
                }
            }
        }
        var nSagas = 0
        for (s in h.sagas) {
            val ids = s.juegos.mapNotNull { idDe[it] }.distinct()
            if (ids.isEmpty()) continue
            val aqui = Sagas.por(s.id)
            if (aqui == null) { Sagas.poner(s.copy(juegos = ids)); nSagas++ }
            else {
                val nuevas = ids.filter { Sagas.de(it) == null }
                if (nuevas.isNotEmpty()) Sagas.poner(aqui.copy(juegos = aqui.juegos + nuevas))
            }
        }
        // La portada de inicio: solo si aquí no la has tocado
        var inicio = false
        h.inicio?.let { o ->
            if (Inicio.config.mod == 0L) {
                val k = o.optJSONArray("k") ?: JSONArray()
                val fijados = (0 until k.length()).mapNotNull { idDe[k.optString(it)] }.distinct()
                val filas = ConfigInicio.deJson(JSONObject(o.toString()).put("k", JSONArray()))?.filas ?: ConfigInicio.porDefecto
                Inicio.cambiar { it.copy(filas = filas, fijados = fijados, destacado = idDe[o.optString("d")].orEmpty()) }
                inicio = true
            }
        }
        val ajustes = h.ajustes?.let { runCatching { Skin.aplicarAjustes(ctx, it) }.getOrDefault(false) } ?: false
        val nube = runCatching { traerNube(idDeKid) }.getOrDefault(0)
        avance(cands.size, cands.size)
        return Resultado(nuevos, ya, portadas, nSagas, inicio, ajustes, nube)
    }

    /** Baja una portada tuya de tu kDrive (la que Carmesí subió) a la carpeta de portadas. null si no se pudo. */
    private fun bajarPortada(ctx: Context, j: Juego, id: Long): String? {
        val dir = File(ctx.filesDir, "portadas").apply { mkdirs() }
        val dest = File(dir, "${j.id}-${System.currentTimeMillis()}.jpg")
        return try { KDrive.download(ctx, id, dest); if (dest.length() > 200) dest.absolutePath else { dest.delete(); null } } catch (e: Exception) { dest.delete(); null }
    }
}

/** El enlace de Ajustes: lee lo que Carmesí tiene en tu cuenta, te dice qué hay y lo trae. */
@Composable
fun SeccionImportarCarmesi() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var estado by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var hallados by remember { mutableStateOf<ImportarCarmesi.Hallazgo?>(null) }
    MagLink(if (ocupado) "Un momento…" else "Traer mi biblioteca de Carmesí", "Solo lee tu cuenta: en Carmesí no cambia nada. Trae juegos, cómo se abren, estado, horas, portadas, sagas, la portada de inicio y la apariencia") {
        if (ocupado) return@MagLink
        if (!Cuenta.vinculada) { estado = "Primero vincula tu cuenta (arriba)."; return@MagLink }
        ocupado = true; estado = "Leyendo lo que Carmesí tiene en tu cuenta…"
        ambito.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ImportarCarmesi.leer() } }
            ocupado = false
            r.onSuccess { l -> if (l == null || l.candidatos.isEmpty()) estado = "No he encontrado nada de Carmesí en tu cuenta." else { hallados = l; estado = "" } }
                .onFailure { estado = it.message ?: "No he podido leer tu cuenta." }
        }
    }
    if (estado.isNotBlank()) Text(estado, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
    hallados?.let { h ->
        val l = h.candidatos
        val n = { e: Estado -> l.count { it.juego.estado == e } }
        val yaEstan = l.count { ImportarCarmesi.existente(it) != null }
        val conRom = l.count { it.juego.rom != null }
        AlertDialog(
            onDismissRequest = { hallados = null }, containerColor = Paper, shape = esquina(4.dp),
            title = { Text("${l.size} juegos en Carmesí", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${n(Estado.JUGANDO)} jugando, ${n(Estado.PENDIENTE)} pendientes, ${n(Estado.PASADO)} pasados, ${n(Estado.ABANDONADO)} abandonados" +
                        (if (h.sagas.isNotEmpty()) " y ${h.sagas.size} ${if (h.sagas.size == 1) "saga" else "sagas"}" else "") + ". " +
                        (if (yaEstan > 0) "$yaEstan ya los tienes aquí: se completan sin duplicarse. " else "") +
                        (if (conRom > 0) "Las ROMs ($conRom) quedan «en otro dispositivo» hasta que escanees su carpeta aquí. " else "") +
                        "Las horas de Carmesí se guardan aparte (como «Carmesí» en la ficha); las de Steam y del PC se vuelven a leer. Los guardados y las ROMs que Carmesí subió a tu kDrive pasan a ser los de aquí.",
                    color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val hallazgo = h; hallados = null; ocupado = true; estado = "Importando…"
                    ambito.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { ImportarCarmesi.importar(ctx, hallazgo) { i, t -> estado = "Importando $i de $t…" } } }
                        ocupado = false
                        estado = r.fold({ "Hecho: ${it.nuevos} juegos nuevos, ${it.yaEstaban} que ya tenías, ${it.portadas} portadas" + (if (it.sagas > 0) ", ${it.sagas} sagas" else "") + (if (it.nube > 0) ", guardados o ROMs de kDrive para ${it.nube}" else "") + (if (it.inicio) ", la portada de inicio" else "") + (if (it.ajustes) " y la apariencia." else ".") }, { e -> e.message ?: "No he podido importar." })
                    }
                }) { Text("Importar", color = Acento, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { hallados = null }) { Text("Cancelar", color = Muted) } }
        )
    }
}
