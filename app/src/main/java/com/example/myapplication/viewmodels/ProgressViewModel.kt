package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import kotlinx.coroutines.launch

class ProgressViewModel(
    private val gamificationUseCase: ManageGamificationUseCase
) : ViewModel() {

    fun awardWeightLogXP() {
        viewModelScope.launch {
            gamificationUseCase.awardXP(50, "WEIGHT_ENTRY")
        }
    }
}


