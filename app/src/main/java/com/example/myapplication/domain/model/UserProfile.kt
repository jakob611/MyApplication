package com.example.myapplication.domain.model

/**
 * Faza 40 — CLEAN ARCHITECTURE FIX (Anomaly 4: Domain Pollution):
 * UserProfile premaknjen iz `data/` v `domain/model/`.
 *
 * Razlog: UserProfile je domenska entiteta (ne podatkovni model) —
 * domain/profile/UserProfileRepository in vse UseCase-e ga referirajo,
 * kar je pomenilo, da je domain sloj bil odvisen od data sloja (kršitev DIP).
 *
 * `data/UserProfile.kt` zdaj vsebuje zgolj `typealias` → backwards compat
 * za data-sloj kodo brez prekinitve obstoječih importov.
 */
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
    val streakFreezes: Int = 0, // Number of available streak freezes
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
    val showStreak: Boolean = false, // Added showStreak
    val showPlanPath: Boolean = false,
    val showChallenges: Boolean = false,
    val showFollowers: Boolean = false,
    val shareActivities: Boolean = false, // Deli aktivnosti (teki, kolesarjenje...) s followerji
    // Notification preferences
    val quietHoursStart: String = "22:00",
    val quietHoursEnd: String = "07:00",
    val muteStreakReminders: Boolean = false,
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
    val sleepHours: String? = null, // "Less than 6", "6-7", "7-8", "8-9", "9+"
    // Faza 7 — Weight Predictor: ciljna teža (opcijsko)
    val goalWeightKg: Double? = null  // e.g. 75.0 kg — napoved prikaže datum dosege
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
        /**
         * Avdit (Točka 3): oba akumulatorja (totalXp, requiredXp) sta zdaj Long.
         *
         * PRED: Int akumulatorji → totalXp prekorači Int.MAX_VALUE pri levelu ~85
         *   (~2.24 mlrd XP). Overflow je ovijal (wrapped) totalXp na negativno vrednost →
         *   calculateLevel() je potencialno vstopil v neskončno zanko ali vrnil napačen nivo;
         *   xpRequiredForLevel(85) je vrnil negativno vrednost → progressToNextLevel
         *   je za level 84 pokazal 0% (totalNeeded < 0 → else 0f).
         *
         * PO: Long akumulatorji prenesejo vrednosti do ~9.2 × 10^18 (Level 400+).
         *   Int interface ohranjen — funkciji vračata Int z coerceAtMost(Int.MAX_VALUE).
         *   Za vse realistične nivoje (xp: Int ≤ Int.MAX_VALUE ≈ 2.15 mlrd) je izračun
         *   matematično korekten in progress bar nikoli ne zamrzne.
         */

        // Level calculation: 100 XP per level (exponential growth)
        fun calculateLevel(xp: Int): Int {
            val xpLong = xp.toLong()  // Prepreči Int overflow v primerjavi
            var level = 1
            var requiredXp = 100L     // Long: ni overflow do levela 400+
            var totalXp = 0L

            while (totalXp + requiredXp <= xpLong) {
                totalXp += requiredXp
                level++
                requiredXp = (requiredXp * 1.2).toLong() // 20% increase per level
            }
            return level
        }

        fun xpRequiredForLevel(level: Int): Int {
            var totalXp = 0L          // Long: prepreči overflow pri levelu 85+
            var requiredXp = 100L
            for (i in 1 until level) {
                totalXp += requiredXp
                requiredXp = (requiredXp * 1.2).toLong()
            }
            // coerceAtMost: za hipotetično ekstremne nivoje vrne Int.MAX_VALUE namesto
            // wrapped negativne vrednosti — progressToNextLevel nikoli ne zamrzne.
            return totalXp.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
    }
}

