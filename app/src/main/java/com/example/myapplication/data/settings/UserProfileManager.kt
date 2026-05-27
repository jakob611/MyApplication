package com.example.myapplication.data.settings

import android.util.Log
import com.example.myapplication.data.UserProfile
import com.example.myapplication.data.store.FirestoreHelper
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Faza 49 — God Object razgradnja, SRP refaktoriranje.
 *
 * [UserProfileManager] je zdaj IZKLJUČNO koordinator Firestore cloud operacij za profil.
 *
 * Odgovornosti so razdeljene na:
 * - Lokalna persistenca  → [UserLocalStore]
 * - DTO mapiranje        → [UserProfileMapper]
 * - Brisanje računa      → [domain.usecase.DeleteAccountUseCase]
 */
object UserProfileManager {

    // ---- Firestore field name constants (ostanejo tukaj za saveProfileFirestore) ----
    private const val KEY_USERNAME          = "username"
    private const val KEY_FIRST_NAME        = "first_name"
    private const val KEY_LAST_NAME         = "last_name"
    private const val KEY_ADDRESS           = "address"
    private const val KEY_BADGES            = "badges"
    private const val KEY_EQUIPMENT         = "equipment"
    const val KEY_WEIGHT_UNIT               = "weight_unit"
    const val KEY_SPEED_UNIT                = "speed_unit"
    const val KEY_START_OF_WEEK             = "start_of_week"
    private const val KEY_QUIET_HOURS_START = "quiet_hours_start"
    private const val KEY_QUIET_HOURS_END   = "quiet_hours_end"
    private const val KEY_MUTE_STREAK       = "mute_streak_reminders"
    private const val KEY_IS_PUBLIC         = "is_public_profile"
    private const val KEY_SHOW_LEVEL        = "show_level"
    private const val KEY_SHOW_BADGES       = "show_badges"
    private const val KEY_SHOW_STREAK       = "show_streak"
    private const val KEY_SHOW_PLAN_PATH    = "show_plan_path"
    private const val KEY_SHOW_CHALLENGES   = "show_challenges"
    private const val KEY_SHOW_FOLLOWERS    = "show_followers"
    private const val KEY_SHARE_ACTIVITIES  = "share_activities"
    private const val KEY_TOTAL_CALORIES    = "total_calories"
    private const val KEY_EARLY_BIRD        = "early_bird_workouts"
    private const val KEY_NIGHT_OWL         = "night_owl_workouts"
    private const val KEY_TOTAL_PLANS       = "total_plans_created"

    // ----------------------------------------------------------------
    // FIRESTORE CLOUD OPERACIJE
    // ----------------------------------------------------------------

    suspend fun setDarkMode(email: String, isDark: Boolean) {
        try {
            FirestoreHelper.getCurrentUserDocRef()
                .set(mapOf("darkMode" to isDark), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("UserProfileManager", "❌ Error saving dark mode", e)
        }
    }

    suspend fun isDarkMode(email: String): Boolean {
        if (email.isBlank()) return false
        return try {
            FirestoreHelper.getCurrentUserDocRef()
                .get().await()
                .getBoolean("darkMode") ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun saveProfileFirestore(profile: UserProfile, batch: com.google.firebase.firestore.WriteBatch? = null) {
        if (profile.email.isBlank()) return
        @Suppress("UNUSED_VARIABLE")
        val uid = Firebase.auth.currentUser?.uid ?: return
        val resolvedRef = FirestoreHelper.getCurrentUserDocRef()

        // VARNO: xp, followers, following so IZVZETI iz tega merge-a.
        // Ti podatki se smejo posodabljati IZKLJUČNO prek atomarnih Firestore transakcij:
        //   - xp        → FirestoreGamificationRepository.awardXP()
        //   - followers  → FollowStore.followUser() / unfollowUser()
        //   - following  → FollowStore.followUser() / unfollowUser()
        val data = mutableMapOf<String, Any?>(
            KEY_USERNAME        to profile.username,
            KEY_FIRST_NAME      to profile.firstName,
            KEY_LAST_NAME       to profile.lastName,
            KEY_ADDRESS         to profile.address,
            // ⛔ KEY_XP, KEY_FOLLOWERS, KEY_FOLLOWING — upravljano IZKLJUČNO prek transakcij
            KEY_BADGES          to profile.badges,
            "streak_freezes"    to profile.streakFreezes,
            KEY_EQUIPMENT       to profile.equipment,
            "focusAreas"        to profile.focusAreas,
            "workoutGoal"       to profile.workoutGoal,
            KEY_WEIGHT_UNIT     to profile.weightUnit,
            KEY_SPEED_UNIT      to profile.speedUnit,
            KEY_START_OF_WEEK   to profile.startOfWeek,
            KEY_QUIET_HOURS_START to profile.quietHoursStart,
            KEY_QUIET_HOURS_END to profile.quietHoursEnd,
            KEY_MUTE_STREAK     to profile.muteStreakReminders,
            "detailed_calories" to profile.detailedCalories,
            KEY_IS_PUBLIC       to profile.isPublicProfile,
            KEY_SHOW_LEVEL      to profile.showLevel,
            KEY_SHOW_BADGES     to profile.showBadges,
            KEY_SHOW_STREAK     to profile.showStreak,
            KEY_SHOW_PLAN_PATH  to profile.showPlanPath,
            KEY_SHOW_CHALLENGES to profile.showChallenges,
            KEY_SHOW_FOLLOWERS  to profile.showFollowers,
            KEY_SHARE_ACTIVITIES to profile.shareActivities,
            "profilePictureUrl" to profile.profilePictureUrl
        )

        if (profile.height != null && profile.height > 0)    data["height"]       = profile.height
        if (profile.age    != null && profile.age > 0)        data["age"]          = profile.age
        if (!profile.gender.isNullOrBlank())                  data["gender"]       = profile.gender
        if (!profile.activityLevel.isNullOrBlank())           data["activityLevel"] = profile.activityLevel
        if (!profile.experience.isNullOrBlank())              data["experience"]   = profile.experience
        if (!profile.bodyFat.isNullOrBlank())                 data["bodyFat"]      = profile.bodyFat
        if (!profile.nutritionStyle.isNullOrBlank())          data["nutritionStyle"] = profile.nutritionStyle
        if (!profile.sleepHours.isNullOrBlank())              data["sleepHours"]   = profile.sleepHours
        if (profile.goalWeightKg != null && profile.goalWeightKg > 0) data["goalWeightKg"] = profile.goalWeightKg

        try {
            if (batch != null) {
                batch.set(resolvedRef, data, SetOptions.merge())
            } else {
                FirestoreHelper.withRetry {
                    resolvedRef.set(data, SetOptions.merge()).await()
                }
            }
        } catch (e: Exception) {
            Log.e("UserProfileManager", "❌ Error saving profile", e)
        }
    }

    /**
     * Naloži profil iz Firestorea.
     * DTO → domain mapiranje delegirano na [UserProfileMapper].
     */
    suspend fun loadProfileFromFirestore(email: String): UserProfile? {
        if (email.isBlank()) return null
        @Suppress("UNUSED_VARIABLE")
        val uid = Firebase.auth.currentUser?.uid ?: return null
        return try {
            val resolvedRef = FirestoreHelper.getCurrentUserDocRef()
            val doc = resolvedRef.get().await()
            if (!doc.exists()) return null
            UserProfileMapper.documentToUserProfile(doc, email)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveWorkoutStats(
        email: String,
        streak: Int,
        totalWorkouts: Int,
        weeklyDone: Int,
        lastWorkoutEpoch: Long,
        planDay: Int = 1,
        weeklyTarget: Int = 0,
        batch: com.google.firebase.firestore.WriteBatch? = null
    ) {
        val data = mapOf(
            "streak_days"               to streak,
            "total_workouts_completed"  to totalWorkouts,
            "weekly_done"               to weeklyDone,
            "last_workout_epoch"        to lastWorkoutEpoch,
            "plan_day"                  to planDay,
            "weekly_target"             to weeklyTarget
        )
        if (batch != null) {
            batch.set(FirestoreHelper.getCurrentUserDocRef(), data, SetOptions.merge())
        } else {
            FirestoreHelper.getCurrentUserDocRef().set(data, SetOptions.merge()).await()
        }
    }

    suspend fun getWorkoutStats(email: String): Map<String, Any>? {
        return try {
            val doc = FirestoreHelper.getCurrentUserDocRef().get().await()
            if (doc.exists()) {
                val todayStr = Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                @Suppress("UNCHECKED_CAST")
                val dailyHistory = (doc.get("dailyHistory") as? Map<String, Any>) ?: emptyMap()
                mapOf(
                    "streak_days"               to (doc.getLong("streak_days")?.toInt() ?: 0),
                    "total_workouts_completed"  to (doc.getLong("total_workouts_completed")?.toInt() ?: 0),
                    "weekly_done"               to (doc.getLong("weekly_done")?.toInt() ?: 0),
                    "last_workout_epoch"        to (doc.getLong("last_workout_epoch") ?: 0L),
                    "plan_day"                  to (doc.getLong("plan_day")?.toInt() ?: 1),
                    "weekly_target"             to (doc.getLong("weekly_target")?.toInt() ?: 0),
                    "streak_freezes"            to (doc.getLong("streak_freezes")?.toInt() ?: 0),
                    "today_status"              to (dailyHistory[todayStr]?.toString() ?: "")
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
