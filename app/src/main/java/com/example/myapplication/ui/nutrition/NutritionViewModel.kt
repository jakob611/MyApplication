package com.example.myapplication.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.domain.usecase.CalculateDailyCalorieTargetUseCase
import com.example.myapplication.domain.usecase.GetNutritionTargetsUseCase
import com.example.myapplication.domain.model.NutritionTargets
import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.domain.nutrition.calculateDailyWaterMl
import com.example.myapplication.domain.nutrition.calculateRestDayCalories
import com.example.myapplication.domain.repository.PlanRepository
import com.example.myapplication.ui.screens.MealType
import com.example.myapplication.ui.screens.SavedCustomMeal
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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import android.content.Context
import android.util.Log
import com.example.myapplication.data.NutritionPlan
import com.example.myapplication.domain.model.UserProfile
import com.example.myapplication.data.daily.DailyLogRepository
import com.example.myapplication.data.repository.NutritionRepository
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
    private val gamificationUseCase: ManageGamificationUseCase,
    // Faza 29.8: DI vmesnik — NE referiramo FoodRepositoryImpl direktno
    private val nutritionRepo: NutritionRepository,
    // Faza 48 — UDF fix: reaktivni vir resnice za workout plan (ne UI posredovanje)
    private val planRepository: PlanRepository
) : ViewModel() {

    /** Faza 9 — SSOT za kalorični izračun */
    private val calorieTargetUseCase = CalculateDailyCalorieTargetUseCase()

    /** Faza 29.8 — Domain Use Case za izračun prehranskih ciljev */
    private val getNutritionTargetsUseCase = GetNutritionTargetsUseCase()

    // ── uidFlow — SSOT za trenutnega uporabnika (mora biti PRVA deklaracija) ───
    // clearUser() nastavi na null → vse flatMapLatest niti se samodejno prekinejo.
    private val uidFlow = MutableStateFlow<String?>(FirestoreHelper.getCurrentUserDocId())

    // ── Faza 48 (UDF fix): Reaktivni tok aktivnega workout plana iz PlanRepository ────────────────
    // PRED: Screen je kličal vm.updatePlanResult(plan) v LaunchedEffect → kršitev UDF.
    // PO:   ViewModel sam naroči na PlanRepository.observePlans() → en vir resnice, brez UI posredovanja.
    // firstOrNull() izbere aktiven plan; ob praznem seznamu ali odjavi emitira null.
    private val _activePlanFlow: StateFlow<PlanResult?> = planRepository.observePlans()
        .map { plans -> plans.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
                nutritionRepo.logWater(safe, todayId)
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
                nutritionRepo
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
                nutritionRepo.logFood(foodMap, todayId)
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
        // Faza 48 — UDF fix: _planResultFlow.value = null ODSTRANJENO.
        // _activePlanFlow se reaktivno počisti ko PlanRepository vrne prazen seznam.
        _xpAwardedDates.clear()           // Faza 47: počisti anti-farming set ob odjavi
        // P1 SSOT: počisti hibridni TDEE singleton — prepreči uhajanje podatkov med računi
        WeightPredictorStore.reset()
        Log.d("NutritionVM", "clearUser() — Firestore listener prekličen, stanje + WeightPredictorStore počiščeni")
    }

    val customMealsState: StateFlow<QuerySnapshot?> = uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else nutritionRepo.observeCustomMeals(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Faza 47 — Custom meals parsirani v ViewModel — Screen ne izvaja več
     * List<Map<String,Any?>> parsiranja iz QuerySnapshot v LaunchedEffect.
     * Reaktivno na customMealsState, izpostavljeno v todayNutritionContext.customMeals.
     */
    val parsedCustomMeals: StateFlow<List<SavedCustomMeal>> = customMealsState.map { snaps ->
        snaps?.documents?.mapNotNull { doc ->
            val name = doc.getString("name") ?: "Custom meal"
            @Suppress("UNCHECKED_CAST")
            val itemsAny = doc.get("items") as? List<Map<String, Any?>>
            val items: List<Map<String, Any>> = itemsAny?.mapNotNull { m ->
                try {
                    mutableMapOf<String, Any>().also { map ->
                        map["id"]   = m["id"]   as? String ?: ""
                        map["name"] = m["name"] as? String ?: ""
                        map["amt"]  = (m["amt"] as? String) ?: (m["amt"]?.toString() ?: "")
                        map["unit"] = m["unit"] as? String ?: ""
                    }
                } catch (_: Exception) { null }
            } ?: emptyList()
            SavedCustomMeal(id = doc.id, name = name, items = items)
        } ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // ── Faza 29.8 — SSOT: nutritionTargets delegira na GetNutritionTargetsUseCase ──
    // Faza 29.6: poslovna logika ZA ZIDOM v combine lambdi (ViewModel sloj)
    // Faza 29.8: poslovna logika PRESELI v Domain sloj (GetNutritionTargetsUseCase)
    //            ViewModel je zdaj samo koordinator — kliče use case in izpostavi rezultat.
    val nutritionTargets: StateFlow<NutritionTargets> = combine(
        _frozenTargets,
        _nutritionPlanPair,
        WeightPredictorStore.hybridTDEEFlow
    ) { frozen, planPair, hybridTDEE ->
        val plan = planPair.first
        // Faza 29.8: vsa logika je v Domain sloju — ViewModel samo posreduje parametre
        getNutritionTargetsUseCase(
            frozenCalories = frozen?.calories,
            frozenProtein  = frozen?.protein,
            frozenCarbs    = frozen?.carbs,
            frozenFat      = frozen?.fat,
            planCalories   = plan?.calories  ?: 0,
            planProtein    = plan?.protein   ?: 0,
            planCarbs      = plan?.carbs     ?: 0,
            planFat        = plan?.fat       ?: 0,
            hybridTDEE     = hybridTDEE
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        NutritionTargets()
    )

    /**
     * Faza 47 — SSOT za dnevni nutrition kontekst.
     * Faza 48 — UDF fix: _planResultFlow (UI-driven) → _activePlanFlow (PlanRepository reaktivni tok).
     *
     * combine() reaktivno poveže:
     *   ① _activePlanFlow  → workout/rest day izračun (epoch-based, DST-safe) — iz PlanRepository
     *   ② _internalProfile  → teža, spol, cilj za vodo in kalorični fallback
     *   ③ nutritionTargets  → kalorični cilj tega dne
     *   ④ parsedCustomMeals → shranjene kombinacije obrokov
     *
     * Screen bere samo .isWorkoutDay, .adjustedWaterTargetMl, .adjustedCalorieTarget, .customMeals.
     * Vse business logike (epoch algebra, calculateDailyWaterMl, calculateRestDayCalories) so tukaj.
     */
    val todayNutritionContext: StateFlow<TodayNutritionContext> = combine(
        _activePlanFlow,
        _internalProfile,
        nutritionTargets,
        parsedCustomMeals
    ) { plan, profile, targets, meals ->

        // ── ① Workout / Rest day (epoch-based) ───────────────────────────────
        val isWorkoutDay = if (plan == null) false else {
            val startDate = try {
                LocalDate.parse(plan.startDate)
            } catch (_: Exception) {
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
            val todayDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val daysSinceStart = (todayDate.toEpochDays() - startDate.toEpochDays())
            plan.weeks.flatMap { it.days }.getOrNull(daysSinceStart.toInt())?.isRestDay?.not() ?: true
        }

        // ── ② Prilagojen cilj za vodo ─────────────────────────────────────────
        val wKg: Double = run {
            val caloriesPerKg = plan?.algorithmData?.caloriesPerKg
            val calories      = plan?.calories
            if (caloriesPerKg != null && caloriesPerKg > 0.0 && calories != null && calories > 0) {
                calories.toDouble() / caloriesPerKg
            } else 70.0
        }
        val isMale   = profile?.gender?.equals("Male", ignoreCase = true) ?: true
        val actLevel = profile?.activityLevel ?: "Sedentary"
        val waterMl  = calculateDailyWaterMl(wKg, isMale, actLevel, isWorkoutDay).toInt()

        // ── ③ Prilagojene kalorije (workout vs rest day) ─────────────────────
        val adjustedCalories = if (isWorkoutDay) {
            targets.calories
        } else {
            val goal = profile?.workoutGoal?.ifBlank { null }
            calculateRestDayCalories(targets.calories.toDouble(), goal, isMale)
        }

        TodayNutritionContext(
            isWorkoutDay          = isWorkoutDay,
            adjustedWaterTargetMl = waterMl,
            adjustedCalorieTarget = adjustedCalories,
            customMeals           = meals
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayNutritionContext())

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
        // Avdit napaka (Točka 1): prejšnja implementacija je uporabljala gnezdeni sekvencialni
        // uidFlow.collect { _activeDateFlow.collectLatest { ... } } — sekvencialen collect ni
        // mogel obdelati null emisije iz uidFlow (clearUser), ker je bil lambda blokiran v
        // neskončnem collectLatest. Posledica: Firestore listener je uhajal po odjavi in se
        // ni zagnal za novega uporabnika po prijavi.
        //
        // Popravek: flatMapLatest(uidFlow) + flatMapLatest(_activeDateFlow) → ista semantika
        // kot _internalProfile in customMealsState. Ko uidFlow emitira null (clearUser()),
        // flatMapLatest TAKOJ prekine Firestore listener prek awaitClose { listener.remove() }.
        viewModelScope.launch {
            combine(uidFlow, _activeDateFlow) { uid, date -> uid to date }
                .flatMapLatest { (uid, date) ->
                    // Faza 14b: ob spremembi uid ali date (onDayTransition), flatMapLatest
                    // samodejno prekine stari listener in zažene novega.
                    // uid == null (clearUser) → flowOf() → listener se prekine brez uhajanja.
                    if (uid == null) flowOf()
                    else nutritionRepo.observeDailyLog(uid, date)
                }
                .collect { doc ->
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

    // ── Faza 47: XP anti-farming — brez Android Context ───────────────────────

    /**
     * Dnevi, za katere smo v tej seji že podelili XP.
     * In-memory set zagotavlja unit-testability; clearUser() ga počisti ob odjavi.
     *
     * Opomba: za cross-session persistent zaščito bo prihodnja verzija preverila
     * Firestore xp_history kolekcijo (datum == todayStr AND reason == NUTRITION_GOAL).
     */
    private val _xpAwardedDates = mutableSetOf<String>()

    /**
     * Enkratni event — Screen zbira z LaunchedEffect za onXPAdded() callback.
     * extraBufferCapacity=1 zagotavlja, da emit ne blokira, če Screen trenutno ni aktiven.
     */
    private val _xpAwardedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val xpAwardedEvent: SharedFlow<Unit> = _xpAwardedEvent.asSharedFlow()

    /**
     * Faza 47 — Preveri kalorični cilj in podeli XP ob dosegu (anti-farming).
     *
     * Logika: porabljene kalorije znotraj ±20% od cilja → XP ni bil podeljen danes
     *   → podeli 100 XP in emitira xpAwardedEvent.
     *
     * Kliče se iz Screen — LaunchedEffect(firestoreFoods, todayId).
     * Ne zahteva Android Context (predhodni SharedPreferences je bil odstranjem).
     */
    fun verifyAndAwardNutritionXP() {
        viewModelScope.launch {
            val todayStr = _activeDateFlow.value
            if (todayStr in _xpAwardedDates) return@launch

            val consumedKcal = _firestoreFoods.value.sumOf { it.caloriesKcal.roundToInt() }
            val dynTarget    = dynamicTargetCalories.value
            val staticTarget = nutritionTargets.value.calories
            val targetCal    = if (dynTarget > 0) dynTarget else staticTarget
            if (targetCal <= 0 || consumedKcal <= 0) return@launch

            val percentageDiff = kotlin.math.abs(targetCal - consumedKcal).toDouble() / targetCal.toDouble()
            if (percentageDiff <= 0.20) {
                val userEmail = Firebase.auth.currentUser?.email ?: return@launch
                Log.d("NutritionVM", "✅ verifyAndAwardNutritionXP: $consumedKcal/$targetCal kcal " +
                    "(${(percentageDiff * 100).toInt()}% diff), email=$userEmail")
                _xpAwardedDates.add(todayStr)
                gamificationUseCase.awardXP(100, "NUTRITION_GOAL")
                _xpAwardedEvent.tryEmit(Unit)
            }
        }
    }

    /** Faza 47: deleteCustomMeal prek NutritionRepository vmesnika (ne direktnega FoodRepositoryImpl). */
    fun deleteCustomMealAsync(mealId: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                nutritionRepo.deleteCustomMeal(mealId)
                Log.d("NutritionVM", "✅ deleteCustomMeal: id=$mealId")
            } catch (e: Exception) {
                Log.e("NutritionVM", "❌ deleteCustomMeal failed: ${e.message}", e)
            } finally {
                onDone()
            }
        }
    }

    /** Faza 47: Overload brez uid — ViewModel določi uid sam iz FirestoreHelper cache-a. */
    suspend fun getCustomMealItems(mealId: String): List<Map<String, Any>>? {
        val uid = FirestoreHelper.getCurrentUserDocId() ?: return null
        return nutritionRepo.getCustomMealItems(uid, mealId)
    }

    /** Faza 29.8: delegira na NutritionRepository — brez direktnih Firestore klicev v VM */
    suspend fun getCustomMealItems(currentUid: String, mealId: String): List<Map<String, Any>>? =
        nutritionRepo.getCustomMealItems(currentUid, mealId)
}
