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
import com.example.myapplication.data.AlgorithmPreferences
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * DailyStreakWorker — zažene se ob polnoči vsakega dne.
 *
 * Logika:
 *  - Prebere plan iz SharedPrefs: kateri je bil danes "plan dan" (workout ali rest)
 *  - Če je bil REST dan → streak +1 avtomatično (ni treba nič narediti)
 *  - Če je bil WORKOUT dan in je bil opravljen → streak je že bil povečan v completeWorkoutSession()
 *  - Če je bil WORKOUT dan in NI bil opravljen → streak = 0, recovery mode
 *  - Načrtuje naslednji Worker (za naslednjo polnoč)
 *
 * STREAK se povečuje TAKOJ ob zaključku vadbe (v completeWorkoutSession).
 * Ta Worker skrbi samo za rest-day avtomatski streak in za reset ob zamujeni vadbi.
 */
class WeeklyStreakWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val email = Firebase.auth.currentUser?.email ?: return Result.success()
        Log.d(TAG, "Daily streak check running for $email")

        val prefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
        val userPrefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)

        // Kateri dan v planu je bil VČERAJ (Worker se zažene ob polnoči = začetek novega dne)
        // "yesterday_was_rest" je boolean ki ga nastavimo zjutraj ko Worker načrtuje naslednji dan
        val yesterdayWasRest = prefs.getBoolean("yesterday_was_rest", false)
        val workoutDoneYesterday = prefs.getBoolean("workout_done_yesterday", false)
        val currentStreak = prefs.getInt("streak_days", 0)
        val planDayBeforeCheck = prefs.getInt("plan_day", 1)

        Log.d(TAG, "yesterdayWasRest=$yesterdayWasRest, workoutDoneYesterday=$workoutDoneYesterday, streak=$currentStreak")

        // Preveri kdaj je bil streak nazadnje posodobljen
        val keyLastUpdate = "${email}_last_streak_update_date"
        val lastUpdate = userPrefs.getString(keyLastUpdate, "") ?: ""
        val yesterdayDate = java.time.LocalDate.now().minusDays(1).toString()
        val alreadyUpdatedForYesterday = (lastUpdate == yesterdayDate)

        when {
            yesterdayWasRest -> {
                if (alreadyUpdatedForYesterday) {
                    Log.d(TAG, "✅ Rest day passed, streak already updated yesterday. Keeping at $currentStreak")
                } else {
                    // Rest day → streak +1 avtomatično (če še ni bil)
                    val newStreak = currentStreak + 1
                    prefs.edit()
                        .putInt("streak_days", newStreak)
                        .putBoolean("workout_done_yesterday", false)
                        .apply()

                    // Posodobi last_streak_update_date na včeraj (da danes šteje kot nov dan)
                    userPrefs.edit().putString(keyLastUpdate, yesterdayDate).apply()

                    saveStreakToFirestore(newStreak, yesterdayDate)
                    Log.d(TAG, "✅ Rest day auto-complete. Streak: $currentStreak → $newStreak")
                }

                // Povečaj težavnost (recovery konec)
                if (AlgorithmPreferences.isRecoveryMode(context)) {
                    AlgorithmPreferences.endRecoveryMode(context)
                }
            }
            workoutDoneYesterday -> {
                // Workout dan in je bil opravljen → streak je bil že povečan takoj ob zaključku vadbe
                // Samo reset flag
                prefs.edit()
                    .putBoolean("workout_done_yesterday", false)
                    .apply()
                Log.d(TAG, "✅ Workout day completed. Streak stays at $currentStreak (already incremented)")
            }
            else -> {
                // Workout dan in NI bil opravljen → reset streaka
                prefs.edit()
                    .putInt("streak_days", 0)
                    .putBoolean("workout_done_yesterday", false)
                    .apply()

                AlgorithmPreferences.startRecoveryMode(
                    context,
                    missedDays = 1,
                    planDayBeforeBreak = planDayBeforeCheck
                )
                saveStreakToFirestore(0, null)
                Log.d(TAG, "❌ Workout missed! Streak reset to 0. Recovery mode activated.")
            }
        }

        // Nastavi flag za danes (ali je danes rest day)
        // Plan day je bil že povečan ob zaključku vadbe ali ostaja isti
        val todayPlanDay = prefs.getInt("plan_day", 1)
        scheduleTomorrowFlags(context, prefs, todayPlanDay)

        // Načrtuj Worker za jutri ob polnoči
        scheduleNext(context)

        return Result.success()
    }

    private suspend fun saveStreakToFirestore(streak: Int, date: String?) {
        try {
            val data = if (date != null) {
                mapOf("streak_days" to streak, "last_streak_update_date" to date)
            } else {
                mapOf("streak_days" to streak)
            }

            com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore update failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "DailyStreakWorker"
        private const val WORK_NAME = "daily_streak_check"

        /**
         * Nastavi flag "yesterday_was_rest" za jutri glede na plan.
         * Kliče se iz completeWorkoutSession in ob zagonu Workerja.
         */
        fun scheduleTomorrowFlags(
            context: Context,
            prefs: android.content.SharedPreferences,
            currentPlanDay: Int
        ) {
            // Preberi plan iz DataStore (sinhrono — samo prefs cache)
            // Plan dan je 1-based, plan.weeks[0].days[0] je dan 1
            // Preprosta logika: "today_is_rest" se nastavi v completeWorkoutSession
            // Tukaj samo prenesemo "today" → "yesterday"
            val todayIsRest = prefs.getBoolean("today_is_rest", false)
            val workoutDoneToday = run {
                val lastWorkoutEpoch = prefs.getLong("last_workout_epoch", 0L)
                if (lastWorkoutEpoch == 0L) false
                else java.time.LocalDate.ofEpochDay(lastWorkoutEpoch) == java.time.LocalDate.now()
            }

            prefs.edit()
                .putBoolean("yesterday_was_rest", todayIsRest)
                .putBoolean("workout_done_yesterday", workoutDoneToday)
                .apply()

            Log.d(TAG, "Tomorrow flags set: yesterdayWillBeRest=$todayIsRest, workoutDoneToday=$workoutDoneToday")
        }

        /**
         * Načrtuje Worker za naslednjo polnoč (00:01).
         */
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

        /** Kliči ob login — zagotovi da je Worker vedno načrtovan */
        fun ensureScheduled(context: Context, startOfWeek: String = "Monday") {
            scheduleNext(context)
        }

        /**
         * Za testiranje — takoj požene Worker simulacijo polnoči.
         * Kliči iz DeveloperSettingsScreen.
         */
        fun simulateDayPass(context: Context) {
            val prefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
            val currentPlanDay = prefs.getInt("plan_day", 1)
            scheduleTomorrowFlags(context, prefs, currentPlanDay)

            val request = OneTimeWorkRequestBuilder<WeeklyStreakWorker>()
                .setInitialDelay(0, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("simulate_day_pass", ExistingWorkPolicy.REPLACE, request)

            Log.d(TAG, "🧪 Simulating day pass for testing")
        }
    }
}
