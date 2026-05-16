package com.example.myapplication.domain.model

/**
 * Minimalni domenski profil za izračun badge napredka.
 * Nadomešča `data.UserProfile` referenco v ManageGamificationUseCase.
 *
 * KMP-ready: čista Kotlin data class, brez Android odvisnosti.
 * ViewModel je odgovoren za pretvorbo data.UserProfile → AchievementProfile.
 */
data class AchievementProfile(
    val totalWorkoutsCompleted: Int = 0,
    val totalCaloriesBurned: Double = 0.0,
    val level: Int = 1,
    val followers: Int = 0,
    val earlyBirdWorkouts: Int = 0,
    val nightOwlWorkouts: Int = 0,
    val currentLoginStreak: Int = 0,
    val totalPlansCreated: Int = 0
)

