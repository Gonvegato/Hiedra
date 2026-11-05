package com.gonzalo.hiedra

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Lo jugado cada día (ms), sumando todos los juegos y todos tus dispositivos. */
fun diasJugados(juegos: List<Juego>): Map<String, Long> {
    val out = HashMap<String, Long>()
    for (j in juegos) for ((k, v) in j.dias()) out[k] = (out[k] ?: 0L) + v
    return out
}

/** Días seguidos jugando, con la regla común de la familia ([Racha]: al menos un minuto; hasta hoy o hasta ayer). */
fun rachaDeJuego(dias: Map<String, Long>, hoy: LocalDate = LocalDate.now()): Int =
    Racha.dias(hoy.toEpochDay()) { Racha.cuentaTiempo(dias[LocalDate.ofEpochDay(it).toString()] ?: 0L) }

/**
 * Entre la fecha y el menú, como en la portada de Atrio: lo jugado hoy, los días seguidos y la semana en barras (cada día, lo que jugaste).
 * Las medidas y las letras son las de Atrio.
 */
@Composable
fun BarrasJuego(juegos: List<Juego>) {
    val dias = remember(juegos.map { it.id to it.msJugados to it.ultima }) { diasJugados(juegos) }
    val hoy = LocalDate.now()
    val hoyMin = (dias[hoy.toString()] ?: 0L) / 60_000L
    val racha = rachaDeJuego(dias)
    val semana = (6 downTo 0).map { hoy.minusDays(it.toLong()) }.map { d -> d to (dias[d.toString()] ?: 0L) / 60_000L }
    val algo = semana.any { it.second >= 1 }
    if (!algo && racha < 2) return
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Acento)); Spacer(Modifier.width(8.dp))
            Text(
                listOfNotNull("JUEGO", if (hoyMin >= 1) "${tiempoDe(hoyMin * 60_000L).uppercase()} HOY" else null, if (racha >= 2) "$racha DÍAS SEGUIDOS" else null).joinToString(" · "),
                fontFamily = Body, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = Charcoal
            )
        }
        if (algo) {
            val tope = maxOf(semana.maxOf { it.second }, 1L).toFloat()
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                semana.forEach { (d, m) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth(0.62f).height(30.dp), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.fillMaxWidth().height(if (m >= 1) maxOf(3f, 30f * m / tope).dp else 1.dp).background(if (m >= 1) Charcoal else Line))
                        }
                        Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale("es", "ES")).uppercase(), fontFamily = Body, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            color = if (d == hoy) Acento else Muted, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
