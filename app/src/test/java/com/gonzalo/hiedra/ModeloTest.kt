package com.gonzalo.hiedra

import org.junit.Assert.*
import org.junit.Test

/** El modelo de Hiedra: un juego con sus lanzadores, sus claves, sus horas por fuente y la biblioteca. Sin móvil ni red. */
class ModeloTest {
    private fun limpia() { Biblioteca.juegos.clear(); Biblioteca.borrados.clear(); Biblioteca.cargada = true; Archivos.limpiarParaPruebas() }
    private fun rom(titulo: String, arch: String = titulo, sis: String = "gba", aqui: Boolean = true, ms: Long = 0, veces: Int = 0): Juego {
        val l = Lanzador.rom(sis, arch)
        if (aqui) Archivos.poner(l.clave, "content://x/$arch", "")
        return nuevoJuego(titulo, lanzador = l, anadido = 50).copy(propias = Horas(ms, veces))
    }

    @Test fun lasClavesDeLosLanzadoresSonLasDeSiempre() {
        assertEquals("rom:gba:zeldae", Lanzador.rom("gba", "Zelda (E)").clave)
        assertEquals("la clave no depende de mayúsculas ni de la carpeta", Lanzador.rom("gba", "zelda (e)").clave, Lanzador.rom("gba", "Zelda (E)").clave)
        assertEquals("app:com.x.y", Lanzador.app("com.x.y").clave)
        assertEquals("web:pokerogue.net", Lanzador.web("https://www.pokerogue.net/").clave)
        assertEquals("pc:steam:1145360", Lanzador.pc("steam:1145360", "pc1").clave)
        assertEquals("juego:hollowknight", claveFicha("Hollow Knight"))
        val l = Lanzador.deJson(Lanzador.rom("gba", "Zelda").copy(emulador = "mgba").aJson())!!
        assertEquals("mgba", l.emulador); assertEquals("rom:gba:zelda", l.clave)
    }

    @Test fun elMenuPequenoSaleDeLosLanzadoresODeLaPlataforma() {
        limpia()
        assertEquals(setOf(Menu.EMULACION), rom("Zelda").menus)
        assertEquals(setOf(Menu.ANDROID), nuevoJuego("Juego", lanzador = Lanzador.app("x.y")).menus)
        assertEquals(setOf(Menu.WEB), nuevoJuego("PokéRogue", lanzador = Lanzador.web("https://pokerogue.net")).menus)
        assertEquals("con dos lanzadores sale en los dos menús", setOf(Menu.EMULACION, Menu.PC), rom("Zelda").conLanzador(Lanzador.pc("steam:1", "pc1")).menus)
        assertEquals(Menu.PC, nuevoJuego("Hades", "PC").menu); assertEquals(Menu.PC, menuDePlataforma("Steam Deck")); assertEquals(Menu.PC, menuDePlataforma(""))
        assertEquals(Menu.EMULACION, menuDePlataforma("Switch")); assertEquals(Menu.EMULACION, menuDePlataforma("PS5")); assertEquals(Menu.EMULACION, menuDePlataforma("Xbox"))
        assertEquals(Menu.ANDROID, menuDePlataforma("Móvil")); assertEquals(Menu.WEB, menuDePlataforma("Navegador"))
    }

    @Test fun unJuegoSinLanzadorYUnaRomQueNoEstaAqui() {
        limpia()
        val f = nuevoJuego("Hollow Knight", "Switch", anadido = 1)
        assertTrue(f.sinLanzador); assertFalse(f.enOtroDispositivo); assertFalse(f.abrible); assertNull(f.tipo)
        assertEquals("sin lanzador, su clave es la de ficha", "juego:hollowknight", f.clave)
        val fuera = rom("Zelda", aqui = false)
        assertTrue(fuera.enOtroDispositivo); assertFalse(fuera.abrible); assertTrue(fuera.uri.isBlank())
        Archivos.poner(fuera.rom!!.clave, "content://b/zelda", "/sd/zelda.gba")
        assertFalse("en cuanto el archivo está aquí, se abre", fuera.enOtroDispositivo); assertTrue(fuera.abrible); assertEquals("/sd/zelda.gba", fuera.ruta)
        assertEquals("con la app además, se abre aunque falte la ROM", false, rom("Metroid", aqui = false).conLanzador(Lanzador.app("x.y")).enOtroDispositivo)
    }

    @Test fun lasHorasSeSumanPorFuenteYLasExternasVanAparte() {
        val j = nuevoJuego("Hades", "PC").copy(propias = Horas(3_000, 1, mapOf("2026-01-01" to 3_000L)), ajenas = mapOf("B" to Horas(1_000, 2, mapOf("2026-01-01" to 1_000L))),
            externas = mapOf(Fuentes.STEAM to Horas(10_000), Fuentes.pc("pc1") to Horas(dias = mapOf("2026-01-02" to 500L))))
        assertEquals(14_000L, j.msJugados); assertEquals(3, j.veces)
        assertEquals(4_000L, j.dias()["2026-01-01"]); assertEquals(500L, j.dias()["2026-01-02"])
        assertEquals("las horas a mano cambian las propias y nunca bajan de las demás", 9_000L, j.conTotalMs(20_000).propias.ms)
        assertEquals(0L, j.conTotalMs(1_000).propias.ms); assertEquals(11_000L, j.conTotalMs(1_000).msJugados)
        assertEquals("las externas se mezclan por máximo", Horas(10_000, 0, mapOf("a" to 5L)), Horas(10_000).max(Horas(2_000, 0, mapOf("a" to 5L))))
    }

    @Test fun unirJuntaLanzadoresHorasYClaves() {
        limpia()
        val ficha = nuevoJuego("Zelda", "Switch", anadido = 10).conEstado(Estado.PASADO, 500).copy(propias = Horas(3_000, 2, mapOf("2026-01-01" to 3_000L)), ajenas = mapOf("B" to Horas(1_000)),
            otrasClaves = listOf("juego:zelda"), externas = mapOf(Fuentes.STEAM to Horas(7_000)))
        val r = rom("Zelda", ms = 1_000, veces = 1).copy(propias = Horas(1_000, 1, mapOf("2026-01-01" to 500L, "2026-01-02" to 500L)), externas = mapOf(Fuentes.STEAM to Horas(5_000)))
        val u = unirJuegos(r, ficha)
        assertEquals(r.id, u.id); assertEquals(listOf(r.rom), u.lanzadores); assertEquals(r.clave, u.clave)
        assertEquals(listOf("juego:zelda"), u.otrasClaves); assertTrue("juego:zelda" in u.claves)
        assertEquals(Estado.PASADO, u.estado); assertEquals(500L, u.finalizado)
        assertEquals("propias sumadas, ajenas sumadas, externas por máximo", 4_000L, u.propias.ms); assertEquals(1_000L, u.ajenas["B"]!!.ms); assertEquals(7_000L, u.externa(Fuentes.STEAM).ms)
        assertEquals(12_000L, u.msJugados); assertEquals(3, u.veces)
        assertEquals(3_500L, u.propias.dias["2026-01-01"]); assertEquals(500L, u.propias.dias["2026-01-02"])
        assertEquals("Switch", u.plataforma); assertEquals(10L, u.anadido)
        // Al unir dos con lanzador, el de origen se suma como otra manera de abrirse
        val dos = unirJuegos(rom("Celeste"), nuevoJuego("Celeste", lanzador = Lanzador.app("com.celeste")))
        assertEquals(2, dos.lanzadores.size); assertEquals(setOf(Menu.EMULACION, Menu.ANDROID), dos.menus); assertTrue(dos.otrasClaves.isEmpty())
    }

    @Test fun laBibliotecaAnadeUnLanzadorAlJuegoQueTocaOCreaUnoNuevo() {
        limpia()
        val ficha = nuevoJuego("Celeste", "PC", anadido = 1)
        Biblioteca.juegos.add(ficha)
        val (j, nuevo) = Biblioteca.anadirLanzador("Celeste", Lanzador.app("com.celeste"))
        assertFalse("se llama igual: va al que ya estaba", nuevo); assertEquals(ficha.id, j.id); assertEquals("PC", j.plataforma)
        assertEquals(j.id, Biblioteca.porClave("app:com.celeste")?.id); assertEquals(1, Biblioteca.juegos.size)
        val (mismo, nuevo2) = Biblioteca.anadirLanzador("Otro nombre", Lanzador.app("com.celeste"))
        assertFalse("la misma clave: el mismo juego, y no se renombra", nuevo2); assertEquals(ficha.id, mismo.id); assertEquals("Celeste", mismo.titulo)
        val (otro, nuevo3) = Biblioteca.anadirLanzador("Hades", Lanzador.rom("switch", "Hades"))
        assertTrue(nuevo3); assertNotEquals(ficha.id, otro.id); assertEquals(2, Biblioteca.juegos.size)
        // Unir dos juegos: el absorbido queda como borrado para los demás dispositivos
        val u = Biblioteca.unir(ficha.id, otro.id)!!
        assertEquals(1, Biblioteca.juegos.size); assertEquals(2, u.lanzadores.size); assertTrue(Biblioteca.borrados.containsKey(otro.id))
        // Quitar un lanzador deja su clave entre las de antes (para Azabache y para no duplicar)
        val sin = u.sinLanzador("rom:switch:hades")
        assertEquals(1, sin.lanzadores.size); assertTrue("rom:switch:hades" in sin.otrasClaves)
        limpia()
    }

    @Test fun todoSeGuardaEnElMovilYViajaConLaCuenta() {
        limpia()
        val j = rom("Zelda").conLanzador(Lanzador.pc("steam:1", "pc1")).copy(portada = "/x/p.jpg", zoom = 1.6f, ox = -0.2f, oy = 0.1f, durHistoria = 600, durExtras = 900, durCompleto = 1500,
            propias = Horas(300 * 60_000L, 3, mapOf("2026-01-01" to 60_000L)), ajenas = mapOf("B" to Horas(5_000)), externas = mapOf(Fuentes.STEAM to Horas(1_000, 0, mapOf("2026-01-02" to 1_000L))),
            otrasClaves = listOf("juego:zelda"), plataforma = "Switch", mod = 5).conEstado(Estado.PASADO, 77)
        assertTrue(j.encuadrada); assertEquals(0.5f, j.avance!!, 0.01f)
        // En el móvil: todo, incluidas las ajenas y la portada local
        val g = Biblioteca.aJuego(Biblioteca.aJson(j))
        assertEquals(j, g)
        // En la cuenta: sin la portada local ni las ajenas (cada dispositivo sube solo las suyas y las externas que conoce)
        val b = Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("B", listOf(j), emptyMap())))!!
        val s = b.juegos[0].juego
        assertEquals(j.id, s.id); assertEquals(j.lanzadores, s.lanzadores); assertEquals(j.otrasClaves, s.otrasClaves); assertEquals(j.propias, s.propias); assertEquals(j.externas, s.externas)
        assertEquals("", s.portada); assertTrue(s.ajenas.isEmpty()); assertEquals(1.6f, s.zoom, 0.001f); assertEquals(1500, s.durCompleto); assertEquals(77L, s.finalizado)
        assertFalse(rom("Metroid").encuadrada); assertNull(rom("Metroid").avance)
    }
}
