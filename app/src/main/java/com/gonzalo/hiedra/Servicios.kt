package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/*
 * Los servicios de Atrio que Hiedra hereda: las portadas de SteamGridDB y las horas de Steam. Las claves (API key de SteamGridDB, clave y
 * perfil de Steam) se cogen solas de lo que Atrio subió a tu cuenta («keys» en los datos de cada dispositivo); también se pueden poner a mano en
 * Ajustes → Servicios (lo de aquí manda). Copiado de Atrio (Api.kt e Imports.kt).
 */

class Hit(val id: Long, val name: String, val sub: String)

object Sgdb {
    private const val BASE = "https://www.steamgriddb.com/api/v2"

    private fun get(path: String, key: String): JSONArray {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.setRequestProperty("Authorization", "Bearer $key")
        c.connectTimeout = 15000; c.readTimeout = 15000
        if (c.responseCode !in 200..299) error("SteamGridDB respondió ${c.responseCode}. Revisa la API key (Ajustes → Cuenta → Servicios).")
        return JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("data")
    }

    /** Busca juegos (con el año, si SteamGridDB lo da). */
    suspend fun buscar(q: String, key: String): List<Hit> = withContext(Dispatchers.IO) {
        val term = URLEncoder.encode(q.trim(), "UTF-8").replace("+", "%20")
        val a = get("/search/autocomplete/$term", key)
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            val rel = o.optLong("release_date", 0L)
            Hit(o.getLong("id"), o.getString("name"), if (rel > 0L) java.time.Instant.ofEpochSecond(rel).atZone(java.time.ZoneOffset.UTC).year.toString() else "")
        }
    }

    /** Portadas (600×900) de un juego: pares (miniatura, imagen entera). */
    suspend fun portadas(id: Long, key: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val a = get("/grids/game/$id?dimensions=600x900", key)
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.getString("thumb") to o.getString("url") } }
    }
}

object Steam {
    class Owned(val name: String, val minutes: Int)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        val code = c.responseCode
        if (code == 401 || code == 403) throw IllegalArgumentException("la clave de Steam no es válida")
        if (code !in 200..299) error("Steam respondió $code")
        return c.inputStream.bufferedReader().readText()
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** El SteamID64 de un perfil: el número, la URL del perfil o el nombre personalizado. */
    suspend fun steamId(user: String, key: String): String = withContext(Dispatchers.IO) {
        val tail = user.trim().trimEnd('/').substringAfterLast("/")
        if (tail.length == 17 && tail.all { it.isDigit() }) return@withContext tail
        val o = JSONObject(get("https://api.steampowered.com/ISteamUser/ResolveVanityURL/v1/?key=${enc(key)}&vanityurl=${enc(tail)}")).optJSONObject("response")
        if (o?.optInt("success") != 1) throw IllegalArgumentException("no encuentro el perfil «$tail»")
        o.optString("steamid")
    }

    /** Lo que has jugado en Steam: nombre y minutos. Sin red: [deJson] se puede probar. */
    suspend fun jugados(user: String, key: String): List<Owned> {
        val id = steamId(user, key)
        return withContext(Dispatchers.IO) { deJson(get("https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/?key=${enc(key)}&steamid=$id&include_appinfo=1&include_played_free_games=1&format=json")) }
    }

    fun deJson(txt: String): List<Owned> {
        val arr = JSONObject(txt).optJSONObject("response")?.optJSONArray("games") ?: throw IllegalArgumentException("tu biblioteca es privada: en Steam, Perfil → Privacidad → Detalles del juego: Público")
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { Owned(it.optString("name"), it.optInt("playtime_forever", 0)) }.filter { it.name.isNotBlank() }
    }

    /**
     * Pone las horas de Steam en los juegos de Hiedra que se llamen igual, como de un dispositivo más («steam»). Los juegos del PC no las
     * reciben: Atrio del PC ya cuenta Steam en sus minutos (si no, se sumarían dos veces). Devuelve cuántos juegos han cambiado.
     */
    fun aplicar(jugados: List<Owned>): Int {
        var n = 0
        val porNombre = jugados.filter { it.minutes > 0 }.associateBy { Catalogo.norm(it.name) }
        for (j in Biblioteca.juegos.toList()) {
            // Los del PC ya traen Steam en los minutos de Atrio del PC: nada se suma dos veces
            if (j.enPc || j.externas.keys.any { Fuentes.esPc(it) }) continue
            val o = porNombre[Catalogo.norm(j.titulo)] ?: continue
            val ms = o.minutes * 60_000L
            if (j.externa(Fuentes.STEAM).ms >= ms) continue
            Biblioteca.poner(j.copy(externas = j.externas + (Fuentes.STEAM to j.externa(Fuentes.STEAM).copy(ms = ms)))); n++
        }
        return n
    }
}

/**
 * Las claves de los servicios: las de Ajustes mandan; si faltan, las que Atrio subió a tu cuenta («keys» en sus datos: SteamGridDB, Steam, RAWG
 * e IGDB). Se traen al vincular la cuenta y en cada sincronización a mano. El token de kDrive NO viene por aquí: lo que hay en tu cuenta lo puede
 * leer tu pareja (modo pareja), y el token da acceso a todos tus archivos; se pega en cada app, como en Atrio.
 */
object Servicios {
    class Claves(val sgdb: String = "", val steamKey: String = "", val steamUser: String = "", val rawg: String = "", val igdbId: String = "", val igdbSecret: String = "")
    private val NOMBRES = listOf("sgdb", "steamKey", "steamUser", "rawg", "igdbId", "igdbSecret")
    private fun prefs(c: Context) = c.getSharedPreferences("hiedra_servicios", Context.MODE_PRIVATE)
    private fun lee(c: Context, prefijo: String): Claves {
        val p = prefs(c)
        fun v(k: String) = p.getString(prefijo + k, "").orEmpty()
        return Claves(v("sgdb"), v("steamKey"), v("steamUser"), v("rawg"), v("igdbId"), v("igdbSecret"))
    }

    /** Las que se usan: las tuyas y, donde falten, las de Atrio. */
    fun claves(c: Context): Claves {
        val m = propias(c); val a = deAtrio(c)
        return Claves(m.sgdb.ifBlank { a.sgdb }, m.steamKey.ifBlank { a.steamKey }, m.steamUser.ifBlank { a.steamUser }, m.rawg.ifBlank { a.rawg }, m.igdbId.ifBlank { a.igdbId }, m.igdbSecret.ifBlank { a.igdbSecret })
    }
    fun propias(c: Context): Claves = lee(c, "")
    fun deAtrio(c: Context): Claves = lee(c, "a_")
    fun poner(c: Context, k: Claves) { prefs(c).edit().putString("sgdb", k.sgdb.trim()).putString("steamKey", k.steamKey.trim()).putString("steamUser", k.steamUser.trim()).putString("rawg", k.rawg.trim()).putString("igdbId", k.igdbId.trim()).putString("igdbSecret", k.igdbSecret.trim()).apply() }
    /** Cuándo se miraron por última vez las de Atrio (0 = nunca). */
    fun ultimaAtrio(c: Context) = prefs(c).getLong("atrio_at", 0L)

    /** Las claves que hay en los datos de Atrio («keys»; de varios dispositivos, la primera que tenga cada una). Sin red: se puede probar. */
    fun deBlobs(blobs: List<JSONObject>): Claves {
        val v = HashMap<String, String>()
        for (b in blobs) b.optJSONObject("keys")?.let { o -> for (k in NOMBRES) if (v[k].isNullOrBlank()) v[k] = o.optString(k).trim() }
        return Claves(v["sgdb"].orEmpty(), v["steamKey"].orEmpty(), v["steamUser"].orEmpty(), v["rawg"].orEmpty(), v["igdbId"].orEmpty(), v["igdbSecret"].orEmpty())
    }

    /** Guarda las de Atrio (sin tocar las tuyas). Sin red: se puede probar. */
    fun aplicarDeAtrio(c: Context, k: Claves) {
        prefs(c).edit().putString("a_sgdb", k.sgdb).putString("a_steamKey", k.steamKey).putString("a_steamUser", k.steamUser).putString("a_rawg", k.rawg).putString("a_igdbId", k.igdbId).putString("a_igdbSecret", k.igdbSecret)
            .putLong("atrio_at", System.currentTimeMillis()).apply()
    }

    /** Baja las claves de Atrio y las guarda (sin tocar las tuyas). Devuelve si ha podido leerlas. Bloqueante (red). */
    fun refrescarDeAtrio(c: Context): Boolean {
        val b = runCatching { ImportarAtrio.blobs() }.getOrNull() ?: return false
        aplicarDeAtrio(c, deBlobs(b)); return true
    }

    fun ultimaSteam(c: Context) = prefs(c).getLong("steam_at", 0L)
    fun anotaSteam(c: Context) { prefs(c).edit().putLong("steam_at", System.currentTimeMillis()).apply() }

    /** Trae las horas de Steam y las pone. Devuelve el mensaje para la interfaz. Bloqueante (red). */
    suspend fun traerHorasSteam(c: Context): String {
        val k = claves(c)
        if (k.steamKey.isBlank() || k.steamUser.isBlank()) return "Faltan la clave y el perfil de Steam: ponlos aquí abajo, o en Atrio y sincroniza."
        return try {
            val n = Steam.aplicar(Steam.jugados(k.steamUser, k.steamKey)); anotaSteam(c)
            if (n == 0) "Horas de Steam al día (ningún juego ha cambiado)." else "Horas de Steam puestas en $n ${if (n == 1) "juego" else "juegos"}."
        } catch (e: IllegalArgumentException) { "Steam: ${e.message}" } catch (e: Exception) { "No he podido hablar con Steam: ${e.message}" }
    }
}

/** Buscar una portada en SteamGridDB: escribes el nombre, eliges el juego y una de sus portadas (600×900). */
@Composable
fun DialogoSgdb(j: Juego, alCerrar: () -> Unit, alElegir: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = remember { Servicios.claves(ctx).sgdb }
    var q by remember { mutableStateOf(j.titulo) }
    var hits by remember { mutableStateOf<List<Hit>>(emptyList()) }
    var elegido by remember { mutableStateOf<Hit?>(null) }
    var portadas by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var ocupado by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    fun buscar() { if (key.isBlank() || q.isBlank()) return; ocupado = true; error = ""; scope.launch { runCatching { Sgdb.buscar(q, key) }.onSuccess { hits = it; if (it.isEmpty()) error = "Nada con ese nombre." }.onFailure { error = it.message ?: "error" }; ocupado = false } }
    fun elegir(h: Hit) { elegido = h; ocupado = true; error = ""; scope.launch { runCatching { Sgdb.portadas(h.id, key) }.onSuccess { portadas = it; if (it.isEmpty()) error = "Ese juego no tiene portadas en SteamGridDB." }.onFailure { error = it.message ?: "error" }; ocupado = false } }
    LaunchedEffect(Unit) { buscar() }
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            RevistaTopBar({ if (elegido != null) { elegido = null; portadas = emptyList() } else alCerrar() }, if (elegido != null) "Juegos" else "Cerrar")
            Filete(3.dp)
            Antetitulo(elegido?.name ?: "SteamGridDB", Modifier.padding(top = 12.dp), color = Acento, conCuadro = false)
            if (key.isBlank()) {
                Text("Falta la API key de SteamGridDB.", color = Charcoal, fontFamily = Display, fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp))
                Cuerpo("Se genera en tu perfil de steamgriddb.com (Preferencias → API). Ponla en Ajustes → Cuenta → Servicios; si ya la tienes en Atrio, se coge sola al vincular la cuenta.", modifier = Modifier.padding(top = 6.dp))
                return@Column
            }
            if (elegido == null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Box(Modifier.weight(1f)) { CampoRevista("Buscar", q, "nombre del juego") { q = it } }
                    EnlaceRevista("Buscar →", acento = true) { buscar() }
                }
            }
            if (ocupado) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Acento) }
            if (error.isNotBlank()) Text(error, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            if (elegido == null) Column(Modifier.verticalScroll(rememberScrollState())) {
                hits.forEach { h ->
                    Row(Modifier.fillMaxWidth().clickable { elegir(h) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(h.name, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                        if (h.sub.isNotBlank()) Text(h.sub, color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    Filete(1.dp, Line)
                }
            } else LazyVerticalGrid(GridCells.Adaptive(100.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
                items(portadas, key = { it.second }) { (thumb, url) ->
                    AsyncImage(thumb, null, Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(esquina(3.dp)).background(Sand).clickable { alElegir(url) }, contentScale = ContentScale.Crop)
                }
            }
        }
    }
}

/** Ajustes → Servicios: las claves (con lo que Atrio tiene, si lo hay) y traer las horas de Steam. */
@Composable
fun SeccionServicios() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var atrio by remember { mutableStateOf(Servicios.deAtrio(ctx)) }
    var p by remember { mutableStateOf(Servicios.propias(ctx)) }
    var msg by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var mirando by remember { mutableStateOf(false) }
    fun guarda(n: Servicios.Claves) { p = n; Servicios.poner(ctx, n) }
    // Al abrir Ajustes, si hay cuenta, se miran las de Atrio (por si las has cambiado allí)
    LaunchedEffect(Unit) { if (Cuenta.vinculada) { mirando = true; withContext(Dispatchers.IO) { runCatching { Servicios.refrescarDeAtrio(ctx) } }; atrio = Servicios.deAtrio(ctx); mirando = false } }
    /** Qué hay de cada clave: la tuya, la de Atrio o ninguna. */
    fun estado(mia: String, deAtrio: String) = when { mia.isNotBlank() -> "la tuya"; deAtrio.isNotBlank() -> "la de Atrio"; else -> "ninguna" }
    val resumen = listOf("SteamGridDB" to estado(p.sgdb, atrio.sgdb), "Steam" to estado(p.steamKey, atrio.steamKey) + (if (p.steamUser.isBlank() && atrio.steamUser.isBlank()) ", sin perfil" else ""),
        "RAWG" to estado(p.rawg, atrio.rawg), "IGDB" to estado(p.igdbId, atrio.igdbId), "kDrive" to (if (KDrive.on(ctx)) "conectado" else "sin conectar (en «Avanzado»)"))
    Cuerpo("SteamGridDB da las portadas; Steam, tus horas; RAWG e IGDB, los datos de cada juego (fecha, estudio, géneros, lo que suele durar). Lo que pongas aquí manda; si lo dejas vacío, se usa lo que tengas en Atrio (viene con tu cuenta).", tam = 12)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp)) { resumen.forEach { (n, v) -> MagFact(n, v) } }
    if (mirando) Text("Mirando las de Atrio…", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp)
    else if (Cuenta.vinculada && Servicios.ultimaAtrio(ctx) == 0L) Text("Todavía no se han leído las de Atrio: sincroniza (arriba).", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp)
    CampoRevista("SteamGridDB", p.sgdb, if (atrio.sgdb.isNotBlank()) "la de Atrio" else "API key (steamgriddb.com → Preferencias → API)") { guarda(p.copia(sgdb = it)) }
    CampoRevista("Steam", p.steamKey, if (atrio.steamKey.isNotBlank()) "la de Atrio" else "clave de Steam (steamcommunity.com/dev/apikey)", secreto = true) { guarda(p.copia(steamKey = it)) }
    CampoRevista("Perfil", p.steamUser, if (atrio.steamUser.isNotBlank()) "el de Atrio" else "URL del perfil, nombre o SteamID64") { guarda(p.copia(steamUser = it)) }
    CampoRevista("RAWG", p.rawg, if (atrio.rawg.isNotBlank()) "la de Atrio" else "API key (rawg.io/apidocs)") { guarda(p.copia(rawg = it)) }
    CampoRevista("IGDB (Client ID)", p.igdbId, if (atrio.igdbId.isNotBlank()) "el de Atrio" else "Client ID de Twitch (dev.twitch.tv)") { guarda(p.copia(igdbId = it)) }
    CampoRevista("IGDB (secreto)", p.igdbSecret, if (atrio.igdbSecret.isNotBlank()) "el de Atrio" else "secreto de esa aplicación", secreto = true) { guarda(p.copia(igdbSecret = it)) }
    Filete(1.dp, Line)
    MagLink(if (ocupado) "Un momento…" else "Traer las horas de Steam", "Las pone en los juegos de Hiedra que se llamen igual (los del PC ya las traen de Atrio). La biblioteca de Steam tiene que ser pública.") {
        if (ocupado) return@MagLink
        ocupado = true; msg = "Hablando con Steam…"
        scope.launch { msg = withContext(Dispatchers.IO) { Servicios.traerHorasSteam(ctx) }; ocupado = false }
    }
    if (msg.isNotBlank()) Text(msg, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
}

/** Una copia con algún campo cambiado (como copy() de un data class, para las claves). */
fun Servicios.Claves.copia(sgdb: String = this.sgdb, steamKey: String = this.steamKey, steamUser: String = this.steamUser, rawg: String = this.rawg, igdbId: String = this.igdbId, igdbSecret: String = this.igdbSecret) =
    Servicios.Claves(sgdb, steamKey, steamUser, rawg, igdbId, igdbSecret)
