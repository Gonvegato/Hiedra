package com.gonzalo.hiedra

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val VERSION = "0.4.5"

private fun aliasInicio(c: Context) = ComponentName(c, "com.gonzalo.hiedra.InicioAlias")
private fun esInicio(c: Context) = c.packageManager.getComponentEnabledSetting(aliasInicio(c)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

/** «Ajustes»: Apariencia (modo, acento, tamaño, estilo), Portadas, Pantalla de inicio y copias. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantEstilo(alVolver: () -> Unit, alSistemas: () -> Unit = {}) {
    val ctx = LocalContext.current
    var inicio by remember { mutableStateOf(esInicio(ctx)) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    fun cambia(f: () -> Unit) { f(); Skin.guardar(ctx) }
    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        if (u != null) mensaje = runCatching { ctx.contentResolver.openOutputStream(u)!!.use { it.write(Biblioteca.aTexto().toByteArray()) }; "Copia guardada" }.getOrElse { "No se pudo guardar la copia" }
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) mensaje = runCatching { Biblioteca.leer(ctx.contentResolver.openInputStream(u)!!.use { it.readBytes().decodeToString() }, reemplazar = false); Biblioteca.guardar(); "Copia importada" }.getOrElse { "Ese archivo no es una copia de Hiedra" }
    }
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(Paper).verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        RevistaTopBar(alVolver, "Hiedra")
        Antetitulo("Sección", Modifier.padding(top = 10.dp), color = Acento, conCuadro = false)
        FitTitle("Ajustes", 52.sp, 56.sp, letterSpacing = (-1.5).sp, modifier = Modifier.padding(top = 2.dp), unaLinea = true)
        Filete(3.dp)
        PestanasRevista(listOf("Apariencia", "Juegos", "Cuenta", "Avanzado"), pestana) { pestana = it }
        Spacer(Modifier.height(14.dp))

        when (pestana) {
            0 -> SeccionApariencia()
            1 -> {
                Antetitulo("Sistemas", Modifier.padding(bottom = 4.dp))
                MagLink("Sistemas y emuladores", "Cada consola, con el emulador que elijas, y las carpetas de ROMs", onClick = alSistemas)
                Spacer(Modifier.height(26.dp))
                Filete(2.dp)
                Spacer(Modifier.height(12.dp))
                Antetitulo("Portadas", Modifier.padding(bottom = 4.dp))
                Fila("Portadas automáticas", "Busca la caja de cada ROM en thumbnails.libretro.com (se envía el nombre del archivo). Si lo desactivas no sale nada a internet. Azabache enseña esa misma caja mientras no pongas otra portada.") {
                    Interruptor(Ajustes.portadasLibretro) { Ajustes.portadasLibretro = it; Ajustes.guardar(ctx) }
                }
                Spacer(Modifier.height(14.dp))
                Filete(2.dp)
                Spacer(Modifier.height(12.dp))
                Antetitulo("Tiempo de juego", Modifier.padding(bottom = 4.dp))
                var uso by remember { mutableStateOf(Uso.permitido(ctx)) }
                val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val ob = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) uso = Uso.permitido(ctx) }
                    owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
                }
                MagLink(if (uso) "Tiempo exacto: activado" else "Tiempo exacto: dar permiso",
                    if (uso) "Hiedra mira cuánto estuvo delante el emulador o el juego (permiso «acceso al uso» de Android). Lo demás no se mira. Al dejar de jugar, las horas se apuntan y suben solas, aunque no vuelvas a Hiedra." else "Sin él, se cuenta todo el rato desde que abres el juego hasta que vuelves a Hiedra (como mucho 12 horas). Con el permiso «acceso al uso» de Android se cuenta solo lo que el emulador o el juego estuvo delante.") { Uso.pedir(ctx) }
                Spacer(Modifier.height(14.dp))
                Filete(2.dp)
                Spacer(Modifier.height(12.dp))
                Antetitulo("Pantalla de inicio", Modifier.padding(bottom = 4.dp))
                Fila("Usar Hiedra como launcher", if (inicio) "Hiedra ya puede elegirse como pantalla de inicio del móvil." else "Permite elegir Hiedra como pantalla de inicio (Android te lo pregunta).") {
                    Interruptor(inicio) { on ->
                        ctx.packageManager.setComponentEnabledSetting(aliasInicio(ctx), if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                        inicio = on
                        if (on) runCatching { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Filete(2.dp)
                Spacer(Modifier.height(12.dp))
                Antetitulo("Tu PC", Modifier.padding(bottom = 4.dp))
                var galeria by remember { mutableStateOf(Suite.aGaleria()) }
                Fila("Capturas del PC en la galería", "Las capturas que pides al PC se guardan también en Imágenes/Hiedra (si no, solo dentro de la app).") {
                    Interruptor(galeria) { galeria = it; Suite.ponAGaleria(it) }
                }
            }
            2 -> {
                Antetitulo("Cuenta de Atrio", Modifier.padding(bottom = 4.dp))
                SeccionCuenta()
                Filete(1.dp, Line)
                SeccionImportarCarmesi()
                Filete(1.dp, Line)
                SeccionImportarAtrio()
                Spacer(Modifier.height(14.dp))
                Filete(2.dp)
                Spacer(Modifier.height(12.dp))
                Antetitulo("Servicios", Modifier.padding(bottom = 4.dp))
                SeccionServicios()
            }
            else -> {
                Antetitulo("kDrive y actualizaciones", Modifier.padding(bottom = 4.dp))
                SeccionActualizaciones()
                Spacer(Modifier.height(14.dp))
                Filete(2.dp)
                Spacer(Modifier.height(12.dp))
                Antetitulo("Copias", Modifier.padding(bottom = 4.dp))
                MagLink("Guardar copia de la biblioteca", "Un archivo con tus juegos y carpetas (no incluye las ROMs)") { exportar.launch("hiedra-biblioteca.json") }
                Filete(1.dp, Line)
                MagLink("Importar copia", "Suma los juegos de una copia a los que ya tienes") { importar.launch(arrayOf("application/json", "*/*")) }
                mensaje?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }

        Spacer(Modifier.height(26.dp))
        Filete(3.dp)
        Spacer(Modifier.height(10.dp))
        Etiqueta("Hiedra $VERSION")
        Text("Hiedra es hermana de Azabache, Marfil y Cobalto. Los comandos para abrir emuladores salen de ES-DE Frontend (licencia MIT, © Northwestern Software AB, Leon Styhre y Alec Lofquist). Las portadas, de libretro. El jugador interno usa LibretroDroid y RadialGamePad (GPL-3.0, Filippo Scognamiglio) y los núcleos de libretro (mGBA, MPL-2.0), que se descargan del buildbot de libretro; por eso Hiedra es software libre (GPL-3.0).", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(30.dp))
    }
}


/** Una casilla de texto con el estilo de la revista: etiqueta en versalitas, texto en cursiva sobre un filete. */
@Composable
fun CampoRevista(etiqueta: String, valor: String, pista: String, secreto: Boolean = false, alCambiar: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text(etiqueta.uppercase(), color = Muted, fontFamily = Body, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.width(96.dp).padding(bottom = 3.dp))
            Box(Modifier.weight(1f)) {
                if (valor.isEmpty()) Text(pista, color = Muted, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp)
                BasicTextField(valor, alCambiar, singleLine = true, textStyle = TextStyle(color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 18.sp),
                    cursorBrush = SolidColor(Acento), visualTransformation = if (secreto) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (secreto) KeyboardType.Password else KeyboardType.Text, autoCorrect = false), modifier = Modifier.fillMaxWidth())
            }
        }
        Filete(2.dp)
    }
}

/** «Cuenta de Atrio»: entrar con tu usuario y contraseña de Atrio, ver cómo va la sincronización, sincronizar ahora o salir. */
@Composable
private fun SeccionCuenta() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var usuario by remember { mutableStateOf("") }
    var clave by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    if (!Cuenta.vinculada) {
        Text("Entra con la misma cuenta que usas en Atrio y tus juegos, horas, notas y diseños de ficha se sincronizan entre tus Hiedras. Las ROMs, los guardados y las portadas van por tu kDrive (se conecta en «Avanzado»).",
            color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        CampoRevista("Usuario", usuario, "tu usuario de Atrio") { usuario = it }
        CampoRevista("Contraseña", clave, "••••••••", secreto = true) { clave = it }
        MagLink(if (ocupado) "Entrando…" else "Vincular cuenta", "Se guarda un pase de acceso, no tu contraseña") {
            if (!ocupado) {
                ocupado = true; aviso = null
                ambito.launch {
                    aviso = try { Cuenta.entrar(ctx, usuario, clave); clave = ""; SincroP.sincronizar(ctx, desdeCero = true); SincroP.escuchar(ctx); null }
                    catch (e: Cuenta.Fail) { e.message } catch (e: Exception) { "No se pudo entrar: ${e.message ?: "sin conexión"}" }
                    ocupado = false
                }
            }
        }
    } else {
        Etiqueta("Vinculada a", Modifier.padding(top = 8.dp))
        Text(Cuenta.usuario, color = Charcoal, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 22.sp)
        val cuando = SincroP.ultima.takeIf { it > 0 }?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it)) }
        Text(listOf(SincroP.estado.ifBlank { "Sin sincronizar todavía" }, cuando?.let { "última vez $it" }).filterNotNull().joinToString(" · "),
            color = Muted, fontFamily = Body, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(6.dp))
        MagLink(if (ocupado) "Sincronizando…" else "Sincronizar ahora", "Baja lo de tus otros Hiedras y sube lo de este") {
            if (!ocupado) { ocupado = true; ambito.launch { SincroP.sincronizar(ctx, desdeCero = true); ocupado = false } }
        }
        Filete(1.dp, Line)
        MagLink("Salir de la cuenta", "Tus juegos se quedan aquí; dejan de sincronizarse") { SincroP.parar(); Cuenta.salir() }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
}


/** «Actualizaciones»: Hiedra se actualiza sola desde la carpeta «versiones» de tu kDrive (la de Atrio), con tu token de kDrive. */
@Composable
private fun SeccionActualizaciones() {
    val ctx = LocalContext.current
    val ambito = rememberCoroutineScope()
    var conectado by remember { mutableStateOf(KDrive.on(ctx)) }
    var token by remember { mutableStateOf("") }
    var unidad by remember { mutableStateOf("") }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var auto by remember { mutableStateOf(Actualizador.activo(ctx)) }
    var sobre by remember { mutableStateOf(Actualizador.puedeReabrirSola(ctx)) }
    val estado = Actualizador.estado.ifBlank { Actualizador.estadoGuardado(ctx) }
    DisposableEffect(Unit) {
        val owner = androidx.lifecycle.ProcessLifecycleOwner.get()
        val ob = androidx.lifecycle.LifecycleEventObserver { _, ev -> if (ev == androidx.lifecycle.Lifecycle.Event.ON_RESUME) sobre = Actualizador.puedeReabrirSola(ctx) }
        owner.lifecycle.addObserver(ob); onDispose { owner.lifecycle.removeObserver(ob) }
    }
    Fila("Actualizar sola", "Mira la carpeta «versiones» de tu kDrive (Hiedra-<versión>.apk) y se instala sin preguntar cuando no la usas.") {
        Interruptor(auto) { auto = it; Actualizador.ponActivo(ctx, it) }
    }
    // Android 13 o más: sin el permiso de avisos, «tienes una versión nueva» y «se ha actualizado, toca para abrirla» no se ven nunca
    if (android.os.Build.VERSION.SDK_INT >= 33) {
        var avisos by remember { mutableStateOf(ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
        val pedirAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { avisos = it }
        if (!avisos) MagLink("Avisos de actualización: dar permiso", "Para avisarte cuando hay una versión nueva que Android no deja instalar sola, y para volver a abrir Hiedra al terminar de actualizarla.") { pedirAvisos.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
    }
    if (conectado) {
        var solas by remember { mutableStateOf(NubeP.romsSolas(ctx)) }
        Fila("Subir mis ROMs a mi kDrive", "Solas y solo con wifi, en cuanto las añades, para tenerlas en tus otros móviles junto a sus guardados. Van a tu kDrive, no a ningún sitio público.") {
            Interruptor(solas) { solas = it; NubeP.ponRomsSolas(ctx, it); if (it) SubirRomsWorker.programar(ctx) }
        }
        // En este móvil, las que subiste desde otro
        var faltan by remember { mutableStateOf<Int?>(null) }
        var pedido by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { faltan = withContext(Dispatchers.IO) { runCatching { NubeP.romsQueFaltan().size }.getOrNull() } }
        val carpetaRoms = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u ->
            if (u != null) {
                Escaneo.recordar(ctx, u)
                if (Biblioteca.carpetas.none { it.uri == u.toString() }) { Biblioteca.carpetas.add(Carpeta(u.toString(), androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, u)?.name ?: "ROMs")); Biblioteca.guardar() }
                if (NubeP.carpetaBajadas(ctx) != null) { BajarRomsWorker.programar(ctx); pedido = true }
            }
        }
        val n = faltan
        if (n != null && n > 0) MagLink(if (pedido) "Bajando ${if (n == 1) "la ROM que falta" else "las $n ROMs que faltan"} (con wifi)…" else "Bajar ${if (n == 1) "la ROM que falta" else "las $n ROMs que faltan"} en este móvil",
            "Las que tienes en tu kDrive y aquí no. A tu carpeta de ROMs, cada una en la de su sistema; solo con wifi, y sigue sola aunque cierres Hiedra.") {
            if (!pedido) { if (NubeP.carpetaBajadas(ctx) != null) { BajarRomsWorker.programar(ctx); pedido = true } else carpetaRoms.launch(null) }
        }
    }
    if (!conectado) {
        Text("Pega tu token de kDrive y el número de tu kDrive (los mismos que en Azabache). Se guardan solo en este móvil.", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
        CampoRevista("Token", token, "token de kDrive", secreto = true) { token = it }
        CampoRevista("kDrive", unidad, "número o dirección") { unidad = it }
        MagLink(if (ocupado) "Comprobando…" else "Conectar", "Comprueba el token y busca tu «Mi carpeta»") {
            if (!ocupado) {
                ocupado = true; aviso = null
                ambito.launch {
                    aviso = try { KDrive.save(ctx, token, unidad); KDrive.test(ctx); conectado = true; token = ""; SubirRomsWorker.programar(ctx); withContext(Dispatchers.IO) { runCatching { Actualizador.comprueba(ctx, manual = true) } }; null }
                    catch (e: Exception) { KDrive.forget(ctx); e.message ?: "No se pudo conectar" }
                    ocupado = false
                }
            }
        }
    } else {
        val vers = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
        Text("Versión instalada: $vers" + if (estado.isNotBlank()) " · última comprobación: $estado" else "", color = Muted, fontFamily = Body, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        // Actualizar ya: mira, baja y instala sin esperar a que cierres la app (para cuando lo automático falla)
        MagLink(if (ocupado) "Actualizando…" else "Actualizar ahora", "Busca la última versión en tu kDrive y la instala ahora", accent = true) {
            if (!ocupado) { ocupado = true; aviso = null; ambito.launch { aviso = withContext(Dispatchers.IO) { Actualizador.actualizarAhora(ctx) }; ocupado = false } }
        }
        Filete(1.dp, Line)
        MagLink("Solo comprobar") {
            if (!ocupado) { ocupado = true; ambito.launch { withContext(Dispatchers.IO) { Actualizador.comprueba(ctx, manual = true) }; ocupado = false } }
        }
        Filete(1.dp, Line)
        MagLink(if (sobre) "Tras actualizar, Hiedra se vuelve a abrir sola" else "Permitir que se reabra sola tras actualizar", if (sobre) null else "Hace falta el permiso «Mostrar sobre otras apps»; si no, te avisa para abrirla") {
            if (!sobre) runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))) }
        }
        Filete(1.dp, Line)
        MagLink("Desconectar kDrive", "Se olvida el token de este móvil") { KDrive.forget(ctx); conectado = false }
    }
    aviso?.let { Text(it, color = Acento, fontFamily = Display, fontStyle = FontStyle.Italic, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
}

/** Si hay una versión nueva ya bajada que Android no deja instalar en silencio, se pregunta (o se lleva al ajuste de «instalar apps»). */
@Composable
fun AvisoActualizacion() {
    val ctx = LocalContext.current
    val n = Actualizador.nueva?.takeIf { !it.silenciosa } ?: return
    var luego by remember(n.nombre) { mutableStateOf(false) }
    if (luego) return
    AlertDialog(
        onDismissRequest = { luego = true }, containerColor = Paper, shape = esquina(4.dp),
        title = { Text("Hiedra tiene una versión nueva", color = Charcoal, fontFamily = Display, fontWeight = FontWeight.Bold) },
        text = { Text(if (Actualizador.faltaPermiso) "Hay una versión nueva en tu kDrive. Para instalarla, Android tiene que dejar a Hiedra instalar apps: te llevo a ese ajuste." else "Hay una versión nueva en tu kDrive, ya descargada. Android te pedirá confirmar la instalación.", color = Muted, fontFamily = Body) },
        confirmButton = { TextButton({
            if (Actualizador.faltaPermiso) { Actualizador.faltaPermiso = false; ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:${ctx.packageName}"))) }
            else runCatching { Actualizador.instala(ctx, n) }.onFailure { Lanzar.aviso(ctx, "No se pudo instalar: ${it.message}") }
        }) { Text(if (Actualizador.faltaPermiso) "PERMITIR" else "ACTUALIZAR AHORA", color = Acento, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } },
        dismissButton = { TextButton({ luego = true }) { Text("LUEGO", color = Charcoal, fontFamily = Body, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp) } }
    )
}
