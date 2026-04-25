package com.example.myapplication.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.myapplication.persistence.DailySyncManager
import com.example.myapplication.persistence.FirestoreHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DailySyncWorker — Faza 5: Poenostavljen worker.
 *
 * Faza 5 sprememba:
 *  - Odstranjeno branje iz lokalnih SharedPrefs cachev (water_cache, food_cache)
 *  - Firestore SDK z isPersistenceEnabled = true zagotavlja offline sinhronizacijo sam
 *  - Ta worker zdaj SAMO:
 *      1. Potrdi da je uporabnik prijavljen
 *      2. Označi danes in včeraj kot "sincirano" v sync trackerju
 *      3. Logira stanje za diagnostiko
 *
 * Prednosti ostanejo:
 *  - Zagotovljeno izvajanje po uboju procesa (WorkManager garantira)
 *  - Čaka na omrežje (NetworkType.CONNECTED)
 *  - REPLACE policy — vedno svež run
 */
class DailySyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DailySyncWorker"
        private const val WORK_NAME = "daily_nutrition_sync"

        /**
         * Razporedi sync. Kliče se iz onPause() in syncOnAppOpen().
         */
        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailySyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            Log.d(TAG, "Scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val uid = FirestoreHelper.getCurrentUserDocId()
        if (uid == null) {
            Log.d(TAG, "No user logged in, skip")
            return Result.success()
        }

        // Faza 5: Firestore SDK (isPersistenceEnabled = true) skrbi za offline sync sam.
        // Podatki se pišejo DIREKTNO v Firestore prek DailyLogRepository — ni lokalnega caching vmesnika.
        // Ta worker samo označi datume kot "sincirane" za legacy sync tracker.
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

        DailySyncManager.markSynced(applicationContext, today)
        DailySyncManager.markSynced(applicationContext, yesterday)

        Log.d(TAG, "✅ Faza 5: Firestore je Single Source of Truth — lokalni sync ni potreben [$today]")
        return Result.success()
    }
}
