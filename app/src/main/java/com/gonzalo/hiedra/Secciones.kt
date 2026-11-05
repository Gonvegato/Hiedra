package com.gonzalo.hiedra

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/*
 * Las secciones de Hiedra, como las de Marfil y las de Atrio: páginas por las que te mueves con el dedo o con las pestañas
 * (Jugando · Pendiente · Pasado · Abandonado · Sagas). Dentro de cada estado, los menús pequeños dicen de dónde es el juego
 * (PC · Emulación · Android · Web): un toque filtra, otro toque en el activo lo quita. No hay «Todos»: la sección entera es todo.
 */

/** El orden con el que empieza cada sección: lo pendiente, por lo último añadido; lo pasado, por la nota; el resto, por lo último jugado. */
fun ordenInicial(e: Estado): Orden = when (e) { Estado.PENDIENTE -> Orden.Anadidos; Estado.PASADO -> Orden.Pasados; else -> Orden.Recientes }

/** Ordena una lista de juegos. */
fun ordenar(o: Orden, l: List<Juego>): List<Juego> = when (o) {
    Orden.Recientes -> recientes(l)
    Orden.Nombre -> l.sortedBy { Catalogo.norm(it.titulo) }
    Orden.Jugados -> l.sortedWith(compareByDescending<Juego> { it.msJugados }.thenByDescending { it.veces })
    Orden.Anadidos -> l.sortedByDescending { it.anadido }
    Orden.Pasados -> l.sortedWith(compareByDescending<Juego> { it.finalizado }.thenByDescending { it.ultima })
}

/** El orden que has elegido para cada sección (se recuerda al cerrar la app). */
object OrdenGuardado {
    private fun prefs(c: Context) = c.getSharedPreferences("hiedra_orden", Context.MODE_PRIVATE)
    fun de(c: Context, e: Estado): Orden = (Orden.entries.firstOrNull { it.name == prefs(c).getString(e.name, null) } ?: ordenInicial(e)).let { if (it == Orden.Pasados && e != Estado.PASADO) ordenInicial(e) else it }
    fun pon(c: Context, e: Estado, o: Orden) { prefs(c).edit().putString(e.name, o.name).apply() }
}

/** Lo que dice debajo del título de un juego: de dónde es, sus horas y, si se pide, su estado. */
fun metaJuego(j: Juego, conEstado: Boolean = false): String = listOf(
    etiquetaDe(j),
    if (j.msJugados >= 60_000) tiempoDe(j.msJugados) else "",
    if (conEstado && j.estado != Estado.PENDIENTE) j.estado.nombre.lowercase() else "",
    if (j.enOtroDispositivo) "en otro dispositivo" else ""
).filter { it.isNotBlank() }.joinToString(" · ")

/**
 * Un juego en una lista: su portada, el título y de dónde es a la izquierda; a la derecha, su nota (si la tiene) y «Jugar →» (si se abre desde el móvil).
 * [numero]: el que tiene dentro de su saga.
 */
@Composable
fun FilaJuego(j: Juego, alAbrir: (Juego) -> Unit, alJugar: ((Juego) -> Unit)? = null, modifier: Modifier = Modifier, portada: Dp = 54.dp, numero: Int = 0, conEstado: Boolean = false, extra: String = "") {
    Row(modifier.fillMaxWidth().clickable { alAbrir(j) }.padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (numero > 0) Text("$numero", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, modifier = Modifier.width(26.dp))
        Box(Modifier.width(portada).aspectRatio(PROPORCION_PORTADA).clip(esquina(4.dp))) { PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 26) }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(j.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = if (portada >= 54.dp) 20.sp else 18.sp, lineHeight = if (portada >= 54.dp) 22.sp else 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOf(metaJuego(j, conEstado), extra).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta.uppercase(), color = Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, maxLines = 2, lineHeight = 15.sp, modifier = Modifier.padding(top = 3.dp))
        }
        if (alJugar != null && !j.sinLanzador) Text(if (j.enPc) "PC →" else "JUGAR →", color = Acento, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1,
            modifier = Modifier.clickable { alJugar(j) }.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
    }
}

/** El abanico de portadas de una saga (hasta tres, una sobre otra). */
@Composable
fun AbanicoSaga(juegos: List<Juego>, modifier: Modifier = Modifier) {
    val v = juegos.take(3)
    Box(modifier.width(38.dp + 14.dp * (v.size - 1).coerceAtLeast(0)).height(52.dp)) {
        v.asReversed().forEachIndexed { i, j ->
            val k = v.size - 1 - i
            Box(Modifier.padding(start = 14.dp * k).width(38.dp).height(52.dp).clip(esquina(3.dp))) { PortadaArco(j, Modifier.matchParentSize(), titulo = false, letra = true, relleno = true, tamLetra = 18) }
        }
    }
}

/** «3 juegos · 1 pasado · jugando»: lo que dice una saga. */
fun resumenSaga(todos: List<Juego>): String {
    val pasados = todos.count { it.estado == Estado.PASADO }
    return listOfNotNull(
        "${todos.size} ${if (todos.size == 1) "juego" else "juegos"}",
        if (pasados > 0) "$pasados ${if (pasados == 1) "pasado" else "pasados"}" else null,
        if (todos.any { it.estado == Estado.JUGANDO }) "jugando" else null
    ).joinToString(" · ")
}

/** Una saga en una lista: el abanico, su nombre y cuántos hay; un toque la abre y enseña sus juegos; «Ver la saga →» va a su página. */
@Composable
fun FilaSaga(f: FilaLista.DeSaga, abierta: Boolean, alAlternar: () -> Unit, alAbrirSaga: (Saga) -> Unit, alAbrirJuego: (Juego) -> Unit, alJugar: ((Juego) -> Unit)? = null) {
    val todos = remember(f.saga, Biblioteca.juegos.size) { Sagas.juegos(f.saga) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable(onClick = alAlternar).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AbanicoSaga(f.miembros)
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 8.dp)) {
                Text(f.saga.nombre, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(("Saga · " + resumenSaga(todos)).uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
            }
            Text(if (abierta) "−" else "+", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        if (abierta) {
            Column(Modifier.fillMaxWidth().padding(start = 10.dp)) {
                f.miembros.forEach { j ->
                    Filete(1.dp, Line)
                    FilaJuego(j, alAbrirJuego, alJugar, portada = 46.dp, numero = f.saga.juegos.indexOf(j.id) + 1)
                }
                EnlaceRevista("Ver la saga →", acento = true) { alAbrirSaga(f.saga) }
            }
        }
    }
    Filete(1.dp, Line)
}

/** Las filas (juegos sueltos y sagas) de una lista de juegos, con las sagas que estén abiertas. */
private fun LazyListScope.filas(
    juegos: List<Juego>, abiertas: Set<String>, alternar: (String) -> Unit, alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit, alAbrirSaga: (Saga) -> Unit, conEstado: Boolean = false
) {
    val filas = Sagas.agrupar(juegos)
    items(filas, key = { when (it) { is FilaLista.Suelto -> "j:" + it.juego.id; is FilaLista.DeSaga -> "s:" + it.saga.id } }) { f ->
        when (f) {
            is FilaLista.Suelto -> { FilaJuego(f.juego, alAbrir, alJugar, conEstado = conEstado, extra = f.saga?.let { "Saga ${it.nombre} · ${f.numero}" }.orEmpty()); Filete(1.dp, Line) }
            is FilaLista.DeSaga -> FilaSaga(f, f.saga.id in abiertas, { alternar(f.saga.id) }, alAbrirSaga, alAbrir, alJugar)
        }
    }
}

private fun vacioDe(e: Estado, m: Menu?) = when {
    m != null -> "Nada de ${m.nombre} aquí. Toca «${m.nombre.uppercase()}» otra vez para ver toda la sección."
    e == Estado.JUGANDO -> "No estás jugando a nada. Abre un juego o márcalo «Jugando» en su ficha y saldrá aquí."
    e == Estado.PENDIENTE -> "No tienes nada pendiente: lo que añadas sin empezar saldrá aquí."
    e == Estado.PASADO -> "Aún no has pasado ninguno. Cuando lo termines, márcalo en su ficha y saldrá aquí."
    else -> "No has abandonado ninguno. Si dejas un juego, márcalo como abandonado desde su ficha."
}

/** Los menús pequeños (PC · Emulación · Android · Web) con sus cuentas: el activo lleva el subrayado de acento; tocarlo otra vez lo quita. */
@Composable
fun MenusPequenos(sel: Menu?, cuentas: Map<Menu, Int>, onSel: (Menu?) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
            Menu.entries.forEach { m ->
                val on = m == sel
                Row(Modifier.clickable { onSel(if (on) null else m) }
                    .drawBehind { if (on) drawRect(Acento, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx())) }
                    .padding(top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(m.nombre.uppercase(), color = if (on) Charcoal else Muted, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, maxLines = 1)
                    Text(" ${cuentas[m] ?: 0}", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
        Filete(1.dp, Line)
    }
}

/** Una página de una sección: título enorme, los menús pequeños, el orden y sus juegos (con las sagas juntas). */
@Composable
private fun PaginaEstante(estado: Estado, todos: List<Juego>, menu: Menu?, alMenu: (Menu?) -> Unit, alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit, alAbrirSaga: (Saga) -> Unit, alAnadir: () -> Unit) {
    val ctx = LocalContext.current
    var orden by remember(estado) { mutableStateOf(OrdenGuardado.de(ctx, estado)) }
    var sistema by rememberSaveable(estado) { mutableStateOf<String?>(null) }
    val delEstado = remember(todos, estado) { todos.filter { it.estado == estado } }
    val cuentas = remember(delEstado) { Menu.entries.associateWith { m -> delEstado.count { m in it.menus } } }
    val sistemas = remember(delEstado) { delEstado.filter { it.rom != null }.groupBy { it.sistema }.entries.sortedByDescending { it.value.size } }
    val juegos = remember(delEstado, menu, orden, sistema) { ordenar(orden, delEstado.filter { (menu == null || menu in it.menus) && (sistema == null || it.sistema == sistema) }) }
    var abiertas by rememberSaveable(estado) { mutableStateOf(setOf<String>()) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo("Sección · ${delEstado.size} ${if (delEstado.size == 1) "juego" else "juegos"}", Modifier.padding(top = 12.dp), color = Acento, conCuadro = false)
                FitTitle(if (estado == Estado.JUGANDO) "Jugando" else estado.nombre + "s", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(1.dp, Line)
                MenusPequenos(menu, cuentas) { sistema = null; alMenu(it) }
                // Cómo se ordena esta sección (se recuerda)
                // «Cuándo lo pasaste», solo en Pasados (en las demás secciones no tiene sentido)
                val ordenes = Orden.entries.filter { it != Orden.Pasados || estado == Estado.PASADO }
                FiltrosRevista(ordenes.map { it.nombre }, ordenes.indexOf(orden).coerceAtLeast(0), Modifier.padding(top = 4.dp)) { i -> orden = ordenes[i]; OrdenGuardado.pon(ctx, estado, orden) }
                // En Emulación, también por sistema
                if (menu == Menu.EMULACION && sistemas.size > 1) FiltrosRevista(listOf("Todos") + sistemas.map { (Catalogo.sistema(it.key)?.corto ?: it.key) + " " + it.value.size }, sistemas.indexOfFirst { it.key == sistema } + 1) { i ->
                    sistema = if (i == 0) null else sistemas[i - 1].key
                }
                Filete(1.dp, Line)
            }
        }
        if (juegos.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(top = 30.dp)) {
                Text(if (todos.isEmpty()) "Todavía no hay nada aquí." else vacioDe(estado, menu), color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp)
                if (todos.isEmpty()) MagLink("Añadir juegos", accent = true, onClick = alAnadir)
            }
        }
        filas(juegos, abiertas, { id -> abiertas = if (id in abiertas) abiertas - id else abiertas + id }, alAbrir, alJugar, alAbrirSaga)
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Todas las sagas, por nombre: cada una con su abanico y sus juegos desplegables; desde aquí se crea una nueva (y se abre para meterle juegos). */
@Composable
private fun PaginaSagas(todos: List<Juego>, alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit, alAbrirSaga: (Saga) -> Unit) {
    val sagas = Sagas.lista.toList()
    val filas = remember(sagas, todos) { sagas.sortedBy { Catalogo.norm(it.nombre) }.map { FilaLista.DeSaga(it, Sagas.juegos(it, todos)) } }
    var abiertas by rememberSaveable { mutableStateOf(setOf<String>()) }
    var nueva by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column {
                Antetitulo("Sección · ${sagas.size} ${if (sagas.size == 1) "saga" else "sagas"}", Modifier.padding(top = 12.dp), color = Acento, conCuadro = false)
                FitTitle("Sagas", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(1.dp, Line)
                EnlaceRevista("+ Nueva saga", Modifier.padding(vertical = 4.dp), acento = true) { nueva = "" }
                Filete(1.dp, Line)
            }
        }
        if (filas.isEmpty()) item {
            Text("Aún no hay sagas. Créala aquí o desde la ficha de un juego («Saga»), y luego ve metiendo sus juegos en orden.",
                color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 30.dp))
        }
        items(filas, key = { "s:" + it.saga.id }) { f ->
            FilaSaga(f, f.saga.id in abiertas, { abiertas = if (f.saga.id in abiertas) abiertas - f.saga.id else abiertas + f.saga.id }, alAbrirSaga, alAbrir, alJugar)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
    nueva?.let { n ->
        DialogoNuevaSaga(n, alCambiar = { nueva = it }, alCerrar = { nueva = null }) { nombre -> nueva = null; alAbrirSaga(Sagas.crear(nombre)) }
    }
}

/** Lo que sale al buscar: todos los juegos que cuadran, estén en la sección que estén. */
@Composable
private fun Resultados(busca: String, todos: List<Juego>, alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit) {
    val q = Catalogo.norm(busca)
    val l = remember(busca, todos) {
        todos.filter { Catalogo.norm(it.titulo).contains(q) || Catalogo.norm(etiquetaDe(it)).contains(q) || Sagas.de(it)?.let { s -> Catalogo.norm(s.nombre).contains(q) } == true }
            .sortedWith(compareByDescending<Juego> { it.ultima }.thenByDescending { it.anadido })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item { Antetitulo("Resultados · ${l.size}", Modifier.padding(top = 14.dp, bottom = 6.dp), color = Acento, conCuadro = false); Filete(1.dp, Line) }
        if (l.isEmpty()) item { Text("Nada con «$busca».", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp, modifier = Modifier.padding(top = 24.dp)) }
        items(l, key = { it.id }) { x -> FilaJuego(x, alAbrir, alJugar, conEstado = true); Filete(1.dp, Line) }
    }
}

/**
 * Las secciones como páginas por las que te mueves (con el dedo o con las pestañas): Jugando, Pendiente, Pasado, Abandonado y Sagas.
 * Buscar mira en todas a la vez.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantSecciones(inicial: Destino, alVolver: () -> Unit, alAbrir: (Juego) -> Unit, alJugar: (Juego) -> Unit, alAbrirSaga: (Saga) -> Unit, alAnadir: () -> Unit, buscandoInicial: Boolean = false) {
    val paginas = listOf(Destino.Jugando, Destino.Pendiente, Destino.Pasado, Destino.Abandonado, Destino.Sagas)
    val pager = rememberPagerState(initialPage = paginas.indexOf(inicial).coerceAtLeast(0)) { paginas.size }
    val ambito = rememberCoroutineScope()
    var buscando by rememberSaveable { mutableStateOf(buscandoInicial) }
    var busca by rememberSaveable { mutableStateOf("") }
    // El menú pequeño elegido se mantiene al pasar de una sección a otra
    var menu by rememberSaveable { mutableStateOf<Menu?>(null) }
    val todos = Biblioteca.juegos.toList()
    val cuentas = listOf(Estado.JUGANDO, Estado.PENDIENTE, Estado.PASADO, Estado.ABANDONADO).map { e -> todos.count { it.estado == e } } + Sagas.lista.size
    val foco = remember { FocusRequester() }
    LaunchedEffect(buscando) { if (buscando) runCatching { foco.requestFocus() } }
    Column(Modifier.fillMaxSize().background(Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp)) {
        RevistaTopBar(alVolver, "Hiedra") {
            EnlaceRevista("Buscar", acento = buscando) { buscando = !buscando; if (!buscando) busca = "" }
            EnlaceRevista("+ Añadir", acento = true) { alAnadir() }
        }
        Filete(3.dp)
        if (buscando) CampoBusquedaRevista(busca, "título, sistema o saga", foco) { busca = it }
        PestanasPaginas(listOf("Jugando", "Pendientes", "Pasados", "Abandonados", "Sagas"), pager.currentPage, cuentas) { i -> ambito.launch { pager.animateScrollToPage(i) } }
        if (busca.isNotBlank()) Resultados(busca, todos, alAbrir, alJugar)
        // (sin páginas vecinas precompuestas: con ellas, en las páginas que no son la primera los enlaces de la cabecera no recibían el toque)
        else HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { paginas[it].name }) { i ->
            when (paginas[i]) {
                Destino.Jugando -> PaginaEstante(Estado.JUGANDO, todos, menu, { menu = it }, alAbrir, alJugar, alAbrirSaga, alAnadir)
                Destino.Pendiente -> PaginaEstante(Estado.PENDIENTE, todos, menu, { menu = it }, alAbrir, alJugar, alAbrirSaga, alAnadir)
                Destino.Pasado -> PaginaEstante(Estado.PASADO, todos, menu, { menu = it }, alAbrir, alJugar, alAbrirSaga, alAnadir)
                Destino.Abandonado -> PaginaEstante(Estado.ABANDONADO, todos, menu, { menu = it }, alAbrir, alJugar, alAbrirSaga, alAnadir)
                else -> PaginaSagas(todos, alAbrir, alJugar, alAbrirSaga)
            }
        }
    }
}

/** Las pestañas de las secciones (como las de Atrio): la elegida en tinta con el subrayado de acento; su número, en acento. Si no caben, se deslizan y la elegida siempre se ve. */
@Composable
private fun PestanasPaginas(nombres: List<String>, sel: Int, cuentas: List<Int>, onSel: (Int) -> Unit) {
    // La fila se desplaza sola solo si la pestaña elegida no se ve entera (y entonces la centra): así no se corta la de al lado
    val scroll = rememberScrollState()
    val sitios = remember { mutableStateMapOf<Int, Pair<Int, Int>>() }   // pestaña → (x, ancho), en coordenadas del contenido
    var anchoFila by remember { mutableIntStateOf(0) }
    LaunchedEffect(sel, sitios[sel], anchoFila) {
        val (x, w) = sitios[sel] ?: return@LaunchedEffect
        if (anchoFila <= 0) return@LaunchedEffect
        if (x < scroll.value || x + w > scroll.value + anchoFila) scroll.animateScrollTo((x + w / 2 - anchoFila / 2).coerceIn(0, scroll.maxValue))
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().onGloballyPositioned { anchoFila = it.size.width }.drawWithContent {
                // Un degradado en los bordes cuando hay más pestañas por un lado o por otro (para que no parezcan cortadas)
                drawContent()
                val f = 28.dp.toPx()
                if (scroll.value > 0) drawRect(Brush.horizontalGradient(listOf(Paper, Color.Transparent), 0f, f), Offset.Zero, Size(f, size.height))
                if (scroll.value < scroll.maxValue) drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Paper), size.width - f, size.width), Offset(size.width - f, 0f), Size(f, size.height))
            }.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.Bottom) {
            nombres.forEachIndexed { i, n ->
                val on = i == sel
                Row(Modifier.onGloballyPositioned { c -> sitios[i] = (c.positionInParent().x + scroll.value).roundToInt() to c.size.width }.clickable { onSel(i) }
                    .drawBehind { if (on) drawRect(Acento, topLeft = Offset(0f, size.height - 3.dp.toPx()), size = Size(size.width, 3.dp.toPx())) }
                    .padding(top = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(n.uppercase(), color = if (on) Charcoal else Muted, fontFamily = Body, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp, maxLines = 1)
                    Text(" ${cuentas.getOrElse(i) { 0 }}", color = Acento, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                }
            }
        }
        Filete(1.dp, Line)
    }
}
