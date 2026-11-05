package com.gonzalo.hiedra

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/*
 * «Tu PC»: Atrio del PC visto desde Hiedra, con la estética de la revista. Lo que se juega ahora (en directo), la biblioteca del PC (para
 * jugar en el PC o en streaming, y para meter juegos en Hiedra), las capturas y el mando (traer Atrio, capturar, rebuscar, energía, un mensaje
 * en pantalla y los dispositivos de tu cuenta). Copiado de Atrio (SuiteScreens.kt), sin apuntes ni guías.
 */

/** "1 h 05 min" desde un momento hasta ahora. */
fun desdeHace(start: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - start) / 60_000L
    return if (m < 1) "ahora mismo" else tiempoDe(m * 60_000L)
}

/** "hace 5 min", "hace 3 h", "ayer"… */
fun hace(t: Long, now: Long = System.currentTimeMillis()): String {
    val m = (now - t) / 60_000L
    return when {
        t <= 0L -> "nunca"
        m < 1 -> "ahora mismo"; m < 60 -> "hace $m min"; m < 24 * 60 -> "hace ${m / 60} h"; m < 48 * 60 -> "ayer"
        else -> "hace ${m / (24 * 60)} días"
    }
}

fun fechaHora(t: Long): String = java.text.SimpleDateFormat("d/M HH:mm", java.util.Locale("es", "ES")).format(java.util.Date(t))

/** Punto de acento que late: algo está pasando en directo. */
@Composable
fun PuntoDirecto(size: Int = 8) {
    val t = rememberInfiniteTransition(label = "latido")
    val a by t.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
    Box(Modifier.size(size.dp).graphicsLayer { alpha = a }.background(Acento, CircleShape))
}

@Composable
private fun BotonDialogo(texto: String, acento: Boolean = false, onClick: () -> Unit) =
    TextButton(onClick) { Text(texto.uppercase(), color = if (acento) Acento else Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }

/** La portada de un juego de la biblioteca del PC (la de Atrio) o, sin ella, la de letra. */
@Composable
private fun PortadaPc(g: PcGame, modifier: Modifier) {
    val j = remember(g.key) { Juego("pc-" + g.key, g.name, portadaUrl = g.cover, lanzadores = listOf(Lanzador.pc(g.key, ""))) }
    Box(modifier.clip(esquina(4.dp))) { PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 30) }
}

/**
 * «Ahora en el PC» para la portada y para «Tu PC»: lo que se juega en directo (con el cronómetro), o el PC conectado sin jugar, o el PC apagado.
 * Sin ningún PC en la cuenta, nada (en «Tu PC» se explica cómo conectarlo).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TarjetaPc(alIr: () -> Unit, alAbrir: (Juego) -> Unit, enPortada: Boolean = true) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pc = Suite.target()
    @Suppress("UNUSED_VARIABLE") val tick = Suite.tick
    var capturando by remember { mutableStateOf(false) }
    var falta by remember { mutableStateOf(false) }
    var busySt by remember { mutableStateOf(false) }
    fun captura() {
        if (capturando) return
        capturando = true
        scope.launch { val (id, m) = Suite.shoot(pc?.dev); Lanzar.aviso(ctx, m); capturando = false; if (id != null) Suite.awaitLocal(id) }
    }
    if (pc == null) {
        val conocido = Suite.pcs().maxByOrNull { it.at } ?: return
        Column(Modifier.fillMaxWidth().clickable(onClick = alIr).padding(bottom = if (enPortada) 18.dp else 0.dp)) {
            Antetitulo("Tu PC · ${conocido.name}", color = Muted, conCuadro = false)
            Text("Desconectado" + if (conocido.at > 0) " · visto ${hace(conocido.at)}" else "", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
            if (!enPortada) Cuerpo("Enciende el PC y abre Atrio con tu cuenta: aparecerá aquí en directo.", tam = 12, modifier = Modifier.padding(top = 4.dp))
            if (enPortada) Filete(1.dp, Line, Modifier.padding(top = 10.dp))
        }
        return
    }
    val g = pc.game
    if (g == null) {
        Column(Modifier.fillMaxWidth().padding(bottom = if (enPortada) 18.dp else 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { PuntoDirecto(); Spacer(Modifier.width(8.dp)); Antetitulo("Tu PC · ${pc.name}", conCuadro = false) }
            Text("Conectado, sin jugar" + (if (pc.today > 0) " · hoy ${tiempoDe(pc.today * 60_000L)}" else ""), color = Charcoal, fontFamily = Display, fontSize = 20.sp, modifier = Modifier.padding(top = 2.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                EnlaceRevista(if (capturando) "Capturando…" else "Captura →") { captura() }
                if (enPortada) EnlaceRevista("Tu PC →", acento = true, onClick = alIr)
            }
            if (enPortada) Filete(1.dp, Line)
        }
        return
    }
    val juego = Biblioteca.porClave("pc:" + g.key)
    Column(Modifier.fillMaxWidth().padding(bottom = if (enPortada) 18.dp else 0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { PuntoDirecto(); Spacer(Modifier.width(8.dp)); Antetitulo("En directo · ${pc.name}", color = Acento, conCuadro = false) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (juego != null) Box(Modifier.width(54.dp).aspectRatio(PROPORCION_PORTADA).clip(esquina(4.dp)).clickable { alAbrir(juego) }) { PortadaArco(juego, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 26) }
            else PortadaPc(PcGame(g.key, g.name, g.cover, g.sg, false, 0, "", 0), Modifier.width(54.dp).aspectRatio(PROPORCION_PORTADA))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                FitTitle(g.name, 26.sp, 28.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, letterSpacing = (-0.5).sp)
                Text(("Jugando desde hace " + desdeHace(g.start) + (juego?.takeIf { it.msJugados >= 60_000 }?.let { " · ${tiempoDe(it.msJugados)} en total" } ?: "")).uppercase(),
                    color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            EnlaceRevista(if (capturando) "Capturando…" else "Captura →") { captura() }
            if (pc.st?.on == true) EnlaceRevista(if (busySt) "Conectando…" else "Streaming →", acento = true) {
                if (busySt) return@EnlaceRevista
                busySt = true
                scope.launch { val err = Stream.play(ctx, null); busySt = false; if (err == Stream.FALTA) falta = true else if (err != null) Lanzar.aviso(ctx, err) }
            }
            if (juego != null) EnlaceRevista("Ficha →") { alAbrir(juego) }
            if (enPortada) EnlaceRevista("Tu PC →", onClick = alIr)
        }
        if (enPortada) Filete(1.dp, Line)
    }
    if (falta) SteamLinkDialog { falta = false }
}

/** Una tira de capturas (y las que están llegando). */
@Composable
fun TiraCapturas(shots: List<Shot>, titulo: String, alAbrir: (Shot) -> Unit) {
    val pendientes = Suite.downloading
    if (shots.isEmpty() && pendientes.isEmpty()) return
    Column {
        Antetitulo(titulo, Modifier.padding(bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(pendientes.entries.toList(), key = { "d" + it.key }) { (_, f) ->
                Box(Modifier.width(150.dp).aspectRatio(16f / 9f).clip(esquina(6.dp)).background(Sand), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { f }, Modifier.size(28.dp), strokeWidth = 3.dp, color = Charcoal)
                }
            }
            items(shots, key = { it.id }) { s ->
                AsyncImage(s.uri, s.game, Modifier.width(150.dp).aspectRatio(16f / 9f).clip(esquina(6.dp)).background(Sand).clickable { alAbrir(s) }, contentScale = ContentScale.Crop)
            }
        }
    }
}

private fun uriCaptura(ctx: Context, s: Shot): Uri = androidx.core.content.FileProvider.getUriForFile(ctx, "com.gonzalo.hiedra.archivos", File(Uri.parse(s.uri).path!!))

fun compartirCaptura(ctx: Context, s: Shot) {
    runCatching {
        val i = Intent(Intent.ACTION_SEND).setType(s.mime).putExtra(Intent.EXTRA_STREAM, uriCaptura(ctx, s)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, s.game.ifBlank { "Captura" }).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

fun abrirCaptura(ctx: Context, s: Shot) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uriCaptura(ctx, s), s.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** Visor de una captura a pantalla completa, con zoom (pellizco o doble toque) y su resolución original. */
@Composable
fun VisorCaptura(shot: Shot, alCerrar: () -> Unit, alBorrar: () -> Unit) {
    val ctx = LocalContext.current
    var scale by remember { mutableStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    var chrome by remember { mutableStateOf(true) }
    var ask by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val req = remember(shot.uri) { coil.request.ImageRequest.Builder(ctx).data(shot.uri).size(coil.size.Size.ORIGINAL).crossfade(150).build() }
        AsyncImage(
            req, shot.game,
            Modifier.fillMaxSize()
                .pointerInput(Unit) { detectTransformGestures { _, pan, z, _ -> val ns = (scale * z).coerceIn(1f, 8f); off = if (ns <= 1f) Offset.Zero else off * (ns / scale) + pan; scale = ns } }
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; off = Offset.Zero } else scale = 2.5f }, onTap = { chrome = !chrome }) }
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y },
            contentScale = ContentScale.Fit
        )
        AnimatedVisibility(chrome) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color(0xAA000000), 1f to Color.Transparent)).statusBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("← VOLVER", color = Color.White, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.clickable(onClick = alCerrar).padding(vertical = 8.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(shot.game.ifBlank { "Escritorio" }, color = Color.White, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${shot.w}×${shot.h} · ${if (shot.mime == "image/jpeg") "JPG" else "PNG original"} · ${shot.bytes / 1024} KB · ${fechaHora(shot.time)}", color = Color(0xCCFFFFFF), fontFamily = Body, fontSize = 11.sp)
                    }
                }
            }
        }
        AnimatedVisibility(chrome, Modifier.align(Alignment.BottomCenter)) {
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color(0xAA000000))).navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                listOf("Compartir" to { compartirCaptura(ctx, shot) }, "Abrir" to { abrirCaptura(ctx, shot) }, "Borrar" to { ask = true }).forEach { (t, f) ->
                    Text(t.uppercase(), color = Color.White, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.clickable(onClick = f).padding(vertical = 8.dp))
                }
            }
        }
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Borrar la captura?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Se borra de Hiedra. Si se guardó en la galería, allí se queda; y en el PC sigue en Imágenes/Atrio.", color = Muted, fontFamily = Body) },
        confirmButton = { BotonDialogo("Borrar", true) { ask = false; alBorrar() } },
        dismissButton = { BotonDialogo("Cancelar") { ask = false } }
    )
}

/** Un juego de la biblioteca del PC: jugar en el PC o en streaming, y meterlo en Hiedra (o abrir su ficha si ya está). */
@Composable
fun HojaJuegoPc(dev: String, g: PcGame, alCerrar: () -> Unit, alAbrir: (Juego) -> Unit) {
    val enHiedra = Biblioteca.porClave("pc:" + g.key)
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text(g.name, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(listOfNotNull(g.src.ifBlank { null }, if (g.minutes > 0) tiempoDe(g.minutes * 60_000L) else null, if (g.lastPlayed > 0) "última vez ${hace(g.lastPlayed)}" else null).joinToString(" · ").uppercase(),
                    color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
                BotonesJugarPc(g.key) { alCerrar() }
                Spacer(Modifier.height(14.dp))
                Filete(1.dp, Line)
                if (enHiedra != null) MagLink("Ver su ficha en Hiedra", "Está en ${enHiedra.estado.nombre}") { alCerrar(); alAbrir(enHiedra) }
                else MagLink("Añadir a Hiedra", "Para llevar su estado y verlo en tus secciones") { alCerrar(); alAbrir(JuegosPc.meter(g, dev)) }
            }
        },
        confirmButton = {}, dismissButton = { BotonDialogo("Cerrar") { alCerrar() } }
    )
}

/** Mando a distancia del PC: lo que se juega, la biblioteca, las capturas y el equipo. */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PantPc(alVolver: () -> Unit, alAbrir: (Juego) -> Unit, alCaptura: (Shot) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    var capturando by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busca by rememberSaveable { mutableStateOf("") }
    var hoja by remember { mutableStateOf<Pair<String, PcGame>?>(null) }
    val pc = Suite.target()
    val dev = pc?.dev ?: Suite.pcElegido.ifBlank { Suite.libs.keys.firstOrNull() }.orEmpty()
    fun act(op: String, args: JSONObject = JSONObject()) { scope.launch { val (_, m) = Suite.send(op, args, pc?.dev, if (op == "power") 25_000 else 45_000); Lanzar.aviso(ctx, m) } }
    fun captura() {
        if (capturando) return
        capturando = true
        scope.launch { val (id, m) = Suite.shoot(pc?.dev); if (id == null) Lanzar.aviso(ctx, m) else Suite.awaitLocal(id)?.let { alCaptura(it) }; capturando = false }
    }
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        RevistaTopBar(alVolver, "Hiedra") {
            if (pc != null) Row(verticalAlignment = Alignment.CenterVertically) { PuntoDirecto(); Spacer(Modifier.width(6.dp)); Text("CONECTADO", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            else Text(Suite.state.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Filete(3.dp)
        Antetitulo("Sección", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle(pc?.name ?: Suite.pcs().firstOrNull()?.name ?: "Tu PC", 44.sp, 48.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
        if (Suite.pcs().size > 1) FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Suite.pcs().forEach { d -> Chip(d.name + if (d.online) "" else " (apagado)", (pc?.dev ?: Suite.pcElegido) == d.dev) { Suite.elegirPc(d.dev) } }
        }
        PestanasRevista(listOf("Ahora", "Biblioteca", "Capturas", "Equipo"), tab, cuentas = listOf(null, Suite.libs[dev]?.size, Suite.localShots.size.takeIf { it > 0 }, null)) { tab = it }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 14.dp)) {
                    if (Suite.pcs().isEmpty()) {
                        Text("Ningún PC conectado", color = Charcoal, fontFamily = Display, fontSize = 22.sp)
                        Cuerpo("Instala Atrio en el PC y entra con esta misma cuenta (en el PC: Ajustes → Cuenta). Al abrirlo aparecerá aquí en directo: lo que juegas, su biblioteca, las capturas y el mando.", modifier = Modifier.padding(top = 6.dp))
                    } else {
                        TarjetaPc(alIr = {}, alAbrir = alAbrir, enPortada = false)
                        Spacer(Modifier.height(18.dp))
                        Filete(1.dp, Line)
                        Spacer(Modifier.height(12.dp))
                        TiraCapturas(Suite.localShots.take(12), "Últimas capturas", alCaptura)
                        val recientes = (Suite.libs[dev] ?: emptyList()).filter { it.lastPlayed > 0 }.sortedByDescending { it.lastPlayed }.take(8)
                        if (recientes.isNotEmpty()) {
                            Spacer(Modifier.height(18.dp))
                            Antetitulo("Seguir jugando", Modifier.padding(bottom = 8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(recientes, key = { it.key }) { g ->
                                    Column(Modifier.width(96.dp).clickable { hoja = dev to g }) {
                                        PortadaPc(g, Modifier.fillMaxWidth().aspectRatio(PROPORCION_PORTADA))
                                        Text(g.name, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
                1 -> {
                    val lib = (Suite.libs[dev] ?: emptyList()).filter { busca.isBlank() || Catalogo.norm(it.name).contains(Catalogo.norm(busca)) }
                        .sortedWith(compareByDescending<PcGame> { it.fav }.thenByDescending { it.lastPlayed }.thenBy { Catalogo.norm(it.name) })
                    Column(Modifier.fillMaxSize()) {
                        CampoBusquedaRevista(busca, "Buscar en la biblioteca del PC") { busca = it }
                        if (lib.isEmpty()) Text(if (Suite.libs.isEmpty()) "Abre Atrio en el PC con tu cuenta para ver aquí su biblioteca." else "Nada con ese nombre.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 20.dp))
                        LazyVerticalGrid(GridCells.Adaptive(96.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
                            items(lib, key = { it.key }) { g ->
                                val en = Biblioteca.porClave("pc:" + g.key) != null
                                Column(Modifier.clickable { hoja = dev to g }) {
                                    PortadaPc(g, Modifier.fillMaxWidth().aspectRatio(PROPORCION_PORTADA))
                                    Text(g.name, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                    Text(listOfNotNull(if (g.minutes > 0) tiempoDe(g.minutes * 60_000L) else null, if (en) "en Hiedra" else null).joinToString(" · ").uppercase(), color = if (en) Acento else Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 2)
                                }
                            }
                        }
                    }
                }
                2 -> Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${Suite.localShots.size} capturas en este móvil".uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                        if (pc != null) EnlaceRevista(if (capturando) "Capturando…" else "Capturar →", acento = true) { captura() }
                    }
                    Cuerpo("Llegan a su resolución original (PNG, sin pérdida) y, si lo tienes activado en Ajustes, se guardan en Imágenes/Hiedra.", tam = 12)
                    if (Suite.downloading.isNotEmpty()) { Spacer(Modifier.height(8.dp)); TiraCapturas(emptyList(), "Llegando") { } }
                    LazyVerticalGrid(GridCells.Adaptive(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
                        items(Suite.localShots, key = { it.id }) { s ->
                            Column(Modifier.clickable { alCaptura(s) }) {
                                AsyncImage(s.uri, s.game, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(esquina(6.dp)).background(Sand), contentScale = ContentScale.Crop)
                                Text(s.game.ifBlank { "Escritorio" }, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                                Text(fechaHora(s.time).uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                            }
                        }
                    }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 14.dp)) {
                    if (pc == null) {
                        Text("Ningún PC conectado", color = Charcoal, fontFamily = Display, fontSize = 22.sp)
                        Cuerpo("Abre Atrio en el PC y entra con tu cuenta. En cuanto esté abierto aparecerá aquí el mando.", modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
                        if (Stream.host() != null) { Filete(2.dp); Spacer(Modifier.height(12.dp)); PanelStreaming() }
                    } else {
                        Antetitulo("Atrio", Modifier.padding(bottom = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            EnlaceRevista("Traer Atrio →") { act("focus") }
                            EnlaceRevista(if (capturando) "Capturando…" else "Captura →") { captura() }
                            EnlaceRevista("Rebuscar la biblioteca →") { act("rescan") }
                        }
                        Spacer(Modifier.height(16.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
                        PanelStreaming()
                        Spacer(Modifier.height(16.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
                        Antetitulo("Energía", Modifier.padding(bottom = 4.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            EnlaceRevista("Suspender →") { confirmar = "suspender" to "¿Suspender ${pc.name}?" }
                            EnlaceRevista("Reiniciar →") { confirmar = "reiniciar" to "¿Reiniciar ${pc.name}?" }
                            EnlaceRevista("Apagar →", acento = true) { confirmar = "apagar" to "¿Apagar ${pc.name}?" }
                        }
                        Spacer(Modifier.height(16.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
                        Antetitulo("Mensaje en la pantalla", Modifier.padding(bottom = 4.dp))
                        var msg by remember { mutableStateOf("") }
                        CampoRevista("Mensaje", msg, "sale como aviso en Atrio") { msg = it }
                        EnlaceRevista("Enviar →", acento = true) { if (msg.isNotBlank()) { act("toast", JSONObject().put("text", msg)); msg = "" } }
                        Spacer(Modifier.height(16.dp)); Filete(2.dp); Spacer(Modifier.height(12.dp))
                        Antetitulo("Dispositivos de tu cuenta", Modifier.padding(bottom = 4.dp))
                        Suite.devices.forEach { d ->
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(if (d.online) Acento else Line, CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text("${d.name} · ${if (d.app == "atrio") "PC" else "móvil"}" + if (d.online) "" else " · ${hace(d.at)}", color = Charcoal, fontFamily = Body, fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
    hoja?.let { (d, g) -> HojaJuegoPc(d, g, alCerrar = { hoja = null }, alAbrir = alAbrir) }
    confirmar?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmar = null }, containerColor = Paper, shape = esquina(4.dp),
            title = { Text(c.second, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
            text = { Text(if (pc?.game != null) "Ahora mismo se está jugando a ${pc.game.name}: guarda la partida antes." else "Atrio guarda tus horas antes de hacerlo.", color = Muted, fontFamily = Body) },
            confirmButton = { BotonDialogo(c.first, true) { confirmar = null; act("power", JSONObject().put("action", c.first)) } },
            dismissButton = { BotonDialogo("Cancelar") { confirmar = null } }
        )
    }
}
