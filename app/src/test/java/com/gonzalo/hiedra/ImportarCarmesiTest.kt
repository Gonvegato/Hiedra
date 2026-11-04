package com.gonzalo.hiedra

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Traer la biblioteca de Carmesí (el formato que Carmesí sube a la cuenta) al modelo de Hiedra. Sin móvil ni red. */
class ImportarCarmesiTest {
    /** Un juego tal como lo sube Carmesí (su Mezcla.aJson). */
    private fun g(k: String, t: String, tp: String, mod: Long = 100, msp: Long = 0, vp: Int = 0, dd: Map<String, Long> = emptyMap(), es: String = "PENDIENTE", al: List<String> = emptyList(),
                  s: String = "", a: String = "", pk: String = "", u: String = "", o: Int = 0, pck: String = "", pcd: String = "", fav: Boolean = false, fi: Long = 0, pl: String = "", pu: String = "") =
        JSONObject().put("k", k).put("t", t).put("tp", tp).put("s", s).put("pk", pk).put("u", u).put("a", a).put("f", fav).put("te", es == "PASADO").put("fi", fi).put("n", 8.5).put("no", "apuntes").put("m", "")
            .put("o", o).put("an", 10L).put("mod", mod).put("msp", msp).put("vp", vp).put("ul", 50L).put("es", es).put("dd", JSONObject().also { d -> dd.forEach { (x, y) -> d.put(x, y) } })
            .put("pl", pl).put("al", JSONArray().also { x -> al.forEach { x.put(it) } }).put("pck", pck).put("pcd", pcd).put("pu", pu).put("ez", 1.3).put("eox", 0.1).put("eoy", 0.0).put("dh", 600).put("de", 0).put("dc", 0)
    private fun blob(dev: String, vararg js: JSONObject, borrados: Map<String, Long> = emptyMap(), extra: (JSONObject) -> Unit = {}) = JSONObject().put("v", 1).put("dev", dev)
        .put("borrados", JSONObject().also { o -> borrados.forEach { (k, v) -> o.put(k, v) } }).put("juegos", JSONArray().also { a -> js.forEach { a.put(it) } }).also(extra)

    @Test fun cadaTipoDeCarmesiPasaASuLanzadorYLaFichaAUnJuegoSinLanzador() {
        val b = blob("movil",
            g("rom:gba:zeldae", "Zelda", "ROM", s = "gba", a = "Zelda (E)", es = "PASADO", fi = 999, fav = true, al = listOf("juego:zelda"), msp = 5_000, vp = 2, dd = mapOf("2026-01-01" to 5_000L)),
            g("app:com.x", "Stardew", "APP", pk = "com.x"),
            g("web:pokerogue.net", "PokéRogue", "WEB", u = "https://pokerogue.net", o = 1),
            g("pc:steam:1", "Hades", "PC", pck = "steam:1", pcd = "pc1", pu = "https://x/h.jpg"),
            g("juego:celeste", "Celeste", "FICHA", pl = "Switch", es = "JUGANDO"))
        val c = ImportarCarmesi.candidatos(mapOf("movil" to b), ahora = 1_000).associateBy { it.juego.titulo }
        assertEquals(5, c.size)
        val z = c["Zelda"]!!
        assertEquals(Lanzador.rom("gba", "Zelda (E)"), z.juego.rom); assertEquals("rom:gba:zeldae", z.juego.clave); assertEquals(listOf("juego:zelda"), z.juego.otrasClaves)
        assertEquals(listOf("rom:gba:zeldae", "juego:zelda"), z.claves); assertEquals(Estado.PASADO, z.juego.estado); assertEquals(999L, z.juego.finalizado); assertTrue(z.juego.favorito)
        assertEquals("las horas de ese dispositivo de Carmesí quedan como fuente aparte", 5_000L, z.juego.externa(Fuentes.carmesi("movil")).ms); assertEquals(2, z.juego.veces); assertEquals(5_000L, z.juego.dias()["2026-01-01"])
        assertTrue("nada en las propias", z.juego.propias.vacias); assertEquals(1.3f, z.juego.zoom, 0.001f); assertEquals(600, z.juego.durHistoria); assertEquals(10L, z.juego.anadido)
        assertEquals("el kid de la portada es el de Carmesí (SHA-1 de la clave)", 16, z.kid.length)
        assertEquals("app:com.x", c["Stardew"]!!.juego.clave); assertEquals(Menu.ANDROID, c["Stardew"]!!.juego.menu)
        assertEquals(1, c["PokéRogue"]!!.juego.web!!.orientacion); assertEquals("web:pokerogue.net", c["PokéRogue"]!!.juego.clave)
        assertEquals("pc1", c["Hades"]!!.juego.pc!!.pcDev); assertEquals("https://x/h.jpg", c["Hades"]!!.juego.portadaUrl)
        val ce = c["Celeste"]!!.juego
        assertTrue(ce.sinLanzador); assertEquals(listOf("juego:celeste"), ce.otrasClaves); assertEquals("Switch", ce.plataforma); assertEquals(Estado.JUGANDO, ce.estado)
    }

    @Test fun variosDispositivosDeCarmesiSonUnJuegoConLasHorasDeCadaUno() {
        val a = blob("A", g("rom:gba:zelda", "Zelda", "ROM", s = "gba", a = "Zelda", mod = 100, msp = 3_000))
        val b = blob("B", g("rom:gba:zelda", "Zelda DX", "ROM", s = "gba", a = "Zelda", mod = 200, msp = 7_000, es = "PASADO"), g("app:y", "Otro", "APP", pk = "y"), borrados = mapOf("app:y" to 900L))
        val c = ImportarCarmesi.candidatos(mapOf("A" to a, "B" to b))
        assertEquals("el borrado en Carmesí no entra", 1, c.size)
        val z = c[0].juego
        assertEquals("gana el cambio más reciente", "Zelda DX", z.titulo); assertEquals(Estado.PASADO, z.estado)
        assertEquals(3_000L, z.externa(Fuentes.carmesi("A")).ms); assertEquals(7_000L, z.externa(Fuentes.carmesi("B")).ms); assertEquals(10_000L, z.msJugados)
    }

    @Test fun lasSagasLaPortadaYLaAparienciaSeLeen() {
        val b = blob("A", g("rom:gba:zelda", "Zelda", "ROM", s = "gba", a = "Zelda"), g("juego:celeste", "Celeste", "FICHA")) { o ->
            o.put("sg", JSONArray().put(JSONObject().put("id", "s1").put("n", "Pokémon").put("mod", 5).put("k", JSONArray().put("rom:gba:zelda").put("juego:celeste").put("rom:gba:nadie")))
                .put(JSONObject().put("id", "s2").put("n", "Borrada").put("mod", 5).put("k", JSONArray().put("rom:gba:zelda"))))
            o.put("sgb", JSONObject().put("s2", 9L))
            o.put("ini", JSONObject().put("mod", 3).put("d", "juego:celeste").put("f", JSONArray()).put("k", JSONArray().put("rom:gba:zelda")))
            o.put("aj", JSONObject().put("at", 50).put("skin", JSONObject().put("fondoId", "granate")))
        }
        val s = ImportarCarmesi.sagasDe(mapOf("A" to b))
        assertEquals(1, s.size); assertEquals("Pokémon", s[0].first.nombre); assertEquals(listOf("rom:gba:zelda", "juego:celeste", "rom:gba:nadie"), s[0].second)
        assertEquals("juego:celeste", ImportarCarmesi.inicioDe(mapOf("A" to b))!!.getString("d"))
        assertEquals(50L, ImportarCarmesi.ajustesDe(mapOf("A" to b))!!.getLong("at"))
    }

    @Test fun loQueYaTienesSeCompletaSinDuplicar() {
        Biblioteca.juegos.clear(); Biblioteca.borrados.clear(); Biblioteca.cargada = true; Archivos.limpiarParaPruebas()
        val aqui = nuevoJuego("Celeste", "PC", anadido = 1).copy(otrasClaves = listOf("juego:celeste"), propias = Horas(1_000))
        Biblioteca.juegos.add(aqui)
        val c = ImportarCarmesi.candidatos(mapOf("A" to blob("A", g("app:com.celeste", "Celeste", "APP", pk = "com.celeste", es = "PASADO", fi = 77, msp = 4_000, al = listOf("juego:celeste")))))[0]
        assertEquals("se reconoce por su clave antigua", aqui.id, ImportarCarmesi.existente(c)?.id)
        val n = ImportarCarmesi.completar(aqui, c)
        assertEquals("gana el lanzador de Carmesí", "app:com.celeste", n.app!!.clave); assertEquals(Estado.PASADO, n.estado); assertEquals(77L, n.finalizado)
        assertEquals("las horas de aquí se quedan y las de Carmesí van aparte", 1_000L, n.propias.ms); assertEquals(4_000L, n.externa(Fuentes.carmesi("A")).ms)
        assertEquals("PC", n.plataforma); assertEquals(listOf("juego:celeste"), n.otrasClaves)
        assertEquals("repetir no cambia nada", n, ImportarCarmesi.completar(n, c))
        Biblioteca.juegos.clear()
    }
}
