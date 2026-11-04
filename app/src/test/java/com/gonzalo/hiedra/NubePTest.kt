package com.gonzalo.hiedra

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Guardados, ROMs y portadas por kDrive y la cuenta, con dos «móviles» y los servidores de mentira de Atrio (Firebase y kDrive). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NubePTest {
    companion object {
        private var fb: Process? = null; private var kd: Process? = null
        private val pf = 9500 + (System.nanoTime() % 200).toInt().let { if (it < 0) -it else it }
        private val pk = pf + 250
        private val dir = File("/home/user/Atrio-Suite-2.0/herramientas")

        private fun espera(p: Int) { repeat(60) { try { URL("http://127.0.0.1:$p/__dump").openConnection().apply { connectTimeout = 300 }.getInputStream().close(); return } catch (e: java.io.IOException) { if (e.message?.contains("Server returned") == true) return; Thread.sleep(100) } }; fail("no arranca $p") }

        @BeforeClass @JvmStatic fun arranca() {
            Assume.assumeTrue("faltan los servidores de mentira de Atrio", File(dir, "fakefirebase.js").exists() && File(dir, "fakekdrive.js").exists())
            fb = ProcessBuilder("node", File(dir, "fakefirebase.js").path, pf.toString()).redirectErrorStream(true).start()
            kd = ProcessBuilder("node", File(dir, "fakekdrive.js").path, pk.toString()).redirectErrorStream(true).start()
            espera(pf); espera(pk)
        }
        @AfterClass @JvmStatic fun para() { fb?.destroy(); kd?.destroy() }

        fun http(m: String, p: String, body: String? = null): String {
            val c = URL("http://127.0.0.1:$pf$p").openConnection() as HttpURLConnection
            if (m == "PUT") { c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", m) } else c.requestMethod = m
            if (body != null) { c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
            return (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        }
    }

    // Una carpeta de verdad (en vez de SAF) con la misma interfaz
    class FArch(val f: File) : NubeP.Arch {
        override val nombre get() = f.name; override val uri get() = f.toURI().toString(); override val tamano get() = f.length(); override val modificado get() = f.lastModified()
        override fun leer(): InputStream = f.inputStream(); override fun escribir(): OutputStream = f.outputStream()
    }
    class FCarpeta(val raiz: File) : NubeP.Carpeta {
        override fun buscar(base: String, ext: Set<String>) = raiz.walkTopDown().filter { it.isFile && it.nameWithoutExtension.equals(base, true) && it.extension.lowercase() in ext }.map { FArch(it) }.toList()
        override fun crear(nombre: String): NubeP.Arch? = FArch(File(raiz, nombre).also { it.parentFile?.mkdirs(); it.createNewFile() })
    }

    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val memoria = HashMap<String, Map<String, Any?>>(); private var actual = ""
    private fun movil(id: String) {
        val sp = ctx.getSharedPreferences("hiedra_nube_p", 0)
        if (actual.isNotEmpty()) memoria[actual] = sp.all
        sp.edit().clear().apply(); memoria[id]?.forEach { (k, v) -> if (v is String) sp.edit().putString(k, v).apply() }
        actual = id
        ctx.getSharedPreferences("hiedra_cuenta", 0).edit().putString("dispositivo", id).apply(); Cuenta.cargar(ctx)
    }
    private fun tmp(n: String) = File(ctx.cacheDir, "nubep-" + System.nanoTime() + "-" + n).apply { mkdirs() }

    @Test fun guardadosRomsYPortadas() {
        Cuenta.dbUrl = "http://127.0.0.1:$pf"; Cuenta.authBase = "http://127.0.0.1:$pf"; KDrive.api = "http://127.0.0.1:$pk"
        val sign = JSONObject(http("POST", "/v1/accounts:signUp?key=K", """{"email":"gon@ronron.example.com","password":"secreto1","returnSecureToken":true}"""))
        http("PUT", "/config/webKey.json?auth=${sign.getString("idToken")}", "\"K\"")
        Biblioteca.cargada = true; Archivos.limpiarParaPruebas()
        val lz = Lanzador.rom("gba", "Zelda (E)")
        val z = Juego("a1", "Zelda", lanzadores = listOf(lz), mod = 1)
        Archivos.poner(lz.clave, "content://a", "")

        // Móvil A: guarda una partida (en una subcarpeta, como RetroArch con «ordenar por núcleo»)
        movil("A"); Cuenta.salir(); runBlockingEntrar()
        KDrive.save(ctx, "tok", "777")
        val dA = tmp("A"); val sa = File(dA, "mGBA/Zelda (E).srm").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(2000) { 1 }) }
        File(dA, "mGBA/Otro juego.srm").writeBytes(ByteArray(10))
        assertNull("sin nada en la nube no hay que bajar", NubeP.antes(ctx, z, FCarpeta(dA)))
        assertEquals(1, NubeP.despues(ctx, z, FCarpeta(dA)))
        assertEquals("sin cambios no se vuelve a subir", 0, NubeP.despues(ctx, z, FCarpeta(dA)))

        // Móvil B, vacío: antes de jugar baja el guardado de A
        movil("B"); runBlockingEntrar()
        val dB = tmp("B")
        assertEquals("Guardado cargado desde la nube", NubeP.antes(ctx, z, FCarpeta(dB)))
        assertArrayEquals(sa.readBytes(), File(dB, "Zelda (E).srm").readBytes())
        assertNull("ya lo tiene", NubeP.antes(ctx, z, FCarpeta(dB)))
        File(dB, "Zelda (E).srm").writeBytes(ByteArray(2500) { 2 })
        assertEquals(1, NubeP.despues(ctx, z, FCarpeta(dB)))

        // A vuelve a jugar: baja lo de B (y deja copia de lo suyo)
        movil("A")
        assertEquals("Guardado cargado desde la nube", NubeP.antes(ctx, z, FCarpeta(dA)))
        assertEquals(2500, sa.length())
        assertEquals(1, File(ctx.filesDir, "copias_guardados").walkTopDown().count { it.isFile })

        // Conflicto: los dos cambian sin sincronizar entre medias → gana la nube y queda copia
        File(dA, "mGBA/Zelda (E).srm").writeBytes(ByteArray(3000) { 3 })
        movil("B"); File(dB, "Zelda (E).srm").writeBytes(ByteArray(2600) { 4 }); assertEquals(1, NubeP.despues(ctx, z, FCarpeta(dB)))
        movil("A")
        val aviso = NubeP.antes(ctx, z, FCarpeta(dA))
        assertTrue(aviso, aviso!!.contains("copia"))
        assertEquals(2600, sa.length())
        assertEquals(2, File(ctx.filesDir, "copias_guardados").walkTopDown().count { it.isFile })

        // Un móvil nuevo con un guardado propio más reciente que el de la nube (nunca sincronizado) no lo pierde
        movil("C"); runBlockingEntrar()
        val dC = tmp("C"); val sc = File(dC, "Zelda (E).srm").apply { writeBytes(ByteArray(777)); setLastModified(System.currentTimeMillis() + 60_000) }
        assertNull(NubeP.antes(ctx, z, FCarpeta(dC))); assertEquals(777, sc.length())
        assertEquals(1, NubeP.despues(ctx, z, FCarpeta(dC)))

        // ROM: A la sube, B (sin ella) la baja y es idéntica
        movil("A")
        val rom = File(tmp("roms"), "Zelda (E).gba").apply { writeBytes(ByteArray(6_000_000) { (it % 251).toByte() }) }   // >5 MB: subida a trozos
        NubeP.subirRom(ctx, z, FArch(rom))
        movil("B")
        val f = NubeP.romEnNube(z)!!; assertEquals("Zelda (E).gba", f.n); assertEquals(rom.length(), f.s)
        val bajada = NubeP.bajarRom(ctx, z, f, FCarpeta(tmp("romsB")))
        assertEquals(NubeP.hashDe(rom.inputStream()), NubeP.hashDe(bajada.leer()))

        // Portada elegida en A → aparece en B (el mismo juego, por su id)
        movil("A"); Biblioteca.juegos.clear(); Biblioteca.borrados.clear()
        val pdir = File(ctx.filesDir, "portadas").apply { mkdirs() }
        val pa = File(pdir, "a1-1.jpg").apply { writeBytes(ByteArray(900) { 9 }) }
        Biblioteca.juegos.add(z.copy(portada = pa.absolutePath))
        assertFalse(NubeP.portadas(ctx))
        movil("B"); Biblioteca.juegos.clear(); Biblioteca.borrados.clear(); pdir.listFiles()?.forEach { it.delete() }
        Biblioteca.juegos.add(z.copy(portada = ""))
        assertTrue(NubeP.portadas(ctx))
        val pb = Biblioteca.por("a1")!!.portada
        assertTrue(pb, pb.isNotBlank()); assertArrayEquals(ByteArray(900) { 9 }, File(pb).readBytes())
        assertFalse("no se vuelve a bajar", NubeP.portadas(ctx))

        // Subida automática de las ROMs que faltan: sube solo las que están aquí y no en la nube, una vez
        movil("A"); Biblioteca.juegos.clear(); Biblioteca.borrados.clear()
        val lm = Lanzador.rom("gba", "Metroid"); Archivos.poner(lm.clave, "content://m", "")
        val m = Juego("a2", "Metroid", lanzadores = listOf(lm), mod = 1)
        Biblioteca.juegos.add(z); Biblioteca.juegos.add(m); Biblioteca.juegos.add(Juego("a3", "Sin archivo", lanzadores = listOf(Lanzador.rom("gba", "Sin archivo")), mod = 1))
        val mf = File(tmp("roms2"), "Metroid.gba").apply { writeBytes(ByteArray(4000) { 5 }) }
        assertEquals("solo Metroid: Zelda ya estaba y la otra no está aquí", 1, NubeP.subirRoms(ctx) { j -> if (j.id == "a2") FArch(mf) else FArch(rom) })
        assertEquals(mf.length(), NubeP.romEnNube(m)!!.s)
        assertEquals(0, NubeP.subirRoms(ctx) { FArch(mf) })
        NubeP.ponRomsSolas(ctx, false)
        val lo = Lanzador.rom("gba", "Otra"); Archivos.poner(lo.clave, "content://o", ""); Biblioteca.juegos.add(Juego("a4", "Otra", lanzadores = listOf(lo), mod = 1))
        assertEquals("con el interruptor apagado no sube", 0, NubeP.subirRoms(ctx) { FArch(mf) }); NubeP.ponRomsSolas(ctx, true)

        // El índice en la cuenta no lleva rutas ni nombres de tu móvil, solo hash, id de kDrive y nombres de archivo
        val raw = http("GET", "/__dump")
        assertFalse(raw.contains("/storage/")); Cuenta.salir()
        Biblioteca.juegos.clear(); Archivos.limpiarParaPruebas()
    }

    private fun runBlockingEntrar() = kotlinx.coroutines.runBlocking { Cuenta.entrar(ctx, "gon", "secreto1") }
}
