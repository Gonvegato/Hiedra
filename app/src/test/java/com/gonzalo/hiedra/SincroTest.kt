package com.gonzalo.hiedra

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** La cuenta y la sincronización de verdad (Firebase REST), contra el Firebase de mentira de Atrio (herramientas/fakefirebase.js). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SincroTest {
    companion object {
        private var proc: Process? = null
        private val puerto = 9000 + (System.nanoTime() % 500).toInt().let { if (it < 0) -it else it }
        private val base get() = "http://127.0.0.1:$puerto"
        private val js = listOf("../../Atrio-Suite-2.0/herramientas/fakefirebase.js", "/home/user/Atrio-Suite-2.0/herramientas/fakefirebase.js").map { File(it) }.firstOrNull { it.exists() }

        @BeforeClass @JvmStatic fun arranca() {
            org.junit.Assume.assumeTrue("falta fakefirebase.js (repo de Atrio)", js != null)
            proc = ProcessBuilder("node", js!!.path, puerto.toString()).redirectErrorStream(true).start()
            repeat(50) { runCatching { URL("$base/__dump").openStream().close(); return }; Thread.sleep(100) }
            fail("el Firebase de mentira no arranca")
        }
        @AfterClass @JvmStatic fun para() { proc?.destroy() }

        fun http(m: String, p: String, body: String? = null): String {
            val c = URL(base + p).openConnection() as HttpURLConnection
            if (m == "PATCH" || m == "PUT") { c.requestMethod = "POST"; c.setRequestProperty("X-HTTP-Method-Override", m) } else c.requestMethod = m
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toByteArray()) } }
            return (if (c.responseCode in 200..299) c.inputStream else c.errorStream).bufferedReader().readText()
        }
    }

    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    // (los dos «móviles» comparten el proceso: lo que cada uno recuerda en su almacenamiento se guarda y se repone al cambiar)
    private val memoria = HashMap<String, Map<String, Any?>>()
    private val biblios = HashMap<String, Triple<List<Juego>, Map<String, Long>, Set<String>>>()
    private var actual = ""
    private fun dispositivo(id: String) {
        val sp = ctx.getSharedPreferences("hiedra_sync", 0)
        if (actual.isNotEmpty()) { memoria[actual] = sp.all; biblios[actual] = Triple(Biblioteca.juegos.toList(), HashMap(Biblioteca.borrados), Archivos.claves()) }
        Biblioteca.juegos.clear(); Biblioteca.borrados.clear(); Archivos.limpiarParaPruebas()
        biblios[id]?.let { (j, b, a) -> Biblioteca.juegos.addAll(j); Biblioteca.borrados.putAll(b); a.forEach { Archivos.poner(it, "content://$id/$it", "") } }
        sp.edit().clear().apply()
        memoria[id]?.forEach { (k, v) -> if (v is String) sp.edit().putString(k, v).apply() else if (v is Long) sp.edit().putLong(k, v).apply() }
        actual = id
        ctx.getSharedPreferences("hiedra_cuenta", 0).edit().putString("dispositivo", id).apply()
        Cuenta.cargar(ctx); SincroP.iniciar(ctx)
    }
    private fun vacia() { Biblioteca.juegos.clear(); Biblioteca.borrados.clear(); Archivos.limpiarParaPruebas() }

    @Test fun dosHiedrasSeSincronizan() = runBlocking {
        Biblioteca.cargada = true
        Cuenta.dbUrl = base; Cuenta.authBase = base
        // Atrio ya dejó su clave web y creó la cuenta
        val sign = JSONObject(http("POST", "/v1/accounts:signUp?key=K", """{"email":"gon@ronron.example.com","password":"secreto1","returnSecureToken":true}"""))
        val uid = sign.getString("localId")
        val tok = sign.getString("idToken")
        http("PUT", "/config/webKey.json?auth=$tok", "\"K\"")

        // --- Hiedra A: entra con un usuario o contraseña malos, y luego bien
        dispositivo("hiedra-A"); vacia(); Cuenta.salir()
        try { Cuenta.entrar(ctx, "gon", "mala"); fail("debería fallar") } catch (e: Cuenta.Fail) { assertTrue(e.message!!, e.message!!.contains("incorrectos")) }
        assertEquals(uid, Cuenta.entrar(ctx, "Gon ", "secreto1"))
        assertTrue(Cuenta.vinculada)

        val zelda = Lanzador.rom("gba", "Zelda"); Archivos.poner(zelda.clave, "content://a", "")
        Biblioteca.juegos.add(Juego("j1", "Zelda", lanzadores = listOf(zelda), mod = 100, propias = Horas(4_000, 1, mapOf("2026-01-01" to 4_000L)), anadido = 10, otrasClaves = listOf("juego:zelda")))
        Biblioteca.juegos.add(Juego("j2", "PokéRogue", lanzadores = listOf(Lanzador.web("https://pokerogue.net/")), mod = 100, favorito = true, anadido = 20))
        Biblioteca.juegos.add(Juego("j3", "Hades", plataforma = "PC", mod = 100, anadido = 30, externas = mapOf(Fuentes.STEAM to Horas(9_000))))
        assertTrue(SincroP.sincronizar(ctx))
        val dump = JSONObject(http("GET", "/__dump")).getJSONObject("db").getJSONObject("users").getJSONObject(uid).getJSONObject("hiedra")
        assertTrue(dump.getJSONObject("dev").has("hiedra-A")); assertTrue(dump.getJSONObject("stamps").has("hiedra-A"))
        // El nodo ligero para Azabache: la fuente de este dispositivo y las externas, por id del juego
        val tiempo = dump.getJSONObject("tiempo")
        val tz = tiempo.getJSONObject("j1").getJSONObject("hiedra-A")
        assertEquals("Zelda", tz.getString("t")); assertEquals(4_000L, tz.getLong("total")); assertEquals("emulacion", tz.getString("m")); assertEquals(4_000L, tz.getJSONObject("days").getLong("2026-01-01"))
        assertTrue(tz.getJSONArray("k").toString().contains("juego:zelda")); assertTrue(tz.getJSONArray("k").toString().contains("rom:gba:zelda"))
        assertEquals("Steam es una fuente aparte", 9_000L, tiempo.getJSONObject("j3").getJSONObject("steam").getLong("total")); assertEquals("pc", tiempo.getJSONObject("j3").getJSONObject("steam").getString("m"))
        assertFalse("sin horas aquí no hay nodo propio", tiempo.getJSONObject("j3").has("hiedra-A"))

        // --- Hiedra B (otro móvil, biblioteca vacía): entra y recibe lo de A
        dispositivo("hiedra-B"); vacia(); Cuenta.salir()
        Cuenta.entrar(ctx, "gon", "secreto1")
        assertTrue(SincroP.sincronizar(ctx))
        assertEquals(3, Biblioteca.juegos.size)
        val z = Biblioteca.por("j1")!!
        assertTrue(z.enOtroDispositivo); assertEquals(4_000L, z.msJugados); assertEquals(4_000L, z.ajenas["hiedra-A"]!!.ms); assertEquals(listOf("juego:zelda"), z.otrasClaves)
        assertTrue(Biblioteca.por("j2")!!.favorito); assertEquals(9_000L, Biblioteca.por("j3")!!.externa(Fuentes.STEAM).ms)

        // B añade la ROM (la encuentra el escaneo) y juega 6 s; B quita PokéRogue
        // (a pelo en la lista, sin Biblioteca.poner: así no se programa una subida sola en medio de la prueba)
        Archivos.poner(zelda.clave, "content://b", "")
        Biblioteca.juegos[Biblioteca.juegos.indexOf(z)] = z.copy(propias = Horas(6_000, 1))
        Biblioteca.juegos.removeAll { it.id == "j2" }; Biblioteca.borrados["j2"] = System.currentTimeMillis()
        assertTrue(SincroP.subir(ctx, forzar = true))
        assertEquals("B sube solo sus horas como suyas", 6_000L, JSONObject(http("GET", "/__dump")).getJSONObject("db").getJSONObject("users").getJSONObject(uid).getJSONObject("hiedra").getJSONObject("tiempo").getJSONObject("j1").getJSONObject("hiedra-B").getLong("total"))

        // --- Hiedra A vuelve: ve las horas sumadas y que PokéRogue se quitó
        dispositivo("hiedra-A")
        assertTrue(SincroP.bajar(ctx))
        assertEquals(2, Biblioteca.juegos.size); assertNull(Biblioteca.por("j2"))
        val za = Biblioteca.por("j1")!!
        assertEquals(10_000L, za.msJugados); assertEquals(4_000L, za.propias.ms); assertEquals(6_000L, za.ajenas["hiedra-B"]!!.ms)
        assertFalse("A sigue teniendo su archivo", za.enOtroDispositivo)
        assertFalse("sin novedades: no baja nada", SincroP.bajar(ctx))

        // --- Carmesí dejó su biblioteca en la cuenta (otro hueco: /portico): Hiedra A la trae, de punta a punta
        val carm = JSONObject().put("v", 1).put("dev", "movil-viejo").put("borrados", JSONObject())
            .put("juegos", org.json.JSONArray()
                .put(JSONObject().put("k", "rom:gba:metroid").put("t", "Metroid Fusion").put("tp", "ROM").put("s", "gba").put("a", "Metroid").put("es", "PASADO").put("te", true).put("fi", 777L).put("an", 5L).put("mod", 100L).put("msp", 9_000L).put("vp", 3).put("dd", JSONObject().put("2026-02-02", 9_000L)).put("al", org.json.JSONArray().put("juego:metroidfusion")))
                .put(JSONObject().put("k", "rom:gba:zelda").put("t", "Zelda").put("tp", "ROM").put("s", "gba").put("a", "Zelda").put("es", "JUGANDO").put("an", 5L).put("mod", 100L).put("msp", 2_000L).put("vp", 1)))
            .put("sg", org.json.JSONArray().put(JSONObject().put("id", "sg1").put("n", "Nintendo").put("mod", 5L).put("k", org.json.JSONArray().put("rom:gba:zelda").put("rom:gba:metroid"))))
            .put("ini", JSONObject().put("mod", 3L).put("d", "rom:gba:metroid").put("f", org.json.JSONArray()).put("k", org.json.JSONArray().put("rom:gba:zelda")))
            .put("aj", JSONObject().put("at", 50L).put("skin", JSONObject().put("fondoId", "granate")))
        val tokA = tok
        http("PUT", "/users/$uid/portico/dev/movil-viejo.json?auth=$tokA", JSONObject().put("name", "Viejo").put("updated", 1L).put("data", carm.toString()).toString())
        http("PUT", "/users/$uid/portico/stamps/movil-viejo.json?auth=$tokA", "1")
        val kidMetroid = java.security.MessageDigest.getInstance("SHA-1").digest("rom:gba:metroid".toByteArray()).joinToString("") { "%02x".format(it) }.take(16)
        http("PUT", "/users/$uid/portico/nube/saves/$kidMetroid/f/abc.json?auth=$tokA", JSONObject().put("n", "Metroid.srm").put("id", 4242L).put("h", "h1").put("s", 10L).put("dev", "movil-viejo").put("t", 1L).toString())
        Inicio.cargar(ctx); Inicio.poner(ConfigInicio(), 0); Skin.cargar(ctx); Skin.fondoId = ""
        val h = ImportarCarmesi.leer()!!
        assertEquals(2, h.candidatos.size); assertEquals(1, h.sagas.size)
        val r = ImportarCarmesi.importar(ctx, h)
        assertEquals("Metroid es nuevo; Zelda ya estaba (por su clave)", 1, r.nuevos); assertEquals(1, r.yaEstaban); assertEquals(1, r.sagas); assertTrue(r.inicio); assertTrue(r.ajustes); assertEquals(1, r.nube)
        val m = Biblioteca.porClave("rom:gba:metroid")!!
        assertEquals(Estado.PASADO, m.estado); assertEquals(777L, m.finalizado); assertEquals(9_000L, m.externa(Fuentes.carmesi("movil-viejo")).ms); assertTrue("juego:metroidfusion" in m.claves)
        assertTrue("la ROM de Carmesí no está en este móvil", m.enOtroDispositivo)
        val zz = Biblioteca.por("j1")!!
        assertEquals("Zelda sigue siendo j1, con sus horas de A, de B y de Carmesí", 12_000L, zz.msJugados); assertEquals(2_000L, zz.externa(Fuentes.carmesi("movil-viejo")).ms)
        assertEquals(listOf("j1", m.id), Sagas.por("sg1")!!.juegos)
        assertEquals(m.id, Inicio.config.destacado); assertEquals(listOf("j1"), Inicio.config.fijados); assertEquals("granate", Skin.fondoId)
        assertEquals("el guardado de kDrive pasa al índice de Hiedra, por el id del juego", 4242L, NubeP.indice("saves", m.id)["abc"]!!.id)
        assertEquals("repetir no duplica nada", 0, ImportarCarmesi.importar(ctx, ImportarCarmesi.leer()!!).nuevos); assertEquals(3, Biblioteca.juegos.size)
        Skin.fondoId = ""; Inicio.poner(ConfigInicio(), 0)

        // La contraseña no se guarda
        val guardado = ctx.getSharedPreferences("hiedra_cuenta", 0).all.values.joinToString("|")
        assertFalse(guardado.contains("secreto1"))
        Cuenta.salir(); assertFalse(Cuenta.vinculada)
    }
}
