package com.example.myapplication.workers
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.myapplication.data.gamification.GamificationFactory
import com.example.myapplication.data.store.FirestoreHelper
import com.example.myapplication.domain.model.UserDayStatus
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

class WeeklyStreakWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val email = Firebase.auth.currentUser?.email ?: return Result.success()
        Log.d(TAG, "Daily streak check running for $email")
        try {
            // Faza 54 — Anomaly #4 Fix: Pred midnight checkom ugotovi, ali je včeraj
            // bil načrtovan počitniški dan v planu. Prenaša informacijo v transakcijo,
            // da preprečimo napačen streak reset na počitniških dnevih.
            val yesterdayWasRestDay = determineYesterdayWasRestDay()
            GamificationFactory.provide(context).executeMidnightStreakCheck(yesterdayWasRestDay)
            Log.d(TAG, "Midnight streak check completed (yesterdayWasRestDay=$yesterdayWasRestDay).")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing midnight streak check.", e)
            return Result.retry()
        }
        scheduleNext(context)
        return Result.success()
    }

    /**
     * Faza 54 — Anomaly #4 Fix: Prebere plan iz Firestore in ugotovi, ali je VČERAJ
     * bil načrtovan počitniški dan.
     *
     * Logika:
     * 1. Preberi user dokument → dailyHistory[yesterday] in plan_day.
     * 2. Če je status že konkluziven (DONE, FROZEN, MISSED) → false (ni potreben override).
     * 3. Če je REST_DAY_PENDING → true (app bila odprta, počitniški dan znan).
     * 4. Če je WORKOUT_PENDING → preberi user_plans in preveri če je plan_day dan isRestDay=true.
     *    Kadar status ni bil nastavljen (user ni odprl app), plan_day se NI inkrementiral,
     *    zato trenutni plan_day == včerajšnji plan_day — to je varna predpostavka.
     *
     * @return true = včeraj je bil počitniški dan; false = delovni dan ali napaka pri branju.
     */
    private suspend fun determineYesterdayWasRestDay(): Boolean {
        return try {
            val userRef = FirestoreHelper.getCurrentUserDocRef() ?: return false
            val userDoc = userRef.get().await()

            val yesterdayStr = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .date.minus(1, DateTimeUnit.DAY).toString()

            @Suppress("UNCHECKED_CAST")
            val dailyHistory = (userDoc.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
            val yesterdayStatus = UserDayStatus.fromFirestore(dailyHistory[yesterdayStr]?.toString())

            // Če je včeraj že konkluziven → ni potreben plan check
            if (yesterdayStatus.isDoneToday
                || yesterdayStatus == UserDayStatus.FROZEN
                || yesterdayStatus == UserDayStatus.MISSED) return false

            // App je bila odprta in počitniški dan je bil zaznan → zanesljiv indikator
            if (yesterdayStatus == UserDayStatus.REST_DAY_PENDING) return true

            // WORKOUT_PENDING → status ni bil nastavljen; potrebujemo plan check.
            // plan_day se NE inkrementira na počitniških dnevih, torej trenutni plan_day
            // == včerajšnji plan_day — varno ga posvetujemo v planu.
            val planDay = userDoc.getLong("plan_day")?.toInt() ?: 1

            val planDoc = FirebaseFirestore.getInstance()
                .collection("user_plans").document(userRef.id).get().await()

            @Suppress("UNCHECKED_CAST")
            val plans = planDoc.get("plans") as? List<Map<String, Any>> ?: return false

            plans.flatMap { plan ->
                @Suppress("UNCHECKED_CAST")
                (plan["weeks"] as? List<Map<String, Any>> ?: emptyList())
                    .flatMap { week ->
                        @Suppress("UNCHECKED_CAST")
                        week["days"] as? List<Map<String, Any>> ?: emptyList()
                    }
            }.any { day ->
                (day["dayNumber"] as? Long)?.toInt() == planDay
                    && (day["isRestDay"] as? Boolean) == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "determineYesterdayWasRestDay spodletel — privzeto false (delovni dan)", e)
            false  // Varno privzeto: penaliziraj samo dejansko zamešane delovne dni
        }
    }

    companion object {
        private const val TAG = "DailyStreakWorker"
        private const val WORK_NAME = "daily_streak_check"
        // ⚠️ DEAD CODE — scheduleTomorrowFlags() je bila stara SharedPrefs logika (pred Faza 21).
        // Obdržana za backward-compatible kompilacijo; zbriši ob naslednjem refactoring prehodu.
        @Suppress("UNUSED_PARAMETER", "unused")
        fun scheduleTomorrowFlags(
            context: Context,
            prefs: android.content.SharedPreferences,
            currentPlanDay: Int
        ) { /* no-op stub */ }

        fun scheduleNext(context: Context) {
            val now = LocalDateTime.now()
            val tomorrow = now.toLocalDate().plusDays(1)
            val targetDateTime = LocalDateTime.of(tomorrow, LocalTime.of(0, 1))
            val delayMs = java.time.Duration.between(now, targetDateTime).toMillis()
            val delayMinutes = if (delayMs > 0) delayMs / 60000 else 1L
            val request = OneTimeWorkRequestBuilder<WeeklyStreakWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            Log.d(TAG, "Scheduled next daily check in ${delayMinutes}min (at $targetDateTime)")
        }
        // startOfWeek parameter obdržan za backward compatibility (MainAppContent.kt klic).
        // Vrednost se ne uporablja — midnight check je epoch-based (neodvisen od dneva tedna).
        @Suppress("UNUSED_PARAMETER")
        fun ensureScheduled(context: Context, startOfWeek: String = "Monday") {
            scheduleNext(context)
        }
        fun simulateDayPass(context: Context) {
            // ⛔ Faza 23: Samo v debug buildu — prepreči naključni klic v produkciji
            if (!com.example.myapplication.BuildConfig.DEBUG) return
            val request = OneTimeWorkRequestBuilder<WeeklyStreakWorker>()
                .setInitialDelay(0, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("simulate_day_pass", ExistingWorkPolicy.REPLACE, request)
            Log.d(TAG, "Simulating day pass for testing")
        }
    }
}
