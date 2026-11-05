package com.gonzalo.hiedra

import android.graphics.Color
import com.swordfish.radialgamepad.library.config.ButtonConfig
import com.swordfish.radialgamepad.library.config.CrossConfig
import com.swordfish.radialgamepad.library.config.PrimaryDialConfig
import com.swordfish.radialgamepad.library.config.RadialGamePadConfig
import com.swordfish.radialgamepad.library.config.RadialGamePadTheme
import com.swordfish.radialgamepad.library.config.SecondaryDialConfig
import com.swordfish.radialgamepad.library.haptics.HapticConfig

/**
 * Los controles táctiles del jugador interno (con RadialGamePad, del autor de Lemuroid): una media luna a cada lado, como en la
 * consola. Cada control lleva como id la tecla del RetroPad que manda ([Mandos.Boton.tecla]); la cruceta, [ID_CRUZ]. Así todo lo
 * que se pulsa en la pantalla pasa por el mismo [Mandos.Estado] que el mando físico.
 *
 * Las posiciones de los botones secundarios van en «horas» de un reloj de 12: 0 son las 3, y suben en sentido contrario a las agujas.
 */
object MandoTactil {
    const val ID_CRUZ = -10

    enum class Lado { IZQUIERDO, DERECHO }

    /** El color de los botones: papel translúcido (se ven sobre cualquier juego) y el acento de Hiedra al pulsar. */
    fun tema(opacidad: Float, acento: Int): RadialGamePadTheme {
        fun a(base: Float) = (base * opacidad.coerceIn(0.1f, 1f) * 255).toInt().coerceIn(0, 255)
        return RadialGamePadTheme(
            normalColor = Color.argb(a(0.55f), 245, 240, 230),
            pressedColor = Color.argb(a(1f).coerceAtLeast(200), Color.red(acento), Color.green(acento), Color.blue(acento)),
            simulatedColor = Color.argb(a(0.55f), 245, 240, 230),
            textColor = Color.argb(a(1f).coerceAtLeast(160), 25, 22, 20),
            backgroundColor = Color.argb(a(0.18f), 245, 240, 230),
            lightColor = Color.argb(a(0.3f), 245, 240, 230),
        )
    }

    private fun boton(b: Mandos.Boton, etiqueta: String = b.nombre.uppercase()) = ButtonConfig(id = b.tecla, label = etiqueta, contentDescription = b.nombre)

    /** La disposición de un lado para un sistema (Fase A: la de GBA; el resto llega con su núcleo). */
    fun configuracion(sistema: String, lado: Lado, opacidad: Float, acento: Int, vibracion: Boolean): RadialGamePadConfig {
        val tema = tema(opacidad, acento)
        val haptic = if (vibracion) HapticConfig.PRESS else HapticConfig.OFF
        return when (lado) {
            Lado.IZQUIERDO -> RadialGamePadConfig(
                sockets = 12,
                primaryDial = PrimaryDialConfig.Cross(CrossConfig(ID_CRUZ, useDiagonals = true)),
                secondaryDials = listOf(
                    SecondaryDialConfig.SingleButton(3, 1f, 0f, boton(Mandos.Boton.L)),
                    SecondaryDialConfig.SingleButton(1, 0.8f, 0f, boton(Mandos.Boton.SELECT, "SELECT")),
                    // Huecos vacíos: mantienen la media luna simétrica con la del otro lado
                    SecondaryDialConfig.Empty(5, 1, 1f, 0f),
                    SecondaryDialConfig.Empty(9, 1, 1f, 0f),
                ),
                haptic = haptic, theme = tema,
            )
            Lado.DERECHO -> RadialGamePadConfig(
                sockets = 12,
                primaryDial = PrimaryDialConfig.PrimaryButtons(listOf(boton(Mandos.Boton.A), boton(Mandos.Boton.B)), rotationInDegrees = 30f),
                secondaryDials = listOf(
                    SecondaryDialConfig.SingleButton(3, 1f, 0f, boton(Mandos.Boton.R)),
                    SecondaryDialConfig.SingleButton(5, 0.8f, 0f, boton(Mandos.Boton.START, "START")),
                    SecondaryDialConfig.Empty(1, 1, 1f, 0f),
                    SecondaryDialConfig.Empty(9, 1, 1f, 0f),
                ),
                haptic = haptic, theme = tema,
            )
        }
    }

    /** A qué va un control del táctil (por su id). */
    fun destinoDe(id: Int): Mandos.Destino? = Mandos.Boton.entries.firstOrNull { it.tecla == id }?.let { Mandos.Destino.Pad(it) }
}
