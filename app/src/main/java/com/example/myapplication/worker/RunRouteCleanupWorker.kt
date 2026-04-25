package com.example.myapplication.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * RunRouteCleanupWorker — tedenski čistilec starih GPS .json datotek.
 *
 * Problem: `RunRouteStore` shranjuje vsak tek kot .json datoteko v
 *   `context.filesDir/run_routes/{sessionId}.json`.
 *   Brez čiščenja se datoteke kopičijo za vedno (potencialno stotine MB po letu).
 *
 * Rešitev: Periodni Worker (1x tedensko):
 *   - Zbriše vse .json datoteke starejše od MAX_AGE_DAYS (privzeto 60 dni).
 *   - Hrani novejše datoteke nedotaknjene (za prikaz na ActivityLogScreen).
 *   - Logiraj koliko je bilo zbrisano za diagnostiko.
 *
 * Registracija: `RunRouteCleanupWorker.ensureScheduled(context)` kliči ob prijavi.
 */
class RunRouteCleanupWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "RunRouteCleanupWorker"
        private const val WORK_NAME = "run_route_cleanup_weekly"

        /** Datoteke starejše od tega se zbrišejo. */
        private const val MAX_AGE_DAYS = 60L

        /** Zaplanira periodni Worker (1x tedensko). Idempotenten klic. */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<RunRouteCleanupWorker>(
                7, TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Če že teče, ne zamenjaj
                request
            )
            Log.d(TAG, "✅ RunRouteCleanupWorker zaplaniran (1x tedensko)")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val dir = File(context.filesDir, "run_routes")
            if (!dir.exists()) return Result.success()

            val cutoffMs = System.currentTimeMillis() - (MAX_AGE_DAYS * 24 * 60 * 60 * 1000L)
            val allFiles = dir.listFiles() ?: return Result.success()

            var deletedCount = 0
            var keptCount = 0
            var totalFreedBytes = 0L

            allFiles.forEach { file ->
                if (file.lastModified() < cutoffMs) {
                    val size = file.length()
                    if (file.delete()) {
                        deletedCount++
                        totalFreedBytes += size
                        Log.v(TAG, "🗑️ Zbrisano: ${file.name} (${size / 1024}KB)")
                    }
                } else {
                    keptCount++
                }
            }

            val freedKb = totalFreedBytes / 1024
            Log.i(TAG, "✅ Čiščenje zaključeno: zbrisano=$deletedCount, ohranjeno=$keptCount, sproščeno=${freedKb}KB")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Napaka pri čiščenju run_routes: ${e.message}")
            Result.retry()
        }
    }
}

