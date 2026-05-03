package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.PlanResult
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.gamification.WorkoutCompletionResult
import com.example.myapplication.domain.workout.GetBodyMetricsUseCase
import com.example.myapplication.domain.workout.SwapPlanDaysUseCase
import com.example.myapplication.domain.workout.UpdateBodyMetricsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val xpReward: Int = 100,
    val accepted: Boolean = false,
    val completed: Boolean = false,
    val iconRes: Int? = null
)

data class BodyHomeUiState(
    val streakDays: Int = 0,
    /** Faza 13.3: število razpoložljivih Streak Freeze zamrznitev */
    val streakFreezes: Int = 0,
    val weeklyDone: Int = 0,
    val weeklyTarget: Int = 3,
    val planDay: Int = 1,
    val totalWorkoutsCompleted: Int = 0,
    val workoutsToday: Int = 0,
    val isWorkoutDoneToday: Boolean = false,
    val dailyKcal: Int = 0,
    val showCompletionAnimation: Boolean = false,
    val todayIsRest: Boolean = false,
    val outdoorSuggestion: String? = null,
    val errorMessage: String? = null,
    val isLoading: Boolean = false,
    val challenges: List<Challenge> = listOf(
        Challenge("c1", "30 days sixpack", "Get a sixpack in 30 days. Perform core exercises daily.", 500),
        Challenge("c2", "30 days pushups", "Improve your pushups in 30 days. Do 50 pushups/day.", 300),
        Challenge("c3", "Mobility week", "Increase your ROM in 7 days. Stretch every morning.", 150)
    )
)

sealed class BodyHomeIntent {
    data class LoadMetrics(val email: String) : BodyHomeIntent()
    object CompleteRestDay : BodyHomeIntent()
    object HideCompletionAnimation : BodyHomeIntent()
    data class AcceptChallenge(val id: String) : BodyHomeIntent()
    data class CompleteChallenge(val id: String) : BodyHomeIntent()
    data class SwapDays(val currentPlan: PlanResult, val dayA: Int, val dayB: Int, val onResult: (PlanResult) -> Unit) : BodyHomeIntent()
    data class CompleteWorkoutSession(
        val email: String,
        val isExtraWorkout: Boolean = false,
        val totalKcal: Int = 0,
        val totalTimeMin: Double = 0.0,
        val exerciseResults: List<Map<String, Any>> = emptyList(),
        /** Fokus mišic te seje — shranjeno v workoutDoc za fetchLastSessionForFocus() (Faza 12). */
        val focusAreas: List<String> = emptyList(),
        val onCompletion: (WorkoutCompletionResult?) -> Unit = {}
    ) : BodyHomeIntent()
}

/** Faza 4b: Event za Toast + HapticFeedback ko se streak poveča */
data class StreakUpdateEvent(val newStreak: Int, val isRestDay: Boolean = false)

class BodyModuleHomeViewModel(
    private val getBodyMetrics: GetBodyMetricsUseCase,
    private val updateBodyMetrics: UpdateBodyMetricsUseCase,
    private val swapPlanDays: SwapPlanDaysUseCase,
    /** Faza 4b: Za CompleteRestDay (Stretching) streak logiko */
    private val gamificationUseCase: ManageGamificationUseCase? = null
) : ViewModel() {

    private val _ui = MutableStateFlow(BodyHomeUiState())
    val ui: StateFlow<BodyHomeUiState> = _ui.asStateFlow()

    /** Faza 4b: Event za prikaz Toast "Daily Goal Met! Streak: X days 🔥" + HapticFeedback */
    private val _streakUpdatedEvent = MutableSharedFlow<StreakUpdateEvent>(extraBufferCapacity = 1)
    val streakUpdatedEvent: SharedFlow<StreakUpdateEvent> = _streakUpdatedEvent.asSharedFlow()

    fun handleIntent(intent: BodyHomeIntent) {
        when (intent) {
            is BodyHomeIntent.LoadMetrics -> {
                viewModelScope.launch {
                    _ui.value = _ui.value.copy(isLoading = true, errorMessage = null)
                    try {
                        getBodyMetrics.invoke(intent.email).collect { newState ->
                            _ui.value = newState
                        }
                    } catch (e: Exception) {
                        _ui.value = _ui.value.copy(errorMessage = e.message, isLoading = false)
                    }
                }
            }
            is BodyHomeIntent.CompleteRestDay -> {
                /** Faza 4b: Stretching = Rest Day Goal Met → streak +1, XP +10, Toast + Haptic */
                if (gamificationUseCase == null) {
                    android.util.Log.w("BodyModuleHomeVM", "gamificationUseCase ni nastavljen — preskočim CompleteRestDay")
                    return
                }
                viewModelScope.launch {
                    _ui.value = _ui.value.copy(isLoading = true)
                    try {
                        val newStreak = gamificationUseCase.restDayInitiated()
                        // Optimistično posodobi UI state
                        _ui.value = _ui.value.copy(
                            isWorkoutDoneToday = true,
                            streakDays = newStreak,
                            isLoading = false
                        )
                        // Sproži event za Toast + HapticFeedback v UI sloju
                        _streakUpdatedEvent.tryEmit(StreakUpdateEvent(newStreak = newStreak, isRestDay = true))
                        android.util.Log.d("BodyModuleHomeVM", "✅ Rest Day stretching done. Novi streak: $newStreak")
                    } catch (e: Exception) {
                        _ui.value = _ui.value.copy(isLoading = false, errorMessage = e.message)
                        android.util.Log.e("BodyModuleHomeVM", "❌ CompleteRestDay napaka: ${e.message}", e)
                    }
                }
            }
            is BodyHomeIntent.HideCompletionAnimation -> {
                _ui.value = _ui.value.copy(showCompletionAnimation = false)
            }
            is BodyHomeIntent.AcceptChallenge -> {
                // Update local state for challenges
            }
            is BodyHomeIntent.CompleteChallenge -> {
                 // Update local state for challenges
            }
            is BodyHomeIntent.SwapDays -> {
                val res = swapPlanDays.invoke(intent.currentPlan, intent.dayA, intent.dayB)
                res.onSuccess { intent.onResult(it) }
                res.onFailure { _ui.value = _ui.value.copy(errorMessage = it.message) }
            }
            is BodyHomeIntent.CompleteWorkoutSession -> {
                viewModelScope.launch {
                    _ui.value = _ui.value.copy(isLoading = true, errorMessage = null)
                    val result = updateBodyMetrics.invoke(
                        email = intent.email,
                        totalKcal = intent.totalKcal,
                        totalTimeMin = intent.totalTimeMin,
                        exercisesCount = intent.exerciseResults.size,
                        planDay = _ui.value.planDay,
                        isExtra = intent.isExtraWorkout,
                        exerciseResults = intent.exerciseResults,
                        focusAreas = intent.focusAreas
                    )

                    result.onSuccess { completionResult ->
                        if (intent.email.isNotBlank()) {
                            try {
                                getBodyMetrics.invoke(intent.email).collect { freshState ->
                                    val newStreak = freshState.streakDays
                                    _ui.value = freshState.copy(
                                        showCompletionAnimation = !intent.isExtraWorkout,
                                        isWorkoutDoneToday = true,
                                        isLoading = false
                                    )
                                    // Faza 4b: Sproži Toast + Haptic tudi za dokončan workout
                                    _streakUpdatedEvent.tryEmit(StreakUpdateEvent(newStreak = newStreak, isRestDay = false))
                                }
                            } catch (_: Exception) {
                                val optimisticStreak = _ui.value.streakDays + 1
                                _ui.value = _ui.value.copy(
                                    isLoading = false,
                                    isWorkoutDoneToday = true,
                                    showCompletionAnimation = !intent.isExtraWorkout,
                                    planDay = _ui.value.planDay + if (!intent.isExtraWorkout) 1 else 0,
                                    streakDays = optimisticStreak,
                                    dailyKcal = _ui.value.dailyKcal + intent.totalKcal
                                )
                                _streakUpdatedEvent.tryEmit(StreakUpdateEvent(newStreak = optimisticStreak, isRestDay = false))
                            }
                        } else {
                            _ui.value = _ui.value.copy(
                                isLoading = false,
                                isWorkoutDoneToday = true,
                                showCompletionAnimation = !intent.isExtraWorkout,
                                dailyKcal = _ui.value.dailyKcal + intent.totalKcal
                            )
                        }
                        intent.onCompletion(completionResult)
                    }
                    result.onFailure { error ->
                        _ui.value = _ui.value.copy(isLoading = false, errorMessage = error.message)
                        intent.onCompletion(null)
                    }
                }
            }
        }
    }
}
