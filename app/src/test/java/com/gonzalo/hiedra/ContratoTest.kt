package com.gonzalo.hiedra

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * El contrato con Azabache (docs/CONTRATO-DATOS.md), de verdad: lo que Hiedra sube a la cuenta, tal cual lo escribe, se deja en
 * `build/outputs/contrato/hiedra.json`. Azabache lo lee con su prueba `ContratoHermanasTest` (copia en `herramientas/casos/hermanas/`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContratoTest {
    @Test fun loQueSubeHiedra() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Catalogo.cargar(ctx); Ajustes.cargar(ctx); Ajustes.portadasLibretro = true
        val dias = mapOf("2026-01-01" to 600_000L, "2026-01-02" to 60_000L)
        // Una ROM pasada con portada de internet, horas propias por día y horas de Steam; otra sin portada tuya (la caja automática)
        val zelda = Juego("j1", "The Legend of Zelda", plataforma = "GBA", lanzadores = listOf(Lanzador.rom("gba", "Zelda.gba")), mod = 100, anadido = 50, ultima = 1_700_000_000_000L,
            favorito = true, finalizado = 1_700_000_000_000L, portadaUrl = "https://x/zelda.jpg", durHistoria = 1_200, durExtras = 2_000, durCompleto = 3_000,
            propias = Horas(900_000L, 7, dias), externas = mapOf("steam" to Horas(120_000L, 1, mapOf("2026-01-01" to 120_000L)))).conEstado(Estado.PASADO)
        val metroid = Juego("j2", "Metroid Fusion", lanzadores = listOf(Lanzador.rom("gba", "Metroid Fusion.gba")), mod = 100, anadido = 60, propias = Horas(300_000L, 2, mapOf("2026-01-03" to 300_000L))).conEstado(Estado.JUGANDO)
        val data = Mezcla.aJson(Mezcla.aBlob("hiedra-A", listOf(zelda, metroid), mapOf("j9" to 10L)))
        val tiempo = JSONObject()
            .put("j1", JSONObject().put("hiedra-A", SincroP.nodoTiempo(zelda, zelda.propias)).put("steam", SincroP.nodoTiempo(zelda, zelda.externa("steam"))))
            .put("j2", JSONObject().put("hiedra-A", SincroP.nodoTiempo(metroid, metroid.propias)))
        val out = JSONObject().put("app", "hiedra").put("version", VERSION)
            .put("claves", JSONObject().put("j1", org.json.JSONArray(zelda.claves)).put("j2", org.json.JSONArray(metroid.claves)))
            .put("dev", JSONObject().put("hiedra-A", JSONObject().put("data", data)))
            .put("tiempo", tiempo)
        // Lo que Azabache espera encontrar (y que esta prueba promete)
        val b = JSONObject(data)
        assertEquals("https://x/zelda.jpg", b.getJSONArray("juegos").getJSONObject(0).getString("pu"))
        assertTrue("sin portada tuya viaja la caja de libretro", b.getJSONArray("juegos").getJSONObject(1).getString("pa").startsWith("https://thumbnails.libretro.com/"))
        val f = File("build/outputs/contrato/hiedra.json").apply { parentFile.mkdirs() }
        f.writeText(out.toString(2))
        assertTrue(f.length() > 100)
    }
}
