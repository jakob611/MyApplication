package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.example.myapplication.health.HealthConnectManager
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toJavaInstant

data class DailyTotals(
    val consumed: Int = 0,
    val burned: Int = 0,
    val water: Int = 0
)

class NutritionViewModel(
    private val gamificationUseCase: ManageGamificationUseCase
) : ViewModel() {

    private val _healthConnectSyncTrigger = MutableStateFlow(0)
    val healthConnectSyncTrigger: StateFlow<Int> = _healthConnectSyncTrigger.asStateFlow()

    private val _uiState = MutableStateFlow(DailyTotals())
    val uiState: StateFlow<DailyTotals> = _uiState.asStateFlow()

    private val uidFlow = MutableStateFlow<String?>(com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId())

    val customMealsState: StateFlow<com.google.firebase.firestore.QuerySnapshot?> = uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else com.example.myapplication.data.nutrition.FoodRepositoryImpl.observeCustomMeals(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun syncHealthConnectNow() {
        _healthConnectSyncTrigger.value += 1
    }

    fun updateDailyTotals(consumed: Int, burned: Int, water: Int) {
        _uiState.value = DailyTotals(consumed = consumed, burned = burned, water = water)
    }

    fun awardNutritionXP(xp: Int) {
        viewModelScope.launch {
            gamificationUseCase.awardXP(xp, "NUTRITION_GOAL")
        }
    }

    suspend fun getCustomMealItems(currentUid: String, mealId: String): List<Map<String, Any>>? {
        return try {
            val db = com.example.myapplication.persistence.FirestoreHelper.getDb()
            val doc = db.collection("users").document(currentUid)
                .collection("customMeals").document(mealId)
                .get()
                .await()

            if (doc.exists()) {
                doc.get("items") as? List<Map<String, Any>>
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
