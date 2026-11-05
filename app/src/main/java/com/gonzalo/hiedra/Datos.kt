package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Los datos de un juego que vienen de fuera (para la casilla «Datos» de la ficha): cuándo salió, quién lo hizo, quién lo editó, sus géneros,
 * un resumen y la nota de Metacritic. Y lo que suele durar (va al juego, en sus duraciones, y viaja con la cuenta).
 *  - Primero la tienda de Steam (sin clave): por el appid si el juego es de Steam en tu PC, o buscando su nombre (solo si coincide).
 *  - Si no está en Steam, RAWG (con tu clave, la de Atrio vale): tiene también los juegos de consola de siempre.
 *  - Lo que suele durar: IGDB (con tu Client ID y secreto), que tiene lo que registran sus jugadores; si no, la media de horas de RAWG.
 * Se guardan en este móvil (datos.json) dos semanas; lo que no se encuentra se vuelve a intentar a los tres días.
 */
data class DatosJuego(
    val fuente: String = "", val salida: String = "", val desarrolladores: List<String> = emptyList(), val editores: List<String> = emptyList(),
    val generos: List<String> = emptyList(), val resumen: String = "", val metacritic: Int = 0,
    /** De dónde salieron las duraciones del juego (IGDB, RAWG…), si las puso Hiedra. */
    val duracionDe: String = "",
    val at: Long = 0L, val nada: Boolean = false,
) {
    val vacios: Boolean get() = salida.isBlank() && desarrolladores.isEmpty() && editores.isEmpty() && generos.isEmpty() && resumen.isBlank() && metacritic == 0

    fun aJson(): JSONObject = JSONObject().put("f", fuente).put("s", salida).put("d", JSONArray(desarrolladores)).put("e", JSONArray(editores)).put("g", JSONArray(generos))
        .put("r", resumen).put("m", metacritic).put("dd", duracionDe).put("at", at).put("n", nada)

    companion object {
        private fun lista(a: JSONArray?): List<String> = a?.let { x -> (0 until x.length()).map { x.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()
        fun deJson(o: JSONObject): DatosJuego = DatosJuego(o.optString("f"), o.optString("s"), lista(o.optJSONArray("d")), lista(o.optJSONArray("e")), lista(o.optJSONArray("g")),
            o.optString("r"), o.optInt("m"), o.optString("dd"), o.optLong("at"), o.optBoolean("n"))
    }
}

object Datos {
    const val FRESCO = 14L * 86_400_000L
    const val FALLIDO = 3L * 86_400_000L
    /** Se apaga en las pruebas (sin red). */
    var activo = true
    val de = mutableStateMapOf<String, DatosJuego>()
    private var archivo: File? = null
    private val pidiendo = HashSet<String>()

    fun cargar(c: Context) {
        if (archivo != null) return
        archivo = File(c.filesDir, "datos.json")
        runCatching { val o = JSONObject(archivo!!.readText()); for (k in o.keys()) o.optJSONObject(k)?.let { de[k] = DatosJuego.deJson(it) } }
    }
    private fun guardar() { archivo?.let { f -> runCatching { f.writeText(JSONObject().also { o -> de.forEach { (k, v) -> o.put(k, v.aJson()) } }.toString()) } } }
    fun poner(id: String, d: DatosJuego) { de[id] = d; guardar() }
    fun olvidar(id: String) { de.remove(id); guardar() }

    /** ¿Hay que pedirlos? (no están, o son viejos, o se intentó hace días sin suerte) */
    fun hacenFalta(id: String, ahora: Long = System.currentTimeMillis()): Boolean {
        val d = de[id] ?: return true
        return ahora - d.at > (if (d.nada) FALLIDO else FRESCO)
    }

    // ---------------------------------------------------------------- lo que se saca de cada respuesta (sin red: se puede probar)

    private fun lista(a: JSONArray?, max: Int, campo: String? = null): List<String> = a?.let { x ->
        (0 until x.length()).mapNotNull { i -> if (campo == null) x.optString(i) else x.optJSONObject(i)?.optString(campo) }.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(max)
    } ?: emptyList()

    private fun limpiaHtml(s: String) = s.replace(Regex("<[^>]+>"), " ").replace("&quot;", "\"").replace("&amp;", "&").replace("&#39;", "'").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    /** La respuesta de appdetails de la tienda de Steam para ese appid. null si Steam no tiene el juego. */
    fun deSteam(json: String, appid: String, ahora: Long = System.currentTimeMillis()): DatosJuego? {
        val d = JSONObject(json).optJSONObject(appid)?.takeIf { it.optBoolean("success") }?.optJSONObject("data") ?: return null
        val rd = d.optJSONObject("release_date")
        return DatosJuego("Steam", if (rd?.optBoolean("coming_soon") == true) "próximamente" else rd?.optString("date").orEmpty().trim(),
            lista(d.optJSONArray("developers"), 3), lista(d.optJSONArray("publishers"), 3), lista(d.optJSONArray("genres"), 4, "description"),
            limpiaHtml(d.optString("short_description")).take(600), d.optJSONObject("metacritic")?.optInt("score") ?: 0, at = ahora)
    }

    /** El appid del juego en la búsqueda de la tienda de Steam, solo si el nombre coincide. */
    fun appidDeBusqueda(json: String, titulo: String): String? {
        val items = JSONObject(json).optJSONArray("items") ?: return null
        val n = Catalogo.norm(titulo)
        return (0 until items.length()).mapNotNull { items.optJSONObject(it) }.firstOrNull { Catalogo.norm(it.optString("name")) == n }?.optLong("id")?.takeIf { it > 0 }?.toString()
    }

    /** El id del juego en una búsqueda de RAWG, solo si el nombre coincide. */
    fun idDeRawg(json: String, titulo: String): Long? {
        val r = JSONObject(json).optJSONArray("results") ?: return null
        val n = Catalogo.norm(titulo)
        return (0 until r.length()).mapNotNull { r.optJSONObject(it) }.firstOrNull { Catalogo.norm(it.optString("name")) == n }?.optLong("id")?.takeIf { it > 0 }
    }

    /** La ficha de un juego en RAWG (/games/<id>). */
    fun deRawg(json: String, ahora: Long = System.currentTimeMillis()): DatosJuego {
        val d = JSONObject(json)
        return DatosJuego("RAWG", fechaBonita(d.optString("released")), lista(d.optJSONArray("developers"), 3, "name"), lista(d.optJSONArray("publishers"), 3, "name"),
            lista(d.optJSONArray("genres"), 4, "name"), limpiaHtml(d.optString("description_raw").ifBlank { d.optString("description") }).take(600), d.optInt("metacritic"), at = ahora)
    }

    /** Las horas medias de RAWG (horas → minutos), o 0. */
    fun horasDeRawg(json: String): Int = JSONObject(json).optInt("playtime", 0) * 60

    /** «2020-09-17» → «17 sept 2020» (y si solo hay año, el año). */
    fun fechaBonita(iso: String): String {
        val p = iso.trim().split('-')
        return when { p.size >= 3 && p[0].length == 4 -> runCatching { Formato.fecha(java.time.LocalDate.of(p[0].toInt(), p[1].toInt(), p[2].take(2).toInt())) }.getOrDefault(iso.trim()); p.isNotEmpty() && p[0].length == 4 -> p[0]; else -> iso.trim() }
    }

    class Duraciones(val historia: Int, val extras: Int, val completo: Int, val de: String)

    /** Las duraciones de IGDB (game_time_to_beats: segundos) para el juego [nombre]; null si no hay. */
    fun duracionesDeIgdb(json: String, nombre: String, jugadores: Int = 0): Duraciones? {
        val o = JSONArray(json).optJSONObject(0) ?: return null
        fun min(k: String) = (o.optLong(k, 0L) / 60L).toInt().coerceIn(0, 60 * 500)
        val normal = min("normally"); val rapido = min("hastily"); val todo = min("completely")
        if (normal == 0 && rapido == 0 && todo == 0) return null
        val n = if (jugadores > 0) jugadores else o.optInt("count", 0)
        return Duraciones(if (normal > 0) normal else rapido, if (normal > 0 && rapido > 0 && rapido != normal) rapido else 0, todo, "IGDB · $nombre" + (if (n > 0) " · $n jugadores" else ""))
    }

    // ---------------------------------------------------------------- la red

    private const val UA = "Mozilla/5.0 (Linux; Android 14) Hiedra"
    private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8")

    private fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Accept-Language", "es-ES,es;q=0.9")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.connectTimeout = 15000; c.readTimeout = 20000
        try { if (c.responseCode !in 200..299) error("El servidor respondió ${c.responseCode}"); return c.inputStream.bufferedReader().readText() } finally { c.disconnect() }
    }

    private fun post(url: String, body: String, headers: Map<String, String> = emptyMap(), tipo: String = "text/plain"): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Content-Type", tipo)
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.connectTimeout = 15000; c.readTimeout = 30000
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            if (c.responseCode !in 200..299) throw HttpFallo(c.responseCode)
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }
    class HttpFallo(val codigo: Int) : Exception("El servidor respondió $codigo")

    /** El appid de Steam de un juego: el de su lanzador del PC (steam:<id>) o buscándolo por nombre en la tienda. */
    private fun appidDe(j: Juego): String? {
        j.pc?.pcKey?.takeIf { it.startsWith("steam:") }?.substringAfter("steam:")?.takeIf { it.all { c -> c.isDigit() } && it.isNotBlank() }?.let { return it }
        return runCatching { appidDeBusqueda(get("https://store.steampowered.com/api/storesearch/?term=${enc(j.titulo)}&l=spanish&cc=ES"), j.titulo) }.getOrNull()
    }

    private fun igdbToken(c: Context, k: Servicios.Claves, nuevo: Boolean): String {
        val p = c.getSharedPreferences("hiedra_servicios", Context.MODE_PRIVATE)
        if (!nuevo && p.getLong("igdb_exp", 0L) > System.currentTimeMillis() + 60_000) p.getString("igdb_tok", "")?.takeIf { it.isNotBlank() }?.let { return it }
        val o = JSONObject(post("https://id.twitch.tv/oauth2/token?client_id=${enc(k.igdbId)}&client_secret=${enc(k.igdbSecret)}&grant_type=client_credentials", "", tipo = "application/x-www-form-urlencoded"))
        val t = o.optString("access_token").ifBlank { error("Twitch no ha dado acceso") }
        p.edit().putString("igdb_tok", t).putLong("igdb_exp", System.currentTimeMillis() + o.optLong("expires_in", 3600L) * 1000L).apply()
        return t
    }

    private fun igdb(c: Context, k: Servicios.Claves, endpoint: String, cuerpo: String): String {
        fun ir(nuevo: Boolean) = post("https://api.igdb.com/v4/$endpoint", cuerpo, mapOf("Client-ID" to k.igdbId, "Authorization" to "Bearer ${igdbToken(c, k, nuevo)}", "Accept" to "application/json"))
        return try { ir(false) } catch (e: HttpFallo) { if (e.codigo == 401) ir(true) else throw e }
    }

    /** Las duraciones según IGDB para un título, o null. Bloqueante (red). */
    private fun duracionesIgdb(c: Context, k: Servicios.Claves, titulo: String): Duraciones? {
        val juegos = JSONArray(igdb(c, k, "games", "search \"${titulo.replace("\"", "")}\"; fields id,name; limit 10;"))
        if (juegos.length() == 0) return null
        val lista = (0 until juegos.length()).map { juegos.getJSONObject(it) }
        val g = lista.firstOrNull { Catalogo.norm(it.optString("name")) == Catalogo.norm(titulo) } ?: lista.first()
        return duracionesDeIgdb(igdb(c, k, "game_time_to_beats", "fields hastily,normally,completely,count; where game_id = ${g.optLong("id")};"), g.optString("name"))
    }

    /**
     * Pide los datos del juego (si hacen falta) y, si no tiene duraciones, las busca y se las pone. Devuelve los datos (o null si nada).
     * Bloqueante (red); los errores de red se tragan (se reintenta más tarde).
     */
    suspend fun pedir(c: Context, j: Juego, forzar: Boolean = false): DatosJuego? = withContext(Dispatchers.IO) {
        cargar(c)
        if (!activo) return@withContext de[j.id]
        if (!forzar && !hacenFalta(j.id)) return@withContext de[j.id]
        synchronized(pidiendo) { if (!pidiendo.add(j.id)) return@withContext de[j.id] }
        try {
            val k = Servicios.claves(c)
            val ahora = System.currentTimeMillis()
            var d: DatosJuego? = null
            var rawgId: Long? = null
            // 1) La tienda de Steam
            appidDe(j)?.let { appid -> d = runCatching { deSteam(get("https://store.steampowered.com/api/appdetails?appids=$appid&l=spanish&cc=ES"), appid, ahora) }.getOrNull() }
            // 2) RAWG
            if (k.rawg.isNotBlank()) {
                rawgId = runCatching { idDeRawg(get("https://api.rawg.io/api/games?key=${enc(k.rawg)}&search=${enc(j.titulo)}&page_size=5"), j.titulo) }.getOrNull()
                if (d == null && rawgId != null) d = runCatching { deRawg(get("https://api.rawg.io/api/games/$rawgId?key=${enc(k.rawg)}"), ahora) }.getOrNull()
            }
            // 3) Lo que suele durar, si el juego no lo tiene
            var duracionDe = de[j.id]?.duracionDe.orEmpty()
            val actual = Biblioteca.por(j.id) ?: j
            if (actual.durHistoria == 0 && actual.durExtras == 0 && actual.durCompleto == 0) {
                var dur: Duraciones? = null
                if (k.igdbId.isNotBlank() && k.igdbSecret.isNotBlank()) dur = runCatching { duracionesIgdb(c, k, j.titulo) }.getOrNull()
                if (dur == null && rawgId != null) runCatching { horasDeRawg(get("https://api.rawg.io/api/games/$rawgId?key=${enc(k.rawg)}")) }.getOrNull()?.takeIf { it > 0 }?.let { dur = Duraciones(it, 0, 0, "RAWG · media de horas de sus jugadores") }
                dur?.let { x -> Biblioteca.cambiar(j.id) { it.copy(durHistoria = x.historia, durExtras = x.extras, durCompleto = x.completo) }; duracionDe = x.de }
            }
            val fin = (d ?: DatosJuego(at = ahora, nada = true)).copy(duracionDe = duracionDe, at = ahora)
            poner(j.id, if (fin.vacios && !fin.nada) fin.copy(nada = true) else fin)
            de[j.id]
        } finally { synchronized(pidiendo) { pidiendo.remove(j.id) } }
    }
}
