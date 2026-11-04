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
        return try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { NubeP.subirRoms(c) }; Result.success() } catch (e: Exception) { Result.retry() }
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
