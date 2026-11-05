package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** La portada: su configuración (por ids; viaja, gana la más reciente), qué va en cada estante y el mando. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class InicioTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private fun rom(id: String, t: String, estado: Estado = Estado.PENDIENTE, ultima: Long = 0, anadido: Long = 1): Juego {
        val l = Lanzador.rom("gba", t); Archivos.poner(l.clave, "content://$id", "")
        return Juego(id, t, lanzadores = listOf(l), anadido = anadido).conEstado(estado).copy(ultima = ultima)
    }

    @Test fun laConfiguracionViajaYGanaLaMasReciente() {
        Inicio.cargar(ctx)
        val c = ConfigInicio(listOf(FilaInicio(Estante.PENDIENTE, grande = true), FilaInicio(Estante.JUGANDO, activa = false)), listOf("j1"), "j2", mod = 5)
        val v = ConfigInicio.deJson(JSONObject(c.aJson().toString()))!!
        assertEquals(c.fijados, v.fijados); assertEquals("j2", v.destacado); assertEquals(5L, v.mod)
        assertEquals(Estante.PENDIENTE, v.filas[0].estante); assertTrue(v.filas[0].grande); assertFalse(v.filas[1].activa)
        assertEquals("los estantes que no estaban se añaden al final, apagados", Estante.entries.size, v.filas.size); assertFalse(v.filas.last().activa)
        Inicio.poner(c, ahora = 10)
        assertFalse("lo más viejo no pisa", Inicio.aplicarRemoto(c.copy(fijados = emptyList(), mod = 3).aJson().toString()))
        assertTrue(Inicio.aplicarRemoto(c.copy(fijados = listOf("x"), mod = 20).aJson().toString()))
        assertEquals(listOf("x"), Inicio.config.fijados)
        val b = Mezcla.deJson(Mezcla.aJson(Mezcla.aBlob("A", emptyList(), emptyMap(), inicio = Inicio.config.aJson().toString())))!!
        assertEquals(20L, ConfigInicio.deJson(JSONObject(b.inicio))!!.mod)
        Inicio.poner(ConfigInicio(), 30)
    }

    @Test fun elDestacadoYLosEstantes() {
        Archivos.limpiarParaPruebas()
        Inicio.cargar(ctx); Inicio.poner(ConfigInicio(fijados = listOf("a")), 1)
        val js = listOf(rom("a", "Zelda", Estado.JUGANDO, ultima = 50), rom("b", "Metroid", Estado.JUGANDO, ultima = 90), rom("c", "Kirby", anadido = 7), rom("d", "Wario", Estado.PASADO))
        val d = Inicio.destacado(js)!!
        assertEquals("el último al que jugaste", "Metroid", d.titulo)
        assertEquals(listOf("Zelda"), Inicio.juegosDe(Estante.JUGANDO, js, d).map { it.titulo })
        assertEquals(listOf("Kirby"), Inicio.juegosDe(Estante.PENDIENTE, js, d).map { it.titulo })
        assertEquals(listOf("Zelda"), Inicio.juegosDe(Estante.FIJADOS, js, d).map { it.titulo })
        Inicio.poner(ConfigInicio(destacado = "c"), 2)
        assertEquals("el que fijaste", "Kirby", Inicio.destacado(js)!!.titulo)
        Inicio.poner(ConfigInicio(), 3)
    }

    @Test fun laPortadaEnsenaElMenuLosEstantesYRespondeAlMando() {
        Datos.activo = false; Catalogo.cargar(ctx); Skin.cargar(ctx); Inicio.cargar(ctx); Inicio.poner(ConfigInicio(), 1)
        Biblioteca.cargada = true; Biblioteca.juegos.clear(); Sagas.cargada = true; Sagas.lista.clear(); Archivos.limpiarParaPruebas()
        Biblioteca.juegos.add(rom("a", "Zelda de prueba", Estado.JUGANDO, ultima = 50))
        Biblioteca.juegos.add(rom("b", "Metroid de prueba", Estado.JUGANDO, ultima = 90))
        Biblioteca.juegos.add(rom("c", "Kirby de prueba"))
        var ir: Destino? = null; var jugado = ""; var abierto = ""
        rule.setContent { HiedraTheme { Surface(Modifier.fillMaxSize(), color = Paper) { PantInicio({ abierto = it.titulo }, { jugado = it.titulo }, {}, { ir = it }, {}) } } }
        listOf("JUGANDO", "PENDIENTES", "PASADOS", "ABANDONADOS", "SAGAS", "TU PC").forEach { rule.onAllNodesWithText(it)[0].assertExists() }
        rule.onAllNodesWithText("PASADOS")[0].performClick(); assertEquals(Destino.Pasado, ir)
        rule.onNodeWithTag("destacado").assertExists(); rule.onAllNodesWithText("Metroid de prueba", useUnmergedTree = true)[0].assertExists()
        rule.onNodeWithText("SIGUE JUGANDO").assertExists(); rule.onNodeWithTag("loseta-a").assertExists(); rule.onNodeWithTag("loseta-c").assertExists()
        rule.onNodeWithTag("loseta-a").performClick(); assertEquals("Zelda de prueba", jugado)
        rule.onNodeWithTag("loseta-c").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick); assertEquals("Kirby de prueba", abierto)
        jugado = ""; abierto = ""
        rule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }; rule.waitForIdle()
        rule.onNodeWithTag("loseta-a").performScrollTo().requestFocus(); rule.waitForIdle()
        rule.onNodeWithTag("loseta-a").assertIsFocused()
        rule.onRoot().performKeyInput { pressKey(Key.ButtonA) }; rule.waitForIdle(); assertEquals("A juega", "Zelda de prueba", jugado)
        rule.onRoot().performKeyInput { pressKey(Key.ButtonX) }; rule.waitForIdle(); assertEquals("X abre la ficha", "Zelda de prueba", abierto)
        Biblioteca.juegos.clear()
    }
}
