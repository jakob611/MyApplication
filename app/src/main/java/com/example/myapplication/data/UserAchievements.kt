package com.example.myapplication.data


data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String, // Material Icons name
    val category: BadgeCategory,
    val unlocked: Boolean = false,
    val unlockedAt: Long? = null,
    val animationShown: Boolean = false,
    val progress: Int = 0, // Current progress towards unlock
    val requirement: Int = 1 // Required progress to unlock
)

enum class BadgeCategory {
    WORKOUT,
    SOCIAL,
    ACHIEVEMENT,
    STREAK,
    SPECIAL
}

data class PrivacySettings(
    val isPublic: Boolean = false,
    val showLevel: Boolean = false,
    val showBadges: Boolean = false,
    val showStreak: Boolean = false, // Added showStreak
    val showPlanPath: Boolean = false,
    val showChallenges: Boolean = false,
    val showFollowers: Boolean = false,
    val shareActivities: Boolean = false // Deli teke/aktivnosti s followerji
)

data class PublicActivity(
    val id: String = "",
    val activityType: String = "RUN", // ActivityType.name
    val distanceMeters: Double = 0.0,
    val durationSeconds: Int = 0,
    val caloriesKcal: Int = 0,
    val elevationGainM: Float = 0f,
    val elevationLossM: Float = 0f,
    val avgSpeedMps: Float = 0f,
    val maxSpeedMps: Float = 0f,
    val startTime: Long = 0L,
    // Komprimirana ruta (RDP ~35 točk) — shranjeno v Firestoreu
    val routePoints: List<Pair<Double, Double>> = emptyList()
)

data class PublicProfile(
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val level: Int? = null,
    val badges: List<Badge>? = null,
    val streak: Int? = null,
    val followers: Int? = null,
    val following: Int? = null,
    val activePlanSummary: String? = null,
    /** ✅ Faza 15: Eksplicitten flag iz Firestore dokumenta GLEDANEGA uporabnika (ne lokalnih nastavitev). */
    val shareActivities: Boolean = false,
    val recentActivities: List<PublicActivity>? = null // Javne aktivnosti (samo če shareActivities=true)
)


enum class XPSource {
    WORKOUT_COMPLETE,
    DAILY_LOGIN,
    PLAN_CREATED,
    LEVEL_UP_BONUS,
    CALORIES_BURNED,
    BADGE_UNLOCKED,
    FOLLOWER_MILESTONE,
    STREAK_BONUS,
    NUTRITION_GOAL,   // Kalorijski cilj dosežen
    RUN_COMPLETED,    // Tek zaključen
    WEIGHT_ENTRY      // Vnos teže
}
