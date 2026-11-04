package com.gonzalo.hiedra

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Traer los juegos de Atrio: estados, horas, plataforma, portadas, sagas y lo que ya tenías. Sin móvil ni red. */
class ImportarAtrioTest {
    private fun arr(vararg o: JSONObject) = JSONArray().also { a -> o.forEach { a.put(it) } }
    private fun valorado(id: Long, nombre: String, general: Int = 80, plat: String = "PC", min: Int = 0, fav: Boolean = false, cover: String = "https://x/c$id.jpg", upd: Long = 100, end: Long = 2000L) =
        JSONObject().put("id", id).put("name", nombre).put("cover", cover).put("general", general).put("gfx", 50).put("story", 50).put("music", 50).put("gameplay", 50).put("review", "")
            .put("fav", fav).put("platform", plat).put("minutes", min).put("start", 1000L).put("end", end).put("updated", upd).put("ttbMain", 600)
    private fun enCurso(id: Long, nombre: String, status: Int, plat: String = "Switch", min: Int = 0, upd: Long = 100) =
        JSONObject().put("id", id).put("name", nombre).put("cover", "").put("hero", "").put("status", status).put("platform", plat).put("minutes", min).put("start", 500L).put("updated", upd)
    private fun saga(id: Long, nombre: String, kind: Int, vararg items: Long) = JSONObject().put("id", id).put("name", nombre).put("kind", kind).put("items", JSONArray().also { a -> items.forEach { a.put(it) } }).put("updated", 5L)

    @Test fun estadosHorasYPlataforma() {
        val blob = JSONObject()
            .put("games", arr(valorado(1, "Hades", general = 87, min = 1500, fav = true), valorado(2, "Celeste", general = 92, plat = "Switch")))
            .put("plays", arr(enCurso(3, "Zelda", 1, min = 90), enCurso(4, "Metroid", 0), enCurso(5, "Bayonetta", 2)))
        val c = ImportarAtrio.candidatos(listOf(blob)).associateBy { it.juego.titulo }
        assertEquals(5, c.size)
        val h = c["Hades"]!!.juego
        assertEquals(Estado.PASADO, h.estado); assertEquals(2000L, h.finalizado); assertTrue(h.favorito)
        assertEquals("las horas de Atrio cuentan como de este dispositivo", 1500 * 60_000L, h.propias.ms); assertEquals("PC", h.plataforma); assertEquals(Menu.PC, h.menu)
        assertEquals("sin lanzador, con la clave de Atrio", true, h.sinLanzador); assertEquals(listOf("juego:hades"), h.otrasClaves); assertEquals(600, h.durHistoria)
        assertEquals("https://x/c1.jpg", c["Hades"]!!.portadaUrl)
        assertEquals(Menu.EMULACION, c["Celeste"]!!.juego.menu)
        assertEquals(Estado.JUGANDO, c["Zelda"]!!.juego.estado); assertEquals(90 * 60_000L, c["Zelda"]!!.juego.msJugados)
        assertEquals(Estado.PENDIENTE, c["Metroid"]!!.juego.estado)
        assertEquals(Estado.ABANDONADO, c["Bayonetta"]!!.juego.estado)
    }

    @Test fun losBorradosEnAtrioNoVienenYLaVersionMasNuevaGana() {
        val a = JSONObject().put("games", arr(valorado(1, "Hades", upd = 100), valorado(2, "Celeste", upd = 100))).put("tombs", JSONObject().put("games:2", 500L))
        val b = JSONObject().put("games", arr(valorado(1, "Hades II", upd = 900)))
        assertEquals(listOf("Hades II"), ImportarAtrio.candidatos(listOf(a, b)).map { it.juego.titulo })
    }

    @Test fun laFotoTuyaSeBuscaEnElIndiceDeKDrive() {
        val blob = JSONObject().put("games", arr(valorado(1, "Hades", cover = "file:///data/x/foto_12.jpg")))
            .put("nube", arr(JSONObject().put("k", "foto:foto_12.jpg").put("f", 777L).put("n", "foto_12.jpg").put("s", 10L).put("updated", 1L)))
        val c = ImportarAtrio.candidatos(listOf(blob))[0]
        assertEquals(777L, c.foto!!.first); assertEquals("", c.portadaUrl)
    }

    @Test fun lasSagasDeJuegosVienenConSusJuegosYLasDeLibrosNo() {
        val blob = JSONObject().put("games", arr(valorado(1, "Hades"), valorado(2, "Celeste"))).put("plays", arr(enCurso(3, "Zelda", 1)))
            .put("sagas", arr(saga(10, "Indies", 0, 2, 1, 99), saga(11, "Dune", 1, 1, 2)))
        val cands = ImportarAtrio.candidatos(listOf(blob))
        val s = ImportarAtrio.sagasDe(listOf(blob), cands)
        val idDe = cands.associate { it.atrioId to it.juego.id }
        assertEquals(1, s.size); assertEquals("atrio10", s[0].id); assertEquals(listOf(idDe[2L], idDe[1L]), s[0].juegos)
    }

    @Test fun loQueYaTienesSeCompletaSinDuplicarYSeQuedaConLaClaveDeAtrio() {
        Biblioteca.juegos.clear(); Biblioteca.borrados.clear(); Biblioteca.cargada = true
        val rom = Juego("r1", "Hades", lanzadores = listOf(Lanzador.rom("switch", "Hades")), anadido = 50)
        Biblioteca.juegos.add(rom)
        val c = ImportarAtrio.candidatos(listOf(JSONObject().put("games", arr(valorado(1, "Hades", general = 90, min = 600, plat = "Switch")))))[0]
        assertEquals("r1", ImportarAtrio.existente(c)?.id)
        val n = ImportarAtrio.completar(rom, c)
        assertEquals(Estado.PASADO, n.estado); assertEquals(600 * 60_000L, n.msJugados); assertEquals("Switch", n.plataforma)
        assertEquals(listOf("juego:hades"), n.otrasClaves); assertEquals(rom.clave, n.clave); assertEquals(1, n.lanzadores.size)
        val jugado = rom.copy(estado = Estado.JUGANDO, propias = Horas(1_000))
        val m = ImportarAtrio.completar(jugado, c)
        assertEquals("pasado en Atrio gana a jugando aquí", Estado.PASADO, m.estado); assertEquals("con horas ya puestas, no se pisan", 1_000L, m.msJugados)
        Biblioteca.juegos.clear()
    }
}
