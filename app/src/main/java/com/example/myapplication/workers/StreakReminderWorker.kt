package com.example.myapplication.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.myapplication.MainActivity
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * StreakReminderWorker — vsak dan ob 20:00 preveri, ali je vadba opravljena.
 *
 * Logika:
 * - Če je danes REST dan → ne pošlje notifikacije (streak bo +1 avtomatično ob polnoči)
 * - Če je danes WORKOUT dan in je vadba že opravljena → ne pošlje notifikacije
 * - Če je danes WORKOUT dan in vadba NI opravljena → pošlje motivacijsko notifikacijo
 *
 * Motivacijska sporočila se personalizirajo glede na:
 * - Trenutni streak
 * - Cilj (goal) iz plana
 * - Dan v planu
 */
class StreakReminderWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "StreakReminderWorker"
        private const val WORK_NAME = "streak_reminder_20h"
        const val CHANNEL_ID = "streak_reminder_channel"
        private const val NOTIFICATION_ID = 1001

        /**
         * Načrtuje opomnik za danes ob 20:00.
         * Kliči ob loginu in ob zagonu aplikacije.
         */
        fun scheduleForToday(context: Context) {
            val now = LocalDateTime.now()
            val targetTime = LocalDateTime.of(now.toLocalDate(), LocalTime.of(20, 0))

            // Če je 20:00 danes že minila, načrtuj za jutri ob 20:00
            val finalTarget = if (now.isBefore(targetTime)) targetTime
                             else targetTime.plusDays(1)

            val delayMs = java.time.Duration.between(now, finalTarget).toMillis()
            val delayMinutes = if (delayMs > 0) delayMs / 60000 else 1L

            val request = OneTimeWorkRequestBuilder<StreakReminderWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)

            Log.d(TAG, "Scheduled streak reminder in ${delayMinutes}min (at $finalTarget)")
        }

        /**
         * Ustvari notification channel. Kliči ob zagonu aplikacije.
         */
        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Streak Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily reminder to complete your workout and keep your streak alive"
                enableVibration(true)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        // Preveri ali je uporabnik prijavljen
        if (Firebase.auth.currentUser == null) {
            Log.d(TAG, "No user logged in — skipping reminder")
            scheduleForTomorrow()
            return Result.success()
        }

        val prefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
        val todayIsRest = prefs.getBoolean("today_is_rest", false)
        val lastWorkoutEpoch = prefs.getLong("last_workout_epoch", 0L)
        val workoutDoneToday = if (lastWorkoutEpoch == 0L) false
            else LocalDate.ofEpochDay(lastWorkoutEpoch) == LocalDate.now()
        val currentStreak = prefs.getInt("streak_days", 0)
        val planDay = prefs.getInt("plan_day", 1)

        Log.d(TAG, "Reminder check: todayIsRest=$todayIsRest, workoutDone=$workoutDoneToday, streak=$currentStreak")

        when {
            todayIsRest -> {
                // Rest dan — ni potreben opomnik
                Log.d(TAG, "Today is rest day — no reminder needed")
            }
            workoutDoneToday -> {
                // Vadba že opravljena — pošlji čestitko
                sendCongratulationsNotification(currentStreak)
            }
            else -> {
                // Vadba še ni opravljena — pošlji opomnik
                sendReminderNotification(currentStreak, planDay)
            }
        }

        // Načrtuj za jutri ob 20:00
        scheduleForTomorrow()

        return Result.success()
    }

    private fun sendReminderNotification(streak: Int, planDay: Int) {
        val title = getReminderTitle(streak)
        val message = getReminderMessage(streak, planDay)

        sendNotification(title, message, isReminder = true)
        Log.d(TAG, "Sent reminder notification: streak=$streak")
    }

    private fun sendCongratulationsNotification(streak: Int) {
        val title = "🔥 Great job today!"
        val message = when {
            streak >= 30 -> "You're on a $streak day streak — absolutely unstoppable! 💪"
            streak >= 14 -> "Two weeks strong! $streak day streak. You're building an amazing habit! 🏆"
            streak >= 7 -> "One week done! $streak day streak. Keep pushing! ⭐"
            streak >= 3 -> "Day $streak streak! You're getting into the rhythm. Keep it up! 🌟"
            else -> "Workout done! Great start to building your streak. 💪"
        }
        sendNotification(title, message, isReminder = false)
        Log.d(TAG, "Sent congratulations notification: streak=$streak")
    }

    private fun getReminderTitle(streak: Int): String {
        return when {
            streak == 0 -> "⚡ Start your streak today!"
            streak >= 30 -> "🔥 Don't break your $streak day streak!"
            streak >= 14 -> "💪 $streak days strong — don't stop now!"
            streak >= 7 -> "🌟 $streak day streak at risk!"
            streak >= 3 -> "🔥 Keep your $streak day streak alive!"
            streak == 1 -> "⭐ Day 2 starts today!"
            else -> "🏋️ Time to work out!"
        }
    }

    private fun getReminderMessage(streak: Int, planDay: Int): String {
        val week = ((planDay - 1) / 7) + 1
        val dayInWeek = ((planDay - 1) % 7) + 1
        val dayContext = "Week $week, Day $dayInWeek"

        return when {
            streak == 0 -> "Start your fitness journey today. Every champion starts somewhere. 🏆 ($dayContext)"
            streak >= 30 -> "You've worked $streak days straight! Don't let tonight be the end of that. 💎 ($dayContext)"
            streak >= 14 -> "Two weeks of consistency! Your workout for today is waiting. 🎯 ($dayContext)"
            streak >= 7 -> "One week of dedication — tonight is not the night to skip! 🔥 ($dayContext)"
            streak >= 3 -> "3+ day streak! One workout away from making it another day. ⚡ ($dayContext)"
            else -> "Your daily workout is waiting. Just 20-30 minutes can change your day! 💪 ($dayContext)"
        }
    }

    private fun sendNotification(title: String, message: String, isReminder: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Ustvari channel če ne obstaja
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Streak Reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Daily reminder to complete your workout and keep your streak alive"
            enableVibration(true)
        }
        notificationManager.createNotificationChannel(channel)

        // Intent za odprtje aplikacije
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .apply {
                if (isReminder) {
                    setVibrate(longArrayOf(0, 300, 100, 300))
                }
            }
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun scheduleForTomorrow() {
        val now = LocalDateTime.now()
        val tomorrow = now.toLocalDate().plusDays(1)
        val targetDateTime = LocalDateTime.of(tomorrow, LocalTime.of(20, 0))

        val delayMs = java.time.Duration.between(now, targetDateTime).toMillis()
        val delayMinutes = if (delayMs > 0) delayMs / 60000 else 1L

        val request = OneTimeWorkRequestBuilder<StreakReminderWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)

        Log.d(TAG, "Scheduled next reminder in ${delayMinutes}min (at $targetDateTime)")
    }
}






