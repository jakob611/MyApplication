package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.model.BodyField
import com.example.myapplication.domain.model.BodyGoldenRatioResult
import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.domain.model.UserDayStatus
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.gamification.WorkoutCompletionResult
import com.example.myapplication.domain.auth.AuthStateRepository
import com.example.myapplication.domain.profile.UserProfileRepository
import com.example.myapplication.domain.model.BodyMeasurementEntry
import com.example.myapplication.domain.repository.BodyMeasurementsRepository
import com.example.myapplication.domain.usecase.CalculateBodyGoldenRatioUseCase
import com.example.myapplication.domain.usecase.GetBodyMetricsUseCase
import com.example.myapplication.domain.usecase.SaveBodyMeasurementsUseCase
import com.example.myapplication.domain.usecase.SwapPlanDaysUseCase
import com.example.myapplication.domain.usecase.UpdateBodyMetricsUseCase
import com.example.myapplication.domain.usecase.ValidationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
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
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Faza 30.9 — Enkratni UI dogodki za BodyModul.
 *
 * Razlika od StateFlow:
 *   StateFlow  = trajno stanje (kateri zaslon je odprt, ali nalagamo…)
 *   Channel    = enkratni dogodek (prikaži Snackbar, navigiraj, zapri dialog)
 *
 * Ne smeš shranjevati teh vrednosti v StateFlow — Snackbar bi se ob rotaciji zaslona
 * prikazal znova. Channel zagotavlja natanko en sprejem.
 */
sealed interface BodyUiEvent {
    /** Meritve so bile uspešno shranjene v Firestore. */
    data object SaveSuccess : BodyUiEvent
    /** Napaka med shranjevanjem — prikaži Snackbar z [message]. */
    data class Error(val message: String) : BodyUiEvent
    /**
     * Faza 32.4 — Prehodna napaka enkratnih akcij (CompleteWorkoutSession, SwapDays, CompleteRestDay).
     * Gre na Channel (enkratni sprejem) — ne onesnaži trajnega _ui.errorMessage stanja.
     * UI ga prikaže kot Snackbar in ga samodejno zavrže ob dimissal.
     */
    data class ShowSnackbar(val message: String) : BodyUiEvent
}

/**
 * Faza 31.1 — Enoten UI state za Golden Ratio sekcijo.
 * Faza 31.3 — inputErrorRes: Int? → invalidFields: Set<BodyField> za per-field natančnost.
 *
 * Faza 31.6 — Dead code odstranjen: bodyProfile je zdaj private, measurementsHistory izbrisan.
 *
 * Nadomešča tri razpršene StateFlow-e (zdaj odstranjene/private):
 *   bodyProfile (private)                → pokrit z Loading/Success
 *   goldenRatioResults: StateFlow<Result?>   → Success.data
 *   isSaving: StateFlow<Boolean>             → Success.isSaving
 *
 * Prednosti:
 *   • Atomarni prehodi — UI nikoli ne vidi nedosledne kombinacije vrednosti
 *   • Loading stanje preprečuje vizualno utripanje ("flash of empty content")
 *   • invalidFields: UI obarva SAMO polja, ki so dejansko zunaj meja
 */
sealed interface GoldenRatioUiState {
    /** Profil se nalaga iz Firestore — pokaži CircularProgressIndicator. */
    data object Loading : GoldenRatioUiState

    /**
     * Profil naložen — pokaži celotni vmesnik z vnosi.
     * @param profileHeight   Višina iz profila (za prikaz in izračun pas/višina razmerja)
     * @param shoulderInput   Faza 31.4 — surovi vnos za ramena (String, ne Double)
     * @param waistInput      Faza 31.4 — surovi vnos za pas
     * @param hipInput        Faza 31.4 — surovi vnos za boke (prazno = ni vnosu)
     * @param data            Rezultat izračuna; null dokler vnos ni veljaven/popoln
     * @param isSaving        true med Firestore batch write — gumb onemogočen
     * @param invalidFields   Set polj, ki so zunaj bioloških meja (30–250 cm).
     * @param isInputComplete Faza 31.5 — true ko so vsa 4 polja (ramena, pas, boki, višina)
     *                        uspešno parsana v vrednosti > 0.0. Ko false + data==null + ni napak,
     *                        UI prikaže nevtralen napotek za vnos.
     */
    data class Success(
        val profileHeight: Double?,
        val shoulderInput: String = "",
        val waistInput: String = "",
        val hipInput: String = "",
        val data: BodyGoldenRatioResult?,
        val isSaving: Boolean = false,
        val invalidFields: Set<BodyField> = emptySet(),
        val isInputComplete: Boolean = false
    ) : GoldenRatioUiState

    /**
     * Napaka pri inicializaciji zaslona (npr. izpad Firestore med nalaganjem profila).
     * NI namenjen validacijskim napakam vnosa — za to se uporablja [Success.invalidFields].
     * @param messageRes  ID string resursa (R.string.*) — lokalizabilen, brez Context v VM
     */
    data class Error(val messageRes: Int) : GoldenRatioUiState
}

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

/**
 * Faza 31.4 — Surovi tekstovni vnosi za telesne mere.
 * Nahajajo se v ViewModel StateFlow (ne v lokalni Compose state!) →
 * preživijo UI state tranzicije (Loading → Success itd.) brez izgube.
 */
data class BodyMeasurementsInputText(
    val shoulder: String = "",
    val waist: String    = "",
    val hip: String      = ""
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
    /** Faza 31.8 — true šele ko LoadMetrics uspešno konča (Firestore vrne podatke).
     *  false = privzete vrednosti (planDay=1 itd.) so ŠE VEDNO nezanesljive.
     *  Guard v CompleteWorkoutSession prepreči Firestore transakcijo z napačnim planDay. */
    val isDataLoaded: Boolean = false,
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
 * Faza 30.6 — Dodan BodyMeasurementsRepository za zgodovino meritev.
 *
 * Auth stanje pride reaktivno prek [AuthStateRepository] vmesnika.
 * Profil se avtomatično osvežuje ob spremembi prijave (flatMapLatest).
 */
class BodyModuleHomeViewModel(
    private val getBodyMetrics: GetBodyMetricsUseCase,
    private val updateBodyMetrics: UpdateBodyMetricsUseCase,
    private val swapPlanDays: SwapPlanDaysUseCase,
    private val gamificationUseCase: ManageGamificationUseCase? = null,
    private val userProfileRepository: UserProfileRepository,
    private val authStateRepository: AuthStateRepository,
    // Faza 30.6: Repozitorij za shranjevanje in opazovanje zgodovine meritev
    private val bodyMeasurementsRepository: BodyMeasurementsRepository
) : ViewModel() {

    // ── Faza 30.1 — Domain Use Case za telesni Zlati Rez ──────────────────────
    private val calculateBodyGoldenRatio = CalculateBodyGoldenRatioUseCase()

    // ── Faza 30.6 — Use Case za shranjevanje meritev (DI prek konstruktorja) ──
    private val saveMeasurementsUseCase = SaveBodyMeasurementsUseCase(bodyMeasurementsRepository)

    // ── Faza 30.2 — Reaktivni profil brez Firebase v VM ──────────────────────
    /**
     * flatMapLatest: ob vsaki spremembi stanja prijave (login/logout) se
     * avtomatično zamenja na pravi profil ali flowOf(null).
     * Preživi rotacijo zaslona — ViewModel ostane živ med config change.
     *
     * Faza 31.6 — private: profil se ne izpostavlja UI neposredno.
     * Javni dostop poteka izključno prek goldenRatioUiState.
     */
    private val bodyProfile: StateFlow<UserProfile?> =
        authStateRepository.observeCurrentUserEmail()
            .flatMapLatest { email ->
                if (email != null) userProfileRepository.observeUserProfile(email)
                else flowOf(null)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Faza 30.1 → Faza 31.4 — Vhodni teksti (String, ne Double) v ViewModel ─
    /**
     * Faza 31.4 — Surovi vnosi so StateFlow v ViewModel (ne lokalni rememberSaveable).
     * Preživijo vsak UI state transition (Loading ↔ Success) brez izgube vrednosti.
     */
    private val _inputText = MutableStateFlow(BodyMeasurementsInputText())

    // ── Faza 30.8 — Stanje shranjevanja (interno — javno prek goldenRatioUiState) ─
    private val _isSaving = MutableStateFlow(false)

    /**
     * Faza 31.1 — ENOTEN UI STATE za Golden Ratio sekcijo.
     * Faza 31.4 — Input strings so del Success stanja → UI vidi točno kar je vtipkal.
     *
     * Kombinira 3 vire (bodyProfile + _inputText + _isSaving) v en atomaren tok.
     * Parsing string→Double se dogaja TUKAJ — UI ostane čist (ne operira s float-i).
     */
    val goldenRatioUiState: StateFlow<GoldenRatioUiState> = combine(
        bodyProfile,
        _inputText,
        _isSaving
    ) { profile, texts, saving ->
        // Faza 31.5 — Locale-safe parsing: zamenjaj vejico s piko (slovenska tipkovnica)
        // "75,5" → "75.5" → 75.5  |  "" → 0.0  |  "abc" → 0.0
        val shoulderCm = texts.shoulder.replace(',', '.').toDoubleOrNull() ?: 0.0
        val waistCm    = texts.waist.replace(',', '.').toDoubleOrNull() ?: 0.0
        val hipCm      = texts.hip.replace(',', '.').toDoubleOrNull() ?: 0.0
        val heightCm   = profile?.height ?: 0.0

        // Faza 31.5 — Zastavica za POPOLN vnos: vsa 4 polja > 0
        // false → UI prikaže nevtralen napotek; true + data!=null → prikaži rezultat
        val isInputComplete = shoulderCm > 0.0 && waistCm > 0.0
                           && hipCm > 0.0 && heightCm > 0.0

        when {
            profile == null -> GoldenRatioUiState.Loading
            else -> {
                // Kliči UseCase samo ko sta obe obvezni vrednosti vneseni
                if (shoulderCm > 0.0 && waistCm > 0.0) {
                    when (val validation = calculateBodyGoldenRatio(
                        shoulderCm = shoulderCm,
                        waistCm    = waistCm,
                        hipCm      = hipCm,
                        heightCm   = heightCm,
                        isMale     = profile.gender?.equals("Male", ignoreCase = true) ?: true
                    )) {
                        is ValidationResult.Success -> GoldenRatioUiState.Success(
                            profileHeight   = profile.height,
                            shoulderInput   = texts.shoulder,
                            waistInput      = texts.waist,
                            hipInput        = texts.hip,
                            data            = validation.data,
                            isSaving        = saving,
                            invalidFields   = emptySet(),
                            isInputComplete = isInputComplete
                        )
                        is ValidationResult.Invalid -> {
                            android.util.Log.d(
                                "BodyModuleHomeVM",
                                "Per-field napaka: ${validation.invalidFields}"
                            )
                            GoldenRatioUiState.Success(
                                profileHeight   = profile.height,
                                shoulderInput   = texts.shoulder,
                                waistInput      = texts.waist,
                                hipInput        = texts.hip,
                                data            = null,
                                isSaving        = saving,
                                invalidFields   = validation.invalidFields,
                                isInputComplete = isInputComplete
                            )
                        }
                    }
                } else {
                    // Polja prazna ali nepopolna — čisto stanje
                    GoldenRatioUiState.Success(
                        profileHeight   = profile.height,
                        shoulderInput   = texts.shoulder,
                        waistInput      = texts.waist,
                        hipInput        = texts.hip,
                        data            = null,
                        isSaving        = saving,
                        invalidFields   = emptySet(),
                        isInputComplete = isInputComplete
                    )
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GoldenRatioUiState.Loading)

    /**
     * Faza 31.4 — Posodobi surove vnosne tekste (iz UI TextField).
     * Parsing string→Double se izvaja v combine, ne tukaj.
     */
    fun updateInputText(shoulder: String, waist: String, hip: String) {
        _inputText.value = BodyMeasurementsInputText(shoulder, waist, hip)
    }

    /** Faza 30.1 kliče UI ko vnese meritve — obdržano za morebitne klice od drugod. */
    fun updateBodyMeasurements(
        shoulderCm: Double,
        waistCm: Double,
        hipCm: Double = 0.0,
        heightCm: Double = 0.0
    ) {
        // Delegiraj na updateInputText — pretvori nazaj v string za konsistentnost
        _inputText.value = BodyMeasurementsInputText(
            shoulder = if (shoulderCm > 0) shoulderCm.toString() else "",
            waist    = if (waistCm    > 0) waistCm.toString()    else "",
            hip      = if (hipCm      > 0) hipCm.toString()      else ""
        )
    }

    // ── Faza 30.9 — Enkratni UI dogodki (Channel = natanko en sprejem) ────────
    /**
     * Channel<BodyUiEvent>: pošiljamo enkratne dogodke (Snackbar, navigacija…).
     * receiveAsFlow() v UI posluša z LaunchedEffect.
     *
     * Zakaj Channel in ne SharedFlow:
     *   SharedFlow (replay=0) bi izgubil dogodek, če UI ni še naročen.
     *   Channel.BUFFERED ohrani sporočilo dokler ga UI ne prebere.
     */
    private val _uiEvent = Channel<BodyUiEvent>(Channel.BUFFERED)
    val uiEvents = _uiEvent.receiveAsFlow()

    /**
     * Faza 30.6 — Shrani meritve v Firestore (batch write):
     *   a) Posodobi profil z aktualnimi vrednostmi
     *   b) Doda vnos v measurements_history (za grafe)
     *
     * Faza 30.8 — isSaving guard: klik na gumb med shranjevanjem se v celoti ignorira.
     * Faza 30.9 — napake se pošljejo kot BodyUiEvent.Error na Channel (→ Snackbar v UI).
     *
     * Klic chain: UI → VM → SaveBodyMeasurementsUseCase → BodyMeasurementsRepository → Firestore
     */
    fun saveBodyMeasurements(
        shoulderCm: Double,
        waistCm: Double,
        hipCm: Double = 0.0,
        heightCm: Double = 0.0
    ) {
        // Dvojni klik → prezri, če shranjevanje že teče
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                // Faza 31.9 — Popravek #2: viewModelScope se prekine ob uničenju zaslona (navigacija nazaj).
                // withContext(NonCancellable) zagotovi, da Firestore transakcija (dejanski vpis) dokonča,
                // tudi če uporabnik pritisne Nazaj med nalaganjem — prepreči izgubo podatkov.
                val result = withContext(NonCancellable) {
                    saveMeasurementsUseCase(shoulderCm, waistCm, hipCm, heightCm)
                }
                result.onSuccess {
                    android.util.Log.d("BodyModuleHomeVM", "✅ Meritve shranjene v zgodovino")
                    // Faza 32.0 — Fix #3 (NonCancellable Channel Exception):
                    // withContext(NonCancellable) je zaključil zapis, ampak viewModelScope je
                    // morda že preklican (uporabnik je odšel z zaslona). _uiEvent.send() je
                    // suspending klic — v preklicani korutini vrže CancellationException in
                    // preskoči finally blok. Preverimo isActive pred vsakim send() klicem.
                    if (currentCoroutineContext().isActive) {
                        _uiEvent.send(BodyUiEvent.SaveSuccess)
                    }
                }
                result.onFailure { e ->
                    android.util.Log.e("BodyModuleHomeVM", "❌ Napaka pri shranjevanju meritev: ${e.message}")
                    if (currentCoroutineContext().isActive) {
                        _uiEvent.send(BodyUiEvent.Error("Shranjevanje ni uspelo. Preverite povezavo."))
                    }
                }
            } finally {
                // Vedno osvobodi, tudi ob nenapovedani izjemi
                _isSaving.value = false
            }
        }
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

    /**
     * Faza 32.0 — Fix #1 (State Stomp): Števec aktivnih async operacij.
     * LoadMetrics ne sme postaviti isLoading=false, dokler SwapDays ali
     * CompleteWorkoutSession še teče. Ko je vrednost > 0, spinner ostane viden.
     */
    private val activeAsyncOperations = MutableStateFlow(0)

    /**
     * Faza 32.0 — Fix #2 (Stale Plan Snapshot): Živi plan, ki ga LoadMetrics
     * collect blok bere dinamično. SwapDays onResult ga posodobi, da todayIsRest
     * izračun nikoli ne temelji na zastarelih podatkih začetne inicializacije.
     */
    private val currentPlanState = MutableStateFlow<PlanResult?>(null)

    fun handleIntent(intent: BodyHomeIntent) {
        when (intent) {
            is BodyHomeIntent.LoadMetrics -> {
                // Cancela morebitni prejšnji LoadMetrics — prepreči dvojno branje
                loadMetricsJob?.cancel()
                // Faza 32.0 — Fix #2: inicializiraj živi plan pred launch-om, da collect
                // blok takoj bere pravilni plan tudi ob prvem klicu.
                currentPlanState.value = intent.plan
                loadMetricsJob = viewModelScope.launch {
                    _ui.update { it.copy(isLoading = true, errorMessage = null) }
                    try {
                        getBodyMetrics.invoke(intent.email).collect { metrics ->
                            if (metrics.isLoading) return@collect
                            // Faza 31.9 — Popravek #4: Cooperative Cancellation Guard.
                            // Stari, pravkar preklicani job (loadMetricsJob?.cancel()) je morda
                            // ravno zaključil mrežni klic in prišel do te točke v milisekundi
                            // POTEM ko novi job že nastavil isLoading = true.
                            // Brez tega guard-a bi stari job prebrisal isLoading = true novega joba.
                            if (!currentCoroutineContext().isActive) return@collect

                            // Faza 32.0 — Fix #2: Beremo iz currentPlanState.value (živi flow),
                            // ne iz intent.plan (statični snapshot zajet ob inicializaciji).
                            // SwapDays onResult posodobi currentPlanState → todayIsRest je vedno
                            // izračunan iz aktualnega plana, ki vsebuje zamenjane dni.
                            val todayIsRest = currentPlanState.value?.weeks
                                ?.flatMap { it.days }
                                ?.firstOrNull { it.dayNumber == metrics.planDay }
                                ?.isRestDay ?: false

                            _ui.update { current ->
                                current.copy(
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
                                    // Faza 32.0/32.2 — Fix #1 (LoadMetrics Premature Overwrite):
                                    // Ne smemo slepo pisati isLoading=false — če SwapDays ali
                                    // CompleteWorkoutSession še teče, activeAsyncOperations > 0
                                    // in spinner mora ostati viden.
                                    isLoading              = activeAsyncOperations.value > 0,
                                    isDataLoaded           = true,
                                    // Faza 32.4 — Fix #2 (LoadMetrics Error Cleanup): Ker akcijske napake
                                    // (SwapDays, CompleteWorkoutSession) zdaj grejo na Channel (ShowSnackbar),
                                    // _ui.errorMessage vsebuje IZKLJUČNO persistentne repo napake iz LoadMetrics.
                                    // Ohranimo current.errorMessage samo med aktivno operacijo, sicer prepišemo.
                                    errorMessage           = if (activeAsyncOperations.value > 0) current.errorMessage else metrics.errorMessage
                                )
                            }
                        }
                    } catch (e: CancellationException) {
                        // Faza 31.8 — Anomalija 1: Preklicani stari job ne sme mutirati stanja novega joba.
                        // Re-throwamo, da isLoading = true (ki ga je postavil novi job) ostane nespremenjen.
                        throw e
                    } catch (e: Exception) {
                        _ui.update { it.copy(errorMessage = e.message, isLoading = false) }
                    }
                }
            }

            is BodyHomeIntent.CompleteRestDay -> {
                if (gamificationUseCase == null) {
                    android.util.Log.w("BodyModuleHomeVM", "gamificationUseCase ni nastavljen — preskočim CompleteRestDay")
                    return
                }
                viewModelScope.launch {
                    // Faza 32.1 — Multi-tap guard: prezri klik, če operacija že teče.
                    if (activeAsyncOperations.value > 0) return@launch
                    // Faza 32.1 — Fix #2: Dodaj tracking kot pri SwapDays.
                    activeAsyncOperations.update { it + 1 }
                    _ui.update { it.copy(isLoading = true) }
                    try {
                        val newStreak = gamificationUseCase.restDayInitiated()
                        // Faza 32.1 — Fix #3: Odstranjen isLoading = false — reaktivni finally prevzame.
                        _ui.update { it.copy(
                            isWorkoutDoneToday = true,
                            streakDays         = newStreak,
                            todayStatus        = UserDayStatus.REST_DAY_DONE
                        ) }
                        _streakUpdatedEvent.tryEmit(StreakUpdateEvent(newStreak = newStreak, isRestDay = true))
                    } catch (e: Exception) {
                        // Faza 32.4 — Fix #1: Prehodna napaka → Channel (Snackbar), ne _ui.errorMessage.
                        // _ui.errorMessage ostane čist za persistentne repo napake iz LoadMetrics.
                        _uiEvent.send(BodyUiEvent.ShowSnackbar(e.localizedMessage ?: "Unknown Error"))
                    } finally {
                        // Faza 32.1 — Fix #3: Reaktivno: isLoading temelji izključno na števcu.
                        activeAsyncOperations.update { it - 1 }
                        _ui.update { it.copy(isLoading = activeAsyncOperations.value > 0) }
                    }
                }
            }

            is BodyHomeIntent.HideCompletionAnimation ->
                _ui.update { it.copy(showCompletionAnimation = false) }

            is BodyHomeIntent.AcceptChallenge  -> { /* lokalni state */ }
            is BodyHomeIntent.CompleteChallenge -> { /* lokalni state */ }

            is BodyHomeIntent.SwapDays -> {
                // Faza 30.4: ViewModel kliče SAMO SwapPlanDaysUseCase — ne PlanDataStore.
                // Klic chain: VM → UseCase (validacija + lokalni model) → Repository → DataStore
                viewModelScope.launch {
                    // Faza 32.1 — Multi-tap guard: prezri klik, če operacija že teče.
                    if (activeAsyncOperations.value > 0) return@launch
                    // Faza 32.0 — Fix #1: Povečaj števec pred operacijo. LoadMetrics collect
                    // bo videl activeAsyncOperations > 0 in ne bo ugasnil spinnerja.
                    activeAsyncOperations.update { it + 1 }
                    try {
                        // Faza 31.8 — Anomalija 3: Snapshot pred pisanjem in pred suspend klicem.
                        val swapSnapshot = _ui.value
                        _ui.update { it.copy(isLoading = true, errorMessage = null) }
                        val lockedDay = if (swapSnapshot.isWorkoutDoneToday) swapSnapshot.planDay else null
                        val res = swapPlanDays.invoke(intent.currentPlan, intent.dayA, intent.dayB, lockedDay)
                        // Faza 32.6 — Proceduralni if/else nadomešča onSuccess/onFailure verige:
                        // send() se pokliče direktno v suspend korutini → atomarni vrstni red
                        // (Snackbar se pošlje PRED finally blokom, ki ugasne spinner).
                        if (res.isSuccess) {
                            // Faza 32.0 — Fix #2: Posodobi živi plan, da LoadMetrics collect blok
                            // pri naslednjem Firestore eventu izračuna todayIsRest iz zamenjanih dni.
                            currentPlanState.value = res.getOrNull()!!
                            intent.onResult(res.getOrNull()!!)
                        } else {
                            val msg = res.exceptionOrNull()?.localizedMessage ?: "Unknown Error"
                            _uiEvent.send(BodyUiEvent.ShowSnackbar(msg))
                        }
                    } catch (e: Exception) {
                        // Faza 32.4 — Fix #1: Nepredvidena runtime izjema → Channel (Snackbar).
                        _uiEvent.send(BodyUiEvent.ShowSnackbar(e.localizedMessage ?: "Unknown Error"))
                    } finally {
                        // Faza 32.1 — Fix #3: Reaktivno: isLoading temelji izključno na števcu.
                        activeAsyncOperations.update { it - 1 }
                        _ui.update { it.copy(isLoading = activeAsyncOperations.value > 0) }
                    }
                }
            }

            is BodyHomeIntent.CompleteWorkoutSession -> {
                viewModelScope.launch {
                    // Faza 32.1 — Multi-tap guard: prezri klik, če operacija že teče.
                    if (activeAsyncOperations.value > 0) return@launch
                    // Faza 32.0 — Fix #1: Povečaj števec pred operacijo.
                    activeAsyncOperations.update { it + 1 }
                    try {
                        // Faza 31.8 — Anomalija 3: Nespremenljiv snapshot PRED vsemi pisanji in suspend klici.
                        val currentStateSnapshot = _ui.value
                        _ui.update { it.copy(isLoading = true, errorMessage = null) }

                        // Faza 31.8 — Anomalija 5: Guard pred Firestore transakcijo.
                        if (!currentStateSnapshot.isDataLoaded) {
                            // Faza 32.4 — Fix #1: Prehodna napaka → Channel (Snackbar).
                            _uiEvent.send(BodyUiEvent.ShowSnackbar("Metrics not yet loaded — please wait."))
                            intent.onCompletion(null)
                            return@launch
                        }

                        val isRestDay     = currentStateSnapshot.todayIsRest
                        val isExtra       = intent.isExtraWorkout
                        val oldPlanDay    = currentStateSnapshot.planDay
                        val oldWeeklyDone = currentStateSnapshot.weeklyDone

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

                        // Faza 32.6 — Proceduralni if/else nadomešča onSuccess/onFailure verige:
                        // send() se pokliče direktno v suspend korutini → atomarni vrstni red
                        // (Snackbar se pošlje PRED finally blokom, ki ugasne spinner).
                        if (result.isSuccess) {
                            val completionResult = result.getOrNull()
                            val todayStatus = when {
                                isRestDay && isExtra -> UserDayStatus.REST_WORKOUT_DONE
                                else                 -> UserDayStatus.WORKOUT_DONE
                            }

                            // Faza 32.7 — Atomarno branje in pisanje stanja znotraj update lambda-e.
                            // _ui.value.streakDays/.isWorkoutDoneToday so bili prej bralni zunaj
                            // update bloka → race condition. Zdaj vse odvisnosti od current stanja
                            // gredo prek `current` parametra lambda-e (atomarni CAS snapshot).
                            // `var newStreak` capture je varen: CAS loop ob retry posodobi vrednost,
                            // končna vrednost se ujema s tistim kar je bilo dejansko zapisano.
                            var newStreak = 0
                            _ui.update { current ->
                                newStreak = completionResult?.newStreakDays?.takeIf { it > 0 }
                                    ?: (current.streakDays + if (todayStatus.contributesToStreak && !current.isWorkoutDoneToday) 1 else 0)
                                // Faza 31.8 — Anomalija 4: Fallback planDay/weeklyDone iz snapshot-a
                                // (sta neodvisna od current stanja → snapshot je dovolj).
                                val newPlanDay = completionResult?.newPlanDay?.takeIf { it > 0 }
                                    ?: (oldPlanDay + if (!isExtra) 1 else 0)
                                val newWeekly = if (todayStatus != UserDayStatus.REST_WORKOUT_DONE)
                                    (oldWeeklyDone + 1).coerceAtMost(currentStateSnapshot.weeklyTarget)
                                else oldWeeklyDone
                                current.copy(
                                    streakDays              = newStreak,
                                    weeklyDone              = newWeekly,
                                    planDay                 = newPlanDay,
                                    isWorkoutDoneToday      = true,
                                    todayStatus             = todayStatus,
                                    showCompletionAnimation = !isExtra
                                )
                            }
                            _streakUpdatedEvent.tryEmit(StreakUpdateEvent(newStreak = newStreak))
                            intent.onCompletion(completionResult)
                        } else {
                            // Faza 32.4/32.6 — Napaka → direktni send() brez nested launch.
                            val msg = result.exceptionOrNull()?.localizedMessage ?: "Unknown Error"
                            _uiEvent.send(BodyUiEvent.ShowSnackbar(msg))
                            intent.onCompletion(null)
                        }
                    } catch (e: Exception) {
                        // Faza 32.4 — Fix #1: Nepredvidena runtime izjema → Channel (Snackbar).
                        _uiEvent.send(BodyUiEvent.ShowSnackbar(e.localizedMessage ?: "Unknown Error"))
                        intent.onCompletion(null)
                    } finally {
                        // Faza 32.1 — Fix #3: Reaktivno: isLoading temelji izključno na števcu.
                        activeAsyncOperations.update { it - 1 }
                        _ui.update { it.copy(isLoading = activeAsyncOperations.value > 0) }
                    }
                }
            }
        }
    }
}
