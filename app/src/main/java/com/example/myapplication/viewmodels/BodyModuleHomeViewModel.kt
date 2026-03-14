package com.example.myapplication.viewmodels

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.PlanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val accepted: Boolean = false
)

data class BodyHomeUiState(
    val streakDays: Int = 0,
    val weeklyDone: Int = 0,
    val weeklyTarget: Int = 3,
    val planDay: Int = 1,
    val totalWorkoutsCompleted: Int = 0,
    val workoutsToday: Int = 0,
    val isWorkoutDoneToday: Boolean = false,
    val showCompletionAnimation: Boolean = false,
    val todayIsRest: Boolean = false,
    val challenges: List<Challenge> = listOf(
        Challenge("c1", "30 days sixpack", "Get a sixpack in 30 days. Short description here..."),
        Challenge("c2", "30 days pushups", "Improve your pushups in 30 days."),
        Challenge("c3", "Mobility week", "Increase your ROM in 7 days.")
    )
)

class BodyModuleHomeViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
    
    // Prevent double submission of workouts/rest days
    private val isCompletingAction = AtomicBoolean(false)

    private val userEmail: String
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""

    private val _ui = MutableStateFlow(BodyHomeUiState())
    val ui: StateFlow<BodyHomeUiState> = _ui

    init {
        viewModelScope.launch {
            updateStreak()
            var waited = 0
            while (userEmail.isEmpty() && waited < 3000) {
                kotlinx.coroutines.delay(100)
                waited += 100
            }
            android.util.Log.d("BodyModuleHomeVM", "init: userEmail='$userEmail' after ${waited}ms wait")
            refreshFromPrefs()
        }
    }

    fun onCompletionAnimationShown() {
        _ui.value = _ui.value.copy(showCompletionAnimation = false)
    }

    fun refreshStats() {
        viewModelScope.launch {
            refreshFromPrefs()
        }
    }

    private suspend fun refreshFromPrefs() {
        withContext(Dispatchers.IO) {
            if (userEmail.isEmpty()) return@withContext

            val today = LocalDate.now()

            try {
                val remoteStats = com.example.myapplication.data.UserPreferences.getWorkoutStats(userEmail)
                if (remoteStats != null) {
                    val remoteStreak = (remoteStats["streak_days"] as? Number)?.toInt() ?: 0
                    val remoteTotal = (remoteStats["total_workouts_completed"] as? Number)?.toInt() ?: 0
                    val remoteLastEpoch = (remoteStats["last_workout_epoch"] as? Number)?.toLong() ?: 0L
                    val remotePlanDay = (remoteStats["plan_day"] as? Number)?.toInt() ?: 1
                    val remoteWeeklyTarget = (remoteStats["weekly_target"] as? Number)?.toInt() ?: 0

                    val localTotal = prefs.getInt("total_workouts_completed", 0)
                    if (remoteTotal > localTotal || (prefs.getInt("plan_day", 1) <= 1 && remotePlanDay > 1)) {
                        prefs.edit {
                            putInt("streak_days", remoteStreak)
                            putInt("total_workouts_completed", remoteTotal)
                            putInt("plan_day", remotePlanDay)
                            putLong("last_workout_epoch", remoteLastEpoch)
                        }
                    }
                    if (remoteWeeklyTarget > 0) {
                        prefs.edit { putInt("weekly_target", remoteWeeklyTarget) }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BodyModuleHomeVM", "Stats sync error: ${e.message}")
            }

            if (prefs.getInt("weekly_target", 0) == 0) {
                try {
                    val profile = com.example.myapplication.data.UserPreferences.loadProfileFromFirestore(userEmail)
                    val parsed = profile?.activityLevel?.replace("x","")?.replace("X","")?.trim()?.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        prefs.edit { putInt("weekly_target", parsed) }
                    } else {
                        val uid = com.example.myapplication.persistence.PlanDataStore.getResolvedUserId()
                        if (uid != null) {
                            val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("user_plans").document(uid).get().await()
                            @Suppress("UNCHECKED_CAST")
                            val td = ((snap.get("plans") as? List<Map<String, Any>>)
                                ?.firstOrNull()?.get("trainingDays") as? Number)?.toInt()
                            if (td != null && td > 0) prefs.edit { putInt("weekly_target", td) }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BodyModuleHomeVM", "weekly_target lookup error: ${e.message}")
                }
            }

            val weeklyDone = calculateWeeklyWorkoutsFromFirestore(userEmail)
                .takeIf { it >= 0 }
                ?: prefs.getInt("weekly_done", 0)

            prefs.edit { putInt("weekly_done", weeklyDone) }

            val lastWorkoutEpoch = prefs.getLong("last_workout_epoch", 0L)
            val isWorkoutDoneToday = lastWorkoutEpoch > 0L && LocalDate.ofEpochDay(lastWorkoutEpoch) == today

            if (!isWorkoutDoneToday) prefs.edit { putInt("workouts_today", 0) }

            val lastRestFlagDay = prefs.getLong("rest_flag_set_epoch", 0L)
            if (lastRestFlagDay != today.toEpochDay()) {
                viewModelScope.launch(Dispatchers.IO) {
                    val planDay = prefs.getInt("plan_day", 1)
                    val isRest = tryGetNextDayIsRest(planDay)
                    prefs.edit {
                        putBoolean("today_is_rest", isRest)
                        putLong("rest_flag_set_epoch", today.toEpochDay())
                    }
                    _ui.value = _ui.value.copy(todayIsRest = isRest)

                    if (isRest && userEmail.isNotEmpty()) {
                        try {
                            com.example.myapplication.persistence.AchievementStore.checkAndUpdatePlanStreak(
                                getApplication(), userEmail, isRestDaySuccess = true
                            )
                            updateStreak()
                        } catch (_: Exception) {}
                    }
                }
            }

            val weeklyTarget = prefs.getInt("weekly_target", 0).let { if (it > 0) it else 3 }

            _ui.value = _ui.value.copy(
                planDay       = prefs.getInt("plan_day", 1),
                weeklyDone    = weeklyDone,
                weeklyTarget  = weeklyTarget,
                workoutsToday = prefs.getInt("workouts_today", 0),
                streakDays    = prefs.getInt("streak_days", 0),
                totalWorkoutsCompleted = prefs.getInt("total_workouts_completed", 0),
                isWorkoutDoneToday = isWorkoutDoneToday,
                todayIsRest   = prefs.getBoolean("today_is_rest", false)
            )
        }
    }

    fun completeRestDayActivity(onXPAdded: () -> Unit = {}) {
        if (isCompletingAction.getAndSet(true)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val email = userEmail
                if (email.isNotBlank()) {
                    val context = getApplication<android.app.Application>().applicationContext
                    val today = LocalDate.now()

                    // 1. Award XP
                    com.example.myapplication.persistence.AchievementStore.awardXP(
                        getApplication(), email, 20, com.example.myapplication.data.XPSource.WORKOUT_COMPLETE, "Completed Rest Day Mobility"
                    )

                    // 2. Mark as done locally AND advance Plan Day
                    val currentPlanDay = prefs.getInt("plan_day", 1)
                    val newPlanDay = currentPlanDay + 1
                    // Check if *tomorrow* (newPlanDay) is rest day
                    val nextDayIsRest = tryGetNextDayIsRest(newPlanDay)

                    prefs.edit {
                        putLong("last_workout_epoch", today.toEpochDay()) // This makes isWorkoutDoneToday true
                        putInt("plan_day", newPlanDay) // Advance plan!
                        putBoolean("today_is_rest", nextDayIsRest) // Set flag for next day
                    }

                    // 3. Update Streak (Rescue or Maintain)
                    com.example.myapplication.persistence.AchievementStore.checkAndUpdatePlanStreak(
                        context, email, isRestDaySuccess = true
                    )

                    // 4. Update UI State immediately - READ FRESH PROFILE like in completeWorkoutSession
                    val updatedProfile = com.example.myapplication.data.UserPreferences.loadProfile(context, email)
                    val updatedStreak = updatedProfile.currentLoginStreak
                    
                    val weeklyTargetStr = prefs.getInt("weekly_target", 3)
                    
                    prefs.edit { 
                        putInt("streak_days", updatedStreak) 
                    }
                    
                     // Also save stats to Firestore (to persist planDay increment)
                try {
                     val totalWorkouts = prefs.getInt("total_workouts_completed", 0)
                     val weeklyDone = prefs.getInt("weekly_done", 0)
                     com.example.myapplication.data.UserPreferences.saveWorkoutStats(
                        email = email,
                        streak = updatedStreak,
                        totalWorkouts = totalWorkouts,
                        weeklyDone = weeklyDone,
                        lastWorkoutEpoch = today.toEpochDay(),
                        planDay = newPlanDay,
                        weeklyTarget = weeklyTargetStr
                    )
                } catch(e: Exception) {
                    android.util.Log.e("RestDay", "Failed to save stats: ${e.message}")
                }

                    withContext(Dispatchers.Main) {
                        _ui.value = _ui.value.copy(
                            isWorkoutDoneToday = true,
                            streakDays = updatedStreak,
                            planDay = newPlanDay
                        )
                        android.widget.Toast.makeText(context, "Mobility Session Completed! +20 XP", android.widget.Toast.LENGTH_SHORT).show()
                        onXPAdded()
                    }
                }
            } finally {
                isCompletingAction.set(false)
            }
        }
    }

    // Extension: prepreči podvajanje when-bloka v completeWorkoutSession in calculateWeeklyWorkoutsFromFirestore
    private fun com.example.myapplication.data.UserProfile.startDayOfWeek(): java.time.DayOfWeek = when (startOfWeek) {
        "Sunday"   -> java.time.DayOfWeek.SUNDAY
        "Saturday" -> java.time.DayOfWeek.SATURDAY
        else       -> java.time.DayOfWeek.MONDAY
    }

    fun completeWorkoutSession(
        isExtraWorkout: Boolean = false,
        onCompletion: (com.example.myapplication.persistence.AchievementStore.WorkoutCompletionResult?) -> Unit = {},
        exerciseResults: List<Map<String, Any>> = emptyList(),
        totalKcal: Int = 0,
        totalTimeMin: Double = 0.0
    ) {
        if (isCompletingAction.getAndSet(true)) return
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<android.app.Application>().applicationContext
                val today = LocalDate.now()
                val lastWorkoutEpoch = prefs.getLong("last_workout_epoch", 0L)
                val lastWorkoutDate = if (lastWorkoutEpoch == 0L) null else LocalDate.ofEpochDay(lastWorkoutEpoch)
                val isFirstToday = lastWorkoutDate != today

                val userProfile = com.example.myapplication.data.UserPreferences.loadProfile(getApplication(), userEmail)
                val currentWeekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(userProfile.startDayOfWeek()))

                val lastWeekReset = prefs.getLong("last_week_reset", 0L)
                val lastResetDate = if (lastWeekReset == 0L) null else LocalDate.ofEpochDay(lastWeekReset)
                val shouldReset = lastResetDate == null || lastResetDate.isBefore(currentWeekStart)
    
                val currentWeeklyDone = if (shouldReset) 0 else prefs.getInt("weekly_done", 0)
                val weeklyTarget = prefs.getInt("weekly_target", 3)
    
                if (isExtraWorkout) {
                    try {
                        val workoutDoc = hashMapOf(
                            "date" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "type" to "extra",
                            "totalKcal" to totalKcal,
                            "totalTimeMin" to totalTimeMin,
                            "exercisesCount" to exerciseResults.size,
                            "planDay" to _ui.value.planDay,
                            "exercises" to exerciseResults
                        )
                        com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                            .collection("workoutSessions").add(workoutDoc).await()
                    } catch (e: Exception) {
                        android.util.Log.e("BodyModuleHome", "Failed to save extra workout: ${e.message}")
                        // Show error on main thread
                        withContext(Dispatchers.Main) {
                            try {
                                android.widget.Toast.makeText(getApplication(), "Failed to save to cloud! Check internet.", android.widget.Toast.LENGTH_LONG).show()
                            } catch (_: Exception) {}
                        }
                    }
                    val newTotal = _ui.value.totalWorkoutsCompleted + 1
                    val extraCount = prefs.getInt("total_extra_workouts", 0) + 1
                    prefs.edit { putInt("total_workouts_completed", newTotal); putInt("total_extra_workouts", extraCount) }
                    _ui.value = _ui.value.copy(totalWorkoutsCompleted = newTotal)
                    withContext(Dispatchers.Main) { onCompletion(null) }
                    return@launch
                }
    
                if (!isFirstToday) {
                    withContext(Dispatchers.Main) { 
                        android.widget.Toast.makeText(getApplication(), "Daily workout already completed!", android.widget.Toast.LENGTH_SHORT).show()
                        onCompletion(null) 
                    }
                    return@launch
                }
    
                var completionResult: com.example.myapplication.persistence.AchievementStore.WorkoutCompletionResult? = null
    
                try {
                    // Determine hour for Early Bird / Night Owl stats
                    val hour = java.time.LocalTime.now().hour
                    
                    // Use AchievementStore to record stats (Early Bird, Night Owl, Total Calories)
                    val res = com.example.myapplication.persistence.AchievementStore.recordWorkoutCompletion(
                        context, userEmail, totalKcal.toDouble(), hour
                    )
                    completionResult = res
                    
                    withContext(Dispatchers.Main) {
                        val msg = if (res.isCritical) 
                            "CRITICAL HIT! Double XP! (+${res.xpAwarded} XP)" 
                        else 
                            "Workout Complete! +${res.xpAwarded} XP"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    }
    
                    // Original logic for saving workout session to Firestore
                    val workoutDoc = hashMapOf(
                        "date" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "type" to "regular",
                        "totalKcal" to totalKcal,
                        "totalTimeMin" to totalTimeMin,
                        "exercisesCount" to exerciseResults.size,
                        "planDay" to _ui.value.planDay,
                        "exercises" to exerciseResults
                    )
                    com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                        .collection("workoutSessions").add(workoutDoc).await()
                } catch (e: Exception) {
                    android.util.Log.e("BodyModuleHome", "Failed to save regular workout: ${e.message}")
                    withContext(Dispatchers.Main) {
                        try {
                            android.widget.Toast.makeText(getApplication(), "Failed to save to cloud! Check internet.", android.widget.Toast.LENGTH_LONG).show()
                        } catch (_: Exception) {}
                    }
                }
    
                val newWeeklyDone = if (shouldReset) 1 else currentWeeklyDone + 1
                val newPlanDay = _ui.value.planDay + 1
                val newTotal = _ui.value.totalWorkoutsCompleted + 1
    
                // Update streak safely via AchievementStore logic (prevent double increment)
                try {
                    com.example.myapplication.persistence.AchievementStore.checkAndUpdatePlanStreak(context, userEmail, isWorkoutSuccess = true)
                } catch (_: Exception) {}
                // Now read the authoritative streak from updated profile
                val updatedProfile = com.example.myapplication.data.UserPreferences.loadProfile(context, userEmail)
                val newStreak = updatedProfile.currentLoginStreak
    
                val nextDayIsRest = tryGetNextDayIsRest(newPlanDay)
    
                prefs.edit {
                    putLong("last_workout_epoch", today.toEpochDay())
                    putInt("weekly_done", newWeeklyDone)
                    putInt("plan_day", newPlanDay)
                    putInt("total_workouts_completed", newTotal)
                    putInt("streak_days", newStreak)
                    putBoolean("today_is_rest", nextDayIsRest)
                    if (shouldReset) putLong("last_week_reset", currentWeekStart.toEpochDay())
                }
    
                if (userEmail.isNotEmpty()) {
                    com.example.myapplication.data.UserPreferences.saveWorkoutStats(
                        email = userEmail,
                        streak = newStreak,
                        totalWorkouts = newTotal,
                        weeklyDone = newWeeklyDone,
                        lastWorkoutEpoch = today.toEpochDay(),
                        planDay = newPlanDay,
                        weeklyTarget = weeklyTarget
                    )
                }
    
                val trainingDays = prefs.getInt("weekly_target", 3)
                val planDayLabel = com.example.myapplication.widget.StreakWidgetProvider.planDayToLabel(newPlanDay, trainingDays)
                com.example.myapplication.widget.StreakWidgetProvider.updateWidgetFromApp(context, newStreak, planDayLabel)
    
                _ui.value = _ui.value.copy(
                    streakDays = newStreak,
                    weeklyDone = newWeeklyDone,
                    planDay = newPlanDay,
                    totalWorkoutsCompleted = newTotal,
                    isWorkoutDoneToday = true,
                    showCompletionAnimation = true
                )
                withContext(Dispatchers.Main) { onCompletion(completionResult) }
            } finally {
                isCompletingAction.set(false)
            }
        }
    }

    private suspend fun tryGetNextDayIsRest(targetDay: Int): Boolean {
        return try {
            // Use PlanDataStore.getResolvedUserId() to ensure we get the correct doc ID (handling migration)
            val uid = com.example.myapplication.persistence.PlanDataStore.getResolvedUserId()
                ?: return false
            val snap = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("user_plans").document(uid)
                .get().await()
            @Suppress("UNCHECKED_CAST")
            val plans = snap.get("plans") as? List<Map<String, Any>> ?: return false
            val firstPlan = plans.firstOrNull() ?: return false
            @Suppress("UNCHECKED_CAST")
            val weeks = firstPlan["weeks"] as? List<Map<String, Any>> ?: return false
            for (weekMap in weeks) {
                @Suppress("UNCHECKED_CAST")
                val days = weekMap["days"] as? List<Map<String, Any>> ?: continue
                for (dayMap in days) {
                    val dayNum = (dayMap["dayNumber"] as? Number)?.toInt() ?: continue
                    if (dayNum == targetDay) {
                        return dayMap["isRestDay"] as? Boolean ?: false
                    }
                }
            }
            false
        } catch (_: Exception) { false }
    }

    private suspend fun updateStreak() {
        withContext(Dispatchers.IO) {
            val currentStreak = prefs.getInt("streak_days", 0)
            _ui.value = _ui.value.copy(streakDays = currentStreak)
        }
    }

    fun swapDaysInPlan(
        currentPlan: PlanResult,
        dayA: Int,
        dayB: Int,
        onResult: (PlanResult) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val weekA = (dayA - 1) / 7
                val weekB = (dayB - 1) / 7

                if (weekA != weekB) {
                    android.util.Log.w("SwapDays", "Cross-week swap not allowed: $dayA <-> $dayB")
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            getApplication(),
                            "Swap only allowed within the same week!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val posA = (dayA - 1) % 7
                val posB = (dayB - 1) % 7
                val allDays = currentPlan.weeks.flatMap { it.days }.toMutableList()

                for (weekIdx in weekA until 4) {
                    val dayNumA = weekIdx * 7 + posA + 1
                    val dayNumB = weekIdx * 7 + posB + 1
                    val iA = allDays.indexOfFirst { it.dayNumber == dayNumA }
                    val iB = allDays.indexOfFirst { it.dayNumber == dayNumB }
                    if (iA >= 0 && iB >= 0) {
                        val rA = allDays[iA].isRestDay; val fA = allDays[iA].focusLabel
                        val rB = allDays[iB].isRestDay; val fB = allDays[iB].focusLabel
                        allDays[iA] = allDays[iA].copy(isRestDay = rB, focusLabel = fB)
                        allDays[iB] = allDays[iB].copy(isRestDay = rA, focusLabel = fA)
                    }
                }

                val updatedWeeks = currentPlan.weeks.map { week ->
                    week.copy(days = allDays.filter { (it.dayNumber - 1) / 7 == week.weekNumber - 1 })
                }
                val updatedPlan = currentPlan.copy(weeks = updatedWeeks)

                // Delegiraj serializacijo PlanDataStore-u — en sam vir resnice za plan shranjevanje
                val context = getApplication<android.app.Application>().applicationContext
                com.example.myapplication.persistence.PlanDataStore.updatePlan(context, updatedPlan)

                withContext(Dispatchers.Main) { onResult(updatedPlan) }
                android.util.Log.d("SwapDays", "✅ Swapped day $dayA <-> $dayB")
            } catch (e: Exception) {
                android.util.Log.e("SwapDays", "Failed to swap days: ${e.message}")
            }
        }
    }

    fun acceptChallenge(id: String) {
        val updated = _ui.value.challenges.map {
            if (it.id == id) it.copy(accepted = true) else it
        }
        _ui.value = _ui.value.copy(challenges = updated)
    }

    private suspend fun calculateWeeklyWorkoutsFromFirestore(email: String): Int {
        if (email.isBlank()) return -1
        val userProfile = com.example.myapplication.data.UserPreferences.loadProfile(getApplication(), email)
        val currentWeekStart = LocalDate.now()
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(userProfile.startDayOfWeek()))
        val startEpochDay = currentWeekStart.toEpochDay()
        val lastEpoch = prefs.getLong("last_workout_epoch", 0L)
        val localCount = if (lastEpoch > 0L && LocalDate.ofEpochDay(lastEpoch).toEpochDay() >= startEpochDay)
            prefs.getInt("weekly_done", 0) else 0

        return try {
            val startTimestamp = com.google.firebase.Timestamp(
                java.util.Date.from(currentWeekStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())
            )
            // Skozi FirestoreHelper — pravilno za email in legacy UID uporabnike
            val ref = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
            val querySnapshot = ref.collection("workoutSessions")
                .whereGreaterThanOrEqualTo("date", startTimestamp)
                .get().await()
            val count = querySnapshot.documents.count { doc ->
                (doc.getString("type") ?: "regular") == "regular"
            }
            android.util.Log.i("BodyModuleHomeVM", "📅 weekly_done from Firestore: $count (week starts $currentWeekStart)")
            maxOf(count, localCount)
        } catch (e: Exception) {
            android.util.Log.e("BodyModuleHomeVM", "Error calculating weekly workouts: ${e.message}")
            if (localCount > 0) localCount else -1
        }
    }
}
