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
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File

/**
 * Traer tus juegos de Atrio (lo que no esté ya en Hiedra). Atrio sube sus datos a tu cuenta (/users/<uid>/dev/<dispositivo>/data); Hiedra los LEE
 * (nada se cambia en Atrio) y crea de cada juego su ficha: título, plataforma, estado (jugando, pendiente, pasado, abandonado), horas, cuándo lo
 * pasaste, favorito, lo que suele durar, la portada (de internet o la foto tuya de tu kDrive) y las sagas de juegos. Las horas que vienen de Atrio
 * cuentan como de este dispositivo. Lo que ya tienes con el mismo nombre no se duplica: se completa, y se queda con la clave de Atrio
 * (`juego:<título>`) entre sus claves (así Azabache sigue enlazando su valoración). Se puede repetir.
 */
object ImportarAtrio {
    class Hallazgo(val candidatos: List<Candidato>, val sagas: List<Saga>)

    /** [portadaUrl]: una dirección de internet; [foto]: una foto tuya en tu kDrive (su id y su nombre). */
    class Candidato(val atrioId: Long, val juego: Juego, val portadaUrl: String = "", val foto: Pair<Long, String>? = null)

    private fun cambio(o: JSONObject) = maxOf(o.optLong("updated", 0L), o.optLong("sv", 0L))

    /** Los candidatos a partir de lo que subieron los dispositivos de Atrio (los datos de cada uno). Sin red: se puede probar. */
    fun candidatos(blobs: List<JSONObject>): List<Candidato> {
        val tombs = HashMap<String, Long>()
        blobs.forEach { b -> b.optJSONObject("tombs")?.let { t -> for (k in t.keys()) tombs[k] = maxOf(tombs[k] ?: 0L, t.optLong(k)) } }
        fun lista(nombre: String): Map<Long, JSONObject> {
            val out = HashMap<Long, JSONObject>()
            for (b in blobs) b.optJSONArray(nombre)?.let { a ->
                for (i in 0 until a.length()) {
                    val o = a.optJSONObject(i) ?: continue
                    val id = o.optLong("id"); if (id == 0L) continue
                    val t = tombs["$nombre:$id"]
                    if (t != null && t >= cambio(o)) continue
                    val cur = out[id]
                    if (cur == null || cambio(o) > cambio(cur)) out[id] = o
                }
            }
            return out
        }
        val valorados = lista("games"); val enCurso = lista("plays")
        val fotos = HashMap<String, JSONObject>()
        for (b in blobs) b.optJSONArray("nube")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val k = o.optString("k"); if (!k.startsWith("foto:")) continue
                val cur = fotos[k]; if (cur == null || o.optLong("updated") > cur.optLong("updated")) fotos[k] = o
            }
        }
        val ahora = System.currentTimeMillis()
        return (valorados.keys + enCurso.keys).mapNotNull { id ->
            val g = valorados[id]; val p = enCurso[id]
            val titulo = (g?.optString("name") ?: p?.optString("name")).orEmpty().trim()
            if (titulo.isBlank()) return@mapNotNull null
            val pst = p?.optInt("status", 0) ?: -1
            val estado = when { pst == 1 -> Estado.JUGANDO; g != null -> Estado.PASADO; pst == 2 -> Estado.ABANDONADO; else -> Estado.PENDIENTE }
            val inicio = listOfNotNull(g?.optLong("start"), p?.optLong("start")).filter { it > 0 }.minOrNull() ?: 0L
            val fin = g?.optLong("end") ?: 0L
            val minutos = maxOf(g?.optInt("minutes") ?: 0, p?.optInt("minutes") ?: 0)
            val plataforma = (g?.optString("platform").orEmpty().ifBlank { p?.optString("platform").orEmpty() }).trim()
            val cover = g?.optString("cover").orEmpty().ifBlank { p?.optString("cover").orEmpty() }
            fun ttb(k: String) = maxOf(g?.optInt(k) ?: 0, p?.optInt(k) ?: 0)
            val juego = nuevoJuego(titulo, plataforma, anadido = if (inicio > 0) inicio else maxOf(g?.optLong("updated") ?: 0L, p?.optLong("updated") ?: 0L).takeIf { it > 0 } ?: ahora).copy(
                estado = estado, finalizado = if (estado == Estado.PASADO) fin else 0L, favorito = g?.optBoolean("fav") ?: false,
                propias = Horas(ms = minutos * 60_000L), ultima = maxOf(fin, inicio),
                durHistoria = ttb("ttbMain"), durExtras = ttb("ttbExtra"), durCompleto = ttb("ttbFull"),
                otrasClaves = listOf(claveFicha(titulo))
            )
            val foto = cover.takeIf { it.startsWith("file:") }?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                ?.let { n -> fotos["foto:$n"]?.let { it.optLong("f") to it.optString("n").ifBlank { n } } }?.takeIf { it.first > 0 }
            Candidato(id, juego, portadaUrl = cover.takeIf { it.startsWith("http") }.orEmpty(), foto = foto)
        }.sortedBy { Catalogo.norm(it.juego.titulo) }
    }

    /** Las sagas de juegos de Atrio, con sus juegos en orden (por el id del candidato; al importar se cambia por el del juego de aquí). */
    fun sagasDe(blobs: List<JSONObject>, cands: List<Candidato>): List<Saga> {
        val tombs = HashMap<String, Long>()
        blobs.forEach { b -> b.optJSONObject("tombs")?.let { t -> for (k in t.keys()) tombs[k] = maxOf(tombs[k] ?: 0L, t.optLong(k)) } }
        val porId = HashMap<Long, JSONObject>()
        for (b in blobs) b.optJSONArray("sagas")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                val id = o.optLong("id"); if (id == 0L || o.optInt("kind", 1) != 0) continue
                val t = tombs["sagas:$id"]
                if (t != null && t >= cambio(o)) continue
                val cur = porId[id]; if (cur == null || cambio(o) > cambio(cur)) porId[id] = o
            }
        }
        val idDe = cands.associate { it.atrioId to it.juego.id }
        return porId.values.mapNotNull { o ->
            val items = o.optJSONArray("items") ?: return@mapNotNull null
            val ids = (0 until items.length()).mapNotNull { idDe[items.optLong(it)] }.distinct()
            val nombre = o.optString("name").trim()
            if (ids.isEmpty() || nombre.isBlank()) null else Saga("atrio${o.optLong("id")}", nombre, ids)
        }.sortedBy { Catalogo.norm(it.nombre) }
    }

    /** Lo último que Atrio subió a tu cuenta (de sus dispositivos más recientes). Bloqueante (red). */
    fun leer(): Hallazgo? = blobs()?.let { b -> candidatos(b).let { Hallazgo(it, sagasDe(b, it)) } }

    /** Los datos que subieron los dispositivos más recientes de Atrio (uno por dispositivo), o null si no hay. Bloqueante (red). */
    /** Lo último bajado, con los sellos de entonces: si Azabache no ha subido nada nuevo, no se vuelve a bajar (son blobs grandes). */
    @Volatile private var cache: Pair<String, List<JSONObject>>? = null
    fun olvidarCache() { cache = null }

    fun blobs(): List<JSONObject>? {
        if (!Cuenta.vinculada) return null
        val st = Cuenta.db("GET", "/users/${Cuenta.uid}/stamps", null)?.let { JSONObject(it) } ?: return null
        val devs = st.keys().asSequence().map { it to st.optLong(it) }.sortedByDescending { it.second }.take(4).toList()
        val llave = "${Cuenta.uid}|" + devs.joinToString(",") { "${it.first}=${it.second}" }
        cache?.let { (k, b) -> if (k == llave) return b }
        val blobs = ArrayList<JSONObject>()
        for ((dev, _) in devs) {
            val raw = Cuenta.db("GET", "/users/${Cuenta.uid}/dev/$dev/data", null) ?: continue
            val datos = (JSONTokener(raw).nextValue() as? String) ?: continue
            runCatching { JSONObject(datos) }.getOrNull()?.let { blobs += it }
        }
        return blobs.ifEmpty { null }.also { if (it != null) cache = llave to it }
    }

    /** El juego de Hiedra que ya es este candidato: por su clave de ficha o porque se llama igual. */
    fun existente(c: Candidato): Juego? = Biblioteca.porClave(claveFicha(c.juego.titulo)) ?: Biblioteca.porTitulo(c.juego.titulo)

    /** Completa un juego que ya tenías con lo que trae Atrio (sin pisar lo que ya tenga). Devuelve el mismo si nada cambia. */
    fun completar(previo: Juego, c: Candidato): Juego {
        var n = previo
        val a = c.juego
        val peso = mapOf(Estado.PENDIENTE to 0, Estado.ABANDONADO to 1, Estado.JUGANDO to 2, Estado.PASADO to 3)
        if ((peso[a.estado] ?: 0) > (peso[previo.estado] ?: 0)) n = n.copy(estado = a.estado, finalizado = if (a.estado == Estado.PASADO) a.finalizado else 0L)
        else if (n.estado == Estado.PASADO && n.finalizado == 0L && a.finalizado > 0) n = n.copy(finalizado = a.finalizado)
        if (previo.plataforma.isBlank() && a.plataforma.isNotBlank()) n = n.copy(plataforma = a.plataforma)
        if (previo.msJugados == 0L && a.propias.ms > 0L) n = n.copy(propias = a.propias)
        if (a.favorito && !previo.favorito) n = n.copy(favorito = true)
        if (previo.durHistoria == 0 && a.durHistoria > 0) n = n.copy(durHistoria = a.durHistoria, durExtras = a.durExtras, durCompleto = a.durCompleto)
        val k = claveFicha(a.titulo)
        if (k !in previo.claves) n = n.copy(otrasClaves = previo.otrasClaves + k)
        if (previo.anadido == 0L || (a.anadido in 1 until previo.anadido)) n = n.copy(anadido = a.anadido)
        return n
    }

    class Resultado(val nuevos: Int, val yaEstaban: Int, val portadas: Int, val sagas: Int = 0)

    /** Pone los candidatos en Hiedra. Bloqueante (red: las portadas). Se puede repetir sin duplicar. */
    fun importar(ctx: Context, cands: List<Candidato>, avance: (Int, Int) -> Unit = { _, _ -> }, sagas: List<Saga> = emptyList()): Resultado {
        var nuevos = 0; var ya = 0; var portadas = 0
        // El id del candidato → el id del juego que se queda (el nuevo o el que ya tenías)
        val idFinal = HashMap<String, String>()
        cands.forEachIndexed { i, c ->
            avance(i, cands.size)
            val previo = existente(c)
            val juego = if (previo == null) { nuevos++; c.juego.also { Biblioteca.poner(it) } } else { ya++; completar(previo, c).also { if (it != previo) Biblioteca.poner(it) } }
            idFinal[c.juego.id] = juego.id
            // La portada: solo si el juego no tiene ninguna puesta
            if (juego.portada.isBlank() || !File(juego.portada).exists()) runCatching { bajarPortada(ctx, juego, c) }.getOrNull()?.let { ruta ->
                Biblioteca.por(juego.id)?.let { Biblioteca.poner(it.copy(portada = ruta)); portadas++ }
            }
        }
        var nSagas = 0
        for (s in sagas) {
            val ids = s.juegos.mapNotNull { idFinal[it] }.distinct()
            if (ids.isEmpty()) continue
            val aqui = Sagas.por(s.id)
            if (aqui == null) { Sagas.poner(s.copy(juegos = ids)); nSagas++ }
            else {
                val nuevas = ids.filter { Sagas.de(it) == null }
                if (nuevas.isNotEmpty()) Sagas.poner(aqui.copy(juegos = aqui.juegos + nuevas))
            }
        }
        avance(cands.size, cands.size)
        return Resultado(nuevos, ya, portadas, nSagas)
    }

    /** Baja la portada del candidato (de internet, o la foto tuya de tu kDrive) a la carpeta de portadas. null si no hay o no se pudo. */
    private fun bajarPortada(ctx: Context, j: Juego, c: Candidato): String? {
        val dir = File(ctx.filesDir, "portadas").apply { mkdirs() }
        val foto = c.foto
        if (foto != null && KDrive.on(ctx)) {
            val dest = File(dir, "${j.id}-${System.currentTimeMillis()}.jpg")
            return try { KDrive.download(ctx, foto.first, dest); if (dest.length() > 200) dest.absolutePath else { dest.delete(); null } } catch (e: Exception) { dest.delete(); null }
        }
        if (c.portadaUrl.isNotBlank()) {
            val (bytes, _) = Internet.descargar(c.portadaUrl, "Mozilla/5.0 (Linux; Android 14) Hiedra")
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
            if (o.outWidth <= 0) return null
            val dest = File(dir, "${j.id}-${System.currentTimeMillis()}.jpg")
            dest.writeBytes(bytes)
            return dest.absolutePath
        }
        return null
    }
}

/** El enlace de Ajustes: lee lo que Atrio tiene en tu cuenta, te dice qué hay y lo trae. */
@Composable
fun SeccionImportarAtrio() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var estado by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var hallados by remember { mutableStateOf<ImportarAtrio.Hallazgo?>(null) }
    MagLink(if (ocupado) "Un momento…" else "Traer mis juegos de Atrio", "Solo lee tu cuenta: no cambia nada en Atrio. Trae fichas, estado, horas, portadas y sagas de juegos") {
        if (ocupado) return@MagLink
        if (!Cuenta.vinculada) { estado = "Primero vincula tu cuenta (arriba)."; return@MagLink }
        ocupado = true; estado = "Leyendo lo que Atrio tiene en tu cuenta…"
        ambito.launch {
            val r = withContext(Dispatchers.IO) { runCatching { ImportarAtrio.leer() } }
            ocupado = false
            r.onSuccess { l -> if (l == null || l.candidatos.isEmpty()) estado = "No he encontrado juegos de Atrio en tu cuenta." else { hallados = l; estado = "" } }
                .onFailure { estado = it.message ?: "No he podido leer tu cuenta." }
        }
    }
    if (estado.isNotBlank()) Text(estado, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
    hallados?.let { h ->
        val l = h.candidatos
        val n = { e: Estado -> l.count { it.juego.estado == e } }
        val yaEstan = l.count { ImportarAtrio.existente(it) != null }
        AlertDialog(
            onDismissRequest = { hallados = null }, containerColor = Paper, shape = esquina(4.dp),
            title = { Text("${l.size} juegos en Atrio", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${n(Estado.PASADO)} pasados, ${n(Estado.JUGANDO)} jugando, ${n(Estado.PENDIENTE)} pendientes, ${n(Estado.ABANDONADO)} abandonados" +
                        (if (h.sagas.isNotEmpty()) " y ${h.sagas.size} ${if (h.sagas.size == 1) "saga" else "sagas"}" else "") + ". " +
                        (if (yaEstan > 0) "$yaEstan ya los tienes aquí: se completan (estado, horas, plataforma) sin duplicarse. " else "") +
                        "Los que no se abren desde el móvil quedan como fichas (PC, consola…): cuando añadas su ROM, su app o su web, el juego gana esa manera de abrirse.",
                    color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cands = l; val sagasAImportar = h.sagas; hallados = null; ocupado = true; estado = "Importando…"
                    ambito.launch {
                        val r = withContext(Dispatchers.IO) { runCatching { ImportarAtrio.importar(ctx, cands, { i, t -> estado = "Importando $i de $t…" }, sagasAImportar) } }
                        ocupado = false
                        estado = r.fold({ "Hecho: ${it.nuevos} juegos nuevos, ${it.yaEstaban} que ya tenías, ${it.portadas} portadas" + (if (it.sagas > 0) " y ${it.sagas} sagas." else ".") }, { e -> e.message ?: "No he podido importar." })
                    }
                }) { Text("Importar", color = Acento, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { hallados = null }) { Text("Cancelar", color = Muted) } }
        )
    }
}
