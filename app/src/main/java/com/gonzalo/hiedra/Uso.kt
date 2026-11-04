package com.gonzalo.hiedra

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * El tiempo de juego exacto: con el permiso de «acceso al uso» (Ajustes → Juegos), al volver a Hiedra se mira cuánto estuvo DELANTE el
 * emulador (o la app del juego) desde que se lanzó, en vez de contar todo el rato hasta que vuelves. Sin el permiso, se cuenta como siempre.
 */
object Uso {
    /** ¿Tiene Hiedra el permiso de acceso al uso? */
    fun permitido(c: Context): Boolean = runCatching {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val modo = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        modo == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Abre la pantalla de Android donde se da el permiso. */
    fun pedir(c: Context) { runCatching { c.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }

    /** Un paso de la app por delante o por detrás (lo que Android apunta). */
    class Paso(val t: Long, val delante: Boolean)

    /** Lo que estuvo delante: el total y cuándo acabó la última vez (para repartirlo entre días). */
    class Delante(val ms: Long, val fin: Long)

    /**
     * Suma lo que la app estuvo delante entre [desde] y [hasta] a partir de sus pasos (en orden de tiempo). Si ya estaba delante al empezar
     * (su primer paso es «detrás»), cuenta desde [desde]; si sigue delante al acabar, cuenta hasta [hasta]. Sin red ni móvil: se prueba.
     */
    fun delante(pasos: List<Paso>, desde: Long, hasta: Long): Delante? {
        if (pasos.isEmpty() || hasta <= desde) return null
        var total = 0L; var abierto: Long? = null; var fin = desde
        for (p in pasos.sortedBy { it.t }) {
            val t = p.t.coerceIn(desde, hasta)
            if (p.delante) { if (abierto == null) abierto = t }
            else { val a = abierto ?: if (total == 0L && fin == desde) desde else null; if (a != null) { total += t - a; fin = t; abierto = null } }
        }
        abierto?.let { total += hasta - it; fin = hasta }
        return Delante(total, fin)
    }

    /** Los pasos de esa app entre dos horas, según Android (null si no hay permiso o nada que contar). */
    fun pasosDe(c: Context, paquete: String, desde: Long, hasta: Long): List<Paso>? {
        if (!permitido(c)) return null
        return runCatching {
            val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val ev = usm.queryEvents(desde - 60_000, hasta)
            val out = ArrayList<Paso>()
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.packageName != paquete) continue
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> out += Paso(e.timeStamp, true)
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> out += Paso(e.timeStamp, false)
                }
            }
            out
        }.getOrNull()
    }

    /** Cuánto estuvo delante esa app desde que se lanzó el juego (null = no se sabe: sin permiso o sin datos; se cuenta como siempre). */
    fun tiempoDelante(c: Context, paquete: String, desde: Long, hasta: Long = System.currentTimeMillis()): Delante? {
        if (paquete.isBlank()) return null
        val pasos = pasosDe(c, paquete, desde, hasta) ?: return null
        return delante(pasos, desde, hasta)
    }
}
