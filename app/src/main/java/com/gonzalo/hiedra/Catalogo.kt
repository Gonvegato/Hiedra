package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONObject
import java.text.Normalizer

/** Un sistema con lo que Hiedra sabe de él: nombre corto, color y carpeta de portadas de libretro. */
class Sistema(
    val id: String, val nombre: String, val corto: String, val color: Color,
    /** Carpeta de https://thumbnails.libretro.com/ (null = no hay portadas automáticas para este sistema). */
    val libretro: String?,
    val extensiones: Set<String>,
    val comandos: List<Pair<String, String>>,
    val alias: Set<String>,
)

/**
 * El catálogo de emuladores y comandos de lanzamiento. Sale de ES-DE (ES-DE Frontend, licencia MIT):
 * assets/emulators.json se generó a partir de su es_systems.xml y es_find_rules.xml de Android, sin tocar los comandos.
 */
object Catalogo {
    /** Nombre del emulador en las reglas (RETROARCH, MELONDS…) → "paquete/actividad" posibles, por orden. */
    var emus: Map<String, List<String>> = emptyMap()
    var sistemas: List<Sistema> = emptyList()
    private var porId: Map<String, Sistema> = emptyMap()
    private var porExt: Map<String, List<Sistema>> = emptyMap()

    private class Meta(val corto: String, val color: Long, val libretro: String?, val alias: List<String> = emptyList())
    // Los sistemas «de casa»: con color, nombre corto y portadas. (Las carpetas de libretro están comprobadas en su web.)
    private val META = mapOf(
        "gba" to Meta("GBA", 0xFF6C4DD6, "Nintendo - Game Boy Advance", listOf("gameboyadvance", "advance")),
        "nds" to Meta("NDS", 0xFF2E86DE, "Nintendo - Nintendo DS", listOf("nintendods", "ds")),
        "gb" to Meta("GB", 0xFF8FA33A, "Nintendo - Game Boy", listOf("gameboy")),
        "gbc" to Meta("GBC", 0xFFD9487A, "Nintendo - Game Boy Color", listOf("gameboycolor", "gameboycolour")),
        "nes" to Meta("NES", 0xFFC8372D, "Nintendo - Nintendo Entertainment System", listOf("famicom", "nintendo")),
        "snes" to Meta("SNES", 0xFF8A7FB8, "Nintendo - Super Nintendo Entertainment System", listOf("supernintendo", "superfamicom", "sfc")),
        "n64" to Meta("N64", 0xFF2FA66A, "Nintendo - Nintendo 64", listOf("nintendo64")),
        "n3ds" to Meta("3DS", 0xFFE0603A, "Nintendo - Nintendo 3DS", listOf("3ds", "nintendo3ds")),
        "gc" to Meta("GC", 0xFF6B5BCE, "Nintendo - GameCube", listOf("gamecube", "ngc")),
        "wii" to Meta("Wii", 0xFF3FB6D9, "Nintendo - Wii", listOf("nintendowii")),
        "megadrive" to Meta("MD", 0xFF4A5A8A, "Sega - Mega Drive - Genesis", listOf("genesis", "segagenesis", "mastersystem1", "md")),
        "mastersystem" to Meta("SMS", 0xFFD6452F, "Sega - Master System - Mark III", listOf("sms", "segamastersystem")),
        "gamegear" to Meta("GG", 0xFF3C7DD9, "Sega - Game Gear", listOf("gg", "segagamegear")),
        "saturn" to Meta("SAT", 0xFF5A6C8C, "Sega - Saturn", listOf("segasaturn")),
        "dreamcast" to Meta("DC", 0xFFF28C28, "Sega - Dreamcast", listOf("segadreamcast", "dc")),
        "psx" to Meta("PS1", 0xFF8892A6, "Sony - PlayStation", listOf("playstation", "ps1", "psone", "playstation1")),
        "ps2" to Meta("PS2", 0xFF2A4BA8, "Sony - PlayStation 2", listOf("playstation2")),
        "psp" to Meta("PSP", 0xFF1F7A8C, "Sony - PlayStation Portable", listOf("playstationportable")),
        "pcengine" to Meta("PCE", 0xFFE08A1E, "NEC - PC Engine - TurboGrafx 16", listOf("turbografx16", "tg16", "pce")),
    )

    fun norm(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().filter { it.isLetterOrDigit() }

    /** Carga assets/emulators.json (una vez). */
    fun cargar(c: Context) { if (sistemas.isEmpty()) analizar(c.assets.open("emulators.json").bufferedReader().use { it.readText() }) }

    fun analizar(json: String) {
        val o = JSONObject(json)
        val e = o.getJSONObject("emus")
        emus = e.keys().asSequence().associateWith { k -> e.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) } } }
        val ss = o.getJSONArray("systems")
        val lista = (0 until ss.length()).map { i ->
            val s = ss.getJSONObject(i)
            val id = s.getString("id"); val nombre = s.getString("name")
            val m = META[id]
            val ex = s.getJSONArray("ext").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
            val cm = s.getJSONArray("cmds").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { x -> x.getString(0) to x.getString(1) } } }
            val color = m?.color?.let { Color(it) } ?: Color.hsv((norm(id).hashCode().mod(360)).toFloat(), 0.45f, 0.62f)
            Sistema(id, nombre, m?.corto ?: id.uppercase().take(4), color, m?.libretro, ex, cm, (setOf(id, norm(nombre)) + (m?.alias ?: emptyList()).toSet()).map(::norm).toSet())
        }
        // Los de casa primero (en este orden), después el resto por nombre
        val orden = META.keys.toList()
        sistemas = lista.sortedWith(compareBy({ orden.indexOf(it.id).let { i -> if (i < 0) 999 else i } }, { it.nombre }))
        porId = sistemas.associateBy { it.id }
        porExt = sistemas.flatMap { s -> s.extensiones.map { it to s } }.groupBy({ it.first }, { it.second })
    }

    fun sistema(id: String): Sistema? = porId[id]
    val deCasa: List<Sistema> get() = sistemas.filter { it.id in META }

    // Extensiones que, aunque otros sistemas también las listen, son de uno solo en la práctica.
    private val PROPIAS = mapOf(
        "gba" to "gba", "agb" to "gba", "gb" to "gb", "gbc" to "gbc", "sgb" to "gb", "nds" to "nds", "dsi" to "nds",
        "nes" to "nes", "sfc" to "snes", "smc" to "snes", "n64" to "n64", "z64" to "n64", "v64" to "n64",
        "md" to "megadrive", "gen" to "megadrive", "smd" to "megadrive", "sms" to "mastersystem", "gg" to "gamegear",
        "3ds" to "n3ds", "cci" to "n3ds", "cxi" to "n3ds", "cia" to "n3ds", "gcm" to "gc", "rvz" to "gc", "gcz" to "gc", "wbfs" to "wii",
        "pce" to "pcengine", "cso" to "psp", "pbp" to "psp", "cue" to "psx",
    )
    /** Extensiones que valen para muchos sistemas: hay que mirar la carpeta (o preguntar). */
    val AMBIGUAS = setOf("zip", "7z", "bin", "iso", "chd", "rom", "img")

    /** El sistema de un archivo por su extensión y, si no basta, por el nombre de la carpeta donde está. null = no se sabe. */
    fun detectar(nombreArchivo: String, carpetas: List<String> = emptyList()): Sistema? {
        val ext = nombreArchivo.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return null
        // 1) la carpeta manda cuando es un sistema (Roms/gba/…, ROMs/Nintendo DS/…)
        for (f in carpetas.asReversed()) { val n = norm(f); sistemas.firstOrNull { n in it.alias && ext in it.extensiones }?.let { return it } }
        // 2) la extensión propia
        PROPIAS[ext]?.let { id -> porId[id]?.let { return it } }
        // 3) una extensión que solo conoce un sistema
        val cand = porExt[ext].orEmpty()
        if (ext !in AMBIGUAS && cand.size == 1) return cand[0]
        return null
    }

    /** ¿Es una extensión que alguno de los sistemas conoce? (para ignorar archivos sueltos de una carpeta: .sav, .png, .txt…) */
    fun esRom(nombre: String): Boolean = nombre.substringAfterLast('.', "").lowercase().let { it in porExt && it !in setOf("txt", "png", "jpg") }
}

/** Nombre bonito a partir del nombre de archivo: sin extensión ni etiquetas como (USA, Europe) o [!]. */
fun tituloDeArchivo(nombre: String): String {
    val base = nombre.substringBeforeLast('.', nombre)
    val sin = base.replace(Regex("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]"), "").replace('_', ' ').trim()
    return sin.ifBlank { base }
}
