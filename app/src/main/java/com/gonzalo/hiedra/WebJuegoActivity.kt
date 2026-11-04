package com.gonzalo.hiedra

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color as AColor
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Juegos de navegador (PokéRogue y compañía): una pantalla completa con un WebView propio, sin barra de direcciones.
 * Guarda cookies y datos de la web entre sesiones (las partidas guardadas viven ahí), deja subir archivos y descargar,
 * mantiene la pantalla encendida y pregunta antes de salir para no perder la partida por un gesto sin querer.
 */
class WebJuegoActivity : ComponentActivity() {
    private var web: WebView? = null
    private var subida: ValueCallback<Array<Uri>>? = null
    private var desde = 0L
    private var juego: Juego? = null

    private val elegirArchivo = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        subida?.onReceiveValue(uris.toTypedArray()); subida = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Biblioteca.cargar(this); Skin.cargar(this)
        val url = intent.getStringExtra("url").orEmpty()
        juego = intent.getStringExtra("id")?.let { Biblioteca.por(it) }
        if (!url.startsWith("https://") && !url.startsWith("http://")) { finish(); return }
        requestedOrientation = when (intent.getIntExtra("orient", 0)) { 1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE; 2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT; else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AColor.TRANSPARENT; window.navigationBarColor = AColor.TRANSPARENT
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars()); systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setTaskDescription(android.app.ActivityManager.TaskDescription(intent.getStringExtra("titulo")))

        val w = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(AColor.BLACK)
            // Deslizar hacia abajo no debe hacer nada: ni rebote, ni brillo de borde, ni «tirar para recargar»
            overScrollMode = android.view.View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
            settings.apply {
                javaScriptEnabled = true; domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false; allowContentAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
                loadWithOverviewMode = true; useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            CookieManager.getInstance().setAcceptCookie(true)
            webViewClient = object : WebViewClient() {
                // Por si el WebView no deja poner el script al principio: se pone en cuanto la página se ve
                override fun onPageCommitVisible(v: WebView, url: String?) { v.evaluateJavascript(JS_SIN_REBOTE, null) }
                override fun onPageFinished(v: WebView, url: String?) { v.evaluateJavascript(JS_SIN_REBOTE, null) }
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val s = r.url.scheme
                    if (s == "http" || s == "https") return false
                    // intent:, market:, mailto: … se ignoran (un juego no debería abrir otras apps sin que lo sepas)
                    return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                    subida?.onReceiveValue(null); subida = cb
                    elegirArchivo.launch("*/*"); return true
                }
            }
            setDownloadListener { u, ua, cd, mime, _ ->
                if (u.startsWith("http")) runCatching {
                    val n = URLUtil.guessFileName(u, cd, mime)
                    val r = DownloadManager.Request(Uri.parse(u)).setMimeType(mime).addRequestHeader("User-Agent", ua)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, n)
                    (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(r)
                    Toast.makeText(this@WebJuegoActivity, "Descargando $n", Toast.LENGTH_SHORT).show()
                } else Toast.makeText(this@WebJuegoActivity, "Este tipo de descarga no está soportado todavía", Toast.LENGTH_LONG).show()
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        // Antes de que la página ponga nada: «overscroll-behavior: none» (así ni el navegador ni la web tiran de la pantalla)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) WebViewCompat.addDocumentStartJavaScript(w, JS_SIN_REBOTE, setOf("*"))
        web = w
        if (savedInstanceState == null) w.loadUrl(url) else w.restoreState(savedInstanceState)

        setContent {
            var menu by remember { mutableStateOf(false) }
            var salir by remember { mutableStateOf(false) }
            BackHandler { salir = true }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AndroidView(factory = { w }, modifier = Modifier.fillMaxSize())
                // Una pestañita casi transparente arriba: recargar, girar o salir
                Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.clip2().background(Color(0x55000000)).clickable { menu = !menu }.padding(horizontal = 14.dp, vertical = 3.dp)) {
                        Icon(if (menu) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, "Menú", tint = Color(0xAAFFFFFF), modifier = Modifier.size(16.dp))
                    }
                    AnimatedVisibility(menu) {
                        Row(Modifier.padding(top = 6.dp).clip2().background(Color(0xE6101216)).padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Acc(Icons.Filled.Refresh, "Recargar") { web?.reload(); menu = false }
                            Acc(Icons.Filled.ScreenRotation, "Girar") {
                                requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                menu = false
                            }
                            Acc(Icons.Filled.Close, "Salir") { salir = true; menu = false }
                        }
                    }
                }
            }
            if (salir) androidx.compose.runtime.LaunchedEffect(Unit) {
                AlertDialog.Builder(this@WebJuegoActivity).setTitle("¿Salir del juego?")
                    .setMessage("Guarda antes si el juego no lo hace solo.")
                    .setPositiveButton("Salir") { _, _ -> finish() }
                    .setNegativeButton("Seguir jugando", null)
                    .setOnDismissListener { salir = false }.show()
            }
        }
    }

    override fun onStart() { super.onStart(); desde = System.currentTimeMillis() }
    override fun onStop() { super.onStop(); juego?.let { Sesion.sumar(it, System.currentTimeMillis() - desde) }; CookieManager.getInstance().flush() }
    override fun onResume() { super.onResume(); web?.onResume() }
    override fun onPause() { super.onPause(); web?.onPause() }
    override fun onSaveInstanceState(o: Bundle) { super.onSaveInstanceState(o); web?.saveState(o) }
    override fun onDestroy() { (web?.parent as? ViewGroup)?.removeView(web); web?.destroy(); web = null; super.onDestroy() }

    companion object {
        /** Quita el rebote y el «deslizar para recargar» de la página (y de Chromium) en la dirección vertical y horizontal. */
        const val JS_SIN_REBOTE = "(function(){try{var id='hiedra-sin-rebote';if(document.getElementById(id))return;var s=document.createElement('style');s.id=id;s.textContent='html,body{overscroll-behavior:none !important}';(document.head||document.documentElement).appendChild(s);}catch(e){}})();"
        fun intent(c: Context, j: Juego) = Intent(c, WebJuegoActivity::class.java)
            .putExtra("id", j.id).putExtra("url", j.url).putExtra("titulo", j.titulo).putExtra("orient", j.orientacion)
    }
}

private fun Modifier.clip2(): Modifier = this.then(Modifier.clip(RoundedCornerShape(50)))

@Composable
private fun Acc(icono: androidx.compose.ui.graphics.vector.ImageVector, texto: String, alHacer: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).clickable(onClick = alHacer).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icono, texto, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(texto, color = Color.White, fontSize = 13.sp)
    }
}
