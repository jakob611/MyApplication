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
    val showPlanPath: Boolean = false,
    val showChallenges: Boolean = false,
    val showFollowers: Boolean = false
)

data class PublicProfile(
    val userId: String,
    val username: String,
    val displayName: String? = null,
    val level: Int? = null,
    val badges: List<Badge>? = null,
    val followers: Int? = null,
    val following: Int? = null,
    val activePlanSummary: String? = null // Basic plan info if showPlanPath is true
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
