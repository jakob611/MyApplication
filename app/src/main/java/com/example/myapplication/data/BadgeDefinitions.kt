package com.example.myapplication.data

object BadgeDefinitions {

    // Badge unlock condition types
    sealed class UnlockCondition {
        data class WorkoutCount(val required: Int) : UnlockCondition()
        data class CaloriesBurned(val required: Double) : UnlockCondition()
        data class ReachLevel(val required: Int) : UnlockCondition()
        data class FollowerCount(val required: Int) : UnlockCondition()
        data class StreakDays(val required: Int) : UnlockCondition()
        data class WorkoutTime(val hour: Int, val count: Int) : UnlockCondition() // Workout at specific time
        data class PlanCount(val required: Int) : UnlockCondition()
        object FirstLogin : UnlockCondition()
    }

    val ALL_BADGES = listOf(
        // Workout Badges
        Badge(
            id = "first_workout",
            name = "First Workout",
            description = "Complete 1 workout",
            iconName = "EmojiEvents", // Trophy icon
            category = BadgeCategory.WORKOUT,
            requirement = 1
        ),
        Badge(
            id = "committed_10",
            name = "Getting Started",
            description = "Complete 10 workouts",
            iconName = "FitnessCenter",
            category = BadgeCategory.WORKOUT,
            requirement = 10
        ),
        Badge(
            id = "committed_50",
            name = "Dedicated",
            description = "Complete 50 workouts",
            iconName = "EmojiEvents",
            category = BadgeCategory.WORKOUT,
            requirement = 50
        ),
        Badge(
            id = "committed_100",
            name = "Committed",
            description = "Complete 100 workouts",
            iconName = "EmojiEvents",
            category = BadgeCategory.WORKOUT,
            requirement = 100
        ),

        // Calorie Badges
        Badge(
            id = "calorie_crusher_1k",
            name = "Calorie Burner",
            description = "Burn 1,000 calories total",
            iconName = "LocalFireDepartment",
            category = BadgeCategory.ACHIEVEMENT,
            requirement = 1000
        ),
        Badge(
            id = "calorie_crusher_5k",
            name = "Calorie Crusher",
            description = "Burn 5,000 calories total",
            iconName = "LocalFireDepartment",
            category = BadgeCategory.ACHIEVEMENT,
            requirement = 5000
        ),
        Badge(
            id = "calorie_crusher_10k",
            name = "Inferno",
            description = "Burn 10,000 calories total",
            iconName = "LocalFireDepartment",
            category = BadgeCategory.ACHIEVEMENT,
            requirement = 10000
        ),

        // Level Badges
        Badge(
            id = "level_5",
            name = "Level 5",
            description = "Reach level 5",
            iconName = "Star",
            category = BadgeCategory.ACHIEVEMENT,
            requirement = 5
        ),
        Badge(
            id = "level_10",
            name = "Level 10",
            description = "Reach level 10",
            iconName = "Star",
            category = BadgeCategory.ACHIEVEMENT,
            requirement = 10
        ),
        Badge(
            id = "level_25",
            name = "Level 25",
            description = "Reach level 25",
            iconName = "Star",
            category = BadgeCategory.ACHIEVEMENT,
            requirement = 25
        ),

        // Social Badges
        Badge(
            id = "first_follower",
            name = "First Follower",
            description = "Get your first follower",
            iconName = "Person",
            category = BadgeCategory.SOCIAL,
            requirement = 1
        ),
        Badge(
            id = "social_butterfly",
            name = "Social Butterfly",
            description = "Get 10 followers",
            iconName = "Group",
            category = BadgeCategory.SOCIAL,
            requirement = 10
        ),
        Badge(
            id = "influencer",
            name = "Influencer",
            description = "Get 50 followers",
            iconName = "Group",
            category = BadgeCategory.SOCIAL,
            requirement = 50
        ),

        // Time-based Badges
        Badge(
            id = "early_bird",
            name = "Early Bird",
            description = "Complete 5 workouts before 7 AM",
            iconName = "WbSunny",
            category = BadgeCategory.SPECIAL,
            requirement = 5
        ),
        Badge(
            id = "night_owl",
            name = "Night Owl",
            description = "Complete 5 workouts after 9 PM",
            iconName = "NightsStay",
            category = BadgeCategory.SPECIAL,
            requirement = 5
        ),

        // Streak Badges
        Badge(
            id = "week_warrior",
            name = "Week Warrior",
            description = "7-day login streak",
            iconName = "LocalFireDepartment",
            category = BadgeCategory.STREAK,
            requirement = 7
        ),
        Badge(
            id = "month_master",
            name = "Month Master",
            description = "30-day login streak",
            iconName = "LocalFireDepartment",
            category = BadgeCategory.STREAK,
            requirement = 30
        ),
        Badge(
            id = "year_champion",
            name = "Year Champion",
            description = "365-day login streak",
            iconName = "LocalFireDepartment",
            category = BadgeCategory.STREAK,
            requirement = 365
        ),

        // Plan Badges
        Badge(
            id = "first_plan",
            name = "Planner",
            description = "Create your first workout plan",
            iconName = "CalendarToday",
            category = BadgeCategory.ACHIEVEMENT,
            requirement = 1
        ),
        Badge(
            id = "plan_master",
            name = "Plan Master",
            description = "Create 5 workout plans",
            iconName = "CalendarToday",
            category = BadgeCategory.ACHIEVEMENT,
            requirement = 5
        )
    )

    fun getBadgeById(id: String): Badge? = ALL_BADGES.find { it.id == id }

    fun getBadgesByCategory(category: BadgeCategory): List<Badge> =
        ALL_BADGES.filter { it.category == category }
}
