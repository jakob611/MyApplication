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
import com.example.myapplication.data.daily.DailyLogRepository
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * WorkManager Worker za zanesljiv daily sync v Firestore.
 *
 * Prednosti pred onPause() + direktni Firestore klic:
 *  - Zagotovljeno izvajanje tudi po uboju procesa
 *  - Čaka na omrežje (NetworkType.CONNECTED) — ne odpove brez interneta
 *  - Android ga NE ubije med izvajanjem (garantiran čas za dokončanje)
 *  - Result.retry() ob napaki — samodejno poskusi znova z backoffom
 *  - REPLACE policy — vedno ima najnovejše podatke
 */
class DailySyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DailySyncWorker"
        private const val WORK_NAME = "daily_nutrition_sync"

        /**
         * Razporedi sync. Kliče se iz onPause().
         * REPLACE: če je že v čakalni vrsti, zamenja s svežim → vedno najnovejši podatki.
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

        // Sinciraj vse datume ki imajo podatke a niso bili sincirani
        // (normalno: today + včeraj če je bil zamuden)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
        val yesterday = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

        val datesToSync = listOf(yesterday, today).filter { date ->
            !DailySyncManager.isSynced(applicationContext, date) &&
            DailySyncManager.hasDataForDate(applicationContext, date)
        }

        if (datesToSync.isEmpty()) {
            Log.d(TAG, "Nothing to sync — all dates already synced")
            return Result.success()
        }

        return try {
            for (date in datesToSync) {
                val waterMl = applicationContext
                    .getSharedPreferences(DailySyncManager.PREFS_WATER, Context.MODE_PRIVATE)
                    .getInt("water_$date", 0)
                // OPOZORILO: burnedCalories se NE sync-a iz lokalnega cache-a.
                // Single Source of Truth za burned kalorije je Firestore (HC delta + Workout transakcija).
                val foodsJson = DailySyncManager.loadFoodsJson(applicationContext, date)

                // Razčleni hrano PRED vstopom v transakcijo
                val parsedItems: List<Map<String, Any>> = if (!foodsJson.isNullOrBlank()) {
                    runCatching {
                        val arr = JSONArray(foodsJson)
                        (0 until arr.length()).map { i ->
                            val obj = arr.getJSONObject(i)
                            mutableMapOf<String, Any>().also { map ->
                                obj.keys().forEach { key -> map[key] = obj.get(key) }
                            }
                        }
                    }.getOrDefault(emptyList())
                } else emptyList()

                // VARNO: Atomarni zapis skozi DailyLogRepository (Firestore Transaction)
                // Ni več .set(payload, SetOptions.merge()) — ni tveganja za Race Condition.
                DailyLogRepository().updateDailyLog(date) { data ->
                    data["waterMl"] = waterMl
                    if (parsedItems.isNotEmpty()) {
                        data["items"] = parsedItems
                    }
                    // burnedCalories, hcBurnedCalories ostajata nedotaknjeni
                }

                // Označi kot sincirano šele po uspešnem upisu
                DailySyncManager.markSynced(applicationContext, date)
                Log.d(TAG, "OK [$date]: water=$waterMl ml, foods=${parsedItems.size}")
            }
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Failed: ${e.message} — WorkManager bo poskusil znova")
            Result.retry()
        }
    }
}
