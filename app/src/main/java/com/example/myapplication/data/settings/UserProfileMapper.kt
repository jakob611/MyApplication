package com.example.myapplication.data.settings

import com.example.myapplication.domain.model.UserProfile
import com.google.firebase.firestore.DocumentSnapshot

/**
 * Faza 49 — SRP refaktoriranje: Mapiranje Firestore [DocumentSnapshot] → domenski [UserProfile].
 *
 * Odgovornost: IZKLJUČNO pretvorba DTO podatkov iz Firestorea v domenski model.
 * Brez I/O, brez stranski učinkov, čista mapirna funkcija (pure function).
 *
 * Klicatelji: [data.profile.FirestoreUserProfileRepository],
 *             [data.settings.UserProfileManager.loadProfileFromFirestore]
 */
object UserProfileMapper {

    /**
     * Pretvori Firestore [DocumentSnapshot] v domenski [UserProfile].
     *
     * @param doc    dokument iz Firestore (mora obstajati)
     * @param email  email uporabnika (se ne shrani v Firestore — posredovan iz Auth)
     */
    fun documentToUserProfile(doc: DocumentSnapshot, email: String): UserProfile {
        val badges = when (val b = doc.get("badges")) {
            is List<*> -> b.filterIsInstance<String>()
            is String  -> b.split(',').filter { it.isNotBlank() }
            else       -> emptyList()
        }
        val equipment = when (val eq = doc.get("equipment")) {
            is List<*> -> eq.filterIsInstance<String>()
            is String  -> eq.split(',').filter { it.isNotBlank() }
            else       -> emptyList()
        }
        val focusAreas = when (val fa = doc.get("focusAreas")) {
            is List<*> -> fa.filterIsInstance<String>()
            is String  -> fa.split(',').filter { it.isNotBlank() }
            else       -> emptyList()
        }
        val limitations = when (val lim = doc.get("limitations")) {
            is List<*> -> lim.filterIsInstance<String>()
            is String  -> lim.split(',').filter { it.isNotBlank() }
            else       -> emptyList()
        }

        return UserProfile(
            username              = doc.getString("username")          ?: "",
            email                 = email,
            firstName             = doc.getString("first_name")        ?: "",
            lastName              = doc.getString("last_name")         ?: "",
            address               = doc.getString("address")           ?: "",
            xp                    = (doc.get("xp") as? Number)?.toInt()                         ?: 0,
            followers             = (doc.get("followers") as? Number)?.toInt()                  ?: 0,
            following             = (doc.get("following") as? Number)?.toInt()                  ?: 0,
            badges                = badges,
            streakFreezes         = (doc.get("streak_freezes") as? Number)?.toInt()             ?: 0,
            equipment             = equipment,
            focusAreas            = focusAreas,
            workoutGoal           = doc.getString("workoutGoal")       ?: "",
            weightUnit            = doc.getString("weight_unit")       ?: "kg",
            speedUnit             = doc.getString("speed_unit")        ?: "km/h",
            startOfWeek           = doc.getString("start_of_week")     ?: "Monday",
            quietHoursStart       = doc.getString("quiet_hours_start") ?: "22:00",
            quietHoursEnd         = doc.getString("quiet_hours_end")   ?: "07:00",
            muteStreakReminders   = doc.getBoolean("mute_streak_reminders")   ?: false,
            detailedCalories      = doc.getBoolean("detailed_calories")        ?: false,
            isPublicProfile       = doc.getBoolean("is_public_profile")        ?: false,
            showLevel             = doc.getBoolean("show_level")               ?: false,
            showBadges            = doc.getBoolean("show_badges")              ?: false,
            showStreak            = doc.getBoolean("show_streak")              ?: false,
            showPlanPath          = doc.getBoolean("show_plan_path")           ?: false,
            showChallenges        = doc.getBoolean("show_challenges")          ?: false,
            showFollowers         = doc.getBoolean("show_followers")           ?: false,
            shareActivities       = doc.getBoolean("share_activities")         ?: false,
            totalWorkoutsCompleted = (doc.get("total_workouts_completed") as? Number)?.toInt() ?: 0,
            totalCaloriesBurned   = (doc.get("total_calories") as? Number)?.toDouble()         ?: 0.0,
            earlyBirdWorkouts     = (doc.get("early_bird_workouts") as? Number)?.toInt()       ?: 0,
            nightOwlWorkouts      = (doc.get("night_owl_workouts") as? Number)?.toInt()        ?: 0,
            currentLoginStreak    = (doc.get("streak_days") as? Number)?.toInt()               ?: 0,
            lastLoginDate         = doc.getString("last_login_date"),
            totalPlansCreated     = (doc.get("total_plans_created") as? Number)?.toInt()       ?: 0,
            profilePictureUrl     = doc.getString("profile_picture_url"),
            height                = (doc.get("height") as? Number)?.toDouble(),
            age                   = (doc.get("age") as? Number)?.toInt(),
            gender                = doc.getString("gender"),
            activityLevel         = doc.getString("activityLevel"),
            experience            = doc.getString("experience"),
            bodyFat               = doc.getString("bodyFat"),
            limitations           = limitations,
            nutritionStyle        = doc.getString("nutritionStyle"),
            sleepHours            = doc.getString("sleepHours"),
            goalWeightKg          = (doc.get("goalWeightKg") as? Number)?.toDouble()
        )
    }
}

