package com.example.myapplication.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Faza 29.3 — Reaktivni singleton za Weight Predictor vrednosti.
 *
 * PRED: @Volatile var polja → thread-safety tveganje pri pisanju iz Dispatchers.Default
 * PO:   Vsako polje je MutableStateFlow (thread-safe) + read-only StateFlow za reactive collect.
 *
 * NutritionViewModel reaktivno posluša [hybridTDEEFlow] prek combine() v init{}.
 * ProgressViewModel piše prek [update()] ali property setters — nikoli direktno iz UI-ja.
 * clearUser() kliče [reset()] prek NutritionViewModel ob odjavi.
 */
object WeightPredictorStore {

    // ── Internalni MutableStateFlow-i (private, thread-safe MutableStateFlow.value) ──

    private val _lastHybridTDEE          = MutableStateFlow(0)
    private val _lastAdaptiveTDEE        = MutableStateFlow(0)
    private val _lastEmaWeightKg         = MutableStateFlow(0.0)
    private val _lastAvgDailyBalanceKcal = MutableStateFlow(0.0)
    private val _last30DayPredictionKg   = MutableStateFlow(0.0)
    private val _lastGoalDateStr         = MutableStateFlow<String?>(null)
    private val _lastGoalWeightKg        = MutableStateFlow<Double?>(null)
    private val _lastDaysToGoal          = MutableStateFlow<Int?>(null)
    private val _lastActiveDaysCount     = MutableStateFlow(0)
    private val _lastConfidenceFactor    = MutableStateFlow(0.0)
    private val _isReady                 = MutableStateFlow(false)

    // ── Read-only StateFlow-i (izpostavljeni za reactive collect) ─────────────────

    /** Hibridni TDEE — NutritionViewModel ga reactive posluša v init{} */
    val hybridTDEEFlow: StateFlow<Int>          = _lastHybridTDEE.asStateFlow()
    val adaptiveTDEEFlow: StateFlow<Int>        = _lastAdaptiveTDEE.asStateFlow()
    val emaWeightKgFlow: StateFlow<Double>      = _lastEmaWeightKg.asStateFlow()
    val avgDailyBalanceFlow: StateFlow<Double>  = _lastAvgDailyBalanceKcal.asStateFlow()
    val prediction30DayFlow: StateFlow<Double>  = _last30DayPredictionKg.asStateFlow()
    val goalDateStrFlow: StateFlow<String?>     = _lastGoalDateStr.asStateFlow()
    val goalWeightKgFlow: StateFlow<Double?>    = _lastGoalWeightKg.asStateFlow()
    val daysToGoalFlow: StateFlow<Int?>         = _lastDaysToGoal.asStateFlow()
    val activeDaysCountFlow: StateFlow<Int>     = _lastActiveDaysCount.asStateFlow()
    val confidenceFactorFlow: StateFlow<Double> = _lastConfidenceFactor.asStateFlow()
    val isReadyFlow: StateFlow<Boolean>         = _isReady.asStateFlow()

    // ── Property-style accessors za backward compatibility (ProgressViewModel) ────

    var lastHybridTDEE: Int
        get() = _lastHybridTDEE.value
        set(value) { _lastHybridTDEE.value = value }

    var lastAdaptiveTDEE: Int
        get() = _lastAdaptiveTDEE.value
        set(value) { _lastAdaptiveTDEE.value = value }

    var lastEmaWeightKg: Double
        get() = _lastEmaWeightKg.value
        set(value) { _lastEmaWeightKg.value = value }

    var lastAvgDailyBalanceKcal: Double
        get() = _lastAvgDailyBalanceKcal.value
        set(value) { _lastAvgDailyBalanceKcal.value = value }

    var last30DayPredictionKg: Double
        get() = _last30DayPredictionKg.value
        set(value) { _last30DayPredictionKg.value = value }

    var lastGoalDateStr: String?
        get() = _lastGoalDateStr.value
        set(value) { _lastGoalDateStr.value = value }

    var lastGoalWeightKg: Double?
        get() = _lastGoalWeightKg.value
        set(value) { _lastGoalWeightKg.value = value }

    var lastDaysToGoal: Int?
        get() = _lastDaysToGoal.value
        set(value) { _lastDaysToGoal.value = value }

    var lastActiveDaysCount: Int
        get() = _lastActiveDaysCount.value
        set(value) { _lastActiveDaysCount.value = value }

    var lastConfidenceFactor: Double
        get() = _lastConfidenceFactor.value
        set(value) { _lastConfidenceFactor.value = value }

    var isReady: Boolean
        get() = _isReady.value
        set(value) { _isReady.value = value }

    // ── Atomarno posodabljanje (priporočeno za ProgressViewModel) ─────────────────

    /**
     * Atomarno posodobi vse vrednosti — kliče ProgressViewModel.storePrediction().
     * Nadomešča posamezno dodeljevanje polj, kar preprečuje vmesna nekonzistentna stanja.
     */
    fun update(
        hybridTDEE: Int,
        adaptiveTDEE: Int,
        emaWeightKg: Double,
        avgDailyBalance: Double,
        predicted30: Double,
        goalWeightKg: Double?,
        goalDateStr: String?,
        daysToGoal: Int?,
        activeDaysCount: Int,
        confidenceFactor: Double
    ) {
        _lastHybridTDEE.value          = hybridTDEE
        _lastAdaptiveTDEE.value        = adaptiveTDEE
        _lastEmaWeightKg.value         = emaWeightKg
        _lastAvgDailyBalanceKcal.value = avgDailyBalance
        _last30DayPredictionKg.value   = predicted30
        _lastGoalWeightKg.value        = goalWeightKg
        _lastGoalDateStr.value         = goalDateStr
        _lastDaysToGoal.value          = daysToGoal
        _lastActiveDaysCount.value     = activeDaysCount
        _lastConfidenceFactor.value    = confidenceFactor
        _isReady.value                 = true
    }

    /**
     * Ponastavi vse vrednosti na privzete.
     * Kliče se ob odjavi uporabnika (NutritionViewModel.clearUser()).
     */
    fun reset() {
        _lastHybridTDEE.value          = 0
        _lastAdaptiveTDEE.value        = 0
        _lastEmaWeightKg.value         = 0.0
        _lastAvgDailyBalanceKcal.value = 0.0
        _last30DayPredictionKg.value   = 0.0
        _lastGoalDateStr.value         = null
        _lastGoalWeightKg.value        = null
        _lastDaysToGoal.value          = null
        _lastActiveDaysCount.value     = 0
        _lastConfidenceFactor.value    = 0.0
        _isReady.value                 = false
    }
}
