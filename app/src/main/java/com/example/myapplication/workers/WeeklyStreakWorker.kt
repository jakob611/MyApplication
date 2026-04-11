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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate
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
            val repository = com.example.myapplication.data.gamification.FirestoreGamificationRepository()
            val useCase = com.example.myapplication.domain.gamification.ManageGamificationUseCase(repository)
            useCase.executeMidnightStreakCheck()
            Log.d(TAG, "Midnight streak check completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing midnight streak check.", e)
            return Result.retry()
        }
        scheduleNext(context)
        return Result.success()
    }
    companion object {
        private const val TAG = "DailyStreakWorker"
        private const val WORK_NAME = "daily_streak_check"
        fun scheduleTomorrowFlags(
            context: Context,
            prefs: android.content.SharedPreferences,
            currentPlanDay: Int
        ) {
            // Keep this interface alive if called by other files for now
        }
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
        fun ensureScheduled(context: Context, startOfWeek: String = "Monday") {
            scheduleNext(context)
        }
        fun simulateDayPass(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeeklyStreakWorker>()
                .setInitialDelay(0, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("simulate_day_pass", ExistingWorkPolicy.REPLACE, request)
            Log.d(TAG, "Simulating day pass for testing")
        }
    }
}
