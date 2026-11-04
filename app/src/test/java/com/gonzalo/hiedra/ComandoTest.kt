package com.gonzalo.hiedra

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/** Prueba el intérprete de comandos con el catálogo real (assets/emulators.json, sacado de ES-DE). Sin emulador ni móvil. */
class ComandoTest {
    private fun entorno(vararg instalados: String) = object : Entorno {
        override fun instalado(paquete: String) = paquete in instalados
        override val internos = "/data/data"
        override val externos = "/storage/emulated/0"
    }
    private val uri = "content://com.android.externalstorage.documents/tree/primary%3ARoms/document/primary%3ARoms%2Fnds%2FPokemon.nds"
    private val rom = RomRef(uri, "/storage/emulated/0/Roms/nds/Pokemon.nds")

    @Before fun cargar() { if (Catalogo.sistemas.isEmpty()) Catalogo.analizar(File("src/main/assets/emulators.json").readText()) }

    private fun plantilla(sistema: String, etiqueta: String) =
        Catalogo.sistema(sistema)!!.comandos.first { it.first == etiqueta }.second

    @Test fun melonDsStandalone() {
        val r = Comando.resolver(plantilla("nds", "melonDS (Standalone)"), rom, entorno("me.magnum.melonds"))
        r as Resultado.Ok
        assertEquals("me.magnum.melonds", r.spec.paquete)
        assertEquals("me.magnum.melonds.ui.emulator.EmulatorActivity", r.spec.clase)
        assertEquals("me.magnum.melonds.LAUNCH_ROM", r.spec.accion)
        assertEquals(uri, r.spec.extras.single { it.clave == "uri" }.valor)
        assertEquals(uri, r.spec.conceder)
    }

    @Test fun drasticAbreElDocumentoYLimpiaLaTarea() {
        val r = Comando.resolver(plantilla("nds", "DraStic (Standalone)"), rom, entorno("com.dsemu.drastic")) as Resultado.Ok
        assertEquals("com.dsemu.drastic", r.spec.paquete)
        assertEquals("com.dsemu.drastic.DraSticActivity", r.spec.clase)
        assertEquals(uri, r.spec.datos)
        assertTrue(r.spec.limpiarTarea && r.spec.limpiarArriba)
    }

    @Test fun mgbaPorRetroArchUsaLaRutaYElNucleo() {
        val gba = RomRef(uri, "/storage/emulated/0/Roms/gba/Emerald.gba")
        val r = Comando.resolver(plantilla("gba", "mGBA"), gba, entorno("com.retroarch.aarch64")) as Resultado.Ok
        assertEquals("com.retroarch.aarch64", r.spec.paquete)
        assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", r.spec.clase)
        val e = r.spec.extras.associate { it.clave to it.valor }
        assertEquals("/storage/emulated/0/Roms/gba/Emerald.gba", e["ROM"])
        assertEquals("/data/data/com.retroarch.aarch64/cores/mgba_libretro_android.so", e["LIBRETRO"])
        assertEquals("/storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg", e["CONFIGFILE"])
        assertNull(r.spec.conceder)
    }

    @Test fun pizzaBoyPasaElDocumentoEnUnExtra() {
        val r = Comando.resolver(plantilla("gba", "Pizza Boy GBA (Standalone)"), rom, entorno("it.dbtecno.pizzaboygba")) as Resultado.Ok
        assertEquals(uri, r.spec.extras.single { it.clave == "rom_uri" }.valor)
        assertEquals(uri, r.spec.conceder)
    }

    @Test fun sinInstalarDevuelveElPaqueteParaLaTienda() {
        val r = Comando.resolver(plantilla("nds", "DraStic (Standalone)"), rom, entorno())
        r as Resultado.Falta
        assertEquals("com.dsemu.drastic", r.paquete)
        assertEquals("DRASTIC", r.emulador)
    }

    @Test fun retroArchSinRutaNoSeInventaUna() {
        val sinRuta = RomRef(uri, null)
        val r = Comando.resolver(plantilla("gba", "mGBA"), sinRuta, entorno("com.retroarch"))
        assertTrue(r is Resultado.Falta)
        // y los que usan el documento siguen funcionando sin ruta
        assertTrue(Comando.resolver(plantilla("nds", "DraStic (Standalone)"), sinRuta, entorno("com.dsemu.drastic")) is Resultado.Ok)
    }

    @Test fun rutaDeDocumentos() {
        assertEquals("/storage/emulated/0/Roms/gba/a b.gba", Comando.rutaDeDocId("primary:Roms/gba/a b.gba"))
        assertEquals("/storage/1234-ABCD/Roms/x.nds", Comando.rutaDeDocId("1234-ABCD:Roms/x.nds"))
        assertNull(Comando.rutaDeDocId("msf:1234"))
        assertNull(Comando.rutaDeDocId("primary:../etc/passwd"))
        assertNull(Comando.rutaDeDocId("raw:/storage/x"))
    }

    @Test fun detectaElSistema() {
        assertEquals("gba", Catalogo.detectar("Pokemon Emerald.gba")?.id)
        assertEquals("nds", Catalogo.detectar("Pokemon Black.nds")?.id)
        assertEquals("snes", Catalogo.detectar("Zelda.sfc")?.id)
        assertEquals("gbc", Catalogo.detectar("Oracle.gbc")?.id)
        assertEquals("gba", Catalogo.detectar("juego.zip", listOf("ROMs", "gba"))?.id)
        assertEquals("nds", Catalogo.detectar("juego.zip", listOf("ROMs", "Nintendo DS"))?.id)
        assertNull("un zip suelto es ambiguo", Catalogo.detectar("juego.zip"))
        assertNull(Catalogo.detectar("README"))
    }

    @Test fun titulosYPortadas() {
        assertEquals("Pokemon - Emerald Version", tituloDeArchivo("Pokemon - Emerald Version (USA, Europe).gba"))
        assertEquals("Super_Mario".replace('_', ' '), tituloDeArchivo("Super_Mario [!].gba"))
        assertEquals("Mario _ Luigi - Superstar Saga", nombreLibretro("Mario & Luigi - Superstar Saga"))
        assertEquals("Que_ pasa_", nombreLibretro("Que? pasa:"))
    }

    @Test fun todoElCatalogoSeInterpretaSinFallar() {
        // Ningún comando soportado del catálogo debe romper el intérprete, con todo instalado y con nada instalado
        val todos = Catalogo.emus.values.flatten().map { it.substringBefore('/') }.toTypedArray()
        var ok = 0
        for (s in Catalogo.sistemas) for ((_, t) in s.comandos) {
            if (!Comando.soportado(t)) continue
            Comando.resolver(t, rom, entorno())
            if (Comando.resolver(t, rom, entorno(*todos)) is Resultado.Ok) ok++
        }
        assertTrue("debería haber cientos de comandos utilizables, hay $ok", ok > 300)
    }

    @Test fun cuePierdeSusPistas() {
        val l = Escaneo.quitarPistas(listOf("1" to "Juego.cue", "2" to "Juego (Track 1).bin", "3" to "Juego (Track 2).bin", "4" to "Otro.bin"))
        assertEquals(listOf("Juego.cue", "Otro.bin"), l.map { it.second })
    }
}
