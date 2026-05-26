package com.example.myapplication

import androidx.lifecycle.SavedStateHandle
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.auth.AuthStateRepository
import com.example.myapplication.domain.gamification.GamificationRepository
import com.example.myapplication.domain.gamification.GamificationState
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.model.BodyMeasurementEntry
import com.example.myapplication.domain.model.DomainException
import com.example.myapplication.domain.model.UserDayStatus
import com.example.myapplication.domain.profile.UserProfileRepository
import com.example.myapplication.domain.repository.BodyMeasurementsRepository
import com.example.myapplication.domain.repository.PlanRepository
import com.example.myapplication.domain.repository.WorkoutStats
import com.example.myapplication.domain.repository.WorkoutStatsRepository
import com.example.myapplication.domain.usecase.GetBodyMetricsUseCase
import com.example.myapplication.domain.usecase.SwapPlanDaysUseCase
import com.example.myapplication.domain.usecase.UpdateBodyMetricsUseCase
import com.example.myapplication.viewmodels.BodyHomeIntent
import com.example.myapplication.viewmodels.BodyModuleHomeViewModel
import com.example.myapplication.viewmodels.BodyUiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regresijski unit testi za BodyModuleHomeViewModel — Faza 35 / Faza 38.
 *
 * Zagotavljajo, da DomainException.AuthenticationExpired pravilno:
 *   1. Sproži BodyUiEvent.AuthExpired na uiEvents Channel
 *   2. Nastavi BodyUiState.isAuthExpired = true
 *   3. Nastavi isLoading = false (UI ne ostane zamrznjen)
 *
 * Faza 38 — Poenotena Result API pogodba — klic chain, ki ga testi pokrivajo:
 *   FakeWorkoutStatsRepository.observeWorkoutStats() vrže DomainException.AuthenticationExpired
 *     → GetBodyMetricsUseCase catch(Exception) prevede v Result.failure(DomainException.AuthenticationExpired)
 *     → GetBodyMetricsUseCase emitira Result.failure prek channelFlow.send()
 *     → BodyModuleHomeViewModel collect { result -> result.isFailure }
 *     → when (exception) { is DomainException.AuthenticationExpired → ... }
 *     → _ui.update { isAuthExpired=true, isLoading=false }
 *     → _uiEvent.send(BodyUiEvent.AuthExpired)
 *
 * Arhitekturna meja je POPOLNOMA ČISTA (Faza 37/38):
 * FakeWorkoutStatsRepository vrže DomainException (ne Firebase tipov),
 * UseCase ga emitira kot Result.failure, ViewModel ga procesira prek Result.isFailure check-a.
 * Nobeden sloj nad data/ ne vsebuje Firebase importov. Presenta­cijski sloj nima catch blokov
 * za domenske napake na stream nivoju — vse napake so vrednosti (tipsko-varne).
 *
 * Testna strategija:
 *   - Brez mocking frameworka (Mockito/MockK) — ročni fake razredi (KMP-friendly, hitrejši)
 *   - UnconfinedTestDispatcher: korutine tečejo sinhronizirano na istem niti → brez časovnih oken
 *   - Dispatchers.setMain/resetMain: override viewModelScope dispatcher
 *   - launch { uiEvents.collect } pred intent dispatch: Channel eventi so dostopni takoj
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BodyModuleHomeViewModelTest {

    // ── TestDispatcher nastavitev ──────────────────────────────────────────────

    /**
     * UnconfinedTestDispatcher: ob vsaki korutinskem launch-u takoj teče sinhronizirano.
     * viewModelScope.launch { ... } se izvede do konca, preden handleIntent() vrne rezultat.
     * Idealen za teste brez naprednega terminiranja (advanceUntilIdle je zadostuje za
     * Channel flush).
     */
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // Nadomestitvi Dispatchers.Main z testnim dispatcherjem.
        // ViewModel-ov viewModelScope interno uporablja Dispatchers.Main — brez tega override-a
        // bi viewModelScope.launch { ... } vrnil napako "Module with the Main dispatcher had failed".
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Fake implementacije odvisnosti ────────────────────────────────────────

    /**
     * Lažni WorkoutStatsRepository ki simulira dve stanji:
     *   [throwPermissionDenied = false] → posreduje empty flow (normalno delovanje)
     *   [throwPermissionDenied = true]  → vrže DomainException.AuthenticationExpired
     *
     * Faza 37 — Clean Architecture: Fake zdaj simulira NOVO obnašanje data sloja —
     * UserWorkoutStatsRepository prevede FirebaseFirestoreException(PERMISSION_DENIED) v
     * DomainException.AuthenticationExpired ZNOTRAJ callbackFlow-a.
     * Testi so posledično popolnoma Firebase-free: arhitekturna meja je čista.
     */
    private class FakeWorkoutStatsRepository(
        private val throwPermissionDenied: Boolean = false
    ) : WorkoutStatsRepository {

        override fun observeWorkoutStats(email: String): Flow<WorkoutStats?> = flow {
            if (throwPermissionDenied) {
                // Simuliramo novo obnašanje data sloja: repository že prevede Firebase
                // PERMISSION_DENIED v platformsko-nevtralni DomainException.
                throw DomainException.AuthenticationExpired
            }
            // Normalen primer: null = dokument ne obstaja (nova registracija)
            emit(null)
        }

        override suspend fun getWorkoutStats(email: String): WorkoutStats? = null
        override suspend fun isWorkoutDoneToday(): Boolean = false
        override suspend fun getPlanDay(): Int = 1
        override suspend fun getDailyCalories(): Int = 2000
    }

    /**
     * Lažni GamificationRepository — minimalna implementacija za izolacijo testov.
     * Vse metode so no-op ali vračajo varne privzete vrednosti.
     */
    private class FakeGamificationRepository : GamificationRepository {
        override suspend fun awardXP(amount: Int, reason: String) {}
        override suspend fun getCurrentStreak(): Int = 0
        override suspend fun markRestDayPending() {}
        override suspend fun runMidnightStreakCheck() {}
        override suspend fun consumeStreakFreeze(): Boolean = false
        override suspend fun getTodayStatus(): UserDayStatus = UserDayStatus.WORKOUT_PENDING
        override suspend fun logBurnedCalories(todayStr: String, calories: Double) {}
        override suspend fun getGamificationState(): GamificationState = GamificationState()
        override suspend fun moveToNextDay(
            newStatus: UserDayStatus,
            xpToBeAwarded: Int,
            xpReason: String,
            caloriesBurned: Double,
            incrementPlanDay: Boolean,
            workoutSessionDoc: Map<String, Any>?
        ): Int = 1
    }

    /**
     * Lažni AuthStateRepository — emitira testni email takoj ob naročnini.
     * Simulira prijavljenega uporabnika brez Firebase Auth klica.
     */
    private class FakeAuthStateRepository(
        private val email: String? = "test@example.com"
    ) : AuthStateRepository {
        override fun observeCurrentUserEmail(): Flow<String?> = flowOf(email)
    }

    /**
     * Lažni UserProfileRepository — ni profilov med LoadMetrics testom.
     * Golden Ratio sekcija ne bo emitirala — to je pričakovano za ta test.
     */
    private class FakeUserProfileRepository : UserProfileRepository {
        override fun observeUserProfile(email: String): Flow<UserProfile> = flow {
            // Nikoli ne emitira — izoliramo LoadMetrics, ne Golden Ratio logike
        }
    }

    /**
     * Lažni PlanRepository — SwapPlanDaysUseCase potrebuje ga,
     * a v LoadMetrics poti ni klican.
     */
    private class FakePlanRepository : PlanRepository {
        override suspend fun swapDays(planId: String, dayA: Int, dayB: Int): Result<Unit> =
            Result.success(Unit)
    }

    /**
     * Lažni BodyMeasurementsRepository — SaveBodyMeasurementsUseCase potrebuje ga,
     * a ni del LoadMetrics-a.
     */
    private class FakeBodyMeasurementsRepository : BodyMeasurementsRepository {
        override suspend fun saveMeasurements(
            dateId: String,
            shoulderCm: Double,
            waistCm: Double,
            hipCm: Double,
            heightCm: Double
        ): Result<Unit> = Result.success(Unit)

        override fun observeMeasurementsHistory(): Flow<List<BodyMeasurementEntry>> =
            flowOf(emptyList())
    }

    // ── Pomočnik za gradnjo ViewModel-a ──────────────────────────────────────

    /**
     * Zgradi testni ViewModel z izmenljivimi ključnimi odvisnostmi.
     *
     * @param statsRepo      WorkoutStatsRepository (privzeto: ni napake)
     * @param authRepo       AuthStateRepository (privzeto: test@example.com)
     */
    private fun buildViewModel(
        statsRepo: WorkoutStatsRepository = FakeWorkoutStatsRepository(),
        authRepo: AuthStateRepository = FakeAuthStateRepository()
    ): BodyModuleHomeViewModel {
        val gamificationRepo = FakeGamificationRepository()
        val gamificationUseCase = ManageGamificationUseCase(gamificationRepo)
        return BodyModuleHomeViewModel(
            getBodyMetrics             = GetBodyMetricsUseCase(statsRepo),
            updateBodyMetrics          = UpdateBodyMetricsUseCase(gamificationUseCase),
            swapPlanDays               = SwapPlanDaysUseCase(FakePlanRepository()),
            gamificationUseCase        = gamificationUseCase,
            userProfileRepository      = FakeUserProfileRepository(),
            authStateRepository        = authRepo,
            bodyMeasurementsRepository = FakeBodyMeasurementsRepository(),
            savedStateHandle           = SavedStateHandle()
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REGRESIJSKI TESTI — Faza 35: PERMISSION_DENIED → AuthExpired handling
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Primarni regresijski test.
     *
     * Scenarij: Auth token je potekel → Firestore vrne PERMISSION_DENIED →
     * ViewModel mora nastaviti isAuthExpired=true, isLoading=false in emitirati AuthExpired.
     *
     * Faza 38 — klic chain (Result API):
     *   handleIntent(LoadMetrics) →
     *   getBodyMetrics.invoke(email).collect { result -> } →
     *   FakeWorkoutStatsRepository.observeWorkoutStats() vrže DomainException.AuthenticationExpired →
     *   GetBodyMetricsUseCase catch(Exception) → emitira Result.failure(DomainException.AuthenticationExpired) →
     *   ViewModel: result.isFailure → when(exception) is AuthenticationExpired →
     *   _ui.update { isAuthExpired=true, isLoading=false } + _uiEvent.send(AuthExpired)
     */
    @Test
    fun `LoadMetrics - PERMISSION_DENIED nastavi isAuthExpired na true`() = runTest {
        // Arrange: repository, ki ob branju vrže PERMISSION_DENIED
        val vm = buildViewModel(
            statsRepo = FakeWorkoutStatsRepository(throwPermissionDenied = true)
        )

        // Zberemo UI evente PRED dispatchom intenta.
        // Channel.BUFFERED drži sporočila v čakalni vrsti, a collect mora biti aktiven
        // preden Channel zapremo (receiveAsFlow ne shranjuje že sprejetih).
        val receivedEvents = mutableListOf<BodyUiEvent>()
        val collectJob = launch(testDispatcher) {
            vm.uiEvents.collect { event -> receivedEvents.add(event) }
        }

        // Act: sproži LoadMetrics intent
        vm.handleIntent(BodyHomeIntent.LoadMetrics())

        // advanceUntilIdle: zagotovi, da so vse čakajoče korutine (vključno z viewModelScope.launch)
        // v celoti zaključene preden preverimo stanje.
        advanceUntilIdle()

        // Assert 1: isAuthExpired mora biti true — UI ve, da seja ni veljavna
        assertTrue(
            "BodyUiState.isAuthExpired mora biti true po PERMISSION_DENIED napaki." +
            "\nDejansko: ${vm.ui.value.isAuthExpired}" +
            "\nPoln stanje: ${vm.ui.value}",
            vm.ui.value.isAuthExpired
        )

        // Assert 2: isLoading mora biti false — UI ne sme ostati zamrznjen v loading stanju
        assertFalse(
            "BodyUiState.isLoading mora biti false po napaki (ne sme ostati zamrznjen)." +
            "\nDejansko: ${vm.ui.value.isLoading}",
            vm.ui.value.isLoading
        )

        // Assert 3: AuthExpired event mora biti emitiran — UI ga prikaže kot Snackbar + onBack()
        assertTrue(
            "uiEvents mora vsebovati BodyUiEvent.AuthExpired." +
            "\nDejansko prejeti eventi: $receivedEvents",
            receivedEvents.any { it is BodyUiEvent.AuthExpired }
        )

        collectJob.cancel()
    }

    /**
     * Negativni test: normalen tok (brez izjeme) NE sproži AuthExpired.
     *
     * Scenarij: observeWorkoutStats() emitira null (prazni podatki — nova registracija).
     * isAuthExpired mora ostati false.
     */
    @Test
    fun `LoadMetrics - brez napake isAuthExpired ostane false`() = runTest {
        // Arrange: normalen fake brez napak
        val vm = buildViewModel(
            statsRepo = FakeWorkoutStatsRepository(throwPermissionDenied = false)
        )

        // Act
        vm.handleIntent(BodyHomeIntent.LoadMetrics())
        advanceUntilIdle()

        // Assert: isAuthExpired mora ostati false — normalen tok ne aktivira auth expiry poti
        assertFalse(
            "isAuthExpired mora ostati false kadar ni PERMISSION_DENIED napake." +
            "\nDejansko: ${vm.ui.value.isAuthExpired}",
            vm.ui.value.isAuthExpired
        )
    }

    /**
     * Test: neprijavljen uporabnik (email = null) nastavi errorMessage, ne isAuthExpired.
     *
     * isAuthExpired je specifičen za PERMISSION_DENIED iz Firestore, ne za splošno
     * neprijavljenost. Ločujemo med "nikoli prijavljen" in "seja je potekla".
     */
    @Test
    fun `LoadMetrics - neprijavljen uporabnik nastavi errorMessage ne isAuthExpired`() = runTest {
        // Arrange: ni prijavljenega uporabnika
        val vm = buildViewModel(
            authRepo = FakeAuthStateRepository(email = null)
        )

        // Act
        vm.handleIntent(BodyHomeIntent.LoadMetrics())
        advanceUntilIdle()

        // Assert: isAuthExpired je false — to ni auth expiry, ampak splošna odjava
        assertFalse(
            "isAuthExpired mora biti false za neprijavljenega uporabnika (to ni token expiry).",
            vm.ui.value.isAuthExpired
        )

        // Assert: errorMessage mora biti nastavljen z ustreznim sporočilom
        assertTrue(
            "errorMessage mora biti nastavljen za neprijavljenega uporabnika." +
            "\nDejansko errorMessage: '${vm.ui.value.errorMessage}'",
            vm.ui.value.errorMessage != null
        )
    }

    /**
     * Test: začetno stanje ViewModel-a je pričakovano.
     * Robni primer: isAuthExpired je false po privzetku.
     */
    @Test
    fun `privzeto stanje ViewModel-a ima isAuthExpired false`() {
        // Arrange & Act: zgradimo ViewModel brez kakršnih koli dejanj
        val vm = buildViewModel()

        // Assert: privzeto stanje nima aktivnega auth expiry
        assertFalse(
            "BodyUiState.isAuthExpired mora biti false v privzetem stanju.",
            vm.ui.value.isAuthExpired
        )
        assertFalse(
            "BodyUiState.isLoading mora biti false v privzetem stanju.",
            vm.ui.value.isLoading
        )
    }
}

