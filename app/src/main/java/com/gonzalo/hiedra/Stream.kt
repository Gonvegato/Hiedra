package com.gonzalo.hiedra

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Jugar en streaming con Steam Link (la app gratuita de Valve, en Google Play): Atrio del PC abre Steam (Steam Link no conecta con un PC sin
 * Steam abierto) y el juego, y aquí se abre Steam Link. Valve no documenta cómo decirle a Steam Link a qué PC o a qué juego ir: se abre en su
 * pantalla de inicio, con el PC que tenga emparejado. Copiado de Atrio (Stream.kt).
 */
object Stream {
    const val STEAM_LINK = "com.valvesoftware.steamlink"
    /** Lo que devuelve [play] cuando falta Steam Link en este móvil. */
    const val FALTA = "STEAM_LINK"

    fun installed(ctx: Context): Boolean = ctx.packageManager.getLaunchIntentForPackage(STEAM_LINK) != null

    fun install(ctx: Context) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$STEAM_LINK")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$STEAM_LINK")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    /** Abre Steam Link (en su pantalla de inicio). */
    fun open(ctx: Context): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(STEAM_LINK) ?: return false
        return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }

    /** El PC al que se manda el streaming: el conectado, o (para encenderlo) el último que tuvo Steam. */
    fun host(): LiveDev? = Suite.target() ?: Suite.pcs().filter { it.st?.on == true }.maxByOrNull { it.at }

    /** Prepara el PC (Steam abierto y, si se da, el juego lanzado) y abre Steam Link. Devuelve un mensaje si algo falla ([FALTA] si no hay Steam Link). */
    suspend fun play(ctx: Context, key: String?): String? {
        if (!installed(ctx)) return FALTA
        val pc = Suite.target() ?: return "El PC no está conectado. Enciéndelo (o abre Atrio en él) y vuelve a probar."
        val st = pc.st
        if (st != null && !st.on) return "${pc.name} no tiene Steam: Steam Link necesita Steam abierto en el PC."
        val r = Suite.sendRaw("stream", JSONObject().apply { if (key != null) put("key", key) }, pc.dev, 75_000)
        if (!r.optBoolean("ok")) return r.optString("msg")
        if (key != null) delay(1500)
        return if (open(ctx)) null else "No se ha podido abrir Steam Link."
    }

    /** Encender el PC por red (Wake-on-LAN): solo en la misma red de casa y con la opción activada en la BIOS. */
    suspend fun wake(h: StreamHost): Boolean = withContext(Dispatchers.IO) {
        val macs = h.macs.mapNotNull { m -> m.split(':', '-').takeIf { it.size == 6 }?.map { it.toInt(16).toByte() } }
        if (macs.isEmpty()) return@withContext false
        val targets = (listOf("255.255.255.255") + h.lan.mapNotNull { ip -> ip.split('.').takeIf { it.size == 4 }?.let { "${it[0]}.${it[1]}.${it[2]}.255" } }).distinct()
        var sent = false
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                for (mac in macs) {
                    val pkt = ByteArray(6) { 0xFF.toByte() } + (0 until 16).flatMap { mac }.toByteArray()
                    for (t in targets) for (port in listOf(9, 7)) {
                        try { s.send(DatagramPacket(pkt, pkt.size, InetAddress.getByName(t), port)); sent = true } catch (e: Exception) { }
                    }
                }
            }
        } catch (e: Exception) { }
        sent
    }
}

private fun toast(ctx: Context, m: String) = Toast.makeText(ctx, m, Toast.LENGTH_LONG).show()

@Composable
private fun BotonDialogo(texto: String, acento: Boolean = false, onClick: () -> Unit) =
    TextButton(onClick) { Text(texto.uppercase(), color = if (acento) Acento else Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) }

/**
 * Las dos maneras de jugar a un juego del PC: abrirlo en el PC o jugarlo en este móvil en streaming (Steam Link). Dos líneas, como el resto de la revista.
 * [key]: clave del juego en la biblioteca del PC. [alHacer]: después de mandar la orden (con el mensaje del PC).
 */
@Composable
fun BotonesJugarPc(key: String, modifier: Modifier = Modifier, alHacer: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pc = Suite.target()
    val any = Stream.host()
    val wol = any?.st?.takeIf { it.macs.isNotEmpty() }
    var busyPc by remember { mutableStateOf(false) }
    var busySt by remember { mutableStateOf(false) }
    var falta by remember { mutableStateOf(false) }
    var waking by remember { mutableStateOf(false) }
    val running = pc?.game?.key == key
    fun launch() {
        busyPc = true
        scope.launch { val (_, m) = Suite.send("launch", JSONObject().put("key", key), pc?.dev); busyPc = false; toast(ctx, m); alHacer(m) }
    }
    fun stream() {
        if (!Stream.installed(ctx)) { falta = true; return }
        if (pc == null) { if (wol != null) waking = true else toast(ctx, "El PC no está conectado: enciéndelo o abre Atrio en él."); return }
        busySt = true
        scope.launch {
            val err = Stream.play(ctx, if (running) null else key)
            busySt = false
            if (err == Stream.FALTA) falta = true else if (err != null) toast(ctx, err) else alHacer("Streaming")
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val canPc = pc != null && !running
        Column(Modifier.fillMaxWidth().clickable(enabled = canPc && !busyPc) { launch() }) {
            Text(if (busyPc) "Abriendo…" else if (running) "En marcha" else "Jugar en el PC →", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (canPc || running) Charcoal else Muted)
            Text((pc?.let { if (running) "Ya en ${it.name}" else "En ${it.name}" } ?: "PC apagado").uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Column(Modifier.fillMaxWidth().clickable(enabled = !busySt) { stream() }) {
            Text(if (busySt) "Conectando…" else "Streaming →", fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Acento)
            Text((if (running) "Seguir en el móvil, con Steam Link" else "En este móvil, con Steam Link").uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
    if (falta) SteamLinkDialog { falta = false }
    if (waking && wol != null) WakeDialog(any?.name ?: "el PC", wol) { waking = false }
}

/** Falta Steam Link en este móvil: qué es y dónde se instala. */
@Composable
fun SteamLinkDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Falta Steam Link", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text("El streaming va con Steam Link, la app gratuita de Valve (está en Google Play). Instálala, ábrela y elige tu PC: te enseña un PIN que se escribe en Steam del PC. Desde entonces, «Streaming» en cualquier juego.", color = Muted, fontFamily = Body) },
        confirmButton = { BotonDialogo("Instalar Steam Link", true) { onDismiss(); Stream.install(ctx) } },
        dismissButton = { BotonDialogo("Ahora no") { onDismiss() } }
    )
}

/** Encender el PC por red (Wake-on-LAN): con el móvil en la wifi de casa y la opción activada en la BIOS. */
@Composable
fun WakeDialog(pcName: String, h: StreamHost, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("¿Encender $pcName?", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Por red (Wake-on-LAN): solo con el móvil en la wifi de casa y la opción activada en la BIOS.", color = Muted, fontFamily = Body)
                if (msg.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(msg, color = Charcoal, fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
            }
        },
        confirmButton = { BotonDialogo("Encender", true) { scope.launch { msg = if (Stream.wake(h)) "Señal enviada. Cuando arranque y se abra Atrio, vuelve a tocar «Streaming»." else "No se ha podido mandar la señal." } } },
        dismissButton = { BotonDialogo("Cerrar") { onDismiss() } }
    )
}

@Composable
private fun LineaEstado(ok: Boolean, texto: String) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).background(if (ok) Acento else Line, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(texto, color = Charcoal, fontFamily = Body, fontSize = 14.sp)
    }
}

/** El panel «Streaming» de «Tu PC»: Steam Link en este móvil, Steam en el PC, emparejar y encender el PC por red. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PanelStreaming() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pc = Suite.target()
    val any = Stream.host()
    val h = (pc ?: any)?.st
    val wol = any?.st?.takeIf { it.macs.isNotEmpty() }
    var link by remember { mutableStateOf(Stream.installed(ctx)) }
    var waking by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var falta by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { link = Stream.installed(ctx); delay(4000) } }
    fun abrir() {
        if (!link) { falta = true; return }
        if (pc == null || h?.on != true) { Stream.open(ctx); return }
        busy = true
        scope.launch { val err = Stream.play(ctx, null); busy = false; if (err == Stream.FALTA) falta = true else if (err != null) toast(ctx, err) }
    }
    Column(Modifier.fillMaxWidth()) {
        Antetitulo("Streaming", Modifier.padding(bottom = 4.dp))
        Cuerpo("Juega a los juegos del PC en este móvil con Steam Link, la app gratuita de Valve.", tam = 13)
        Spacer(Modifier.height(8.dp))
        LineaEstado(link, if (link) "Steam Link instalado" else "Steam Link no está instalado")
        LineaEstado(h?.on == true, when {
            h == null -> "Atrio del PC aún no ha dicho nada de Steam"
            !h.on -> "El PC no tiene Steam instalado"
            h.running -> "Steam abierto en ${h.name}"
            else -> "Steam cerrado en ${h.name} (Atrio lo abre al jugar)"
        })
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            if (!link) EnlaceRevista("Instalar Steam Link →", acento = true) { Stream.install(ctx) }
            else EnlaceRevista(if (busy) "Abriendo…" else "Abrir Steam Link →", acento = true) { if (!busy) abrir() }
            if (pc == null && wol != null) EnlaceRevista("Encender ${any?.name ?: "el PC"} →") { waking = true }
        }
        Spacer(Modifier.height(6.dp))
        Cuerpo("La primera vez: en Steam Link elige tu PC y te enseña un PIN; escríbelo en Steam del PC (sale una ventana, o en Ajustes → Remote Play → Emparejar Steam Link). Steam Link también puede conectarse fuera de casa, por internet.", tam = 12)
    }
    if (falta) SteamLinkDialog { falta = false }
    if (waking && wol != null) WakeDialog(any?.name ?: "el PC", wol) { waking = false }
}

/** En la ficha de un juego del PC: jugar en el PC o en streaming. */
@Composable
fun BotonesPc(j: Juego) {
    if (j.pcKey.isBlank()) { Text("Sin clave del PC", color = Muted, fontFamily = Body, fontSize = 12.sp); return }
    BotonesJugarPc(j.pcKey)
}

/** Al tocar un juego del PC (en el lienzo, en una lista o en un widget): elegir dónde jugar. */
@Composable
fun DialogoJugarPc(j: Juego, alCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = alCerrar, containerColor = Paper, shape = esquina(4.dp),
        title = { Text(j.titulo, color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { BotonesJugarPc(j.pcKey) { alCerrar() } },
        confirmButton = {}, dismissButton = { BotonDialogo("Cerrar") { alCerrar() } }
    )
}
