package com.gonzalo.hiedra

import com.gonzalo.hiedra.NubeP.PasoPortada.*
import org.junit.Assert.assertEquals
import org.junit.Test

/** Las portadas que eliges, en todos tus móviles: manda el cambio más reciente, venga de donde venga (como los libros en Atrio). */
class PortadasNubeTest {
    private fun d(local: String?, ultimo: String, nube: String?, tLocal: Long = 0, tNube: Long = 0, quitadaEnNube: Boolean = false, quitadaAqui: Boolean = false) =
        NubeP.decidirPortada(local, tLocal, ultimo, nube, tNube, quitadaEnNube, quitadaAqui)

    @Test fun laPonesAquiYSube() {
        assertEquals(SUBIR, d("a", "", null))
        assertEquals("la cambias aquí", SUBIR, d("b", "a", "a"))
        assertEquals("ya estaba subida", NADA, d("a", "a", "a"))
        assertEquals("la misma en los dos sitios, sin apuntar", ANOTAR, d("a", "", "a"))
    }

    @Test fun laPusisteEnOtroMovilYSeBaja() {
        assertEquals("este móvil no tenía", BAJAR, d(null, "", "a"))
        assertEquals("la cambiaste en otro: se cambia aquí aunque aquí hubiera una", BAJAR, d("a", "a", "b"))
        assertEquals("las dos cambiaron: la más nueva (la de la nube)", BAJAR, d("x", "a", "b", tLocal = 10, tNube = 20))
        assertEquals("las dos cambiaron: la más nueva (la de aquí)", SUBIR, d("x", "a", "b", tLocal = 30, tNube = 20))
    }

    @Test fun seHabiaPerdidoAqui() {
        // El juego dejó de apuntar a su portada (la mezcla con otro móvil la quitaba): se vuelve a poner
        assertEquals(REENLAZAR, d(null, "a", "a"))
    }

    @Test fun quitarlaViajaTambien() {
        assertEquals("la quitas aquí: se quita de tu kDrive", QUITAR_EN_NUBE, d(null, "a", "a", quitadaAqui = true))
        assertEquals("la quitaste en otro móvil: se quita aquí", QUITAR_AQUI, d("a", "a", null, quitadaEnNube = true))
        assertEquals("pero si aquí pusiste otra después, sube", SUBIR, d("b", "a", null, quitadaEnNube = true))
        assertEquals(NADA, d(null, "", null))
    }

    @Test fun otroMovilSinPortadaDeInternetNoBorraLaTuya() {
        val aqui = Juego("j1", "Hades", portadaUrl = "https://sgdb/hades.png", mod = 1)
        val otro = Juego("j1", "Hades", estado = Estado.JUGANDO, mod = 5)
        val r = Mezcla.mezclar(listOf(aqui), emptyMap(), listOf(Blob("B", listOf(JuegoSync(otro)), emptyMap())), "A")
        assertEquals("https://sgdb/hades.png", r.juegos.single().portadaUrl)
        assertEquals(Estado.JUGANDO, r.juegos.single().estado)
    }
}
