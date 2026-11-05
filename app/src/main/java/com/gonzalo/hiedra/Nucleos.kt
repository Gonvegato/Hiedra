package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipInputStream

/**
 * Los núcleos libretro del jugador interno («Hiedra» en el selector de emulador). No van dentro del APK: se bajan del buildbot de
 * libretro (el mismo sitio del que los baja RetroArch), uno por sistema, la primera vez que se juega o desde Sistemas.
 *
 * Un núcleo NO se actualiza solo: un estado guardado (el «guardado rápido») solo lo entiende la misma versión del núcleo que lo hizo,
 * así que cambiarlo por debajo podría dejar sin cargar la partida de otro móvil. Se actualiza a mano, avisando, y la versión anterior
 * se guarda hasta que se haya jugado con la nueva. La partida de verdad (la SRAM, .sav/.srm) no depende de la versión.
 *
 * En cada carpeta `filesDir/nucleos/<id>/`: el `.so` y `nucleo.json` con la fecha del buildbot, el tamaño y el SHA-1 (la «versión»
 * que viaja con cada estado guardado).
 */
object Nucleos {
    /** Un núcleo: su nombre en el buildbot, el nombre que se enseña y las extensiones de ROM que abre tal cual (sin descomprimir). */
    class Nucleo(val id: String, val nombre: String, val extensiones: Set<String>)

    /** Lo instalado de un núcleo. [version] es su SHA-1 (corto): dos móviles con la misma versión pueden compartir estados. */
    data class Instalado(val id: String, val fecha: Long, val tamano: Long, val version: String)

    /**
     * Qué núcleo usa cada sistema (los que usa Lemuroid, probados en Android). Solo los de [activos] se ofrecen ya: el resto entra por
     * fases, cuando se haya probado en el móvil.
     */
    val catalogo: Map<String, Nucleo> = mapOf(
        "gba" to Nucleo("mgba", "mGBA", setOf("gba")),
    )
    /** Los sistemas que ya se juegan con Hiedra (Fase A: GBA). */
    val activos: Set<String> get() = catalogo.keys

    fun para(sistema: String): Nucleo? = catalogo[sistema]

    const val BUILDBOT = "https://buildbot.libretro.com/nightly/android/latest/arm64-v8a"

    // ------------------------------------------------------------------ puro (se prueba sin móvil)

    fun nombreSo(id: String) = "${id}_libretro_android.so"
    fun url(id: String) = "$BUILDBOT/${nombreSo(id)}.zip"

    /** «Wed, 04 Nov 2026 07:22:14 GMT» → ms (0 si no se entiende). */
    fun fechaHttp(cabecera: String?): Long = runCatching {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply { timeZone = TimeZone.getTimeZone("GMT") }.parse(cabecera!!)!!.time
    }.getOrDefault(0L)

    fun aJson(i: Instalado): JSONObject = JSONObject().put("id", i.id).put("fecha", i.fecha).put("tamano", i.tamano).put("version", i.version)
    fun deJson(o: JSONObject): Instalado? = o.optString("id").takeIf { it.isNotBlank() }?.let { Instalado(it, o.optLong("fecha"), o.optLong("tamano"), o.optString("version")) }

    /** «mGBA · 4 nov 2026 · 2,4 MB» */
    fun describir(n: Nucleo, i: Instalado?): String =
        if (i == null) "${n.nombre} · se descarga al jugar"
        else listOf(n.nombre, if (i.fecha > 0) Formato.fecha(i.fecha) else null, megas(i.tamano)).filterNotNull().joinToString(" · ")

    fun megas(b: Long): String = String.format(Locale("es", "ES"), "%.1f MB", b / 1_048_576.0)

    // ------------------------------------------------------------------ en el móvil

    /** Cambia cuando se instala o se quita un núcleo (las pantallas lo observan). */
    var version by mutableIntStateOf(0)
        private set

    private fun dir(c: Context, id: String) = File(c.filesDir, "nucleos/$id")
    fun archivo(c: Context, id: String) = File(dir(c, id), nombreSo(id))
    fun instalado(c: Context, id: String): Instalado? =
        runCatching { File(dir(c, id), "nucleo.json").takeIf { it.exists() && archivo(c, id).exists() }?.readText()?.let { deJson(JSONObject(it)) } }.getOrNull()
    fun anterior(c: Context, id: String): Instalado? =
        runCatching { File(c.filesDir, "nucleos/$id.anterior/nucleo.json").takeIf { it.exists() }?.readText()?.let { deJson(JSONObject(it)) } }.getOrNull()

    /** La carpeta del sistema de libretro (las BIOS que pida un núcleo). */
    fun sistema(c: Context) = File(c.filesDir, "system").apply { mkdirs() }

    /**
     * Baja el núcleo del buildbot y lo deja listo. Si ya había uno, se guarda como «anterior» (por si los estados de la versión vieja
     * hacen falta). Bloqueante (red): fuera del hilo principal. [progreso] recibe la fracción (0–1).
     */
    fun instalar(c: Context, id: String, progreso: (Float) -> Unit = {}): Instalado {
        val tmp = File(c.cacheDir, "nucleo-$id.zip")
        val con = URL(url(id)).openConnection() as HttpURLConnection
        con.connectTimeout = 20_000; con.readTimeout = 60_000
        con.setRequestProperty("User-Agent", "Hiedra/${VERSION} (Android)")
        try {
            if (con.responseCode !in 200..299) throw IOException("el buildbot de libretro respondió ${con.responseCode}")
            val total = con.contentLengthLong
            val fecha = fechaHttp(con.getHeaderField("Last-Modified"))
            con.inputStream.use { i -> tmp.outputStream().use { o ->
                val buf = ByteArray(64 * 1024); var leido = 0L
                while (true) { val n = i.read(buf); if (n < 0) break; o.write(buf, 0, n); leido += n; if (total > 0) progreso(leido.toFloat() / total) }
            } }
            val nuevo = File(c.cacheDir, "nucleo-$id.so")
            var hallado = false
            ZipInputStream(tmp.inputStream()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory && e.name.endsWith(".so")) { nuevo.outputStream().use { z.copyTo(it) }; hallado = true; break }
                }
            }
            if (!hallado || nuevo.length() < 10_000) throw IOException("el archivo del núcleo ha llegado roto")
            val sha = MessageDigest.getInstance("SHA-1").let { md -> nuevo.inputStream().use { i -> val b = ByteArray(64 * 1024); while (true) { val n = i.read(b); if (n < 0) break; md.update(b, 0, n) } }; md.digest() }
            val inst = Instalado(id, fecha, nuevo.length(), sha.joinToString("") { "%02x".format(it) }.take(12))
            val d = dir(c, id)
            if (d.exists()) {
                val ant = File(c.filesDir, "nucleos/$id.anterior")
                ant.deleteRecursively(); d.renameTo(ant)
            }
            d.mkdirs()
            if (!nuevo.renameTo(archivo(c, id))) { nuevo.copyTo(archivo(c, id), overwrite = true); nuevo.delete() }
            File(d, "nucleo.json").writeText(aJson(inst).toString())
            version++
            return inst
        } finally { tmp.delete(); con.disconnect() }
    }

    /** Quita el núcleo (y la copia anterior). Los estados guardados se quedan: sirven si vuelves a bajar la misma versión. */
    fun quitar(c: Context, id: String) {
        dir(c, id).deleteRecursively(); File(c.filesDir, "nucleos/$id.anterior").deleteRecursively(); version++
    }
}
