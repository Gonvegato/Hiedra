package com.gonzalo.hiedra

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

/** Arranca los datos de la app (una vez). */
fun iniciar(c: Context) {
    Catalogo.cargar(c); Skin.cargar(c); Ajustes.cargar(c); NubeP.cargar(c); Biblioteca.cargar(c); Sagas.cargar(c); Inicio.cargar(c)
    TemaAtrio.cargarGuardado(c)
    Cuenta.cargar(c); SincroP.iniciar(c)
}

/** Dónde estás: la portada y sus secciones (se llega a ellas desde el menú de la portada, como en Atrio). Sistemas se abre desde Ajustes. */
enum class Destino { Portada, Jugando, Pendiente, Pasado, Abandonado, Sagas, Sistemas, Ajustes, PC }

/** La app: portada de revista y sus secciones, la ficha de un juego o una saga por encima y la hoja de «añadir». */
@Composable
fun HiedraApp(fichaPedida: String? = null, jugarPedido: Boolean = false, alAbrirPedida: () -> Unit = {}) {
    val ctx = LocalContext.current
    var destino by rememberSaveable { mutableIntStateOf(0) }
    var buscar by rememberSaveable { mutableStateOf(false) }
    var fichaId by rememberSaveable { mutableStateOf<String?>(null) }
    var sagaId by rememberSaveable { mutableStateOf<String?>(null) }
    var anadir by rememberSaveable { mutableStateOf(false) }
    var tipoInicial by remember { mutableStateOf<TipoLanzador?>(null) }
    /** La ficha sin lanzador a la que se le va a vincular lo que se añada ahora. */
    var vincularA by remember { mutableStateOf<String?>(null) }
    /** Un juego del PC al que se va a jugar: se elige si en el PC o en streaming. */
    var jugarPc by remember { mutableStateOf<Juego?>(null) }
    /** Un juego cuya ROM está en otro móvil: se baja de tu kDrive antes de jugar. */
    var bajarRom by remember { mutableStateOf<Juego?>(null) }
    /** Una captura abierta a pantalla completa. */
    var captura by remember { mutableStateOf<Shot?>(null) }
    val d = Destino.entries[destino]
    // Un widget pide abrir la ficha de un juego
    LaunchedEffect(fichaPedida) {
        val j = fichaPedida?.let { Biblioteca.por(it) }
        if (j != null) { if (jugarPedido && j.enPc) jugarPc = j else { fichaId = fichaPedida; destino = 0; if (jugarPedido && j.enOtroDispositivo) bajarRom = j } }
        if (fichaPedida != null) alAbrirPedida()
    }
    // Una captura recién llegada del PC se abre
    LaunchedEffect(Suite.arrived) { Suite.arrived?.let { captura = it; Suite.arrived = null } }
    LaunchedEffect(Suite.notice) { Suite.notice?.let { Lanzar.aviso(ctx, it); Suite.notice = null } }

    fun jugar(j: Juego) {
        when {
            j.enPc -> jugarPc = j
            // La ROM está en otro de tus móviles: se baja de tu kDrive y se juega (el jugador de Hiedra la baja él solo)
            j.enOtroDispositivo && !Lanzar.esInterno(Lanzar.elegir(ctx, j)) -> bajarRom = j
            else -> Lanzar.lanzar(ctx, j)?.let { Lanzar.aviso(ctx, it) }
        }
    }
    fun abrirAnadir(t: TipoLanzador?, para: String? = null) { tipoInicial = t; vincularA = para; anadir = true }
    fun ir(x: Destino, buscando: Boolean = false) { buscar = buscando; destino = x.ordinal }

    BackHandler(enabled = fichaId != null) { fichaId = null }
    BackHandler(enabled = sagaId != null && fichaId == null) { sagaId = null }
    AvisoActualizacion()
    BackHandler(enabled = fichaId == null && sagaId == null && d != Destino.Portada) { destino = 0 }

    Box(Modifier.fillMaxSize().background(Paper)) {
        when (d) {
            Destino.Portada -> PantInicio(
                alAbrir = { fichaId = it.id }, alJugar = ::jugar, alAnadir = { abrirAnadir(it) },
                alIr = { ir(it) }, alBuscar = { ir(Destino.Jugando, true) }
            )
            Destino.Sistemas -> PantSistemas(alVolver = { destino = Destino.Ajustes.ordinal }, alAnadirCarpeta = { abrirAnadir(null) })
            Destino.Ajustes -> PantEstilo(alVolver = { destino = 0 }, alSistemas = { destino = Destino.Sistemas.ordinal })
            Destino.PC -> PantPc(alVolver = { destino = 0 }, alAbrir = { fichaId = it.id }, alCaptura = { captura = it })
            else -> PantSecciones(d, alVolver = { destino = 0 }, alAbrir = { fichaId = it.id }, alJugar = ::jugar, alAbrirSaga = { sagaId = it.id }, alAnadir = { abrirAnadir(null) }, buscandoInicial = buscar)
        }
        AnimatedVisibility(sagaId != null, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            sagaId?.let { id -> PantSaga(id, alCerrar = { sagaId = null }, alAbrir = { fichaId = it.id }, alJugar = ::jugar) }
        }
        AnimatedVisibility(fichaId != null, enter = slideInVertically { it / 8 } + fadeIn(), exit = slideOutVertically { it / 8 } + fadeOut()) {
            fichaId?.let { id -> Ficha(id, alCerrar = { fichaId = null }, alJugar = ::jugar, alVincular = { abrirAnadir(null, it.id) }, alIrA = { fichaId = it }) }
        }
        AnimatedVisibility(captura != null, enter = fadeIn(), exit = fadeOut()) {
            captura?.let { s -> VisorCaptura(s, alCerrar = { captura = null }, alBorrar = { Suite.deleteLocal(s); captura = null }) }
        }
    }
    BackHandler(enabled = captura != null) { captura = null }
    jugarPc?.let { j -> DialogoJugarPc(j) { jugarPc = null } }
    bajarRom?.let { j -> DialogoBajarRom(j, jugar = true) { bajarRom = null } }
    if (anadir) HojaAnadir(tipoInicial, vincularA = vincularA, alVincular = { fichaId = it.id }) { anadir = false; vincularA = null }
}
