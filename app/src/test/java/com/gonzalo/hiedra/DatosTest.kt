package com.gonzalo.hiedra

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Los datos de fuera de un juego (Steam, RAWG, IGDB) leídos de sus respuestas, y las claves de Atrio (con el kDrive). Sin red. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatosTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun laTiendaDeSteamDaEstudioEditorFechaGenerosYResumen() {
        val json = """{"1145360":{"success":true,"data":{"name":"Hades","developers":["Supergiant Games"],"publishers":["Supergiant Games"],
            "release_date":{"coming_soon":false,"date":"17 SEP 2020"},"genres":[{"id":"1","description":"Acción"},{"id":"23","description":"Indie"},{"id":"3","description":"Rol"}],
            "short_description":"Desafía al dios de los muertos &amp; <b>escapa</b> del inframundo.","metacritic":{"score":93,"url":"x"}}}}"""
        val d = Datos.deSteam(json, "1145360", ahora = 5)!!
        assertEquals("Steam", d.fuente); assertEquals("17 SEP 2020", d.salida); assertEquals(listOf("Supergiant Games"), d.desarrolladores); assertEquals(listOf("Supergiant Games"), d.editores)
        assertEquals(listOf("Acción", "Indie", "Rol"), d.generos); assertEquals("Desafía al dios de los muertos & escapa del inframundo.", d.resumen); assertEquals(93, d.metacritic); assertEquals(5L, d.at)
        assertFalse(d.vacios); assertFalse(d.nada)
        assertNull("si Steam no lo tiene, nada", Datos.deSteam("""{"99":{"success":false}}""", "99"))
        assertEquals("próximamente", Datos.deSteam("""{"7":{"success":true,"data":{"release_date":{"coming_soon":true,"date":"2027"}}}}""", "7")!!.salida)
        // Va y vuelve del archivo de este móvil
        assertEquals(d, DatosJuego.deJson(JSONObject(d.aJson().toString())))
    }

    @Test fun laBusquedaSoloValeSiElNombreCoincide() {
        val steam = """{"total":2,"items":[{"id":1145360,"name":"Hades"},{"id":1,"name":"Hades II"}]}"""
        assertEquals("1145360", Datos.appidDeBusqueda(steam, "hades")); assertEquals("1", Datos.appidDeBusqueda(steam, "Hades II")); assertNull(Datos.appidDeBusqueda(steam, "Celeste"))
        val rawg = """{"results":[{"id":3328,"name":"The Witcher 3: Wild Hunt"},{"id":5,"name":"The Witcher"}]}"""
        assertEquals(3328L, Datos.idDeRawg(rawg, "The Witcher 3 - Wild Hunt")); assertEquals(5L, Datos.idDeRawg(rawg, "The Witcher")); assertNull(Datos.idDeRawg(rawg, "Witcher 2"))
    }

    @Test fun rawgDaLosJuegosDeConsolaYSuMediaDeHoras() {
        val json = """{"id":9,"name":"The Legend of Zelda: The Minish Cap","released":"2004-11-04","developers":[{"name":"Capcom"},{"name":"Flagship"}],"publishers":[{"name":"Nintendo"}],
            "genres":[{"name":"Adventure"},{"name":"RPG"}],"description_raw":"Link se encoge.","metacritic":89,"playtime":16}"""
        val d = Datos.deRawg(json, ahora = 1)
        assertEquals("RAWG", d.fuente); assertEquals("4 nov 2004", d.salida); assertEquals(listOf("Capcom", "Flagship"), d.desarrolladores); assertEquals(listOf("Nintendo"), d.editores)
        assertEquals(listOf("Adventure", "RPG"), d.generos); assertEquals("Link se encoge.", d.resumen); assertEquals(89, d.metacritic)
        assertEquals(16 * 60, Datos.horasDeRawg(json)); assertEquals(0, Datos.horasDeRawg("{}"))
        assertEquals("2004", Datos.fechaBonita("2004")); assertEquals("", Datos.fechaBonita("")); assertEquals("1 feb 2020", Datos.fechaBonita("2020-02-01"))
    }

    @Test fun igdbDaLoQueSueleDurarEnMinutos() {
        val json = """[{"id":1,"game_id":2,"hastily":72000,"normally":90000,"completely":180000,"count":41}]"""
        val d = Datos.duracionesDeIgdb(json, "Hades")!!
        assertEquals(1500, d.historia); assertEquals(1200, d.extras); assertEquals(3000, d.completo); assertEquals("IGDB · Hades · 41 jugadores", d.de)
        assertNull(Datos.duracionesDeIgdb("[]", "x")); assertNull(Datos.duracionesDeIgdb("""[{"hastily":0,"normally":0,"completely":0}]""", "x"))
        assertEquals("sin «normal», vale «deprisa»", 600, Datos.duracionesDeIgdb("""[{"hastily":36000}]""", "x")!!.historia)
    }

    @Test fun losDatosSeGuardanYSePidenDeNuevoCuandoEnvejecen() {
        Datos.cargar(ctx)
        Datos.poner("j1", DatosJuego("Steam", "2020", at = 1_000))
        assertFalse(Datos.hacenFalta("j1", ahora = 1_000 + Datos.FRESCO - 1)); assertTrue(Datos.hacenFalta("j1", ahora = 1_000 + Datos.FRESCO + 1))
        Datos.poner("j2", DatosJuego(at = 1_000, nada = true))
        assertTrue("lo que no se encontró se reintenta antes", Datos.hacenFalta("j2", ahora = 1_000 + Datos.FALLIDO + 1)); assertFalse(Datos.hacenFalta("j2", ahora = 1_000 + Datos.FALLIDO - 1))
        assertTrue(Datos.hacenFalta("nadie"))
        Datos.olvidar("j1"); Datos.olvidar("j2")
    }

    @Test fun lasClavesDeAtrioTraenRawgEIgdbYLasTuyasMandan() {
        val b = JSONObject().put("keys", JSONObject().put("sgdb", "abc").put("steamKey", "k1").put("steamUser", "gon").put("rawg", "r1").put("igdbId", "id1").put("igdbSecret", "s1").put("kdToken", "tok"))
        val k = Servicios.deBlobs(listOf(JSONObject().put("keys", JSONObject().put("rawg", "")), b))
        assertEquals("r1", k.rawg); assertEquals("id1", k.igdbId); assertEquals("s1", k.igdbSecret)
        KDrive.forget(ctx); Servicios.poner(ctx, Servicios.Claves(steamKey = "mia"))
        Servicios.aplicarDeAtrio(ctx, k)
        assertFalse("el kDrive no viene de la cuenta (tu pareja puede leerla)", KDrive.on(ctx))
        val usadas = Servicios.claves(ctx)
        assertEquals("la tuya manda", "mia", usadas.steamKey); assertEquals("y donde no hay, la de Atrio", "abc", usadas.sgdb); assertEquals("r1", usadas.rawg)
        assertTrue(Servicios.ultimaAtrio(ctx) > 0)
        Servicios.poner(ctx, Servicios.Claves())
    }
}
