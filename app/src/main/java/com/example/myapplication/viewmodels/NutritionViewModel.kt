package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import android.content.Context
import android.util.Log
import kotlin.math.roundToInt

data class DailyTotals(
    val consumed: Int = 0,
    val burned: Int = 0,
    val water: Int = 0
)

class NutritionViewModel(
    private val gamificationUseCase: ManageGamificationUseCase
) : ViewModel() {

    private val _healthConnectSyncTrigger = MutableStateFlow(0)
    val healthConnectSyncTrigger: StateFlow<Int> = _healthConnectSyncTrigger.asStateFlow()

    private val _uiState = MutableStateFlow(DailyTotals())
    val uiState: StateFlow<DailyTotals> = _uiState.asStateFlow()

    // ── Optimistična voda (Faza 13.1) ─────────────────────────────────────────
    /** Lokalna override vrednost za vodo — UI se posodobi TAKOJ, Firestore sync je debounced. */
    private val _localWaterMl = MutableStateFlow<Int?>(null)
    val localWaterMl: StateFlow<Int?> = _localWaterMl.asStateFlow()

    private var waterSyncJob: Job? = null

    /** Optimistično posodobi vodo lokalno, nato debounce-aj Firestore zapis (800ms). */
    fun updateWaterOptimistic(newValue: Int, todayId: String) {
        _localWaterMl.value = newValue.coerceAtLeast(0)
        waterSyncJob?.cancel()
        waterSyncJob = viewModelScope.launch {
            delay(800L)
            try {
                com.example.myapplication.data.nutrition.FoodRepositoryImpl.logWater(newValue.coerceAtLeast(0), todayId)
                Log.d("NutritionVM", " Water synced to Firestore: ${newValue}ml")
            } catch (e: Exception) {
                Log.e("NutritionVM", "Failed to sync water to Firestore", e)
                _localWaterMl.value = null // rollback na server vrednost
            }
            _localWaterMl.value = null // počisti override po uspešnem syncu
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
    fun logFoodAsync(foodMap: Map<String, Any>, todayId: String, onDone: () -> Unit = {}) {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                com.example.myapplication.data.nutrition.FoodRepositoryImpl.logFood(foodMap, todayId)
                Log.d("NutritionVM", "✅ Food logged: ${foodMap["name"]}")
            } catch (e: Exception) {
                Log.e("NutritionVM", "Failed to log food", e)
            } finally {
                _isLoading.value = false
                onDone()
            }
        }
    }

    private val uidFlow = MutableStateFlow<String?>(com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId())

    /**
     * clearUser() — pokliči ob odjavi, da se prekine Firestore listener in počistijo lokalni podatki.
     * Nastavi uidFlow = null → flatMapLatest emitira flowOf(null) → listener se samodejno prekliče.
     */
    fun clearUser() {
        waterSyncJob?.cancel()
        waterSyncJob = null
        uidFlow.value = null
        _firestoreFoods.value = emptyList()
        _localWaterMl.value = null
        _uiState.value = DailyTotals()
        Log.d("NutritionVM", "clearUser() — Firestore listener prekličen, stanje počiščeno")
    }

    val customMealsState: StateFlow<com.google.firebase.firestore.QuerySnapshot?> = uidFlow.flatMapLatest { uid ->
        if (uid == null) flowOf(null)
        else com.example.myapplication.data.nutrition.FoodRepositoryImpl.observeCustomMeals(uid)
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

    /**
     * Nastavi BMR in cilj, ko je plan/profil naložen.
     * Kliče se iz NutritionScreen z LaunchedEffect ob spremembi plana ali nutritionPlan.
     *
     * @param bmr     Bazalna presnova (kcal/dan) iz AlgorithmData plana
     * @param goal    Cilj ("Lose fat", "Build muscle", "General health" …)
     */
    fun setUserMetrics(bmr: Double, goal: String) {
        val staticBase = bmr * 1.2  // Mifflin-St Jeor sedentarni TDEE

        // Če je hibridni TDEE že izračunan iz ProgressScreen (WeightPredictorStore),
        // ga uporabimo namesto fiksnega Mifflin množilnika.
        val hybridTDEE = com.example.myapplication.debug.WeightPredictorStore.lastHybridTDEE
        _baseTdee.value = if (hybridTDEE > 800) hybridTDEE.toDouble() else staticBase

        _goalAdjustment.value = when {
            goal.contains("Lose", ignoreCase = true) ||
            goal.contains("Cut",  ignoreCase = true) -> -500
            goal.contains("Build", ignoreCase = true) ||
            goal.contains("Gain",  ignoreCase = true) -> 300
            else -> 0
        }
        // Debug store — za DebugDashboard
        com.example.myapplication.debug.NutritionDebugStore.lastBmr = bmr
        com.example.myapplication.debug.NutritionDebugStore.lastGoal = goal
        com.example.myapplication.debug.NutritionDebugStore.lastGoalAdjustment = _goalAdjustment.value
        Log.d("NutritionVM", "✅ setUserMetrics: BMR=${"%.0f".format(bmr)} hybrid=$hybridTDEE → baseTdee=${"%.0f".format(_baseTdee.value)}, goalAdj=${_goalAdjustment.value}")
    }

    /**
     * Posodobi bazni TDEE iz hibridnega izračuna (adaptive × C + theoretical × (1−C)).
     * Kliče se iz ProgressScreen po vsakem preračunu napovedi.
     * Vrednosti > 800 kcal se sprejmejo kot veljavne.
     */
    fun applyHybridTDEE(hybridTDEE: Int) {
        if (hybridTDEE > 800 && _baseTdee.value > 0.0) {
            _baseTdee.value = hybridTDEE.toDouble()
            Log.d("NutritionVM", " applyHybridTDEE: $hybridTDEE kcal → baseTdee updated")
        }
    }

    // ── Obstoječa logika ───────────────────────────────────────────────────────

    init {
        observeDailyTotals()
    }

    private fun observeDailyTotals() {
        viewModelScope.launch {
            uidFlow.collect { uid ->
                if (uid != null) {
                    val todayId = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                    com.example.myapplication.data.nutrition.FoodRepositoryImpl.observeDailyLog(uid, todayId).collect { doc ->
                        Log.d("DEBUG_DATA", "Raw burnedCalories value from DB: ${doc.get("burnedCalories")}")

                        val serverWater    = (doc.get("waterMl")          as? Number)?.toInt() ?: 0
                        val serverBurned   = (doc.get("burnedCalories")   as? Number)?.toInt() ?: 0
                        val serverConsumed = (doc.get("consumedCalories") as? Number)?.toInt() ?: 0

                        updateDailyTotals(
                            consumed = serverConsumed,
                            burned   = serverBurned,
                            water    = serverWater
                        )
                        // Debug store — posodobi surove vrednosti za DebugDashboard
                        com.example.myapplication.debug.NutritionDebugStore.lastBurnedCalories = serverBurned
                        com.example.myapplication.debug.NutritionDebugStore.lastConsumedCalories = serverConsumed
                        com.example.myapplication.debug.NutritionDebugStore.lastWaterMl = serverWater

                        // ── Data Budgeting: parse items enkrat tukaj (ne v NutritionScreen) ───
                        val rawItems = doc.get("items") as? List<*>
                        if (rawItems != null) {
                            val foods = parseRawItemsToTrackedFoods(rawItems)
                            if (foods.isNotEmpty()) _firestoreFoods.value = foods
                        }
                        // ────────────────────────────────────────────────────────────────────
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
                id              = (m["id"] as? String) ?: java.util.UUID.randomUUID().toString(),
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
            val syncUseCase = com.example.myapplication.domain.workout.SyncHealthConnectUseCase()
            syncUseCase(context)
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
            val db = com.example.myapplication.persistence.FirestoreHelper.getDb()
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
