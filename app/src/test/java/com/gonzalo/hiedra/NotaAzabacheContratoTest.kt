package com.gonzalo.hiedra

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** «En Azabache» con lo que Azabache sube DE VERDAD (`herramientas/casos/azabache.json`, generado por su `ContratoTest`). */
class NotaAzabacheContratoTest {
    private fun raiz(): File { var d: File? = File("").absoluteFile; while (d != null && !File(d, "herramientas/casos/azabache.json").exists()) d = d.parentFile; return d ?: error("no encuentro herramientas/casos") }

    @Test fun laNotaDeAzabacheSeLeeDeSuBlob() {
        val c = JSONObject(File(raiz(), "herramientas/casos/azabache.json").readText())
        val blobs = c.getJSONObject("dev").let { d -> d.keys().asSequence().map { JSONObject(d.getJSONObject(it).getString("data")) }.toList() }
        val notas = NotaAzabache.deBlobs(blobs)
        val hades = Juego("h", "Hades II", lanzadores = listOf(Lanzador.app("com.supergiant.hades2")))
        val n = NotaAzabache.de(notas, hades)!!
        assertEquals(91, n.general); assertEquals("Mejor que el primero.", n.resena); assertEquals(c.getLong("idHades"), n.atrioId)
        // La ficha que Azabache refleja de Hiedra sin nota no cuenta como valoración
        assertNull(NotaAzabache.de(notas, Juego("c", "Celeste", lanzadores = listOf(Lanzador.rom("gba", "Celeste.gba")))))
    }
}
