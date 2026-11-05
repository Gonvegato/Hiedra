package com.gonzalo.hiedra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun arriba(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp

/** Estado de un sistema: cuántos juegos tiene y con qué emulador se abren. */
private class EstadoSistema(val s: Sistema, val juegos: Int, val emu: String, val listo: Boolean)

/** «Sistemas»: cada consola con el emulador que se usa, y las carpetas de ROMs. */
@Composable
fun PantSistemas(alVolver: () -> Unit, alAnadirCarpeta: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val todos = Biblioteca.juegos.toList()
    val conteo = todos.filter { it.rom != null }.groupBy { it.sistema }.mapValues { it.value.size }
    var verTodos by remember { mutableStateOf(false) }
    var busca by remember { mutableStateOf("") }
    var abierto by remember { mutableStateOf<String?>(null) }
    var escaneando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val lista = remember(conteo, verTodos, busca, abierto) {
        Catalogo.sistemas.filter { s -> (verTodos || busca.isNotBlank() || (conteo[s.id] ?: 0) > 0) && (busca.isBlank() || Catalogo.norm(s.nombre).contains(Catalogo.norm(busca)) || s.id == Catalogo.norm(busca)) }
            .map { s ->
                val e = Lanzar.elegirSistema(ctx, s.id)
                EstadoSistema(s, conteo[s.id] ?: 0, if (e == null) "Sin comandos" else if (e.resultado is Resultado.Ok) e.etiqueta else "Sin emulador instalado", e?.resultado is Resultado.Ok)
            }
    }
    LazyColumn(Modifier.fillMaxSize().background(Paper).statusBarsPadding(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp)) {
        item {
            Column {
                RevistaTopBar(alVolver, "Hiedra") { EnlaceRevista("+ Carpeta", acento = true, onClick = alAnadirCarpeta) }
                Antetitulo("Sección · ${Catalogo.sistemas.size} sistemas", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
                FitTitle("Sistemas", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
                Filete(3.dp)
                Text("Cada consola, con el emulador que tú elijas. La Game Boy Advance se juega en la propia Hiedra; para las demás, Hiedra abre los emuladores que ya tienes instalados.", color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(vertical = 12.dp))
                Filete(2.dp)
                Spacer(Modifier.height(12.dp))
                Antetitulo("Carpetas de ROMs", Modifier.padding(bottom = 6.dp))
                if (Biblioteca.carpetas.isEmpty()) Text("Todavía no has añadido ninguna. Añade la carpeta donde guardas tus ROMs y Hiedra la recorre.", color = Muted, fontFamily = Body, fontSize = 14.sp, lineHeight = 20.sp)
                else Biblioteca.carpetas.forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c.nombre, color = Charcoal, fontFamily = Display, fontSize = 19.sp, modifier = Modifier.weight(1f))
                        Text("QUITAR", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp, modifier = Modifier.clickable { Biblioteca.carpetas.remove(c); Biblioteca.guardar() }.padding(8.dp))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    EnlaceRevista("Añadir carpeta →", acento = true, onClick = alAnadirCarpeta)
                    if (Biblioteca.carpetas.isNotEmpty()) {
                        if (escaneando) CircularProgressIndicator(Modifier.size(20.dp), color = Acento, strokeWidth = 2.dp)
                        else EnlaceRevista("Buscar nuevos →") {
                            escaneando = true
                            scope.launch { val i = reescanearCarpetas(ctx); escaneando = false; aviso = if (i.anadidos == 0) "No hay juegos nuevos" else "${i.anadidos} juegos nuevos" }
                        }
                    }
                }
                aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp) }
                Spacer(Modifier.height(10.dp))
                Filete(2.dp)
                CampoBusquedaRevista(busca, "Buscar un sistema") { busca = it }
                Filete(1.dp, Line)
            }
        }
        items(lista, key = { it.s.id }) { e ->
            FilaRevista(e.s.nombre, "${if (e.juegos == 0) "sin juegos" else if (e.juegos == 1) "1 juego" else "${e.juegos} juegos"} · ${e.emu}",
                derecha = { Text(if (e.listo) "LISTO" else "FALTA", color = if (e.listo) Charcoal else Acento, fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, modifier = Modifier.padding(start = 10.dp)) }) { abierto = e.s.id }
        }
        item {
            if (busca.isBlank()) Text((if (verTodos) "Ver solo los míos" else "Ver los ${Catalogo.sistemas.size} sistemas").uppercase() + " →", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp,
                modifier = Modifier.clickable { verTodos = !verTodos }.padding(vertical = 18.dp))
        }
    }
    abierto?.let { SelectorEmulador(it, null) { abierto = null } }
}
