@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screens

import kotlinx.datetime.toLocalDateTime

// =====================================================================
// NutritionScreen.kt
// Vsebuje: glavni NutritionScreen composable, ActiveCaloriesBar,
//          NutritionDetailRow.
// Modeli/enumi    â†’ NutritionModels.kt
// Custom View     â†’ DonutProgressView.kt
// Dialogi         â†’ NutritionDialogs.kt
// Iskanje hrane   â†’ AddFoodSheet.kt
// =====================================================================

import android.content.Context
import android.util.Log
import com.example.myapplication.domain.model.PlanResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.data.repository.FoodRepositoryImpl
import com.example.myapplication.data.store.FirestoreHelper
import com.example.myapplication.network.OpenFoodFactsProduct
import com.example.myapplication.ui.nutrition.NutritionViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// -----------------------------------------------------------------------
// ActiveCaloriesBar - navpicna progresna vrstica za porabljene kalorije
// -----------------------------------------------------------------------
@Composable
fun ActiveCaloriesBar(
    currentCalories: Int,
    goal: Int = 800
) {
    val progress = (currentCalories.toFloat() / goal).coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.height(240.dp)
    ) {
        Text("", fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(16.dp)
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                        )
                    )
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$currentCalories",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}


@Composable
fun NutritionScreen(
    plan: PlanResult?,
    onScanBarcode: () -> Unit = {},
    scannedProduct: Pair<OpenFoodFactsProduct, String>? = null,
    onProductConsumed: () -> Unit = {},
    openBarcodeScan: Boolean = false,
    openFoodSearch: Boolean = false,
    onXPAdded: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    userProfile: com.example.myapplication.data.UserProfile = com.example.myapplication.data.UserProfile(),
    isOnline: Boolean = true,
    onOpenMenu: () -> Unit = {},
    onProClick: () -> Unit = {},
    onOpenAdditives: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nutritionViewModel: NutritionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.example.myapplication.ui.screens.MyViewModelFactory(context)
    )
    val uiState by nutritionViewModel.uiState.collectAsState()
    // Dinamični TDEE — real-time vrednost iz ViewModel (0 = profil še ni naložen)
    val dynamicTargetCalories by nutritionViewModel.dynamicTargetCalories.collectAsState()
    // Faza 13.1: optimistična voda (instant UI feedback, debounced Firestore sync)
    val localWaterMl by nutritionViewModel.localWaterMl.collectAsState()
    // Faza 13.1: loading stanje za food operacije
    val isLoading by nutritionViewModel.isLoading.collectAsState()
    // Faza 16.1: navigating stanje za takojšen odziv ob kliku na +
    val isNavigating by nutritionViewModel.isNavigating.collectAsState()
    // Faza 29.6: En atomarni NutritionTargets StateFlow — brez UI tearing, brez !! operatorja.
    // UI prejema vse 4 cilje hkrati iz enega combine bloka v ViewModelu.
    val targets by nutritionViewModel.nutritionTargets.collectAsState()
    val targetCalories = targets.calories
    val targetProtein  = targets.protein
    val targetCarbs    = targets.carbs
    val targetFat      = targets.fat

    // Snackbar feedback state
    var showAddedMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showAddedMessage) {
        showAddedMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
            showAddedMessage = null
        }
    }

    // ── Health Connect sinhronizacija — lifecycle-aware (Faza 14) ────────────────
    // ⛔ ODSTRANJENO: while(true) { delay(5000) } polling (uničeval Firestore kvoto)
    // ✅ ZAMENJANO: single sync ob vsaki ON_RESUME — enkrat ob vstopu, ne vsakih 5s
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                nutritionViewModel.syncHealthConnectNow(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Faza 5: Začenemo s praznim listom — Firestore snapshot listener (observeDailyLog)
    // bo naložil podatke asinhrono. Firestore SDK z isPersistenceEnabled=true zagotavlja
    // da so podatki dostopni takoj tudi brez omrežja (offline cache).
    var trackedFoods by remember { mutableStateOf<List<TrackedFood>>(emptyList()) }

    // UI debounce za vode gumbe (preprečuje double-tap)
    val lastWaterClickState = remember { mutableStateOf(0L) }

    // P2 POPRAVEK (Faza 29.1): sumOf izračuni preseljeni v NutritionViewModel.macroTotals StateFlow.
    // UI ne sme klicati .sumOf{} v telesu Composable — povzroča rekompozicijo ob vsakem zunanjev triggeru.
    val macroTotals by nutritionViewModel.macroTotals.collectAsState()
    // Lokalni aliasi — ohranjajo obstoječe reference v UI kodi brez potrebe po preimenovanju
    val consumedProtein     = macroTotals.protein
    val consumedCarbs       = macroTotals.carbs
    val consumedFat         = macroTotals.fat
    val consumedFiber       = macroTotals.fiber
    val consumedSugar       = macroTotals.sugar
    val consumedSodium      = macroTotals.sodium
    val consumedPotassium   = macroTotals.potassium
    val consumedCholesterol = macroTotals.cholesterol
    val consumedSatFat      = macroTotals.satFat

    // Eksplicitna MutableState (ne 'by' delegation) — odpravlja napačno opozorilo IDE
    // "Assigned value is never read" v callback lambdah (Compose recomposition ni vidna statični analizi)
    val showOtherMacros = remember { mutableStateOf(false) }

    // Faza 29.2: nutritionPlan — ViewModel ga naloži sam, UI samo bere.
    val nutritionPlan by nutritionViewModel.nutritionPlan.collectAsState()
    val nutritionPlanLoadComplete by nutritionViewModel.nutritionPlanLoadComplete.collectAsState()

    // Faza 29.3: LaunchedEffect(plan) { vm.updatePlanResult(plan) } ODSTRANJEN.
    // NutritionViewModel samostojno naloži NutritionPlan iz Firestorea prek observeNutritionPlan().
    // UI je popolnoma pasiven — targets prihajajo iz NutritionViewModel.target* StateFlows.

    // Modali/sheets
    var sheetMeal by remember { mutableStateOf<MealType?>(null) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Dialog state kot eksplicitna MutableState — IDE ne more slediti Compose recomposition
    // prek 'by' delegation, zato se vsak .value = X v lambdah napačno označi kot "never read".
    // Z eksplicitnim .value dostopom ta lažna opozorila izginejo brez kakršne koli suppression.
    val showMakeCustom = remember { mutableStateOf(false) }
    val pendingCustomMeal = remember { mutableStateOf<SavedCustomMeal?>(null) }
    val askWhereToAdd = remember { mutableStateOf(false) }
    val chooseMealForCustom = remember { mutableStateOf(MealType.Breakfast) }
    val showFoodDetailDialog = remember { mutableStateOf<TrackedFood?>(null) }

    // Avtomatsko odpri Add Food Sheet, ko je produkt skeniran
    LaunchedEffect(scannedProduct) {
        if (scannedProduct != null) {
            sheetMeal = MealType.Snacks // Default v Snacks, lahko spremenimo
        }
    }

    // Auto-open barcode scan from widget
    LaunchedEffect(openBarcodeScan) {
        if (openBarcodeScan) {
            Log.d("NutritionScreen", "Auto-opening barcode scanner (flag=$openBarcodeScan)")
            kotlinx.coroutines.delay(100) // Small delay for UI to be ready
            onScanBarcode()
        }
    }

    // Auto-open food search from widget
    LaunchedEffect(openFoodSearch) {
        if (openFoodSearch) {
            // Determine current meal type based on time
            val calendar = java.util.Calendar.getInstance()
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val minute = calendar.get(java.util.Calendar.MINUTE)
            val timeInMinutes = hour * 60 + minute

            val mealType = when {
                timeInMinutes in 300..599 -> MealType.Breakfast
                timeInMinutes in 690..899 -> MealType.Lunch
                timeInMinutes in 1080..1199 -> MealType.Dinner
                else -> MealType.Snacks
            }

            Log.d("NutritionScreen", "Auto-opening food search for $mealType (flag=$openFoodSearch)")
            kotlinx.coroutines.delay(100) // Small delay for UI to be ready
            sheetMeal = mealType // This triggers the ModalBottomSheet to open
        }
    }

    // Real-time shranjeni custom meals (ÄŤipi)
    var savedMeals by remember { mutableStateOf<List<SavedCustomMeal>>(emptyList()) }
    val confirmDelete = remember { mutableStateOf<SavedCustomMeal?>(null) }

    // StateFlow collect zamenjuje direktni collect v UI
    val snaps by nutritionViewModel.customMealsState.collectAsState()
    LaunchedEffect(snaps) {
        if (snaps != null) {
            val list = snaps!!.documents.mapNotNull { doc ->
                val name = doc.getString("name") ?: "Custom meal"
                @Suppress("UNCHECKED_CAST")
                val itemsAny = doc.get("items") as? List<Map<String, Any?>>
                val items: List<Map<String, Any>> = itemsAny?.mapNotNull { m ->
                    try {
                        val map = mutableMapOf<String, Any>()
                        map["id"] = m["id"] as? String ?: ""
                        map["name"] = m["name"] as? String ?: ""
                        map["amt"] = (m["amt"] as? String) ?: (m["amt"]?.toString() ?: "")
                        map["unit"] = m["unit"] as? String ?: ""
                        map
                    } catch (_: Exception) {
                        null
                    }
                } ?: emptyList()
                SavedCustomMeal(id = doc.id, name = name, items = items)
            }
            savedMeals = list
        }
    }

    // Faza 14b: todayId NI VEČ remember{} — prihaja iz ViewModel.currentDate StateFlow.
    // Ob polnočnem prehodu ViewModel kliče onDayTransition(newDate) → currentDate se posodobi
    // → todayId se avtomatično posodobi → vsi LaunchedEffecti z todayId kot ključem se re-sprožijo.
    val todayId by nutritionViewModel.currentDate.collectAsState()

    // Faza 14b — Midnight Bug Fix: Preverjamo prehod dneva vsako minuto.
    // Če se datum spremeni (00:00 prehod), pokličemo onDayTransition() → ViewModel resetira
    // Firestore listener, frozenTargets, foods in totals za novi dan — brez ponovnega zagona app.
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000L) // preveri vsako minuto
            val newDate = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            if (newDate != todayId) {
                nutritionViewModel.onDayTransition(newDate)
                // todayId se samodejno posodobi prek currentDate StateFlow
                Log.d("NutritionScreen", "⏰ Midnight transition: $todayId → $newDate")
            }
        }
    }

    // Faza 14 — Zgodovinski Snapshoti: Zagotovi zamrznitev ciljnih vrednosti za današnji dan.
    // Faza 14b — Varovalka: nutritionPlanLoadComplete kot ključ zagotovi re-trigger po nalaganju plana.
    // Faza 29.5 — targetCalories/Protein/Carbs/Fat prihajajo iz ViewModel SSOT (ni ugibanja).
    // DailyLogRepository ignorira initTarget* parametre, če dokument za ta dan že obstaja.
    LaunchedEffect(targetCalories, targetProtein, targetCarbs, targetFat, nutritionPlanLoadComplete) {
        if (targetCalories > 0) {
            nutritionViewModel.ensureDayInitialized(
                date           = todayId,
                targetCalories = targetCalories,
                targetProtein  = targetProtein,
                targetCarbs    = targetCarbs,
                targetFat      = targetFat,
                isPlanLoaded   = nutritionPlanLoadComplete  // ← varovalka pred lažnimi fallbacki
            )
        }
    }

    // ── Data Budgeting: brez novega Firestore listenerja ────────────────────────
    // Items se parsajo enkrat v NutritionViewModel.observeDailyTotals() in delijo
    // prek firestoreFoods StateFlow. NI novega addSnapshotListener() tukaj.
    // Pred tem: 2× Firestore listener na dailyLogs/{danes}, zdaj: 1×.
    val firestoreFoods by nutritionViewModel.firestoreFoods.collectAsState()
    LaunchedEffect(firestoreFoods) {
        if (firestoreFoods.isNotEmpty()) {
            trackedFoods = firestoreFoods
        }
    }

    // Faza 5: Lokalni JSON cache odstranjen — Firestore je Single Source of Truth.
    // Ta LaunchedEffect zdaj skrbi SAMO za XP nagrado ob doseganju kaloričnega cilja.
    LaunchedEffect(trackedFoods, todayId) {
        val consumedKcal = trackedFoods.sumOf { it.caloriesKcal.roundToInt() }

        // XP nagrada za dosežen kalorični cilj (anti-farming: enkrat na dan)
        // Uporablja efectiveTargetCalories = dynamicTarget (real-time) ali statični fallback
        val targetCal = if (dynamicTargetCalories > 0) dynamicTargetCalories else targetCalories
        val consumedCal = consumedKcal
        val difference = kotlin.math.abs(targetCal - consumedCal)
        val percentageDiff = if (targetCal > 0) (difference.toDouble() / targetCal.toDouble()) else 1.0
        if (percentageDiff <= 0.20) {
            val xpKey = "nutrition_xp_$todayId"
            val prefs = context.getSharedPreferences("nutrition_xp", Context.MODE_PRIVATE)
            if (!prefs.getBoolean(xpKey, false)) {
                val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                if (userEmail != null) {
                    prefs.edit().putBoolean(xpKey, true).apply()
                    nutritionViewModel.awardNutritionXP(100)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onXPAdded() }
                }
            }
        }
    }

    // Barve iz teme — preberi enkrat v Composable kontekstu
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground

    // Ugotovi ali je danes workout day ali rest day (glede na aktiven plan)
    val isWorkoutDayToday = remember(plan) {
        if (plan == null) false
        else {
            // Preveri pri tekočem planu ali je danes trening ali počitek
            val startDate = try { kotlinx.datetime.LocalDate.parse(plan.startDate) } catch (_: Exception) { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
            val todayDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val daysSinceStart = (todayDate.toEpochDays() - startDate.toEpochDays())
            val allDays = plan.weeks.flatMap { it.days }
            val todayDayInPlan = allDays.getOrNull(daysSinceStart)
            // Če ni podatka o dnevu, predpostavi workout day
            todayDayInPlan?.isRestDay?.not() ?: true
        }
    }

    // Prilagojeni cilj za vodo — glede na profil in dan
    val adjustedWaterTarget = remember(userProfile, plan, isWorkoutDayToday) {
        // Teža: vzamemo iz algorithmData (kalorij/kg * skupne kalorije) ali fallback 70 kg
        val wKg: Double = run {
            val caloriesPerKg = plan?.algorithmData?.caloriesPerKg
            val calories = plan?.calories
            if (caloriesPerKg != null && caloriesPerKg > 0.0 && calories != null && calories > 0) {
                calories.toDouble() / caloriesPerKg
            } else 70.0
        }
        val isMale = userProfile.gender == "Male"
        val actLevel = userProfile.activityLevel ?: "Sedentary"
        com.example.myapplication.domain.nutrition.calculateDailyWaterMl(wKg, isMale, actLevel, isWorkoutDayToday)
    }

    // Prilagojene kalorije — workout day vs rest day (statični fallback)
    val adjustedTargetCalories = remember(targetCalories, isWorkoutDayToday, userProfile) {
        if (isWorkoutDayToday) {
            targetCalories
        } else {
            val isMale = userProfile.gender == "Male"
            val goal = userProfile.workoutGoal.ifBlank { null }
            com.example.myapplication.domain.nutrition.calculateRestDayCalories(targetCalories.toDouble(), goal, isMale)
        }
    }

    // ── Dinamični kalorični limit ──────────────────────────────────────────────
    // dynamicTargetCalories = BMR×1.2 + burnedCalories + goalAdj (real-time)
    // Fallback na statični adjustedTargetCalories, dokler profil ni naložen (dynamicTargetCalories == 0)
    val effectiveTargetCalories = if (dynamicTargetCalories > 0) dynamicTargetCalories else adjustedTargetCalories
    // Koliko "boost-a" smo dobili z aktivnostjo danes
    val activityBoostKcal = if (dynamicTargetCalories > 0 && uiState.burned > 0) uiState.burned else 0

    // Water tracking — prilagojen cilj
    // Faza 13.1: Prikaži lokalni override (optimistična voda) ali server vrednost
    val effectiveWaterMl = localWaterMl ?: uiState.water
    val waterTarget = adjustedWaterTarget  // calculateDailyWaterMl vrača Float
    val waterProgress = (effectiveWaterMl.toFloat() / waterTarget).coerceIn(0f, 1f)

    // Calculate macro calories
    val fatCals = (consumedFat * 9).roundToInt()
    val proteinCals = (consumedProtein * 4).roundToInt()
    val carbsCals = (consumedCarbs * 4).roundToInt()
    val totalMacroCalories = fatCals + proteinCals + carbsCals

    // Calculate proportions for each segment
    val fatProp = if (totalMacroCalories > 0) (fatCals.toFloat() / totalMacroCalories.toFloat()).coerceIn(0f, 1f) else 0f
    val proteinProp = if (totalMacroCalories > 0) (proteinCals.toFloat() / totalMacroCalories.toFloat()).coerceIn(0f, 1f) else 0f
    val carbsProp = if (totalMacroCalories > 0) (carbsCals.toFloat() / totalMacroCalories.toFloat()).coerceIn(0f, 1f) else 0f

    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior(androidx.compose.material3.rememberTopAppBarState())

    androidx.compose.material3.Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            com.example.myapplication.GlobalHeaderBar(
                isOnline = isOnline,
                onOpenMenu = onOpenMenu,
                onProClick = onProClick,
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = backgroundColor
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundColor)
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp)
            ) {

            // Donut Progress View
            // Donut Progress View + Active Calories Bar
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Workout / Rest day oznaka
                    if (plan != null) {
                        val dayLabel = if (isWorkoutDayToday) "️ Workout day" else " Rest day"
                        val labelColor = if (isWorkoutDayToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Text(
                            text = dayLabel,
                            color = labelColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    AndroidView(
                        factory = { ctx ->
                            DonutProgressView(ctx).apply {
                                simpleMode = !userProfile.detailedCalories
                                fatProportion = fatProp
                                proteinProportion = proteinProp
                                carbsProportion = carbsProp
                                this.fatCalories = fatCals
                                this.proteinCalories = proteinCals
                                this.carbsCalories = carbsCals
                                 this.consumedCalories = uiState.consumed
                                 this.targetCalories = adjustedTargetCalories
                                 innerProgress = waterProgress
                                 textColor = textPrimary.toArgb()
                                 waterColor = textPrimary.toArgb()
                                 innerValue = effectiveWaterMl.toString()
                                innerLabel = "ml"
                                weightUnit = userProfile.weightUnit
                                centerValue = "${uiState.consumed}/$adjustedTargetCalories"
                                centerLabel = "kcal"
                                startAngle = 135f
                                sweepAngle = 270f
                                onSegmentClick = { segment -> /* segment click: ${segment} */ }
                            }
                        },
                        modifier = Modifier.size(240.dp),
                         update = { view ->
                            view.simpleMode = !userProfile.detailedCalories
                            view.fatProportion = fatProp
                            view.proteinProportion = proteinProp
                            view.carbsProportion = carbsProp
                            view.fatCalories = fatCals
                            view.proteinCalories = proteinCals
                            view.carbsCalories = carbsCals
                            view.consumedCalories = uiState.consumed
                            view.targetCalories = effectiveTargetCalories
                             view.innerProgress = waterProgress
                             view.textColor = textPrimary.toArgb()
                             view.waterColor = textPrimary.toArgb()
                             view.innerValue = effectiveWaterMl.toString()
                            view.weightUnit = userProfile.weightUnit
                            view.centerValue = "${uiState.consumed}/$effectiveTargetCalories"
                            view.onSegmentClick = { clicked ->
                                view.clickedSegment = clicked
                                view.invalidate()
                            }
                        }
                    )
                    // Prilagojena voda pod grafom
                    if (userProfile.activityLevel != null || userProfile.gender != null) {
                        Text(
                            text = " Cilj: ${adjustedWaterTarget} ml",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    //  Dinamični boost indikator — prikazuje se le, ko je aktivnost > 0
                    if (activityBoostKcal > 0) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = " +$activityBoostKcal kcal boost",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                ) {
                    // Active Calories Bar — cilj = dinamični bolj cilj ali 500 kcal minimalno
                    val burnedGoal = if (dynamicTargetCalories > 0)
                        (dynamicTargetCalories - (dynamicTargetCalories * 0.8).toInt()).coerceAtLeast(300)
                    else 500
                    ActiveCaloriesBar(currentCalories = uiState.burned, goal = burnedGoal)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Other Macros button
            Button(
                onClick = {
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                        context,
                        com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK
                    )
                    showOtherMacros.value = true
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_info_details),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("OTHER MACROS", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Water controls
            WaterControlsRow(
                waterConsumedMl = effectiveWaterMl,
                textPrimary = textPrimary,
                lastClickState = lastWaterClickState,
                onMinus = { newVal ->
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK)
                    // Faza 13.1: Optimistično posodobimo UI takoj, Firestore sync debounced (800ms)
                    nutritionViewModel.updateWaterOptimistic(newVal, todayId)
                },
                onPlus = { newVal ->
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK)
                    // Faza 13.1: Optimistično posodobimo UI takoj, Firestore sync debounced (800ms)
                    nutritionViewModel.updateWaterOptimistic(newVal, todayId)
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Macro numbers row
            MacroTextRow(
                consumedProtein = consumedProtein,
                consumedFat = consumedFat,
                consumedCarbs = consumedCarbs,
                targetProtein = targetProtein,
                targetFat = targetFat,
                targetCarbs = targetCarbs,
                textPrimary = textPrimary,
                weightUnit = userProfile.weightUnit
            )

            Spacer(modifier = Modifier.height(8.dp))

            HintText("Tap colored segments for details")

            Spacer(modifier = Modifier.height(12.dp))

            // Make custom meals button
            Button(
                onClick = {
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK)
                    showMakeCustom.value = true
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("MAKE CUSTOM MEALS", color = MaterialTheme.colorScheme.background, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Saved custom meals
            savedMeals.forEach { meal ->
                SavedMealChip(
                    meal = meal,
                    textPrimary = textPrimary,
                    surfaceVariantColor = surfaceVariantColor,
                    onClick = { pendingCustomMeal.value = meal; askWhereToAdd.value = true },
                    onDelete = { confirmDelete.value = meal }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Meal cards (Breakfast, Lunch, Dinner, Snacks)
            listOf(
                MealType.Breakfast to "Breakfast",
                MealType.Lunch to "Lunch",
                MealType.Dinner to "Dinner",
                MealType.Snacks to "Snacks"
            ).forEach { (mealType, title) ->
                MealCard(
                    mealType = mealType,
                    title = title,
                    trackedFoods = trackedFoods,
                    surfaceVariantColor = surfaceVariantColor,
                    textPrimary = textPrimary,
                    onAddFood = {
                        // Faza 13.1: onemogoči dodajanje, ko je food operacija v teku
                        if (!isLoading) {
                            com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK)
                            // Faza 16.1: Takojšen odziv — setNavigating(true) pred odpiranjem sheeta
                            nutritionViewModel.setNavigating(true)
                            sheetMeal = mealType
                        }
                    },
                    onFoodClick = { showFoodDetailDialog.value = it },
                    onFoodDelete = { trackedFoods = trackedFoods.filterNot { t -> t.id == it.id } }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recipes search section
            RecipesSearchSection(
                onScanBarcode = onScanBarcode,
                userProfile = userProfile,
                onOpenAdditives = onOpenAdditives
            )

            Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for snackbar
        }
        // Faza 13.1 + 16.1: Loading/Navigating overlay — prikazuje se med food log operacijami in ob kliku na +
        if (isLoading || isNavigating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    } // Closes Scaffold!

    // Sheet: iskanje hrane + dodajanje
    if (sheetMeal != null) {
        // Faza 16.1: Ko se sheet dejansko odpre, resetiraj navigating overlay
        LaunchedEffect(sheetMeal) {
            if (sheetMeal != null) nutritionViewModel.setNavigating(false)
        }
        ModalBottomSheet(
            onDismissRequest = {
                sheetMeal = null
                onProductConsumed() // PoÄŤisti scanned product
            },
            sheetState = addSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            AddFoodSheet(
                meal = sheetMeal!!,
                onClose = {
                    sheetMeal = null
                    onProductConsumed() // PoÄŤisti scanned product
                },
                onAddTracked = { tf ->
                    trackedFoods = trackedFoods + tf
                    showAddedMessage = "Added ${tf.name} to ${tf.meal.title}" // immediate feedback
                    // Faza 13.1: logFoodAsync nastavi isLoading=true med Firestore zapisom
                    val map = mutableMapOf<String, Any>(
                        "id" to tf.id,
                        "name" to tf.name,
                        "meal" to tf.meal.name,
                        "amount" to tf.amount,
                        "unit" to tf.unit,
                        "caloriesKcal" to tf.caloriesKcal
                    )
                    tf.proteinG?.let { map["proteinG"] = it }
                    tf.carbsG?.let { map["carbsG"] = it }
                    tf.fatG?.let { map["fatG"] = it }
                    nutritionViewModel.logFoodAsync(map, todayId) {
                        Log.d("NutritionScreen", "Food persisted: ${tf.name}")
                    }
                    onProductConsumed()
                },
                scannedProduct = scannedProduct,
                onProductConsumed = onProductConsumed,
                isImperial = userProfile.weightUnit == "lb" || userProfile.speedUnit == "mph" // Simplified check
            )
        }
    }

    // Dialog: ustvarjanje custom meals
    if (showMakeCustom.value) {
        MakeCustomMealsDialog(
            onDismiss = { showMakeCustom.value = false },
            onSaved = { saved, mealType, isSaveOnly ->
                if (isSaveOnly) {
                    // "Save Only" — zapri dialog, prikaži Toast, NE odpri ChooseMealDialog
                    showMakeCustom.value = false
                    android.widget.Toast.makeText(context, "Meal Saved", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    // "Save & Add" — vprašaj za destinacijo
                    pendingCustomMeal.value = saved
                    chooseMealForCustom.value = mealType
                    showMakeCustom.value = false
                    askWhereToAdd.value = true
                }
            }
        )
    }

    // Food Detail Dialog - prikazuje podrobnosti o tracked food item
    showFoodDetailDialog.value?.let { trackedFood ->
        TrackedFoodDetailDialog(
            trackedFood = trackedFood,
            onDismiss = { showFoodDetailDialog.value = null },
            userProfile = userProfile
        )
    }

    // Dialog: izberi obrok in dodaj custom meal
    if (askWhereToAdd.value && pendingCustomMeal.value != null) {
        ChooseMealDialog(
            selected = chooseMealForCustom.value,
            onCancel = {
                askWhereToAdd.value = false
                pendingCustomMeal.value = null
            },
            onConfirmAsync = { mealChosen ->
                val cm = pendingCustomMeal.value!!
                val currentUid = FirestoreHelper.getCurrentUserDocId()

                if (currentUid == null) {
                    // Removed redundant qualifier
                    android.widget.Toast.makeText(context, "Not logged in", android.widget.Toast.LENGTH_SHORT).show()
                    pendingCustomMeal.value = null
                    askWhereToAdd.value = false
                    return@ChooseMealDialog true
                }

                val scope = kotlinx.coroutines.MainScope()
                scope.launch {
                    try {
                        val items = nutritionViewModel.getCustomMealItems(currentUid, cm.id)

                        if (items == null) {
                            android.widget.Toast.makeText(context, "Meal not found", android.widget.Toast.LENGTH_SHORT).show()
                            pendingCustomMeal.value = null
                            askWhereToAdd.value = false
                            return@launch
                        }

                        val newItems = items.mapNotNull { m ->
                            val name = m["name"] as? String ?: return@mapNotNull null
                            val amtStr = m["amt"] as? String ?: return@mapNotNull null
                            val amt = amtStr.toDoubleOrNull() ?: 1.0
                            val unit = m["unit"] as? String ?: "servings"

                            // Get macros from Firestore data
                            val caloriesKcal = (m["caloriesKcal"] as? Number)?.toDouble() ?: 0.0
                            val proteinG = (m["proteinG"] as? Number)?.toDouble() ?: 0.0
                            val carbsG = (m["carbsG"] as? Number)?.toDouble() ?: 0.0
                            val fatG = (m["fatG"] as? Number)?.toDouble() ?: 0.0
                            val fiberG = (m["fiberG"] as? Number)?.toDouble()
                            val sugarG = (m["sugarG"] as? Number)?.toDouble()
                            val saturatedFatG = (m["saturatedFatG"] as? Number)?.toDouble()
                            val sodiumMg = (m["sodiumMg"] as? Number)?.toDouble()
                            val potassiumMg = (m["potassiumMg"] as? Number)?.toDouble()
                            val cholesterolMg = (m["cholesterolMg"] as? Number)?.toDouble()

                            TrackedFood(
                                id = java.util.UUID.randomUUID().toString(),
                                name = name,
                                meal = mealChosen,
                                amount = amt,
                                unit = unit,
                                caloriesKcal = caloriesKcal,
                                proteinG = proteinG,
                                carbsG = carbsG,
                                fatG = fatG,
                                fiberG = fiberG,
                                sugarG = sugarG,
                                saturatedFatG = saturatedFatG,
                                sodiumMg = sodiumMg,
                                potassiumMg = potassiumMg,
                                cholesterolMg = cholesterolMg
                            )
                        }

                        if (newItems.isNotEmpty()) {
                            trackedFoods = trackedFoods + newItems
                            showAddedMessage = "Added custom meal: ${cm.name}"
                        } else {
                            android.widget.Toast.makeText(context, "No items found in custom meal.", android.widget.Toast.LENGTH_LONG).show()
                        }
                        pendingCustomMeal.value = null
                        askWhereToAdd.value = false
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Failed to load meal: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        pendingCustomMeal.value = null
                        askWhereToAdd.value = false
                    }
                }

                true // Close dialog immediately, Firestore callback will handle the rest
            }
        )
    }

    // Dialog: potrdi brisanje custom meal
    confirmDelete.value?.let { mealToDelete ->
        AlertDialog(
            onDismissRequest = { confirmDelete.value = null },
            confirmButton = {
                Button(onClick = {
                    val uidDel = FirestoreHelper.getCurrentUserDocId()
                    if (uidDel != null) {
                        val scope = kotlinx.coroutines.MainScope()
                        scope.launch {
                            try {
                                FoodRepositoryImpl.deleteCustomMeal(mealToDelete.id)
                                confirmDelete.value = null
                                // Refresh quick meal widget after deletion
                                com.example.myapplication.widget.QuickMealWidgetProvider.forceRefresh(context)
                            } catch (_: Exception) {
                                // handle
                            }
                        }
                    } else confirmDelete.value = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete.value = null }) { Text("Cancel") } },
            title = { Text("Delete custom meal?") },
            text = { Text("This will remove '${mealToDelete.name}' from saved custom meals.") }
        )
    }

    // Dialog: prikaži druge makre
    if (showOtherMacros.value) {
        val textColor = MaterialTheme.colorScheme.onSurface // Define textColor here
        AlertDialog(
            onDismissRequest = { showOtherMacros.value = false },
            confirmButton = {
                TextButton(onClick = { showOtherMacros.value = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            title = {
                Text(
                    "Other Macros",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Fiber
                            NutritionDetailRow(
                                "đźŚľ Fiber",
                                formatMacroWeight(consumedFiber, userProfile.weightUnit),
                                Color(0xFF10B981),
                                textColor // Pass textColor here
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Sugar
                            NutritionDetailRow(
                                "đźŤ¬ Sugar",
                                formatMacroWeight(consumedSugar, userProfile.weightUnit),
                                Color(0xFFEC4899),
                                textColor // Pass textColor here
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Saturated Fat
                            NutritionDetailRow(
                                "đźĄ“ Saturated Fat",
                                formatMacroWeight(consumedSatFat, userProfile.weightUnit),
                                Color(0xFFEF4444),
                                textColor // Pass textColor here
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Sodium
                            NutritionDetailRow(
                                "đź§‚ Sodium",
                                String.format(Locale.US, "%.0f mg", consumedSodium),
                                Color(0xFFF59E0B),
                                textColor // Pass textColor here
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Potassium
                            NutritionDetailRow(
                                "đźŤŚ Potassium",
                                String.format(Locale.US, "%.0f mg", consumedPotassium),
                                MaterialTheme.colorScheme.primary,
                                textColor // Pass textColor here
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            // Cholesterol
                            NutritionDetailRow(
                                "đźĄš Cholesterol",
                                String.format(Locale.US, "%.0f mg", consumedCholesterol),
                                Color(0xFF8B5CF6),
                                textColor // Pass textColor here
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "These values are calculated from all foods consumed today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

} // End NutritionScreen

