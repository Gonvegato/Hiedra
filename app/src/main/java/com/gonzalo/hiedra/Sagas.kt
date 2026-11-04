package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Una saga de juegos: juegos juntos bajo un nombre, en su orden. No mueve nada de su sitio (cada juego sigue en su sección); solo los junta en las listas.
 * Los juegos se apuntan por su id (el mismo en todos tus dispositivos), así que la saga viaja con tu cuenta.
 */
data class Saga(val id: String, val nombre: String, val juegos: List<String>, val mod: Long = 0L) {
    fun aJson(): JSONObject = JSONObject().put("id", id).put("n", nombre).put("mod", mod).put("j", JSONArray().also { a -> juegos.forEach { a.put(it) } })
    companion object {
        fun deJson(o: JSONObject): Saga? {
            val id = o.optString("id").ifBlank { return null }
            val k = o.optJSONArray("j") ?: JSONArray()
            return Saga(id, o.optString("n"), (0 until k.length()).map { k.optString(it) }.filter { it.isNotBlank() }.distinct(), o.optLong("mod"))
        }
    }
}

/** Una fila de una lista de juegos: un juego suelto o una saga con sus juegos (los que entran en esa lista, en el orden de la saga). */
sealed class FilaLista {
    class Suelto(val juego: Juego, val saga: Saga? = null, val numero: Int = 0) : FilaLista()
    class DeSaga(val saga: Saga, val miembros: List<Juego>) : FilaLista()
}

object Sagas {
    val lista = mutableStateListOf<Saga>()
    /** Las que borraste (su id y cuándo): así los demás dispositivos también las quitan. */
    val borradas = HashMap<String, Long>()
    var cargada by mutableStateOf(false)
    private var archivo: File? = null
    private var appCtx: Context? = null

    fun cargar(c: Context) {
        if (cargada) return
        appCtx = c.applicationContext
        val f = File(c.filesDir, "sagas.json").also { archivo = it }
        if (f.exists()) runCatching {
            val o = JSONObject(f.readText())
            o.optJSONArray("sagas")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Saga::deJson) }.forEach { lista.add(it) } }
            o.optJSONObject("borradas")?.let { b -> b.keys().forEach { k -> borradas[k] = b.optLong(k) } }
        }
        cargada = true
    }

    private fun guardar() {
        val f = archivo ?: return
        runCatching {
            val o = JSONObject().put("sagas", JSONArray().also { a -> lista.forEach { a.put(it.aJson()) } }).put("borradas", JSONObject().also { b -> borradas.forEach { (k, v) -> b.put(k, v) } })
            val t = File(f.path + ".tmp"); t.writeText(o.toString()); t.renameTo(f)
        }
        appCtx?.let { c -> Widgets.pedir(c); SincroP.cambio(c) }
    }

    fun por(id: String): Saga? = lista.firstOrNull { it.id == id }

    /** La saga a la que pertenece un juego (por su id), si tiene. */
    fun de(id: String): Saga? = lista.firstOrNull { id in it.juegos }
    fun de(j: Juego): Saga? = de(j.id)

    /** Guarda una saga (con la hora de ahora). Un juego solo está en una: se quita de las demás. */
    fun poner(s: Saga) {
        val ahora = System.currentTimeMillis()
        val nueva = s.copy(juegos = s.juegos.distinct(), mod = ahora)
        // Los juegos de esta saga salen de las otras
        lista.toList().forEach { o -> if (o.id != nueva.id && o.juegos.any { it in nueva.juegos }) {
            val i = lista.indexOfFirst { it.id == o.id }
            val resto = o.juegos.filter { it !in nueva.juegos }
            if (resto.isEmpty()) { lista.removeAt(i); borradas[o.id] = ahora } else lista[i] = o.copy(juegos = resto, mod = ahora)
        } }
        val i = lista.indexOfFirst { it.id == nueva.id }
        if (i >= 0) lista[i] = nueva else { lista.add(nueva); borradas.remove(nueva.id) }
        guardar()
    }

    fun crear(nombre: String, juegos: List<String> = emptyList()): Saga = Saga(UUID.randomUUID().toString().replace("-", "").take(10), nombre.trim(), juegos).also { poner(it) }

    fun quitar(id: String) { if (lista.removeAll { it.id == id }) { borradas[id] = System.currentTimeMillis(); guardar() } }

    /** Pone un juego en una saga (al final) o, con [saga] null, lo saca de la que estuviera. */
    fun asignar(juegoId: String, saga: Saga?) {
        val actual = de(juegoId)
        if (actual?.id == saga?.id) return
        if (actual != null) { val r = actual.juegos - juegoId; if (r.isEmpty()) quitar(actual.id) else poner(actual.copy(juegos = r)) }
        if (saga != null) por(saga.id)?.let { poner(it.copy(juegos = it.juegos + juegoId)) }
    }

    /** Pone en la biblioteca lo que ha salido de mezclar con tus otros dispositivos (sin cambiar la hora de nadie). */
    fun aplicarMezcla(nuevas: List<Saga>, borr: Map<String, Long>) {
        lista.clear(); lista.addAll(nuevas); borradas.clear(); borradas.putAll(borr)
        val f = archivo ?: return
        runCatching {
            val o = JSONObject().put("sagas", JSONArray().also { a -> lista.forEach { a.put(it.aJson()) } }).put("borradas", JSONObject().also { b -> borradas.forEach { (k, v) -> b.put(k, v) } })
            File(f.path + ".tmp").also { it.writeText(o.toString()) }.renameTo(f)
        }
        appCtx?.let { Widgets.pedir(it) }
    }

    /** Los juegos de una saga, en su orden (los que ya no están en la biblioteca se saltan). */
    fun juegos(s: Saga, todos: List<Juego> = Biblioteca.juegos.toList()): List<Juego> {
        val porId = todos.associateBy { it.id }
        return s.juegos.mapNotNull { porId[it] }
    }

    /**
     * Agrupa una lista de juegos (ya ordenada) por sagas: cada saga sale una vez, donde está su primer juego de la lista, con los de la lista en el
     * orden de la saga. Una saga con un solo juego en la lista sale como juego suelto (con su saga y número, para no perder el dato).
     */
    fun agrupar(juegos: List<Juego>, sagas: List<Saga> = lista.toList()): List<FilaLista> {
        val deId = HashMap<String, Saga>().also { m -> sagas.forEach { s -> s.juegos.forEach { m[it] = s } } }
        val vistas = HashSet<String>()
        val out = ArrayList<FilaLista>()
        for (j in juegos) {
            val s = deId[j.id]
            if (s == null) { out += FilaLista.Suelto(j); continue }
            if (!vistas.add(s.id)) continue
            val miembros = s.juegos.mapNotNull { id -> juegos.firstOrNull { it.id == id } }
            if (miembros.size >= 2) out += FilaLista.DeSaga(s, miembros)
            else out += FilaLista.Suelto(miembros.first(), s, s.juegos.indexOf(miembros.first().id) + 1)
        }
        return out
    }
}
