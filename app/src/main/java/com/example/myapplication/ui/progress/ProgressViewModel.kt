package com.example.myapplication.ui.progress

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.model.DailyLogSummary
import com.example.myapplication.domain.model.UserProfile
import com.example.myapplication.domain.model.WeightLog
import com.example.myapplication.domain.nutrition.calculateAdaptiveTDEE
import com.example.myapplication.domain.nutrition.calculateEMA
import com.example.myapplication.domain.repository.ProgressRepository
import com.example.myapplication.debug.NutritionDebugStore
import com.example.myapplication.debug.WeightPredictorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import com.example.myapplication.domain.now
import com.example.myapplication.domain.minusDays
import com.example.myapplication.domain.plusDays
import kotlin.math.abs

/**
 * ViewModel za ProgressScreen.
 *
 * ## Bugfix log (Phase 59a):
 *  - P-1 Fix: computeWeightPrediction() izvaja na Dispatchers.Default, ne Main niti.
 *  - P-2 Fix: Podatkovni tokovi iz ProgressRepository, ne DisposableEffect v UI.
 *  - P-3 Fix: saveWeightLog() delegira v ProgressRepository (ne direktni Firestore).
 *  - P-5 Fix: Mrtvi weightLogsState (duplikat) odstranjen — en sam listener.
 *  - P-8 Fix: Repository strogo uporablja getCurrentUserDocRef().
 *
 * @param gamificationUseCase  XP podeljevanje ob vpisu teže.
 * @param progressRepository   SSOT za vse Firestore operacije Progress modula.
 */
class ProgressViewModel(
    private val gamificationUseCase: ManageGamificationUseCase,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    companion object {
        private const val TAG = "ProgressViewModel"
    }

    // ── Reactive Data Streams ─────────────────────────────────────────────────

    /** Live seznam telesnih tež — Firestore SnapshotListener prek Repository. */
    val weightLogsState: StateFlow<List<WeightLog>> =
        progressRepository.observeWeightLogs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Live seznam dnevnih logov (kalorije + voda + burnedCalories). */
    val dailyLogsState: StateFlow<List<DailyLogSummary>> =
        progressRepository.observeDailyLogs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Porabljene kalorije po dnevih — izpeljano iz dailyLogsState.
     * Ni ločen Firestore listener — reaktivno od dailyLogsState.
     */
    val burnedByDayState: StateFlow<List<Pair<LocalDate, Double>>> =
        dailyLogsState
            .map { logs ->
                logs.filter { it.burnedCalories > 0.0 }
                    .map { it.date to it.burnedCalories }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── UserProfile (posredovano iz Screen-a) ────────────────────────────────

    private val _userProfile = MutableStateFlow(UserProfile())

    /**
     * Posodobi UserProfile, ki se uporablja pri napovedi teže.
     * Klici iz Screen-a v LaunchedEffect(userProfile).
     */
    fun updateUserProfile(profile: UserProfile) {
        _userProfile.value = profile
    }

    // ── P-1 Fix: Weight Prediction na Dispatchers.Default ────────────────────

    /**
     * Napoved teže — izračunana na Dispatchers.Default, ne Main niti.
     *
     * P-1 Fix: Prej je bilo `remember { computeWeightPrediction(...) }` v Progress.kt
     *          — sinhrono na Composition (Main) niti. Zdaj teče v viewModelScope na
     *          Dispatchers.Default prek combine { withContext(Dispatchers.Default) }.
     */
    val weightPredictionState: StateFlow<WeightPredictionFull?> = combine(
        weightLogsState,
        dailyLogsState,
        _userProfile
    ) { weightLogs, dailyLogs, userProfile ->
        withContext(Dispatchers.Default) {
            computeWeightPrediction(weightLogs, dailyLogs, userProfile)
        }?.also { full ->
            // P-5 Fix: WeightPredictorStore posodablja ViewModel, ne LaunchedEffect v Progress.kt
            WeightPredictorStore.update(
                hybridTDEE       = full.hybridTDEE,
                adaptiveTDEE     = full.adaptiveTDEE,
                emaWeightKg      = full.emaWeightKg,
                avgDailyBalance  = full.avgDailyBalanceKcal,
                predicted30      = full.predictedWeightIn30Days,
                goalWeightKg     = full.goalWeightKg,
                goalDateStr      = full.goalDateStr,
                daysToGoal       = full.daysToGoal,
                activeDaysCount  = full.activeDaysCount,
                confidenceFactor = full.confidenceFactor
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Write Operations ──────────────────────────────────────────────────────

    /** Aktivno shranjevanje — true med saveWeightLog operacijo. */
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /**
     * Shrani telesno težo prek Repository (P-3 Fix).
     * Nastavi [isSaving] na true med operacijo.
     */
    suspend fun saveWeightLog(dateStr: String, weightKg: Double): Result<Unit> {
        _isSaving.value = true
        return try {
            progressRepository.saveWeightLog(dateStr, weightKg)
        } finally {
            _isSaving.value = false
        }
    }

    /**
     * Sproži ponovni izračun prehranskega plana.
     * P-4 Fix: NutritionPlanStore klican iz Repository, ne iz @Composable.
     */
    suspend fun recalculateNutritionPlan(weightKg: Double): Result<Boolean> =
        progressRepository.recalculateUserNutritionPlan(weightKg)

    // ── Gamification ──────────────────────────────────────────────────────────

    fun awardWeightLogXP() {
        viewModelScope.launch {
            gamificationUseCase.awardXP(50, "WEIGHT_ENTRY")
        }
    }

    // ── P-1 Fix: computeWeightPrediction v ViewModel (prej private fun v Progress.kt) ──

    /**
     * Čista funkcija brez stranskih učinkov — izračuna napoved teže.
     * Kliče se IZKLJUČNO iz [weightPredictionState] combine bloka na Dispatchers.Default.
     *
     * Preseljeno iz Progress.kt (Phase 59a — P-1 fix).
     */
    private fun computeWeightPrediction(
        weightLogs: List<WeightLog>,
        dailyLogs: List<DailyLogSummary>,
        userProfile: UserProfile
    ): WeightPredictionFull? {
        if (weightLogs.isEmpty()) return null

        val sortedWeights = weightLogs.sortedBy { it.date }.map { it.weightKg }
        val emaWeightKg = calculateEMA(sortedWeights, period = 7)

        val today = LocalDate.now()
        val sevenDaysAgo = today.minusDays(6)
        // burnedCalories je zdaj del DailyLogSummary — ni ločen parameter
        val burnedMap = dailyLogs.associate { it.date to it.burnedCalories }
        val last7Days = dailyLogs.filter { it.date >= sevenDaysAgo && it.date <= today }
        val activeDaysWithData = last7Days.filter { it.calories > 0.0 }

        if (activeDaysWithData.isEmpty()) return null

        val theoreticalTDEEEarly = (NutritionDebugStore.lastBmr * 1.2).toInt()
        val prevHybridTDEE = WeightPredictorStore.lastHybridTDEE
        val effectiveTDEE: Double = when {
            prevHybridTDEE > 800     -> prevHybridTDEE.toDouble()
            theoreticalTDEEEarly > 800 -> theoreticalTDEEEarly.toDouble()
            else                     -> 2000.0
        }

        val avgDailyBalance = activeDaysWithData.map { log ->
            val dayBurned = burnedMap[log.date] ?: 0.0
            log.calories - effectiveTDEE - dayBurned
        }.average()

        val predictedChangeIn30Days = (avgDailyBalance * 30.0) / 7700.0
        val predictedWeightIn30Days = emaWeightKg + predictedChangeIn30Days

        val goalWeightKg = userProfile.goalWeightKg
        val daysToGoal: Int?
        val goalDateStr: String?

        if (goalWeightKg != null && goalWeightKg > 0.0 && avgDailyBalance != 0.0) {
            val kgDiff = goalWeightKg - emaWeightKg
            val dailyKgChange = avgDailyBalance / 7700.0
            val correctDirection = (kgDiff < 0.0 && dailyKgChange < 0.0) || (kgDiff > 0.0 && dailyKgChange > 0.0)
            if (correctDirection && abs(dailyKgChange) > 0.00001) {
                val days = (kgDiff / dailyKgChange).toInt().coerceIn(1, 3650)
                daysToGoal = days
                val goalDate = today.plusDays(days)
                goalDateStr = "${goalDate.dayOfMonth} ${monthName(goalDate.monthNumber)} ${goalDate.year}"
            } else {
                daysToGoal = null
                goalDateStr = null
            }
        } else {
            daysToGoal = null
            goalDateStr = null
        }

        val prevEmaWeightKg = if (sortedWeights.size >= 2)
            calculateEMA(sortedWeights.dropLast(1), period = 7)
        else
            emaWeightKg

        val tdeeResult = calculateAdaptiveTDEE(
            last7DaysCalories   = activeDaysWithData.map { it.calories.toInt() },
            emaWeightChangeDelta = emaWeightKg - prevEmaWeightKg,
            theoreticalTDEE     = theoreticalTDEEEarly
        )

        val confidenceValue = when {
            activeDaysWithData.size < 3  -> 0.0
            activeDaysWithData.size <= 5 -> 0.5
            else                         -> 1.0
        }

        val display = WeightPredictionDisplay(
            emaWeightKg             = emaWeightKg,
            avgDailyBalanceKcal     = avgDailyBalance,
            predictedWeightIn30Days = predictedWeightIn30Days,
            goalWeightKg            = goalWeightKg,
            daysToGoal              = daysToGoal,
            goalDateStr             = goalDateStr,
            activeDaysInLastWeek    = activeDaysWithData.size,
            confidenceFactor        = confidenceValue
        )

        return WeightPredictionFull(
            display                 = display,
            emaWeightKg             = emaWeightKg,
            avgDailyBalanceKcal     = avgDailyBalance,
            predictedWeightIn30Days = predictedWeightIn30Days,
            goalWeightKg            = goalWeightKg,
            goalDateStr             = goalDateStr,
            daysToGoal              = daysToGoal,
            activeDaysCount         = activeDaysWithData.size,
            hybridTDEE              = tdeeResult.hybridTDEE,
            adaptiveTDEE            = tdeeResult.adaptiveTDEE,
            confidenceFactor        = confidenceValue
        )
    }
}

