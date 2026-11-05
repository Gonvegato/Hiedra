package com.gonzalo.hiedra

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// El nombre de un archivo como lo guarda libretro en su servidor de portadas: los caracteres prohibidos (& * / : ` < > ? \ |) pasan a «_».
fun nombreLibretro(archivo: String): String = archivo.replace(Regex("[&*/:`<>?\\\\|]"), "_")

/** Las direcciones donde buscar la portada de una ROM, por orden (caja, y si no, pantalla de título). */
fun urlsLibretro(j: Juego): List<String> {
    val s = Catalogo.sistema(j.sistema)?.libretro ?: return emptyList()
    if (j.archivo.isBlank()) return emptyList()
    val base = "https://thumbnails.libretro.com/" + Uri.encode(s) + "/"
    val n = Uri.encode(nombreLibretro(j.archivo)) + ".png"
    return listOf(base + "Named_Boxarts/" + n, base + "Named_Titles/" + n)
}

/** Los dos colores del degradado de un juego: el de su sistema (o un tono según el nombre) un poco movido por el título. */
/** La proporción de una portada (ancho/alto): 2:3, la de las carátulas (SteamGridDB, cajas). La misma en la ficha, las listas y la pantalla de inicio, para que nada se recorte distinto. */
const val PROPORCION_PORTADA = 2f / 3f

/** Las telas de las portadas de letra: las mismas en Hiedra, Marfil, Cobalto y Azabache (un título, siempre el mismo color). */
val TELAS = listOf(0xFF7A2E2E, 0xFF2F4A6B, 0xFF35523F, 0xFF5B3F6B, 0xFF8A5A2B, 0xFF3F5E63, 0xFF6B6B3A, 0xFF4A4A52).map { Color(it) }
fun telaDe(titulo: String): Color = TELAS[(normTela(titulo).hashCode() and 0x7fffffff) % TELAS.size]
/** La misma normalización que Marfil (así un título tiene la misma tela en las cuatro apps). */
fun normTela(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
private fun mezcla(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

fun tonosDe(j: Juego): Pair<Color, Color> {
    // La tela del título (como las tapas de Marfil), de un poco más clara arriba a más oscura abajo
    val tela = telaDe(j.titulo)
    return mezcla(tela, Color.White, 0.10f) to mezcla(tela, Color.Black, 0.32f)
}
private fun Color.toArgb2(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

private val iconos = HashMap<String, ImageBitmap?>()

/** El icono de una app instalada, ya dibujado (se guarda en memoria). */
@Composable
fun rememberIconoApp(paquete: String): ImageBitmap? {
    val c = LocalContext.current
    val estado = produceState(iconos[paquete], paquete) {
        if (!iconos.containsKey(paquete)) value = withContext(Dispatchers.Default) {
            runCatching { c.packageManager.getApplicationIcon(paquete).aBitmap(256).asImageBitmap() }.getOrNull().also { iconos[paquete] = it }
        } else value = iconos[paquete]
    }
    return estado.value
}

fun Drawable.aBitmap(lado: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null && bitmap.width >= lado) return bitmap
    val b = Bitmap.createBitmap(lado, lado, Bitmap.Config.ARGB_8888)
    val cv = Canvas(b); setBounds(0, 0, lado, lado); draw(cv); return b
}

/** Una imagen que prueba una dirección y, si falla, la siguiente. [alFallar] se llama si no hay ninguna que sirva. */
@Composable
private fun ImagenEnCadena(datos: List<Any>, modifier: Modifier, escala: ContentScale, alCargar: () -> Unit) {
    if (datos.isEmpty()) return
    val c = LocalContext.current
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(c).data(datos[0]).crossfade(true).build(), contentDescription = null,
        modifier = modifier, contentScale = escala, loading = {}, success = { androidx.compose.foundation.Image(painter = painter, contentDescription = null, contentScale = escala, modifier = Modifier.fillMaxSize()); LaunchedEffect(Unit) { alCargar() } },
        error = { ImagenEnCadena(datos.drop(1), Modifier.fillMaxSize(), escala, alCargar) }
    )
}

/**
 * La portada de Hiedra: un arco (la puerta de Atrio). Un degradado con la inicial, y encima la imagen: la que elegiste, la de
 * libretro (ROMs), el icono (apps) o la de la propia web. Abajo, el título; y un botón de jugar si se da [alJugar].
 */
@Composable
fun PortadaArco(
    j: Juego, modifier: Modifier = Modifier, titulo: Boolean = true, alJugar: (() -> Unit)? = null, letra: Boolean = true, relleno: Boolean = false, tamLetra: Int = 64,
) {
    val c = LocalContext.current
    val (a, b) = remember(j.id, j.titulo, j.sistema) { tonosDe(j) }
    val forma = if (relleno) androidx.compose.ui.graphics.RectangleShape else arco()
    var conImagen by remember(j.id, j.portada) { mutableStateOf(false) }
    val hayPortada = remember(j.portada) { j.portada.isNotBlank() && File(j.portada).exists() }
    val borde = Skin.forma
    Box(
        modifier.let { if (relleno) it else it.aspectRatio(PROPORCION_PORTADA) }
            .let { m -> if (borde.sombraDura) m.drawBehind { drawRect(Charcoal, topLeft = Offset(6.dp.toPx(), 6.dp.toPx()), size = size) } else m }
            .clip(forma).background(Brush.linearGradient(listOf(a, b), Offset.Zero, Offset(600f, 1200f)))
            .let { if (borde.borde.value > 0f) it.border(borde.borde, if (borde.bordeTinta) Charcoal else Line, forma) else it }
    ) {
        if (letra && !conImagen) Text(
            j.titulo.trim().firstOrNull()?.uppercase() ?: "?", color = Color.White.copy(alpha = 0.28f), fontFamily = Display, fontWeight = FontWeight.Bold,
            fontSize = tamLetra.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = (tamLetra * 0.4f).dp)
        )
        // La imagen, según de dónde sea el juego
        when {
            // (si el archivo de tu portada ya no está, se enseña la siguiente: la de internet o la automática)
            hayPortada && j.encuadrada -> { ImagenEncuadrada(File(j.portada), j.zoom, j.ox, j.oy, Modifier.fillMaxSize()); LaunchedEffect(j.portada) { conImagen = true } }
            hayPortada -> ImagenEnCadena(listOf(File(j.portada)), Modifier.fillMaxSize(), ContentScale.Crop) { conImagen = true }
            j.portadaUrl.isNotBlank() -> ImagenEnCadena(listOf(j.portadaUrl), Modifier.fillMaxSize(), ContentScale.Crop) { conImagen = true }
            j.rom != null && Ajustes.portadasLibretro -> ImagenEnCadena(urlsLibretro(j), Modifier.fillMaxSize(), ContentScale.Crop) { conImagen = true }
            j.app != null -> rememberIconoApp(j.paquete)?.let {
                Image(it, null, Modifier.align(Alignment.Center).fillMaxWidth(0.52f).aspectRatio(1f).offset(y = (-14).dp).clip(esquina(18.dp)), contentScale = ContentScale.Fit)
            }
            j.web != null -> faviconWeb(j.url)?.let { u -> ImagenEnCadena(listOf(u), Modifier.align(Alignment.Center).fillMaxWidth(0.4f).aspectRatio(1f).offset(y = (-14).dp).clip(esquina(14.dp)), ContentScale.Fit) {} }
        }
        if (titulo && !conImagen) {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))).padding(start = 10.dp, end = 10.dp, top = 28.dp, bottom = 8.dp)) {
                Text(j.titulo, color = Color.White, fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // La fila de abajo deja sitio al botón de jugar (que va a su derecha), así el título usa todo el ancho
                Row(Modifier.fillMaxWidth().heightIn(min = if (alJugar != null) 30.dp else 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(etiquetaDe(j), color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, maxLines = 1, modifier = Modifier.weight(1f).padding(end = if (alJugar != null) 34.dp else 0.dp))
                }
            }
        }
        if (j.favorito) Icon(Icons.Filled.Star, "Favorito", tint = Color(0xFFFFD24A), modifier = Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 12.dp).size(16.dp))
        if (alJugar != null) Box(
            Modifier.align(Alignment.BottomEnd).padding(8.dp).size(30.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Acento).clickable(onClick = alJugar),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.PlayArrow, "Jugar ${j.titulo}", tint = SobreAcento, modifier = Modifier.size(20.dp)) }
    }
}

/** El texto pequeño bajo el título: el sistema, «Android» o la web. */
fun etiquetaDe(j: Juego): String = when (j.tipo) {
    TipoLanzador.ROM -> Catalogo.sistema(j.sistema)?.corto ?: j.sistema.uppercase()
    TipoLanzador.APP -> "Android"
    TipoLanzador.WEB -> runCatching { Uri.parse(j.url).host?.removePrefix("www.") }.getOrNull() ?: "Web"
    TipoLanzador.PC -> "PC"
    null -> j.plataforma.ifBlank { "Ficha" }
}

/** La etiqueta de un lanzador: «GBA», «Android», la web o «PC». */
fun etiquetaDe(l: Lanzador): String = when (l.tipo) {
    TipoLanzador.ROM -> Catalogo.sistema(l.sistema)?.corto ?: l.sistema.uppercase()
    TipoLanzador.APP -> "Android"
    TipoLanzador.WEB -> runCatching { Uri.parse(l.url).host?.removePrefix("www.") }.getOrNull() ?: "Web"
    TipoLanzador.PC -> "PC"
}

/** De dónde es un juego, con todos sus lanzadores: «GBA · PC». */
fun etiquetasDe(j: Juego): String = if (j.lanzadores.size <= 1) etiquetaDe(j) else j.lanzadores.joinToString(" · ") { etiquetaDe(it) }

/** El icono de la propia web (su /favicon.ico: se pide solo a ella, no a terceros). */
fun faviconWeb(url: String): String? = runCatching { Uri.parse(url).let { "${it.scheme}://${it.host}/favicon.ico" } }.getOrNull()

/** Texto de horas: "1 h 20 min", "45 min", "—". */
fun tiempoDe(ms: Long): String {
    val m = (ms / 60000).toInt()
    return when { m <= 0 -> "—"; m < 60 -> "$m min"; m % 60 == 0 -> "${m / 60} h"; else -> "${m / 60} h ${m % 60} min" }
}

/** «hace 3 días», «ayer», «hoy». */
fun haceCuanto(t: Long, ahora: Long = System.currentTimeMillis()): String {
    if (t <= 0) return "Nunca"
    val d = ((ahora - t) / 86_400_000L).toInt()
    return when { d <= 0 -> "Hoy"; d == 1 -> "Ayer"; d < 30 -> "Hace $d días"; d < 365 -> "Hace ${d / 30} meses"; else -> "Hace más de un año" }
}
