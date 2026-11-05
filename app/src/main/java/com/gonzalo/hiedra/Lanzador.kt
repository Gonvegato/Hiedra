package com.gonzalo.hiedra

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast

/** Cómo se eligió el comando de un juego. */
class Eleccion(val etiqueta: String, val plantilla: String, val resultado: Resultado)

object Lanzar {
    fun entorno(c: Context) = object : Entorno {
        val pm = c.packageManager
        override fun instalado(paquete: String): Boolean = try {
            pm.getPackageInfo(paquete, 0); true
        } catch (_: PackageManager.NameNotFoundException) { false }
        override val internos = "/data/data"
        override val externos: String = Environment.getExternalStorageDirectory().absolutePath
    }

    fun comandosDe(sistema: String): List<Pair<String, String>> =
        Catalogo.sistema(sistema)?.comandos.orEmpty().filter { Comando.soportado(it.second) }

    fun rom(j: Juego) = RomRef(j.uri, j.ruta.ifBlank { null })

    /** Resultado de cada comando del sistema para esta ROM (para enseñar cuáles se pueden usar ahora). */
    fun evaluar(c: Context, j: Juego): List<Eleccion> = evaluar(c, j.sistema, rom(j))

    fun evaluar(c: Context, sistema: String, r: RomRef): List<Eleccion> {
        val env = entorno(c)
        return listOfNotNull(interno(c, sistema)) + comandosDe(sistema).map { (l, t) -> Eleccion(l, t, Comando.resolver(t, r, env)) }
    }

    /** El jugador interno: un emulador más en la lista («Hiedra»), para los sistemas con núcleo. Siempre se puede (el núcleo se baja al jugar). */
    const val HIEDRA = "Hiedra"
    const val PLANTILLA_HIEDRA = "hiedra:interno"
    fun esInterno(e: Eleccion?) = e?.plantilla == PLANTILLA_HIEDRA
    private fun interno(c: Context, sistema: String): Eleccion? = Nucleos.para(sistema)?.let {
        Eleccion(HIEDRA, PLANTILLA_HIEDRA, Resultado.Ok(Especificacion(c.packageName, null, null, null, null, emptyList(), emptyList(), false, false, null, HIEDRA)))
    }

    /** El comando que se usaría para un sistema, con una ROM de mentira (para la pantalla de sistemas). */
    fun elegirSistema(c: Context, sistema: String): Eleccion? = elegirDe(evaluar(c, sistema, RomRef("content://sonda", "/storage/emulated/0/sonda")), Ajustes.emuladorPorSistema[sistema].orEmpty())

    /**
     * El comando que se usará: el elegido para el juego, si no el elegido para el sistema, y si no uno automático:
     * primero los emuladores propios (sin núcleos que descargar), luego RetroArch, entre los que estén instalados.
     */
    fun elegir(c: Context, j: Juego): Eleccion? = elegirDe(evaluar(c, j), j.emulador.ifBlank { Ajustes.emuladorPorSistema[j.sistema].orEmpty() })

    private fun elegirDe(todos: List<Eleccion>, pedido: String): Eleccion? {
        if (todos.isEmpty()) return null
        if (pedido.isNotBlank()) todos.firstOrNull { it.etiqueta == pedido }?.let { return it }
        // Automático: el jugador interno, si el sistema lo tiene (sabe cuándo acabas, guarda al salir y lo sube)
        todos.firstOrNull { esInterno(it) }?.let { return it }
        return todos.filter { it.resultado is Resultado.Ok && !Comando.esRetroArch(it.plantilla) }.firstOrNull()
            ?: todos.firstOrNull { it.resultado is Resultado.Ok }
            ?: todos.firstOrNull { !Comando.esRetroArch(it.plantilla) } ?: todos.first()
    }

    fun aIntent(s: Especificacion): Intent {
        val i = Intent()
        s.clase?.let { i.setClassName(s.paquete, it) } ?: i.setPackage(s.paquete)
        s.accion?.let { i.action = it }
        val d = s.datos?.let { Uri.parse(it) }
        if (d != null && s.mime != null) i.setDataAndType(d, s.mime) else if (d != null) i.data = d else if (s.mime != null) i.type = s.mime
        s.categorias.forEach { i.addCategory(it) }
        for (e in s.extras) when (val v = e.valor) { is Boolean -> i.putExtra(e.clave, v); is Int -> i.putExtra(e.clave, v); else -> i.putExtra(e.clave, v.toString()) }
        var f = Intent.FLAG_ACTIVITY_NEW_TASK
        if (s.limpiarTarea) f = f or Intent.FLAG_ACTIVITY_CLEAR_TASK
        if (s.limpiarArriba) f = f or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (s.conceder != null) {
            f = f or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            // El permiso de lectura vale también para el documento que va dentro de un extra o dentro de los datos
            i.clipData = ClipData.newRawUri("rom", Uri.parse(s.conceder))
        }
        i.addFlags(f)
        return i
    }

    /** Lanza el juego. Devuelve null si salió bien o el texto del problema. */
    fun lanzar(c: Context, j: Juego, alTerminar: () -> Unit = {}): String? {
        // Con guardados en la nube: antes de abrir el emulador se mira si hay uno más reciente en otro móvil (en segundo plano; se abre al acabar)
        if (j.principal?.tipo == TipoLanzador.ROM && NubeP.activo(c, j)) {
            val e = elegir(c, j)
            if (e != null && e.resultado is Resultado.Ok) {
                val app = c.applicationContext
                val principal = android.os.Handler(android.os.Looper.getMainLooper())
                kotlin.concurrent.thread {
                    val texto = runCatching { NubeP.carpetaReal(app, NubeP.carpetas[j.sistema]!!)?.let { NubeP.antes(app, j, it) } }.getOrNull()
                    principal.post {
                        texto?.let { aviso(app, it) }
                        lanzarYa(c, j)?.let { aviso(app, it) }
                        alTerminar()
                    }
                }
                return null
            }
        }
        return lanzarYa(c, j).also { alTerminar() }
    }

    private fun lanzarYa(c: Context, j: Juego): String? {
        var paqueteAbierto = ""
        val error: String? = try {
            val l = j.principal ?: return "«${j.titulo}» no se juega desde el móvil" + (if (j.plataforma.isNotBlank()) " (${j.plataforma})" else "") + ". Si tienes su ROM, su app o su web, añádela desde la ficha."
            when (l.tipo) {
                TipoLanzador.ROM -> {
                    val e = elegir(c, j) ?: return "Hiedra no sabe lanzar «${l.sistema}»"
                    // Sin la ROM aquí: el jugador de Hiedra la baja él solo de tu kDrive; los demás emuladores necesitan el archivo
                    if (!Archivos.tiene(l.clave) && !esInterno(e)) return "Esta ROM está en otro de tus móviles: en su ficha, «Jugar» la baja de tu kDrive."
                    // El jugador interno: lleva la cuenta del tiempo él mismo (sabe exactamente cuándo juegas)
                    if (esInterno(e)) { c.startActivity(JuegoActivity.intent(c, j.id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return null }
                    when (val r = e.resultado) {
                        is Resultado.Ok -> { c.startActivity(aIntent(r.spec)); paqueteAbierto = r.spec.paquete; null }
                        is Resultado.Falta -> "${e.etiqueta}: ${r.motivo}"
                    }
                }
                TipoLanzador.APP -> {
                    val i = c.packageManager.getLaunchIntentForPackage(l.paquete)
                    if (i == null) "Esa app ya no está instalada" else { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); c.startActivity(i); paqueteAbierto = l.paquete; null }
                }
                TipoLanzador.WEB -> { c.startActivity(WebJuegoActivity.intent(c, j)); null }
                TipoLanzador.PC -> return "«${j.titulo}» es del PC: desde su ficha, «Jugar en el PC» o «Streaming»."
            }
        } catch (e: ActivityNotFoundException) { "No se pudo abrir: ¿sigue instalado el emulador?"
        } catch (e: SecurityException) { "Android no deja abrir esto: ${e.message}" }
        if (error == null) Sesion.empezar(c, j, paqueteAbierto)
        return error
    }

    fun aviso(c: Context, texto: String) = Toast.makeText(c, texto, Toast.LENGTH_LONG).show()

    /** Abre la ficha de un emulador en la tienda (Play Store, o el navegador si no hay). */
    fun tienda(c: Context, paquete: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$paquete")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { c.startActivity(i) } catch (_: ActivityNotFoundException) {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$paquete")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/**
 * Tiempo de juego aproximado. Cuando lanzas un juego (ROM o app) se apunta la hora; cuando vuelves a Hiedra, lo que ha pasado
 * se suma al juego (con un máximo de 12 h por sesión; menos de 20 s no cuenta). Es una aproximación: Android no dice cuándo
 * cierras un emulador. Los juegos web se miden dentro de su propia pantalla.
 */
object Sesion {
    private fun p(c: Context) = c.getSharedPreferences("hiedra_sesion", Context.MODE_PRIVATE)

    /** Reparte un tiempo de juego entre los días que abarca (una sesión que cruza la medianoche cuenta en los dos). */
    fun repartir(fin: Long, ms: Long, zona: java.util.TimeZone = java.util.TimeZone.getDefault()): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        val f = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply { timeZone = zona }
        var hasta = fin; var resto = ms
        while (resto > 0) {
            val cal = java.util.Calendar.getInstance(zona).apply { timeInMillis = hasta - 1 }
            val inicioDia = cal.apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
            val enDia = minOf(resto, hasta - inicioDia)
            val k = f.format(java.util.Date(hasta - 1))
            out[k] = (out[k] ?: 0L) + enDia
            resto -= enDia; hasta = inicioDia
        }
        return out
    }

    fun conDias(j: Juego, fin: Long, ms: Long): Map<String, Long> = conDias(j.propias.dias, fin, ms)

    fun conDias(dias: Map<String, Long>, fin: Long, ms: Long): Map<String, Long> {
        val d = HashMap(dias)
        repartir(fin, ms).forEach { (k, v) -> d[k] = (d[k] ?: 0L) + v }
        return d.entries.sortedByDescending { it.key }.take(400).associate { it.key to it.value }
    }

    /** [paquete]: la app que se ha abierto (el emulador o el juego), para medir cuánto estuvo delante si hay permiso de uso. */
    /** Apunta que se ha empezado a jugar: «Jugando» si estaba pendiente, la última vez y una vez más. */
    fun marcarJugado(j: Juego) {
        val k = if (j.estado == Estado.PENDIENTE) j.conEstado(Estado.JUGANDO) else j
        Biblioteca.poner(k.copy(ultima = System.currentTimeMillis(), propias = j.propias.copy(veces = j.propias.veces + 1)))
    }

    fun empezar(c: Context, j: Juego, paquete: String = "") {
        marcarJugado(j)
        if (j.principal?.tipo != TipoLanzador.WEB) {
            p(c).edit().putString("id", j.id).putLong("desde", System.currentTimeMillis()).putString("paq", paquete).apply()
            // Con el permiso de uso, la sesión se cierra sola cuando el juego deja de estar delante (sin esperar a volver a Hiedra)
            if (paquete.isNotBlank()) SesionWorker.programar(c)
        }
    }

    /** ¿Hay una sesión de juego abierta (lanzada y sin cerrar)? */
    fun abierta(c: Context): Boolean = p(c).contains("id")
    /** Cuánto lleva abierta la sesión (0 si no hay). */
    fun abiertaDesde(c: Context, ahora: Long = System.currentTimeMillis()): Long = p(c).getLong("desde", 0L).takeIf { it > 0 }?.let { ahora - it } ?: 0L

    /** Con el permiso de uso: ¿sigue delante la app del juego de la sesión abierta? null = no se sabe (sin permiso, sin pasos). */
    fun sigueDelante(c: Context, ahora: Long = System.currentTimeMillis()): Boolean? {
        val p = p(c); val paq = p.getString("paq", "") ?: ""; val desde = p.getLong("desde", 0L)
        if (paq.isBlank() || desde <= 0L) return null
        val d = Uso.tiempoDelante(c, paq, desde, ahora) ?: return null
        return sigueDelante(d, ahora)
    }
    /** Lo que dice [Uso.delante]: si contó hasta el final es que seguía delante. Sin móvil: se prueba. */
    fun sigueDelante(d: Uso.Delante, hasta: Long): Boolean = d.ms > 0 && d.fin >= hasta

    /** Sin permiso de uso: todo el rato hasta volver (como mucho 12 h). */
    fun aOjo(desde: Long, ahora: Long): Long = (ahora - desde).coerceAtMost(12L * 3600_000)

    /** Al volver a Hiedra: suma el tiempo de la sesión que había abierta (exacto si Android nos deja ver cuándo estuvo delante la app). */
    fun cerrar(c: Context) {
        val p = p(c); val id = p.getString("id", null) ?: return
        val desde = p.getLong("desde", 0L); val paq = p.getString("paq", "") ?: ""; p.edit().clear().apply()
        SesionWorker.cancelar(c)
        val ahora = System.currentTimeMillis()
        val exacto = Uso.tiempoDelante(c, paq, desde, ahora)
        val ms = exacto?.ms ?: aOjo(desde, ahora)
        val fin = exacto?.fin ?: ahora
        if (ms >= 20_000) Biblioteca.por(id)?.let { Biblioteca.poner(it.copy(propias = it.propias.copy(ms = it.propias.ms + ms, dias = conDias(it, fin, ms)))) }
        // Volver de jugar: si el guardado de este móvil ha cambiado, se sube a la nube
        Biblioteca.por(id)?.takeIf { NubeP.activo(c, it) }?.let { j ->
            val app = c.applicationContext
            kotlin.concurrent.thread { runCatching { NubeP.carpetaReal(app, NubeP.carpetas[j.sistema]!!)?.let { NubeP.despues(app, j, it) } } }
        }
    }

    fun sumar(j: Juego, ms: Long) { if (ms >= 5_000) Biblioteca.por(j.id)?.let { Biblioteca.poner(it.copy(propias = it.propias.copy(ms = it.propias.ms + ms, dias = conDias(it, System.currentTimeMillis(), ms)))) } }
}
