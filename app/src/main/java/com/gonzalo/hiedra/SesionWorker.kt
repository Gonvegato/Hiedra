package com.gonzalo.hiedra

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Cerrar la sesión de juego sin esperar a que vuelvas a Hiedra. Si lanzas un juego y luego te vas a otra cosa (o Hiedra no es tu
 * pantalla de inicio), las horas solo se apuntaban al abrir Hiedra otra vez, y hasta entonces no llegaban a tu cuenta ni al Diario de
 * Azabache. Con el permiso de uso, cada cuarto de hora se mira si el emulador (o el juego) sigue delante; cuando ya no lo está, se
 * cierra la sesión aquí mismo y se sube. Sin el permiso no se puede saber: se cierra al volver, como siempre.
 */
class SesionWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        if (!Sesion.abierta(c) || Actualizador.primerPlano) return Result.success()   // ya cerrada, o Hiedra delante (la cierra ella)
        when (Sesion.sigueDelante(c)) {
            true, null -> { if (Sesion.abiertaDesde(c) < MAXIMO) programar(c, ESPERA, desdeElTrabajo = true) }   // sigue jugando (o no se sabe): se vuelve a mirar
            false -> {
                iniciar(c)
                Sesion.cerrar(c)
                if (Cuenta.vinculada) runCatching { SincroP.subir(c, forzar = true) }
            }
        }
        return Result.success()
    }

    companion object {
        const val ESPERA = 15L * 60_000L
        /** Más de 12 horas sin saber nada: se deja de mirar (al volver a Hiedra se cierra con el tope de siempre). */
        const val MAXIMO = 12L * 3_600_000L

        /**
         * Al lanzar un juego (solo sirve con el permiso de uso; sin él no hay nada que mirar). Desde el propio trabajo ([desdeElTrabajo]),
         * la siguiente mirada se encadena detrás de esta (reemplazarla cancelaría la que está corriendo).
         */
        fun programar(c: Context, espera: Long = ESPERA, desdeElTrabajo: Boolean = false) {
            if (!Uso.permitido(c)) return
            runCatching {
                val req = OneTimeWorkRequestBuilder<SesionWorker>().setInitialDelay(espera, TimeUnit.MILLISECONDS).build()
                WorkManager.getInstance(c).enqueueUniqueWork("sesion", if (desdeElTrabajo) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE, req)
            }
        }

        fun cancelar(c: Context) { runCatching { WorkManager.getInstance(c).cancelUniqueWork("sesion") } }
    }
}
