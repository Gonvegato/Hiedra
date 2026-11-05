package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Capturas de la interfaz en el ordenador (sin emulador), con juegos de ejemplo. Para revisar el diseño:
 *   gradle :app:testDebugUnitTest --tests '*CapturasTest*' -Proborazzi.test.record=true
 * Las imágenes quedan en app/build/outputs/roborazzi/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class CapturasTest {
    @get:Rule val rule = createComposeRule()
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun siembra() {
        Catalogo.cargar(ctx); Skin.cargar(ctx); Ajustes.cargar(ctx)
        Skin.invertido = false; Skin.origenAtrio = false; Skin.fondoId = ""
        Ajustes.portadasLibretro = false
        Biblioteca.cargada = true
        Biblioteca.juegos.clear(); Archivos.limpiarParaPruebas()
        // Los datos de fuera: sin red, con dos de ejemplo
        Datos.activo = false; Datos.cargar(ctx)
        Datos.poner("r1", DatosJuego("RAWG", "1/2/2005", listOf("Game Freak"), listOf("Nintendo"), listOf("RPG", "Aventura"), "La tercera versión de Hoenn: Rayquaza, el Frente de Batalla y los dos equipos a la vez.", 76, "IGDB · Pokémon Emerald · 310 jugadores"))
        val ahora = System.currentTimeMillis()
        // Unos días de juego para que se vean las barras de la semana en la portada (solo en el primero)
        val hoyD = java.time.LocalDate.now()
        val diasJuego = listOf(0 to 52, 1 to 35, 2 to 0, 3 to 70, 4 to 25, 5 to 41, 6 to 18).filter { it.second > 0 }.associate { (atras, min) -> hoyD.minusDays(atras.toLong()).toString() to min * 60_000L }
        fun rom(n: String, s: String, k: Int, fav: Boolean = false, jug: Long = 0, ms: Long = 0, veces: Int = 0) {
            val l = Lanzador.rom(s, n); Archivos.poner(l.clave, "content://x/$k", "")
            Biblioteca.juegos.add(Juego("r$k", n, lanzadores = listOf(l), favorito = fav, anadido = ahora - k * 1000L, ultima = jug, propias = Horas(ms, veces, if (k == 1) diasJuego else emptyMap())))
        }
        rom("Pokémon Esmeralda", "gba", 1, fav = true, jug = ahora - 3600_000, ms = 3 * 3600_000L + 1_200_000, veces = 7)
        rom("Metroid Fusion", "gba", 2); rom("Advance Wars", "gba", 3, fav = true); rom("The Minish Cap", "gba", 4)
        rom("Pokémon Platino", "nds", 5, jug = ahora - 86_400_000L * 2, ms = 12 * 3600_000L, veces = 21); rom("Mario Kart DS", "nds", 6); rom("Hollow Knight (SNES hack)", "snes", 7)
        Biblioteca.juegos.add(Juego("a1", "Stardew Valley", lanzadores = listOf(Lanzador.app("com.chucklefish.stardewvalley")), anadido = ahora - 9000))
        Biblioteca.juegos.add(Juego("w1", "PokéRogue", lanzadores = listOf(Lanzador.web("https://pokerogue.net")), anadido = ahora - 10000, ultima = ahora - 600_000, propias = Horas(5400_000, 3)))
        fun estado(id: String, e: Estado) { val i = Biblioteca.juegos.indexOfFirst { it.id == id }; Biblioteca.juegos[i] = Biblioteca.juegos[i].conEstado(e, ahora - 86_400_000L * 3) }
        Biblioteca.juegos[0] = Biblioteca.juegos[0].copy(durHistoria = 36 * 60, durExtras = 70 * 60, durCompleto = 140 * 60)
        estado("r1", Estado.PASADO); estado("r7", Estado.PASADO); estado("r5", Estado.JUGANDO); estado("w1", Estado.JUGANDO); estado("r4", Estado.ABANDONADO)
        // Un juego de PC pasado, con horas de Steam y de Carmesí (sin lanzador aquí), y uno de Switch a medias
        val hades = nuevoJuego("Hades", "PC", anadido = ahora - 20_000).conEstado(Estado.PASADO, ahora - 86_400_000L * 10).copy(
            externas = mapOf(Fuentes.STEAM to Horas(40 * 3600_000L), Fuentes.carmesi("viejo") to Horas(2 * 3600_000L)), otrasClaves = listOf("juego:hades"), durHistoria = 22 * 60, durExtras = 55 * 60, durCompleto = 95 * 60)
        Biblioteca.juegos.add(hades)
        Datos.poner(hades.id, DatosJuego("Steam", "17 SEP 2020", listOf("Supergiant Games"), listOf("Supergiant Games"), listOf("Acción", "Indie", "Rol"), "Desafía al dios de los muertos y escapa del inframundo en este roguelike de Supergiant.", 93))
        Biblioteca.juegos.add(nuevoJuego("Tears of the Kingdom", "Switch", anadido = ahora - 30_000).conEstado(Estado.JUGANDO).copy(propias = Horas(25 * 3600_000L, 9), ultima = ahora - 7200_000))
        // Un juego con dos maneras de abrirse: la ROM aquí y el mismo juego en el PC
        Biblioteca.juegos[Biblioteca.juegos.indexOfFirst { it.id == "r7" }] = Biblioteca.por("r7")!!.conLanzador(Lanzador.pc("steam:367520", "pc1")).copy(externas = mapOf(Fuentes.pc("pc1") to Horas(30 * 3600_000L)))
        Sagas.cargada = true; Sagas.lista.clear(); Sagas.borradas.clear()
        Sagas.lista.add(Saga("s1", "Pokémon", listOf("r1", "r5"), 1))
        Sagas.lista.add(Saga("s2", "The Legend of Zelda", listOf("r4", Biblioteca.juegos.first { it.titulo == "Tears of the Kingdom" }.id), 1))
    }

    private fun foto(nombre: String) {
        rule.waitForIdle(); Thread.sleep(500); rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    private fun montar(oscuro: Boolean = true) {
        coil.Coil.setImageLoader(coil.ImageLoader.Builder(ctx).dispatcher(Dispatchers.Unconfined).allowHardware(false).build())
        Modo.eleccion = if (oscuro) 2 else 1
        rule.setContent { HiedraTheme { Surface(Modifier.fillMaxSize(), color = Paper) { HiedraApp() } } }
    }

    private fun ir(texto: String) { rule.onNodeWithText(texto).performScrollTo().performClick() }
    private fun tocarVisible(texto: String) {
        val nodos = rule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true).fetchSemanticsNodes()
        val ancho = rule.onRoot().fetchSemanticsNode().size.width
        val i = nodos.indexOfFirst { it.boundsInRoot.left >= 0f && it.boundsInRoot.right <= ancho }
        rule.onAllNodesWithText(texto, substring = true, useUnmergedTree = true)[if (i < 0) 0 else i].performScrollTo().performClick(); rule.waitForIdle()
    }
    private fun pulsar(texto: String) { val n = rule.onNodeWithText(texto); runCatching { n.performScrollTo() }; n.performClick(); rule.waitForIdle() }

    @Test
    fun interfaces() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Catalogo.cargar(ctx); Skin.cargar(ctx)
        Biblioteca.cargada = true; Biblioteca.juegos.clear()
        montar(oscuro = false)
        foto("1_portada_vacia")
    }

    @Test
    fun conJuegos() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        montar(oscuro = false)
        foto("2_portada")
        ir("JUGANDO"); foto("3_juegos")
        rule.onAllNodesWithText("PENDIENTES")[0].performClick(); rule.waitForIdle(); foto("3b_pendiente")
        rule.onAllNodesWithText("PASADOS")[0].performClick(); rule.waitForIdle(); foto("3c_pasado")
        tocarVisible("EMULACIÓN"); foto("3d_pasado_emulacion")
        rule.onAllNodesWithText("SAGAS")[0].performScrollTo().performClick(); rule.waitForIdle(); foto("3e_sagas")
        rule.onAllNodesWithText("The Legend of Zelda", useUnmergedTree = true).onLast().performClick(); rule.waitForIdle(); foto("3f_saga_abierta")
        rule.onNodeWithText("VER LA SAGA →", useUnmergedTree = true).performClick(); foto("3g_pagina_saga")
        pulsar("← VOLVER")
        pulsar("← HIEDRA"); rule.onNodeWithContentDescription("Ajustes").performClick(); foto("5_ajustes")
        rule.onNodeWithText("JUEGOS").performClick(); rule.waitForIdle(); foto("5b_ajustes_juegos")
        rule.onNodeWithText("CUENTA").performClick(); rule.waitForIdle(); foto("5c_ajustes_servicios")
        rule.onNodeWithText("AVANZADO").performClick(); rule.waitForIdle(); foto("5d_ajustes_avanzado")
        rule.onNodeWithText("JUEGOS").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Sistemas y emuladores", substring = true).performScrollTo().performClick(); foto("4_sistemas")
        pulsar("← HIEDRA"); pulsar("← HIEDRA"); ir("PASADOS")
        rule.onAllNodesWithText("Pokémon Esmeralda", substring = true, useUnmergedTree = true)[0].performClick(); foto("6_ficha")
        pulsar("← VOLVER")
        rule.onAllNodesWithText("Hades", substring = true, useUnmergedTree = true)[0].performClick(); foto("6b_ficha_sin_lanzador")
        pulsar("← VOLVER")
        rule.onAllNodesWithText("Hollow Knight", substring = true, useUnmergedTree = true)[0].performClick(); rule.onNodeWithText("EN AZABACHE").performScrollTo(); foto("6c_ficha_dos_lanzadores")
    }

    @Test
    fun tuPc() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        val ahora = System.currentTimeMillis()
        fun g(k: String, n: String, m: Int = 0, lp: Long = 0) = org.json.JSONObject().put("k", k).put("n", n).put("m", m).put("lp", lp).put("c", "")
        val arbol = org.json.JSONObject()
            .put("live", org.json.JSONObject().put("pc1", org.json.JSONObject().put("app", "atrio").put("dn", "Torre").put("os", "win32").put("on", true).put("at", ahora).put("today", 95)
                .put("game", org.json.JSONObject().put("key", "steam:1145360").put("name", "Hades").put("s", ahora - 47 * 60_000L).put("sg", 7L))
                .put("st", org.json.JSONObject().put("on", true).put("run", true).put("name", "torre"))))
            .put("lib", org.json.JSONObject().put("pc1", org.json.JSONObject().put("name", "torre").put("games", org.json.JSONArray()
                .put(g("steam:1145360", "Hades", 2400, ahora - 47 * 60_000L)).put(g("steam:504230", "Celeste", 600, ahora - 86_400_000L * 4)).put(g("epic:aw2", "Alan Wake 2"))
                .put(g("steam:1245620", "Elden Ring", 5000, ahora - 86_400_000L * 40)).put(g("steam:413150", "Stardew Valley", 90, ahora - 86_400_000L * 9)).put(g("gog:witcher3", "The Witcher 3")))))
        Suite.simular(arbol)
        JuegosPc.aplicar(ctx, Suite.leer(arbol, "yo"), emptyList())
        montar(oscuro = false)
        foto("22_portada_pc")
        rule.onAllNodesWithText("TU PC")[0].performClick(); rule.waitForIdle(); foto("23_tu_pc")
        rule.onAllNodesWithText("BIBLIOTECA", substring = true)[0].performClick(); rule.waitForIdle(); foto("24_tu_pc_biblioteca")
        rule.onAllNodesWithText("EQUIPO", substring = true)[0].performClick(); rule.waitForIdle(); foto("25_tu_pc_equipo")
        rule.onAllNodesWithText("AHORA", substring = true)[0].performClick(); rule.waitForIdle()
        rule.onAllNodesWithText("FICHA →", substring = true)[0].performClick(); rule.waitForIdle(); foto("26_ficha_pc")
        rule.onNodeWithText("EN AZABACHE").performScrollTo(); foto("26b_ficha_pc_abajo")
    }

    @Test
    fun inicio() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        Inicio.poner(ConfigInicio(fijados = listOf("r3", "r6", Biblioteca.juegos.first { it.titulo == "Hades" }.id)), 1)
        montar(oscuro = false)
        rule.waitForIdle(); Thread.sleep(2500); foto("20_inicio")
        rule.onNodeWithText("MIS ELEGIDOS").performScrollTo(); foto("20b_inicio_estantes")
        rule.onNodeWithText("EDITAR").performScrollTo().performClick(); rule.waitForIdle(); rule.onNodeWithText("ESTANTES").performScrollTo(); foto("21_inicio_editando")
        Inicio.poner(ConfigInicio(), 2)
    }

    @Test
    fun ajustesConFondoOscuro() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra(); Skin.fondoId = "granate"
        montar(oscuro = false)
        rule.onNodeWithContentDescription("Ajustes").performClick(); foto("30_ajustes_fondo_oscuro")
        Skin.fondoId = ""
    }

    @Test
    fun oscuro() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        montar(oscuro = true)
        foto("7_portada_oscuro")
        ir("JUGANDO"); foto("8_juegos_oscuro")
    }

    @Test
    fun hojaAnadir() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        montar(oscuro = false)
        ir("+ AÑADIR"); foto("9_anadir")
    }

    @Test
    fun fichaYSusCasillas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        montar(oscuro = false)
        ir("PASADOS")
        rule.onAllNodesWithText("Pokémon Esmeralda", substring = true, useUnmergedTree = true)[0].performClick()
        rule.onNodeWithText("EN AZABACHE").performScrollTo(); foto("12_ficha_casillas")
        rule.onNodeWithText("Datos de").performScrollTo(); foto("12b_ficha_datos")
        pulsar("← VOLVER")
        tocarVisible("Hades")
        rule.onNodeWithText("Supergiant Games", substring = true).assertExists()
        rule.onNodeWithText("Datos de").performScrollTo(); foto("12c_ficha_datos_steam")
    }

    @Test
    fun emuladorDeHiedra() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        montar(oscuro = false)
        ir("PASADOS")
        rule.onAllNodesWithText("Pokémon Esmeralda", substring = true, useUnmergedTree = true)[0].performClick(); rule.waitForIdle()
        rule.onNodeWithText("Emulador →", substring = true, ignoreCase = true).performScrollTo().performClick(); foto("6d_ficha_emulador_hiedra")
    }

    @Test
    fun servicios() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        montar(oscuro = false)
        rule.onNodeWithContentDescription("Ajustes").performClick()
        rule.onNodeWithText("CUENTA").performClick(); rule.waitForIdle()
        rule.onNodeWithText("Traer las horas de Steam", substring = true).performScrollTo(); foto("31_ajustes_servicios")
    }

    @Test
    fun buscadorDePortadas() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        montar(oscuro = false)
        ir("PASADOS")
        rule.onAllNodesWithText("Pokémon Esmeralda", substring = true, useUnmergedTree = true)[0].performClick()
        rule.onNodeWithTag("portada").performScrollTo().performClick(); foto("10_portada_opciones")
        rule.onNodeWithText("Buscar en internet →").performClick(); foto("11_buscador")
    }

    @Test
    fun invertidos() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        Skin.invertido = true
        montar(oscuro = false)
        foto("15_invertido_portada")
        ir("PASADOS"); foto("16_invertido_juegos")
        rule.onAllNodesWithText("Pokémon Esmeralda", substring = true, useUnmergedTree = true)[0].performClick()
        rule.onNodeWithText("EN AZABACHE").performScrollTo(); foto("17_invertido_ficha")
    }

    @Test
    fun invertidosEnOscuro() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        Skin.invertido = true
        montar(oscuro = true)
        foto("18_invertido_oscuro_portada")
    }

    @Test
    fun coloresDeAtrioInvertidos() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        siembra()
        org.robolectric.Robolectric.setupContentProvider(TemaAtrioTest.AtrioFalso::class.java, "com.gonzalo.gamerate.tema")
        TemaAtrio.refrescar(ctx)
        Skin.origenAtrio = true; Skin.invertido = true
        montar(oscuro = false)
        foto("19_atrio_invertido_portada")
        rule.onNodeWithContentDescription("Ajustes").performClick()
        rule.onNodeWithText("COLORES DE AZABACHE", substring = true).performScrollTo(); foto("20_ajustes_colores_atrio")
    }
}
