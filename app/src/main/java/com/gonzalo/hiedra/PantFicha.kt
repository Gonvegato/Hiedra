package com.gonzalo.hiedra

import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.LinearGradient
import android.graphics.Shader
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Copia la imagen elegida a la carpeta de la app (reducida) y devuelve su ruta: así no depende de permisos. */
suspend fun guardarPortada(c: Context, id: String, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1; while (o.outWidth / s > 1000 || o.outHeight / s > 1400) s *= 2
        val b = c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) } ?: return@runCatching null
        val dir = File(c.filesDir, "portadas").apply { mkdirs() }
        val f = File(dir, "$id-${System.currentTimeMillis()}.jpg")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        f.absolutePath
    }.getOrNull()
}

/** Igual, pero con los bytes de una imagen ya bajada de internet. null = no es una imagen. */
suspend fun guardarPortadaBytes(c: Context, id: String, bytes: ByteArray, original: Boolean = false): String? = withContext(Dispatchers.IO) {
    runCatching {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return@runCatching null
        // Original: los mismos bytes que bajó, sin reducir ni recomprimir (máxima calidad; lo usa la pantalla de inicio)
        if (original) {
            val f = File(File(c.filesDir, "portadas").apply { mkdirs() }, "$id-${System.currentTimeMillis()}.img")
            f.writeBytes(bytes)
            return@runCatching f.absolutePath
        }
        var s = 1; while (o.outWidth / s > 2000 || o.outHeight / s > 2800) s *= 2
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return@runCatching null
        val f = File(File(c.filesDir, "portadas").apply { mkdirs() }, "$id-${System.currentTimeMillis()}.jpg")
        f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        f.absolutePath
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun Ficha(id: String, alCerrar: () -> Unit, alJugar: (Juego) -> Unit, abrirPortada: Boolean = false, alVerPortada: () -> Unit = {}, alVincular: (Juego) -> Unit = {}, alIrA: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val j = Biblioteca.por(id)
    if (j == null) { LaunchedEffect(Unit) { alCerrar() }; return }
    var editar by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    var selector by remember { mutableStateOf(false) }
    var editarWeb by remember { mutableStateOf(false) }
    var eligePortada by remember { mutableStateOf(abrirPortada) }
    var editarHoras by remember { mutableStateOf(false) }
    var buscaInternet by remember { mutableStateOf(false) }
    var saga by remember { mutableStateOf(false) }
    var sgdb by remember { mutableStateOf(false) }
    var encuadre by remember { mutableStateOf(false) }
    // La valoración de Azabache, solo para verla (se refresca de vez en cuando)
    var notas by remember(NotaAzabache.version) { mutableStateOf(NotaAzabache.cargar(ctx)) }
    LaunchedEffect(Unit) { if (Cuenta.vinculada && NotaAzabache.vieja(ctx)) withContext(Dispatchers.IO) { if (NotaAzabache.refrescar(ctx)) notas = NotaAzabache.cargar(ctx) } }
    LaunchedEffect(abrirPortada) { if (abrirPortada) alVerPortada() }
    // Los datos de fuera (fecha, estudio, géneros, lo que suele durar): se piden si faltan o son viejos
    var buscandoDatos by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { Datos.cargar(ctx); if (Datos.activo && Datos.hacenFalta(id)) { buscandoDatos = true; runCatching { Datos.pedir(ctx, j) }; buscandoDatos = false } }
    val cambiarPortada = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) scope.launch { guardarPortada(ctx, j.id, u)?.let { Biblioteca.poner(Biblioteca.por(j.id)!!.copy(portada = it, zoom = 1f, ox = 0f, oy = 0f)) } }
    }
    val sistema = Catalogo.sistema(j.sistema)
    val miSaga = Sagas.de(j)

    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp)) {
        // Arriba: favorito y editar (el nombre, la plataforma, las horas, quitar)
        RevistaTopBar(alCerrar, "Volver") {
            EnlaceRevista(if (j.favorito) "★ Favorito" else "☆ Favorito", acento = j.favorito) { Biblioteca.poner(j.copy(favorito = !j.favorito)) }
            EnlaceRevista("Editar") { editar = true }
        }
        Filete(3.dp)
        // El estado, como las pestañas de estado de Atrio, pegado a la barra de arriba
        FiltrosRevista(Estado.entries.map { it.nombre }, j.estado.ordinal, Modifier.padding(top = 4.dp)) { i -> Biblioteca.cambiar(j.id) { it.conEstado(Estado.entries[i]) } }
        Filete(1.dp)
        Spacer(Modifier.height(14.dp))
        RevistaHeadline(j.titulo, if (j.sinLanzador) j.plataforma.ifBlank { "Sin lanzador en el móvil" } else j.lanzadores.joinToString(" · ") { l -> when (l.tipo) {
            TipoLanzador.ROM -> Catalogo.sistema(l.sistema)?.let { s -> listOf(s.corto, s.nombre).distinct().joinToString(" · ") } ?: l.sistema; TipoLanzador.APP -> "Android"; TipoLanzador.WEB -> "Navegador"; TipoLanzador.PC -> "PC" } })
        val deck = listOfNotNull(
            if (j.estado == Estado.PASADO) (if (j.finalizado > 0) "pasado el " + fechaCorta(j.finalizado) else "pasado") else if (j.estado != Estado.PENDIENTE) j.estado.nombre.lowercase() else null,
            if (j.ultima > 0) "jugado por última vez " + haceCuanto(j.ultima).lowercase() else null
        ).joinToString(" · ")
        if (deck.isNotBlank()) Text(deck, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(14.dp))

        // La portada (un toque la cambia) y tus horas (como la ficha de Marfil: la portada y por dónde vas)
        Row(Modifier.fillMaxWidth().height(225.dp)) {
            Box(Modifier.width(150.dp).height(225.dp).clip(esquina(3.dp)).border(1.dp, Line, esquina(3.dp)).clickable { eligePortada = true }.testTag("portada")) {
                PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 64)
            }
            Column(Modifier.weight(1f).padding(start = 18.dp).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                val avance = j.avance
                if (avance != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${(avance * 100).toInt()}", color = Acento, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 62.sp)
                        Text(" %", color = Muted, fontFamily = Body, fontSize = 14.sp, modifier = Modifier.padding(bottom = 10.dp))
                        Spacer(Modifier.weight(1f))
                        Text("${tiempoDe(j.msJugados)} / ${tiempoDe(j.durHistoria * 60_000L)}", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 0.6.sp, maxLines = 1, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    Barra(avance)
                    Text("de la historia (lo que suele durar)".uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 6.dp))
                } else {
                    FitTitle(if (j.msJugados >= 60_000) tiempoDe(j.msJugados) else "0 h", 56.sp, 58.sp, color = Acento, letterSpacing = (-1.5).sp, unaLinea = true, maxLines = 1)
                    Text("TUS HORAS", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                val desglose = desgloseHoras(j)
                if (desglose.isNotBlank()) Text(desglose.uppercase(), color = Muted, fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, lineHeight = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                Text(listOfNotNull(if (j.veces > 0) "${j.veces} ${if (j.veces == 1) "vez" else "veces"}" else null, if (j.ultima > 0) "última vez " + haceCuanto(j.ultima).lowercase() else null).joinToString(" · ").ifBlank { if (j.msJugados > 0) "jugado" else "sin empezar" },
                    color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(16.dp))

        // Las acciones: jugar (o vincular) y la saga
        Filete(1.dp)
        // La acción de siempre, como en Marfil y Cobalto: un botón grande (jugar); la saga, al lado, en pequeño
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (j.sinLanzador) AccionGrande(androidx.compose.material.icons.Icons.Rounded.Add, "Añadir cómo se abre", Modifier.weight(1f)) { alVincular(j) }
            else AccionGrande(androidx.compose.material.icons.Icons.Rounded.PlayArrow, if (j.enPc && !j.abrible) "Jugar en el PC" else "Jugar", Modifier.weight(1f)) { alJugar(j) }
            EnlaceRevista(if (miSaga != null) "Saga: ${miSaga.nombre} →" else "Saga →", acento = miSaga != null) { saga = true }
        }
        Filete(1.dp)
        Spacer(Modifier.height(4.dp))
        Filete(2.dp)

        // Las casillas, fijas: cómo se abre (cada lanzador), En Azabache, lo que suele durar y la nube
        if (!j.sinLanzador) Casilla("Cómo se abre", "+ Añadir" to { alVincular(j) }) {
            j.lanzadores.forEachIndexed { i, l ->
                if (i > 0) { Spacer(Modifier.height(8.dp)); Filete(1.dp, Line); Spacer(Modifier.height(8.dp)) }
                when (l.tipo) {
                    TipoLanzador.ROM -> {
                        val e = remember(j, l) { Lanzar.elegir(ctx, j) }
                        val aqui = Archivos.tiene(l.clave)
                        Text((Catalogo.sistema(l.sistema)?.nombre ?: l.sistema) + " · " + (e?.etiqueta ?: "sin emulador"), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
                        val r = e?.resultado
                        Text((if (!aqui) "La ROM está en otro de tus móviles" else if (Lanzar.esInterno(e)) "Se juega en Hiedra · " + Nucleos.describir(Nucleos.para(l.sistema)!!, Nucleos.instalado(ctx, Nucleos.para(l.sistema)!!.id)) + " · guarda al salir" else when (r) { is Resultado.Ok -> if (Comando.esRetroArch(e.plantilla)) "Instalado · necesita el núcleo descargado en RetroArch" else "Instalado y listo"; is Resultado.Falta -> r.motivo; null -> "" }).uppercase(),
                            color = if (aqui && r is Resultado.Ok) Muted else Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                    TipoLanzador.APP -> { Text("App de Android", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp); Text(l.paquete, color = Muted, fontFamily = Body, fontSize = 12.sp) }
                    TipoLanzador.WEB -> { Text(l.url, color = Charcoal, fontFamily = Display, fontSize = 17.sp, maxLines = 2); Text(("Pantalla: " + listOf("automática", "horizontal", "vertical")[l.orientacion.coerceIn(0, 2)]).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
                    TipoLanzador.PC -> BotonesJugarPc(l.pcKey)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    when (l.tipo) {
                        TipoLanzador.ROM -> EnlaceRevista("Emulador →") { selector = true }
                        TipoLanzador.WEB -> EnlaceRevista("Editar →") { editarWeb = true }
                        else -> {}
                    }
                    EnlaceRevista("Quitar") { Biblioteca.cambiar(j.id) { it.sinLanzador(l.clave) }; if (l.tipo == TipoLanzador.ROM) Archivos.quitar(l.clave) }
                }
            }
        }
        val nota = NotaAzabache.de(notas, j)
        Casilla("En Azabache", if (NotaAzabache.instalado(ctx)) "Abrir" to { NotaAzabache.abrir(ctx, nota) } else null) {
            if (nota == null) {
                // Una línea: lo que hay y qué hacer (antes eran dos que decían lo mismo)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (NotaAzabache.instalado(ctx)) "Sin valorar aún · " else "Sin valorar aún", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp)
                    if (NotaAzabache.instalado(ctx)) EnlaceRevista("Valorar en Azabache →", acento = true) { NotaAzabache.abrir(ctx, null) }
                }
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(nota.general.toString(), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 64.sp, lineHeight = 62.sp, letterSpacing = (-2).sp, maxLines = 1, softWrap = false)
                    Text("/100", color = Muted, fontFamily = Body, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
                }
                if (nota.resena.isNotBlank()) Text(nota.resena, color = Charcoal, fontFamily = Display, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 6, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
        val datos = Datos.de[j.id]
        if (j.durHistoria > 0 || j.durExtras > 0 || j.durCompleto > 0) Casilla("Lo que suele durar") {
            listOf("Historia" to j.durHistoria, "Con extras" to j.durExtras, "Al completo" to j.durCompleto).filter { it.second > 0 }.forEach { (n, m) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(n.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
                    Text(tiempoDe(m * 60_000L), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                }
            }
            if (!datos?.duracionDe.isNullOrBlank()) Text("Según ${datos!!.duracionDe}.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        // Datos (como la ficha de Marfil): lo de fuera y lo de aquí, en líneas con puntos guía
        Casilla("Datos", "Buscar de nuevo" to { scope.launch { buscandoDatos = true; runCatching { Datos.pedir(ctx, j, forzar = true) }; buscandoDatos = false } }) {
            if (datos != null && !datos.nada) {
                if (datos.salida.isNotBlank()) MagFact("Salida", datos.salida)
                if (datos.desarrolladores.isNotEmpty()) MagFact(if (datos.desarrolladores.size == 1) "Estudio" else "Estudios", datos.desarrolladores.joinToString(" · "))
                if (datos.editores.isNotEmpty() && datos.editores != datos.desarrolladores) MagFact(if (datos.editores.size == 1) "Editor" else "Editores", datos.editores.joinToString(" · "))
                if (datos.generos.isNotEmpty()) MagFact("Géneros", datos.generos.joinToString(" · "))
                if (datos.metacritic > 0) MagFact("Metacritic", "${datos.metacritic}/100")
            }
            if (j.plataforma.isNotBlank()) MagFact("Plataforma", j.plataforma)
            j.lanzadores.forEach { l -> when (l.tipo) {
                TipoLanzador.ROM -> MagFact("ROM", if (Archivos.tiene(l.clave)) l.archivo.ifBlank { j.titulo } else "en otro dispositivo")
                TipoLanzador.APP -> MagFact("App", l.paquete)
                TipoLanzador.WEB -> MagFact("Web", l.url.removePrefix("https://").removePrefix("http://").removePrefix("www."))
                TipoLanzador.PC -> MagFact("En el PC", Suite.nombresPc[l.pcDev]?.ifBlank { null } ?: l.pcKey)
            } }
            if (miSaga != null) MagFact("Saga", "${miSaga.nombre} · ${miSaga.juegos.indexOf(j.id) + 1}")
            if (j.anadido > 0) MagFact("Añadido", fechaCorta(j.anadido))
            if (j.estado == Estado.PASADO && j.finalizado > 0) MagFact("Pasado", fechaCorta(j.finalizado))
            if (j.veces > 0) MagFact("Partidas", "${j.veces}")
            if (datos != null && !datos.nada && datos.fuente.isNotBlank()) MagFact("Datos de", datos.fuente)
            val pie = when {
                buscandoDatos -> "Buscando los datos del juego…"
                datos == null -> if (Datos.activo) "" else "Sin datos de fuera."
                datos.nada -> if (Servicios.claves(ctx).rawg.isBlank()) "No está en la tienda de Steam. Con una clave de RAWG (Ajustes → Cuenta → Servicios, o en Atrio) se buscan también los juegos de consola." else "No he encontrado este juego en Steam ni en RAWG. Si su nombre aquí no es el oficial, cámbialo en «Editar» y busca de nuevo."
                else -> ""
            }
            if (pie.isNotBlank()) Text(pie, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 6.dp))
            if (datos != null && datos.resumen.isNotBlank()) Text(datos.resumen, color = Charcoal, fontFamily = Display, fontSize = 15.sp, lineHeight = 21.sp, maxLines = 8, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp))
        }
        SeccionNube(j)
        Spacer(Modifier.height(20.dp))
    }
    if (editar) DialogoEditarJuego(j, alCerrar = { editar = false }, alHoras = { editar = false; editarHoras = true }, alQuitar = { editar = false; borrar = true })
    if (saga) DialogoSagaDeJuego(j) { saga = false }
    if (borrar) AlertDialog(
        onDismissRequest = { borrar = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Quitar «${j.titulo}»?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (j.rom != null) "Solo se quita de Hiedra: el archivo de la ROM no se toca." else "Solo se quita de Hiedra.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({ Biblioteca.quitar(j.id); borrar = false; alCerrar() }) { Text("QUITAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ borrar = false }) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
    if (eligePortada) AlertDialog(
        onDismissRequest = { eligePortada = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Portada de «${j.titulo}»", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                MagLink("SteamGridDB", "Las portadas de los juegos (600×900), con tu API key") { eligePortada = false; sgdb = true }
                Filete(1.dp, Line)
                MagLink("Buscar en internet", "Google Imágenes, Pinterest, ArtStation, Newgrounds") { eligePortada = false; buscaInternet = true }
                Filete(1.dp, Line)
                MagLink("Elegir de la galería") { eligePortada = false; cambiarPortada.launch("image/*") }
                if (j.portada.isNotBlank() && File(j.portada).exists()) { Filete(1.dp, Line); MagLink("Encuadrar", "Mueve y amplía la imagen entera dentro del hueco") { eligePortada = false; encuadre = true } }
                if (j.portada.isNotBlank()) { Filete(1.dp, Line); MagLink("Usar la portada automática") { eligePortada = false; Biblioteca.poner(j.copy(portada = "", zoom = 1f, ox = 0f, oy = 0f)); NubeP.portadaQuitada(ctx, j) } }
            }
        },
        confirmButton = {}, dismissButton = { TextButton({ eligePortada = false }) { Text("CERRAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
    if (encuadre) DialogoEncuadre(j) { encuadre = false }
    if (sgdb) DialogoSgdb(j, alCerrar = { sgdb = false }) { url ->
        sgdb = false
        scope.launch {
            val ruta = withContext(Dispatchers.IO) { runCatching { guardarPortadaBytes(ctx, j.id, Internet.descargar(url, "Mozilla/5.0 (Linux; Android 14) Hiedra").first, original = true) }.getOrNull() }
            if (ruta == null) Lanzar.aviso(ctx, "No he podido bajar esa portada") else Biblioteca.cambiar(j.id) { it.copy(portada = ruta, zoom = 1f, ox = 0f, oy = 0f) }
        }
    }
    if (buscaInternet) InternetPicker(
        initialQuery = j.titulo + (if (j.rom != null) " " + (sistema?.nombre ?: "") + " portada" else " portada"),
        idJuego = j.id,
        onPick = { ruta -> Biblioteca.por(j.id)?.let { Biblioteca.poner(it.copy(portada = ruta, zoom = 1f, ox = 0f, oy = 0f)) }; buscaInternet = false },
        onDismiss = { buscaInternet = false }, original = true
    )
    if (selector) SelectorEmulador(j.sistema, j) { selector = false }
    if (editarWeb) DialogoWeb(j) { editarWeb = false }
    if (editarHoras) DialogoHoras(j.msJugados, alAceptar = { ms -> Biblioteca.cambiar(j.id) { it.conTotalMs(ms) }; editarHoras = false }, alCerrar = { editarHoras = false })
}

/** Un cuadro con una línea de texto para escribir. */
@Composable
fun DialogoTexto(titulo: String, inicial: String, boton: String, alCerrar: () -> Unit, alAceptar: (String) -> Unit) {
    var t by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text(titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Campo(t, "", { t = it }) },
        confirmButton = { TextButton({ alAceptar(t) }) { Text(boton.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton(alCerrar) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}

/** Una línea de texto con un filete debajo (la casilla de la revista). */
@Composable
fun Campo(valor: String, pista: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            if (valor.isEmpty()) Text(pista, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
            BasicTextField(valor, alCambiar, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontSize = 18.sp), cursorBrush = SolidColor(Acento), modifier = Modifier.fillMaxWidth())
        }
        Filete(1.5.dp)
    }
}

/** Qué emulador usar: «Automático» o uno de los del sistema; ✓ si está instalado, «Instalar» si no. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectorEmulador(sistemaId: String, juego: Juego?, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val sistema = Catalogo.sistema(sistemaId)
    val evaluados = remember(juego, sistemaId) { if (juego != null) Lanzar.evaluar(ctx, juego) else Lanzar.evaluar(ctx, sistemaId, RomRef("content://sonda", "/storage/emulated/0/sonda")) }
    var actual by remember { mutableStateOf(if (juego != null) juego.emulador else Ajustes.emuladorPorSistema[sistemaId].orEmpty()) }
    ModalBottomSheet(alCerrar, containerColor = Paper, contentColor = Charcoal, shape = RectangleShape) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Antetitulo(if (juego != null) "Emulador de este juego" else "Emulador", Modifier.padding(bottom = 4.dp), color = Acento, conCuadro = false)
            FitTitle(sistema?.nombre ?: sistemaId, 34.sp, 36.sp, letterSpacing = (-1).sp, maxLines = 2)
            Cuerpo(if (Nucleos.para(sistemaId) != null) "«Automático» usa Hiedra: el núcleo se baja la primera vez, al salir se guarda donde lo dejaste y sigues en tu otro móvil. Los demás emuladores siguen aquí; los que tienes instalados salen marcados."
                else "Los que ya tienes instalados salen marcados. «Automático» usa uno propio (sin núcleos que descargar) y, si no hay, RetroArch.", tam = 12, modifier = Modifier.padding(vertical = 8.dp))
            Nucleos.para(sistemaId)?.let { n -> BloqueNucleo(n) }
            Filete(2.dp)
            fun elegir(l: String) {
                actual = l
                if (juego != null) Biblioteca.cambiar(juego.id) { x -> x.copy(lanzadores = x.lanzadores.map { if (it.tipo == TipoLanzador.ROM) it.copy(emulador = l) else it }) }
                else { if (l.isEmpty()) Ajustes.emuladorPorSistema.remove(sistemaId) else Ajustes.emuladorPorSistema[sistemaId] = l; Ajustes.guardar(ctx) }
            }
            Opcion("Automático", "Recomendado", actual.isEmpty(), true, null) { elegir("") }
            if (evaluados.isEmpty()) Cuerpo("Hiedra todavía no sabe lanzar este sistema.", modifier = Modifier.padding(top = 12.dp))
            evaluados.forEach { e ->
                val r = e.resultado
                val sub = when {
                    Lanzar.esInterno(e) -> "El de Hiedra · " + Nucleos.describir(Nucleos.para(sistemaId)!!, remember(Nucleos.version) { Nucleos.instalado(ctx, Nucleos.para(sistemaId)!!.id) })
                    r is Resultado.Ok -> if (Comando.esRetroArch(e.plantilla)) "Instalado · necesita el núcleo en RetroArch" else "Instalado"
                    r is Resultado.Falta -> r.motivo
                    else -> ""
                }
                Opcion(e.etiqueta, sub, actual == e.etiqueta, r is Resultado.Ok, (r as? Resultado.Falta)?.paquete) { elegir(e.etiqueta) }
            }
        }
    }
}

@Composable
private fun Opcion(titulo: String, sub: String, elegida: Boolean, instalado: Boolean, paquetePlay: String?, alElegir: () -> Unit) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().clickable(onClick = alElegir).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(titulo, color = if (elegida) Acento else Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text(sub.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        if (!instalado && paquetePlay != null) Text("INSTALAR\u00A0→", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp,
            modifier = Modifier.clickable { Lanzar.tienda(ctx, paquetePlay) }.padding(10.dp))
        else if (elegida) Text("✓", color = Acento, fontWeight = FontWeight.Black, fontSize = 18.sp)
        else if (instalado && titulo != "Automático") Text("INSTALADO", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
    Filete(1.dp, Line)
}

/**
 * El núcleo del jugador interno para un sistema: lo que hay instalado y Descargar / Actualizar / Quitar. Actualizar avisa: un estado
 * guardado solo carga con la versión que lo hizo (la partida del cartucho no se pierde nunca).
 */
@Composable
private fun BloqueNucleo(n: Nucleos.Nucleo) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val inst = remember(Nucleos.version) { Nucleos.instalado(ctx, n.id) }
    var bajando by remember { mutableStateOf<Float?>(null) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var confirmar by remember { mutableStateOf(false) }
    fun bajar() {
        bajando = 0f; mensaje = null
        scope.launch {
            mensaje = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Nucleos.instalar(ctx, n.id) { p -> bajando = p } } }
                .fold({ "Listo: ${Nucleos.describir(n, it)}" }, { "No se ha podido bajar: ${it.message ?: "sin conexión"}" })
            bajando = null
        }
    }
    Antetitulo("Núcleo de Hiedra", Modifier.padding(top = 6.dp, bottom = 2.dp))
    Text(Nucleos.describir(n, inst), color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
    if (inst != null) Text("VERSIÓN ${inst.version}", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        when {
            bajando != null -> Text("BAJANDO · ${((bajando ?: 0f) * 100).toInt()} %", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp)
            inst == null -> EnlaceRevista("Descargar ahora →", acento = true) { bajar() }
            else -> {
                EnlaceRevista(if (confirmar) "Sí, actualizar →" else "Actualizar", acento = confirmar) { if (confirmar) { confirmar = false; bajar() } else confirmar = true }
                EnlaceRevista("Quitar") { Nucleos.quitar(ctx, n.id); mensaje = "Quitado: se volverá a bajar al jugar" }
            }
        }
    }
    if (confirmar) Cuerpo("Los estados guardados con esta versión pueden dejar de cargar con la nueva (en todos tus móviles, hasta que también la actualicen). La partida del cartucho no se pierde.", tam = 12)
    mensaje?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp) }
}

/** Horas y minutos jugados, para corregirlos a mano. */
@Composable
fun DialogoHoras(ms: Long, alAceptar: (Long) -> Unit, alCerrar: () -> Unit) {
    var h by remember { mutableStateOf((ms / 3_600_000).toString()) }
    var m by remember { mutableStateOf(((ms / 60_000) % 60).toString()) }
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Horas jugadas", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) { Etiqueta("Horas"); Campo(h, "0", { h = it.filter(Char::isDigit).take(5) }) }
                    Column(Modifier.weight(1f)) { Etiqueta("Minutos"); Campo(m, "0", { m = it.filter(Char::isDigit).take(2) }) }
                }
                Cuerpo("Hiedra suma solo el tiempo que pasas fuera cuando abres un juego (aproximado). Aquí lo corriges.", tam = 12)
            }
        },
        confirmButton = { TextButton({ alAceptar((h.toLongOrNull() ?: 0L) * 3_600_000L + (m.toLongOrNull() ?: 0L).coerceAtMost(59L) * 60_000L) }) { Text("GUARDAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton(alCerrar) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}

/** Una barra fina de avance, como la de Marfil. */
@Composable
fun Barra(f: Float) {
    Box(Modifier.fillMaxWidth().height(3.dp).background(Line)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).fillMaxHeight().background(Acento)) }
}

/** De dónde salen las horas: «Torre 40 h · Steam 42 h · este móvil 1 h». Vacío si todo es de aquí. */
fun desgloseHoras(j: Juego): String {
    val partes = ArrayList<String>()
    if (j.propias.ms >= 60_000) partes += "este móvil " + tiempoDe(j.propias.ms)
    val otros = j.ajenas.values.sumOf { it.ms }
    if (otros >= 60_000) partes += "otros móviles " + tiempoDe(otros)
    for ((k, h) in j.externas) {
        if (h.ms < 60_000) continue
        partes += nombreFuente(k) + " " + tiempoDe(h.ms)
    }
    return if (partes.size <= 1) "" else partes.joinToString(" · ")
}

/** Cómo se llama una fuente de horas en las pantallas. */
fun nombreFuente(k: String): String = when {
    Fuentes.esPc(k) -> Suite.nombresPc[k.removePrefix("pc:")]?.ifBlank { null } ?: "PC"
    k == Fuentes.STEAM -> "Steam"
    Fuentes.esCarmesi(k) -> "Carmesí"
    else -> k
}

private fun fechaCorta(t: Long) = Formato.fecha(t)
fun notaTexto(n: Float) = if (n <= 0f) "—" else if (n % 1f == 0f) n.toInt().toString() else String.format(java.util.Locale("es", "ES"), "%.1f", n)

/** Una casilla fija de la ficha: su antetítulo (con una acción a la derecha, si la hay) y lo que pinta, entre filetes. */
@Composable
fun Casilla(titulo: String, accion: Pair<String, () -> Unit>? = null, contenido: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { Antetitulo(titulo, tam = 12.sp, conCuadro = false) }
            if (accion != null) Text(accion.first.uppercase(), color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.2.sp, modifier = Modifier.clickable(onClick = accion.second).padding(4.dp))
        }
        contenido()
    }
    Filete(2.dp)
}

/** «Editar»: el nombre, la plataforma (en las fichas sin lanzador), corregir las horas y quitar el juego. */
@Composable
fun DialogoEditarJuego(j: Juego, alCerrar: () -> Unit, alHoras: () -> Unit, alQuitar: () -> Unit) {
    var nombre by remember { mutableStateOf(j.titulo) }
    var plataforma by remember { mutableStateOf(j.plataforma) }
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Editar", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                CampoRevista("Nombre", nombre, "nombre del juego") { nombre = it }
                if (j.sinLanzador) CampoRevista("Plataforma", plataforma, "PC, Switch, PS5…") { plataforma = it }
                Spacer(Modifier.height(12.dp))
                MagLink("Corregir las horas", "Hiedra las apunta al volver de jugar; aquí las ajustas", onClick = alHoras)
                Filete(1.dp, Line)
                MagLink("Quitar de Hiedra", if (j.rom != null) "La ROM no se toca" else null, onClick = alQuitar)
            }
        },
        confirmButton = { TextButton({ if (nombre.isNotBlank()) Biblioteca.cambiar(j.id) { it.copy(titulo = nombre.trim(), plataforma = plataforma.trim()) }; alCerrar() }) { Text("GUARDAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton(alCerrar) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}

/** «En la nube» (solo ROMs): carpeta de guardados del sistema, subir la ROM a tu kDrive y bajarla en otro móvil. */
@Composable
private fun SeccionNube(j: Juego) {
    val rom = j.rom ?: return
    val ctx = LocalContext.current
    val sistema = Catalogo.sistema(j.sistema)
    val listo = Cuenta.vinculada && KDrive.on(ctx)
    var mensaje by remember(j.id) { mutableStateOf<String?>(null) }
    var ocupado by remember(j.id) { mutableStateOf(false) }
    var nube by remember(j.id, j.uri) { mutableStateOf<NubeP.Ficha?>(null) }
    LaunchedEffect(j.id, j.uri, listo) { if (listo) nube = withContext(Dispatchers.IO) { NubeP.romEnNube(j) } }
    val carpeta = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) NubeP.ponCarpeta(ctx, j.sistema, u) }
    var bajar by remember(j.id) { mutableStateOf(false) }
    if (bajar) DialogoBajarRom(j, jugar = false) { bajar = false; nube = null }
    Spacer(Modifier.height(16.dp))
    Antetitulo("En la nube", Modifier.padding(bottom = 8.dp), tam = 12.sp, conCuadro = false)
    if (!listo) {
        Cuerpo("Para llevarte la partida a otro móvil: vincula tu cuenta de Atrio y conecta tu kDrive (Ajustes). Hiedra sube el guardado al volver de jugar y baja el último antes de jugar.", tam = 12)
    } else {
        val car = NubeP.carpetas[j.sistema]
        MagLink(if (car == null) "Elegir la carpeta de guardados de ${sistema?.corto ?: j.sistema}" else "Carpeta de guardados elegida · cambiar",
            "La carpeta donde ${sistema?.corto ?: "el emulador"} deja los .srm / .sav. Solo se sincronizan los de esta ROM (mismo nombre que el archivo).") { carpeta.launch(null) }
        Filete(1.dp, Line)
        when {
            j.enOtroDispositivo && nube != null -> MagLink("Descargar la ROM de mi kDrive", "A la carpeta de ROMs de este móvil, en la de ${sistema?.corto ?: j.sistema} («Jugar» también la baja)") { bajar = true }
            j.enOtroDispositivo -> Cuerpo("Esta ROM está en otro de tus móviles y todavía no ha llegado a tu kDrive: se sube sola, con wifi, al abrir Hiedra en ese móvil.", tam = 12)
            nube != null -> Cuerpo("La ROM ya está en tu kDrive: en tus otros móviles, «Jugar» la baja.", tam = 12)
            else -> MagLink(if (ocupado) "Subiendo…" else "Subir la ROM a mi kDrive ahora", if (NubeP.romsSolas(ctx)) "Se sube sola con wifi; esto la sube ya, también con datos" else "Solo a tu kDrive, para tenerla en tus otros móviles") {
                if (!ocupado && j.uri.isNotBlank()) {
                    ocupado = true; mensaje = "Subiendo la ROM…"
                    kotlin.concurrent.thread {
                        val r = runCatching {
                            val d = androidx.documentfile.provider.DocumentFile.fromSingleUri(ctx, android.net.Uri.parse(j.uri)) ?: error("no encuentro el archivo")
                            NubeP.subirRom(ctx.applicationContext, j, DocArch(ctx.applicationContext, d))
                        }
                        android.os.Handler(android.os.Looper.getMainLooper()).post { mensaje = r.fold({ "ROM subida a tu kDrive" }, { "No se pudo subir: ${it.message}" }); ocupado = false; nube = null }
                    }
                }
            }
        }
    }
    mensaje?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
}
