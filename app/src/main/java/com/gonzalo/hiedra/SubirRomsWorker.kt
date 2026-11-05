package com.gonzalo.hiedra

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.BackoffPolicy
import java.util.concurrent.TimeUnit

/** Sube las ROMs que faltan a tu kDrive, solo con wifi (son archivos grandes). Si se corta, WorkManager lo reintenta. */
class SubirRomsWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Biblioteca.cargar(c)
        // Otra vuelta mientras suba algo: las ROMs que añadas mientras sube las anteriores también se suben
        return try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { var vueltas = 0; while (NubeP.subirRoms(c) > 0 && ++vueltas < 20) Unit }; Result.success() } catch (e: Exception) { Result.retry() }
    }

    companion object {
        fun programar(c: Context) {
            if (!Cuenta.vinculada || !KDrive.on(c) || !NubeP.romsSolas(c)) return
            val req = OneTimeWorkRequestBuilder<SubirRomsWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES).build()
            WorkManager.getInstance(c).enqueueUniqueWork("subir-roms", ExistingWorkPolicy.KEEP, req)
        }
    }
}

/**
 * Baja a este móvil todas las ROMs de tu biblioteca que están en tu kDrive y aquí no (Ajustes → Avanzado), a la carpeta de bajadas
 * ([NubeP.carpetaBajadas]). Solo con wifi; si se corta, sigue luego por donde iba (las que ya bajó no se repiten).
 */
class BajarRomsWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        Biblioteca.cargar(c)
        if (!Cuenta.vinculada || !KDrive.on(c)) return Result.success()
        val raiz = NubeP.carpetaBajadas(c) ?: return Result.success()
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { for ((j, f) in NubeP.romsQueFaltan()) NubeP.bajarRomAqui(c, j, f, raiz) }
            Result.success()
        } catch (e: Exception) { Result.retry() }
    }

    companion object {
        fun programar(c: Context) {
            val req = OneTimeWorkRequestBuilder<BajarRomsWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES).build()
            WorkManager.getInstance(c).enqueueUniqueWork("bajar-roms", ExistingWorkPolicy.KEEP, req)
        }
    }
}
