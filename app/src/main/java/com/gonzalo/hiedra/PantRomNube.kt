package com.gonzalo.hiedra

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** En qué va la ROM de un juego que está en otro móvil. */
private sealed interface PasoRom {
    data object Mirando : PasoRom
    data object NoSubida : PasoRom
    data class Lista(val f: NubeP.Ficha) : PasoRom
    data class Bajando(val f: NubeP.Ficha) : PasoRom
    data class Fallo(val texto: String) : PasoRom
}

/**
 * La ROM de un juego que está en otro de tus móviles: se baja de tu kDrive a la carpeta de ROMs de este móvil (en la subcarpeta del
 * sistema) y, con [jugar], se abre al acabar. La primera vez, si no hay ninguna carpeta de ROMs en la que se pueda escribir, la pide
 * (y se queda como carpeta de ROMs).
 */
@Composable
fun DialogoBajarRom(j: Juego, jugar: Boolean, alCerrar: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext
    var paso by remember(j.id) { mutableStateOf<PasoRom>(PasoRom.Mirando) }
    var carpeta by remember { mutableStateOf(NubeP.carpetaBajadas(ctx)) }
    var hecho by remember { mutableFloatStateOf(0f) }
    val listo = Cuenta.vinculada && KDrive.on(ctx)
    LaunchedEffect(j.id, listo) {
        if (!listo) return@LaunchedEffect
        paso = withContext(Dispatchers.IO) { NubeP.romEnNube(j) }?.let { PasoRom.Lista(it) } ?: PasoRom.NoSubida
    }
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u: Uri? ->
        if (u != null) {
            Escaneo.recordar(ctx, u)
            val nombre = DocumentFile.fromTreeUri(ctx, u)?.name ?: "ROMs"
            if (Biblioteca.carpetas.none { it.uri == u.toString() }) { Biblioteca.carpetas.add(Carpeta(u.toString(), nombre)); Biblioteca.guardar() }
            carpeta = NubeP.carpetaBajadas(ctx)
        }
    }
    // Bajando: cuánto lleva (el archivo a medias crece mientras llega)
    val bajando = paso as? PasoRom.Bajando
    if (bajando != null) LaunchedEffect(bajando) {
        val tmp = NubeP.temporalRom(app, j)
        while (isActive) { hecho = if (bajando.f.s > 0) (tmp.length().toFloat() / bajando.f.s).coerceIn(0f, 1f) else 0f; delay(300) }
    }
    if (bajando != null) LaunchedEffect("bajar" + j.id) {
        val raiz = carpeta ?: return@LaunchedEffect
        val r = withContext(Dispatchers.IO) { runCatching { NubeP.bajarRomAqui(app, j, bajando.f, raiz) } }
        r.onSuccess {
            if (jugar) { (Biblioteca.por(j.id) ?: j).let { g -> Lanzar.lanzar(ctx, g)?.let { Lanzar.aviso(ctx, it) } } } else Lanzar.aviso(ctx, "ROM descargada en ${raiz.name ?: "tu carpeta de ROMs"}/${j.sistema}")
            alCerrar()
        }.onFailure { paso = PasoRom.Fallo("No se ha podido bajar: ${it.message ?: "error de red"}") }
    }

    val sistema = Catalogo.sistema(j.sistema)?.corto ?: j.sistema
    AlertDialog(
        onDismissRequest = { if (bajando == null) alCerrar() }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text(j.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                val texto = if (!listo) "Esta ROM está en otro de tus móviles. Para bajarla aquí, vincula tu cuenta de Atrio y conecta tu kDrive (Ajustes)." else when (val p = paso) {
                    PasoRom.Mirando -> "Mirando en tu kDrive…"
                    PasoRom.NoSubida -> "Esta ROM está en otro de tus móviles y todavía no ha llegado a tu kDrive. Se sube sola, con wifi, al abrir Hiedra en ese móvil (Ajustes → Avanzado → Subir mis ROMs)."
                    is PasoRom.Lista -> "«${p.f.n}» (${Nucleos.megas(p.f.s)}) está en tu kDrive. " +
                        (carpeta?.let { "Se baja a ${it.name ?: "tu carpeta de ROMs"}/${j.sistema}, donde la encuentran tus emuladores." } ?: "Elige dónde dejar las ROMs que bajes (la carpeta de ROMs de este móvil): se guarda para la próxima vez.")
                    is PasoRom.Bajando -> "Bajando «${p.f.n}»… ${(hecho * 100).toInt()} %"
                    is PasoRom.Fallo -> p.texto
                }
                Text(texto, color = Charcoal, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
                if (bajando != null) Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(3.dp).background(Line)) {
                    Box(Modifier.fillMaxWidth(hecho).fillMaxHeight().background(Acento))
                }
                if (paso is PasoRom.Lista && carpeta != null) Text("Elegir otra carpeta", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 14.sp,
                    modifier = Modifier.padding(top = 10.dp).clickable { elegir.launch(null) })
            }
        },
        confirmButton = {
            val p = paso
            when {
                p is PasoRom.Lista && carpeta == null -> BotonRom("Elegir carpeta", acento = true) { elegir.launch(null) }
                p is PasoRom.Lista -> BotonRom(if (jugar) "Bajar y jugar" else "Bajar", acento = true) { paso = PasoRom.Bajando(p.f) }
                p is PasoRom.Fallo -> BotonRom("Reintentar", acento = true) { paso = PasoRom.Mirando; carpeta = NubeP.carpetaBajadas(ctx)
                    kotlin.concurrent.thread { val f = NubeP.romEnNube(j); android.os.Handler(android.os.Looper.getMainLooper()).post { paso = f?.let { PasoRom.Lista(it) } ?: PasoRom.NoSubida } } }
                else -> {}
            }
        },
        dismissButton = { if (bajando == null) BotonRom("Cerrar") { alCerrar() } }
    )
}

@Composable
private fun BotonRom(texto: String, acento: Boolean = false, onClick: () -> Unit) =
    TextButton(onClick) { Text(texto.uppercase(), color = if (acento) Acento else Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }
