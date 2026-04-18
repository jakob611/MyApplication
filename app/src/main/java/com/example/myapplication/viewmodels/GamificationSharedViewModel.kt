package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.gamification.GamificationState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class GamificationSharedViewModel(
    private val gamificationUseCase: ManageGamificationUseCase
) : ViewModel() {

    val gamificationStateFlow: Flow<GamificationState> = gamificationUseCase.getGamificationStateFlow()

    fun getBadgeProgress(badgeId: String, userProfile: UserProfile): Int {
        return gamificationUseCase.getBadgeProgress(badgeId, userProfile)
    }
}



