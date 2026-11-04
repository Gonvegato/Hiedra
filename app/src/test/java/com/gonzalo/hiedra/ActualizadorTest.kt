package com.gonzalo.hiedra

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.Assume
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Actualizarse sola: qué versión es la última de «versiones», si es más nueva, y que se lee y se baja de kDrive (el de mentira de Atrio). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActualizadorTest {
    companion object {
        private var proc: Process? = null
        private val puerto = 8800 + (System.nanoTime() % 500).toInt().let { if (it < 0) -it else it }
        private val js = listOf("/home/user/Atrio-Suite-2.0/herramientas/fakekdrive.js", "../../Atrio-Suite-2.0/herramientas/fakekdrive.js").map { File(it) }.firstOrNull { it.exists() }

        @BeforeClass @JvmStatic fun arranca() {
            Assume.assumeTrue("falta fakekdrive.js (repo de Atrio)", js != null)
            proc = ProcessBuilder("node", js!!.path, puerto.toString()).redirectErrorStream(true).start()
            repeat(50) { try { URL("http://127.0.0.1:$puerto/").openConnection().apply { connectTimeout = 300 }.getInputStream().close(); return } catch (e: java.io.IOException) { if (e.message?.contains("Server returned") == true) return; Thread.sleep(100) } }
            fail("el kDrive de mentira no arranca")
        }
        @AfterClass @JvmStatic fun para() { proc?.destroy() }

        fun sube(ruta: String, nombre: String, datos: ByteArray) {
            val c = URL("http://127.0.0.1:$puerto/3/drive/777/upload?directory_id=2&directory_path=$ruta&file_name=$nombre&total_size=${datos.size}&conflict=version").openConnection() as HttpURLConnection
            c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Authorization", "Bearer tok")
            c.outputStream.use { it.write(datos) }
            assertEquals(200, c.responseCode)
        }
    }

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun versiones() {
        assertEquals(listOf(0, 8, 0), Actualizador.versionDe("Hiedra-0.8.0.apk"))
        assertEquals(listOf(1), Actualizador.versionDe("Hiedra-1.apk"))
        assertNull("las de Atrio no son de Pórtico", Actualizador.versionDe("Atrio-1.0.7.apk"))
        assertNull(Actualizador.versionDe("Hiedra-0.8.0-beta.apk")); assertNull(Actualizador.versionDe("notas.txt"))
        assertTrue("0.10 es más nueva que 0.9 (números, no texto)", Actualizador.mayor(listOf(0, 10), listOf(0, 9)))
        assertFalse(Actualizador.mayor(listOf(0, 8), listOf(0, 8, 0)))
        val u = Actualizador.ultima(listOf("Hiedra-0.9.0.apk" to 1L, "Hiedra-0.10.0.apk" to 2L, "Atrio-9.9.apk" to 3L, "léeme.txt" to 4L, "Hiedra-0.8.0.apk" to 5L))
        assertEquals("Hiedra-0.10.0.apk", u?.first); assertEquals(2L, u?.second)
        assertNull(Actualizador.ultima(listOf("Atrio-1.0.7.apk" to 1L)))
    }

    @Test fun sinPreguntarSegunAndroid() {
        val pm = org.robolectric.Shadows.shadowOf(ctx.packageManager)
        pm.setCanRequestPackageInstalls(true)
        assertTrue(Actualizador.puedeSilenciosa(ctx, sdk = 34, target = 34)); assertFalse(Actualizador.puedeSilenciosa(ctx, sdk = 30, target = 34))
        assertFalse(Actualizador.puedeSilenciosa(ctx, sdk = 35, target = 32))
        pm.setCanRequestPackageInstalls(false)
        assertFalse("sin el permiso de instalar apps, no", Actualizador.puedeSilenciosa(ctx, sdk = 35, target = 34))
    }

    @Test fun leeLaCarpetaVersionesYBajaLaUltima() {
        KDrive.api = "http://127.0.0.1:$puerto"
        KDrive.save(ctx, "tok", "https://ksuite.infomaniak.com/all/kdrive/app/drive/777/files/2")
        assertEquals("777", KDrive.drive(ctx)); assertTrue(KDrive.on(ctx))
        assertTrue("sin carpeta «versiones»: nada", KDrive.versiones(ctx).isEmpty())
        val a = ByteArray(3000) { (it % 7).toByte() }; val b = ByteArray(4000) { (it % 5).toByte() }
        sube("versiones", "Hiedra-0.8.0.apk", a); sube("versiones", "Hiedra-0.9.0.apk", b)
        sube("versiones", "Atrio-1.0.7.apk", a); sube("otra", "Hiedra-9.9.apk", a)
        val l = KDrive.versiones(ctx)
        assertEquals(setOf("Hiedra-0.8.0.apk", "Hiedra-0.9.0.apk", "Atrio-1.0.7.apk"), l.map { it.first }.toSet())
        val u = Actualizador.ultima(l)!!
        assertEquals("Hiedra-0.9.0.apk", u.first)
        val dest = File(ctx.cacheDir, "bajada.apk")
        KDrive.download(ctx, u.second, dest)
        assertEquals(b.toList(), dest.readBytes().toList())
        // Un token malo se dice claro
        KDrive.save(ctx, "malo", "777")
        try { KDrive.reach(ctx); fail() } catch (e: KDrive.Fail) { assertTrue(e.message!!, e.message!!.contains("token")) }
        KDrive.forget(ctx); assertFalse(KDrive.on(ctx))
    }

    @Test fun sinKDriveNoHaceNada() {
        KDrive.forget(ctx)
        Actualizador.comprueba(ctx, manual = true)
        assertTrue(Actualizador.estado, Actualizador.estado.contains("kDrive"))
        assertNull(Actualizador.nueva)
    }

    @Test fun actualizarAhora() {
        KDrive.forget(ctx)
        assertEquals("kDrive sin conectar", Actualizador.actualizarAhora(ctx))
        KDrive.save(ctx, "tok", "777"); Actualizador.ponActivo(ctx, false)
        val m = Actualizador.actualizarAhora(ctx)
        assertTrue(m, m.contains("versiones") || m.contains("internet", true) || m.contains("kDrive", true))
        assertFalse(Actualizador.estado, Actualizador.estado.contains("apagada"))
        Actualizador.ponActivo(ctx, true); KDrive.forget(ctx)
    }
}
