package com.gonzalo.hiedra

import android.content.Context
import android.graphics.Bitmap
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Lo que guarda el jugador interno de cada juego:
 *
 *  - ESTADOS (el «guardado rápido»): una foto de la consola en ese instante. Al salir del juego se hace solo (hueco «auto») y se sube a
 *    tu kDrive; al volver a jugar, en este móvil o en otro, se carga y sigues en el mismo segundo. Hay además tres huecos a mano. Un
 *    estado solo lo entiende el MISMO núcleo, y a menudo solo la misma versión: por eso viaja con el núcleo y su versión.
 *    Archivos: `filesDir/estados/<id>/<hueco>.state` + `<hueco>.json` (+ `<hueco>.png`, la miniatura). En kDrive:
 *    `Hiedra/Estados/<sistema>/<id>/<hueco>.state`. Índice en la cuenta: `nube/states/<id>/f/<hueco>` (como `nube/saves`, más
 *    `nucleo`, `version` y `dn`, el nombre del dispositivo).
 *
 *  - LA PARTIDA (SRAM, .sav/.srm): lo que el juego guarda en su cartucho. Esa sí la entienden los demás emuladores: se deja en la
 *    carpeta de guardados del sistema (la misma que ya se sincroniza con NubeP) con el nombre de la ROM, y además una copia interna.
 */
object Estados {
    const val AUTO = "auto"
    val huecos = listOf(AUTO, "1", "2", "3")

    /** Lo que se sabe de un estado guardado (aquí o en la nube). */
    data class Meta(
        val hueco: String, val nucleo: String, val version: String, val t: Long,
        val dev: String, val dn: String, val hash: String, val tam: Long, val id: Long = 0L,
    ) {
        fun aJson(): JSONObject = JSONObject().put("hueco", hueco).put("nucleo", nucleo).put("version", version).put("t", t)
            .put("dev", dev).put("dn", dn).put("h", hash).put("s", tam).put("id", id)
        companion object {
            fun deJson(o: JSONObject, hueco: String = o.optString("hueco")): Meta? {
                if (o.optString("h").isBlank()) return null
                return Meta(hueco, o.optString("nucleo"), o.optString("version"), o.optLong("t"), o.optString("dev"), o.optString("dn"), o.optString("h"), o.optLong("s"), o.optLong("id"))
            }
        }
    }

    /** Qué hacer con el estado automático al empezar a jugar. */
    enum class Decision { NADA, USAR_LOCAL, SUBIR_Y_USAR_LOCAL, BAJAR, PREGUNTAR }

    /**
     * La regla (pura, se prueba): manda el más reciente, siempre que sea del mismo núcleo.
     *  - Nada en la nube (o de otro núcleo): el de aquí (y se sube si aún no lo estaba).
     *  - El mismo en los dos sitios: el de aquí.
     *  - El de aquí es más nuevo: el de aquí (y se sube).
     *  - El de la nube es más nuevo: se baja; si es de otra versión del núcleo, se pregunta (puede no cargar).
     */
    fun decidir(local: Meta?, nube: Meta?, nucleo: String, version: String, subido: Boolean): Decision {
        val deAqui = if (local == null) Decision.NADA else if (subido) Decision.USAR_LOCAL else Decision.SUBIR_Y_USAR_LOCAL
        if (nube == null || nube.nucleo != nucleo) return deAqui
        if (local != null && local.hash == nube.hash) return Decision.USAR_LOCAL
        if (local != null && local.t >= nube.t) return deAqui
        return if (nube.version != version) Decision.PREGUNTAR else Decision.BAJAR
    }

    fun sha(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------ en el móvil

    private fun dir(c: Context, kid: String) = File(c.filesDir, "estados/$kid").apply { mkdirs() }
    fun archivo(c: Context, kid: String, hueco: String) = File(dir(c, kid), "$hueco.state")
    fun miniatura(c: Context, kid: String, hueco: String) = File(dir(c, kid), "$hueco.png")
    private fun archivoMeta(c: Context, kid: String, hueco: String) = File(dir(c, kid), "$hueco.json")
    private fun prefs(c: Context) = c.getSharedPreferences("hiedra_estados", Context.MODE_PRIVATE)

    fun meta(c: Context, kid: String, hueco: String): Meta? =
        runCatching { archivoMeta(c, kid, hueco).takeIf { it.exists() && archivo(c, kid, hueco).exists() }?.readText()?.let { Meta.deJson(JSONObject(it), hueco) } }.getOrNull()

    fun leer(c: Context, kid: String, hueco: String): ByteArray? = archivo(c, kid, hueco).takeIf { it.exists() }?.readBytes()

    /** Guarda un estado (y su miniatura, si la hay). Devuelve su meta. */
    fun guardar(c: Context, kid: String, hueco: String, datos: ByteArray, nucleo: String, version: String, mini: Bitmap? = null, t: Long = System.currentTimeMillis()): Meta {
        val tmp = File(dir(c, kid), "$hueco.state.tmp")
        tmp.writeBytes(datos); tmp.renameTo(archivo(c, kid, hueco))
        val m = Meta(hueco, nucleo, version, t, Cuenta.dispositivo, Cuenta.nombreDispositivo, sha(datos), datos.size.toLong())
        archivoMeta(c, kid, hueco).writeText(m.aJson().toString())
        mini?.let { b -> runCatching { miniatura(c, kid, hueco).outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 90, it) } } }
        return m
    }

    fun guardarMiniatura(c: Context, kid: String, hueco: String, mini: Bitmap) {
        runCatching { miniatura(c, kid, hueco).outputStream().use { mini.compress(Bitmap.CompressFormat.PNG, 90, it) } }
    }

    fun subido(c: Context, kid: String, hueco: String, hash: String) = prefs(c).getString("s:$kid:$hueco", "") == hash
    private fun anotaSubido(c: Context, kid: String, hueco: String, hash: String) = prefs(c).edit().putString("s:$kid:$hueco", hash).apply()

    // ------------------------------------------------------------------ en la nube

    /** El índice de estados de un juego en la cuenta (vacío sin cuenta o sin red: nunca falla). */
    fun indiceNube(kid: String): Map<String, Meta> = runCatching {
        if (!Cuenta.vinculada) return emptyMap()
        val raw = Cuenta.db("GET", "${Cuenta.raiz}/nube/states/$kid/f", null) ?: return emptyMap()
        if (raw == "null") return emptyMap()
        val o = JSONObject(raw)
        o.keys().asSequence().mapNotNull { k -> o.optJSONObject(k)?.let { Meta.deJson(it, k) }?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    /** Sube un estado si no lo está ya. Bloqueante (red). */
    fun subir(c: Context, kid: String, sistema: String, hueco: String): Boolean {
        val m = meta(c, kid, hueco) ?: return false
        if (subido(c, kid, hueco, m.hash)) return true
        if (!Cuenta.vinculada || !KDrive.on(c)) return false
        val f = archivo(c, kid, hueco)
        val id = KDrive.upload(c, "Hiedra/Estados/$sistema/$kid", "$hueco.state", f.length()) { f.inputStream() }
        val o = JSONObject().put("n", "$hueco.state").put("id", id).put("h", m.hash).put("s", m.tam).put("dev", Cuenta.dispositivo)
            .put("dn", m.dn).put("nucleo", m.nucleo).put("version", m.version).put("t", JSONObject().put(".sv", "timestamp"))
        Cuenta.db("PUT", "${Cuenta.raiz}/nube/states/$kid/f/$hueco", o.toString(), "print=silent")
        anotaSubido(c, kid, hueco, m.hash)
        return true
    }

    /** Baja un estado de la nube y lo deja como el de aquí (comprobando que llega entero). Lo que hubiera aquí queda en «.anterior». */
    fun bajar(c: Context, kid: String, nube: Meta): ByteArray {
        val tmp = File(c.cacheDir, "estado-$kid-${nube.hueco}.tmp")
        try {
            KDrive.download(c, nube.id, tmp)
            val b = tmp.readBytes()
            if (sha(b) != nube.hash) throw IOException("el estado guardado ha llegado incompleto")
            archivo(c, kid, nube.hueco).takeIf { it.exists() }?.copyTo(File(dir(c, kid), "${nube.hueco}.state.anterior"), overwrite = true)
            guardar(c, kid, nube.hueco, b, nube.nucleo, nube.version, t = nube.t)
            // Lo bajado ya está en la nube: no hay que volver a subirlo
            anotaSubido(c, kid, nube.hueco, nube.hash)
            miniatura(c, kid, nube.hueco).delete()
            return b
        } finally { tmp.delete() }
    }

    /** Deja encargada la subida de los estados y la partida de un juego (con red; aunque Android congele la app nada más salir). */
    fun encolarSubida(c: Context, kid: String, sistema: String) = runCatching {
        val req = OneTimeWorkRequestBuilder<EstadosWorker>()
            .setInputData(workDataOf("kid" to kid, "sistema" to sistema))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(c.applicationContext).enqueueUniqueWork("estados-$kid", ExistingWorkPolicy.REPLACE, req)
    }

    // ------------------------------------------------------------------ la partida (SRAM)

    /** La extensión con la que la dejan los emuladores de ese sistema (la misma partida sirve en ellos). */
    fun extensionPartida(sistema: String) = when (sistema) { "gba" -> "sav"; else -> "srm" }

    private fun partidaInterna(c: Context, kid: String) = File(c.filesDir, "guardados/$kid/partida.srm").also { it.parentFile?.mkdirs() }

    /** La partida más reciente entre la copia interna y la de la carpeta de guardados del sistema (si la elegiste). */
    fun leerPartida(c: Context, j: Juego): ByteArray? {
        val interna = partidaInterna(c, j.id).takeIf { it.exists() && it.length() > 0 }
        val car = NubeP.carpetas[j.sistema]?.let { NubeP.carpetaReal(c, it) }
        val fuera = runCatching { car?.buscar(j.archivo.ifBlank { j.titulo }, NubeP.EXT_GUARDADO)?.maxByOrNull { it.modificado } }.getOrNull()
        return when {
            fuera != null && (interna == null || fuera.modificado > interna.lastModified()) -> runCatching { fuera.leer().use { it.readBytes() } }.getOrNull() ?: interna?.readBytes()
            else -> interna?.readBytes()
        }
    }

    /** Guarda la partida aquí y en la carpeta de guardados del sistema (solo si ha cambiado). Bloqueante (disco y SAF). */
    fun guardarPartida(c: Context, j: Juego, datos: ByteArray) {
        if (datos.isEmpty()) return
        val interna = partidaInterna(c, j.id)
        if (interna.exists() && interna.readBytes().contentEquals(datos)) return
        interna.writeBytes(datos)
        val car = NubeP.carpetas[j.sistema]?.let { NubeP.carpetaReal(c, it) } ?: return
        runCatching {
            val base = j.archivo.ifBlank { j.titulo }
            val a = car.buscar(base, NubeP.EXT_GUARDADO).maxByOrNull { it.modificado } ?: car.crear("$base.${extensionPartida(j.sistema)}")
            a?.escribir()?.use { it.write(datos) }
        }
    }
}

/** Sube los estados que hayan cambiado de un juego y, si hay carpeta de guardados, su partida (como al volver de un emulador). */
class EstadosWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        iniciar(c)
        val kid = inputData.getString("kid") ?: return Result.success()
        val sistema = inputData.getString("sistema").orEmpty()
        if (!Cuenta.vinculada || !KDrive.on(c)) return Result.success()
        var ok = true
        for (h in Estados.huecos) if (!runCatching { Estados.subir(c, kid, sistema, h) }.getOrDefault(false) && Estados.meta(c, kid, h) != null) ok = false
        Biblioteca.por(kid)?.takeIf { NubeP.activo(c, it) }?.let { j -> runCatching { NubeP.carpetaReal(c, NubeP.carpetas[j.sistema]!!)?.let { NubeP.despues(c, j, it) } } }
        return if (ok || runAttemptCount >= 6) Result.success() else Result.retry()
    }
}
