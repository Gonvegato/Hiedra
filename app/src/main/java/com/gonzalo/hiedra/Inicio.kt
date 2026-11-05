package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/*
 * La portada de Hiedra: un destacado (el juego de ahora, o el que fijes) y debajo estantes (filas de portadas) que eliges, ordenas y
 * dimensionas. No hay colores por casilla ni casillas sueltas: todo va sobre el papel de la página, como una revista, y por eso mismo lo
 * puede pintar igual el móvil, la tableta o el PC. Es una lista de entradas (qué estante, qué tamaño, qué juegos fijados), guardada en el
 * móvil y en tu cuenta (gana el cambio más reciente).
 */

/** Los estantes que puede tener la portada. */
enum class Estante(val nombre: String) { JUGANDO("Sigue jugando"), FIJADOS("Mis elegidos"), PENDIENTE("Pendientes"), SAGAS("Sagas"), PC("Tu PC"), PASADO("Pasados") }

/** Un estante en la portada: cuál, si va con portadas grandes y si está a la vista. */
data class FilaInicio(val estante: Estante, val grande: Boolean = false, val activa: Boolean = true)

/** La configuración de la portada: las filas en su orden, los juegos fijados (por id, en orden) y el destacado ("" = automático; si no, un id). */
data class ConfigInicio(val filas: List<FilaInicio> = porDefecto, val fijados: List<String> = emptyList(), val destacado: String = "", val mod: Long = 0L) {
    fun aJson(): JSONObject = JSONObject().put("mod", mod).put("d", destacado)
        .put("f", JSONArray().also { a -> filas.forEach { a.put(JSONObject().put("e", it.estante.name).put("g", it.grande).put("on", it.activa)) } })
        .put("k", JSONArray().also { a -> fijados.forEach { a.put(it) } })

    companion object {
        val porDefecto = listOf(FilaInicio(Estante.JUGANDO, grande = true), FilaInicio(Estante.FIJADOS), FilaInicio(Estante.PENDIENTE), FilaInicio(Estante.SAGAS), FilaInicio(Estante.PC), FilaInicio(Estante.PASADO, activa = false))
        fun deJson(o: JSONObject?): ConfigInicio? {
            o ?: return null
            val fa = o.optJSONArray("f") ?: return null
            val filas = (0 until fa.length()).mapNotNull { i -> fa.optJSONObject(i)?.let { x -> Estante.entries.firstOrNull { it.name == x.optString("e") }?.let { e -> FilaInicio(e, x.optBoolean("g"), x.optBoolean("on", true)) } } }.distinctBy { it.estante }
            // Un estante nuevo que esta copia no conocía se añade al final, apagado
            val completas = filas + Estante.entries.filter { e -> filas.none { it.estante == e } }.map { FilaInicio(it, activa = false) }
            val k = o.optJSONArray("k") ?: JSONArray()
            return ConfigInicio(completas, (0 until k.length()).map { k.optString(it) }.filter { it.isNotBlank() }.distinct(), o.optString("d"), o.optLong("mod"))
        }
    }
}

object Inicio {
    var config by mutableStateOf(ConfigInicio()); private set
    private var appCtx: Context? = null
    private fun prefs(c: Context) = c.getSharedPreferences("hiedra_inicio", Context.MODE_PRIVATE)

    fun cargar(c: Context) {
        appCtx = c.applicationContext
        config = runCatching { ConfigInicio.deJson(JSONObject(prefs(c).getString("cfg", "") ?: "")) }.getOrNull() ?: ConfigInicio()
    }

    /** Cambia la portada (con la hora de ahora): se guarda y viaja a tus otros dispositivos. */
    fun poner(n: ConfigInicio, ahora: Long = System.currentTimeMillis()) {
        config = n.copy(mod = ahora)
        appCtx?.let { c -> prefs(c).edit().putString("cfg", config.aJson().toString()).apply(); SincroP.cambio(c) }
    }

    /** Lo que llega de otro dispositivo: solo si es más nuevo que lo de aquí. Devuelve si ha cambiado. */
    fun aplicarRemoto(json: String?): Boolean {
        val r = runCatching { ConfigInicio.deJson(JSONObject(json ?: return false)) }.getOrNull() ?: return false
        if (r.mod <= config.mod) return false
        config = r
        appCtx?.let { c -> prefs(c).edit().putString("cfg", config.aJson().toString()).apply() }
        return true
    }

    fun cambiar(f: (ConfigInicio) -> ConfigInicio) = poner(f(config))

    /** El juego destacado: el que fijaste, si sigue; si no, el último al que jugaste (o el último añadido). */
    fun destacado(juegos: List<Juego>): Juego? =
        config.destacado.takeIf { it.isNotBlank() }?.let { k -> juegos.firstOrNull { it.id == k } }
            ?: juegos.filter { it.estado == Estado.JUGANDO }.maxByOrNull { it.ultima }
            ?: juegos.filter { it.ultima > 0 }.maxByOrNull { it.ultima }
            ?: juegos.maxByOrNull { it.anadido }

    /** Los juegos de un estante (sin el destacado, que ya está arriba). */
    fun juegosDe(e: Estante, juegos: List<Juego>, destacado: Juego?, max: Int = 12): List<Juego> = when (e) {
        Estante.JUGANDO -> juegos.filter { it.estado == Estado.JUGANDO }.sortedByDescending { it.ultima }
        Estante.FIJADOS -> config.fijados.mapNotNull { k -> juegos.firstOrNull { it.id == k } }
        Estante.PENDIENTE -> juegos.filter { it.estado == Estado.PENDIENTE }.sortedByDescending { it.anadido }
        Estante.PASADO -> juegos.filter { it.estado == Estado.PASADO }.sortedByDescending { it.finalizado }
        Estante.PC -> juegos.filter { it.enPc }.sortedByDescending { it.ultima }
        Estante.SAGAS -> emptyList()
    }.filter { e == Estante.FIJADOS || it.id != destacado?.id }.take(max)
}
