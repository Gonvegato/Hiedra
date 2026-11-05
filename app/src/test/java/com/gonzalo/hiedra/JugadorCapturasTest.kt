package com.gonzalo.hiedra

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import android.os.Looper
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers

/**
 * Capturas del jugador interno: el mando táctil (en apaisado y en vertical) y el menú. El núcleo es código nativo de ARM y no corre en
 * el ordenador, así que el juego se ve como un fondo verde liso (JuegoActivity.sinMotor). Solo al grabar capturas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class JugadorCapturasTest {
    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun preparar() {
        org.junit.Assume.assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        Catalogo.cargar(ctx); Skin.cargar(ctx); Ajustes.cargar(ctx)
        Biblioteca.cargada = true; Biblioteca.juegos.clear(); Archivos.limpiarParaPruebas()
        val l = Lanzador.rom("gba", "Pokémon Esmeralda"); Archivos.poner(l.clave, "content://x/1", "")
        Biblioteca.juegos.add(Juego("r1", "Pokémon Esmeralda", lanzadores = listOf(l)))
        JuegoActivity.sinMotor = true
    }

    @After fun quitar() { JuegoActivity.sinMotor = false }

    private fun foto(nombre: String) {
        repeat(3) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(150) }
        captureScreenRoboImage("build/outputs/roborazzi/$nombre.png")
    }

    @Test fun apaisado() {
        RuntimeEnvironment.setQualifiers("+land")
        val a = Robolectric.buildActivity(JuegoActivity::class.java, JuegoActivity.intent(ctx, "r1")).setup().get()
        foto("40_jugador_tactil")
        a.abrirMenu(); foto("41_jugador_menu")
        a.pantallaMenu = "avance"; foto("42_jugador_avance")
        a.pantallaMenu = "mando"; foto("43_jugador_mando")
        a.pantallaMenu = "guardar"; foto("44_jugador_guardar")
        a.pantallaMenu = "imagen"; foto("45_jugador_imagen")
        a.cerrarMenu()
        // Con mando: el táctil se oculta y quedan las píldoras (MENÚ y ▶▶)
        a.mandoConectado = true; foto("46_jugador_con_mando")
    }

    @Test fun vertical() {
        RuntimeEnvironment.setQualifiers("+port")
        Robolectric.buildActivity(JuegoActivity::class.java, JuegoActivity.intent(ctx, "r1")).setup().get()
        foto("47_jugador_vertical")
    }
}
