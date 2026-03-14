package com.example.myapplication.persistence

import android.content.Context
import android.util.Log
import com.example.myapplication.data.BadgeDefinitions
import com.example.myapplication.data.UserPreferences
import com.example.myapplication.data.UserProfile
import com.example.myapplication.data.XPSource
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate

object AchievementStore {

    data class WorkoutCompletionResult(
        val unlockedBadges: List<com.example.myapplication.data.Badge>,
        val xpAwarded: Int,
        val isCritical: Boolean
    )

    /**
     * Interno: samo shrani XP + level up bonus, brez badge preverjanja.
     * Kliče se iz checkAndUnlockBadges (za badge XP nagrade) — prepreči rekurzijo.
     */
    private suspend fun awardXPInternal(
        context: Context,
        email: String,
        amount: Int,
        source: XPSource,
        description: String = ""
    ) = withContext(Dispatchers.IO) {
        try {
            val currentProfile = UserPreferences.loadProfileFromFirestore(email)
                ?: UserPreferences.loadProfile(context, email)
            val oldLevel = currentProfile.level
            val newXP = currentProfile.xp + amount
            val updatedProfile = currentProfile.copy(xp = newXP)
            val newLevel = updatedProfile.level
            UserPreferences.saveProfile(context, updatedProfile)
            UserPreferences.saveProfileFirestore(updatedProfile)
            logXPActivity(email, amount, source.name, description)
            if (newLevel > oldLevel) {
                val bonusXP = 200
                val profileWithBonus = updatedProfile.copy(xp = newXP + bonusXP)
                UserPreferences.saveProfile(context, profileWithBonus)
                UserPreferences.saveProfileFirestore(profileWithBonus)
                logXPActivity(email, bonusXP, "LEVEL_UP_BONUS", "Reached level $newLevel")
            }
            Log.d("AchievementStore", "awardXPInternal: $amount XP for $source: $description")
        } catch (e: Exception) {
            Log.e("AchievementStore", "Error in awardXPInternal: ${e.message}")
        }
    }

    /**
     * Award XP to user — javna metoda za zunanje klicatelje.
     * Pokliče awardXPInternal + enkrat checkAndUnlockBadges.
     */
    suspend fun awardXP(
        context: Context,
        email: String,
        amount: Int,
        source: XPSource,
        description: String = ""
    ) = withContext(Dispatchers.IO) {
        try {
            awardXPInternal(context, email, amount, source, description)
            val updatedProfile = UserPreferences.loadProfileFromFirestore(email)
                ?: UserPreferences.loadProfile(context, email)
            checkAndUnlockBadges(context, updatedProfile)
            Log.d("AchievementStore", "Awarded $amount XP for $source: $description")
        } catch (e: Exception) {
            Log.e("AchievementStore", "Error awarding XP: ${e.message}")
        }
    }

    /**
     * Calculate badge progress from a profile — single source of truth
     */
    fun getBadgeProgress(badgeId: String, profile: UserProfile): Int {
        return when (badgeId) {
            "first_workout", "committed_10", "committed_50", "committed_100",
            "committed_250", "committed_500" ->
                profile.totalWorkoutsCompleted
            "calorie_crusher_1k", "calorie_crusher_5k", "calorie_crusher_10k" ->
                profile.totalCaloriesBurned.toInt()
            "level_5", "level_10", "level_25", "level_50" ->
                profile.level
            "first_follower", "social_butterfly", "influencer", "celebrity" ->
                profile.followers
            "early_bird" -> profile.earlyBirdWorkouts
            "night_owl" -> profile.nightOwlWorkouts
            "week_warrior", "month_master", "year_champion" ->
                profile.currentLoginStreak
            "first_plan", "plan_master" ->
                profile.totalPlansCreated
            else -> 0
        }
    }

    /**
     * Check all badge conditions and unlock if met.
     * Reads FRESH profile from Firestore (not stale local cache).
     * Returns list of newly unlocked Badge objects.
     */
    suspend fun checkAndUnlockBadges(
        context: Context,
        userProfile: UserProfile
    ): List<com.example.myapplication.data.Badge> = withContext(Dispatchers.IO) {
        val newlyUnlocked = mutableListOf<com.example.myapplication.data.Badge>()

        try {
            // Always load the freshest version from Firestore to avoid stale local data
            val freshProfile = UserPreferences.loadProfileFromFirestore(userProfile.email)
                ?: UserPreferences.loadProfile(context, userProfile.email)

            val allBadges = BadgeDefinitions.ALL_BADGES
            // Already-unlocked badge IDs: union of Firestore badges field AND computed thresholds
            val currentUnlocked = freshProfile.badges.toSet()

            Log.d("AchievementStore", "Checking badges for ${freshProfile.email}: workouts=${freshProfile.totalWorkoutsCompleted}, calories=${freshProfile.totalCaloriesBurned}, level=${freshProfile.level}, streak=${freshProfile.currentLoginStreak}")

            allBadges.forEach { badge ->
                if (!currentUnlocked.contains(badge.id)) {
                    val progress = getBadgeProgress(badge.id, freshProfile)
                    // badge.requirement je definiran v BadgeDefinitions.ALL_BADGES — en sam vir resnice
                    val requirement = badge.requirement

                    if (progress >= requirement) {
                        Log.d("AchievementStore", "Unlocking badge: ${badge.id} (progress=$progress, req=$requirement)")
                        unlockBadge(context, freshProfile, badge.id)
                        newlyUnlocked.add(badge.copy(unlocked = true, unlockedAt = System.currentTimeMillis()))
                        // Uporabi awardXPInternal (ne awardXP) — prepreči rekurzijo badge→awardXP→checkBadge→...
                        if (!badge.id.startsWith("level_")) {
                            awardXPInternal(context, freshProfile.email, 100, XPSource.BADGE_UNLOCKED, "Unlocked: ${badge.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AchievementStore", "Error checking badges: ${e.message}")
        }

        newlyUnlocked
    }

    /**
     * Called on every app startup — syncs profile from Firestore then checks all badges.
     * This is the crash-recovery safety net: if the app crashed during a badge unlock,
     * this will catch it on the next launch.
     */
    suspend fun checkAndSyncBadgesOnStartup(
        context: Context,
        email: String
    ): List<com.example.myapplication.data.Badge> = withContext(Dispatchers.IO) {
        if (email.isBlank()) return@withContext emptyList()
        try {
            Log.d("AchievementStore", "Startup badge sync for $email")
            // Load fresh from Firestore (source of truth)
            val firestoreProfile = UserPreferences.loadProfileFromFirestore(email)
            if (firestoreProfile != null) {
                // Also sync local cache with Firestore data
                UserPreferences.saveProfile(context, firestoreProfile)
                // Now check all badges against fresh data
                return@withContext checkAndUnlockBadges(context, firestoreProfile)
            }
        } catch (e: Exception) {
            Log.e("AchievementStore", "Startup badge sync error: ${e.message}")
        }
        emptyList()
    }

    /**
     * Unlock a specific badge — uses Firestore atomic array union to avoid race conditions
     */
    private suspend fun unlockBadge(
        context: Context,
        userProfile: UserProfile,
        badgeId: String
    ) = withContext(Dispatchers.IO) {
        try {
            val docRef = FirestoreHelper.getCurrentUserDocRef()

            // Use Firestore arrayUnion for atomic update — avoids overwriting concurrent changes
            docRef.update("badges", FieldValue.arrayUnion(badgeId)).await()
            Log.d("AchievementStore", "Unlocked badge via arrayUnion: $badgeId")

            // Also update local cache
            val freshLocal = UserPreferences.loadProfile(context, userProfile.email)
            if (!freshLocal.badges.contains(badgeId)) {
                val updated = freshLocal.copy(badges = freshLocal.badges + badgeId)
                UserPreferences.saveProfile(context, updated)
            }
        } catch (e: Exception) {
            Log.e("AchievementStore", "Error unlocking badge $badgeId: ${e.message}")
            // Fallback: full profile save
            try {
                val freshProfile = UserPreferences.loadProfile(context, userProfile.email)
                if (!freshProfile.badges.contains(badgeId)) {
                    val updatedProfile = freshProfile.copy(badges = freshProfile.badges + badgeId)
                    UserPreferences.saveProfile(context, updatedProfile)
                    UserPreferences.saveProfileFirestore(updatedProfile)
                }
            } catch (e2: Exception) {
                Log.e("AchievementStore", "Fallback badge unlock also failed: ${e2.message}")
            }
        }
    }

    /**
     * Record workout completion — updates stats then checks badges.
     * Vrne rezultat z badge-i in XP info.
     */
    suspend fun recordWorkoutCompletion(
        context: Context,
        email: String,
        caloriesBurned: Double,
        hour: Int
    ): WorkoutCompletionResult = withContext(Dispatchers.IO) {
        try {
            val profile = UserPreferences.loadProfileFromFirestore(email)
                ?: UserPreferences.loadProfile(context, email)

            val updatedProfile = profile.copy(
                totalWorkoutsCompleted = profile.totalWorkoutsCompleted + 1,
                totalCaloriesBurned = profile.totalCaloriesBurned + caloriesBurned,
                earlyBirdWorkouts = if (hour < 7) profile.earlyBirdWorkouts + 1 else profile.earlyBirdWorkouts,
                nightOwlWorkouts = if (hour >= 21) profile.nightOwlWorkouts + 1 else profile.nightOwlWorkouts
            )

            UserPreferences.saveProfile(context, updatedProfile)
            UserPreferences.saveProfileFirestore(updatedProfile)

            // awardXP → awardXPInternal + checkAndUnlockBadges (enkrat, ne dvakrat)
            val isCritical = kotlin.random.Random.nextFloat() < 0.1f // 10% chance
            val baseXP = 50
            val awardedXP = if (isCritical) baseXP * 2 else baseXP
            val desc = if (isCritical) "Completed workout (CRITICAL HIT!)" else "Completed workout"
            
            awardXPInternal(context, email, awardedXP, XPSource.WORKOUT_COMPLETE, desc)

            val calorieXP = (caloriesBurned / 8).toInt()
            if (calorieXP > 0) {
                awardXPInternal(context, email, calorieXP, XPSource.CALORIES_BURNED, "Burned $caloriesBurned kcal")
            }

            val unlockedBadges = checkAndUnlockBadges(context, updatedProfile)
            
            WorkoutCompletionResult(unlockedBadges, awardedXP, isCritical)

        } catch (e: Exception) {
            Log.e("AchievementStore", "Error recording workout: ${e.message}")
            // Fallback: return empty result so app doesn't crash
            WorkoutCompletionResult(emptyList(), 0, false)
        }
    }

    /**
     * Record plan creation.
     * Bere SVEŽ profil iz Firestorea (ne lokalnega cache-a — ta je lahko zastarel).
     * Anti-farming: dedicirano polje "lastPlanCreatedDate" v Firestoreu (ne lastLoginDate).
     */
    suspend fun recordPlanCreation(
        context: Context,
        email: String
    ) = withContext(Dispatchers.IO) {
        try {
            val profile = UserPreferences.loadProfileFromFirestore(email)
                ?: UserPreferences.loadProfile(context, email)

            val today = LocalDate.now().toString()

            // Anti-farming: ločeno polje "lastPlanCreatedDate" — lastLoginDate se ne dotika
            // Skozi FirestoreHelper — pravilno za email in legacy UID uporabnike
            val docRef = FirestoreHelper.getCurrentUserDocRef()
            val lastPlanDate = try {
                docRef.get().await().getString("lastPlanCreatedDate") ?: ""
            } catch (e: Exception) { "" }

            if (lastPlanDate == today) {
                Log.d("AchievementStore", "Plan že zabeležen danes — samo preverjam badge-e")
                checkAndUnlockBadges(context, profile)
                return@withContext
            }

            val updatedProfile = profile.copy(totalPlansCreated = profile.totalPlansCreated + 1)
            UserPreferences.saveProfile(context, updatedProfile)
            UserPreferences.saveProfileFirestore(updatedProfile)

            // Shrani datum atomično — ločeno od profila, da ne povozi sočasnih sprememb
            try {
                docRef.update("lastPlanCreatedDate", today).await()
            } catch (e: Exception) {
                Log.w("AchievementStore", "Ne morem shraniti lastPlanCreatedDate: ${e.message}")
            }

            Log.d("AchievementStore", "Plan zabeležen: totalPlansCreated=${updatedProfile.totalPlansCreated}")
            awardXP(context, email, 100, XPSource.PLAN_CREATED, "Created workout plan")
            // checkAndUnlockBadges se pokliče znotraj awardXP — ne kličemo ga dvakrat

        } catch (e: Exception) {
            Log.e("AchievementStore", "Napaka pri beleženju plana: ${e.message}")
        }
    }

    /**
     * Update logina — SAMO posodobi lastLoginDate, NE streaka.
     * Streak se posodobi le ob 'checkAndUpdatePlanStreak' (ko je rest day ali workout).
     */
    suspend fun recordLoginOnly(
        context: Context,
        email: String
    ) = withContext(Dispatchers.IO) {
        try {
            val profile = UserPreferences.loadProfileFromFirestore(email)
                ?: UserPreferences.loadProfile(context, email)
            val today = LocalDate.now().toString()

            if (profile.lastLoginDate != today) {
                val updatedProfile = profile.copy(lastLoginDate = today)
                UserPreferences.saveProfile(context, updatedProfile)
                // Firestore shranimo asinhrono da ne blokira UI
                try {
                    val ref = FirestoreHelper.getCurrentUserDocRef()
                    ref.update("last_login_date", today).await()
                } catch (_: Exception) {}
                awardXP(context, email, 10, XPSource.DAILY_LOGIN, "Daily login")
            }
        } catch (e: Exception) {
            Log.e("AchievementStore", "Error recording login: ${e.message}")
        }
    }

    /**
     * Varna metoda za posodobitev streaka.
     * Preveri 'last_streak_update_date' da prepreči dvojno štetje.
     * isRestDaySuccess: če je danes Rest Day in je uporabnik prišel v app (ali pa smo to avtomatsko zaznali)
     * isWorkoutSuccess: če je uporabnik opravil trening
     */
    suspend fun checkAndUpdatePlanStreak(
        context: Context,
        email: String,
        isRestDaySuccess: Boolean = false,
        isWorkoutSuccess: Boolean = false
    ) = withContext(Dispatchers.IO) {
        if (!isRestDaySuccess && !isWorkoutSuccess) return@withContext

        try {
            val today = LocalDate.now()
            val todayStr = today.toString()
            val yesterdayStr = today.minusDays(1).toString()

            // 1. Check if we already have a log for today in Firestore
            // This is the "mapica" user requested — a subcollection of completed days
            val docRef = FirestoreHelper.getCurrentUserDocRef()
            val todayLogRef = docRef.collection("daily_logs").document(todayStr)
            
            val todayLogSnap = todayLogRef.get().await()
            if (todayLogSnap.exists()) {
                Log.d("AchievementStore", "Streak already recorded for today ($todayStr) in daily_logs.")
                return@withContext
            }

            // 2. Load profile
            val profile = UserPreferences.loadProfileFromFirestore(email)
                ?: UserPreferences.loadProfile(context, email)

            // 3. Determine consecutive status using daily_logs or fallback to profile
            // We check if yesterday was completed
            val yesterdayLogRef = docRef.collection("daily_logs").document(yesterdayStr)
            val yesterdayExists = yesterdayLogRef.get().await().exists()

            var effectiveStreak = profile.currentLoginStreak
            var consumedFreezes = 0
            
            // If yesterday is missing AND we think we have a streak > 0, we must check if it's broken or frozen
            // NOTE: If this is the FIRST time using daily_logs, yesterday might be missing even if streak is valid.
            // So we only penalize if daily_logs implies we missed it, OR if last_streak_update_date is old.
            
            // Legacy check
            val keyLastUpdate = "${email}_last_streak_update_date"
            val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            var lastUpdateLegacy = prefs.getString(keyLastUpdate, "") ?: ""
            
            // If legacy date is empty, fetch from Firestore
            if (lastUpdateLegacy.isEmpty()) {
                val fsDoc = docRef.get().await()
                lastUpdateLegacy = fsDoc.getString("last_streak_update_date") ?: ""
            }
            
            val lastUpdateDate = if (lastUpdateLegacy.isNotEmpty()) LocalDate.parse(lastUpdateLegacy) else null
            val daysBetween = if (lastUpdateDate != null) java.time.temporal.ChronoUnit.DAYS.between(lastUpdateDate, today) else 0L

            if (daysBetween > 1) {
                // Missed day(s) detected via legacy date
                val missedDays = (daysBetween - 1).toInt()
                if (profile.streakFreezes >= missedDays) {
                     consumedFreezes = missedDays
                     Log.d("AchievementStore", "❄️ Rescuing streak! Consuming $consumedFreezes freezes.")
                     withContext(Dispatchers.Main) {
                         try {
                             android.widget.Toast.makeText(context, "❄️ Streak Freeze Used ($consumedFreezes)!", android.widget.Toast.LENGTH_LONG).show()
                         } catch (_: Exception) {}
                     }
                } else {
                    effectiveStreak = 0
                }
            } else if (!yesterdayExists && daysBetween > 1) {
                 // Double confirm: if yesterday log is missing and legacy says we missed days -> break streak
                 // (We prioritize legacy check for now until daily_logs helps build history)
            } else {
                 // Streak is safe (either daysBetween <= 1 or it's a new install/first run)
            }
            
            // 4. Calculate new values
            val newStreak = effectiveStreak + 1
            // Ensure we don't consume more freezes than we have
            val newFreezes = (profile.streakFreezes - consumedFreezes).coerceAtLeast(0)

            // 5. Save to "daily_logs" (The Map/Collection user requested)
            val logData = mapOf(
                "date" to todayStr,
                "completed" to true,
                "type" to if (isWorkoutSuccess) "workout" else "rest_activity",
                "streak_at_this_point" to newStreak,
                "timestamp" to FieldValue.serverTimestamp()
            )
            todayLogRef.set(logData).await()

            // 6. Update Profile
            val updatedProfile = profile.copy(
                currentLoginStreak = newStreak,
                streakFreezes = newFreezes
            )
            UserPreferences.saveProfile(context, updatedProfile)

            // 7. Update Firestore user document
            try {
                val updates = mapOf(
                    "streak_days" to newStreak,
                    "streak_freezes" to newFreezes,
                    "last_streak_update_date" to todayStr
                )
                docRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
            } catch (e: Exception) {
                Log.e("AchievementStore", "Error saving streak to Firestore: ${e.message}")
            }

            // 8. Update local legacy pref
            prefs.edit().putString(keyLastUpdate, todayStr).apply()

            Log.d("AchievementStore", "Streak updated consistently: $newStreak")

        } catch (e: Exception) {
            Log.e("AchievementStore", "Error updating plan streak: ${e.message}")
        }
    }

    /**
     * Log XP activity to Firestore
     */
    private suspend fun logXPActivity(
        email: String,
        xpAmount: Int,
        source: String,
        description: String
    ) = withContext(Dispatchers.IO) {
        try {
            val data = mapOf(
                "xp" to xpAmount,
                "source" to source,
                "description" to description,
                "timestamp" to System.currentTimeMillis()
            )
            // Skozi FirestoreHelper — pravilno za email in legacy UID uporabnike
            FirestoreHelper.getCurrentUserDocRef()
                .collection("xp_history")
                .add(data)
                .await()
        } catch (e: Exception) {
            Log.e("AchievementStore", "Error logging XP activity: ${e.message}")
        }
    }
}
