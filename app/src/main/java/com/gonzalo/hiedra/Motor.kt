package com.gonzalo.hiedra

import android.view.KeyEvent
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid

/**
 * Lo que el jugador le pide a la consola. La de verdad es LibretroDroid (código nativo, que no corre en las pruebas del
 * ordenador); las pruebas usan una de mentira que apunta lo que se le pide.
 */
interface Motor {
    /** Una foto de la consola (el estado), o null si el núcleo no sabe hacerla. */
    fun estado(): ByteArray?
    fun cargarEstado(datos: ByteArray): Boolean
    /** La partida del cartucho (SRAM). */
    fun partida(): ByteArray?
    fun boton(boton: Mandos.Boton, abajo: Boolean)
    fun cruceta(x: Float, y: Float)
    fun analogico(izquierdo: Boolean, x: Float, y: Float)
    /** Fotogramas del núcleo por imagen (1 = normal; más = avance rápido). */
    var velocidad: Int
    var audio: Boolean
    fun pausar()
    fun reanudar()
    fun reiniciar()

    /** Manda al juego lo que sale de [Mandos.Estado] (las acciones de Hiedra, como el menú, no van aquí). */
    fun aplicar(s: Mandos.Salida) {
        when (s) {
            is Mandos.Salida.Boton -> boton(s.boton, s.abajo)
            is Mandos.Salida.Cruceta -> cruceta(s.x, s.y)
            is Mandos.Salida.Analogico -> analogico(s.izquierdo, s.x, s.y)
            is Mandos.Salida.Hacer -> {}
        }
    }
}

/** El motor de verdad: una vista de LibretroDroid. */
class MotorRetro(val vista: GLRetroView) : Motor {
    override fun estado(): ByteArray? = runCatching { vista.serializeState() }.getOrNull()?.takeIf { it.isNotEmpty() }
    override fun cargarEstado(datos: ByteArray): Boolean = runCatching { vista.unserializeState(datos) }.getOrDefault(false)
    override fun partida(): ByteArray? = runCatching { vista.serializeSRAM() }.getOrNull()
    override fun boton(boton: Mandos.Boton, abajo: Boolean) = vista.sendKeyEvent(if (abajo) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, boton.tecla)
    override fun cruceta(x: Float, y: Float) = vista.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, x, y)
    override fun analogico(izquierdo: Boolean, x: Float, y: Float) =
        vista.sendMotionEvent(if (izquierdo) GLRetroView.MOTION_SOURCE_ANALOG_LEFT else GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, x, y)
    override var velocidad: Int
        get() = vista.frameSpeed
        set(v) { vista.frameSpeed = v.coerceIn(1, 8) }
    override var audio: Boolean
        get() = vista.audioEnabled
        set(v) { vista.audioEnabled = v }
    /**
     * LibretroDroid no tiene una pausa pública: se pausa como lo hace él con el ciclo de vida (la vista y el núcleo). Mientras tanto se
     * pueden pedir estados igual: la vista atiende las peticiones aunque esté en pausa.
     */
    private var enPausa = false
    /** Siempre pausa (aunque ya lo estuviera): al volver a la app, LibretroDroid se reanuda solo y hay que pausarlo otra vez. */
    override fun pausar() { enPausa = true; runCatching { vista.onPause(); LibretroDroid.pause() } }
    override fun reanudar() { if (!enPausa) return; enPausa = false; runCatching { LibretroDroid.resume(); vista.onResume() } }
    override fun reiniciar() { runCatching { vista.reset() } }
}
