package com.gonzalo.hiedra

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.view.InputDevice
import android.view.KeyEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.swordfish.libretrodroid.ViewportAlignment
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// La pantalla de juego va en oscuro siempre (el juego manda): tinta clara sobre carbón, y el acento de Hiedra para lo que se toca.
private val Carbon = Color(0xFF121110)
private val FondoMenu = Color(0xF2141210)
private val Tinta = Color(0xFFF1ECE3)
private val Gris = Color(0xFFA39C90)
private val Raya = Color(0x33F1ECE3)

/** La pantalla del jugador interno (lo que dibuja [JuegoActivity]). */
@Composable
fun PantJuego(a: JuegoActivity) {
    Box(Modifier.fillMaxSize().background(Carbon)) {
        when (val f = a.fase) {
            is FaseJuego.Preparando -> Aviso(a.juego?.titulo.orEmpty(), f.texto, f.fraccion)
            is FaseJuego.Pregunta -> Aviso(a.juego?.titulo.orEmpty(), f.texto, null) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 20.dp)) {
                    BotonOscuro(f.si, acento = true, foco = true) { f.respuesta.complete(true) }
                    BotonOscuro(f.no) { f.respuesta.complete(false) }
                }
            }
            is FaseJuego.Error -> Aviso(a.juego?.titulo.orEmpty(), f.texto, null) {
                Row(Modifier.padding(top = 20.dp)) { BotonOscuro("Volver a Hiedra", acento = true, foco = true) { a.finish() } }
            }
            FaseJuego.Jugando -> Jugando(a)
        }
    }
}

@Composable
private fun Aviso(titulo: String, texto: String, fraccion: Float?, botones: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("HIEDRA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(titulo, color = Tinta, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 34.sp, modifier = Modifier.padding(top = 6.dp))
        Text(texto, color = Gris, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 12.dp).widthIn(max = 520.dp))
        if (fraccion != null) LinearProgressIndicator(progress = { fraccion.coerceIn(0f, 1f) }, color = Acento, trackColor = Raya, modifier = Modifier.padding(top = 16.dp).widthIn(max = 320.dp).fillMaxWidth())
        botones()
    }
}

@Composable
private fun Jugando(a: JuegoActivity) {
    val conf = LocalConfiguration.current
    val vertical = conf.orientation == Configuration.ORIENTATION_PORTRAIT
    val vista = a.vista
    if (vista == null && !JuegoActivity.sinMotor) return
    // En vertical, el juego arriba y los mandos debajo
    LaunchedEffect(vertical) { vista?.viewportAlignment = if (vertical) ViewportAlignment.TOP else ViewportAlignment.CENTER }
    val tactil = when (AjustesJuego.tactil) {
        AjustesJuego.Tactil.AUTO -> !a.mandoConectado || a.tactilForzado
        AjustesJuego.Tactil.SIEMPRE -> true
        AjustesJuego.Tactil.NUNCA -> false
    }
    val botonesPequenos = tactil || AjustesJuego.botonesPequenosConMando
    Box(Modifier.fillMaxSize()) {
        // La vista del juego: siempre en el mismo sitio (ver JuegoActivity)
        if (vista != null) AndroidView(factory = { vista }, modifier = Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(Color(0xFF3A5A40)))
        // Con el mando conectado y el táctil oculto, tocar la pantalla lo vuelve a enseñar
        if (!tactil && AjustesJuego.tactil == AjustesJuego.Tactil.AUTO)
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { a.tactilForzado = true } })
        if (tactil) {
            val lado = (if (vertical) (conf.screenWidthDp * 0.5f).dp else minOf(conf.screenHeightDp * 0.62f, 270f).dp) * AjustesJuego.tamano
            val abajo = if (vertical) 28.dp else 8.dp
            key(AjustesJuego.opacidad, AjustesJuego.vibracion, AjustesJuego.tamano) {
                AndroidView(factory = { a.crearPad(MandoTactil.Lado.IZQUIERDO) }, modifier = Modifier.align(Alignment.BottomStart).padding(bottom = abajo).size(lado))
                AndroidView(factory = { a.crearPad(MandoTactil.Lado.DERECHO) }, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = abajo).size(lado))
            }
        }
        if (botonesPequenos && !a.menu) {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Pildora("MENÚ") { a.abrirMenu() }
                PildoraAvance(a)
            }
        }
        a.indicador?.let { t ->
            Text(t, color = Tinta, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 13.sp, letterSpacing = 1.2.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp).background(Color(0x99000000), RoundedPill).padding(horizontal = 12.dp, vertical = 5.dp))
        }
        a.aviso?.let { t ->
            LaunchedEffect(t) { delay(3_500); if (a.aviso == t) a.aviso = null }
            Text(t, color = Tinta, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (tactil) 10.dp else 24.dp).background(Color(0xCC000000), RoundedPill).padding(horizontal = 14.dp, vertical = 7.dp))
        }
        if (a.menu) MenuJuego(a)
    }
}

private val RoundedPill = androidx.compose.foundation.shape.RoundedCornerShape(50)

@Composable
private fun Pildora(texto: String, onClick: () -> Unit) {
    Text(texto, color = Tinta, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.6.sp,
        modifier = Modifier.background(Color(0x66000000), RoundedPill).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
}

/** El botón de avance rápido: en «mientras lo pulsas» acelera mientras el dedo está encima; en «conmutar», cada toque. */
@Composable
private fun PildoraAvance(a: JuegoActivity) {
    val on = a.indicador != null
    Text("▶▶", color = if (on) Carbon else Tinta, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 14.sp,
        modifier = Modifier.background(if (on) Acento else Color(0x66000000), RoundedPill)
            .pointerInput(Unit) { detectTapGestures(onPress = { a.pulsarAvance(true); tryAwaitRelease(); a.pulsarAvance(false) }) }
            .padding(horizontal = 16.dp, vertical = 7.dp))
}

// ------------------------------------------------------------------ el menú en pausa

@Composable
private fun MenuJuego(a: JuegoActivity) {
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(FondoMenu).pointerInput(Unit) { detectTapGestures { } }) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 22.dp)) {
            Text(if (a.pantallaMenu == "principal") "EN PAUSA" else "← EN PAUSA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 2.sp,
                modifier = Modifier.clickable { if (a.pantallaMenu == "principal") a.cerrarMenu() else a.pantallaMenu = "principal" }.padding(vertical = 4.dp))
            Text(a.juego?.titulo.orEmpty(), color = Tinta, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 33.sp, maxLines = 2)
            Box(Modifier.padding(top = 8.dp, bottom = 4.dp).fillMaxWidth().height(2.dp).background(Tinta))
            when (a.pantallaMenu) {
                "guardar" -> MenuHuecos(a, guardar = true)
                "cargar" -> MenuHuecos(a, guardar = false)
                "avance" -> MenuAvance(a)
                "mando" -> MenuMando(a)
                "mapeo" -> MenuMapeo(a)
                "diagnostico" -> MenuDiagnostico(a)
                "imagen" -> MenuImagen(a)
                else -> MenuPrincipal(a)
            }
            a.aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp)) }
        }
    }
}

@Composable
private fun MenuPrincipal(a: JuegoActivity) {
    var reiniciar by remember { mutableStateOf(false) }
    FilaOscura("Continuar", null, foco = true) { a.cerrarMenu() }
    FilaOscura("Guardar estado", "Tres huecos, además del automático al salir") { a.pantallaMenu = "guardar" }
    FilaOscura("Cargar estado", "El automático o uno de tus huecos") { a.pantallaMenu = "cargar" }
    FilaOscura("Avance rápido", "×${AjustesJuego.avanceVelocidad} · ${AjustesJuego.avanceModo.nombre.lowercase()}" + if (AjustesJuego.avanceSilencio) " · sin sonido" else "") { a.pantallaMenu = "avance" }
    FilaOscura("Mando y controles", (a.ultimoMando?.name ?: if (a.mandoConectado) "Mando conectado" else "Sin mando: controles táctiles")) { a.pantallaMenu = "mando" }
    FilaOscura("Imagen y guardado", "${AjustesJuego.filtro.nombre} · ${AjustesJuego.orientacion.nombre.lowercase()}") { a.pantallaMenu = "imagen" }
    FilaOscura("Captura", "A Imágenes/Hiedra") { a.guardarCaptura() }
    FilaOscura(if (reiniciar) "Pulsa otra vez para reiniciar" else "Reiniciar", "Vuelve al principio (la partida del cartucho no se borra)") {
        if (reiniciar) { a.motor?.reiniciar(); a.cerrarMenu() } else reiniciar = true
    }
    FilaOscura("Salir", "Se guarda donde estás y se sube a tu nube") { a.salir() }
}

@Composable
private fun MenuHuecos(a: JuegoActivity, guardar: Boolean) {
    val ctx = LocalContext.current
    val j = a.juego ?: return
    val v = a.versionEstados
    val huecos = if (guardar) Estados.huecos.filter { it != Estados.AUTO } else Estados.huecos
    huecos.forEachIndexed { i, h ->
        val m = remember(v, h) { Estados.meta(ctx, j.id, h) }
        val mini = remember(v, h) { Estados.miniatura(ctx, j.id, h).takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }.getOrNull() } }
        val nombre = if (h == Estados.AUTO) "Automático" else "Hueco $h"
        val sub = m?.let { (if (it.dev == Cuenta.dispositivo || it.dn.isBlank()) "" else "de ${it.dn} · ") + SimpleDateFormat("d MMM · HH:mm", Locale("es", "ES")).format(Date(it.t)) } ?: "vacío"
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (mini != null) Image(mini, null, contentScale = ContentScale.Crop, modifier = Modifier.padding(end = 12.dp).size(72.dp, 48.dp))
            Column(Modifier.weight(1f)) {
                FilaOscura(nombre, sub, foco = i == 0, activa = guardar || m != null) { if (guardar) a.guardarEnHueco(h) else if (m != null) a.cargarHueco(h) }
            }
        }
    }
    Text(if (guardar) "Al salir del juego se guarda solo en «Automático», y con eso sigues en otro móvil." else "Un estado solo carga con la misma versión del núcleo que lo hizo.",
        color = Gris, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun MenuAvance(a: JuegoActivity) {
    val ctx = LocalContext.current
    fun guarda() { AjustesJuego.guardar(ctx); a.aplicarAjustesEntrada() }
    Etiqueta2("Velocidad")
    Opciones(AvanceRapido.velocidades.map { if (it == 8) "×8 (lo que pueda)" else "×$it" }, AvanceRapido.velocidades.indexOf(AjustesJuego.avanceVelocidad), foco = true) { AjustesJuego.avanceVelocidad = AvanceRapido.velocidades[it]; guarda() }
    Etiqueta2("Cómo se activa")
    Opciones(AvanceRapido.Modo.entries.map { it.nombre }, AjustesJuego.avanceModo.ordinal) { AjustesJuego.avanceModo = AvanceRapido.Modo.entries[it]; guarda() }
    Etiqueta2("Sonido mientras acelera")
    Opciones(listOf("Silenciado", "Acelerado"), if (AjustesJuego.avanceSilencio) 0 else 1) { AjustesJuego.avanceSilencio = it == 0; guarda() }
    Text("En el mando: R2 (o el gatillo derecho) acelera; en la pantalla, el botón ▶▶. Se puede cambiar en «Mando y controles».",
        color = Gris, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun MenuMando(a: JuegoActivity) {
    val ctx = LocalContext.current
    fun guarda() { AjustesJuego.guardar(ctx); a.aplicarAjustesEntrada() }
    Etiqueta2("Controles táctiles")
    Opciones(AjustesJuego.Tactil.entries.map { it.nombre }, AjustesJuego.tactil.ordinal, foco = true) { AjustesJuego.tactil = AjustesJuego.Tactil.entries[it]; guarda() }
    Etiqueta2("Opacidad")
    val opacidades = listOf(0.3f, 0.45f, 0.6f, 0.8f, 1f)
    Opciones(opacidades.map { "${(it * 100).toInt()} %" }, opacidades.indexOfFirst { kotlin.math.abs(it - AjustesJuego.opacidad) < 0.01f }) { AjustesJuego.opacidad = opacidades[it]; guarda() }
    Etiqueta2("Tamaño")
    val tamanos = listOf(0.8f, 1f, 1.2f, 1.4f)
    Opciones(tamanos.map { "${(it * 100).toInt()} %" }, tamanos.indexOfFirst { kotlin.math.abs(it - AjustesJuego.tamano) < 0.01f }) { AjustesJuego.tamano = tamanos[it]; guarda() }
    FilaInterruptor("Vibrar al pulsar", AjustesJuego.vibracion) { AjustesJuego.vibracion = it; guarda() }
    FilaInterruptor("Menú y ▶▶ visibles con mando", AjustesJuego.botonesPequenosConMando) { AjustesJuego.botonesPequenosConMando = it; guarda() }
    FilaInterruptor("Botones como en el mando", AjustesJuego.porEtiqueta, "Apagado: el botón de la derecha es A (como en la consola). Encendido: va por la letra pintada.") { AjustesJuego.porEtiqueta = it; guarda() }
    Etiqueta2("Zona muerta del stick")
    val zonas = listOf(0.2f, 0.35f, 0.5f)
    Opciones(zonas.map { "${(it * 100).toInt()} %" }, zonas.indexOfFirst { kotlin.math.abs(it - AjustesJuego.zonaMuerta) < 0.01f }) { AjustesJuego.zonaMuerta = zonas[it]; guarda() }
    FilaOscura("Cambiar botones del mando", a.ultimoMando?.name ?: "Pulsa un botón del mando para elegirlo") { a.pantallaMenu = "mapeo" }
    FilaOscura("Diagnóstico de mando", "Qué mandos ve el móvil y qué manda cada botón") { a.pantallaMenu = "diagnostico" }
}

@Composable
private fun MenuMapeo(a: JuegoActivity) {
    val ctx = LocalContext.current
    val d = a.ultimoMando
    if (d == null) {
        Text("Pulsa cualquier botón del mando que quieras configurar.", color = Gris, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 12.dp))
        return
    }
    val sistema = a.juego?.sistema ?: "gba"
    var version by remember { mutableIntStateOf(0) }
    val mapa = remember(d.descriptor, version, a.esperandoMapeo) { AjustesJuego.mapa(ctx, d.descriptor) }
    Text(d.name, color = Tinta, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, modifier = Modifier.padding(top = 10.dp))
    a.esperandoMapeo?.let { Text("Pulsa en el mando el botón para «${it.nombre}»…", color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(vertical = 8.dp)) }
    val dis = Mandos.disposicion(sistema)
    val destinos: List<Mandos.Destino> = Mandos.Boton.entries.filter { it in dis.botones || it.name in setOf("ARRIBA", "ABAJO", "IZQUIERDA", "DERECHA") }.map { Mandos.Destino.Pad(it) } +
        Mandos.Accion.entries.map { Mandos.Destino.Hacer(it) }
    destinos.forEachIndexed { i, dst ->
        val teclas = Mandos.teclasDeMando.filter { Mandos.efectivo(it, mapa, sistema, AjustesJuego.porEtiqueta) == dst }
        FilaOscura(dst.nombre, teclas.joinToString(" · ") { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_").removePrefix("BUTTON_") }.ifBlank { "sin botón" }, foco = i == 0,
            activa = true) { a.esperandoMapeo = dst }
    }
    FilaOscura("Restablecer este mando", "Vuelve a lo de por defecto") { AjustesJuego.ponMapa(ctx, d.descriptor, emptyMap()); a.aplicarAjustesEntrada(); version++ }
}

@Composable
private fun MenuDiagnostico(a: JuegoActivity) {
    val ctx = LocalContext.current
    val dispositivos = remember(a.mandoConectado, a.ultimoMando) {
        InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.filter { !it.isVirtual && (it.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || it.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK || it.sources and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) }
    }
    val texto = buildString {
        appendLine("Hiedra $VERSION · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}")
        if (dispositivos.isEmpty()) appendLine("No se ve ningún mando.")
        dispositivos.forEach { d ->
            val fuentes = listOfNotNull("mando".takeIf { d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD }, "joystick".takeIf { d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK },
                "cruceta".takeIf { d.sources and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD }, "teclado".takeIf { d.sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD })
            appendLine("• ${d.name} · ${String.format("%04x:%04x", d.vendorId, d.productId)} · ${fuentes.joinToString()} · ${if (d.isExternal) "externo" else "interno"} · ${d.descriptor.take(10)}")
        }
        appendLine(); appendLine("Últimos eventos:")
        a.registro.forEach { appendLine(it) }
    }
    Text(texto, color = Tinta, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 10.dp))
    FilaOscura("Copiar", "Para pegármelo si algo no va bien", foco = true) {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Diagnóstico de mando", texto))
        a.aviso = "Copiado"
    }
}

@Composable
private fun MenuImagen(a: JuegoActivity) {
    val ctx = LocalContext.current
    Etiqueta2("Filtro de imagen")
    Opciones(AjustesJuego.Filtro.entries.map { it.nombre }, AjustesJuego.filtro.ordinal, foco = true) {
        AjustesJuego.filtro = AjustesJuego.Filtro.entries[it]; AjustesJuego.guardar(ctx); a.vista?.shader = a.shaderDe(AjustesJuego.filtro)
    }
    Etiqueta2("Pantalla")
    Opciones(AjustesJuego.Orientacion.entries.map { it.nombre }, AjustesJuego.orientacion.ordinal) { AjustesJuego.orientacion = AjustesJuego.Orientacion.entries[it]; AjustesJuego.guardar(ctx); a.aplicarOrientacion() }
    Etiqueta2("Guardado automático mientras juegas")
    val mins = listOf(0, 1, 2, 5)
    Opciones(mins.map { if (it == 0) "Solo al salir" else "Cada $it min" }, mins.indexOf(AjustesJuego.autoguardadoMin).coerceAtLeast(0)) { AjustesJuego.autoguardadoMin = mins[it]; AjustesJuego.guardar(ctx) }
}

// ------------------------------------------------------------------ piezas

@Composable
private fun Etiqueta2(t: String) {
    Text(t.uppercase(), color = Gris, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

/** Una fila del menú: se elige con el dedo o con el mando (al tener el foco se marca en el acento). */
@Composable
private fun FilaOscura(titulo: String, sub: String?, foco: Boolean = false, activa: Boolean = true, onClick: () -> Unit) {
    val fr = remember { FocusRequester() }
    var enfocada by remember { mutableStateOf(false) }
    if (foco) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Column(Modifier.fillMaxWidth().focusRequester(fr).onFocusChanged { enfocada = it.isFocused }.clickable(enabled = activa, onClick = onClick)
        .background(if (enfocada) Color(0x22F1ECE3) else Color.Transparent).padding(vertical = 11.dp, horizontal = 4.dp)) {
        Text(titulo, color = if (!activa) Gris else if (enfocada) Acento else Tinta, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        if (!sub.isNullOrBlank()) Text(sub.uppercase(), color = Gris, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, lineHeight = 14.sp)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(Raya))
}

@Composable
private fun FilaInterruptor(titulo: String, on: Boolean, sub: String? = null, alCambiar: (Boolean) -> Unit) {
    FilaOscura(titulo + if (on) " · sí" else " · no", sub) { alCambiar(!on) }
}

@Composable
private fun Opciones(opciones: List<String>, sel: Int, foco: Boolean = false, alElegir: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        opciones.forEachIndexed { i, o ->
            val fr = remember { FocusRequester() }
            var enfocada by remember { mutableStateOf(false) }
            if (foco && i == sel.coerceAtLeast(0)) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
            val on = i == sel
            Text(o.uppercase(), color = if (on) Carbon else if (enfocada) Acento else Tinta, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp,
                modifier = Modifier.focusRequester(fr).onFocusChanged { enfocada = it.isFocused }.clickable { alElegir(i) }
                    .background(if (on) Acento else Color.Transparent, RoundedPill).border(1.dp, if (enfocada) Acento else Raya, RoundedPill).padding(horizontal = 14.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun BotonOscuro(t: String, acento: Boolean = false, foco: Boolean = false, onClick: () -> Unit) {
    val fr = remember { FocusRequester() }
    if (foco) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Text(t.uppercase(), color = if (acento) Carbon else Tinta, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp,
        modifier = Modifier.focusRequester(fr).clickable(onClick = onClick).background(if (acento) Acento else Color.Transparent, RoundedPill).border(1.dp, if (acento) Acento else Raya, RoundedPill).padding(horizontal = 18.dp, vertical = 10.dp))
}
