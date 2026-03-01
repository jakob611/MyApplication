package com.example.myapplication.persistence

import android.content.Context
import android.util.Log
import com.example.myapplication.data.BadgeDefinitions
import com.example.myapplication.data.UserPreferences
import com.example.myapplication.data.UserProfile
import com.example.myapplication.data.XPSource
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate

object AchievementStore {
    private val firestore = Firebase.firestore
    private val auth = Firebase.auth

    /**
     * Award XP to user and update profile
     */
    suspend fun awardXP(
        context: Context,
        email: String,
        amount: Int,
        source: XPSource,
        description: String = ""
    ) = withContext(Dispatchers.IO) {
        try {
            // Beri iz Firestorea — da ne prepiše height/age/gender z null vrednostmi iz lokalnega cache-a
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
            "first_workout", "committed_10", "committed_50", "committed_100" ->
                profile.totalWorkoutsCompleted
            "calorie_crusher_1k", "calorie_crusher_5k", "calorie_crusher_10k" ->
                profile.totalCaloriesBurned.toInt()
            "level_5", "level_10", "level_25" ->
                profile.level
            "first_follower", "social_butterfly", "influencer" ->
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
                    val requirement = when (badge.id) {
                        "first_workout" -> 1
                        "committed_10" -> 10
                        "committed_50" -> 50
                        "committed_100" -> 100
                        "calorie_crusher_1k" -> 1000
                        "calorie_crusher_5k" -> 5000
                        "calorie_crusher_10k" -> 10000
                        "level_5" -> 5
                        "level_10" -> 10
                        "level_25" -> 25
                        "first_follower" -> 1
                        "social_butterfly" -> 10
                        "influencer" -> 50
                        "early_bird" -> 5
                        "night_owl" -> 5
                        "week_warrior" -> 7
                        "month_master" -> 30
                        "year_champion" -> 365
                        "first_plan" -> 1
                        "plan_master" -> 5
                        else -> Int.MAX_VALUE
                    }

                    if (progress >= requirement) {
                        Log.d("AchievementStore", "Unlocking badge: ${badge.id} (progress=$progress, req=$requirement)")
                        unlockBadge(context, freshProfile, badge.id)
                        newlyUnlocked.add(badge.copy(unlocked = true, unlockedAt = System.currentTimeMillis()))
                        // Award XP for badge unlock (but not recursively for level badges)
                        if (!badge.id.startsWith("level_")) {
                            awardXP(context, freshProfile.email, 100, XPSource.BADGE_UNLOCKED, "Unlocked: ${badge.name}")
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
     * Record workout completion — updates stats then checks badges
     */
    suspend fun recordWorkoutCompletion(
        context: Context,
        email: String,
        caloriesBurned: Double,
        hour: Int
    ) = withContext(Dispatchers.IO) {
        try {
            // Beri iz Firestorea — da ne prepiše height/age/gender z null vrednostmi iz lokalnega cache-a
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

            awardXP(context, email, 50, XPSource.WORKOUT_COMPLETE, "Completed workout")

            val calorieXP = (caloriesBurned / 8).toInt()
            if (calorieXP > 0) {
                awardXP(context, email, calorieXP, XPSource.CALORIES_BURNED, "Burned $caloriesBurned kcal")
            }
            // checkAndUnlockBadges se pokliče znotraj awardXP — ne kličemo ga dvakrat

        } catch (e: Exception) {
            Log.e("AchievementStore", "Error recording workout: ${e.message}")
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
            // Beri iz Firestorea — edini zanesljiv vir vrednosti totalPlansCreated
            val profile = UserPreferences.loadProfileFromFirestore(email)
                ?: UserPreferences.loadProfile(context, email)

            val today = LocalDate.now().toString()

            // Anti-farming: ločeno polje "lastPlanCreatedDate" — lastLoginDate se ne dotika
            val uid = FirestoreHelper.getCurrentUserDocId() ?: return@withContext
            val lastPlanDate = try {
                val doc = firestore.collection("users").document(uid).get().await()
                doc.getString("lastPlanCreatedDate") ?: ""
            } catch (e: Exception) { "" }

            if (lastPlanDate == today) {
                Log.d("AchievementStore", "Plan že zabeležen danes — samo preverjam badge-e")
                checkAndUnlockBadges(context, profile)
                return@withContext
            }

            val updatedProfile = profile.copy(
                totalPlansCreated = profile.totalPlansCreated + 1
            )

            UserPreferences.saveProfile(context, updatedProfile)
            UserPreferences.saveProfileFirestore(updatedProfile)

            // Shrani datum atomično — ločeno od profila, da ne povozi sočasnih sprememb
            try {
                firestore.collection("users").document(uid)
                    .update("lastPlanCreatedDate", today)
                    .await()
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
     * Update login streak — also triggers badge check
     */
    suspend fun updateLoginStreak(
        context: Context,
        email: String
    ) = withContext(Dispatchers.IO) {
        try {
            // Beri iz Firestorea — da ne prepiše height/age/gender z null vrednostmi iz lokalnega cache-a
            val profile = UserPreferences.loadProfileFromFirestore(email)
                ?: UserPreferences.loadProfile(context, email)
            val today = LocalDate.now().toString()
            val yesterday = LocalDate.now().minusDays(1).toString()

            val newStreak = when (profile.lastLoginDate) {
                today -> profile.currentLoginStreak
                yesterday -> profile.currentLoginStreak + 1
                else -> 1
            }

            val updatedProfile = profile.copy(
                currentLoginStreak = newStreak,
                lastLoginDate = today
            )

            UserPreferences.saveProfile(context, updatedProfile)
            UserPreferences.saveProfileFirestore(updatedProfile)

            if (profile.lastLoginDate != today) {
                awardXP(context, email, 10, XPSource.DAILY_LOGIN, "Daily login")
                // checkAndUnlockBadges se pokliče znotraj awardXP — ne kličemo ga dvakrat
            }

        } catch (e: Exception) {
            Log.e("AchievementStore", "Error updating login streak: ${e.message}")
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
            val uid = FirestoreHelper.getCurrentUserDocId() ?: return@withContext
            val timestamp = System.currentTimeMillis()

            val data = mapOf(
                "xp" to xpAmount,
                "source" to source,
                "description" to description,
                "timestamp" to timestamp
            )

            firestore
                .collection("users")
                .document(uid)
                .collection("xp_history")
                .add(data)
                .await()
        } catch (e: Exception) {
            Log.e("AchievementStore", "Error logging XP activity: ${e.message}")
        }
    }
}
