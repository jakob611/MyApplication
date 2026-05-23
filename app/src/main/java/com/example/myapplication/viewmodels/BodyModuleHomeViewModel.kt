package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.model.BodyGoldenRatioResult
import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.domain.model.UserDayStatus
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.gamification.WorkoutCompletionResult
import com.example.myapplication.domain.auth.AuthStateRepository
import com.example.myapplication.domain.profile.UserProfileRepository
import com.example.myapplication.domain.usecase.CalculateBodyGoldenRatioUseCase
import com.example.myapplication.domain.usecase.GetBodyMetricsUseCase
import com.example.myapplication.domain.usecase.SwapPlanDaysUseCase
import com.example.myapplication.domain.usecase.UpdateBodyMetricsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Faza 30.1 — Vhodni podatki za telesni Zlati Rez.
 * shoulderCm/waistCm = 0.0 pomeni: ni vnosa → goldenRatioResults = null.
 */
data class BodyMeasurementsInput(
    val shoulderCm: Double = 0.0,
    val waistCm: Double    = 0.0,
    val hipCm: Double      = 0.0,
    val heightCm: Double   = 0.0  // fallback, če profil nima višine
)

data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val xpReward: Int = 100,
    val accepted: Boolean = false,
    val completed: Boolean = false,
    val iconRes: Int? = null
)

/**
 * UI stanje za Body Home zaslon.
 *
 * Faza 21: todayStatus je zdaj tipsko-varni [UserDayStatus] namesto String.
 */
data class BodyHomeUiState(
    val streakDays: Int = 0,
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
    /** Tipsko-varni status današnjega dne — nadomešča String "WORKOUT_DONE" itd. */
    val todayStatus: UserDayStatus = UserDayStatus.WORKOUT_PENDING,
    val challenges: List<Challenge> = listOf(
        Challenge("c1", "30 days sixpack", "Get a sixpack in 30 days. Perform core exercises daily.", 500),
        Challenge("c2", "30 days pushups", "Improve your pushups in 30 days. Do 50 pushups/day.", 300),
        Challenge("c3", "Mobility week", "Increase your ROM in 7 days. Stretch every morning.", 150)
    )
)

sealed class BodyHomeIntent {
    data class LoadMetrics(val email: String, val plan: PlanResult? = null) : BodyHomeIntent()
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
        val focusAreas: List<String> = emptyList(),
        val onCompletion: (WorkoutCompletionResult?) -> Unit = {}
    ) : BodyHomeIntent()
}

data class StreakUpdateEvent(val newStreak: Int, val isRestDay: Boolean = false)

/**
 * Faza 30.2 — BodyModuleHomeViewModel brez kakršnih koli Firebase SDK klicev.
 *
 * Auth stanje pride reaktivno prek [AuthStateRepository] vmesnika.
 * Profil se avtomatično osvežuje ob spremembi prijave (flatMapLatest).
 */
class BodyModuleHomeViewModel(
    private val getBodyMetrics: GetBodyMetricsUseCase,
    private val updateBodyMetrics: UpdateBodyMetricsUseCase,
    private val swapPlanDays: SwapPlanDaysUseCase,
    private val gamificationUseCase: ManageGamificationUseCase? = null,
    // Faza 30.2: non-nullable — ViewModel ga nujno potrebuje
    private val userProfileRepository: UserProfileRepository,
    // Faza 30.2: reaktivni auth — NI več FirebaseAuth.getInstance() v VM
    private val authStateRepository: AuthStateRepository
) : ViewModel() {

    // ── Faza 30.1 — Domain Use Case za telesni Zlati Rez ──────────────────────
    private val calculateBodyGoldenRatio = CalculateBodyGoldenRatioUseCase()

    // ── Faza 30.2 — Reaktivni profil brez Firebase v VM ──────────────────────
    /**
     * flatMapLatest: ob vsaki spremembi stanja prijave (login/logout) se
     * avtomatično zamenja na pravi profil ali flowOf(null).
     * Preživi rotacijo zaslona — ViewModel ostane živ med config change.
     */
    val bodyProfile: StateFlow<UserProfile?> =
        authStateRepository.observeCurrentUserEmail()
            .flatMapLatest { email ->
                if (email != null) userProfileRepository.observeUserProfile(email)
                else flowOf(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Faza 30.1 — Vhodni podatki za Golden Ratio (iz UI vnosa) ─────────────
    /** UI kliče [updateBodyMeasurements] ko uporabnik vnese obsege. */
    private val _bodyMeasurements = MutableStateFlow(BodyMeasurementsInput())

    /**
     * Reaktivni rezultati telesnega Zlatega Reza.
     * Kombinira profil (višina, spol) z ročno vnesenimi meritvami.
     * null kadar obseg ramen ali pasu ni vnesen.
     *
     * Faza 30.1 — Pasiven UI:
     *   UI NE računa — samo bere ta StateFlow in izriše vrednosti.
     */
    val goldenRatioResults: StateFlow<BodyGoldenRatioResult?> = combine(
        bodyProfile,
        _bodyMeasurements
    ) { profile, measurements ->
        if (measurements.shoulderCm <= 0.0 || measurements.waistCm <= 0.0) return@combine null
        try {
            calculateBodyGoldenRatio(
                shoulderCm = measurements.shoulderCm,
                waistCm    = measurements.waistCm,
                hipCm      = measurements.hipCm,
                heightCm   = profile?.height ?: measurements.heightCm,
                isMale     = profile?.gender?.equals("Male", ignoreCase = true) ?: true
            )
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("BodyModuleHomeVM", "Golden ratio calc error: ${e.message}")
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Kliče UI ko vnese meritve (obseg ramen, pas, boki). */
    fun updateBodyMeasurements(
        shoulderCm: Double,
        waistCm: Double,
        hipCm: Double = 0.0,
        heightCm: Double = 0.0
    ) {
        _bodyMeasurements.value = BodyMeasurementsInput(shoulderCm, waistCm, hipCm, heightCm)
    }

    // ──────────────────────────────────────────────────────────────────────────

    private val _ui = MutableStateFlow(BodyHomeUiState())
    val ui: StateFlow<BodyHomeUiState> = _ui.asStateFlow()

    private val _streakUpdatedEvent = MutableSharedFlow<StreakUpdateEvent>(extraBufferCapacity = 1)
    val streakUpdatedEvent: SharedFlow<StreakUpdateEvent> = _streakUpdatedEvent.asSharedFlow()

    /**
     * Faza 23: Job tracking za LoadMetrics — vsak klic cancela prejšnjega.
     * Preprečuje race condition med vzporednimi Firestore branii.
     */
    private var loadMetricsJob: Job? = null

    fun handleIntent(intent: BodyHomeIntent) {
        when (intent) {
            is BodyHomeIntent.LoadMetrics -> {
                // Cancela morebitni prejšnji LoadMetrics — prepreči dvojno branje
                loadMetricsJob?.cancel()
                loadMetricsJob = viewModelScope.launch {
                    _ui.value = _ui.value.copy(isLoading = true, errorMessage = null)
                    try {
                        getBodyMetrics.invoke(intent.email).collect { metrics ->
                            if (metrics.isLoading) return@collect
                            val todayIsRest = intent.plan?.weeks
                                ?.flatMap { it.days }
                                ?.firstOrNull { it.dayNumber == metrics.planDay }
                                ?.isRestDay ?: false

                            _ui.value = _ui.value.copy(
                                streakDays             = metrics.streakDays,
                                streakFreezes          = metrics.streakFreezes,
                                weeklyDone             = metrics.weeklyDone,
                                weeklyTarget           = metrics.weeklyTarget,
                                planDay                = metrics.planDay,
                                totalWorkoutsCompleted = metrics.totalWorkoutsCompleted,
                                isWorkoutDoneToday     = metrics.isWorkoutDoneToday,
                                dailyKcal              = metrics.dailyKcal,
                                todayIsRest            = todayIsRest,
                                todayStatus            = metrics.todayStatus,
                                isLoading              = false,
                                errorMessage           = metrics.errorMessage
                            )
                        }
                    } catch (e: Exception) {
                        _ui.value = _ui.value.copy(errorMessage = e.message, isLoading = false)
                    }
                }
            }

            is BodyHomeIntent.CompleteRestDay -> {
                if (gamificationUseCase == null) {
                    android.util.Log.w("BodyModuleHomeVM", "gamificationUseCase ni nastavljen — preskočim CompleteRestDay")
                    return
                }
                viewModelScope.launch {
                    _ui.value = _ui.value.copy(isLoading = true)
                    try {
                        val newStreak = gamificationUseCase.restDayInitiated()
                        _ui.value = _ui.value.copy(
                            isWorkoutDoneToday = true,
                            streakDays         = newStreak,
                            todayStatus        = UserDayStatus.REST_DAY_DONE,
                            isLoading          = false
                        )
                        _streakUpdatedEvent.tryEmit(StreakUpdateEvent(newStreak = newStreak, isRestDay = true))
                    } catch (e: Exception) {
                        _ui.value = _ui.value.copy(isLoading = false, errorMessage = e.message)
                    }
                }
            }

            is BodyHomeIntent.HideCompletionAnimation ->
                _ui.value = _ui.value.copy(showCompletionAnimation = false)

            is BodyHomeIntent.AcceptChallenge  -> { /* lokalni state */ }
            is BodyHomeIntent.CompleteChallenge -> { /* lokalni state */ }

            is BodyHomeIntent.SwapDays -> {
                val lockedDay = if (_ui.value.isWorkoutDoneToday) _ui.value.planDay else null
                val res = swapPlanDays.invoke(intent.currentPlan, intent.dayA, intent.dayB, lockedDay)
                res.onSuccess { intent.onResult(it) }
                res.onFailure { _ui.value = _ui.value.copy(errorMessage = it.message) }
            }

            is BodyHomeIntent.CompleteWorkoutSession -> {
                viewModelScope.launch {
                    _ui.value = _ui.value.copy(isLoading = true, errorMessage = null)
                    val isRestDay    = _ui.value.todayIsRest
                    val isExtra      = intent.isExtraWorkout
                    val oldPlanDay   = _ui.value.planDay
                    val oldWeeklyDone = _ui.value.weeklyDone

                    val result = updateBodyMetrics.invoke(
                        email           = intent.email,
                        totalKcal       = intent.totalKcal,
                        totalTimeMin    = intent.totalTimeMin,
                        exercisesCount  = intent.exerciseResults.size,
                        planDay         = oldPlanDay,
                        isExtra         = isExtra,
                        exerciseResults = intent.exerciseResults,
                        focusAreas      = intent.focusAreas,
                        isRestDay       = isRestDay
                    )

                    result.onSuccess { completionResult ->
                        // Faza 23: Optimistični update iz WorkoutCompletionResult — brez dodatnega Firestore read-a.
                        // moveToNextDay() je atomarno že zapisal vse podatke; streak + planDay sta znana.
                        val todayStatus = when {
                            isRestDay && isExtra -> UserDayStatus.REST_WORKOUT_DONE
                            else                 -> UserDayStatus.WORKOUT_DONE
                        }
                        val newStreak   = completionResult?.newStreakDays?.takeIf { it > 0 }
                            ?: (_ui.value.streakDays + if (todayStatus.contributesToStreak) 1 else 0)
                        val newPlanDay  = completionResult?.newPlanDay?.takeIf { it > 0 }
                            ?: (oldPlanDay + if (!isExtra) 1 else 0)
                        val newWeekly   = if (todayStatus != UserDayStatus.REST_WORKOUT_DONE)
                            (oldWeeklyDone + 1).coerceAtMost(_ui.value.weeklyTarget)
                        else oldWeeklyDone

                        _ui.value = _ui.value.copy(
                            streakDays              = newStreak,
                            weeklyDone              = newWeekly,
                            planDay                 = newPlanDay,
                            isWorkoutDoneToday      = true,
                            todayStatus             = todayStatus,
                            showCompletionAnimation = !isExtra,
                            isLoading               = false
                        )
                        _streakUpdatedEvent.tryEmit(StreakUpdateEvent(newStreak = newStreak))
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
