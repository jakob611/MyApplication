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
import com.example.myapplication.data.UserPreferences
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker ki se sproži ob polnoči zadnjega dne v tednu.
 * Preveri ali je uporabnik opravil vse workoutou tega tedna:
 *   - DA  → streak + 1, Firestore update
 *   - NE  → streak = 0, aktiviraj recovery mode, Firestore update
 *
 * Načrtuje naslednji Workerji vsakič ko se zažene (enkrat tedensko).
 */
class WeeklyStreakWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val email = Firebase.auth.currentUser?.email ?: return Result.success()
        Log.d("WeeklyStreakWorker", "Running weekly streak check for $email")

        val prefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
        val weeklyDone = prefs.getInt("weekly_done", 0)
        val weeklyTarget = prefs.getInt("weekly_target", 3)
        val currentStreak = prefs.getInt("streak_weeks", 0)
        val planDayBeforeCheck = prefs.getInt("plan_day", 1)

        // Preberi start of week iz Firestore profila
        val userProfile = UserPreferences.loadProfileFromFirestore(email)
        val startDayOfWeek = when (userProfile?.startOfWeek) {
            "Sunday" -> DayOfWeek.SUNDAY
            "Saturday" -> DayOfWeek.SATURDAY
            else -> DayOfWeek.MONDAY
        }

        val today = LocalDate.now()

        val weekCompleted = weeklyDone >= weeklyTarget

        if (weekCompleted) {
            // ─── TEDEN USPEŠNO ZAKLJUČEN ─────────────────────────────────
            val newStreak = currentStreak + 1
            prefs.edit()
                .putInt("streak_weeks", newStreak)
                .putLong("last_completed_week", today.toEpochDay())
                .putInt("weekly_done", 0) // Reset za naslednji teden
                .apply()

            AlgorithmPreferences.incrementWeeklyDifficulty(context)
            if (AlgorithmPreferences.isRecoveryMode(context)) {
                AlgorithmPreferences.endRecoveryMode(context)
            }

            saveStreakToFirestore(newStreak, weeklyDone, weekCompleted = true)
            Log.d("WeeklyStreakWorker", "✅ Week complete! New streak: $newStreak")

        } else {
            // ─── TEDEN NI BIL ZAKLJUČEN — RESET ──────────────────────────
            val missedDays = weeklyTarget - weeklyDone
            prefs.edit()
                .putInt("streak_weeks", 0)
                .putInt("weekly_done", 0) // Reset za naslednji teden
                .apply()

            AlgorithmPreferences.startRecoveryMode(
                context,
                missedDays = missedDays,
                planDayBeforeBreak = planDayBeforeCheck
            )

            saveStreakToFirestore(0, weeklyDone, weekCompleted = false)
            Log.d("WeeklyStreakWorker", "❌ Streak reset. Missed $missedDays days. Recovery mode activated.")
        }

        // Načrtuj naslednji Worker (za konec naslednjega tedna)
        scheduleNext(context, startDayOfWeek)

        return Result.success()
    }

    private suspend fun saveStreakToFirestore(
        streak: Int,
        weeklyDone: Int,
        weekCompleted: Boolean
    ) {
        try {
            val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                ?: return
            Firebase.firestore.collection("users").document(uid)
                .set(
                    mapOf(
                        "streak_days" to streak,
                        "weekly_done" to weeklyDone,
                        "week_completed" to weekCompleted,
                        "is_recovery_week" to !weekCompleted
                    ),
                    SetOptions.merge()
                ).await()
        } catch (e: Exception) {
            Log.e("WeeklyStreakWorker", "Firestore update failed: ${e.message}")
        }
    }

    companion object {
        private const val WORK_NAME = "weekly_streak_check"

        /**
         * Načrtuje Worker da se zažene ob polnoči zadnjega dne v tednu.
         * Kliče se enkrat ob zagonu aplikacije in po vsakem izvajanju Workerja.
         */
        fun scheduleNext(context: Context, startDayOfWeek: DayOfWeek = DayOfWeek.MONDAY) {
            val now = LocalDateTime.now()
            val today = now.toLocalDate()

            // Zadnji dan tega tedna = dan pred začetkom naslednjega tedna
            val weekStart = today.with(TemporalAdjusters.previousOrSame(startDayOfWeek))
            val weekEnd = weekStart.plusDays(6) // 6 dni po začetku = zadnji dan

            // Čas izvajanja: polnoč (00:00) zadnjega dne tedna
            // Dejansko: konec tedna (23:59 + 1min = naslednji dan 00:00)
            val targetDateTime = LocalDateTime.of(weekEnd.plusDays(1), LocalTime.of(0, 1))

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

            Log.d("WeeklyStreakWorker", "Scheduled next check in ${delayMinutes}min (at $targetDateTime)")
        }

        /** Kliči ob login — zagotovi da je Worker vedno načrtovan */
        fun ensureScheduled(context: Context, startOfWeek: String = "Monday") {
            val dayOfWeek = when (startOfWeek) {
                "Sunday" -> DayOfWeek.SUNDAY
                "Saturday" -> DayOfWeek.SATURDAY
                else -> DayOfWeek.MONDAY
            }
            scheduleNext(context, dayOfWeek)
        }
    }
}
