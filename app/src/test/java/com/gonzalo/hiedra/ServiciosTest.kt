package com.gonzalo.hiedra

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Las horas de Steam (fuente externa) y las claves de Atrio. Sin red. */
class ServiciosTest {
    @Test fun lasHorasDeSteamSeLeenYSePonenPorNombreSinPisarAlPc() {
        val txt = """{"response":{"game_count":3,"games":[{"appid":1,"name":"Hades","playtime_forever":2400},{"appid":2,"name":"Celeste","playtime_forever":0},{"appid":3,"name":"Elden Ring","playtime_forever":5000}]}}"""
        val o = Steam.deJson(txt)
        assertEquals(3, o.size); assertEquals(2400, o[0].minutes)
        Biblioteca.juegos.clear(); Biblioteca.borrados.clear(); Biblioteca.cargada = true
        Biblioteca.juegos.add(nuevoJuego("Hades", "PC", anadido = 1).copy(propias = Horas(2000 * 60_000L), externas = mapOf(Fuentes.carmesi("viejo") to Horas(100 * 60_000L))))
        Biblioteca.juegos.add(nuevoJuego("Celeste", "PC", anadido = 2))
        Biblioteca.juegos.add(Juego("pc1", "Elden Ring", lanzadores = listOf(Lanzador.pc("steam:3", "pc1")), externas = mapOf(Fuentes.pc("pc1") to Horas(5100 * 60_000L))))
        assertEquals(1, Steam.aplicar(o))
        val h = Biblioteca.porTitulo("Hades")!!
        assertEquals("Steam es una fuente más: no pisa las propias ni las de Carmesí", 2400 * 60_000L, h.externa(Fuentes.STEAM).ms); assertEquals(2000 * 60_000L, h.propias.ms); assertEquals(4500 * 60_000L, h.msJugados)
        assertEquals("sin horas en Steam, nada", 0L, Biblioteca.porTitulo("Celeste")!!.msJugados)
        assertEquals("el del PC ya las trae: no se suman dos veces", 5100 * 60_000L, Biblioteca.porClave("pc:steam:3")!!.msJugados)
        assertEquals("repetir no cambia nada", 0, Steam.aplicar(o))
        assertTrue(runCatching { Steam.deJson("""{"response":{}}""") }.exceptionOrNull() is IllegalArgumentException)
        Biblioteca.juegos.clear()
    }

    @Test fun lasClavesDeAtrioSeCogenDeSusDatos() {
        val b = JSONObject().put("keys", JSONObject().put("sgdb", "abc").put("steamKey", "k1").put("steamUser", "https://steamcommunity.com/id/gonzalo/"))
        val k = Servicios.deBlobs(listOf(JSONObject(), b))
        assertEquals("abc", k.sgdb); assertEquals("k1", k.steamKey); assertEquals("https://steamcommunity.com/id/gonzalo/", k.steamUser)
    }
}
