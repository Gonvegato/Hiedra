package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Las secciones: los menús pequeños filtran por los lanzadores del juego y se quitan con otro toque. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class SeccionesTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun losMenusPequenosFiltranYSeQuitanConOtroToque() {
        Datos.activo = false; Catalogo.cargar(ctx); Skin.cargar(ctx); Ajustes.cargar(ctx); Ajustes.portadasLibretro = false
        Biblioteca.cargada = true; Biblioteca.juegos.clear(); Sagas.cargada = true; Sagas.lista.clear(); Archivos.limpiarParaPruebas()
        val pe = Lanzador.rom("gba", "pe"); Archivos.poner(pe.clave, "content://x", "")
        Biblioteca.juegos.add(Juego("r1", "Pokémon Esmeralda", lanzadores = listOf(pe), anadido = 1).conEstado(Estado.PASADO, 5))
        Biblioteca.juegos.add(nuevoJuego("Hades", "PC", anadido = 2).conEstado(Estado.PASADO, 6))
        Biblioteca.juegos.add(Juego("a1", "Stardew Valley", lanzadores = listOf(Lanzador.app("x.y")), anadido = 3))
        // Un juego con dos lanzadores sale en los dos menús
        Biblioteca.juegos.add(Juego("d1", "Celeste", lanzadores = listOf(Lanzador.rom("switch", "Celeste"), Lanzador.pc("steam:2", "pc1")), anadido = 4).conEstado(Estado.PASADO, 7))
        rule.setContent { HiedraTheme { Surface(Modifier.fillMaxSize(), color = Paper) { PantSecciones(Destino.Pasado, {}, {}, {}, {}, {}) } } }
        rule.onNodeWithText("Hades", useUnmergedTree = true).assertIsDisplayed()
        fun enPantalla(t: String) = rule.onAllNodesWithText(t, useUnmergedTree = true).fetchSemanticsNodes().any { it.boundsInRoot.left >= 0f && it.boundsInRoot.right <= 1080f && it.boundsInRoot.top >= 0f }
        fun tocar(t: String) { val n = rule.onAllNodesWithText(t); n[n.fetchSemanticsNodes().indexOfFirst { it.boundsInRoot.left >= 0f && it.boundsInRoot.right <= 1080f }].performClick(); rule.waitForIdle() }
        tocar("EMULACIÓN")
        assertFalse("con Emulación, Hades (PC) no sale", enPantalla("Hades"))
        rule.onNodeWithText("Pokémon Esmeralda", useUnmergedTree = true).assertIsDisplayed()
        assertTrue("Celeste tiene ROM: sale", enPantalla("Celeste"))
        tocar("EMULACIÓN")
        rule.onNodeWithText("Hades", useUnmergedTree = true).assertIsDisplayed()
        tocar("PC")
        assertTrue("Celeste también es del PC: sale", enPantalla("Celeste")); assertTrue(enPantalla("Hades")); assertFalse(enPantalla("Pokémon Esmeralda"))
        Biblioteca.juegos.clear(); Sagas.lista.clear()
    }
}
