package com.gonzalo.hiedra

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * Guardados, ROMs y portadas en tu kDrive, con el índice en tu cuenta (como los libros en Atrio: el archivo va a kDrive y
 * lo que dice qué hay y dónde viaja con la cuenta, en /users/<uid>/hiedra/nube/<tipo>/<id del juego>/f/<archivo>).
 *
 * GUARDADOS. Eliges, para cada sistema, la carpeta donde su emulador deja los guardados (solo ahí puede mirar Hiedra: Android no
 * deja a una app leer las carpetas privadas de otra, y la mayoría de emuladores guardan ahí por defecto; ver README).
 *  - Antes de jugar: si en la nube hay un guardado distinto del que tienes y que no es el que ya habías sincronizado, se baja
 *    (se comprueba que llega entero) y se pone en su sitio. Lo que sustituya se guarda antes en una copia.
 *  - Al volver a Hiedra: si el guardado de este móvil ha cambiado, se sube.
 * No hay vigilancia mientras juegas (Android no avisa de cuándo un emulador escribe un archivo): se mira al lanzar y al volver.
 */
object NubeP {
    /** Una carpeta con archivos (la de guardados o la de ROMs): la de verdad usa SAF; las pruebas, una carpeta normal. */
    interface Carpeta {
        /** Los archivos (en cualquier subcarpeta, hasta 4 niveles) cuyo nombre sin extensión es [base] y con una de las extensiones dadas. */
        fun buscar(base: String, ext: Set<String>): List<Arch>
        fun crear(nombre: String): Arch?
    }
    interface Arch {
        val nombre: String
        val uri: String
        val tamano: Long
        val modificado: Long
        fun leer(): InputStream
        fun escribir(): OutputStream
    }

    class Ficha(val n: String, val id: Long, val h: String, val s: Long, val dev: String, val t: Long)

    val EXT_GUARDADO = setOf("srm", "sav", "dsv", "eep", "fla", "mpk", "mcr", "sra")

    /** Carpeta de guardados de cada sistema (sistema → uri del árbol que diste). */
    val carpetas = mutableStateMapOf<String, String>()
    private fun prefs(c: Context) = c.getSharedPreferences("hiedra_nube_p", Context.MODE_PRIVATE)

    fun cargar(c: Context) {
        carpetas.clear()
        runCatching { JSONObject(prefs(c).getString("carpetas", "{}")!!).let { o -> o.keys().forEach { carpetas[it] = o.getString(it) } } }
    }

    fun ponCarpeta(c: Context, sistema: String, uri: Uri?) {
        if (uri == null) carpetas.remove(sistema) else {
            runCatching { c.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            carpetas[sistema] = uri.toString()
        }
        prefs(c).edit().putString("carpetas", JSONObject(carpetas as Map<*, *>).toString()).apply()
    }

    /** ¿Se sincronizan los guardados de este juego? (ROM, con carpeta elegida, cuenta y kDrive conectados) */
    fun activo(c: Context, j: Juego) = j.rom != null && Archivos.tiene(j.rom!!.clave) && carpetas[j.sistema] != null && Cuenta.vinculada && KDrive.on(c)

    fun carpetaReal(c: Context, uri: String): Carpeta? = DocumentFile.fromTreeUri(c, Uri.parse(uri))?.let { DocCarpeta(c, it) }

    // ---------------------------------------------------------------- utilidades

    fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    /** En la cuenta, un juego se llama por su id (viaja con la cuenta y no tiene puntos ni barras). */
    fun kid(j: Juego) = j.id
    private fun fk(nombre: String) = hex(MessageDigest.getInstance("SHA-1").digest(nombre.lowercase().toByteArray())).take(12)
    fun hash(a: Arch): String = hashDe(a.leer())
    fun hashDe(i: InputStream): String = i.use { inp ->
        val md = MessageDigest.getInstance("SHA-256"); val buf = ByteArray(64 * 1024)
        while (true) { val n = inp.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        hex(md.digest())
    }

    private fun ultimo(c: Context, kid: String, fk: String) = prefs(c).getString("h:$kid:$fk", "").orEmpty()
    private fun anota(c: Context, kid: String, fk: String, h: String) = prefs(c).edit().putString("h:$kid:$fk", h).apply()

    // ---------------------------------------------------------------- el índice (en la cuenta)

    fun indice(tipo: String, kid: String): Map<String, Ficha> {
        val raw = Cuenta.db("GET", "${Cuenta.raiz}/nube/$tipo/$kid/f", null) ?: return emptyMap()
        return deJson(JSONObject(raw))
    }

    private fun deJson(o: JSONObject): Map<String, Ficha> = o.keys().asSequence().associateWith { k ->
        o.getJSONObject(k).let { Ficha(it.optString("n"), it.optLong("id"), it.optString("h"), it.optLong("s"), it.optString("dev"), it.optLong("t")) }
    }

    private fun escribe(tipo: String, kid: String, fk: String, n: String, id: Long, h: String, s: Long) {
        val o = JSONObject().put("n", n).put("id", id).put("h", h).put("s", s).put("dev", Cuenta.dispositivo).put("t", JSONObject().put(".sv", "timestamp"))
        Cuenta.db("PUT", "${Cuenta.raiz}/nube/$tipo/$kid/f/$fk", o.toString(), "print=silent")
    }

    // ---------------------------------------------------------------- guardados

    private fun copiar(c: Context, kid: String, a: Arch) {
        val dir = File(c.filesDir, "copias_guardados/$kid").apply { mkdirs() }
        File(dir, "${a.nombre}.${System.currentTimeMillis()}").outputStream().use { o -> a.leer().use { it.copyTo(o) } }
        dir.listFiles { f -> f.name.startsWith(a.nombre + ".") }?.sortedByDescending { it.name }?.drop(5)?.forEach { it.delete() }
    }

    /**
     * Antes de lanzar: baja el guardado de la nube si es distinto (y más nuevo) que el de aquí. Devuelve un aviso para enseñar, o null.
     * Sin conexión o sin nada en la nube: null (se juega con lo que hay).
     */
    fun antes(c: Context, j: Juego, car: Carpeta): String? {
        val kid = kid(j)
        val idx = try { indice("saves", kid) } catch (e: IOException) { return null } catch (e: Cuenta.Fail) { return null }
        if (idx.isEmpty()) return null
        val base = j.archivo.ifBlank { j.titulo }
        val locales = car.buscar(base, EXT_GUARDADO)
        var aviso: String? = null
        for ((k, f) in idx) {
            val loc = locales.firstOrNull { it.nombre.equals(f.n, true) }
            val ult = ultimo(c, kid, k)
            val hl = loc?.let { hash(it) }
            if (hl == f.h) { anota(c, kid, k, f.h); continue }
            if (f.h == ult) continue                                       // la nube no ha cambiado desde la última vez: lo de aquí es lo último
            if (loc != null && ult.isBlank() && loc.modificado > f.t) continue // nunca sincronizado y el de aquí es más reciente: se sube al volver
            val tmp = File(c.cacheDir, "guardado-$kid-$k.tmp")
            try {
                KDrive.download(c, f.id, tmp)
                if (hashDe(tmp.inputStream()) != f.h) throw IOException("el guardado ha llegado incompleto")
                if (loc != null) copiar(c, kid, loc)
                val dest = loc ?: car.crear(f.n) ?: continue
                dest.escribir().use { o -> tmp.inputStream().use { it.copyTo(o) } }
                anota(c, kid, k, f.h)
                aviso = if (loc != null && hl != ult && ult.isNotBlank()) "Se ha cargado el guardado de la nube (el de este móvil también había cambiado: queda una copia)" else "Guardado cargado desde la nube"
            } catch (e: Exception) { aviso = "No se ha podido bajar el guardado: ${e.message ?: "sin conexión"}" } finally { tmp.delete() }
        }
        return aviso
    }

    /** Al volver de jugar: sube los guardados de este móvil que hayan cambiado. Devuelve cuántos se han subido. */
    fun despues(c: Context, j: Juego, car: Carpeta): Int {
        val kid = kid(j)
        val base = j.archivo.ifBlank { j.titulo }
        val locales = car.buscar(base, EXT_GUARDADO)
        if (locales.isEmpty()) return 0
        val idx = try { indice("saves", kid) } catch (e: Exception) { return 0 }
        var n = 0
        for (loc in locales) {
            val k = fk(loc.nombre)
            val h = hash(loc)
            if (h == ultimo(c, kid, k)) continue
            if (idx[k]?.h == h) { anota(c, kid, k, h); continue }
            try {
                val id = KDrive.upload(c, "Hiedra/Guardados/${j.sistema}/$kid", loc.nombre, loc.tamano) { loc.leer() }
                escribe("saves", kid, k, loc.nombre, id, h, loc.tamano)
                anota(c, kid, k, h); n++
            } catch (e: Exception) { return n }
        }
        return n
    }

    // ---------------------------------------------------------------- ROMs

    fun romEnNube(j: Juego): Ficha? = try { indice("roms", kid(j))["rom"] } catch (e: Exception) { null }

    fun subirRom(c: Context, j: Juego, a: Arch, progreso: (Long) -> Unit = {}) {
        val h = hash(a)
        val id = KDrive.upload(c, "Hiedra/ROMs/${j.sistema}", a.nombre, a.tamano, progreso) { a.leer() }
        escribe("roms", kid(j), "rom", a.nombre, id, h, a.tamano)
    }

    /** Baja la ROM a [destino] (se comprueba que llega entera). Devuelve el archivo creado. */
    fun bajarRom(c: Context, j: Juego, f: Ficha, destino: Carpeta): Arch {
        val tmp = File(c.cacheDir, "rom-${kid(j)}.tmp")
        try {
            KDrive.download(c, f.id, tmp)
            if (tmp.length() != f.s || hashDe(tmp.inputStream()) != f.h) throw IOException("la ROM ha llegado incompleta")
            val a = destino.crear(f.n) ?: throw IOException("no se pudo crear el archivo en esa carpeta")
            a.escribir().use { o -> tmp.inputStream().use { it.copyTo(o) } }
            return a
        } finally { tmp.delete() }
    }

    /** ¿Subir las ROMs solas? (con wifi; el interruptor está en Ajustes → Avanzado) */
    fun romsSolas(c: Context) = prefs(c).getBoolean("roms_solas", true)
    fun ponRomsSolas(c: Context, v: Boolean) { prefs(c).edit().putBoolean("roms_solas", v).apply() }

    /** Sube a tu kDrive las ROMs de este móvil que aún no están (una por una). Devuelve cuántas ha subido. */
    fun subirRoms(c: Context, abre: (Juego) -> Arch? = { j -> androidx.documentfile.provider.DocumentFile.fromSingleUri(c, Uri.parse(j.uri))?.takeIf { it.exists() }?.let { DocArch(c, it) } }): Int {
        if (!Cuenta.vinculada || !KDrive.on(c) || !romsSolas(c)) return 0
        val remoto = Cuenta.db("GET", "${Cuenta.raiz}/nube/roms", null)?.let { JSONObject(it) }
        var n = 0
        for (j in Biblioteca.juegos.toList()) {
            if (j.rom == null || j.uri.isBlank()) continue
            if (remoto?.optJSONObject(kid(j))?.optJSONObject("f")?.has("rom") == true) continue
            val a = abre(j) ?: continue
            subirRom(c, j, a); n++
        }
        return n
    }

    // ---------------------------------------------------------------- portadas propias

    private val hashes = HashMap<String, String>()
    private fun hashPortada(f: File): String = hashes.getOrPut("${f.path}|${f.length()}|${f.lastModified()}") { hashDe(f.inputStream()) }

    /** Portadas elegidas por ti: las de este móvil se suben; las que otro de tus móviles subió y aquí faltan, se bajan. */
    fun portadas(c: Context): Boolean {
        if (!Cuenta.vinculada || !KDrive.on(c)) return false
        val propias = File(c.filesDir, "portadas").path
        var cambio = false
        val remoto = Cuenta.db("GET", "${Cuenta.raiz}/nube/portadas", null)?.let { JSONObject(it) }
        for (j in Biblioteca.juegos.toList()) {
            val kid = kid(j)
            val f = j.portada.takeIf { it.startsWith(propias) }?.let { File(it) }?.takeIf { it.exists() }
            val cloud = remoto?.optJSONObject(kid)?.optJSONObject("f")?.let { deJson(it)["img"] }
            if (f != null) {
                val h = hashPortada(f)
                if (h == ultimo(c, kid, "img") || cloud?.h == h) { anota(c, kid, "img", h); continue }
                val id = KDrive.upload(c, "Hiedra/Portadas", "$kid.jpg", f.length()) { f.inputStream() }
                escribe("portadas", kid, "img", "$kid.jpg", id, h, f.length())
                anota(c, kid, "img", h)
            } else if (cloud != null && cloud.h != ultimo(c, kid, "img") && (j.portada.isBlank() || !File(j.portada).exists())) {
                val dir = File(c.filesDir, "portadas").apply { mkdirs() }
                val dest = File(dir, "${j.id}-${System.currentTimeMillis()}.jpg")
                try {
                    KDrive.download(c, cloud.id, dest)
                    if (hashPortada(dest) != cloud.h) { dest.delete(); continue }
                    anota(c, kid, "img", cloud.h)
                    Biblioteca.por(j.id)?.let { Biblioteca.poner(it.copy(portada = dest.absolutePath)); cambio = true }
                } catch (e: Exception) { dest.delete() }
            }
        }
        return cambio
    }
}

/** Una carpeta de verdad (SAF). */
class DocCarpeta(private val c: Context, private val raiz: DocumentFile) : NubeP.Carpeta {
    override fun buscar(base: String, ext: Set<String>): List<NubeP.Arch> {
        val out = ArrayList<NubeP.Arch>()
        fun rec(d: DocumentFile, nivel: Int) {
            if (nivel > 4 || out.size >= 20) return
            for (f in d.listFiles()) {
                if (f.isDirectory) rec(f, nivel + 1) else {
                    val n = f.name ?: continue
                    if (n.substringBeforeLast('.').equals(base, true) && n.substringAfterLast('.', "").lowercase() in ext) out += DocArch(c, f)
                }
            }
        }
        rec(raiz, 0)
        return out
    }
    override fun crear(nombre: String): NubeP.Arch? {
        val f = raiz.createFile("application/octet-stream", nombre) ?: return null
        if (f.name != nombre) f.renameTo(nombre)
        return DocArch(c, f)
    }
}

class DocArch(private val c: Context, private val f: DocumentFile) : NubeP.Arch {
    override val nombre: String get() = f.name.orEmpty()
    override val uri: String get() = f.uri.toString()
    override val tamano: Long get() = f.length()
    override val modificado: Long get() = f.lastModified()
    override fun leer(): InputStream = c.contentResolver.openInputStream(f.uri) ?: throw IOException("no se puede leer $nombre")
    override fun escribir(): OutputStream = c.contentResolver.openOutputStream(f.uri, "wt") ?: throw IOException("no se puede escribir $nombre")
}
