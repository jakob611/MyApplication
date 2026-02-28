package com.example.myapplication.data

data class UserProfile(
    val username: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val address: String = "",
    val xp: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    val badges: List<String> = emptyList(),
    val equipment: List<String> = emptyList(), // Added equipment field
    val focusAreas: List<String> = emptyList(), // Added focus areas from plan
    val workoutGoal: String = "", // Added workout goal from plan
    val weightUnit: String = "kg",
    val speedUnit: String = "km/h",
    val startOfWeek: String = "Monday",
    // Display settings
    val detailedCalories: Boolean = false, // false = simple blue, true = segmented fat/protein/carbs
    // Privacy settings for public profile
    val isPublicProfile: Boolean = false,
    val showLevel: Boolean = false,
    val showBadges: Boolean = false,
    val showPlanPath: Boolean = false,
    val showChallenges: Boolean = false,
    val showFollowers: Boolean = false,
    // Achievement tracking
    val totalWorkoutsCompleted: Int = 0,
    val totalCaloriesBurned: Double = 0.0,
    val earlyBirdWorkouts: Int = 0, // Workouts before 7 AM
    val nightOwlWorkouts: Int = 0, // Workouts after 9 PM
    val currentLoginStreak: Int = 0,
    val lastLoginDate: String? = null,
    val totalPlansCreated: Int = 0,
    val profilePictureUrl: String? = null,
    // Plan calculation parameters (za avtomatsko posodobitev nutrition plana)
    val height: Double? = null, // cm
    val age: Int? = null,
    val gender: String? = null, // "Male" ali "Female"
    val activityLevel: String? = null, // "2x", "3x", "4x", "5x", "6x"
    val experience: String? = null, // "Beginner", "Intermediate", "Advanced"
    val bodyFat: String? = null,
    val limitations: List<String> = emptyList(),
    val nutritionStyle: String? = null, // "Standard", "Vegetarian", "Vegan", "Keto/LCHF", "Intermittent fasting"
    val sleepHours: String? = null // "Less than 6", "6-7", "7-8", "8-9", "9+"
) {
    val level: Int
        get() = calculateLevel(xp)

    val xpForCurrentLevel: Int
        get() = xpRequiredForLevel(level)

    val xpForNextLevel: Int
        get() = xpRequiredForLevel(level + 1)

    val progressToNextLevel: Float
        get() {
            val currentLevelXp = xpForCurrentLevel
            val nextLevelXp = xpForNextLevel
            val currentProgress = xp - currentLevelXp
            val totalNeeded = nextLevelXp - currentLevelXp
            return if (totalNeeded > 0) currentProgress.toFloat() / totalNeeded else 0f
        }

    companion object {
        // Level calculation: 100 XP per level (exponential growth)
        fun calculateLevel(xp: Int): Int {
            var level = 1
            var requiredXp = 100
            var totalXp = 0

            while (totalXp + requiredXp <= xp) {
                totalXp += requiredXp
                level++
                requiredXp = (requiredXp * 1.2).toInt() // 20% increase per level
            }
            return level
        }

        fun xpRequiredForLevel(level: Int): Int {
            var totalXp = 0
            var requiredXp = 100
            for (i in 1 until level) {
                totalXp += requiredXp
                requiredXp = (requiredXp * 1.2).toInt()
            }
            return totalXp
        }
    }
}
