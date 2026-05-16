package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.gamification.GamificationState
import com.example.myapplication.domain.model.AchievementProfile
import kotlinx.coroutines.flow.Flow

class GamificationSharedViewModel(
    private val gamificationUseCase: ManageGamificationUseCase
) : ViewModel() {

    val gamificationStateFlow: Flow<GamificationState> = gamificationUseCase.getGamificationStateFlow()

    /**
     * Izračunaj badge napredek.
     * @param userProfile data.UserProfile — ViewModel ga pretvori v AchievementProfile (domain model).
     */
    fun getBadgeProgress(badgeId: String, userProfile: UserProfile): Int {
        val profile = AchievementProfile(
            totalWorkoutsCompleted = userProfile.totalWorkoutsCompleted,
            totalCaloriesBurned    = userProfile.totalCaloriesBurned,
            level                  = userProfile.level,
            followers              = userProfile.followers,
            earlyBirdWorkouts      = userProfile.earlyBirdWorkouts,
            nightOwlWorkouts       = userProfile.nightOwlWorkouts,
            currentLoginStreak     = userProfile.currentLoginStreak,
            totalPlansCreated      = userProfile.totalPlansCreated
        )
        return gamificationUseCase.getBadgeProgress(badgeId, profile)
    }
}

