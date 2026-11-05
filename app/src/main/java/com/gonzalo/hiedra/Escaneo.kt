package com.gonzalo.hiedra

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Un archivo encontrado (suelto o dentro de una carpeta): nombre, documento y carpetas por las que se llegó a él. */
class Hallazgo(val nombre: String, val uri: Uri, val docId: String, val carpetas: List<String>)

class Informe(val anadidos: Int, val repetidos: Int, val sinSistema: List<Hallazgo>)

object Escaneo {
    private const val MAX_PROFUNDIDAD = 8
    private const val MAX_ARCHIVOS = 20000

    /**
     * Pide a Android que se acuerde del permiso sobre este documento o carpeta (si no deja, se sigue sin él). De escritura también si
     * se puede: en la carpeta de ROMs se dejan las que bajes de tu kDrive.
     */
    fun recordar(c: Context, uri: Uri) {
        runCatching { c.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .onFailure { runCatching { c.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    }

    /** Recorre una carpeta (y las de dentro) buscando archivos que algún sistema conozca. */
    suspend fun listar(c: Context, arbol: Uri, progreso: (Int) -> Unit = {}): List<Hallazgo> = withContext(Dispatchers.IO) {
        val raiz = DocumentsContract.getTreeDocumentId(arbol)
        val nombreRaiz = raiz.substringAfterLast('/').substringAfter(':').ifEmpty { raiz.substringBefore(':') }
        val salida = ArrayList<Hallazgo>()
        fun rec(docId: String, ruta: List<String>, prof: Int) {
            if (prof > MAX_PROFUNDIDAD || salida.size >= MAX_ARCHIVOS) return
            val hijos = DocumentsContract.buildChildDocumentsUriUsingTree(arbol, docId)
            val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
            val carpetas = ArrayList<Pair<String, String>>()
            val archivos = ArrayList<Pair<String, String>>()
            runCatching {
                c.contentResolver.query(hijos, cols, null, null, null)?.use { q ->
                    while (q.moveToNext()) {
                        val id = q.getString(0); val n = q.getString(1) ?: continue
                        if (q.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) carpetas.add(id to n) else archivos.add(id to n)
                    }
                }
            }
            for ((id, n) in quitarPistas(archivos)) {
                if (Catalogo.esRom(n)) { salida.add(Hallazgo(n, DocumentsContract.buildDocumentUriUsingTree(arbol, id), id, ruta)); progreso(salida.size) }
            }
            for ((id, n) in carpetas) rec(id, ruta + n, prof + 1)
        }
        rec(raiz, listOf(nombreRaiz), 0)
        salida
    }

    /** En una carpeta con .cue, el .bin (y las pistas) son del mismo juego: se queda solo el .cue. */
    fun quitarPistas(archivos: List<Pair<String, String>>): List<Pair<String, String>> {
        val cues = archivos.filter { it.second.endsWith(".cue", true) }.map { it.second.substringBeforeLast('.').lowercase() }.toSet()
        if (cues.isEmpty()) return archivos
        return archivos.filterNot { (_, n) ->
            val ext = n.substringAfterLast('.', "").lowercase()
            ext in setOf("bin", "img", "wav", "ccd", "sub") && cues.any { cue -> n.lowercase().startsWith(cue) }
        }
    }

    /** Nombre del archivo de un documento suelto. */
    fun nombreDe(c: Context, uri: Uri): String? = runCatching {
        c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()

    fun hallazgoSuelto(c: Context, uri: Uri): Hallazgo? {
        val n = nombreDe(c, uri) ?: return null
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrDefault("")
        return Hallazgo(n, uri, id, emptyList())
    }

    /** Pasa lo encontrado a la biblioteca. [forzar]: sistema para los que no se reconocen solos (zip, iso…). */
    fun importar(hallazgos: List<Hallazgo>, forzar: Sistema? = null, externos: String = "/storage/emulated/0"): Informe {
        var nuevos = 0; var rep = 0; val sin = ArrayList<Hallazgo>()
        val ahora = System.currentTimeMillis()
        for (h in hallazgos) {
            val s = Catalogo.detectar(h.nombre, h.carpetas) ?: forzar
            if (s == null) { sin.add(h); continue }
            val l = Lanzador.rom(s.id, h.nombre.substringBeforeLast('.', h.nombre))
            val estaba = Archivos.tiene(l.clave) && Biblioteca.porClave(l.clave) != null
            // Dónde está el archivo en este móvil (no viaja con la cuenta)
            Archivos.poner(l.clave, h.uri.toString(), Comando.rutaDeDocId(h.docId, externos).orEmpty())
            if (estaba) { rep++; continue }
            // El juego: el que ya tenía esta ROM (de otro dispositivo), el que se llama igual (le añade la ROM), o uno nuevo
            Biblioteca.anadirLanzador(tituloDeArchivo(h.nombre), l, anadido = ahora - nuevos)
            nuevos++
        }
        Biblioteca.guardar()
        return Informe(nuevos, rep, sin)
    }
}
