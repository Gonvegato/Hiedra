package com.gonzalo.hiedra

import org.junit.Assert.*
import org.junit.Test

/** Las reglas de mezcla entre tus Hiedras (por id, horas por dispositivo, externas por máximo, borrados), sin móvil ni red. */
class MezclaTest {
    private fun rom(id: String, titulo: String, arch: String = titulo, mod: Long = 100, ms: Long = 0, veces: Int = 0, fav: Boolean = false, externas: Map<String, Horas> = emptyMap()) =
        Juego(id, titulo, lanzadores = listOf(Lanzador.rom("gba", arch)), mod = mod, propias = Horas(ms, veces), favorito = fav, anadido = 50, externas = externas)

    private fun blobDe(dev: String, js: List<Juego>, bor: Map<String, Long> = emptyMap()) = Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob(dev, js, bor)))!!
    private fun mezclar(l: List<Juego>, r: List<Blob>, b: Map<String, Long> = emptyMap(), ahora: Long = 1_000) = Mezcla.mezclar(l, b, r, "A", ahora)

    @Test fun unJuegoNuevoDeOtroDispositivoSeCreaConElMismoIdYSinArchivo() {
        Archivos.limpiarParaPruebas()
        val r = mezclar(emptyList(), listOf(blobDe("B", listOf(rom("j1", "Zelda", ms = 5_000, veces = 2)))))
        assertTrue(r.cambio); assertEquals(1, r.juegos.size)
        val j = r.juegos[0]
        assertEquals("j1", j.id); assertTrue(j.enOtroDispositivo); assertEquals(5_000L, j.msJugados); assertEquals(2, j.veces); assertEquals(0L, j.propias.ms)
        assertEquals("las horas de B quedan como de B", 5_000L, j.ajenas["B"]!!.ms)
    }

    @Test fun lasHorasSeSumanPorDispositivoYNoDosVeces() {
        val mia = rom("j1", "Zelda", ms = 3_000, veces = 1)
        val r = mezclar(listOf(mia), listOf(blobDe("B", listOf(rom("j1", "Zelda", ms = 7_000, veces = 4)))))
        assertEquals(10_000L, r.juegos[0].msJugados); assertEquals(5, r.juegos[0].veces); assertEquals(3_000L, r.juegos[0].propias.ms)
        val r2 = mezclar(r.juegos, listOf(blobDe("B", listOf(rom("j1", "Zelda", ms = 7_000, veces = 4)))))
        assertFalse("repetir la mezcla no cambia nada ni suma dos veces", r2.cambio); assertEquals(10_000L, r2.juegos[0].msJugados)
        val r3 = mezclar(r.juegos, listOf(blobDe("B", listOf(rom("j1", "Zelda", ms = 8_000, veces = 5))), blobDe("C", listOf(rom("j1", "Zelda", ms = 1_000)))))
        assertEquals("B sube más y C aparece: cada uno lo suyo", 12_000L, r3.juegos[0].msJugados); assertEquals(1_000L, r3.juegos[0].ajenas["C"]!!.ms)
    }

    @Test fun lasFuentesExternasSeQuedanConElMaximo() {
        val mia = rom("j1", "Hades", externas = mapOf(Fuentes.STEAM to Horas(10_000, 0, mapOf("2026-01-01" to 100L))))
        val deB = rom("j1", "Hades", externas = mapOf(Fuentes.STEAM to Horas(12_000, 0, mapOf("2026-01-01" to 50L, "2026-01-02" to 70L)), Fuentes.pc("pc1") to Horas(3_000)))
        val j = mezclar(listOf(mia), listOf(blobDe("B", listOf(deB)))).juegos[0]
        assertEquals(12_000L, j.externa(Fuentes.STEAM).ms); assertEquals(100L, j.externa(Fuentes.STEAM).dias["2026-01-01"]); assertEquals(70L, j.externa(Fuentes.STEAM).dias["2026-01-02"])
        assertEquals(3_000L, j.externa(Fuentes.pc("pc1")).ms); assertEquals("no se suman dos veces", 15_000L, j.msJugados)
        // Las de Carmesí (importadas) también viajan y se mezclan por máximo
        val c = mezclar(emptyList(), listOf(blobDe("B", listOf(rom("j2", "Zelda", externas = mapOf(Fuentes.carmesi("viejo") to Horas(9_000, 3))))))).juegos[0]
        assertEquals(9_000L, c.externa(Fuentes.carmesi("viejo")).ms); assertEquals(3, c.veces)
    }

    @Test fun ganaElCambioMasRecienteEnLoEditable() {
        val l = rom("j1", "Zelda", mod = 100, fav = false)
        val remoto = rom("j1", "Zelda DX", mod = 200, fav = true).conLanzador(Lanzador.app("com.zelda")).copy(otrasClaves = listOf("juego:zelda"), plataforma = "Switch")
        val r = mezclar(listOf(l), listOf(blobDe("B", listOf(remoto))))
        val j = r.juegos[0]
        assertEquals("Zelda DX", j.titulo); assertTrue(j.favorito); assertEquals(2, j.lanzadores.size); assertEquals(listOf("juego:zelda"), j.otrasClaves); assertEquals("Switch", j.plataforma); assertEquals(200L, j.mod)
        val r2 = mezclar(listOf(l.copy(mod = 300)), listOf(blobDe("B", listOf(remoto))))
        assertEquals("Zelda", r2.juegos[0].titulo); assertFalse(r2.juegos[0].favorito); assertEquals(1, r2.juegos[0].lanzadores.size)
    }

    @Test fun elBorradoSePropagaSalvoQueSeCambieDespues() {
        val l = rom("j1", "Zelda", mod = 100)
        val r = mezclar(listOf(l), listOf(blobDe("B", emptyList(), mapOf("j1" to 500L))))
        assertTrue(r.juegos.isEmpty()); assertEquals(500L, r.borrados["j1"])
        val r2 = mezclar(listOf(l.copy(mod = 800)), listOf(blobDe("B", emptyList(), mapOf("j1" to 500L))))
        assertEquals(1, r2.juegos.size)
        val r3 = mezclar(emptyList(), listOf(blobDe("C", listOf(rom("j1", "Zelda", mod = 100)))), mapOf("j1" to 500L))
        assertTrue("un juego borrado no resucita desde un dispositivo que lo sigue teniendo", r3.juegos.isEmpty())
    }

    @Test fun losPropiosBlobsSeIgnoranYLosBorradosViejosSeOlvidan() {
        val r = mezclar(emptyList(), listOf(blobDe("A", listOf(rom("j1", "Zelda")))))
        assertFalse(r.cambio); assertTrue(r.juegos.isEmpty())
        assertTrue(mezclar(emptyList(), emptyList(), mapOf("j9" to 1L), ahora = Mezcla.OLVIDO + 10).borrados.isEmpty())
        assertNull(Mezcla.deJson("{no es json")); assertNull(Mezcla.deJson(""))
    }

    @Test fun losNuevosVanAlPrincipioYLosDeAquiEnSuSitio() {
        val l = listOf(rom("j1", "A"), rom("j2", "B"))
        val r = mezclar(l, listOf(blobDe("B", listOf(rom("j3", "C").copy(anadido = 10), rom("j4", "D").copy(anadido = 20)))))
        assertEquals(listOf("j4", "j3", "j1", "j2"), r.juegos.map { it.id })
    }

    @Test fun laAparienciaLasSagasYLaPortadaViajan() {
        val aj = """{"at":50,"skin":{"fondoId":"granate","acento":"azul","escala":112}}"""
        val b = Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("B", emptyList(), emptyMap(), ajustes = aj, sagas = listOf(Saga("s1", "Zelda", listOf("j1", "j2"), 7)), sagasBorradas = mapOf("s2" to 9L), inicio = """{"mod":3,"d":"j1","f":[],"k":["j2"]}""")))!!
        assertEquals(50L, org.json.JSONObject(b.ajustes).getLong("at"))
        assertEquals(listOf("j1", "j2"), b.sagas[0].juegos); assertEquals(9L, b.sagasBorradas["s2"]); assertEquals("j1", org.json.JSONObject(b.inicio).getString("d"))
        assertEquals("sin apariencia no añade nada", "", Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("B", emptyList(), emptyMap())))!!.ajustes)
        val rs = Mezcla.mezclarSagas(listOf(Saga("s1", "Zelda", listOf("j1"), 3)), emptyMap(), listOf(b), "A", 1_000)
        assertTrue(rs.cambio); assertEquals("gana la más reciente", listOf("j1", "j2"), rs.sagas[0].juegos); assertEquals(9L, rs.borradas["s2"])
    }
}
