package com.example.myapplication.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.usecase.CalculateDailyCalorieTargetUseCase
import com.example.myapplication.ui.screens.MealType
import com.example.myapplication.ui.screens.TrackedFood
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import android.content.Context
import android.util.Log
import com.example.myapplication.data.NutritionPlan
import com.example.myapplication.data.UserProfile
import com.example.myapplication.data.daily.DailyLogRepository
import com.example.myapplication.data.repository.FoodRepositoryImpl
import com.example.myapplication.data.settings.UserProfileManager
import com.example.myapplication.data.store.NutritionPlanStore
import com.example.myapplication.debug.NutritionDebugStore
import com.example.myapplication.debug.WeightPredictorStore
import com.example.myapplication.domain.usecase.SyncHealthConnectUseCase
import com.example.myapplication.data.store.FirestoreHelper
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import kotlin.collections.get
import kotlin.math.roundToInt

data class DailyTotals(
    val consumed: Int = 0,
    val burned: Int = 0,
    val water: Int = 0
)

/**
 * Faza 14 — Zgodovinski Snapshoti: Zamrznjeni kalorični in makro cilji za posamezen dan.
 * Berejo se iz dailyLogs/{date}.targetCalories/targetProtein/targetCarbs/targetFat.
 * Vrednosti so null, dokler dokument ni inicializiran ALI za stare dneve brez snapshota.
 */
data class FrozenDayTargets(
    val calories: Int,
    val protein: Int?,
    val carbs: Int?,
    val fat: Int?
)

/**
 * Faza 29.6 — Atomarni kalorični in makro cilji.
 * En sam StateFlow namesto štirih ločenih combine blokov — brez UI tearing, brez !! operatorja.
 */
data class NutritionTargets(
    val calories: Int = 2000,
    val protein: Int = 100,
    val carbs: Int = 200,
    val fat: Int = 60
)

/**
 * Seštevki makrohranil za vse sledene živilske vnose tega dne.
 * Izračunano enkrat v ViewModel-u iz [_firestoreFoods], nato izpostavljeno
 * kot reaktiven [StateFlow] — UI ne sme klicat .sumOf{} v telesu Composable funkcije.
 */
data class MacroTotals(
    val protein     : Double = 0.0,
    val carbs       : Double = 0.0,
    val fat         : Double = 0.0,
    val fiber       : Double = 0.0,
    val sugar       : Double = 0.0,
    val sodium      : Double = 0.0,
    val potassium   : Double = 0.0,
    val cholesterol : Double = 0.0,
    val satFat      : Double = 0.0
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NutritionViewModel(
    private val gamificationUseCase: ManageGamificationUseCase
) : ViewModel() {

    /** Faza 9 — SSOT za kalorični izračun */
    private val calorieTargetUseCase = CalculateDailyCalorieTargetUseCase()

    // ── uidFlow — SSOT za trenutnega uporabnika (mora biti PRVA deklaracija) ───
    // clearUser() nastavi na null → vse flatMapLatest niti se samodejno prekinejo.
    private val uidFlow = MutableStateFlow<String?>(FirestoreHelper.getCurrentUserDocId())

    // ── Faza 29.2: Reaktivni profil in plan — ViewModel jih naloži sam, brez LaunchedEffect ──────

    /**
     * UserProfile — naložen reaktivno iz Firestorea prek Firestore snapshot listener.
     * Reagira na uidFlow: ko clearUser() nastavi uid=null, se listener avtomatično prekine.
     * NutritionScreen ne posreduje profila prek LaunchedEffect z logiko — VM je SSOT.
     */
    private val _internalProfile: StateFlow<UserProfile?> = uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf<UserProfile?>(null)
        else callbackFlow {
            val email = Firebase.auth.currentUser?.email ?: ""
            val docRef = FirestoreHelper.getUserRef(uid)
            val listener = docRef.addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                trySend(UserProfileManager.documentToUserProfile(snap, email))
            }
            awaitClose { listener.remove() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * NutritionPlan — reaktiven real-time tok iz Firestorea (Faza 29.3 — P5 popravek).
     *
     * PRED: enkraten suspend NutritionPlanStore.loadNutritionPlan() v flow { emit(loading); emit(loaded) }
     * PO:   NutritionPlanStore.observeNutritionPlan() z addSnapshotListener → real-time spremembe
     *
     * stateIn initialValue = Pair(null, false) → loadComplete=false med nalaganjem.
     * Po prvi Firestore emisiji → Pair(plan?, true) → loadComplete=true v nutritionPlanLoadComplete.
     */
    private val _nutritionPlanPair: StateFlow<Pair<NutritionPlan?, Boolean>> =
        uidFlow.flatMapLatest { uid ->
            if (uid == null) flowOf<Pair<NutritionPlan?, Boolean>>(Pair(null, false))
            else NutritionPlanStore.observeNutritionPlan(uid)
                .map { plan -> Pair(plan, true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(null, false))

    /** Izpostavljen NutritionPlan za UI (targets, frozen snapshots) */
    val nutritionPlan: StateFlow<NutritionPlan?> = _nutritionPlanPair
        .map { it.first }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** true ko je NutritionPlanStore.loadNutritionPlan() zaključil (plan = null ali dejanski plan) */
    val nutritionPlanLoadComplete: StateFlow<Boolean> = _nutritionPlanPair
        .map { it.second }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)


    private val _healthConnectSyncTrigger = MutableStateFlow(0)
    val healthConnectSyncTrigger: StateFlow<Int> = _healthConnectSyncTrigger.asStateFlow()

    private val _uiState = MutableStateFlow(DailyTotals())
    val uiState: StateFlow<DailyTotals> = _uiState.asStateFlow()

    // ── Faza 14: Zamrznjeni cilji tega dne (Zgodovinski Snapshoti) ────────────
    /** Kalorični in makro cilji, kot so bili ob prvem zapisu tega dne v Firestore.
     *  null = dokument ni inicializiran ali je stari dan brez snapshota (UI bo uporabil fallback). */
    private val _frozenTargets = MutableStateFlow<FrozenDayTargets?>(null)
    val frozenTargets: StateFlow<FrozenDayTargets?> = _frozenTargets.asStateFlow()

    // ── Faza 14b: Aktivni datum — reaktiven ob polnočnem prehodu ──────────────
    /**
     * Aktivni datum za Firestore subscription. Ko se dan zamenja, se `_activeDateFlow` posodobi
     * prek [onDayTransition] → `observeDailyTotals()` collectLatest samodejno prekine stari
     * Firestore listener in začne novega za novi datum. Brez ponovnega zagona aplikacije.
     */
    private val _activeDateFlow = MutableStateFlow(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    )
    /** Izpostavljen aktivni datum za polnočni nadzor v NutritionScreen. */
    val currentDate: StateFlow<String> = _activeDateFlow.asStateFlow()

    // ── Faza 29.7 — Water Sync Glitch Fix ────────────────────────────────────
    /**
     * Lokalna optimistična vrednost za vodo.
     * Nastavi se takoj ob kliku (brez omrežnega zakasnitve), počisti ko vsi zapisi zaključijo.
     */
    private val _localWaterMl = MutableStateFlow<Int?>(null)

    /**
     * Števec aktivnih Firestore pisanj za vodo.
     * > 0 → UI prikazuje lokalno vrednost (blokiramo Firestore override).
     * = 0 → UI prikazuje server vrednost (vse pisanje je zaključeno).
     *
     * Inkrement PRED cancelom prejšnjega joba → preprečuje ničelni blisk med jobi.
     */
    private val pendingWaterWrites = MutableStateFlow(0)

    private var waterSyncJob: Job? = null

    /**
     * Enotni reaktivni vir resnice za prikaz vode v UI-ju.
     *
     * Faza 29.7 — logika:
     *   pendingWaterWrites > 0 → lokalDa vrednost (pisanje v teku, blokira Firestore override)
     *   pendingWaterWrites = 0 → server vrednost (vse pisanje zaključeno, UI se sinhronizira)
     *
     * PRED: `effectiveWaterMl = localWaterMl ?: uiState.water` v UI (NutritionScreen)
     *       → Firestore listener je po uspešnem zapisu takoj povozil lokalno stanje → flip-flop
     * PO:   en sam StateFlow v ViewModelu z atomarno logiko → UI nikoli ne utripa
     */
    val waterDisplayMl: StateFlow<Int> = combine(
        _localWaterMl,
        _uiState,
        pendingWaterWrites
    ) { localMl, totals, pending ->
        if (pending > 0) localMl ?: totals.water
        else totals.water
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * Optimistično posodobi vodo:
     * 1. Takoj nastavi lokalno vrednost (instantni UI odziv).
     * 2. Inkrement pendingWaterWrites PRED cancelom → ni ničelnega bliska med jobi.
     * 3. Cancel prejšnjega joba (debounce 800ms za batching hitrih klikov).
     * 4. try-finally zagotavlja decrement tudi ob omrežni napaki ali cancellationu.
     */
    fun updateWaterOptimistic(newValue: Int, todayId: String) {
        val safe = newValue.coerceAtLeast(0)
        _localWaterMl.value = safe
        pendingWaterWrites.value += 1      // ← inkrement PRED cancelom
        waterSyncJob?.cancel()             // ← cancel prejšnjega (njegov finally dekrementira)
        waterSyncJob = viewModelScope.launch {
            try {
                delay(800L)                // debounce: batching hitrih klikov
                FoodRepositoryImpl.logWater(safe, todayId)
                Log.d("NutritionVM", "✅ Water synced: ${safe}ml (pending=${pendingWaterWrites.value})")
            } catch (e: Exception) {
                Log.e("NutritionVM", "❌ Water sync failed: ${e.message}")
            } finally {
                pendingWaterWrites.value = (pendingWaterWrites.value - 1).coerceAtLeast(0)
                if (pendingWaterWrites.value == 0) {
                    _localWaterMl.value = null  // odpri pot Firestore vrednosti
                }
            }
        }
    }

    // ── Loading stanje za food operacije (Faza 13.1) ──────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Navigating stanje za meal sheet (Faza 16.1) ───────────────────────────
    /** Takoj ob kliku na + postavi na true → UI prikaže overlay za takojšen odziv. */
    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    fun setNavigating(value: Boolean) {
        _isNavigating.value = value
    }

    /**
     * Shrani hrano v Firestore asinhronično.
     * Nastavi [isLoading] = true med trajanjem operacije, da UI lahko onemogoči gumbe.
     */
    /**
     * Faza 13b — Briši sledeni vnos hrane.
     * Kliče atomarno transakcijo v repozitoriju: odstrani iz items + odšteje kalorije.
     *
     * @param foodId       UUID vnosa (TrackedFood.id)
     * @param todayId      Datum v obliki "YYYY-MM-DD"
     * @param caloriesKcal Kalorije tega vnosa za odštevanje iz consumedCalories
     * @param onDone       Callback po končani operaciji (uspeh ali neuspeh)
     */
    fun removeFoodItemAsync(
        foodId: String,
        todayId: String,
        caloriesKcal: Double,
        onDone: () -> Unit = {}
    ) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                FoodRepositoryImpl
                    .removeFoodItem(foodId, todayId, caloriesKcal)
                Log.d("NutritionVM", "✅ Food removed: id=$foodId (−${caloriesKcal.toInt()} kcal)")
            } catch (e: Exception) {
                Log.e("NutritionVM", "❌ removeFoodItem failed: ${e.message}", e)
            } finally {
                _isLoading.value = false
                onDone()
            }
        }
    }

    fun logFoodAsync(foodMap: Map<String, Any>, todayId: String, onDone: () -> Unit = {}) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                FoodRepositoryImpl.logFood(foodMap, todayId)
                Log.d("NutritionVM", "✅ Food logged: ${foodMap["name"]}")
            } catch (e: Exception) {
                Log.e("NutritionVM", "Failed to log food", e)
            } finally {
                _isLoading.value = false
                onDone()
            }
        }
    }

    /**
     * clearUser() — pokliči ob odjavi, da se prekine Firestore listener in počistijo lokalni podatki.
     * Nastavi uidFlow = null → flatMapLatest emitira flowOf(null) → listener se samodejno prekliče.
     */
    fun clearUser() {
        waterSyncJob?.cancel()
        waterSyncJob = null
        pendingWaterWrites.value = 0
        uidFlow.value = null
        _firestoreFoods.value = emptyList()
        _localWaterMl.value = null
        _uiState.value = DailyTotals()
        _baseTdee.value = 0.0
        _goalAdjustment.value = 0
        _frozenTargets.value = null
        // P1 SSOT: počisti hibridni TDEE singleton — prepreči uhajanje podatkov med računi
        WeightPredictorStore.reset()
        Log.d("NutritionVM", "clearUser() — Firestore listener prekličen, stanje + WeightPredictorStore počiščeni")
    }

    val customMealsState: StateFlow<QuerySnapshot?> = uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else FoodRepositoryImpl.observeCustomMeals(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Data Budgeting: Skupni Firestore listener za hrano ─────────────────────
    /**
     * Data Budgeting (Faza 6):
     * NutritionScreen je prej imel LASTEN LaunchedEffect > observeDailyLog().collect {},
     * ki je PODVOJIL Firestore listener na istem dokumentu.
     *
     * Zdaj: items parsamo TUKAJ v ViewModel (en sam listener),
     * NutritionScreen bere iz tega StateFlow — brez novega Firestore listenerja.
     * Rezultat: -1 Firestore listener = -50% branj na dailyLogs/{danes}.
     */
    private val _firestoreFoods = MutableStateFlow<List<TrackedFood>>(emptyList())
    internal val firestoreFoods: StateFlow<List<TrackedFood>> = _firestoreFoods.asStateFlow()

    /**
     * Seštevki makrohranil — izračunani enkrat v ViewModel-u, reaktivni na spremembe v [_firestoreFoods].
     * UI bere samo ta StateFlow, brez ponavljajočih .sumOf{} v telesu Composable-a.
     */
    val macroTotals: StateFlow<MacroTotals> = _firestoreFoods.map { foods ->
        MacroTotals(
            protein     = foods.sumOf { it.proteinG        ?: 0.0 },
            carbs       = foods.sumOf { it.carbsG          ?: 0.0 },
            fat         = foods.sumOf { it.fatG            ?: 0.0 },
            fiber       = foods.sumOf { it.fiberG          ?: 0.0 },
            sugar       = foods.sumOf { it.sugarG          ?: 0.0 },
            sodium      = foods.sumOf { it.sodiumMg        ?: 0.0 },
            potassium   = foods.sumOf { it.potassiumMg     ?: 0.0 },
            cholesterol = foods.sumOf { it.cholesterolMg   ?: 0.0 },
            satFat      = foods.sumOf { it.saturatedFatG   ?: 0.0 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MacroTotals())

    // ── Dinamični TDEE ─────────────────────────────────────────────────────────

    /**
     * Sedentarna baza: BMR × 1.2.
     * Nastavimo jo enkrat ob nalaganju plana/profila. Ne vključuje nobenega treninga —
     * to je kalorični minimum za popolno mirovanje + NEAT.
     */
    private val _baseTdee = MutableStateFlow(0.0)

    /**
     * Ciljna prilagoditev glede na cilj:
     *  -500 = hujšanje, 0 = vzdrževanje, +300 = mišična masa
     */
    private val _goalAdjustment = MutableStateFlow(0)

    /**
     * Dinamični dnevni kalorični limit (real-time):
     *   baseTdee  (BMR × 1.2)
     * + burnedCalories  (HC + Workout iz Firestore dailyLogs — posodablja se sproti)
     * + goalAdjustment  (-500 / 0 / +300)
     *
     * Ko gre uporabnik na tek in pokuri 500 kcal → burnedCalories ↑ → dynamicTargetCalories ↑
     * Zjutraj v postelji → samo baseTdee → limit je minimalen.
     */
    val dynamicTargetCalories: StateFlow<Int> = combine(
        _baseTdee,
        _uiState,
        _goalAdjustment
    ) { base, totals, goalAdj ->
        if (base <= 0.0) return@combine 0   // Profil še ni naložen → UI bo uporabil statični fallback
        val dynamic = base + totals.burned.toDouble() + goalAdj
        dynamic.coerceAtLeast(1200.0).roundToInt()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ── Faza 29.6 — SSOT: en atomarni NutritionTargets StateFlow ─────────────
    // Faza 29.5: 4 ločeni combine bloki → overhead + UI tearing tveganje + !! operatorji
    // Faza 29.6: en sam 3-source combine → atomarna dostava vseh 4 ciljev hkrati, 0x !!
    //
    // Prioritetna hierarhija (kalorije):
    //   1. frozenTargets (zamrznjeni Firestore snapshot tega dne)
    //   2. hybridTDEE > 800 (izmerjeni TDEE iz ProgressViewModel)
    //   3. nutritionPlan.calories > 0 (real-time plan iz Firestore)
    //   4. 2000 kcal (varni fallback)
    // Makri: frozen → plan → fallback
    val nutritionTargets: StateFlow<NutritionTargets> = combine(
        _frozenTargets,
        _nutritionPlanPair,
        WeightPredictorStore.hybridTDEEFlow
    ) { frozen, planPair, hybridTDEE ->
        val plan = planPair.first

        // Kalorije — stroga prioriteta + zaokrožitev navzdol na 100
        val rawCalories = when {
            frozen != null             -> frozen.calories
            hybridTDEE > 800           -> hybridTDEE
            (plan?.calories ?: 0) > 0  -> plan?.calories ?: 2000
            else                       -> 2000
        }
        val calories = (rawCalories / 100) * 100

        // Makri — frozen → plan → fallback (brez !! operatorja)
        val protein = when {
            (frozen?.protein ?: 0) > 0  -> frozen?.protein ?: 100
            (plan?.protein   ?: 0) > 0  -> plan?.protein   ?: 100
            else                        -> 100
        }
        val carbs = when {
            (frozen?.carbs ?: 0) > 0  -> frozen?.carbs ?: 200
            (plan?.carbs   ?: 0) > 0  -> plan?.carbs   ?: 200
            else                      -> 200
        }
        val fat = when {
            (frozen?.fat ?: 0) > 0  -> frozen?.fat ?: 60
            (plan?.fat   ?: 0) > 0  -> plan?.fat   ?: 60
            else                    -> 60
        }

        NutritionTargets(calories = calories, protein = protein, carbs = carbs, fat = fat)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        NutritionTargets()
    )

    /**
     * Nastavi BMR in cilj, ko je plan/profil naložen.
     * Kliče se iz NutritionScreen z LaunchedEffect ob spremembi plana ali nutritionPlan.
     *
     * Faza 9 — SSOT: delegira izračun TDEE in ciljne prilagoditve na
     * [CalculateDailyCalorieTargetUseCase.fromBmr] (ne več inline `bmr × 1.2`).
     *
     * @param bmr           Bazalna presnova (kcal/dan) iz AlgorithmData plana
     * @param goal          Cilj ("Lose fat", "Build muscle", "General health" …)
     * @param activityLevel Frekvenca treningov ("2x"–"6x"); null → sedentarni fallback 1.2
     */
    /**
     * @param bodyFatPercentage Opcijsko BF% iz userProfile.bodyFat (parsiran v Double).
     *   Posreduje se v [CalculateDailyCalorieTargetUseCase.fromBmr] za debug beleženje.
     *   V prihodnji verziji: lahko sproži polni invoice() za svež Katch-McArdle BMR.
     */
    fun setUserMetrics(
        bmr: Double,
        goal: String,
        activityLevel: String? = null,
        bodyFatPercentage: Double? = null,
        // P4 — SSOT SmartCalories: opcijski dodatni parametri za poenoteno kalorično formulo
        bmi: Double? = null,
        ageYears: Int? = null,
        isMale: Boolean? = null,
        experience: String? = null,
        limitations: List<String> = emptyList(),
        // Faza 29.4: hybridTDEE iz centralnega combine() — 0 = ProgressScreen še ni bil obiskan
        hybridTDEE: Int = 0
    ) {
        val calorieResult = calorieTargetUseCase.fromBmr(
            bmr               = bmr,
            goal              = goal,
            activityLevel     = activityLevel,
            bodyFatPercentage = bodyFatPercentage,
            bmi               = bmi,
            ageYears          = ageYears,
            isMale            = isMale,
            experience        = experience,
            limitations       = limitations
        )

        // Faza 29.4: hybridTDEE prihaja direktno iz combine() — ni več ločenega collectorja.
        // hybridTDEE > 800 → verodostojna vrednost iz ProgressViewModel.storePrediction()
        // hybridTDEE == 0 → ProgressScreen še ni bil obiskan → fallback na teoretični TDEE
        _baseTdee.value = if (hybridTDEE > 800) hybridTDEE.toDouble() else calorieResult.tdee
        _goalAdjustment.value = calorieResult.goalAdjustment

        // Debug store — za DebugDashboard
        NutritionDebugStore.lastBmr = bmr
        NutritionDebugStore.lastGoal = goal
        NutritionDebugStore.lastGoalAdjustment = _goalAdjustment.value
        Log.d("NutritionVM", "✅ setUserMetrics [29.4]: BMR=${"%.0f".format(bmr)} BF%=${bodyFatPercentage?.let { "%.1f".format(it) } ?: "n/a"} bmi=${bmi?.let { "%.1f".format(it) } ?: "n/a"} tdee=${"%.0f".format(calorieResult.tdee)} hybrid=$hybridTDEE → baseTdee=${"%.0f".format(_baseTdee.value)}, goalAdj=${_goalAdjustment.value}")
    }

    // ── Obstoječa logika ───────────────────────────────────────────────────────

    init {
        observeDailyTotals()
        // Faza 29.4: EN centralni 3-source combine odpravlja race condition.
        // PRED: ločeni coroutini — combine(profil, plan) in hybridTDEEFlow.collect{}
        //       → race condition: hybridTDEE collector je imel pogoj `_baseTdee.value > 0.0`
        //         (ki je bil false dokler combine še ni nastavil baseTdee), kar je pomenilo,
        //         da so zgodnje emisije hybridTDEE tiho izginile.
        // PO:   en sam combine s 3 viri → atomarna dostava vseh treh vrednosti hkrati.
        //       Ko katerikoli vir emitira, se recomputeCalorieTarget() pokliče s svežimi vrednostmi.
        viewModelScope.launch {
            combine(
                _internalProfile,
                _nutritionPlanPair,
                WeightPredictorStore.hybridTDEEFlow
            ) { profile, planPair, hybridTDEE ->
                Triple(profile, planPair, hybridTDEE)
            }.collectLatest { (profile, planPair, hybridTDEE) ->
                val (nutritionPlan, planLoaded) = planPair
                if (profile != null && planLoaded) {
                    recomputeCalorieTarget(profile, nutritionPlan, hybridTDEE)
                }
            }
        }
    }

    /**
     * Faza 29.4 — SSOT za preračun kalorij: EN centralni 3-source combine kliče to funkcijo.
     *
     * @param userProfile  Reaktivni profil iz Firestore snapshot listenerja
     * @param nutritionPlan Real-time plan iz NutritionPlanStore.observeNutritionPlan()
     * @param hybridTDEE   Hibridni TDEE iz WeightPredictorStore.hybridTDEEFlow (0 = ProgressScreen
     *                     še ni bil obiskan → fallback na calorieTargetUseCase.fromBmr())
     */
    private fun recomputeCalorieTarget(
        userProfile: UserProfile,
        nutritionPlan: NutritionPlan?,
        hybridTDEE: Int
    ) {
        val bmr = nutritionPlan?.algorithmData?.bmr?.takeIf { it > 0 }
            ?: return  // Brez BMR-ja ne moremo izračunati — počakaj na nalaganje plana

        val goal = userProfile.workoutGoal.ifBlank { null }
            ?: nutritionPlan.algorithmData?.trainingStrategy
            ?: "General health"

        val bfPercentage = userProfile.bodyFat
            ?.replace("%", "")?.trim()
            ?.split("-", "–")?.firstOrNull()?.trim()
            ?.toDoubleOrNull()

        val bmi = nutritionPlan.algorithmData?.bmi
        val isMale = userProfile.gender?.equals("Male", ignoreCase = true) ?: true

        // Posreduj hybridTDEE v setUserMetrics — ta ga aplicira neposredno na _baseTdee
        // brez ločenega Flow collectorja ali pogoja `_baseTdee.value > 0.0`.
        setUserMetrics(
            bmr               = bmr,
            goal              = goal,
            activityLevel     = userProfile.activityLevel,
            bodyFatPercentage = bfPercentage,
            bmi               = bmi,
            ageYears          = userProfile.age,
            isMale            = isMale,
            experience        = userProfile.experience,
            limitations       = userProfile.limitations,
            hybridTDEE        = hybridTDEE
        )
    }

    private fun observeDailyTotals() {
        viewModelScope.launch {
            uidFlow.collect { uid ->
                if (uid != null) {
                    // Faza 14b: collectLatest na _activeDateFlow — ob spremembi datuma (onDayTransition)
                    // coroutine za stari dan se samodejno prekine, zažene se nov listener za novi dan.
                    _activeDateFlow.collectLatest { todayId ->
                        FoodRepositoryImpl.observeDailyLog(uid, todayId).collect { doc ->
                            Log.d("DEBUG_DATA", "Raw burnedCalories value from DB: ${doc.get("burnedCalories")}")

                            val serverWater    = (doc.get("waterMl")          as? Number)?.toInt() ?: 0
                            val serverBurned   = (doc.get("burnedCalories")   as? Number)?.toInt() ?: 0
                            val serverConsumed = (doc.get("consumedCalories") as? Number)?.toInt() ?: 0

                            updateDailyTotals(
                                consumed = serverConsumed,
                                burned   = serverBurned,
                                water    = serverWater
                            )

                            // Faza 14 — Zgodovinski Snapshoti: preberi zamrznjene cilje tega dne
                            val frozenCal = (doc.get("targetCalories") as? Number)?.toInt()
                            if (frozenCal != null) {
                                _frozenTargets.value = FrozenDayTargets(
                                    calories = frozenCal,
                                    protein  = (doc.get("targetProtein") as? Number)?.toInt(),
                                    carbs    = (doc.get("targetCarbs")   as? Number)?.toInt(),
                                    fat      = (doc.get("targetFat")     as? Number)?.toInt()
                                )
                            }

                            // Debug store — posodobi surove vrednosti za DebugDashboard
                            NutritionDebugStore.lastBurnedCalories = serverBurned
                            NutritionDebugStore.lastConsumedCalories = serverConsumed
                            NutritionDebugStore.lastWaterMl = serverWater

                            // ── Data Budgeting: parse items enkrat tukaj (ne v NutritionScreen) ───
                            val rawItems = doc.get("items") as? List<*>
                            if (rawItems != null) {
                                val foods = parseRawItemsToTrackedFoods(rawItems)
                                if (foods.isNotEmpty()) _firestoreFoods.value = foods
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Parsira Firestore "items" seznam v [TrackedFood] modele.
     * Centralizirana logika — ne podvajamo v Screen.
     */
    internal fun parseRawItemsToTrackedFoods(rawItems: List<*>): List<TrackedFood> =
        rawItems.mapNotNull { any ->
            val m = any as? Map<*, *> ?: return@mapNotNull null
            val name = m["name"] as? String ?: return@mapNotNull null
            val mealStr = m["meal"] as? String ?: "Breakfast"
            val meal = runCatching { MealType.valueOf(mealStr) }.getOrNull() ?: MealType.Breakfast
            val amount = (m["amount"] as? Number)?.toDouble()
                ?: (m["amount"] as? String)?.toDoubleOrNull() ?: 1.0
            val unit = m["unit"] as? String ?: "servings"
            val kcal = (m["caloriesKcal"] as? Number)?.toDouble()
                ?: (m["caloriesKcal"] as? String)?.toDoubleOrNull() ?: 0.0
            val p   = (m["proteinG"]   as? Number)?.toDouble() ?: (m["proteinG"]   as? String)?.toDoubleOrNull()
            val c   = (m["carbsG"]     as? Number)?.toDouble() ?: (m["carbsG"]     as? String)?.toDoubleOrNull()
            val f   = (m["fatG"]       as? Number)?.toDouble() ?: (m["fatG"]       as? String)?.toDoubleOrNull()
            TrackedFood(
                id              = (m["id"] as? String) ?: UUID.randomUUID().toString(),
                name            = name,
                meal            = meal,
                amount          = amount,
                unit            = unit,
                caloriesKcal    = kcal,
                proteinG        = p,
                carbsG          = c,
                fatG            = f,
                fiberG          = (m["fiberG"]        as? Number)?.toDouble() ?: (m["fiberG"]        as? String)?.toDoubleOrNull(),
                sugarG          = (m["sugarG"]        as? Number)?.toDouble() ?: (m["sugarG"]        as? String)?.toDoubleOrNull(),
                saturatedFatG   = (m["saturatedFatG"] as? Number)?.toDouble() ?: (m["saturatedFatG"] as? String)?.toDoubleOrNull(),
                sodiumMg        = (m["sodiumMg"]      as? Number)?.toDouble() ?: (m["sodiumMg"]      as? String)?.toDoubleOrNull(),
                potassiumMg     = (m["potassiumMg"]   as? Number)?.toDouble() ?: (m["potassiumMg"]   as? String)?.toDoubleOrNull(),
                cholesterolMg   = (m["cholesterolMg"] as? Number)?.toDouble() ?: (m["cholesterolMg"] as? String)?.toDoubleOrNull(),
                barcode         = m["barcode"] as? String
            )
        }

    fun syncHealthConnectNow(context: Context) {
        viewModelScope.launch {
            val syncUseCase = SyncHealthConnectUseCase()
            syncUseCase(context)
        }
    }

    /**
     * Faza 14b — Polnočni Prehod: Pokliči ko NutritionScreen zazna spremembo datuma.
     *
     * Resetira vse state starega dne in reaktivira Firestore subscription za novi dan.
     * [_activeDateFlow] emitira novi datum → [observeDailyTotals] collectLatest samodejno
     * prekine stari listener in zažene novega za [newDate]. Brez ponovnega zagona aplikacije.
     *
     * @param newDate Nov datum v obliki "YYYY-MM-DD"
     */
    fun onDayTransition(newDate: String) {
        waterSyncJob?.cancel()
        waterSyncJob = null
        pendingWaterWrites.value = 0        // Počisti čakajoče pisanje — nov dan, nov začetek
        _frozenTargets.value = null         // Počisti zamrznjene cilje starega dne
        _firestoreFoods.value = emptyList() // Počisti jedilnik starega dne
        _uiState.value = DailyTotals()      // Reset kalorije, voda, burned
        _localWaterMl.value = null          // Počisti optimistično vodo
        _activeDateFlow.value = newDate     // ← Sproži collectLatest → nov Firestore listener
        Log.d("NutritionVM", "⏰ onDayTransition → novi dan: $newDate")
    }

    /**
     * Faza 14 — Zgodovinski Snapshoti: Zagotovi, da dailyLogs dokument za današnji dan
     * vsebuje zamrznjene kalorične in makro cilje.
     *
     * Faza 14b — Varovalka pred lažnimi fallbacki: Če je [isPlanLoaded] == false,
     * plan iz Firestore še ni prispel — zamrznitev se odloži (ne bo zamrznila 2000 kcal fallbacka).
     * LaunchedEffect v screenu bo poklican znova, ko se [isPlanLoaded] spremeni v true.
     *
     * @param date             Datum v obliki "YYYY-MM-DD"
     * @param targetCalories   Kalorični cilj tega dne
     * @param targetProtein    Proteinski cilj (g)
     * @param targetCarbs      Ogljikohidratni cilj (g)
     * @param targetFat        Maščobni cilj (g)
     * @param isPlanLoaded     true = NutritionPlanStore.loadNutritionPlan() je zaključil (uspešno ali null)
     */
    fun ensureDayInitialized(
        date: String,
        targetCalories: Int,
        targetProtein: Int,
        targetCarbs: Int,
        targetFat: Int,
        isPlanLoaded: Boolean = false
    ) {
        if (!isPlanLoaded) {
            // Plan še ni naložen — ne zamrzni fallback vrednosti 2000 kcal.
            // LaunchedEffect(rawTargetCalories, ..., nutritionPlanLoadComplete) bo poklican znova,
            // ko nutritionPlanLoadComplete postane true.
            Log.d("NutritionVM", "ensureDayInitialized: preskočeno — plan še ni naložen")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            DailyLogRepository().updateDailyLog(
                date               = date,
                initTargetCalories = targetCalories,
                initTargetProtein  = targetProtein,
                initTargetCarbs    = targetCarbs,
                initTargetFat      = targetFat
            ) { /* brez dodatnih sprememb — samo inicializacija */ }
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
            val db = FirestoreHelper.getDb()
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
