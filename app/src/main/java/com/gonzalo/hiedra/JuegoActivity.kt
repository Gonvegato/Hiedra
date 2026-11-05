package com.gonzalo.hiedra

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.radialgamepad.library.RadialGamePad
import com.swordfish.radialgamepad.library.event.Event
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/** En qué punto está la pantalla del juego. */
sealed interface FaseJuego {
    data class Preparando(val texto: String, val fraccion: Float? = null) : FaseJuego
    data class Pregunta(val texto: String, val si: String, val no: String, val respuesta: CompletableDeferred<Boolean>) : FaseJuego
    data class Error(val texto: String) : FaseJuego
    data object Jugando : FaseJuego
}

/**
 * El jugador interno: un juego abierto con un núcleo libretro (LibretroDroid), a pantalla completa.
 *
 * Al entrar: se asegura el núcleo (se baja si falta), se prepara la ROM, se carga la partida (SRAM) más reciente y se mira en la nube
 * si hay un estado automático más nuevo de otro móvil. Al salir por cualquier camino (el menú, atrás, el botón de inicio, otra app
 * encima) se hace el estado automático, se guarda la partida y se deja encargada la subida. Mientras juegas, cada pocos minutos,
 * también, por si el móvil cierra la app.
 *
 * La vista de LibretroDroid se crea UNA vez y no cambia de sitio en la composición: si se quitara y se volviera a poner (al girar el
 * móvil, al abrir el menú), perdería su contexto de OpenGL. Por eso la actividad declara todos los cambios de configuración.
 */
class JuegoActivity : ComponentActivity() {
    companion object {
        fun intent(c: Context, id: String): Intent = Intent(c, JuegoActivity::class.java).putExtra("id", id)
        /** Los fotogramas por segundo de cada consola (para medir la velocidad real del avance rápido). */
        fun fpsNominal(sistema: String) = when (sistema) { "gba", "gb", "gbc" -> 59.73f; else -> 60f }
        /** Solo para las capturas de las pruebas (el núcleo es código nativo de ARM y no corre en el ordenador): sin núcleo ni juego. */
        @androidx.annotation.VisibleForTesting internal var sinMotor = false
    }

    var juego: Juego? = null; private set
    var motor: Motor? = null; private set
    var vista: GLRetroView? = null; private set
    var nucleo: Nucleos.Instalado? = null; private set
    private lateinit var nucleoDef: Nucleos.Nucleo

    val entrada = Mandos.Estado("gba")
    val avance = AvanceRapido()

    var fase by mutableStateOf<FaseJuego>(FaseJuego.Preparando("Preparando…"))
    var menu by mutableStateOf(false)
    var pantallaMenu by mutableStateOf("principal")
    var mandoConectado by mutableStateOf(false)
    /** Tocaste la pantalla con el mando conectado: se vuelven a enseñar los controles táctiles hasta que vuelvas a usar el mando. */
    var tactilForzado by mutableStateOf(false)
    var indicador by mutableStateOf<String?>(null)
    var aviso by mutableStateOf<String?>(null)
    var esperandoMapeo by mutableStateOf<Mandos.Destino?>(null)
    var ultimoMando by mutableStateOf<InputDevice?>(null)
    val registro = mutableStateListOf<String>()
    /** Cambia cuando cambian los huecos de estados (el menú lo observa). */
    var versionEstados by mutableStateOf(0)

    private var estadoCargado = false
    private val mapas = HashMap<String, Map<Int, Mandos.Destino>>()
    private var delanteDesde = 0L
    private var imagenes = 0
    private var autoguardado: Job? = null
    private val pads = HashMap<MandoTactil.Lado, Job>()

    // ------------------------------------------------------------------ ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iniciar(this); AjustesJuego.cargar(this)
        val j = intent.getStringExtra("id")?.let { Biblioteca.por(it) }
        if (j == null) { Lanzar.aviso(this, "Ese juego ya no está en Hiedra"); finish(); return }
        val n = Nucleos.para(j.sistema)
        if (n == null) { Lanzar.aviso(this, "Hiedra todavía no juega a ${Catalogo.sistema(j.sistema)?.nombre ?: j.sistema}"); finish(); return }
        juego = j; nucleoDef = n
        entrada.sistema = j.sistema
        aplicarAjustesEntrada()
        pantallaCompleta()
        aplicarOrientacion()
        setContent { HiedraTheme { PantJuego(this) } }
        (getSystemService(Context.INPUT_SERVICE) as InputManager).registerInputDeviceListener(oyenteMandos, Handler(Looper.getMainLooper()))
        comprobarMandos()
        if (sinMotor) { nucleo = Nucleos.Instalado(n.id, 0L, 0L, "prueba"); fase = FaseJuego.Jugando; return }
        lifecycleScope.launch { preparar() }
    }

    override fun onResume() {
        super.onResume()
        pantallaCompleta()
        if (fase == FaseJuego.Jugando) {
            // LibretroDroid reanuda el núcleo DESPUÉS de este onResume (al avisar al ciclo de vida): con el menú abierto, se vuelve a
            // pausar justo detrás
            if (menu) window.decorView.post { if (menu) motor?.pausar() }
            empiezaTiempo()
            programarAutoguardado()
        }
    }

    override fun onPause() {
        super.onPause()
        autoguardado?.cancel()
        if (fase == FaseJuego.Jugando) {
            soltarTodo()
            acabaTiempo()
            guardarTodo(subir = true)
        }
    }

    override fun onDestroy() {
        runCatching { (getSystemService(Context.INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(oyenteMandos) }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) pantallaCompleta() else soltarTodo()
    }

    private fun pantallaCompleta() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    fun aplicarOrientacion() {
        requestedOrientation = when (AjustesJuego.orientacion) {
            AjustesJuego.Orientacion.APAISADA -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            AjustesJuego.Orientacion.VERTICAL -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            AjustesJuego.Orientacion.MOVIL -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        }
    }

    fun aplicarAjustesEntrada() {
        entrada.porEtiqueta = AjustesJuego.porEtiqueta; entrada.zonaMuerta = AjustesJuego.zonaMuerta
        avance.modo = AjustesJuego.avanceModo; avance.velocidad = AjustesJuego.avanceVelocidad
        mapas.clear()
    }

    // ------------------------------------------------------------------ preparar y arrancar

    private suspend fun preparar() {
        val j = juego ?: return
        try {
            // 1. El núcleo (se baja la primera vez)
            nucleo = Nucleos.instalado(this, nucleoDef.id) ?: run {
                fase = FaseJuego.Preparando("Bajando el núcleo ${nucleoDef.nombre}…", 0f)
                withContext(Dispatchers.IO) { Nucleos.instalar(this@JuegoActivity, nucleoDef.id) { p -> fase = FaseJuego.Preparando("Bajando el núcleo ${nucleoDef.nombre}…", p) } }
            }
            // 2. La ROM, en un archivo que el núcleo pueda abrir (si está en otro móvil, se baja antes de tu kDrive)
            val (jj, local) = if (j.uri.isBlank()) bajarDeLaNube(j) else j to null
            fase = FaseJuego.Preparando("Abriendo «${j.titulo}»…")
            val rom = withContext(Dispatchers.IO) { prepararRom(jj, local) }
            // 3. La partida (SRAM) más reciente: la de aquí o la de la carpeta de guardados (que ya trae la de la nube si era más nueva)
            val partida = withContext(Dispatchers.IO) { Estados.leerPartida(this@JuegoActivity, j) }
            // 4. El estado automático: el de aquí o, si es más nuevo, el de otro móvil
            val estado = elegirEstado(j)
            arrancar(j, rom, partida, estado)
        } catch (e: Exception) {
            fase = FaseJuego.Error(e.message ?: "No se ha podido preparar el juego")
        }
    }

    private suspend fun elegirEstado(j: Juego): ByteArray? {
        val n = nucleo ?: return null
        val local = Estados.meta(this, j.id, Estados.AUTO)
        fase = FaseJuego.Preparando("Mirando si lo dejaste a medias en otro móvil…")
        val nube = withTimeoutOrNull(5_000) { withContext(Dispatchers.IO) { Estados.indiceNube(j.id)[Estados.AUTO] } }
        val subido = local?.let { Estados.subido(this, j.id, Estados.AUTO, it.hash) } ?: false
        val decision = Estados.decidir(local, nube?.takeIf { it.dev != Cuenta.dispositivo || local == null }, n.id, n.version, subido)
        suspend fun bajar(): ByteArray? = try {
            fase = FaseJuego.Preparando("Bajando la partida de ${nube!!.dn.ifBlank { "otro móvil" }}…")
            withContext(Dispatchers.IO) { Estados.bajar(this@JuegoActivity, j.id, nube) }.also { aviso = "Sigues donde lo dejaste en ${nube.dn.ifBlank { "otro móvil" }}" }
        } catch (e: Exception) { aviso = "No se ha podido bajar la partida de la nube: sigues con la de aquí"; Estados.leer(this, j.id, Estados.AUTO) }
        return when (decision) {
            Estados.Decision.NADA -> null
            Estados.Decision.USAR_LOCAL, Estados.Decision.SUBIR_Y_USAR_LOCAL -> local?.takeIf { it.nucleo == n.id }?.let { Estados.leer(this, j.id, Estados.AUTO) }
            Estados.Decision.BAJAR -> bajar()
            Estados.Decision.PREGUNTAR -> {
                val r = CompletableDeferred<Boolean>()
                fase = FaseJuego.Pregunta("La partida que dejaste en ${nube!!.dn.ifBlank { "otro móvil" }} es de otra versión del núcleo ${nucleoDef.nombre}. Puede que no cargue (la partida del cartucho no se pierde).",
                    "Intentarlo", "Seguir con la de aquí", r)
                if (r.await()) bajar() else local?.let { Estados.leer(this, j.id, Estados.AUTO) }
            }
        }
    }

    /**
     * La ROM está en otro de tus móviles: se baja de tu kDrive. Con carpeta de ROMs en este móvil, a ella (y queda apuntada, para
     * cualquier emulador); sin ella, dentro de Hiedra. Devuelve el juego al día y, en el segundo caso, el archivo.
     */
    private suspend fun bajarDeLaNube(j: Juego): Pair<Juego, File?> {
        val app = applicationContext
        val f = withContext(Dispatchers.IO) { if (Cuenta.vinculada && KDrive.on(app)) runCatching { NubeP.romEnNube(j) }.getOrNull() else null }
            ?: throw IOException("La ROM de «${j.titulo}» no está en este móvil ni en tu kDrive. Se sube sola, con wifi, al abrir Hiedra en el móvil donde la tienes.")
        val tmp = NubeP.temporalRom(app, j)
        val vigia = lifecycleScope.launch {
            while (isActive) { fase = FaseJuego.Preparando("Bajando «${f.n}» de tu kDrive…", if (f.s > 0) (tmp.length().toFloat() / f.s).coerceIn(0f, 1f) else null); delay(300) }
        }
        try {
            val raiz = NubeP.carpetaBajadas(app)
            if (raiz != null) {
                withContext(Dispatchers.IO) { NubeP.bajarRomAqui(app, j, f, raiz) }
                return (Biblioteca.por(j.id) ?: j) to null
            }
            val dest = File(filesDir, "roms/${j.id}/${f.n}")
            if (!(dest.exists() && dest.length() == f.s)) {
                File(filesDir, "roms").listFiles()?.filter { it.name != j.id }?.forEach { it.deleteRecursively() } // solo la del último juego
                withContext(Dispatchers.IO) { NubeP.bajarRomAArchivo(app, j, f, dest) }
            }
            return j to dest
        } finally { vigia.cancel() }
    }

    /** Copia la ROM a la caché de la app (los núcleos abren archivos, no documentos de Android); descomprime un .zip. */
    private fun prepararRom(j: Juego, local: File? = null): File {
        if (j.uri.isBlank() && local == null) throw IOException("La ROM de «${j.titulo}» no está en este móvil")
        val uri = Uri.parse(j.uri)
        val doc = if (local == null) DocumentFile.fromSingleUri(this, uri) else null
        val nombre = local?.name ?: doc?.name ?: (j.archivo + "." + nucleoDef.extensiones.first())
        val sello = if (local != null) "$nombre|${local.length()}|${local.lastModified()}" else "$nombre|${doc?.length() ?: -1}|${doc?.lastModified() ?: 0}"
        fun abrir() = if (local != null) local.inputStream() else contentResolver.openInputStream(uri) ?: throw IOException("No se puede leer la ROM")
        val raiz = File(cacheDir, "rom")
        val dir = File(raiz, j.id)
        val marca = File(dir, ".sello")
        // Solo se queda en la caché la ROM del último juego (la caché no es para guardar ROMs)
        raiz.listFiles()?.filter { it.name != j.id }?.forEach { it.deleteRecursively() }
        if (marca.exists() && marca.readText() == sello) dir.listFiles()?.firstOrNull { it.name != ".sello" }?.let { return it }
        dir.deleteRecursively(); dir.mkdirs()
        val ext = nombre.substringAfterLast('.', "").lowercase()
        val destino: File = if (ext == "zip") {
            var hallado: File? = null
            ZipInputStream(abrir()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val n = e.name.substringAfterLast('/')
                    if (!e.isDirectory && n.substringAfterLast('.', "").lowercase() in nucleoDef.extensiones) {
                        hallado = File(dir, n).also { f -> f.outputStream().use { z.copyTo(it) } }; break
                    }
                }
            }
            hallado ?: throw IOException("En ese .zip no hay ninguna ROM que ${nucleoDef.nombre} sepa abrir (${nucleoDef.extensiones.joinToString { ".$it" }})")
        } else {
            if (ext !in nucleoDef.extensiones) throw IOException("${nucleoDef.nombre} no abre archivos .$ext")
            File(dir, nombre).also { f -> abrir().use { i -> f.outputStream().use { i.copyTo(it) } } }
        }
        marca.writeText(sello)
        return destino
    }

    private fun arrancar(j: Juego, rom: File, partida: ByteArray?, estado: ByteArray?) {
        val n = nucleo ?: return
        val datos = GLRetroViewData(this).apply {
            coreFilePath = Nucleos.archivo(this@JuegoActivity, n.id).absolutePath
            gameFilePath = rom.absolutePath
            systemDirectory = Nucleos.sistema(this@JuegoActivity).absolutePath
            savesDirectory = File(filesDir, "guardados/${j.id}").apply { mkdirs() }.absolutePath
            saveRAMState = partida?.takeIf { it.isNotEmpty() }
            shader = shaderDe(AjustesJuego.filtro)
            preferLowLatencyAudio = true
            rumbleEventsEnabled = false
        }
        val v = GLRetroView(this, datos)
        v.isFocusable = false
        vista = v; motor = MotorRetro(v)
        lifecycle.addObserver(v)
        lifecycleScope.launch {
            v.getGLRetroEvents().collect { e ->
                when (e) {
                    // La superficie puede crearse otra vez (si Android tira el contexto de OpenGL): el estado se carga solo la primera
                    is GLRetroView.GLRetroEvents.SurfaceCreated -> if (!estadoCargado) { estadoCargado = true
                        estado?.let { if (motor?.cargarEstado(it) != true) aviso = "El estado guardado no ha cargado: sigues desde la última partida del cartucho" } }
                    is GLRetroView.GLRetroEvents.FrameRendered -> imagenes++
                }
            }
        }
        lifecycleScope.launch { v.getGLRetroErrors().collect { c -> fase = FaseJuego.Error(textoError(c)) } }
        lifecycleScope.launch { medirVelocidad(j) }
        Sesion.marcarJugado(j)
        fase = FaseJuego.Jugando
        empiezaTiempo()
        programarAutoguardado()
    }

    fun shaderDe(f: AjustesJuego.Filtro): ShaderConfig = when (f) {
        AjustesJuego.Filtro.NITIDO -> ShaderConfig.Sharp
        AjustesJuego.Filtro.SUAVE -> ShaderConfig.Default
        AjustesJuego.Filtro.LCD -> ShaderConfig.LCD
        AjustesJuego.Filtro.CRT -> ShaderConfig.CRT
    }

    private fun textoError(c: Int) = when (c) {
        GLRetroView.ERROR_LOAD_LIBRARY -> "No se ha podido cargar el núcleo ${nucleoDef.nombre}. Quítalo y vuelve a bajarlo en Sistemas."
        GLRetroView.ERROR_LOAD_GAME -> "${nucleoDef.nombre} no ha podido abrir esta ROM. ¿Está bien el archivo?"
        GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "Este móvil no tiene la versión de OpenGL que hace falta."
        else -> "El núcleo se ha parado (error $c)."
    }

    // ------------------------------------------------------------------ guardar

    /**
     * Guarda el estado automático y la partida. Lo que hay que pedirle a la consola se pide aquí (rápido); lo que va a disco y a la
     * carpeta de guardados, en otro hilo. Con [subir], deja encargada la subida a la nube.
     */
    fun guardarTodo(subir: Boolean) {
        val j = juego ?: return; val m = motor ?: return; val n = nucleo ?: return
        val estado = m.estado(); val partida = m.partida()
        val app = applicationContext
        // El estado y la partida, ya (aunque la app se cierre ahora mismo); la miniatura, cuando llegue
        kotlin.concurrent.thread {
            runCatching { if (estado != null) Estados.guardar(app, j.id, Estados.AUTO, estado, n.id, n.version) }
            runCatching { if (partida != null) Estados.guardarPartida(app, j, partida) }
            if (subir && Cuenta.vinculada && KDrive.on(app)) Estados.encolarSubida(app, j.id, j.sistema)
            runOnUiThread { versionEstados++ }
        }
        if (estado != null) capturar { mini -> mini?.let { kotlin.concurrent.thread { Estados.guardarMiniatura(app, j.id, Estados.AUTO, it); runOnUiThread { versionEstados++ } } } }
    }

    fun guardarEnHueco(h: String) {
        val j = juego ?: return; val m = motor ?: return; val n = nucleo ?: return
        val estado = m.estado() ?: run { aviso = "${nucleoDef.nombre} no sabe guardar estados de este juego"; return }
        val app = applicationContext
        kotlin.concurrent.thread {
            Estados.guardar(app, j.id, h, estado, n.id, n.version)
            if (Cuenta.vinculada && KDrive.on(app)) Estados.encolarSubida(app, j.id, j.sistema)
            runOnUiThread { versionEstados++; aviso = "Guardado en el hueco $h" }
        }
        capturar { mini -> mini?.let { kotlin.concurrent.thread { Estados.guardarMiniatura(app, j.id, h, it); runOnUiThread { versionEstados++ } } } }
    }

    fun cargarHueco(h: String) {
        val j = juego ?: return
        val meta = Estados.meta(this, j.id, h)
        if (meta != null && meta.nucleo != nucleo?.id) { aviso = "Ese estado es de otro núcleo (${meta.nucleo})"; return }
        val datos = Estados.leer(this, j.id, h) ?: return
        aviso = if (motor?.cargarEstado(datos) == true) "Cargado" else "Ese estado no ha cargado (¿es de otra versión del núcleo?)"
        cerrarMenu()
    }

    /** Una foto de lo que se ve del juego (para la miniatura de un estado o una captura). Sin foto, null. */
    fun capturar(listo: (Bitmap?) -> Unit) {
        val v = vista
        if (v == null || v.width <= 0 || v.height <= 0) { listo(null); return }
        val b = runCatching { Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888) }.getOrNull() ?: run { listo(null); return }
        try {
            PixelCopy.request(v, b, { r -> listo(if (r == PixelCopy.SUCCESS) escalar(b) else null) }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) { listo(null) }
    }

    private fun escalar(b: Bitmap): Bitmap { val w = 360; return if (b.width <= w) b else Bitmap.createScaledBitmap(b, w, (b.height * w / b.width.toFloat()).toInt().coerceAtLeast(1), true) }

    fun guardarCaptura() {
        val j = juego ?: return
        val v = vista ?: return
        val b = runCatching { Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888) }.getOrNull() ?: return
        PixelCopy.request(v, b, { r ->
            if (r != PixelCopy.SUCCESS) { aviso = "No se ha podido hacer la captura"; return@request }
            kotlin.concurrent.thread {
                val ok = runCatching {
                    val safe = j.titulo.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "Juego" }
                    val cv = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "$safe ${SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(Date())}.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Hiedra/$safe")
                    }
                    val u = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: error("sin sitio")
                    contentResolver.openOutputStream(u)!!.use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }.isSuccess
                runOnUiThread { aviso = if (ok) "Captura guardada en Imágenes/Hiedra" else "No se ha podido guardar la captura" }
            }
        }, Handler(Looper.getMainLooper()))
    }

    private fun programarAutoguardado() {
        autoguardado?.cancel()
        val min = AjustesJuego.autoguardadoMin
        if (min <= 0) return
        autoguardado = lifecycleScope.launch {
            while (isActive) { delay(min * 60_000L); if (!menu && fase == FaseJuego.Jugando) guardarTodo(subir = false) }
        }
    }

    // ------------------------------------------------------------------ tiempo jugado

    private fun empiezaTiempo() { if (delanteDesde == 0L && !menu) delanteDesde = System.currentTimeMillis() }
    private fun acabaTiempo() {
        val j = juego ?: return
        if (delanteDesde == 0L) return
        val ms = System.currentTimeMillis() - delanteDesde; delanteDesde = 0L
        Biblioteca.por(j.id)?.let { Sesion.sumar(it, ms) }
    }

    // ------------------------------------------------------------------ avance rápido

    fun pulsarAvance(abajo: Boolean) { if (avance.pulsar(abajo)) aplicarVelocidad() }

    fun aplicarVelocidad() {
        val m = motor ?: return
        m.velocidad = avance.factor
        m.audio = !(avance.activo && AjustesJuego.avanceSilencio)
        indicador = if (avance.activo) AvanceRapido.indicador(avance.factor, null) else null
    }

    private suspend fun medirVelocidad(j: Juego) {
        val nominal = fpsNominal(j.sistema)
        while (lifecycleScope.coroutineContext.isActive) {
            imagenes = 0
            delay(1_000)
            if (avance.activo) indicador = AvanceRapido.indicador(avance.factor, AvanceRapido.real(imagenes.toFloat(), avance.factor, nominal))
        }
    }

    // ------------------------------------------------------------------ menú

    fun abrirMenu() {
        if (fase != FaseJuego.Jugando || menu) return
        soltarTodo()
        if (avance.apagar()) aplicarVelocidad()
        acabaTiempo()
        menu = true; pantallaMenu = "principal"
        motor?.pausar()
    }

    fun cerrarMenu() {
        if (!menu) return
        menu = false; esperandoMapeo = null
        motor?.reanudar()
        empiezaTiempo()
    }

    fun salir() {
        // onPause guarda y sube
        finish()
    }

    // ------------------------------------------------------------------ mandos

    private val oyenteMandos = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(id: Int) = comprobarMandos()
        override fun onInputDeviceRemoved(id: Int) = comprobarMandos()
        override fun onInputDeviceChanged(id: Int) = comprobarMandos()
    }

    private fun comprobarMandos() {
        val hay = InputDevice.getDeviceIds().any { InputDevice.getDevice(it)?.let(::esMando) == true }
        if (!hay) { tactilForzado = false; ultimoMando = null }
        mandoConectado = hay
    }

    private fun esMando(d: InputDevice): Boolean =
        !d.isVirtual && (d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD || d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)

    private fun mapaDe(d: InputDevice?): Map<Int, Mandos.Destino> {
        val k = d?.descriptor ?: return emptyMap()
        return mapas.getOrPut(k) { AjustesJuego.mapa(this, k) }
    }

    private fun usandoMando(d: InputDevice?) {
        if (d == null || !esMando(d)) return
        if (!mandoConectado) mandoConectado = true
        tactilForzado = false
        if (ultimoMando?.id != d.id) ultimoMando = d
    }

    fun procesar(salidas: List<Mandos.Salida>) {
        for (s in salidas) when (s) {
            is Mandos.Salida.Hacer -> when (s.accion) {
                Mandos.Accion.AVANCE -> pulsarAvance(s.abajo)
                Mandos.Accion.MENU -> if (s.abajo) { if (menu) cerrarMenu() else abrirMenu() }
            }
            else -> motor?.aplicar(s)
        }
    }

    fun soltarTodo() { motor?.let { m -> entrada.soltarTodo().forEach { m.aplicar(it) } } }

    private fun apunta(t: String) { registro.add(0, SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date()) + " · " + t); while (registro.size > 40) registro.removeAt(registro.size - 1) }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val d = e.device
        if (d != null && esMando(d) && e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0)
            apunta("${d.name} · ${KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_")} ↓ → ${Mandos.efectivo(e.keyCode, mapaDe(d), entrada.sistema, entrada.porEtiqueta)?.nombre ?: "nada"}")
        // Cambiando un botón: la siguiente tecla del mando es la nueva
        val espera = esperandoMapeo
        if (espera != null && d != null && esMando(d) && Mandos.esDeMando(e.keyCode)) {
            if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) {
                val nuevo = Mandos.reasignar(mapaDe(d), e.keyCode, espera, entrada.sistema, entrada.porEtiqueta)
                AjustesJuego.ponMapa(this, d.descriptor, nuevo); mapas[d.descriptor] = nuevo
                esperandoMapeo = null; ultimoMando = d
                aviso = "«${espera.nombre}» es ahora ${KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_").removePrefix("BUTTON_")}"
            }
            return true
        }
        // Atrás (del móvil o del mando): el menú, nunca salir sin guardar
        if (e.keyCode == KeyEvent.KEYCODE_BACK) {
            if (e.action == KeyEvent.ACTION_UP) { if (menu) { if (pantallaMenu != "principal") pantallaMenu = "principal" else cerrarMenu() } else abrirMenu() }
            return true
        }
        if (fase != FaseJuego.Jugando || menu) {
            // En el menú, el mando se mueve por él (Android convierte A en «aceptar»); el botón central lo cierra
            if (menu && e.keyCode == KeyEvent.KEYCODE_BUTTON_MODE && e.action == KeyEvent.ACTION_DOWN) { cerrarMenu(); return true }
            return super.dispatchKeyEvent(e)
        }
        if (!Mandos.esDeMando(e.keyCode)) return super.dispatchKeyEvent(e)
        usandoMando(d)
        if (e.repeatCount > 0) return true
        procesar(entrada.tecla(d?.descriptor ?: "teclado", e.keyCode, e.action == KeyEvent.ACTION_DOWN, mapaDe(d)))
        return true
    }

    override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
        val d = e.device
        val esEje = e.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK && e.action == MotionEvent.ACTION_MOVE
        if (!esEje || d == null) return super.dispatchGenericMotionEvent(e)
        val ejes = Mandos.Ejes(
            hatX = e.getAxisValue(MotionEvent.AXIS_HAT_X), hatY = e.getAxisValue(MotionEvent.AXIS_HAT_Y),
            x = e.getAxisValue(MotionEvent.AXIS_X), y = e.getAxisValue(MotionEvent.AXIS_Y),
            z = e.getAxisValue(MotionEvent.AXIS_Z), rz = e.getAxisValue(MotionEvent.AXIS_RZ),
            gatilloL = maxOf(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE)),
            gatilloR = maxOf(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)),
        )
        val vivos = listOf("HAT_X" to ejes.hatX, "HAT_Y" to ejes.hatY, "X" to ejes.x, "Y" to ejes.y, "Z" to ejes.z, "RZ" to ejes.rz, "L2" to ejes.gatilloL, "R2" to ejes.gatilloR)
            .filter { kotlin.math.abs(it.second) > 0.5f }
        if (vivos.isNotEmpty() && (pantallaMenu == "mando" || registro.isEmpty() || !registro.first().contains(vivos.joinToString { it.first })))
            apunta("${d.name} · " + vivos.joinToString(" · ") { "${it.first} ${String.format(Locale.ROOT, "%.2f", it.second)}" })
        if (fase != FaseJuego.Jugando || menu) return super.dispatchGenericMotionEvent(e)
        usandoMando(d)
        procesar(entrada.ejes(d.descriptor, ejes, mapaDe(d)))
        return true
    }

    // ------------------------------------------------------------------ el mando táctil

    /** Crea (o rehace, si cambiaste la opacidad o el tamaño) un lado del mando táctil y escucha lo que se pulsa en él. */
    fun crearPad(lado: MandoTactil.Lado): RadialGamePad {
        val acento = android.graphics.Color.rgb((Acento.red * 255).toInt(), (Acento.green * 255).toInt(), (Acento.blue * 255).toInt())
        val pad = RadialGamePad(MandoTactil.configuracion(juego?.sistema ?: "gba", lado, AjustesJuego.opacidad, acento, AjustesJuego.vibracion), 12f, this)
        pads[lado]?.cancel()
        pads[lado] = lifecycleScope.launch {
            pad.events().collect { ev ->
                if (menu || fase != FaseJuego.Jugando) return@collect
                when (ev) {
                    is Event.Button -> MandoTactil.destinoDe(ev.id)?.let { procesar(entrada.destino("tactil:${ev.id}", it, ev.action == KeyEvent.ACTION_DOWN)) }
                    is Event.Direction -> if (ev.id == MandoTactil.ID_CRUZ) procesar(entrada.crucetaTactil(ev.xAxis, ev.yAxis))
                }
            }
        }
        return pad
    }
}
