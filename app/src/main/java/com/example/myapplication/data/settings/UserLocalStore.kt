package com.example.myapplication.data.settings

import android.util.Log
import com.example.myapplication.domain.model.UserProfile
import com.example.myapplication.domain.settings.SettingsManager
import com.russhwolf.settings.Settings

/**
 * Faza 49 — SRP refaktoriranje: Vsa lokalna SharedPreferences persistenca profila
 * je izluščena iz [UserProfileManager] v ta singleton.
 *
 * Odgovornost: IZKLJUČNO lokalno branje/pisanje [UserProfile] podatkov prek Settings API.
 * Brez Firestore, brez omrežnih klicev, brez domenskih kalkulacij.
 *
 * Klicatelji: [workers.StreakReminderWorker], [ui.MainAppContent], [viewmodels.ShopViewModel]
 */
object UserLocalStore {

    private const val PREFS_NAME = "user_prefs"

    // ---- Lokalni ključi (SharedPreferences) ----
    private const val KEY_USERNAME          = "username"
    private const val KEY_FIRST_NAME        = "first_name"
    private const val KEY_LAST_NAME         = "last_name"
    private const val KEY_ADDRESS           = "address"
    private const val KEY_XP                = "xp"
    private const val KEY_FOLLOWERS         = "followers"
    private const val KEY_FOLLOWING         = "following"
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
    private const val KEY_LAST_LOGIN        = "last_login_date"
    private const val KEY_TOTAL_PLANS       = "total_plans_created"
    private const val KEY_PROFILE_PICTURE   = "profile_picture_url"

    private fun getSettings(): Settings = SettingsManager.provider.getSettings(PREFS_NAME)

    private fun userKey(email: String, key: String): String = "${email}_$key"

    // ----------------------------------------------------------------
    // WRITE
    // ----------------------------------------------------------------

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
        if (profile.lastLoginDate != null) {
            s.putString(userKey(profile.email, KEY_LAST_LOGIN), profile.lastLoginDate)
        }
        s.putInt(userKey(profile.email, KEY_TOTAL_PLANS), profile.totalPlansCreated)
        if (profile.profilePictureUrl != null) {
            s.putString(userKey(profile.email, KEY_PROFILE_PICTURE), profile.profilePictureUrl)
        }
        s.putFloat(userKey(profile.email, "height"), (profile.height ?: 0.0).toFloat())
        s.putInt(userKey(profile.email, "age"), profile.age ?: 0)
        if (profile.gender != null)        s.putString(userKey(profile.email, "gender"),         profile.gender)
        if (profile.activityLevel != null) s.putString(userKey(profile.email, "activityLevel"),   profile.activityLevel)
        if (profile.experience != null)    s.putString(userKey(profile.email, "experience"),      profile.experience)
        if (profile.bodyFat != null)       s.putString(userKey(profile.email, "bodyFat"),         profile.bodyFat)
        s.putString(userKey(profile.email, "workoutGoal"), profile.workoutGoal)
        s.putString(userKey(profile.email, "limitations"), profile.limitations.joinToString(","))
        if (profile.nutritionStyle != null) s.putString(userKey(profile.email, "nutritionStyle"), profile.nutritionStyle)
        if (profile.sleepHours != null)    s.putString(userKey(profile.email, "sleepHours"),      profile.sleepHours)
        s.putString(userKey(profile.email, "focusAreas"), profile.focusAreas.joinToString(","))
    }

    // ----------------------------------------------------------------
    // READ
    // ----------------------------------------------------------------

    fun loadProfile(email: String): UserProfile {
        val s = getSettings()
        val heightVal = s.getFloat(userKey(email, "height"), 0f).toDouble()
        val ageVal    = s.getInt(userKey(email, "age"), 0)
        return UserProfile(
            username              = s.getString(userKey(email, KEY_USERNAME), ""),
            email                 = email,
            firstName             = s.getString(userKey(email, KEY_FIRST_NAME), ""),
            lastName              = s.getString(userKey(email, KEY_LAST_NAME), ""),
            address               = s.getString(userKey(email, KEY_ADDRESS), ""),
            xp                    = s.getInt(userKey(email, KEY_XP), 0),
            followers             = s.getInt(userKey(email, KEY_FOLLOWERS), 0),
            following             = s.getInt(userKey(email, KEY_FOLLOWING), 0),
            badges                = s.getString(userKey(email, KEY_BADGES), "").split(",").filter { it.isNotBlank() },
            streakFreezes         = s.getInt(userKey(email, "streak_freezes"), 0),
            equipment             = s.getString(userKey(email, KEY_EQUIPMENT), "").split(",").filter { it.isNotBlank() },
            weightUnit            = s.getString(userKey(email, KEY_WEIGHT_UNIT), "kg"),
            speedUnit             = s.getString(userKey(email, KEY_SPEED_UNIT), "km/h"),
            startOfWeek           = s.getString(userKey(email, KEY_START_OF_WEEK), "Monday"),
            quietHoursStart       = s.getString(userKey(email, KEY_QUIET_HOURS_START), "22:00"),
            quietHoursEnd         = s.getString(userKey(email, KEY_QUIET_HOURS_END), "07:00"),
            muteStreakReminders   = s.getBoolean(userKey(email, KEY_MUTE_STREAK), false),
            detailedCalories      = s.getBoolean(userKey(email, "detailed_calories"), false),
            isPublicProfile       = s.getBoolean(userKey(email, KEY_IS_PUBLIC), false),
            showLevel             = s.getBoolean(userKey(email, KEY_SHOW_LEVEL), false),
            showBadges            = s.getBoolean(userKey(email, KEY_SHOW_BADGES), false),
            showStreak            = s.getBoolean(userKey(email, KEY_SHOW_STREAK), false),
            showPlanPath          = s.getBoolean(userKey(email, KEY_SHOW_PLAN_PATH), false),
            showChallenges        = s.getBoolean(userKey(email, KEY_SHOW_CHALLENGES), false),
            showFollowers         = s.getBoolean(userKey(email, KEY_SHOW_FOLLOWERS), false),
            shareActivities       = s.getBoolean(userKey(email, KEY_SHARE_ACTIVITIES), false),
            totalWorkoutsCompleted = s.getInt(userKey(email, "total_workouts_completed"), 0),
            totalCaloriesBurned   = s.getFloat(userKey(email, KEY_TOTAL_CALORIES), 0f).toDouble(),
            earlyBirdWorkouts     = s.getInt(userKey(email, KEY_EARLY_BIRD), 0),
            nightOwlWorkouts      = s.getInt(userKey(email, KEY_NIGHT_OWL), 0),
            currentLoginStreak    = s.getInt(userKey(email, "streak_days"), 0),
            lastLoginDate         = s.getStringOrNull(userKey(email, KEY_LAST_LOGIN)),
            totalPlansCreated     = s.getInt(userKey(email, KEY_TOTAL_PLANS), 0),
            profilePictureUrl     = s.getStringOrNull(userKey(email, KEY_PROFILE_PICTURE)),
            height                = if (heightVal > 0) heightVal else null,
            age                   = if (ageVal > 0) ageVal else null,
            gender                = s.getStringOrNull(userKey(email, "gender")),
            activityLevel         = s.getStringOrNull(userKey(email, "activityLevel")),
            experience            = s.getStringOrNull(userKey(email, "experience")),
            bodyFat               = s.getStringOrNull(userKey(email, "bodyFat")),
            workoutGoal           = s.getString(userKey(email, "workoutGoal"), ""),
            limitations           = s.getString(userKey(email, "limitations"), "").split(",").filter { it.isNotBlank() },
            nutritionStyle        = s.getStringOrNull(userKey(email, "nutritionStyle")),
            sleepHours            = s.getStringOrNull(userKey(email, "sleepHours")),
            focusAreas            = s.getString(userKey(email, "focusAreas"), "").split(",").filter { it.isNotBlank() }
        )
    }

    // ----------------------------------------------------------------
    // CLEAR
    // ----------------------------------------------------------------

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
        Log.d("UserLocalStore", "✅ All local SharedPreferences cleared + fresh_start flag set")
    }
}

