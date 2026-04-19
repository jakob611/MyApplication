package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NutritionViewModel(
    private val gamificationUseCase: ManageGamificationUseCase
) : ViewModel() {

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
