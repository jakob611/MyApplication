package com.example.myapplication.data.settings

import android.util.Log
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.settings.SettingsManager
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

object UserProfileManager {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USERNAME = "username"
    private const val KEY_FIRST_NAME = "first_name"
    private const val KEY_LAST_NAME = "last_name"
    private const val KEY_ADDRESS = "address"
    private const val KEY_XP = "xp"
    private const val KEY_FOLLOWERS = "followers"
    private const val KEY_FOLLOWING = "following"
    private const val KEY_BADGES = "badges"
    private const val KEY_EQUIPMENT = "equipment"
    const val KEY_WEIGHT_UNIT = "weight_unit"
    const val KEY_SPEED_UNIT = "speed_unit"
    const val KEY_START_OF_WEEK = "start_of_week"
    private const val KEY_QUIET_HOURS_START = "quiet_hours_start"
    private const val KEY_QUIET_HOURS_END = "quiet_hours_end"
    private const val KEY_MUTE_STREAK = "mute_streak_reminders"
    private const val KEY_IS_PUBLIC = "is_public_profile"
    private const val KEY_SHOW_LEVEL = "show_level"
    private const val KEY_SHOW_BADGES = "show_badges"
    private const val KEY_SHOW_STREAK = "show_streak"
    private const val KEY_SHOW_PLAN_PATH = "show_plan_path"
    private const val KEY_SHOW_CHALLENGES = "show_challenges"
    private const val KEY_SHOW_FOLLOWERS = "show_followers"
    private const val KEY_SHARE_ACTIVITIES = "share_activities"
    private const val KEY_TOTAL_CALORIES = "total_calories"
    private const val KEY_EARLY_BIRD = "early_bird_workouts"
    private const val KEY_NIGHT_OWL = "night_owl_workouts"
    private const val KEY_LAST_LOGIN = "last_login_date"
    private const val KEY_TOTAL_PLANS = "total_plans_created"
    private const val KEY_PROFILE_PICTURE = "profile_picture_url"

    private fun getSettings() = SettingsManager.provider.getSettings(PREFS_NAME)

    private fun userKey(email: String, key: String): String {
        return "${email}_$key"
    }

    fun saveProfile(profile: UserProfile) {
        val s = getSettings()
        s.putString(userKey(profile.email, KEY_USERNAME), profile.username)
        s.putString(userKey(profile.email, KEY_FIRST_NAME), profile.firstName)
        s.putString(userKey(profile.email, KEY_LAST_NAME), profile.lastName)
        s.putString(userKey(profile.email, KEY_ADDRESS), profile.address)
        s.putInt(userKey(profile.email, KEY_XP), profile.xp)
        s.putInt(userKey(profile.email, KEY_FOLLOWERS), profile.followers)
        s.putInt(userKey(profile.email, KEY_FOLLOWING), profile.following)
        s.putString(userKey(profile.email, KEY_BADGES), profile.badges.joinToString(","))
        s.putInt(userKey(profile.email, "streak_freezes"), profile.streakFreezes)
        s.putString(userKey(profile.email, KEY_EQUIPMENT), profile.equipment.joinToString(","))
        s.putString(userKey(profile.email, KEY_WEIGHT_UNIT), profile.weightUnit)
        s.putString(userKey(profile.email, KEY_SPEED_UNIT), profile.speedUnit)
        s.putString(userKey(profile.email, KEY_START_OF_WEEK), profile.startOfWeek)
        s.putString(userKey(profile.email, KEY_QUIET_HOURS_START), profile.quietHoursStart)
        s.putString(userKey(profile.email, KEY_QUIET_HOURS_END), profile.quietHoursEnd)
        s.putBoolean(userKey(profile.email, KEY_MUTE_STREAK), profile.muteStreakReminders)
        s.putBoolean(userKey(profile.email, "detailed_calories"), profile.detailedCalories)
        s.putBoolean(userKey(profile.email, KEY_IS_PUBLIC), profile.isPublicProfile)
        s.putBoolean(userKey(profile.email, KEY_SHOW_LEVEL), profile.showLevel)
        s.putBoolean(userKey(profile.email, KEY_SHOW_BADGES), profile.showBadges)
        s.putBoolean(userKey(profile.email, KEY_SHOW_STREAK), profile.showStreak)
        s.putBoolean(userKey(profile.email, KEY_SHOW_PLAN_PATH), profile.showPlanPath)
        s.putBoolean(userKey(profile.email, KEY_SHOW_CHALLENGES), profile.showChallenges)
        s.putBoolean(userKey(profile.email, KEY_SHOW_FOLLOWERS), profile.showFollowers)
        s.putBoolean(userKey(profile.email, KEY_SHARE_ACTIVITIES), profile.shareActivities)
        s.putInt(userKey(profile.email, "total_workouts_completed"), profile.totalWorkoutsCompleted)
        s.putFloat(userKey(profile.email, KEY_TOTAL_CALORIES), profile.totalCaloriesBurned.toFloat())
        s.putInt(userKey(profile.email, KEY_EARLY_BIRD), profile.earlyBirdWorkouts)
        s.putInt(userKey(profile.email, KEY_NIGHT_OWL), profile.nightOwlWorkouts)
        s.putInt(userKey(profile.email, "streak_days"), profile.currentLoginStreak)
        if (profile.lastLoginDate != null) s.putString(userKey(profile.email, KEY_LAST_LOGIN), profile.lastLoginDate)
        s.putInt(userKey(profile.email, KEY_TOTAL_PLANS), profile.totalPlansCreated)
        if (profile.profilePictureUrl != null) s.putString(userKey(profile.email, KEY_PROFILE_PICTURE), profile.profilePictureUrl)

        s.putFloat(userKey(profile.email, "height"), (profile.height ?: 0.0).toFloat())
        s.putInt(userKey(profile.email, "age"), profile.age ?: 0)
        if (profile.gender != null) s.putString(userKey(profile.email, "gender"), profile.gender)
        if (profile.activityLevel != null) s.putString(userKey(profile.email, "activityLevel"), profile.activityLevel)
        if (profile.experience != null) s.putString(userKey(profile.email, "experience"), profile.experience)
        if (profile.bodyFat != null) s.putString(userKey(profile.email, "bodyFat"), profile.bodyFat)
        s.putString(userKey(profile.email, "workoutGoal"), profile.workoutGoal)
        s.putString(userKey(profile.email, "limitations"), profile.limitations.joinToString(","))
        if (profile.nutritionStyle != null) s.putString(userKey(profile.email, "nutritionStyle"), profile.nutritionStyle)
        if (profile.sleepHours != null) s.putString(userKey(profile.email, "sleepHours"), profile.sleepHours)
        s.putString(userKey(profile.email, "focusAreas"), profile.focusAreas.joinToString(","))
    }

    fun loadProfile(email: String): UserProfile {
        val s = getSettings()
        val heightVal = s.getFloat(userKey(email, "height"), 0f).toDouble()
        val ageVal = s.getInt(userKey(email, "age"), 0)
        return UserProfile(
            username = s.getString(userKey(email, KEY_USERNAME), ""),
            email = email,
            firstName = s.getString(userKey(email, KEY_FIRST_NAME), ""),
            lastName = s.getString(userKey(email, KEY_LAST_NAME), ""),
            address = s.getString(userKey(email, KEY_ADDRESS), ""),
            xp = s.getInt(userKey(email, KEY_XP), 0),
            followers = s.getInt(userKey(email, KEY_FOLLOWERS), 0),
            following = s.getInt(userKey(email, KEY_FOLLOWING), 0),
            badges = s.getString(userKey(email, KEY_BADGES), "").split(",").filter { it.isNotBlank() },
            streakFreezes = s.getInt(userKey(email, "streak_freezes"), 0),
            equipment = s.getString(userKey(email, KEY_EQUIPMENT), "").split(",").filter { it.isNotBlank() },
            weightUnit = s.getString(userKey(email, KEY_WEIGHT_UNIT), "kg"),
            speedUnit = s.getString(userKey(email, KEY_SPEED_UNIT), "km/h"),
            startOfWeek = s.getString(userKey(email, KEY_START_OF_WEEK), "Monday"),
            quietHoursStart = s.getString(userKey(email, KEY_QUIET_HOURS_START), "22:00"),
            quietHoursEnd = s.getString(userKey(email, KEY_QUIET_HOURS_END), "07:00"),
            muteStreakReminders = s.getBoolean(userKey(email, KEY_MUTE_STREAK), false),
            detailedCalories = s.getBoolean(userKey(email, "detailed_calories"), false),
            isPublicProfile = s.getBoolean(userKey(email, KEY_IS_PUBLIC), false),
            showLevel = s.getBoolean(userKey(email, KEY_SHOW_LEVEL), false),
            showBadges = s.getBoolean(userKey(email, KEY_SHOW_BADGES), false),
            showStreak = s.getBoolean(userKey(email, KEY_SHOW_STREAK), false),
            showPlanPath = s.getBoolean(userKey(email, KEY_SHOW_PLAN_PATH), false),
            showChallenges = s.getBoolean(userKey(email, KEY_SHOW_CHALLENGES), false),
            showFollowers = s.getBoolean(userKey(email, KEY_SHOW_FOLLOWERS), false),
            shareActivities = s.getBoolean(userKey(email, KEY_SHARE_ACTIVITIES), false),
            totalWorkoutsCompleted = s.getInt(userKey(email, "total_workouts_completed"), 0),
            totalCaloriesBurned = s.getFloat(userKey(email, KEY_TOTAL_CALORIES), 0f).toDouble(),
            earlyBirdWorkouts = s.getInt(userKey(email, KEY_EARLY_BIRD), 0),
            nightOwlWorkouts = s.getInt(userKey(email, KEY_NIGHT_OWL), 0),
            currentLoginStreak = s.getInt(userKey(email, "streak_days"), 0),
            lastLoginDate = s.getStringOrNull(userKey(email, KEY_LAST_LOGIN)),
            totalPlansCreated = s.getInt(userKey(email, KEY_TOTAL_PLANS), 0),
            profilePictureUrl = s.getStringOrNull(userKey(email, KEY_PROFILE_PICTURE)),
            height = if (heightVal > 0) heightVal else null,
            age = if (ageVal > 0) ageVal else null,
            gender = s.getStringOrNull(userKey(email, "gender")),
            activityLevel = s.getStringOrNull(userKey(email, "activityLevel")),
            experience = s.getStringOrNull(userKey(email, "experience")),
            bodyFat = s.getStringOrNull(userKey(email, "bodyFat")),
            workoutGoal = s.getString(userKey(email, "workoutGoal"), ""),
            limitations = s.getString(userKey(email, "limitations"), "").split(",").filter { it.isNotBlank() },
            nutritionStyle = s.getStringOrNull(userKey(email, "nutritionStyle")),
            sleepHours = s.getStringOrNull(userKey(email, "sleepHours")),
            focusAreas = s.getString(userKey(email, "focusAreas"), "").split(",").filter { it.isNotBlank() }
        )
    }

    suspend fun setDarkMode(email: String, isDark: Boolean) {
        try {
            com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                .set(mapOf("darkMode" to isDark), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("UserProfileManager", "❌ Error saving dark mode", e)
        }
    }

    suspend fun isDarkMode(email: String): Boolean {
        if (email.isBlank()) return false
        return try {
            com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                .get().await()
                .getBoolean("darkMode") ?: false
        } catch (e: Exception) {
            false
        }
    }

    suspend fun saveProfileFirestore(profile: UserProfile, batch: com.google.firebase.firestore.WriteBatch? = null) {
        if (profile.email.isBlank()) return
        val uid = Firebase.auth.currentUser?.uid ?: return
        val resolvedRef = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()

        val data = mutableMapOf<String, Any?>(
            KEY_USERNAME to profile.username,
            KEY_FIRST_NAME to profile.firstName,
            KEY_LAST_NAME to profile.lastName,
            KEY_ADDRESS to profile.address,
            KEY_XP to profile.xp,
            KEY_FOLLOWERS to profile.followers,
            KEY_FOLLOWING to profile.following,
            KEY_BADGES to profile.badges,
            "streak_freezes" to profile.streakFreezes,
            KEY_EQUIPMENT to profile.equipment,
            "focusAreas" to profile.focusAreas,
            "workoutGoal" to profile.workoutGoal,
            KEY_WEIGHT_UNIT to profile.weightUnit,
            KEY_SPEED_UNIT to profile.speedUnit,
            KEY_START_OF_WEEK to profile.startOfWeek,
            KEY_QUIET_HOURS_START to profile.quietHoursStart,
            KEY_QUIET_HOURS_END to profile.quietHoursEnd,
            KEY_MUTE_STREAK to profile.muteStreakReminders,
            "detailed_calories" to profile.detailedCalories,
            KEY_IS_PUBLIC to profile.isPublicProfile,
            KEY_SHOW_LEVEL to profile.showLevel,
            KEY_SHOW_BADGES to profile.showBadges,
            KEY_SHOW_STREAK to profile.showStreak,
            KEY_SHOW_PLAN_PATH to profile.showPlanPath,
            KEY_SHOW_CHALLENGES to profile.showChallenges,
            KEY_SHOW_FOLLOWERS to profile.showFollowers,
            KEY_SHARE_ACTIVITIES to profile.shareActivities,
            "profilePictureUrl" to profile.profilePictureUrl
        )

        if (profile.height != null && profile.height > 0) data["height"] = profile.height
        if (profile.age != null && profile.age > 0) data["age"] = profile.age
        if (!profile.gender.isNullOrBlank()) data["gender"] = profile.gender
        if (!profile.activityLevel.isNullOrBlank()) data["activityLevel"] = profile.activityLevel
        if (!profile.experience.isNullOrBlank()) data["experience"] = profile.experience
        if (!profile.bodyFat.isNullOrBlank()) data["bodyFat"] = profile.bodyFat
        if (!profile.nutritionStyle.isNullOrBlank()) data["nutritionStyle"] = profile.nutritionStyle
        if (!profile.sleepHours.isNullOrBlank()) data["sleepHours"] = profile.sleepHours

        try {
            if (batch != null) {
                batch.set(resolvedRef, data, SetOptions.merge())
            } else {
                com.example.myapplication.persistence.FirestoreHelper.withRetry {
                    resolvedRef.set(data, SetOptions.merge()).await()
                }
            }
        } catch (e: Exception) {
            Log.e("UserProfileManager", "❌ Error saving profile", e)
        }
    }

    suspend fun loadProfileFromFirestore(email: String): UserProfile? {
        if (email.isBlank()) return null
        val uid = Firebase.auth.currentUser?.uid ?: return null
        return try {
            val resolvedRef = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
            val doc = resolvedRef.get().await()
            if (!doc.exists()) return null
            documentToUserProfile(doc, email)
        } catch (e: Exception) {
            null
        }
    }

    fun documentToUserProfile(doc: com.google.firebase.firestore.DocumentSnapshot, email: String): UserProfile {
        val username = doc.getString(KEY_USERNAME) ?: ""
        val firstName = doc.getString(KEY_FIRST_NAME) ?: ""
        val lastName = doc.getString(KEY_LAST_NAME) ?: ""
        val address = doc.getString(KEY_ADDRESS) ?: ""
        val xp = (doc.get(KEY_XP) as? Number)?.toInt() ?: 0
        val followers = (doc.get(KEY_FOLLOWERS) as? Number)?.toInt() ?: 0
        val following = (doc.get(KEY_FOLLOWING) as? Number)?.toInt() ?: 0
        val badges = when (val b = doc.get(KEY_BADGES)) {
            is List<*> -> b.filterIsInstance<String>()
            is String -> b.split(',').filter { it.isNotBlank() }
            else -> emptyList()
        }
        val streakFreezes = (doc.get("streak_freezes") as? Number)?.toInt() ?: 0
        val equipment = when (val eq = doc.get(KEY_EQUIPMENT)) {
            is List<*> -> eq.filterIsInstance<String>()
            is String -> eq.split(',').filter { it.isNotBlank() }
            else -> emptyList()
        }
        val focusAreas = when (val fa = doc.get("focusAreas")) {
            is List<*> -> fa.filterIsInstance<String>()
            is String -> fa.split(',').filter { it.isNotBlank() }
            else -> emptyList()
        }
        val workoutGoal = doc.getString("workoutGoal") ?: ""
        val weightUnit = doc.getString(KEY_WEIGHT_UNIT) ?: "kg"
        val speedUnit = doc.getString(KEY_SPEED_UNIT) ?: "km/h"
        val startOfWeek = doc.getString(KEY_START_OF_WEEK) ?: "Monday"
        val quietHoursStart = doc.getString(KEY_QUIET_HOURS_START) ?: "22:00"
        val quietHoursEnd = doc.getString(KEY_QUIET_HOURS_END) ?: "07:00"
        val muteStreakReminders = doc.getBoolean(KEY_MUTE_STREAK) ?: false
        val detailedCalories = doc.getBoolean("detailed_calories") ?: false
        val isPublic = doc.getBoolean(KEY_IS_PUBLIC) ?: false
        val showLevel = doc.getBoolean(KEY_SHOW_LEVEL) ?: false
        val showBadges = doc.getBoolean(KEY_SHOW_BADGES) ?: false
        val showStreak = doc.getBoolean(KEY_SHOW_STREAK) ?: false
        val showPlanPath = doc.getBoolean(KEY_SHOW_PLAN_PATH) ?: false
        val showChallenges = doc.getBoolean(KEY_SHOW_CHALLENGES) ?: false
        val showFollowers = doc.getBoolean(KEY_SHOW_FOLLOWERS) ?: false
        val shareActivities = doc.getBoolean(KEY_SHARE_ACTIVITIES) ?: false
        val totalWorkouts = (doc.get("total_workouts_completed") as? Number)?.toInt() ?: 0
        val totalCalories = (doc.get(KEY_TOTAL_CALORIES) as? Number)?.toDouble() ?: 0.0
        val earlyBird = (doc.get(KEY_EARLY_BIRD) as? Number)?.toInt() ?: 0
        val nightOwl = (doc.get(KEY_NIGHT_OWL) as? Number)?.toInt() ?: 0
        val loginStreak = (doc.get("streak_days") as? Number)?.toInt() ?: 0
        val lastLogin = doc.getString(KEY_LAST_LOGIN)
        val totalPlans = (doc.get(KEY_TOTAL_PLANS) as? Number)?.toInt() ?: 0
        val profilePictureUrl = doc.getString(KEY_PROFILE_PICTURE)
        val height = (doc.get("height") as? Number)?.toDouble()
        val age = (doc.get("age") as? Number)?.toInt()
        val gender = doc.getString("gender")
        val activityLevel = doc.getString("activityLevel")
        val experience = doc.getString("experience")
        val bodyFat = doc.getString("bodyFat")
        val limitations = when (val lim = doc.get("limitations")) {
            is List<*> -> lim.filterIsInstance<String>()
            is String -> lim.split(',').filter { it.isNotBlank() }
            else -> emptyList()
        }
        val nutritionStyle = doc.getString("nutritionStyle")
        val sleepHours = doc.getString("sleepHours")
        return UserProfile(
            username = username, email = email,
            firstName = firstName, lastName = lastName, address = address,
            xp = xp, followers = followers, following = following, badges = badges,
            streakFreezes = streakFreezes, equipment = equipment, focusAreas = focusAreas, workoutGoal = workoutGoal,
            weightUnit = weightUnit, speedUnit = speedUnit, startOfWeek = startOfWeek,
            quietHoursStart = quietHoursStart, quietHoursEnd = quietHoursEnd, muteStreakReminders = muteStreakReminders,
            detailedCalories = detailedCalories,
            isPublicProfile = isPublic, showLevel = showLevel, showBadges = showBadges,
            showStreak = showStreak, showPlanPath = showPlanPath, showChallenges = showChallenges, showFollowers = showFollowers,
            shareActivities = shareActivities,
            totalWorkoutsCompleted = totalWorkouts, totalCaloriesBurned = totalCalories,
            earlyBirdWorkouts = earlyBird, nightOwlWorkouts = nightOwl,
            currentLoginStreak = loginStreak, lastLoginDate = lastLogin,
            totalPlansCreated = totalPlans, profilePictureUrl = profilePictureUrl,
            height = height, age = age, gender = gender,
            activityLevel = activityLevel, experience = experience, bodyFat = bodyFat,
            limitations = limitations, nutritionStyle = nutritionStyle, sleepHours = sleepHours
        )
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
            "streak_days" to streak,
            "total_workouts_completed" to totalWorkouts,
            "weekly_done" to weeklyDone,
            "last_workout_epoch" to lastWorkoutEpoch,
            "plan_day" to planDay,
            "weekly_target" to weeklyTarget
        )
        if (batch != null) {
            batch.set(com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef(), data, SetOptions.merge())
        } else {
            com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef().set(data, SetOptions.merge()).await()
        }
    }

    suspend fun getWorkoutStats(email: String): Map<String, Any>? {
        return try {
            val doc = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef().get().await()
            if (doc.exists()) {
                mapOf(
                    "streak_days" to (doc.getLong("streak_days")?.toInt() ?: 0),
                    "total_workouts_completed" to (doc.getLong("total_workouts_completed")?.toInt() ?: 0),
                    "weekly_done" to (doc.getLong("weekly_done")?.toInt() ?: 0),
                    "last_workout_epoch" to (doc.getLong("last_workout_epoch") ?: 0L),
                    "plan_day" to (doc.getLong("plan_day")?.toInt() ?: 1),
                    "weekly_target" to (doc.getLong("weekly_target")?.toInt() ?: 0)
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteUserData(email: String) {
        if (email.isBlank()) return
        val uid = Firebase.auth.currentUser?.uid
        val db = com.example.myapplication.persistence.FirestoreHelper.getDb()
        try {
            val subcollections = listOf(
                "weightLogs", "dailyLogs", "dailyMetrics", "daily_health",
                "customMeals", "nutritionPlan", "meal_feedback", "exerciseLogs",
                "workoutSessions", "xp_history", "activePlan", "runSessions"
            )
            for (sub in subcollections) {
                try {
                    val docs = db.collection("users").document(email).collection(sub).get().await()
                    for (doc in docs) { doc.reference.delete().await() }
                } catch(_: Exception) {}
            }
            db.collection("users").document(email).delete().await()

            if (uid != null && uid != email) {
                for (sub in subcollections) {
                    try {
                        val docs = db.collection("users").document(uid).collection(sub).get().await()
                        for (doc in docs) { doc.reference.delete().await() }
                    } catch(_: Exception) {}
                }
                try { db.collection("users").document(uid).delete().await() } catch (_: Exception) {}
            }

            val resolvedId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: email
            for (key in setOf(resolvedId, uid, email).filterNotNull()) {
                try { db.collection("user_plans").document(key).delete().await() } catch (_: Exception) {}
            }
            try {
                for (key in setOf(resolvedId, uid, email).filterNotNull()) {
                    val asDocs = db.collection("follows").whereEqualTo("followerId", key).get().await()
                    for (doc in asDocs) { doc.reference.delete().await() }
                    val bsDocs = db.collection("follows").whereEqualTo("followingId", key).get().await()
                    for (doc in bsDocs) { doc.reference.delete().await() }
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Log.e("UserProfileManager", "❌ Error deleting user data", e)
        }
    }

    fun clearAllLocalData() {
        val prefNames = listOf(
            "user_prefs", "body_module", "nutrition_xp", "bm_prefs",
            "smartwatch_prefs", "algorithm_prefs", "weight_widget_prefs",
            "water_widget_prefs", "food_cache", "water_cache", "burned_cache",
            "daily_sync_prefs", "streak_widget_prefs"
        )
        for (name in prefNames) {
            SettingsManager.provider.getSettings(name).clear()
        }
        SettingsManager.provider.getSettings("app_flags").putBoolean("fresh_start_on_login", true)
        Log.d("UserProfileManager", "✅ All local SharedPreferences cleared + fresh_start flag set")
    }
}

