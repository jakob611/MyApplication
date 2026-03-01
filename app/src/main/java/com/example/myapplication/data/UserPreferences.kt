package com.example.myapplication.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth
import kotlinx.coroutines.tasks.await
import android.util.Log

object UserPreferences {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USERNAME = "username"
    private const val KEY_FIRST_NAME = "first_name"
    private const val KEY_LAST_NAME = "last_name"
    private const val KEY_ADDRESS = "address"
    private const val KEY_XP = "xp"
    private const val KEY_FOLLOWERS = "followers"
    private const val KEY_FOLLOWING = "following"
    private const val KEY_BADGES = "badges"
    private const val KEY_EQUIPMENT = "equipment" // Added equipment key
    const val KEY_WEIGHT_UNIT = "weight_unit"
    const val KEY_SPEED_UNIT = "speed_unit"
    const val KEY_START_OF_WEEK = "start_of_week"
    // Privacy settings keys
    private const val KEY_IS_PUBLIC = "is_public_profile"
    private const val KEY_SHOW_LEVEL = "show_level"
    private const val KEY_SHOW_BADGES = "show_badges"
    private const val KEY_SHOW_PLAN_PATH = "show_plan_path"
    private const val KEY_SHOW_CHALLENGES = "show_challenges"
    private const val KEY_SHOW_FOLLOWERS = "show_followers"
    // Achievement tracking keys
    private const val KEY_TOTAL_WORKOUTS = "total_workouts"
    private const val KEY_TOTAL_CALORIES = "total_calories"
    private const val KEY_EARLY_BIRD = "early_bird_workouts"
    private const val KEY_NIGHT_OWL = "night_owl_workouts"
    private const val KEY_LOGIN_STREAK = "login_streak"
    private const val KEY_LAST_LOGIN = "last_login_date"
    private const val KEY_TOTAL_PLANS = "total_plans_created"
    private const val KEY_PROFILE_PICTURE = "profile_picture_url"

    private val db = Firebase.firestore

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Helper to create per-user key
    private fun userKey(email: String, key: String): String {
        return "${email}_$key"
    }

    fun saveProfile(context: Context, profile: UserProfile) {
        getPrefs(context).edit().apply {
            putString(userKey(profile.email, KEY_USERNAME), profile.username)
            putString(userKey(profile.email, KEY_FIRST_NAME), profile.firstName)
            putString(userKey(profile.email, KEY_LAST_NAME), profile.lastName)
            putString(userKey(profile.email, KEY_ADDRESS), profile.address)
            putInt(userKey(profile.email, KEY_XP), profile.xp)
            putInt(userKey(profile.email, KEY_FOLLOWERS), profile.followers)
            putInt(userKey(profile.email, KEY_FOLLOWING), profile.following)
            putString(userKey(profile.email, KEY_BADGES), profile.badges.joinToString(","))
            putString(userKey(profile.email, KEY_EQUIPMENT), profile.equipment.joinToString(","))
            putString(userKey(profile.email, KEY_WEIGHT_UNIT), profile.weightUnit)
            putString(userKey(profile.email, KEY_SPEED_UNIT), profile.speedUnit)
            putString(userKey(profile.email, KEY_START_OF_WEEK), profile.startOfWeek)
            // Display settings
            putBoolean(userKey(profile.email, "detailed_calories"), profile.detailedCalories)
            // Privacy settings
            putBoolean(userKey(profile.email, KEY_IS_PUBLIC), profile.isPublicProfile)
            putBoolean(userKey(profile.email, KEY_SHOW_LEVEL), profile.showLevel)
            putBoolean(userKey(profile.email, KEY_SHOW_BADGES), profile.showBadges)
            putBoolean(userKey(profile.email, KEY_SHOW_PLAN_PATH), profile.showPlanPath)
            putBoolean(userKey(profile.email, KEY_SHOW_CHALLENGES), profile.showChallenges)
            putBoolean(userKey(profile.email, KEY_SHOW_FOLLOWERS), profile.showFollowers)
            // Achievement tracking
            putInt(userKey(profile.email, KEY_TOTAL_WORKOUTS), profile.totalWorkoutsCompleted)
            putFloat(userKey(profile.email, KEY_TOTAL_CALORIES), profile.totalCaloriesBurned.toFloat())
            putInt(userKey(profile.email, KEY_EARLY_BIRD), profile.earlyBirdWorkouts)
            putInt(userKey(profile.email, KEY_NIGHT_OWL), profile.nightOwlWorkouts)
            putInt(userKey(profile.email, KEY_LOGIN_STREAK), profile.currentLoginStreak)
            putString(userKey(profile.email, KEY_LAST_LOGIN), profile.lastLoginDate)
            putInt(userKey(profile.email, KEY_TOTAL_PLANS), profile.totalPlansCreated)
            putString(userKey(profile.email, KEY_PROFILE_PICTURE), profile.profilePictureUrl)
            // 🔥 BIOMETRIJSKI PODATKI - KRITIČNO!
            putFloat(userKey(profile.email, "height"), (profile.height ?: 0.0).toFloat())
            putInt(userKey(profile.email, "age"), profile.age ?: 0)
            putString(userKey(profile.email, "gender"), profile.gender)
            putString(userKey(profile.email, "activityLevel"), profile.activityLevel)
            putString(userKey(profile.email, "experience"), profile.experience)
            putString(userKey(profile.email, "bodyFat"), profile.bodyFat)
            putString(userKey(profile.email, "workoutGoal"), profile.workoutGoal)
            putString(userKey(profile.email, "limitations"), profile.limitations.joinToString(","))
            putString(userKey(profile.email, "nutritionStyle"), profile.nutritionStyle)
            putString(userKey(profile.email, "sleepHours"), profile.sleepHours)
            putString(userKey(profile.email, "focusAreas"), profile.focusAreas.joinToString(","))
            apply()
        }
    }

    fun loadProfile(context: Context, email: String): UserProfile {
        val prefs = getPrefs(context)
        val heightVal = prefs.getFloat(userKey(email, "height"), 0f).toDouble()
        val ageVal = prefs.getInt(userKey(email, "age"), 0)
        return UserProfile(
            username = prefs.getString(userKey(email, KEY_USERNAME), "") ?: "",
            email = email,
            firstName = prefs.getString(userKey(email, KEY_FIRST_NAME), "") ?: "",
            lastName = prefs.getString(userKey(email, KEY_LAST_NAME), "") ?: "",
            address = prefs.getString(userKey(email, KEY_ADDRESS), "") ?: "",
            xp = prefs.getInt(userKey(email, KEY_XP), 0),
            followers = prefs.getInt(userKey(email, KEY_FOLLOWERS), 0),
            following = prefs.getInt(userKey(email, KEY_FOLLOWING), 0),
            badges = prefs.getString(userKey(email, KEY_BADGES), "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            equipment = prefs.getString(userKey(email, KEY_EQUIPMENT), "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            weightUnit = prefs.getString(userKey(email, KEY_WEIGHT_UNIT), "kg") ?: "kg",
            speedUnit = prefs.getString(userKey(email, KEY_SPEED_UNIT), "km/h") ?: "km/h",
            startOfWeek = prefs.getString(userKey(email, KEY_START_OF_WEEK), "Monday") ?: "Monday",
            // Display settings
            detailedCalories = prefs.getBoolean(userKey(email, "detailed_calories"), false),
            // Privacy settings
            isPublicProfile = prefs.getBoolean(userKey(email, KEY_IS_PUBLIC), false),
            showLevel = prefs.getBoolean(userKey(email, KEY_SHOW_LEVEL), false),
            showBadges = prefs.getBoolean(userKey(email, KEY_SHOW_BADGES), false),
            showPlanPath = prefs.getBoolean(userKey(email, KEY_SHOW_PLAN_PATH), false),
            showChallenges = prefs.getBoolean(userKey(email, KEY_SHOW_CHALLENGES), false),
            showFollowers = prefs.getBoolean(userKey(email, KEY_SHOW_FOLLOWERS), false),
            // Achievement tracking
            totalWorkoutsCompleted = prefs.getInt(userKey(email, KEY_TOTAL_WORKOUTS), 0),
            totalCaloriesBurned = prefs.getFloat(userKey(email, KEY_TOTAL_CALORIES), 0f).toDouble(),
            earlyBirdWorkouts = prefs.getInt(userKey(email, KEY_EARLY_BIRD), 0),
            nightOwlWorkouts = prefs.getInt(userKey(email, KEY_NIGHT_OWL), 0),
            currentLoginStreak = prefs.getInt(userKey(email, KEY_LOGIN_STREAK), 0),
            lastLoginDate = prefs.getString(userKey(email, KEY_LAST_LOGIN), null),
            totalPlansCreated = prefs.getInt(userKey(email, KEY_TOTAL_PLANS), 0),
            profilePictureUrl = prefs.getString(userKey(email, KEY_PROFILE_PICTURE), null),
            // 🔥 BIOMETRIJSKI PODATKI
            height = if (heightVal > 0) heightVal else null,
            age = if (ageVal > 0) ageVal else null,
            gender = prefs.getString(userKey(email, "gender"), null),
            activityLevel = prefs.getString(userKey(email, "activityLevel"), null),
            experience = prefs.getString(userKey(email, "experience"), null),
            bodyFat = prefs.getString(userKey(email, "bodyFat"), null),
            workoutGoal = prefs.getString(userKey(email, "workoutGoal"), "") ?: "",
            limitations = prefs.getString(userKey(email, "limitations"), "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
            nutritionStyle = prefs.getString(userKey(email, "nutritionStyle"), null),
            sleepHours = prefs.getString(userKey(email, "sleepHours"), null),
            focusAreas = prefs.getString(userKey(email, "focusAreas"), "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        )
    }


    fun addXPWithCallback(context: Context, email: String, amount: Int, onSuccess: (Int) -> Unit) {
        val prefs = getPrefs(context)
        val currentXP = prefs.getInt(userKey(email, KEY_XP), 0)
        val newXP = currentXP + amount
        prefs.edit().putInt(userKey(email, KEY_XP), newXP).apply()

        // Also save to Firestore
        db.collection("users")
            .document(email)
            .set(mapOf(KEY_XP to newXP), SetOptions.merge())
            .addOnSuccessListener {
                onSuccess(amount)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
    }

    // Firestore: save/load dark mode
    suspend fun setDarkMode(email: String, isDark: Boolean) {
        try {
            db.collection("users")
                .document(email)
                .set(mapOf("darkMode" to isDark), SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("UserPreferences", "❌ Error saving profile to Firestore", e)
            e.printStackTrace()
        }
    }

    suspend fun isDarkMode(email: String): Boolean {
        if (email.isBlank()) return false // Logged-out users
        return try {
            val doc = db.collection("users")
                .document(email)
                .get()
                .await()
            doc.getBoolean("darkMode") ?: false // Default je FALSE (light mode)
        } catch (e: Exception) {
            e.printStackTrace()
            false // Default je light mode
        }
    }

    // Firestore: save/load full user profile (remote single source of truth)
    suspend fun saveProfileFirestore(profile: UserProfile) {
        Log.d("UserPreferences", "🔥 saveProfileFirestore CALLED! email=${profile.email}, height=${profile.height}, age=${profile.age}, gender=${profile.gender}")
        if (profile.email.isBlank()) {
            Log.e("UserPreferences", "❌ Email is blank!")
            return
        }
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            Log.e("UserPreferences", "❌ UID is null! User not authenticated!")
            return
        }
        val resolvedRef = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
        Log.d("UserPreferences", "🔥 Got resolved doc ID: ${resolvedRef.id}")

        // Osnovna mapa — polja ki so vedno prisotna
        val data = mutableMapOf<String, Any?>(
            KEY_USERNAME to profile.username,
            KEY_FIRST_NAME to profile.firstName,
            KEY_LAST_NAME to profile.lastName,
            KEY_ADDRESS to profile.address,
            KEY_XP to profile.xp,
            KEY_FOLLOWERS to profile.followers,
            KEY_FOLLOWING to profile.following,
            KEY_BADGES to profile.badges,
            KEY_EQUIPMENT to profile.equipment,
            "focusAreas" to profile.focusAreas,
            "workoutGoal" to profile.workoutGoal,
            KEY_WEIGHT_UNIT to profile.weightUnit,
            KEY_SPEED_UNIT to profile.speedUnit,
            KEY_START_OF_WEEK to profile.startOfWeek,
            "detailed_calories" to profile.detailedCalories,
            KEY_IS_PUBLIC to profile.isPublicProfile,
            KEY_SHOW_LEVEL to profile.showLevel,
            KEY_SHOW_BADGES to profile.showBadges,
            KEY_SHOW_PLAN_PATH to profile.showPlanPath,
            KEY_SHOW_CHALLENGES to profile.showChallenges,
            KEY_SHOW_FOLLOWERS to profile.showFollowers,
            KEY_TOTAL_WORKOUTS to profile.totalWorkoutsCompleted,           // "total_workouts"
            "total_workouts_completed" to profile.totalWorkoutsCompleted,    // alias za BodyModuleHome
            KEY_TOTAL_CALORIES to profile.totalCaloriesBurned,
            KEY_EARLY_BIRD to profile.earlyBirdWorkouts,
            KEY_NIGHT_OWL to profile.nightOwlWorkouts,
            KEY_LOGIN_STREAK to profile.currentLoginStreak,                   // "login_streak"
            "streak_days" to profile.currentLoginStreak,                      // alias za widget + BodyModuleHome
            KEY_LAST_LOGIN to (profile.lastLoginDate ?: ""),
            KEY_TOTAL_PLANS to profile.totalPlansCreated,
            KEY_PROFILE_PICTURE to profile.profilePictureUrl,
            "limitations" to profile.limitations,
        )

        // KRITIČNO: polja ki so null se NE smejo pisati v Firestore!
        // SetOptions.merge() sicer ohrani obstoječa polja — ampak samo tista ki jih sploh
        // ne vključiš v mapo. Če pošlješ height=0 ali gender="" bo to PREPISALO pravo vrednost.
        if (profile.height != null && profile.height > 0) data["height"] = profile.height
        if (profile.age != null && profile.age > 0) data["age"] = profile.age
        if (!profile.gender.isNullOrBlank()) data["gender"] = profile.gender
        if (!profile.activityLevel.isNullOrBlank()) data["activityLevel"] = profile.activityLevel
        if (!profile.experience.isNullOrBlank()) data["experience"] = profile.experience
        if (!profile.bodyFat.isNullOrBlank()) data["bodyFat"] = profile.bodyFat
        if (!profile.nutritionStyle.isNullOrBlank()) data["nutritionStyle"] = profile.nutritionStyle
        if (!profile.sleepHours.isNullOrBlank()) data["sleepHours"] = profile.sleepHours

        try {
            resolvedRef
                .set(data, SetOptions.merge())
                .await()
            Log.d("UserPreferences", "✅ Profile SAVED to Firestore! doc=${resolvedRef.id}, height=${profile.height}, age=${profile.age}, gender=${profile.gender}")
        } catch (e: Exception) {
            Log.e("UserPreferences", "❌ Error saving profile to Firestore", e)
            e.printStackTrace()
        }
    }

    suspend fun loadProfileFromFirestore(email: String): UserProfile? {
        if (email.isBlank()) return null
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            Log.e("UserPreferences", "❌ loadProfileFromFirestore: UID is null!")
            return null
        }
        return try {
            val resolvedRef = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
            Log.d("UserPreferences", "🔥 loadProfileFromFirestore: Loading from doc=${resolvedRef.id}")
            val doc = resolvedRef.get().await()
            if (!doc.exists()) {
                Log.d("UserPreferences", "⚠️ loadProfileFromFirestore: Document does not exist for doc=${resolvedRef.id}")
                return null
            }
            Log.d("UserPreferences", "✅ loadProfileFromFirestore: Document found!")
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
            val equipment = when (val eq = doc.get(KEY_EQUIPMENT)) { // Load from Firestore
                is List<*> -> eq.filterIsInstance<String>()
                is String -> eq.split(',').filter { it.isNotBlank() }
                else -> emptyList()
            }
            val focusAreas = when (val fa = doc.get("focusAreas")) { // Load focus areas
                is List<*> -> fa.filterIsInstance<String>()
                is String -> fa.split(',').filter { it.isNotBlank() }
                else -> emptyList()
            }
            val workoutGoal = doc.getString("workoutGoal") ?: "" // Load workout goal
            val weightUnit = doc.getString(KEY_WEIGHT_UNIT) ?: "kg"
            val speedUnit = doc.getString(KEY_SPEED_UNIT) ?: "km/h"
            val startOfWeek = doc.getString(KEY_START_OF_WEEK) ?: "Monday"
            // Display settings
            val detailedCalories = doc.getBoolean("detailed_calories") ?: false
            // Privacy settings
            val isPublic = doc.getBoolean(KEY_IS_PUBLIC) ?: false
            val showLevel = doc.getBoolean(KEY_SHOW_LEVEL) ?: false
            val showBadges = doc.getBoolean(KEY_SHOW_BADGES) ?: false
            val showPlanPath = doc.getBoolean(KEY_SHOW_PLAN_PATH) ?: false
            val showChallenges = doc.getBoolean(KEY_SHOW_CHALLENGES) ?: false
            val showFollowers = doc.getBoolean(KEY_SHOW_FOLLOWERS) ?: false
            // Achievement tracking — beri oba ključa (legacy + novi) in vzemi večjega
            val totalWorkouts = maxOf(
                (doc.get(KEY_TOTAL_WORKOUTS) as? Number)?.toInt() ?: 0,         // "total_workouts"
                (doc.get("total_workouts_completed") as? Number)?.toInt() ?: 0   // alias
            )
            val totalCalories = (doc.get(KEY_TOTAL_CALORIES) as? Number)?.toDouble() ?: 0.0
            val earlyBird = (doc.get(KEY_EARLY_BIRD) as? Number)?.toInt() ?: 0
            val nightOwl = (doc.get(KEY_NIGHT_OWL) as? Number)?.toInt() ?: 0
            val loginStreak = maxOf(
                (doc.get(KEY_LOGIN_STREAK) as? Number)?.toInt() ?: 0,  // "login_streak"
                (doc.get("streak_days") as? Number)?.toInt() ?: 0       // alias
            )
            val lastLogin = doc.getString(KEY_LAST_LOGIN)
            val totalPlans = (doc.get(KEY_TOTAL_PLANS) as? Number)?.toInt() ?: 0
            val profilePictureUrl = doc.getString(KEY_PROFILE_PICTURE)

            // Plan calculation parameters
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

            UserProfile(
                username = username,
                email = email,
                firstName = firstName,
                lastName = lastName,
                address = address,
                xp = xp,
                followers = followers,
                following = following,
                badges = badges,
                equipment = equipment, // Included in returned UserProfile
                focusAreas = focusAreas, // Included
                workoutGoal = workoutGoal, // Included
                weightUnit = weightUnit,
                speedUnit = speedUnit,
                startOfWeek = startOfWeek,
                // Display settings
                detailedCalories = detailedCalories,
                // Privacy settings
                isPublicProfile = isPublic,
                showLevel = showLevel,
                showBadges = showBadges,
                showPlanPath = showPlanPath,
                showChallenges = showChallenges,
                showFollowers = showFollowers,
                // Achievement tracking
                totalWorkoutsCompleted = totalWorkouts,
                totalCaloriesBurned = totalCalories,
                earlyBirdWorkouts = earlyBird,
                nightOwlWorkouts = nightOwl,
                currentLoginStreak = loginStreak,
                lastLoginDate = lastLogin,
                totalPlansCreated = totalPlans,
                profilePictureUrl = profilePictureUrl,
                // Plan calculation parameters
                height = height,
                age = age,
                gender = gender,
                activityLevel = activityLevel,
                experience = experience,
                bodyFat = bodyFat,
                limitations = limitations,
                nutritionStyle = nutritionStyle,
                sleepHours = sleepHours
            )
        } catch (e: Exception) {
            e.printStackTrace()
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
        weeklyTarget: Int = 0  // 0 = ne posodabljaj (obdrži obstoječo vrednost)
    ) {
        try {
            val data = mutableMapOf<String, Any>(
                // Poenoten ključ: "login_streak" = berejo loadProfileFromFirestore + MainActivity listener
                "login_streak" to streak,
                // Alias za widget in BodyModuleHome ki bere "streak_days"
                "streak_days" to streak,
                // Poenoten ključ: "total_workouts" = berejo loadProfileFromFirestore + MainActivity listener
                "total_workouts" to totalWorkouts,
                // Alias za BodyModuleHome ki bere "total_workouts_completed"
                "total_workouts_completed" to totalWorkouts,
                "weekly_done" to weeklyDone,
                "last_workout_epoch" to lastWorkoutEpoch,
                "plan_day" to planDay
            )
            // Shrani weeklyTarget samo če je > 0 (ob kreiranju plana ali zamenjavi)
            if (weeklyTarget > 0) data["weekly_target"] = weeklyTarget
            db.collection("users")
                .document(email)
                .set(data, SetOptions.merge())
                .await()
        } catch (e: Exception) {
            Log.e("UserPreferences", "❌ Error saving workout stats to Firestore", e)
            e.printStackTrace()
        }
    }

    suspend fun getWorkoutStats(email: String): Map<String, Any>? {
        return try {
            val doc = db.collection("users").document(email).get().await()
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
            e.printStackTrace()
            null
        }
    }

    // Deletes ALL user data from Firestore (profile + all subcollections + plans + follows)
    suspend fun deleteUserData(email: String) {
        if (email.isBlank()) return
        val uid = Firebase.auth.currentUser?.uid

        try {
            // All known subcollections under a user document
            val subcollections = listOf(
                "weightLogs", "dailyLogs", "dailyMetrics", "daily_health",
                "customMeals", "nutritionPlan", "meal_feedback", "exerciseLogs",
                "workoutSessions", "xp_history", "activePlan", "runSessions"
            )

            // Delete subcollections + top-level doc for EMAIL
            for (sub in subcollections) { deleteSubcollection("users", email, sub) }
            db.collection("users").document(email).delete().await()
            Log.d("UserPreferences", "Deleted email doc: $email")

            // Delete subcollections + top-level doc for UID (legacy data)
            if (uid != null && uid != email) {
                for (sub in subcollections) { deleteSubcollection("users", uid, sub) }
                try { db.collection("users").document(uid).delete().await()
                    Log.d("UserPreferences", "Deleted UID doc: $uid")
                } catch (_: Exception) {}
            }

            // Delete user_plans documents (all possible keys)
            val resolvedId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: email
            for (key in setOf(resolvedId, uid, email).filterNotNull()) {
                try { db.collection("user_plans").document(key).delete().await() } catch (_: Exception) {}
            }

            // Delete follow relationships
            try {
                for (key in setOf(resolvedId, uid, email).filterNotNull()) {
                    val asDocs = db.collection("follows").whereEqualTo("followerId", key).get().await()
                    for (doc in asDocs) { doc.reference.delete().await() }
                    val bsDocs = db.collection("follows").whereEqualTo("followingId", key).get().await()
                    for (doc in bsDocs) { doc.reference.delete().await() }
                }
            } catch (_: Exception) {}

            Log.d("UserPreferences", "✅ All Firestore data deleted for $email")
        } catch (e: Exception) {
            Log.e("UserPreferences", "❌ Error deleting user data", e)
            e.printStackTrace()
            throw e
        }
    }

    /** Delete all documents in a subcollection */
    private suspend fun deleteSubcollection(parentCollection: String, docId: String, subName: String) {
        try {
            val docs = db.collection(parentCollection).document(docId)
                .collection(subName).get().await()
            for (doc in docs) { doc.reference.delete().await() }
        } catch (_: Exception) {}
    }

    /** Clear ALL local data (SharedPreferences). Call on delete data/account. */
    fun clearAllLocalData(context: Context) {
        val prefNames = listOf(
            "user_prefs", "body_module", "nutrition_xp", "bm_prefs",
            "smartwatch_prefs", "algorithm_prefs", "weight_widget_prefs",
            "water_widget_prefs", "food_cache", "water_cache", "burned_cache",
            "daily_sync_prefs", "streak_widget_prefs"
        )
        for (name in prefNames) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
        Log.d("UserPreferences", "✅ All local SharedPreferences cleared")
    }
}
