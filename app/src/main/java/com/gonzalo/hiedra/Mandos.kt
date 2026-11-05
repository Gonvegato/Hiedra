package com.gonzalo.hiedra

import android.view.KeyEvent
import kotlin.math.abs

/**
 * Los mandos del jugador interno: qué hace cada botón del mando físico (o del táctil) en el juego. Todo pasa por aquí: el mando
 * físico, los controles táctiles y los combos. Es puro (no toca Android salvo las constantes de KeyEvent) y se prueba sin móvil.
 *
 * Los botones del juego son los del «RetroPad» de libretro, y a LibretroDroid se le mandan con estas mismas teclas: KEYCODE_BUTTON_A
 * es el A del RetroPad, el botón de la DERECHA (como en una consola de Nintendo), y KEYCODE_BUTTON_B el de ABAJO.
 *
 * Android, en cambio, llama BUTTON_A al botón de abajo del mando (el A de Xbox). Por eso, por defecto, se traduce por POSICIÓN: el
 * botón de la derecha del mando es el A del juego, sea cual sea la letra que lleve pintada (es lo que hacen RetroArch y Lemuroid).
 * Con «Botones como en el mando» se traduce por la letra.
 */
object Mandos {
    /** Los botones del RetroPad, con la tecla con la que se le mandan a LibretroDroid. */
    enum class Boton(val tecla: Int, val nombre: String) {
        A(KeyEvent.KEYCODE_BUTTON_A, "A"), B(KeyEvent.KEYCODE_BUTTON_B, "B"),
        X(KeyEvent.KEYCODE_BUTTON_X, "X"), Y(KeyEvent.KEYCODE_BUTTON_Y, "Y"),
        L(KeyEvent.KEYCODE_BUTTON_L1, "L"), R(KeyEvent.KEYCODE_BUTTON_R1, "R"),
        L2(KeyEvent.KEYCODE_BUTTON_L2, "L2"), R2(KeyEvent.KEYCODE_BUTTON_R2, "R2"),
        L3(KeyEvent.KEYCODE_BUTTON_THUMBL, "L3"), R3(KeyEvent.KEYCODE_BUTTON_THUMBR, "R3"),
        START(KeyEvent.KEYCODE_BUTTON_START, "Start"), SELECT(KeyEvent.KEYCODE_BUTTON_SELECT, "Select"),
        ARRIBA(KeyEvent.KEYCODE_DPAD_UP, "Arriba"), ABAJO(KeyEvent.KEYCODE_DPAD_DOWN, "Abajo"),
        IZQUIERDA(KeyEvent.KEYCODE_DPAD_LEFT, "Izquierda"), DERECHA(KeyEvent.KEYCODE_DPAD_RIGHT, "Derecha");
    }

    /** Lo que no es del juego: acelerar y abrir el menú. */
    enum class Accion(val nombre: String) { AVANCE("Avance rápido"), MENU("Menú") }

    /** A qué va un botón: a un botón del juego o a una acción de Hiedra. */
    sealed interface Destino {
        data class Pad(val boton: Boton) : Destino
        data class Hacer(val accion: Accion) : Destino
        /** Un botón que has dejado sin nada (al darle a otro su función). */
        data object Nada : Destino
        fun clave(): String = when (this) { is Pad -> "pad:${boton.name}"; is Hacer -> "accion:${accion.name}"; Nada -> "nada" }
        val nombre: String get() = when (this) { is Pad -> boton.nombre; is Hacer -> accion.nombre; Nada -> "Nada" }
        companion object {
            fun deClave(k: String): Destino? = when {
                k.startsWith("pad:") -> Boton.entries.firstOrNull { it.name == k.removePrefix("pad:") }?.let { Pad(it) }
                k.startsWith("accion:") -> Accion.entries.firstOrNull { it.name == k.removePrefix("accion:") }?.let { Hacer(it) }
                k == "nada" -> Nada
                else -> null
            }
        }
    }

    /** Las teclas de mando que se tienen en cuenta al cambiar un botón (todas las de un mando de Android). */
    val teclasDeMando: List<Int> = listOf(
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
    ) + (KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16)

    /** Lo que hace de verdad una tecla en un mando (lo que cambiaste tú y, si no, lo de por defecto). */
    fun efectivo(tecla: Int, mapa: Map<Int, Destino>, sistema: String, porEtiqueta: Boolean): Destino? =
        mapa[tecla] ?: porDefecto(tecla, sistema, porEtiqueta)

    /**
     * Le da a [tecla] la función [destino]. Las demás teclas que la tenían se quedan sin nada (si no, dos botones harían lo mismo y el
     * que antes era «A» seguiría siéndolo). Devuelve el mapa nuevo.
     */
    fun reasignar(mapa: Map<Int, Destino>, tecla: Int, destino: Destino, sistema: String, porEtiqueta: Boolean): Map<Int, Destino> {
        val m = HashMap(mapa)
        for (t in (teclasDeMando + mapa.keys).distinct()) if (t != tecla && efectivo(t, m, sistema, porEtiqueta) == destino) m[t] = Destino.Nada
        m[tecla] = destino
        return m
    }

    /** Los botones que usa cada sistema (para el mando táctil y para saber si L2/R2 quedan libres para el avance rápido). */
    class Disposicion(val botones: Set<Boton>, val analogico: Boolean = false)

    val disposiciones: Map<String, Disposicion> = mapOf(
        "gba" to Disposicion(setOf(Boton.A, Boton.B, Boton.L, Boton.R, Boton.START, Boton.SELECT)),
    )
    fun disposicion(sistema: String): Disposicion = disposiciones[sistema] ?: Disposicion(Boton.entries.toSet(), analogico = true)

    /**
     * Qué hace una tecla de un mando físico si no la has cambiado tú. [porEtiqueta]: «Botones como en el mando» (por la letra en vez
     * de por la posición). R2 (o el gatillo derecho) acelera si el sistema no lo usa; el botón central del mando (Home/Guide) abre el menú.
     */
    fun porDefecto(tecla: Int, sistema: String, porEtiqueta: Boolean): Destino? {
        val d = disposicion(sistema)
        return when (tecla) {
            KeyEvent.KEYCODE_BUTTON_A -> Destino.Pad(if (porEtiqueta) Boton.A else Boton.B)
            KeyEvent.KEYCODE_BUTTON_B -> Destino.Pad(if (porEtiqueta) Boton.B else Boton.A)
            KeyEvent.KEYCODE_BUTTON_X -> Destino.Pad(if (porEtiqueta) Boton.X else Boton.Y)
            KeyEvent.KEYCODE_BUTTON_Y -> Destino.Pad(if (porEtiqueta) Boton.Y else Boton.X)
            KeyEvent.KEYCODE_BUTTON_L1 -> Destino.Pad(Boton.L)
            KeyEvent.KEYCODE_BUTTON_R1 -> Destino.Pad(Boton.R)
            KeyEvent.KEYCODE_BUTTON_L2 -> if (Boton.L2 in d.botones) Destino.Pad(Boton.L2) else null
            KeyEvent.KEYCODE_BUTTON_R2 -> if (Boton.R2 in d.botones) Destino.Pad(Boton.R2) else Destino.Hacer(Accion.AVANCE)
            KeyEvent.KEYCODE_BUTTON_THUMBL -> Destino.Pad(Boton.L3)
            KeyEvent.KEYCODE_BUTTON_THUMBR -> Destino.Pad(Boton.R3)
            KeyEvent.KEYCODE_BUTTON_START -> Destino.Pad(Boton.START)
            KeyEvent.KEYCODE_BUTTON_SELECT -> Destino.Pad(Boton.SELECT)
            KeyEvent.KEYCODE_BUTTON_MODE -> Destino.Hacer(Accion.MENU)
            KeyEvent.KEYCODE_DPAD_UP -> Destino.Pad(Boton.ARRIBA)
            KeyEvent.KEYCODE_DPAD_DOWN -> Destino.Pad(Boton.ABAJO)
            KeyEvent.KEYCODE_DPAD_LEFT -> Destino.Pad(Boton.IZQUIERDA)
            KeyEvent.KEYCODE_DPAD_RIGHT -> Destino.Pad(Boton.DERECHA)
            else -> null
        }
    }

    /** ¿Es una tecla de mando (para no quitarle a Android el volumen ni el botón atrás del móvil)? */
    fun esDeMando(tecla: Int): Boolean = KeyEvent.isGamepadButton(tecla) || tecla in setOf(
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER)

    /** Los ejes de un mando en un momento (los gatillos llegan como LTRIGGER/RTRIGGER o, en algunos mandos, como BRAKE/GAS). */
    data class Ejes(
        val hatX: Float = 0f, val hatY: Float = 0f, val x: Float = 0f, val y: Float = 0f,
        val z: Float = 0f, val rz: Float = 0f, val gatilloL: Float = 0f, val gatilloR: Float = 0f,
    )

    /** Un valor de eje a -1, 0 o 1 con su zona muerta. */
    fun cuantiza(v: Float, zona: Float): Float = if (abs(v) < zona) 0f else if (v > 0) 1f else -1f

    /** La cruceta que sale de los ejes: la del «hat» si se está usando y, si no, el stick izquierdo convertido en cruceta. */
    fun crucetaDe(e: Ejes, zona: Float): Pair<Float, Float> {
        val hx = cuantiza(e.hatX, 0.5f); val hy = cuantiza(e.hatY, 0.5f)
        return if (hx != 0f || hy != 0f) hx to hy else cuantiza(e.x, zona) to cuantiza(e.y, zona)
    }

    /** Lo que hay que mandar al juego (o hacer) como resultado de una entrada. */
    sealed interface Salida {
        data class Boton(val boton: Mandos.Boton, val abajo: Boolean) : Salida
        data class Cruceta(val x: Float, val y: Float) : Salida
        data class Analogico(val izquierdo: Boolean, val x: Float, val y: Float) : Salida
        data class Hacer(val accion: Accion, val abajo: Boolean) : Salida
    }

    /**
     * El estado de todos los mandos a la vez (el físico o los físicos y el táctil), que se funden en un solo jugador: un botón está
     * pulsado mientras lo esté en alguno de ellos (pulsarlo en el táctil y en el mando a la vez no lo «suelta» al levantar uno), y se
     * mandan solo los cambios. También resuelve el combo Select + Start (menú) y los gatillos analógicos como botones.
     */
    class Estado(var sistema: String, var porEtiqueta: Boolean = false, var zonaMuerta: Float = 0.35f) {
        private val fuentes = HashMap<Destino, MutableSet<String>>()
        private val crucetas = HashMap<String, Pair<Float, Float>>()
        private var ultimaCruceta = 0f to 0f
        private val analogicos = HashMap<Boolean, Pair<Float, Float>>()
        /** Teclas que se comieron en un combo (su «soltar» tampoco va al juego). */
        private val enCombo = HashSet<String>()

        private fun pulsado(d: Destino) = fuentes[d]?.isNotEmpty() == true

        private fun cambia(fuente: String, d: Destino, abajo: Boolean): Salida? {
            val antes = pulsado(d)
            val s = fuentes.getOrPut(d) { HashSet() }
            if (abajo) s.add(fuente) else s.remove(fuente)
            val ahora = pulsado(d)
            if (antes == ahora) return null
            return when (d) { is Destino.Pad -> Salida.Boton(d.boton, ahora); is Destino.Hacer -> Salida.Hacer(d.accion, ahora); Destino.Nada -> null }
        }

        /** Una tecla de un mando físico. [mapa]: lo que cambiaste tú para ese mando (tecla → destino). */
        fun tecla(dispositivo: String, tecla: Int, abajo: Boolean, mapa: Map<Int, Destino> = emptyMap()): List<Salida> {
            val d = mapa[tecla] ?: porDefecto(tecla, sistema, porEtiqueta) ?: return emptyList()
            if (d == Destino.Nada) return emptyList()
            return destino("$dispositivo:t$tecla", d, abajo)
        }

        /** Un control del mando táctil (o cualquier otra fuente que ya sepa a qué destino va). */
        fun destino(fuente: String, d: Destino, abajo: Boolean): List<Salida> {
            // Select + Start: el menú (sin mandar Start al juego)
            if (abajo && d == Destino.Pad(Boton.START) && pulsado(Destino.Pad(Boton.SELECT))) {
                enCombo.add(fuente); return listOf(Salida.Hacer(Accion.MENU, true))
            }
            if (!abajo && enCombo.remove(fuente)) return listOf(Salida.Hacer(Accion.MENU, false))
            return listOfNotNull(cambia(fuente, d, abajo))
        }

        /** Los ejes de un mando físico: la cruceta (hat o stick), el stick como analógico y los gatillos como botones. */
        fun ejes(dispositivo: String, e: Ejes, mapa: Map<Int, Destino> = emptyMap()): List<Salida> {
            val out = ArrayList<Salida>()
            val dis = disposicion(sistema)
            crucetas[dispositivo] = if (dis.analogico) cuantiza(e.hatX, 0.5f) to cuantiza(e.hatY, 0.5f) else crucetaDe(e, zonaMuerta)
            cruceta()?.let { out += it }
            if (dis.analogico) {
                val izq = (if (abs(e.x) < zonaMuerta) 0f else e.x) to (if (abs(e.y) < zonaMuerta) 0f else e.y)
                val der = (if (abs(e.z) < zonaMuerta) 0f else e.z) to (if (abs(e.rz) < zonaMuerta) 0f else e.rz)
                if (analogicos[true] != izq) { analogicos[true] = izq; out += Salida.Analogico(true, izq.first, izq.second) }
                if (analogicos[false] != der) { analogicos[false] = der; out += Salida.Analogico(false, der.first, der.second) }
            }
            // Los gatillos analógicos hacen lo mismo que su botón (L2/R2), con su mapeo
            for ((tecla, v) in listOf(KeyEvent.KEYCODE_BUTTON_L2 to e.gatilloL, KeyEvent.KEYCODE_BUTTON_R2 to e.gatilloR)) {
                val d = mapa[tecla] ?: porDefecto(tecla, sistema, porEtiqueta) ?: continue
                if (d == Destino.Nada) continue
                out += destino("$dispositivo:g$tecla", d, v > 0.5f)
            }
            return out
        }

        /** La cruceta del mando táctil. */
        fun crucetaTactil(x: Float, y: Float): List<Salida> {
            crucetas["tactil"] = cuantiza(x, 0.5f) to cuantiza(y, 0.5f)
            return listOfNotNull(cruceta())
        }

        /** La cruceta que se manda: la suma de todas las fuentes, sin pasarse de 1. */
        private fun cruceta(): Salida? {
            val x = crucetas.values.sumOf { it.first.toDouble() }.toFloat().coerceIn(-1f, 1f)
            val y = crucetas.values.sumOf { it.second.toDouble() }.toFloat().coerceIn(-1f, 1f)
            if ((x to y) == ultimaCruceta) return null
            ultimaCruceta = x to y
            return Salida.Cruceta(x, y)
        }

        /** Suelta todo (al abrir el menú o al perder el foco): nada se queda pulsado en el juego. */
        fun soltarTodo(): List<Salida> {
            val out = ArrayList<Salida>()
            for ((d, s) in fuentes) if (s.isNotEmpty()) { s.clear(); if (d is Destino.Pad) out += Salida.Boton(d.boton, false) }
            enCombo.clear(); crucetas.clear()
            if (ultimaCruceta != (0f to 0f)) { ultimaCruceta = 0f to 0f; out += Salida.Cruceta(0f, 0f) }
            return out
        }
    }

    /** Lo que se guarda de un mando que has cambiado: tecla → destino, en texto («96=pad:A;103=accion:AVANCE»). */
    fun mapaATexto(m: Map<Int, Destino>): String = m.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value.clave()}" }
    fun mapaDeTexto(t: String): Map<Int, Destino> = t.split(';').mapNotNull { p ->
        val k = p.substringBefore('=', "").toIntOrNull() ?: return@mapNotNull null
        Destino.deClave(p.substringAfter('=', ""))?.let { k to it }
    }.toMap()
}
