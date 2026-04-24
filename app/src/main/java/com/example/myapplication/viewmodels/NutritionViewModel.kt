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
import android.content.Context
import android.util.Log

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

    init {
        observeDailyTotals()
    }

    private fun observeDailyTotals() {
        viewModelScope.launch {
            uidFlow.collect { uid ->
                if (uid != null) {
                    val todayId = kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString()
                    com.example.myapplication.data.nutrition.FoodRepositoryImpl.observeDailyLog(uid, todayId).collect { doc ->
                        Log.d("DEBUG_DATA", "Raw burnedCalories value from DB: ${doc.get("burnedCalories")}")
                        
                        val serverWater = (doc.get("waterMl") as? Number)?.toInt() ?: 0
                        val serverBurned = (doc.get("burnedCalories") as? Number)?.toInt() ?: 0
                        val serverConsumed = (doc.get("consumedCalories") as? Number)?.toInt() ?: 0

                        updateDailyTotals(
                            consumed = serverConsumed,
                            burned = serverBurned,
                            water = serverWater
                        )
                    }
                }
            }
        }
    }

    fun syncHealthConnectNow(context: Context) {
        viewModelScope.launch {
            val healthManager = HealthConnectManager.getInstance(context)
            if (healthManager.isAvailable() && healthManager.hasAllPermissions()) {
                try {
                    val now = Clock.System.now().toJavaInstant()
                    val tz = TimeZone.currentSystemDefault()
                    val startOfDay = Clock.System.now().toLocalDateTime(tz).date
                    val startOfDayJavaObj = java.time.LocalDate.of(startOfDay.year, startOfDay.monthNumber, startOfDay.dayOfMonth).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
                    val todayId = startOfDay.toString()

                    val healthConnectCalories = healthManager.readCalories(startOfDayJavaObj, now)

                    val prefs = context.getSharedPreferences("hc_sync_prefs", Context.MODE_PRIVATE)
                    val lastSyncedKey = "hc_kcal_$todayId"
                    val lastSyncedHcKcal = prefs.getInt(lastSyncedKey, 0)
                    
                    val delta = healthConnectCalories - lastSyncedHcKcal

                    if (delta > 0) {
                        val db = com.example.myapplication.persistence.FirestoreHelper.getDb()
                        val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                        if (uid != null) {
                            val ref = db.collection("users").document(uid).collection("dailyLogs").document(todayId)
                            ref.set(mapOf(
                                "date" to todayId,
                                "burnedCalories" to com.google.firebase.firestore.FieldValue.increment(delta.toDouble())
                            ), com.google.firebase.firestore.SetOptions.merge()).await()
                            
                            prefs.edit().putInt(lastSyncedKey, healthConnectCalories).apply()
                            Log.d("DEBUG_DATA", "Successfully synced Health Connect delta: $delta")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("NutritionViewModel", "Failed to sync health connect data", e)
                }
            }
        }
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
