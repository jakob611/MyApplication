package com.example.myapplication.viewmodels

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.daily.DailyLogRepository
import com.example.myapplication.data.daily.TransactionRecord
import com.example.myapplication.debug.NutritionDebugStore
import com.example.myapplication.debug.WeightPredictorStore
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Surove TDEE vrednosti za Debug Dashboard.
 */
data class TdeeDebugInputs(
    val bmr: Double = 0.0,
    val activityMultiplier: Double = 1.2,
    val baseTdee: Double = 0.0,
    val burnedCaloriesDelta: Int = 0,
    val goalAdjustment: Int = 0,
    val dynamicTarget: Int = 0,
    val goal: String = "—",
    val consumedCalories: Int = 0,
    val waterMl: Int = 0
)

/**
 * Surove Weight Predictor vrednosti za Debug Dashboard.
 */
data class WeightPredictorDebugInputs(
    val emaWeightKg: Double = 0.0,
    val avgDailyBalanceKcal: Double = 0.0,
    val predicted30DayKg: Double = 0.0,
    val goalWeightKg: Double? = null,
    val daysToGoal: Int? = null,
    val goalDateStr: String? = null,
    val activeDaysInLastWeek: Int = 0,
    val isReady: Boolean = false,
    // ── Hibridni TDEE (Faza 7.1) ──────────────────────────────────────────
    val hybridTDEE: Int = 0,
    val adaptiveTDEE: Int = 0,
    val confidenceFactor: Double = 0.0
)

/**
 * Debug Dashboard ViewModel.
 * Zbira podatke iz DailyLogRepository.lastTransactions, NutritionDebugStore in Firestore metadata.
 */
class DebugViewModel : ViewModel() {

    // ── Sledilnik transakcij ─────────────────────────────────────────────────
    private val _transactions = MutableStateFlow<List<TransactionRecord>>(emptyList())
    val transactions: StateFlow<List<TransactionRecord>> = _transactions.asStateFlow()

    // ── Cache vs Server indikator ─────────────────────────────────────────────
    /** null = ni preverjeno, true = iz cache, false = iz strežnika */
    private val _isFromCache = MutableStateFlow<Boolean?>(null)
    val isFromCache: StateFlow<Boolean?> = _isFromCache.asStateFlow()

    // ── TDEE surove vrednosti ─────────────────────────────────────────────
    private val _tdeeInputs = MutableStateFlow(TdeeDebugInputs())
    val tdeeInputs: StateFlow<TdeeDebugInputs> = _tdeeInputs.asStateFlow()

    // ── Weight Predictor surove vrednosti ─────────────────────────────────
    private val _weightPredictorInputs = MutableStateFlow(WeightPredictorDebugInputs())
    val weightPredictorInputs: StateFlow<WeightPredictorDebugInputs> = _weightPredictorInputs.asStateFlow()

    // ── Hard Reset status ─────────────────────────────────────────────────────
    private val _hardResetStatus = MutableStateFlow("")
    val hardResetStatus: StateFlow<String> = _hardResetStatus.asStateFlow()

    init {
        startPolling()
        checkCacheStatus()
    }

    /**
     * Vsake 2s osveži transakcije in TDEE vrednosti iz singleton shrambe.
     */
    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                _transactions.value = synchronized(DailyLogRepository.lastTransactions) {
                    DailyLogRepository.lastTransactions.toList()
                }
                val bmr = NutritionDebugStore.lastBmr
                val burned = NutritionDebugStore.lastBurnedCalories
                val goalAdj = NutritionDebugStore.lastGoalAdjustment
                val baseTdee = bmr * NutritionDebugStore.activityMultiplier
                val dynamic = (baseTdee + burned + goalAdj).coerceAtLeast(1200.0).toInt()
                _tdeeInputs.value = TdeeDebugInputs(
                    bmr = bmr,
                    activityMultiplier = NutritionDebugStore.activityMultiplier,
                    baseTdee = baseTdee,
                    burnedCaloriesDelta = burned,
                    goalAdjustment = goalAdj,
                    dynamicTarget = dynamic,
                    goal = NutritionDebugStore.lastGoal,
                    consumedCalories = NutritionDebugStore.lastConsumedCalories,
                    waterMl = NutritionDebugStore.lastWaterMl
                )
                // Weight Predictor store
                _weightPredictorInputs.value = WeightPredictorDebugInputs(
                    emaWeightKg = WeightPredictorStore.lastEmaWeightKg,
                    avgDailyBalanceKcal = WeightPredictorStore.lastAvgDailyBalanceKcal,
                    predicted30DayKg = WeightPredictorStore.last30DayPredictionKg,
                    goalWeightKg = WeightPredictorStore.lastGoalWeightKg,
                    daysToGoal = WeightPredictorStore.lastDaysToGoal,
                    goalDateStr = WeightPredictorStore.lastGoalDateStr,
                    activeDaysInLastWeek = WeightPredictorStore.lastActiveDaysCount,
                    isReady = WeightPredictorStore.isReady,
                    hybridTDEE = WeightPredictorStore.lastHybridTDEE,
                    adaptiveTDEE = WeightPredictorStore.lastAdaptiveTDEE,
                    confidenceFactor = WeightPredictorStore.lastConfidenceFactor
                )
                delay(2000)
            }
        }
    }

    /**
     * Preveri, ali je zadnji dailyLog iz Firestore lokalnega cache-a ali strežnika.
     * Metadata.isFromCache = true → podatki prihajajo iz lokalnega SQLite cache (Firestore Persistence).
     */
    fun checkCacheStatus() {
        viewModelScope.launch {
            val uid = FirestoreHelper.getCurrentUserDocId() ?: return@launch
            val today = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            try {
                // DEFAULT source: vrne cached + pove metadata.isFromCache
                val doc = Firebase.firestore
                    .collection("users").document(uid)
                    .collection("dailyLogs").document(today)
                    .get(Source.DEFAULT)
                    .await()
                _isFromCache.value = doc.metadata.isFromCache
            } catch (e: Exception) {
                _isFromCache.value = null
            }
        }
    }

    /**
     * Nasilna sinhronizacija: terminira Firestore instanco, pobriše lokalni SQLite cache,
     * nato znova zažene aplikacijo s svežimi podatki s strežnika.
     */
    fun hardResetFirestoreCache(context: Context) {
        viewModelScope.launch {
            _hardResetStatus.value = "🔄 Brisanje lokalnega cache-a..."
            try {
                val db = Firebase.firestore
                db.terminate().await()
                db.clearPersistence().await()
                _hardResetStatus.value = "✅ Cache izbrisan! Ponovni zagon..."
                delay(800)
                // Restartaj aplikacijo
                val intent = context.packageManager
                    .getLaunchIntentForPackage(context.packageName)
                    ?.apply { addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK) }
                if (intent != null) context.startActivity(intent)
                Process.killProcess(Process.myPid())
            } catch (e: Exception) {
                _hardResetStatus.value = "❌ Napaka: ${e.message}"
            }
        }
    }
}

