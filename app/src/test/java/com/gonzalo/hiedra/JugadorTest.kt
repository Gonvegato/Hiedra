package com.gonzalo.hiedra

import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Las reglas puras del jugador interno: mandos, avance rápido, núcleos y qué estado se carga al empezar. */
class JugadorTest {
    private val pad = { b: Mandos.Boton -> Mandos.Destino.Pad(b) }

    // ------------------------------------------------------------------ mandos

    @Test fun porDefectoVaPorPosicion() {
        // El botón de la derecha de un mando de Android (BUTTON_B) es el A del juego, como en una consola de Nintendo
        assertEquals(pad(Mandos.Boton.A), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_B, "gba", porEtiqueta = false))
        assertEquals(pad(Mandos.Boton.B), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_A, "gba", porEtiqueta = false))
        assertEquals(pad(Mandos.Boton.Y), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_X, "gba", porEtiqueta = false))
    }

    @Test fun porEtiquetaVaPorLaLetra() {
        assertEquals(pad(Mandos.Boton.A), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_A, "gba", porEtiqueta = true))
        assertEquals(pad(Mandos.Boton.B), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_B, "gba", porEtiqueta = true))
    }

    @Test fun r2AceleraSiElSistemaNoLoUsa() {
        assertEquals(Mandos.Destino.Hacer(Mandos.Accion.AVANCE), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_R2, "gba", false))
        assertNull(Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_L2, "gba", false))
        // Un sistema sin disposición conocida usa todos los botones: R2 es R2
        assertEquals(pad(Mandos.Boton.R2), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_R2, "psx", false))
        assertEquals(Mandos.Destino.Hacer(Mandos.Accion.MENU), Mandos.porDefecto(KeyEvent.KEYCODE_BUTTON_MODE, "gba", false))
    }

    @Test fun reasignarDejaSinNadaAlQueLoTenia() {
        // Le doy el A del juego al botón de abajo (BUTTON_A): el de la derecha (BUTTON_B), que era A, se queda sin nada
        val m = Mandos.reasignar(emptyMap(), KeyEvent.KEYCODE_BUTTON_A, pad(Mandos.Boton.A), "gba", false)
        assertEquals(pad(Mandos.Boton.A), Mandos.efectivo(KeyEvent.KEYCODE_BUTTON_A, m, "gba", false))
        assertEquals(Mandos.Destino.Nada, Mandos.efectivo(KeyEvent.KEYCODE_BUTTON_B, m, "gba", false))
        // Solo un botón hace A
        assertEquals(1, Mandos.teclasDeMando.count { Mandos.efectivo(it, m, "gba", false) == pad(Mandos.Boton.A) })
    }

    @Test fun mapaDeIdaYVuelta() {
        val m = mapOf(96 to pad(Mandos.Boton.A), 103 to Mandos.Destino.Hacer(Mandos.Accion.AVANCE), 99 to Mandos.Destino.Nada)
        assertEquals(m, Mandos.mapaDeTexto(Mandos.mapaATexto(m)))
        assertEquals(emptyMap<Int, Mandos.Destino>(), Mandos.mapaDeTexto(""))
        assertEquals(emptyMap<Int, Mandos.Destino>(), Mandos.mapaDeTexto("basura;=;x=pad:A;1=pad:NOEXISTE"))
    }

    @Test fun mandoYTactilSeFunden() {
        val e = Mandos.Estado("gba")
        val a = pad(Mandos.Boton.A)
        assertEquals(listOf(Mandos.Salida.Boton(Mandos.Boton.A, true)), e.tecla("mando", KeyEvent.KEYCODE_BUTTON_B, true))
        // Pulsarlo también en el táctil no manda nada (ya está pulsado)
        assertEquals(emptyList<Mandos.Salida>(), e.destino("tactil", a, true))
        // Soltarlo en el mando no lo suelta (sigue en el táctil)
        assertEquals(emptyList<Mandos.Salida>(), e.tecla("mando", KeyEvent.KEYCODE_BUTTON_B, false))
        assertEquals(listOf(Mandos.Salida.Boton(Mandos.Boton.A, false)), e.destino("tactil", a, false))
    }

    @Test fun selectMasStartAbreElMenuSinMandarStart() {
        val e = Mandos.Estado("gba")
        assertEquals(listOf(Mandos.Salida.Boton(Mandos.Boton.SELECT, true)), e.tecla("m", KeyEvent.KEYCODE_BUTTON_SELECT, true))
        assertEquals(listOf(Mandos.Salida.Hacer(Mandos.Accion.MENU, true)), e.tecla("m", KeyEvent.KEYCODE_BUTTON_START, true))
        assertEquals(listOf(Mandos.Salida.Hacer(Mandos.Accion.MENU, false)), e.tecla("m", KeyEvent.KEYCODE_BUTTON_START, false))
        // Start solo, sin Select, sí va al juego
        e.tecla("m", KeyEvent.KEYCODE_BUTTON_SELECT, false)
        assertEquals(listOf(Mandos.Salida.Boton(Mandos.Boton.START, true)), e.tecla("m", KeyEvent.KEYCODE_BUTTON_START, true))
    }

    @Test fun gatilloAnalogicoComoBoton() {
        val e = Mandos.Estado("gba")
        val acelera = Mandos.Salida.Hacer(Mandos.Accion.AVANCE, true)
        assertTrue(acelera in e.ejes("m", Mandos.Ejes(gatilloR = 0.9f)))
        // Seguir apretado no repite
        assertFalse(e.ejes("m", Mandos.Ejes(gatilloR = 1f)).any { it is Mandos.Salida.Hacer })
        assertTrue(Mandos.Salida.Hacer(Mandos.Accion.AVANCE, false) in e.ejes("m", Mandos.Ejes(gatilloR = 0.1f)))
    }

    @Test fun crucetaDelHatODelStick() {
        assertEquals(1f to 0f, Mandos.crucetaDe(Mandos.Ejes(hatX = 1f, x = -1f), 0.35f))
        assertEquals(-1f to 0f, Mandos.crucetaDe(Mandos.Ejes(x = -0.8f), 0.35f))
        assertEquals(0f to 0f, Mandos.crucetaDe(Mandos.Ejes(x = 0.2f, y = -0.3f), 0.35f))
        val e = Mandos.Estado("gba")
        assertEquals(listOf(Mandos.Salida.Cruceta(0f, -1f)), e.ejes("m", Mandos.Ejes(hatY = -1f)))
        // Lo mismo otra vez: no se repite
        assertEquals(emptyList<Mandos.Salida>(), e.ejes("m", Mandos.Ejes(hatY = -1f)))
    }

    @Test fun soltarTodoNoDejaNadaPulsado() {
        val e = Mandos.Estado("gba")
        e.tecla("m", KeyEvent.KEYCODE_BUTTON_B, true)
        e.crucetaTactil(1f, 0f)
        val s = e.soltarTodo()
        assertTrue(Mandos.Salida.Boton(Mandos.Boton.A, false) in s)
        assertTrue(Mandos.Salida.Cruceta(0f, 0f) in s)
        assertEquals(emptyList<Mandos.Salida>(), e.soltarTodo())
    }

    @Test fun tactilVaAlMismoBoton() {
        assertEquals(Mandos.Destino.Pad(Mandos.Boton.START), MandoTactil.destinoDe(KeyEvent.KEYCODE_BUTTON_START))
        assertNull(MandoTactil.destinoDe(MandoTactil.ID_CRUZ))
    }

    // ------------------------------------------------------------------ avance rápido

    @Test fun avanceMantener() {
        val a = AvanceRapido(AvanceRapido.Modo.MANTENER, 4)
        assertEquals(1, a.factor)
        assertTrue(a.pulsar(true)); assertEquals(4, a.factor)
        assertTrue(a.pulsar(false)); assertEquals(1, a.factor)
    }

    @Test fun avanceConmutar() {
        val a = AvanceRapido(AvanceRapido.Modo.CONMUTAR, 8)
        assertTrue(a.pulsar(true)); assertFalse(a.pulsar(false)); assertEquals(8, a.factor)
        assertTrue(a.pulsar(true)); assertEquals(1, a.factor)
        a.pulsar(true); assertTrue(a.apagar()); assertFalse(a.apagar())
    }

    @Test fun velocidadReal() {
        assertEquals(4f, AvanceRapido.real(60f, 4), 0.001f)
        assertEquals(3.2f, AvanceRapido.real(48f, 4), 0.001f)
        assertEquals("▶▶ ×4", AvanceRapido.indicador(4, 3.95f))
        assertEquals("▶▶ ×4 · real ×3,2", AvanceRapido.indicador(4, 3.2f))
        assertEquals("▶▶ ×2", AvanceRapido.indicador(2, null))
    }

    // ------------------------------------------------------------------ núcleos

    @Test fun nucleoDeGba() {
        val n = Nucleos.para("gba")!!
        assertEquals("mgba", n.id)
        assertEquals("https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/mgba_libretro_android.so.zip", Nucleos.url(n.id))
        assertNull(Nucleos.para("snes"))
    }

    @Test fun fechaYDescripcion() {
        val t = Nucleos.fechaHttp("Wed, 04 Nov 2026 07:22:14 GMT")
        assertEquals(1793776934000L, t)
        assertEquals(0L, Nucleos.fechaHttp(null))
        assertEquals(0L, Nucleos.fechaHttp("ayer"))
        val n = Nucleos.para("gba")!!
        assertEquals("mGBA · se descarga al jugar", Nucleos.describir(n, null))
        assertEquals("mGBA · 2,4 MB", Nucleos.describir(n, Nucleos.Instalado("mgba", 0L, 2_516_582L, "abc")))
    }

    // ------------------------------------------------------------------ estados

    private fun meta(t: Long, hash: String = "h$t", nucleo: String = "mgba", version: String = "v1") =
        Estados.Meta(Estados.AUTO, nucleo, version, t, "dev", "Móvil", hash, 10)

    @Test fun decidirEstado() {
        val d = Estados::decidir
        assertEquals(Estados.Decision.NADA, d(null, null, "mgba", "v1", false))
        assertEquals(Estados.Decision.SUBIR_Y_USAR_LOCAL, d(meta(5), null, "mgba", "v1", false))
        assertEquals(Estados.Decision.USAR_LOCAL, d(meta(5), null, "mgba", "v1", true))
        // El mismo en los dos sitios
        assertEquals(Estados.Decision.USAR_LOCAL, d(meta(5, "x"), meta(9, "x"), "mgba", "v1", false))
        // El de aquí es más nuevo
        assertEquals(Estados.Decision.SUBIR_Y_USAR_LOCAL, d(meta(9), meta(5), "mgba", "v1", false))
        // El de la nube es más nuevo: se baja; de otra versión del núcleo, se pregunta
        assertEquals(Estados.Decision.BAJAR, d(meta(5), meta(9), "mgba", "v1", true))
        assertEquals(Estados.Decision.BAJAR, d(null, meta(9), "mgba", "v1", false))
        assertEquals(Estados.Decision.PREGUNTAR, d(meta(5), meta(9, version = "v2"), "mgba", "v1", true))
        // De otro núcleo: no vale
        assertEquals(Estados.Decision.NADA, d(null, meta(9, nucleo = "vbam"), "mgba", "v1", false))
    }

    @Test fun metaDeIdaYVuelta() {
        val m = meta(123).copy(id = 77)
        assertEquals(m, Estados.Meta.deJson(m.aJson()))
        assertNull(Estados.Meta.deJson(org.json.JSONObject()))
    }
}

/** El jugador interno en la lista de emuladores: sale el primero en GBA y es el automático. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanzarInternoTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun cargar() { if (Catalogo.sistemas.isEmpty()) Catalogo.analizar(File("src/main/assets/emulators.json").readText()) }

    @Test fun hiedraEsLaPrimeraEnGba() {
        val r = RomRef("content://x", "/storage/emulated/0/Roms/gba/Juego.gba")
        val todos = Lanzar.evaluar(ctx, "gba", r)
        assertTrue(Lanzar.esInterno(todos.first()))
        assertEquals(Lanzar.HIEDRA, todos.first().etiqueta)
        assertEquals(1, todos.count { Lanzar.esInterno(it) })
        assertTrue(Lanzar.esInterno(Lanzar.elegirSistema(ctx, "gba")))
    }

    @Test fun romEnOtroMovilConElJugadorDeHiedra() {
        Biblioteca.cargada = true; Biblioteca.juegos.clear(); Archivos.limpiarParaPruebas()
        // Una ROM de GBA que está en otro móvil (aquí no hay archivo): el jugador de Hiedra la baja él solo de tu kDrive
        Biblioteca.juegos.add(Juego("g1", "Golden Sun", lanzadores = listOf(Lanzador.rom("gba", "Golden Sun"))))
        val gba = Biblioteca.por("g1")!!
        assertTrue(gba.enOtroDispositivo)
        assertNull(Lanzar.lanzar(ctx, gba))
        val abierta = org.robolectric.Shadows.shadowOf(ctx as android.app.Application).nextStartedActivity
        assertEquals(JuegoActivity::class.java.name, abierta.component?.className)
        // Con otro emulador hace falta el archivo: se dice cómo bajarlo
        Biblioteca.juegos.add(Juego("s1", "Chrono Trigger", lanzadores = listOf(Lanzador.rom("snes", "Chrono Trigger"))))
        assertEquals("Esta ROM está en otro de tus móviles: en su ficha, «Jugar» la baja de tu kDrive.", Lanzar.lanzar(ctx, Biblioteca.por("s1")!!))
    }

    @Test fun mezclarNoQuitaLaPortadaQuePusisteMientras() {
        Biblioteca.juegos.clear()
        val j = Juego("p1", "Advance Wars", mod = 1)
        Biblioteca.juegos.add(j)
        val mezcla = listOf(j.copy(titulo = "Advance Wars 2", mod = 2))   // hecha con la copia de antes
        Biblioteca.juegos[0] = j.copy(portada = "/files/portadas/p1-9.jpg")  // mientras, pusiste su portada
        Biblioteca.aplicarMezcla(mezcla, emptyMap())
        val r = Biblioteca.por("p1")!!
        assertEquals("/files/portadas/p1-9.jpg", r.portada); assertEquals("Advance Wars 2", r.titulo)
        Biblioteca.juegos.clear()
    }

    @Test fun sinNucleoNoSale() {
        val r = RomRef("content://x", "/storage/emulated/0/Roms/snes/Juego.sfc")
        assertFalse(Lanzar.evaluar(ctx, "snes", r).any { Lanzar.esInterno(it) })
        assertFalse(Lanzar.esInterno(Lanzar.elegirSistema(ctx, "snes")))
    }
}
