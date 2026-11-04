package com.gonzalo.hiedra

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File
import kotlin.math.roundToInt

/*
 * Encuadrar una portada: se guarda la imagen ENTERA (tal como se bajó) y tú decides qué parte se ve en el hueco de la portada (3:4):
 * [zoom] 1 = la imagen cubre el hueco; menos, más pequeña; más, ampliada; y [ox]/[oy] la mueven, como fracción del ancho y del alto del
 * hueco (-1..1). El mismo encuadre vale para la ficha, las portadas de la revista y los widgets.
 */

/** El tamaño (px) con que se dibuja una imagen de [iw]×[ih] para cubrir un hueco de [w]×[h] con ese [zoom]. */
fun tamanoEncuadre(w: Float, h: Float, iw: Float, ih: Float, zoom: Float): Pair<Float, Float> {
    if (iw <= 0f || ih <= 0f) return w * zoom to h * zoom
    val ar = iw / ih
    val base = if (ar > w / h) (h * ar to h) else (w to w / ar)
    return base.first * zoom to base.second * zoom
}

/** El tamaño de la foto tal como se ve (con su giro EXIF), leyendo solo la cabecera del archivo; null si no se puede. */
fun medidasFoto(f: File): Pair<Int, Int>? = runCatching {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, o)
    if (o.outWidth <= 0 || o.outHeight <= 0) return@runCatching null
    val giro = runCatching { android.media.ExifInterface(f.path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
    if (giro in setOf(5, 6, 7, 8)) o.outHeight to o.outWidth else o.outWidth to o.outHeight
}.getOrNull()

/**
 * La parte de un bitmap que se ve en un hueco de proporción [forma] (ancho/alto) con ese encuadre: para los widgets, que dibujan bitmaps.
 * Lo que queda fuera del hueco se pierde; si la imagen no llega a cubrirlo (zoom < 1), se rellena con el borde.
 */
fun recortarEncuadre(b: Bitmap, zoom: Float, ox: Float, oy: Float, forma: Float = 0.75f): Bitmap {
    if (zoom == 1f && ox == 0f && oy == 0f) return b
    // El hueco, en píxeles de la imagen dibujada: se calcula con un hueco de 1000 px de alto
    val h = 1000f; val w = h * forma
    val (dw, dh) = tamanoEncuadre(w, h, b.width.toFloat(), b.height.toFloat(), zoom)
    // La imagen va centrada en el hueco y desplazada (ox, oy); el hueco, en coordenadas de la imagen dibujada:
    val izq = (w - dw) / 2 + ox * w; val arr = (h - dh) / 2 + oy * h
    val esc = b.width / dw
    val x0 = ((0f - izq) * esc).roundToInt(); val y0 = ((0f - arr) * esc).roundToInt()
    val cw = (w * esc).roundToInt().coerceAtLeast(1); val ch = (h * esc).roundToInt().coerceAtLeast(1)
    val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    c.drawColor(android.graphics.Color.argb(255, 24, 24, 28))
    c.drawBitmap(b, -x0.toFloat(), -y0.toFloat(), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
    return out
}

/** Una imagen encuadrada en su hueco, con total libertad (la foto entera, y el hueco solo enseña lo que queda dentro). */
@Composable
fun ImagenEncuadrada(f: File, zoom: Float, ox: Float, oy: Float, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val lado = if (zoom > 1.3f) 3072 else 2048
    val peticion = remember(f.path, f.lastModified(), lado) { coil.request.ImageRequest.Builder(ctx).data(f).size(lado).build() }
    val medidas = remember(f.path, f.lastModified()) { medidasFoto(f) }
    val d = LocalDensity.current
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val (dw, dh) = tamanoEncuadre(w, h, medidas?.first?.toFloat() ?: 0f, medidas?.second?.toFloat() ?: 0f, zoom)
        AsyncImage(peticion, null, Modifier.requiredSize(with(d) { dw.toDp() }, with(d) { dh.toDp() }).offset { IntOffset((ox * w).roundToInt(), (oy * h).roundToInt()) }, contentScale = ContentScale.FillBounds)
    }
}

/** Encuadrar la portada de un juego: con la forma del hueco (3:4), arrastra para mover y pellizca (o usa la barra) para el zoom. */
@Composable
fun DialogoEncuadre(j: Juego, alCerrar: () -> Unit) {
    var zoom by remember { mutableStateOf(j.zoom) }
    var ox by remember { mutableStateOf(j.ox) }
    var oy by remember { mutableStateOf(j.oy) }
    val f = File(j.portada)
    val colores = SliderDefaults.colors(thumbColor = Acento, activeTrackColor = Acento, inactiveTrackColor = Sand)
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Encuadrar la portada", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.testTag("encuadre").fillMaxWidth(0.6f).aspectRatio(0.75f).clip(esquina(3.dp)).background(Sand).border(1.dp, Line, esquina(3.dp))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, z, _ ->
                            zoom = (zoom * z).coerceIn(0.3f, 8f)
                            ox = (ox + pan.x / size.width).coerceIn(-1f, 1f)
                            oy = (oy + pan.y / size.height).coerceIn(-1f, 1f)
                        }
                    }) {
                    if (f.exists()) ImagenEncuadrada(f, zoom, ox, oy, Modifier.fillMaxSize())
                }
                Etiqueta("Zoom (menos de 1 la hace más pequeña que el hueco)", Modifier.padding(top = 10.dp).align(Alignment.Start))
                Slider(zoom, { zoom = it }, valueRange = 0.3f..8f, colors = colores)
                Cuerpo("Arrastra en cualquier dirección: queda como lo veas, en la ficha, en la portada y en los widgets.", tam = 12)
                MagLink("Restablecer") { zoom = 1f; ox = 0f; oy = 0f }
            }
        },
        confirmButton = { TextButton({ Biblioteca.cambiar(j.id) { it.copy(zoom = zoom, ox = ox, oy = oy) }; alCerrar() }) { Text("GUARDAR", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton(alCerrar) { Text("CANCELAR", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}
