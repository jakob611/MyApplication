package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import kotlinx.coroutines.launch

class NutritionViewModel(
    private val gamificationUseCase: ManageGamificationUseCase
) : ViewModel() {

    fun awardNutritionXP(xp: Int) {
        viewModelScope.launch {
            gamificationUseCase.awardXP(xp, "NUTRITION_GOAL")
        }
    }
}

