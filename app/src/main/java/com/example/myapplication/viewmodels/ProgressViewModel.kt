package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ProgressViewModel(
    private val gamificationUseCase: ManageGamificationUseCase
) : ViewModel() {

    fun awardWeightLogXP() {
        viewModelScope.launch {
            gamificationUseCase.awardXP(50, "WEIGHT_ENTRY")
        }
    }

    suspend fun saveWeightLog(uid: String, dateStr: String, weightKg: Double, onSaved: () -> Unit) {
        try {
            com.example.myapplication.persistence.FirestoreHelper.getUserRef(uid)
                .collection("weightLogs").document(dateStr)
                .set(mapOf("date" to dateStr, "weightKg" to weightKg))
                .await()
            onSaved()
        } catch (_: Exception) {}
    }
}
