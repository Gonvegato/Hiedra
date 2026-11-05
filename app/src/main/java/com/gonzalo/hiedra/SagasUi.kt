package com.gonzalo.hiedra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** De dónde son los juegos de una saga: «GBA», «GBA y PC», «GBA y otros». */
fun origenesDe(juegos: List<Juego>): String = juegos.map { etiquetaDe(it) }.filter { it.isNotEmpty() }.distinct().let {
    when (it.size) { 0 -> ""; 1 -> it[0]; 2 -> "${it[0]} y ${it[1]}"; else -> "${it[0]} y otros" }
}

/** El que toca ahora en la saga: el que estás jugando o, si no, el primero sin empezar (ni abandonar). */
fun siguienteDe(juegos: List<Juego>): Juego? = juegos.firstOrNull { it.estado == Estado.JUGANDO } ?: juegos.firstOrNull { it.estado == Estado.PENDIENTE }

@Composable
private fun BotonTexto(texto: String, acento: Boolean = false, onClick: () -> Unit) =
    TextButton(onClick) { Text(texto.uppercase(), color = if (acento) Acento else Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }

/** La página de una saga: sus juegos en orden, el que toca ahora y cómo editarla (nombre, orden, juegos). */
@Composable
fun PantSaga(id: String, alCerrar: () -> Unit, alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit) {
    val s = Sagas.por(id)
    if (s == null) { LaunchedEffect(Unit) { alCerrar() }; return }
    val juegos = Sagas.juegos(s)
    var editando by remember { mutableStateOf(false) }
    var renombrar by remember { mutableStateOf(false) }
    var borrar by remember { mutableStateOf(false) }
    var anadir by remember { mutableStateOf(false) }
    val siguiente = siguienteDe(juegos)
    fun mover(k: String, d: Int) {
        val l = s.juegos.toMutableList(); val i = l.indexOf(k); val j = i + d
        if (i < 0 || j !in l.indices) return
        l[i] = l[j].also { l[j] = l[i] }; Sagas.poner(s.copy(juegos = l))
    }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        RevistaTopBar(alCerrar, "Volver") { EnlaceRevista(if (editando) "Listo" else "Editar", acento = editando) { editando = !editando } }
        Filete(3.dp)
        Spacer(Modifier.height(14.dp))
        RevistaHeadline(s.nombre, listOfNotNull("Saga", origenesDe(juegos).ifBlank { null }).joinToString(" · "))
        Text(resumenSaga(juegos), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp, bottom = 10.dp))
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            val pasados = juegos.count { it.estado == Estado.PASADO }
            Column(Modifier.weight(1f)) {
                FitTitle("$pasados/${juegos.size}", 44.sp, 46.sp, unaLinea = true, maxLines = 1)
                Text("PASADOS", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            val ms = juegos.sumOf { it.msJugados }
            if (ms >= 60_000) Column(Modifier.weight(1f)) {
                FitTitle(tiempoDe(ms), 44.sp, 46.sp, unaLinea = true, maxLines = 1)
                Text("EN TOTAL", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (siguiente != null) {
            Filete(1.dp)
            Row(Modifier.fillMaxWidth().clickable { alAbrir(siguiente) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (siguiente.estado == Estado.JUGANDO) "AHORA" else "TOCA ESTE", color = Acento, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
                    Text(siguiente.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 24.sp, maxLines = 2)
                }
                if (!siguiente.sinLanzador) Text("JUGAR →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp, modifier = Modifier.clickable { alJugar(siguiente) }.padding(8.dp))
                else Text("ABRIR →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp)
            }
        }
        Filete(2.dp)
        if (juegos.isEmpty()) Text("Esta saga no tiene juegos todavía.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(vertical = 16.dp))
        juegos.forEachIndexed { i, j ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { FilaJuego(j, alAbrir, if (editando) null else alJugar, numero = i + 1, conEstado = true) }
                if (editando) Row {
                    Text("▲", color = if (i > 0) Charcoal else Line, fontSize = 18.sp, modifier = Modifier.clickable(enabled = i > 0) { mover(j.id, -1) }.padding(8.dp))
                    Text("▼", color = if (i < juegos.size - 1) Charcoal else Line, fontSize = 18.sp, modifier = Modifier.clickable(enabled = i < juegos.size - 1) { mover(j.id, 1) }.padding(8.dp))
                    Text("✕", color = Acento, fontSize = 18.sp, modifier = Modifier.clickable { val r = s.juegos - j.id; if (r.isEmpty()) { Sagas.quitar(s.id); alCerrar() } else Sagas.poner(s.copy(juegos = r)) }.padding(8.dp))
                }
            }
            Filete(1.dp, Line)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            EnlaceRevista("Añadir juegos →", acento = true) { anadir = true }
            EnlaceRevista("Renombrar →") { renombrar = true }
        }
        Filete(1.dp, Line)
        MagLink("Borrar la saga", "Los juegos se quedan donde están; solo se deshace el grupo") { borrar = true }
        Spacer(Modifier.height(30.dp))
    }
    if (anadir) SelectorJuegos("Añadir a «${s.nombre}»", Biblioteca.juegos.filter { it.id !in s.juegos }, { anadir = false }) { ids -> anadir = false; Sagas.poner(s.copy(juegos = s.juegos + ids)) }
    if (renombrar) {
        var t by remember { mutableStateOf(s.nombre) }
        AlertDialog(onDismissRequest = { renombrar = false }, containerColor = Paper, shape = esquina(4.dp),
            title = { Text("Nombre de la saga", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
            text = { CampoRevista("Nombre", t, "nombre") { t = it } },
            confirmButton = { BotonTexto("Guardar", true) { if (t.isNotBlank()) { Sagas.poner(s.copy(nombre = t.trim())); renombrar = false } } },
            dismissButton = { BotonTexto("Cancelar") { renombrar = false } })
    }
    if (borrar) AlertDialog(onDismissRequest = { borrar = false }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Borrar «${s.nombre}»?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("Se deshace la saga en todos tus dispositivos. Los ${juegos.size} juegos siguen en tu biblioteca, cada uno en su sección.", color = Muted, fontFamily = Body) },
        confirmButton = { BotonTexto("Borrar", true) { Sagas.quitar(s.id); borrar = false; alCerrar() } },
        dismissButton = { BotonTexto("Cancelar") { borrar = false } })
}

/** Elegir una saga para un juego: una de las que hay, ninguna, o una nueva con el nombre que escribas. */
@Composable
fun DialogoSagaDeJuego(j: Juego, alCerrar: () -> Unit) {
    val actual = Sagas.de(j)
    var nueva by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Saga de «${j.titulo}»", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (Sagas.lista.isEmpty()) Text("Aún no hay sagas. Escribe un nombre para crear la primera.", color = Muted, fontFamily = Body, fontSize = 13.sp)
                Sagas.lista.sortedBy { Catalogo.norm(it.nombre) }.forEach { s ->
                    val on = s.id == actual?.id
                    Text((if (on) "● " else "○ ") + s.nombre, color = if (on) Acento else Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp,
                        modifier = Modifier.fillMaxWidth().clickable { Sagas.asignar(j.id, s); alCerrar() }.padding(vertical = 8.dp))
                }
                if (actual != null) Text("Quitar de su saga", color = Muted, fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().clickable { Sagas.asignar(j.id, null); alCerrar() }.padding(vertical = 10.dp))
                CampoRevista("Nueva", nueva, "nombre de la saga") { nueva = it }
            }
        },
        confirmButton = { BotonTexto("Crear y meterlo", true) { if (nueva.isNotBlank()) { val s = Sagas.crear(nueva); Sagas.asignar(j.id, s); alCerrar() } } },
        dismissButton = { BotonTexto("Cerrar") { alCerrar() } }
    )
}

/** Crear una saga vacía (se abre después para meterle juegos). */
@Composable
fun DialogoNuevaSaga(nombre: String, alCambiar: (String) -> Unit, alCerrar: () -> Unit, alCrear: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Nueva saga", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { CampoRevista("Nombre", nombre, "p. ej. Zelda") { alCambiar(it) } },
        confirmButton = { BotonTexto("Crear", true) { if (nombre.isNotBlank()) alCrear(nombre.trim()) } },
        dismissButton = { BotonTexto("Cerrar") { alCerrar() } }
    )
}

/** Elegir varios juegos de la biblioteca (a pantalla completa, con buscador): devuelve las claves de los marcados. Con [uno], solo uno (y se devuelve al tocarlo). */
@Composable
fun SelectorJuegos(titulo: String, candidatos: List<Juego>, alCerrar: () -> Unit, uno: Boolean = false, alElegir: (List<String>) -> Unit) {
    Dialog(onDismissRequest = alCerrar, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var marcados by remember { mutableStateOf(listOf<String>()) }
        var busca by remember { mutableStateOf("") }
        val q = Catalogo.norm(busca)
        val lista = candidatos.filter { q.isEmpty() || Catalogo.norm(it.titulo).contains(q) || Catalogo.norm(etiquetaDe(it)).contains(q) }.sortedBy { Catalogo.norm(it.titulo) }
        Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            RevistaTopBar(alCerrar, "Cerrar") { if (!uno) EnlaceRevista(if (marcados.isEmpty()) "Elige" else "Añadir ${marcados.size} →", acento = marcados.isNotEmpty()) { if (marcados.isNotEmpty()) alElegir(marcados) } }
            Filete(3.dp)
            Text(titulo.uppercase(), color = Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, modifier = Modifier.padding(top = 12.dp))
            CampoRevista("Buscar", busca, "título o sistema") { busca = it }
            LazyColumn(Modifier.fillMaxSize()) {
                if (lista.isEmpty()) item { Text("No hay más juegos para elegir.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 17.sp, modifier = Modifier.padding(top = 20.dp)) }
                items(lista, key = { it.id }) { j ->
                    val on = j.id in marcados
                    Row(Modifier.fillMaxWidth().clickable { if (uno) alElegir(listOf(j.id)) else marcados = if (on) marcados - j.id else marcados + j.id }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!uno) Text(if (on) "■" else "□", color = if (on) Acento else Muted, fontSize = 22.sp, modifier = Modifier.width(34.dp))
                        Box(Modifier.width(40.dp).aspectRatio(PROPORCION_PORTADA).clip(esquina(3.dp))) { PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 18) }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(j.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, maxLines = 2)
                            Text(metaJuego(j, conEstado = true).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                    Filete(1.dp, Line)
                }
            }
        }
    }
}
