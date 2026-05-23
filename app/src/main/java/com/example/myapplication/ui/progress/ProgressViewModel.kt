package com.example.myapplication.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.data.store.FirestoreHelper
import com.example.myapplication.debug.WeightPredictorStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.LocalDate

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProgressViewModel(
    private val gamificationUseCase: ManageGamificationUseCase
) : ViewModel() {

    private val uidFlow = MutableStateFlow<String?>(null)

    init {
        uidFlow.value = FirestoreHelper.getCurrentUserDocId()
    }

    val weightLogsState: StateFlow<List<Pair<LocalDate, Double>>> = uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList())
        else callbackFlow {
            val listener = FirestoreHelper.getUserRef(uid).collection("weightLogs")
                .addSnapshotListener { snap, _ ->
                    val logs = snap?.documents?.mapNotNull { d ->
                        val dateStr = d.getString("date") ?: d.id
                        val w = (d.get("weightKg") as? Number)?.toDouble() ?: return@mapNotNull null
                        val date = runCatching { LocalDate.Companion.parse(dateStr) }.getOrNull()
                            ?: return@mapNotNull null
                        date to w
                    }?.sortedBy { it.first } ?: emptyList()
                    trySend(logs)
                }
            awaitClose { listener.remove() }
        }
    }.stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), emptyList())

    fun awardWeightLogXP() {
        viewModelScope.launch {
            gamificationUseCase.awardXP(50, "WEIGHT_ENTRY")
        }
    }

    suspend fun saveWeightLog(uid: String, dateStr: String, weightKg: Double, onSaved: () -> Unit) {
        try {
            FirestoreHelper.getUserRef(uid)
                .collection("weightLogs").document(dateStr)
                .set(mapOf("date" to dateStr, "weightKg" to weightKg))
                .await()
            onSaved()
        } catch (_: Exception) {}
    }

    /**
     * Faza 29.2 — SSOT za pisanje v WeightPredictorStore.
     *
     * Progress.kt kliče to funkcijo prek [LaunchedEffect(weightPredictionFull)] namesto neposrednega
     * pisanja v singleton prek [SideEffect]. Pisanje se izvaja v ozadju (viewModelScope, ne Main thread)
     * in je varno pred race conditioni — coroutine je zaporedna znotraj viewModelScope.
     *
     * @param hybridTDEE        Hib. TDEE (adaptivni × C + teoretični × (1−C)) v kcal
     * @param adaptiveTDEE      Čisto adaptivni TDEE iz opazovane telesne mase
     * @param emaWeightKg       EMA-sglajana trenutna teža
     * @param avgDailyBalance   Povprečni dnevni kalorični balans (negativno = deficit)
     * @param predicted30       Napovedana teža čez 30 dni
     * @param goalWeightKg      Ciljna teža ali null
     * @param goalDateStr       ISO datum dosege cilja ali null
     * @param daysToGoal        Dni do cilja ali null
     * @param activeDaysCount   Aktivni dnevi v zadnjih 7 dneh
     * @param confidenceFactor  Zaupanje C ∈ {0.0, 0.5, 1.0}
     */
    fun storePrediction(
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
        viewModelScope.launch(Dispatchers.Default) {
            WeightPredictorStore.lastEmaWeightKg         = emaWeightKg
            WeightPredictorStore.lastAvgDailyBalanceKcal = avgDailyBalance
            WeightPredictorStore.last30DayPredictionKg   = predicted30
            WeightPredictorStore.lastGoalWeightKg        = goalWeightKg
            WeightPredictorStore.lastGoalDateStr         = goalDateStr
            WeightPredictorStore.lastDaysToGoal          = daysToGoal
            WeightPredictorStore.lastActiveDaysCount     = activeDaysCount
            WeightPredictorStore.lastHybridTDEE          = hybridTDEE
            WeightPredictorStore.lastAdaptiveTDEE        = adaptiveTDEE
            WeightPredictorStore.lastConfidenceFactor    = confidenceFactor
            WeightPredictorStore.isReady                 = true
        }
    }
}