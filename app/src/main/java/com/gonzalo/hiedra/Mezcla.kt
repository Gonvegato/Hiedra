package com.gonzalo.hiedra

import org.json.JSONArray
import org.json.JSONObject

/*
 * Mezclar la biblioteca de tus dispositivos (docs/CONTRATO-DATOS.md). Cada dispositivo sube a tu cuenta SU biblioteca (un Blob) y baja la de los demás:
 *  - los juegos se emparejan por su id (el mismo en todos los dispositivos);
 *  - lo editable (título, plataforma, estado, favorito, lanzadores, portada de internet, encuadre, duraciones…) lo decide el cambio más reciente;
 *  - las horas de cada dispositivo se guardan por dispositivo y el total es la suma; las de las fuentes externas (Steam, cada PC) son una sola
 *    cosa: de lo que suban varios dispositivos se queda el máximo;
 *  - lo que quitas viaja como «borrado» (con su hora) y se quita en los demás, salvo que se haya vuelto a cambiar después.
 */

/** Lo que un dispositivo sube de cada juego: lo compartido, SUS horas y las externas que conoce (sin la portada local ni lo de otros dispositivos). */
class JuegoSync(val juego: Juego) {
    val id: String get() = juego.id
    val mod: Long get() = juego.mod
}

/** La biblioteca de un dispositivo, tal como la sube a la cuenta. */
class Blob(val dev: String, val juegos: List<JuegoSync>, val borrados: Map<String, Long>, val ajustes: String = "",
           val sagas: List<Saga> = emptyList(), val sagasBorradas: Map<String, Long> = emptyMap(), val inicio: String = "")

class ResMezcla(val juegos: List<Juego>, val borrados: Map<String, Long>, val cambio: Boolean)

object Mezcla {
    /** Los borrados se olvidan a los 180 días (un dispositivo que lleve más sin conectar ya no los verá). */
    const val OLVIDO = 180L * 86_400_000L

    fun aBlob(dev: String, juegos: List<Juego>, borrados: Map<String, Long>, ajustes: String = "", sagas: List<Saga> = emptyList(), sagasBorradas: Map<String, Long> = emptyMap(), inicio: String = ""): Blob =
        Blob(dev, juegos.filter { it.titulo.isNotBlank() }.distinctBy { it.id }.map { j -> JuegoSync(j.copy(portada = "", ajenas = emptyMap(), propias = j.propias.copy(dias = j.propias.dias.entries.sortedByDescending { it.key }.take(400).associate { it.key to it.value }))) },
            borrados, ajustes, sagas, sagasBorradas, inicio)

    private fun juegoAJson(j: Juego): JSONObject = JSONObject().put("id", j.id).put("t", j.titulo).put("pl", j.plataforma).put("es", j.estado.name).put("f", j.favorito).put("fi", j.finalizado)
        .put("an", j.anadido).put("ul", j.ultima).put("mod", j.mod).put("pu", j.portadaUrl)
        .put("ez", j.zoom.toDouble()).put("eox", j.ox.toDouble()).put("eoy", j.oy.toDouble()).put("dh", j.durHistoria).put("de", j.durExtras).put("dc", j.durCompleto)
        .put("k", JSONArray().also { a -> j.otrasClaves.forEach { a.put(it) } })
        .put("ln", JSONArray().also { a -> j.lanzadores.forEach { a.put(it.aJson()) } })
        // La portada automática (la caja de libretro de una ROM), para que Azabache enseñe la misma que aquí cuando no hay otra
        .put("pa", if (j.portadaUrl.isBlank() && Ajustes.portadasLibretro) urlsLibretro(j).firstOrNull().orEmpty() else "")
        .put("msp", j.propias.ms).put("vp", j.propias.veces).put("dd", JSONObject().also { d -> j.propias.dias.forEach { (k, v) -> d.put(k, v) } })
        .put("ex", JSONObject().also { o -> j.externas.forEach { (k, v) -> o.put(k, v.aJson()) } })

    private fun juegoDeJson(g: JSONObject): Juego? {
        val id = g.optString("id").ifBlank { return null }
        return Juego(
            id = id, titulo = g.optString("t"), plataforma = g.optString("pl"), estado = Estado.entries.firstOrNull { it.name == g.optString("es") } ?: Estado.PENDIENTE,
            favorito = g.optBoolean("f"), finalizado = g.optLong("fi"), anadido = g.optLong("an"), ultima = g.optLong("ul"), mod = g.optLong("mod"), portadaUrl = g.optString("pu"),
            zoom = g.optDouble("ez", 1.0).toFloat(), ox = g.optDouble("eox", 0.0).toFloat(), oy = g.optDouble("eoy", 0.0).toFloat(), durHistoria = g.optInt("dh"), durExtras = g.optInt("de"), durCompleto = g.optInt("dc"),
            otrasClaves = g.optJSONArray("k")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList(),
            lanzadores = g.optJSONArray("ln")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Lanzador::deJson) } } ?: emptyList(),
            propias = Horas(g.optLong("msp"), g.optInt("vp"), g.optJSONObject("dd")?.let { d -> d.keys().asSequence().associateWith { k -> d.optLong(k) } } ?: emptyMap()),
            externas = g.optJSONObject("ex")?.let { a -> a.keys().asSequence().associateWith { k -> Horas.deJson(a.optJSONObject(k)) } } ?: emptyMap(),
        )
    }

    fun aJson(b: Blob): String = JSONObject().put("v", 1).put("dev", b.dev)
        .put("borrados", JSONObject().also { o -> b.borrados.forEach { (k, v) -> o.put(k, v) } })
        .put("juegos", JSONArray().also { a -> b.juegos.forEach { a.put(juegoAJson(it.juego)) } })
        .apply {
            if (b.ajustes.isNotBlank()) put("aj", JSONObject(b.ajustes))
            if (b.sagas.isNotEmpty()) put("sg", JSONArray().also { a -> b.sagas.forEach { a.put(it.aJson()) } })
            if (b.sagasBorradas.isNotEmpty()) put("sgb", JSONObject().also { x -> b.sagasBorradas.forEach { (k, v) -> x.put(k, v) } })
            if (b.inicio.isNotBlank()) put("ini", JSONObject(b.inicio))
        }.toString()

    fun deJson(s: String?): Blob? = try {
        if (s.isNullOrBlank()) null else {
            val o = JSONObject(s)
            val ja = o.optJSONArray("juegos") ?: JSONArray()
            val bo = o.optJSONObject("borrados")
            Blob(o.optString("dev"), (0 until ja.length()).mapNotNull { i -> juegoDeJson(ja.getJSONObject(i))?.let { JuegoSync(it) } },
                bo?.keys()?.asSequence()?.associateWith { bo.optLong(it) } ?: emptyMap(),
                o.optJSONObject("aj")?.toString().orEmpty(),
                o.optJSONArray("sg")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(Saga::deJson) } } ?: emptyList(),
                o.optJSONObject("sgb")?.let { x -> x.keys().asSequence().associateWith { x.optLong(it) } } ?: emptyMap(),
                o.optJSONObject("ini")?.toString().orEmpty())
        }
    } catch (e: Exception) { null }

    /**
     * Mezcla [locales] con los [remotos] (los de los OTROS dispositivos que han cambiado). [yo] es este dispositivo (sus propios blobs se ignoran).
     * Devuelve la biblioteca nueva, los borrados y si ha cambiado algo.
     */
    fun mezclar(locales: List<Juego>, borrados: Map<String, Long>, remotos: List<Blob>, yo: String, ahora: Long = System.currentTimeMillis()): ResMezcla {
        val otros = remotos.filter { it.dev != yo }
        val bor = HashMap(borrados)
        otros.forEach { r -> r.borrados.forEach { (k, t) -> if (t > (bor[k] ?: 0L)) bor[k] = t } }
        bor.entries.removeAll { ahora - it.value > OLVIDO }

        // Lo de los demás, por id (sin lo que se borró después de su último cambio)
        val remotoDe = LinkedHashMap<String, MutableList<Pair<String, Juego>>>()
        otros.forEach { r -> r.juegos.forEach { g -> if ((bor[g.id] ?: 0L) <= g.mod) remotoDe.getOrPut(g.id) { ArrayList() }.add(r.dev to g.juego) } }

        val vistos = HashSet<String>()
        val salida = ArrayList<Juego>()
        for (l in locales) {
            if (!vistos.add(l.id)) continue
            if ((bor[l.id] ?: 0L) > l.mod) continue               // quitado en otro dispositivo después de tu último cambio
            val rs = remotoDe[l.id]
            salida += if (rs == null) l else unir(l, rs)
        }
        for ((id, rs) in remotoDe) {
            if (id in vistos) continue
            vistos += id
            salida += crear(rs)
        }
        val ids = locales.map { it.id }.toSet()
        val finales = salida.filter { it.id !in ids }.sortedByDescending { it.anadido } + salida.filter { it.id in ids }
        return ResMezcla(finales, bor, finales != locales || bor != borrados)
    }

    private fun externasDe(base: Map<String, Horas>, rs: List<Pair<String, Juego>>): Map<String, Horas> {
        val out = HashMap(base)
        rs.forEach { (_, g) -> g.externas.forEach { (k, h) -> out[k] = (out[k] ?: Horas()).max(h) } }
        return out
    }

    private fun unir(l: Juego, rs: List<Pair<String, Juego>>): Juego {
        val g = rs.maxByOrNull { it.second.mod }!!.second
        val base = if (g.mod > l.mod) l.copy(titulo = g.titulo.ifBlank { l.titulo }, plataforma = g.plataforma, estado = g.estado, favorito = g.favorito, finalizado = g.finalizado,
            portadaUrl = g.portadaUrl.ifBlank { l.portadaUrl }, zoom = g.zoom, ox = g.ox, oy = g.oy, durHistoria = g.durHistoria, durExtras = g.durExtras, durCompleto = g.durCompleto,
            otrasClaves = g.otrasClaves, lanzadores = g.lanzadores, mod = g.mod) else l
        val ajenas = l.ajenas + rs.associate { it.first to it.second.propias }
        val anadidos = (listOf(l.anadido) + rs.map { it.second.anadido }).filter { it > 0 }
        return base.copy(ajenas = ajenas, externas = externasDe(l.externas, rs), ultima = maxOf(l.ultima, rs.maxOf { it.second.ultima }), anadido = anadidos.minOrNull() ?: l.anadido)
    }

    private fun crear(rs: List<Pair<String, Juego>>): Juego {
        val g = rs.maxByOrNull { it.second.mod }!!.second
        return g.copy(portada = "", propias = Horas(), ajenas = rs.associate { it.first to it.second.propias }, externas = externasDe(emptyMap(), rs),
            anadido = rs.map { it.second.anadido }.filter { it > 0 }.minOrNull() ?: g.anadido, ultima = rs.maxOf { it.second.ultima })
    }

    class ResSagas(val sagas: List<Saga>, val borradas: Map<String, Long>, val cambio: Boolean)

    /** Mezcla las sagas de este dispositivo con las de los demás: de cada una gana la que se cambió más tarde; lo borrado se borra salvo que se haya vuelto a cambiar después. */
    fun mezclarSagas(locales: List<Saga>, borradas: Map<String, Long>, remotos: List<Blob>, yo: String, ahora: Long = System.currentTimeMillis()): ResSagas {
        val otros = remotos.filter { it.dev != yo }
        val bor = HashMap(borradas)
        otros.forEach { r -> r.sagasBorradas.forEach { (k, t) -> if (t > (bor[k] ?: 0L)) bor[k] = t } }
        bor.entries.removeAll { ahora - it.value > OLVIDO }
        val por = LinkedHashMap<String, Saga>()
        for (s in locales + otros.flatMap { it.sagas }) { val cur = por[s.id]; if (cur == null || s.mod > cur.mod) por[s.id] = s }
        val vivas = por.values.filter { (bor[it.id] ?: 0L) <= it.mod }
        val orden = locales.map { it.id }
        val finales = vivas.sortedBy { s -> orden.indexOf(s.id).let { if (it < 0) Int.MAX_VALUE else it } }
        return ResSagas(finales, bor, finales != locales || bor != borradas)
    }
}
