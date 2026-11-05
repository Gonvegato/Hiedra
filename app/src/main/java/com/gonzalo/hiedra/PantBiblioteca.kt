package com.gonzalo.hiedra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Orden(val nombre: String) { Recientes("Recientes"), Nombre("Nombre"), Jugados("Más jugados"), Anadidos("Añadidos"), Pasados("Cuándo lo pasaste") }

/** La casilla de búsqueda de la revista: «Buscar» en versalitas y el texto en cursiva sobre un filete. */
@Composable
fun CampoBusquedaRevista(valor: String, pista: String, foco: FocusRequester? = null, alCambiar: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text("BUSCAR", color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(end = 10.dp, bottom = 3.dp))
            Box(Modifier.weight(1f)) {
                if (valor.isEmpty()) Text(pista, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
                BasicTextField(valor, alCambiar, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp), cursorBrush = SolidColor(Acento),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), modifier = Modifier.fillMaxWidth().let { if (foco != null) it.focusRequester(foco) else it })
            }
            if (valor.isNotEmpty()) Text("✕", color = Muted, fontSize = 16.sp, modifier = Modifier.clickable { alCambiar("") }.padding(start = 8.dp))
        }
        Filete(2.dp)
    }
}

/** Un campo de búsqueda suelto (para los diálogos): el mismo, con otro nombre. */
@Composable
fun CampoBusqueda(valor: String, alCambiar: (String) -> Unit) = CampoBusquedaRevista(valor, "Buscar", null, alCambiar)
