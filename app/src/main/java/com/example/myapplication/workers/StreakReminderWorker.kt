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
import com.example.myapplication.data.settings.UserProfileManager
import com.example.myapplication.persistence.FirestoreHelper
import com.example.myapplication.data.workout.FirestoreWorkoutRepository
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

    private data class ReminderContext(
        val streak: Int,
        val planDay: Int,
        val waterMl: Int,
        val consumedCalories: Int,
        val burnedCalories: Int,
        val streakFreezes: Int,
        val currentHour: Int
    )

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
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null || currentUser.email == null) {
            Log.d(TAG, "No user logged in — skipping reminder")
            scheduleForTomorrow()
            return Result.success()
        }

        val email = currentUser.email.orEmpty()
        val userProfile = UserProfileManager.loadProfile(email)

        // --- 1. QUIET HOURS PREVERBA (na podlagi uporabnikovih nastavitev) ---
        try {
            val now = LocalTime.now()
            val startParts = userProfile.quietHoursStart.split(":")
            val endParts = userProfile.quietHoursEnd.split(":")
            if (startParts.size == 2 && endParts.size == 2) {
                val start = LocalTime.of(startParts[0].toInt(), startParts[1].toInt())
                val end = LocalTime.of(endParts[0].toInt(), endParts[1].toInt())
                
                val inQuietHours = if (start.isBefore(end)) {
                    now.isAfter(start) && now.isBefore(end)
                } else {
                    now.isAfter(start) || now.isBefore(end)
                }
                
                if (inQuietHours) {
                    Log.d(TAG, "Current time is within quiet hours (${userProfile.quietHoursStart}-${userProfile.quietHoursEnd}) — skipping reminder")
                    scheduleForTomorrow()
                    return Result.success()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing quiet hours: ${e.message}")
        }

        // --- 2. PREVERI NASTAVITVE OBNOVITEV (Mute) ---
        if (userProfile.muteStreakReminders) {
            Log.d(TAG, "User muted streak reminders — skipping")
            scheduleForTomorrow()
            return Result.success()
        }

        // --- 3. PREBERI PODATKE O VADBI IN STREAKU ---
        val prefs = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
        val todayIsRest = prefs.getBoolean("today_is_rest", false)
        val lastWorkoutEpoch = prefs.getLong("last_workout_epoch", 0L)
        val workoutDoneToday = if (lastWorkoutEpoch == 0L) false
            else LocalDate.ofEpochDay(lastWorkoutEpoch) == LocalDate.now()
        val currentStreak = prefs.getInt("streak_days", 0)
        val planDay = prefs.getInt("plan_day", 1)
        val reminderContext = loadReminderContext(currentStreak, planDay)

        Log.d(TAG, "Reminder check: todayIsRest=$todayIsRest, workoutDone=$workoutDoneToday, streak=$currentStreak")

        // --- 4. ODLOČITEV KAJ NAREDITI NA PODLAGI STANJA ---
        when {
            todayIsRest -> {
                // Rest dan — ni potreben opomnik
                Log.d(TAG, "Today is rest day — no reminder needed")
            }
            workoutDoneToday -> {
                // Vadba že opravljena — pošlji čestitko
                sendCongratulationsNotification(reminderContext)
            }
            else -> {
                // Vadba še ni opravljena — pošlji opomnik
                sendReminderNotification(reminderContext)
            }
        }

        // --- 5. NAČRTUJ ZA JUTRI OB 20:00 ---
        scheduleForTomorrow()

        return Result.success()
    }

    private fun loadReminderContext(streak: Int, planDay: Int): ReminderContext {
        val dateKey = LocalDate.now().toString()
        val waterMl = context.getSharedPreferences("water_cache", Context.MODE_PRIVATE)
            .getInt("water_$dateKey", 0)
        val consumedCalories = context.getSharedPreferences("calories_cache", Context.MODE_PRIVATE)
            .getInt("calories_$dateKey", 0)
        val burnedCalories = context.getSharedPreferences("burned_cache", Context.MODE_PRIVATE)
            .getInt("burned_$dateKey", 0)

        val email = Firebase.auth.currentUser?.email
        // --- 6. PREBERI ŠTEVILO ZAMRZOVANJ (FROZEN DAYS) ---
        val streakFreezes = try {
            runCatching { UserProfileManager.loadProfile(email ?: "").streakFreezes }.getOrDefault(0)
        } catch(e:Exception) {
            0
        }

        return ReminderContext(
            streak = streak,
            planDay = planDay,
            waterMl = waterMl,
            consumedCalories = consumedCalories,
            burnedCalories = burnedCalories,
            streakFreezes = streakFreezes,
            currentHour = LocalDateTime.now().hour
        )
    }

    private fun sendReminderNotification(ctx: ReminderContext) {
        // A/B test determination based on simple hash of user ID or device time if no user
        val uid = Firebase.auth.currentUser?.uid ?: ""
        val toneVariant = if (uid.isNotEmpty()) Math.abs(uid.hashCode()) % 3 else 0
        Log.d(TAG, "Notification Tone Variant selected: $toneVariant")
        
        val title = getReminderTitle(ctx, toneVariant)
        val message = getReminderMessage(ctx, toneVariant)

        sendNotification(title, message, isReminder = true)
        Log.d(
            TAG,
            "Sent contextual reminder: streak=${ctx.streak}, water=${ctx.waterMl}, calories=${ctx.consumedCalories}, burned=${ctx.burnedCalories}, freezes=${ctx.streakFreezes}"
        )
    }

    private fun sendCongratulationsNotification(ctx: ReminderContext) {
        val uid = Firebase.auth.currentUser?.uid ?: ""
        val toneVariant = if (uid.isNotEmpty()) Math.abs(uid.hashCode()) % 3 else 0
        
        val title = "🔥 Great job today!"
        val message = when (toneVariant) {
            0 -> when { // Encouraging & Soft
                ctx.streak >= 30 -> "You're on a ${ctx.streak} day streak. So proud of your discipline! 🌟"
                ctx.streak >= 14 -> "Two weeks straight (${ctx.streak} days). You're doing amazing! ✨"
                ctx.streak >= 7 -> "One week down (${ctx.streak} days). Keep up the fantastic work! 🌻"
                ctx.waterMl < 1200 -> "You did great. Just remember to drink some water to recover better! 💧"
                else -> "Workout done. Pat yourself on the back! 👏"
            }
            1 -> when { // Strict & Coach
                ctx.streak >= 30 -> "Hit ${ctx.streak} days. Elite discipline. No excuses tomorrow. 💪"
                ctx.streak >= 14 -> "14+ days (${ctx.streak}). Don't lose the momentum now. 💯"
                ctx.streak >= 7 -> "7 days down. The real test starts now. Keep pushing. 🔥"
                ctx.waterMl < 1200 -> "Mission accomplished, but hydration is weak. Drink water. 🚰"
                else -> "Session logged. Get ready for the next one. 🏋️‍♂️"
            }
            else -> when { // Default / Data driven
                ctx.streak >= 30 -> "You're on a ${ctx.streak} day streak and still consistent. Elite discipline. 💪"
                ctx.streak >= 14 -> "Two weeks+ consistency (${ctx.streak} days). Keep this momentum! 🏆"
                ctx.streak >= 7 -> "Week streak secured (${ctx.streak} days). Nice execution today. ⭐"
                ctx.waterMl < 1200 -> "Workout done. Add some hydration before sleep to recover better. 💧"
                else -> "Workout done and streak alive. Great finish for today. 💪"
            }
        }
        
        sendNotification(title, message, isReminder = false)
        Log.d(TAG, "Sent congratulations notification: streak=${ctx.streak}")
    }

    private fun getReminderTitle(ctx: ReminderContext, toneVariant: Int): String {
        val isLate = ctx.currentHour >= 21
        return when (toneVariant) {
            0 -> when { // Encouraging
                 ctx.streak >= 14 && ctx.streakFreezes == 0 -> "Don't break your ${ctx.streak}-day streak! 🥺"
                 ctx.waterMl < 600 -> "Let's drink some water! 💧"
                 ctx.consumedCalories < 600 && isLate -> "Time for a small meal! 🍎"
                 ctx.streak == 0 -> "Ready to start a new streak? ✨"
                 ctx.streak >= 7 -> "Your ${ctx.streak}-day streak is beautiful! 🌟"
                 ctx.streak >= 3 -> "Keep your momentum going! 🏁"
                 else -> "You can do this workout! ❤️"
            }
            1 -> when { // Strict Coach
                 ctx.streak >= 14 && ctx.streakFreezes == 0 -> "🚨 ${ctx.streak} days at risk. Move!"
                 ctx.waterMl < 600 -> "Hydrate immediately. 💧"
                 ctx.consumedCalories < 600 && isLate -> "Fuel up. No excuses. 🥩"
                 ctx.streak == 0 -> "Zero streak. Change that now. ⚡"
                 ctx.streak >= 7 -> "Protect your ${ctx.streak}-day streak or lose it. 🔥"
                 ctx.streak >= 3 -> "Don't get lazy now. ⏱️"
                 else -> "Time to work out! Go! 🏋️"
            }
            else -> when { // Default
                ctx.streak >= 14 && ctx.streakFreezes == 0 -> "🚨 ${ctx.streak}-day streak at risk"
                ctx.waterMl < 600 -> "💧 Hydrate + train"
                ctx.consumedCalories < 600 && isLate -> "🍽️ Fuel up and finish your workout"
                ctx.streak == 0 -> "⚡ Start your streak today"
                ctx.streak >= 7 -> "🔥 Protect your ${ctx.streak}-day streak"
                ctx.streak >= 3 -> "⏱️ Keep the streak alive"
                else -> "🏋️ Time to work out!"
            }
        }
    }

    private fun getReminderMessage(ctx: ReminderContext, toneVariant: Int): String {
        val week = ((ctx.planDay - 1) / 7) + 1
        val dayInWeek = ((ctx.planDay - 1) % 7) + 1
        val dayContext = "Week $week, Day $dayInWeek"
        
        val freezeContext = if (ctx.streakFreezes > 0) {
            "You still have ${ctx.streakFreezes} streak freeze${if (ctx.streakFreezes == 1) "" else "s"}, but use it only when necessary."
        } else {
            "No streak freeze available today, so this session matters."
        }

        return when (toneVariant) {
            0 -> when { // Extrinsic / Friendly
                ctx.waterMl < 600 -> "Your water intake is quite low (${ctx.waterMl} ml). Please hydrate and then try a quick workout. $freezeContext ($dayContext)"
                ctx.streak == 0 -> "Today is the perfect day to start a new streak. Even 15 min is enough! ($dayContext)"
                else -> "Take some time for yourself today. Your daily workout awaits. ($dayContext)"
            }
            1 -> when { // Intrinsic / Aggressive
                ctx.waterMl < 600 -> "Low hydration (${ctx.waterMl} ml) ruins performance. Drink up and get the work done. $freezeContext ($dayContext)"
                ctx.streak == 0 -> "A streak of 0 isn't progress. Put the work in today. ($dayContext)"
                else -> "Discipline over motivation. Go finish your session. ($dayContext)"
            }
            else -> when { // Default
                ctx.waterMl < 600 -> "Hydration is low (${ctx.waterMl} ml). Drink water first, then finish a short workout. $freezeContext ($dayContext)"
                ctx.consumedCalories < 600 && ctx.currentHour >= 21 -> "You logged only ${ctx.consumedCalories} kcal so far. Add a light meal and complete your session to recover well. $freezeContext ($dayContext)"
                ctx.burnedCalories >= 700 -> "You already burned ${ctx.burnedCalories} kcal today. Great activity - complete the planned workout to lock the streak. $freezeContext ($dayContext)"
                ctx.streak == 0 -> "Start your first streak day now. Even 20 minutes is enough to build momentum. ($dayContext)"
                ctx.streak >= 14 && ctx.streakFreezes == 0 -> "${ctx.streak} days of consistency are on the line. Complete today's workout to protect your run. ($dayContext)"
                ctx.streak >= 7 -> "One week+ streak in progress (${ctx.streak} days). Stay consistent tonight. ($dayContext)"
                ctx.streak >= 3 -> "Good rhythm so far (${ctx.streak} days). One more session keeps the chain going. ($dayContext)"
                else -> "Your daily workout is waiting. Quick session now makes tomorrow easier. ($dayContext)"
            }
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
