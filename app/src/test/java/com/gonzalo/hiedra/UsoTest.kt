package com.gonzalo.hiedra

import org.junit.Assert.*
import org.junit.Test

/** El tiempo de juego exacto: cuánto estuvo delante el emulador según los pasos que apunta Android. Sin móvil. */
class UsoTest {
    private val M = 60_000L
    private fun paso(min: Long, delante: Boolean) = Uso.Paso(min * M, delante)

    @Test fun seSumaSoloLoQueEstuvoDelante() {
        // Lanzado en el minuto 0; delante del 1 al 31, otra vez del 40 al 50; se vuelve a Hiedra en el 60
        val d = Uso.delante(listOf(paso(1, true), paso(31, false), paso(40, true), paso(50, false)), 0, 60 * M)!!
        assertEquals(40 * M, d.ms); assertEquals(50 * M, d.fin)
    }

    @Test fun siSigueDelanteAlVolverCuentaHastaAhora() {
        val d = Uso.delante(listOf(paso(1, true)), 0, 30 * M)!!
        assertEquals(29 * M, d.ms); assertEquals(30 * M, d.fin)
    }

    @Test fun unaPausaSinSuResumeCuentaDesdeElPrincipio() {
        // Android a veces no apunta el primer «delante» (ya estaba abierto): el primer «detrás» cierra desde el lanzamiento
        val d = Uso.delante(listOf(paso(20, false)), 0, 60 * M)!!
        assertEquals(20 * M, d.ms); assertEquals(20 * M, d.fin)
    }

    @Test fun dosResumeSeguidosNoCuentanDoble() {
        val d = Uso.delante(listOf(paso(1, true), paso(2, true), paso(10, false), paso(10, false)), 0, 60 * M)!!
        assertEquals(9 * M, d.ms)
    }

    @Test fun sinPasosNoSeSabe() {
        assertNull(Uso.delante(emptyList(), 0, 60 * M))
        assertNull(Uso.delante(listOf(paso(1, true)), 60 * M, 0))
    }

    @Test fun aOjoEsTodoElRatoConTopeDeDoceHoras() {
        assertEquals(30 * M, Sesion.aOjo(0, 30 * M))
        assertEquals(12 * 60 * M, Sesion.aOjo(0, 3 * 24 * 60 * M))
    }

    @Test fun unaSesionQueCruzaLaMedianocheSeRepartePorElFinExacto() {
        // Jugado 2 h que acaban a la 1:00 del día 2 (zona UTC): 1 h en el día 1 y 1 h en el día 2
        val z = java.util.TimeZone.getTimeZone("UTC")
        val fin = 24 * 60 * M + 60 * M
        val r = Sesion.repartir(fin, 120 * M, z)
        assertEquals(60 * M, r["1970-01-01"]); assertEquals(60 * M, r["1970-01-02"])
    }
}
