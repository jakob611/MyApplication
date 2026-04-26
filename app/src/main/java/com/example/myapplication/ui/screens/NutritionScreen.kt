@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import com.example.myapplication.data.PlanResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.data.HealthStorage
import com.example.myapplication.health.HealthConnectManager
import com.example.myapplication.network.OpenFoodFactsProduct
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaInstant
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    onProClick: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val nutritionViewModel: com.example.myapplication.viewmodels.NutritionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.example.myapplication.ui.screens.MyViewModelFactory(context)
    )
    val uiState by nutritionViewModel.uiState.collectAsState()
    // Dinamični TDEE — real-time vrednost iz ViewModel (0 = profil še ni naložen)
    val dynamicTargetCalories by nutritionViewModel.dynamicTargetCalories.collectAsState()
    // Faza 13.1: optimistična voda (instant UI feedback, debounced Firestore sync)
    val localWaterMl by nutritionViewModel.localWaterMl.collectAsState()
    // Faza 13.1: loading stanje za food operacije
    val isLoading by nutritionViewModel.isLoading.collectAsState()

    // Snackbar feedback state
    var showAddedMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showAddedMessage) {
        showAddedMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
            showAddedMessage = null
        }
    }

    // Active Calories (Health Connect) sync logic prenesena v ViewModel
    LaunchedEffect(Unit) {
        // Taksen loop avtomatsko sproži syncHealthConnectNow vsakih 5 sekund. 
        // Sync funkcija preveri delto in updejta Firestore, kar nato osveži uiState.burned preko listenerja.
        while (true) {
            nutritionViewModel.syncHealthConnectNow(context)
            kotlinx.coroutines.delay(5000)
        }
    }

    // Faza 5: Začenemo s praznim listom — Firestore snapshot listener (observeDailyLog)
    // bo naložil podatke asinhrono. Firestore SDK z isPersistenceEnabled=true zagotavlja
    // da so podatki dostopni takoj tudi brez omrežja (offline cache).
    var trackedFoods by remember { mutableStateOf<List<TrackedFood>>(emptyList()) }

    // UI debounce za vode gumbe (preprečuje double-tap)
    val lastWaterClickState = remember { mutableStateOf(0L) }

    // Poraba (izračuni)
    val consumedKcal = trackedFoods.sumOf { it.caloriesKcal.roundToInt() }
    val consumedProtein = trackedFoods.sumOf { it.proteinG ?: 0.0 }
    val consumedCarbs = trackedFoods.sumOf { it.carbsG ?: 0.0 }
    val consumedFat = trackedFoods.sumOf { it.fatG ?: 0.0 }
    val consumedFiber = trackedFoods.sumOf { it.fiberG ?: 0.0 }
    val consumedSugar = trackedFoods.sumOf { it.sugarG ?: 0.0 }
    val consumedSodium = trackedFoods.sumOf { it.sodiumMg ?: 0.0 }
    val consumedPotassium = trackedFoods.sumOf { it.potassiumMg ?: 0.0 }
    val consumedCholesterol = trackedFoods.sumOf { it.cholesterolMg ?: 0.0 }
    val consumedSatFat = trackedFoods.sumOf { it.saturatedFatG ?: 0.0 }

    var showOtherMacros by remember { mutableStateOf(false) }

    // ?? KRITIďż˝NO: Preberi nutrition plan iz NutritionPlanStore (posodobljiv plan)
    var nutritionPlan by remember { mutableStateOf<com.example.myapplication.data.NutritionPlan?>(null) }
    LaunchedEffect(Unit) {
        val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
        if (uid != null) {
            nutritionPlan = com.example.myapplication.persistence.NutritionPlanStore.loadNutritionPlan(uid)
        }
    }

    // Nastavi dinamični TDEE: preberi BMR iz shranjenih algoritmičnih podatkov
    // Prioriteta: nutritionPlan.algorithmData.bmr > plan.algorithmData.bmr
    LaunchedEffect(nutritionPlan, plan) {
        val bmr = nutritionPlan?.algorithmData?.bmr?.takeIf { it > 0 }
            ?: plan?.algorithmData?.bmr?.takeIf { it > 0.0 }
        val goal = nutritionPlan?.let {
            // goal ni shranjen v NutritionPlan — vzamemo iz userProfile ali plan
            userProfile.workoutGoal.ifBlank { null }
        } ?: plan?.goal ?: userProfile.workoutGoal
        if (bmr != null && bmr > 0) {
            nutritionViewModel.setUserMetrics(bmr, goal ?: "")
        }
    }

    // TarÄŤe
    val parsed = remember(plan?.algorithmData?.macroBreakdown) { parseMacroBreakdown(plan?.algorithmData?.macroBreakdown) }
    // Round target calories down to nearest 100
    val rawTargetCalories = nutritionPlan?.calories ?: parsed.calories ?: plan?.calories ?: 2000
    val targetCalories = (rawTargetCalories / 100) * 100

    val targetProtein  = nutritionPlan?.protein ?: parsed.proteinG ?: plan?.protein ?: 100
    val targetCarbs    = nutritionPlan?.carbs ?: parsed.carbsG ?: plan?.carbs ?: 200
    val targetFat      = nutritionPlan?.fat ?: parsed.fatG ?: plan?.fat ?: 60

    // Modali/sheets
    var sheetMeal by remember { mutableStateOf<MealType?>(null) }
    val addSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showMakeCustom by remember { mutableStateOf(false) }
    var pendingCustomMeal by remember { mutableStateOf<SavedCustomMeal?>(null) }
    var askWhereToAdd by remember { mutableStateOf(false) }
    var chooseMealForCustom by remember { mutableStateOf(MealType.Breakfast) }
    var showFoodDetailDialog by remember { mutableStateOf<TrackedFood?>(null) }

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
    var confirmDelete by remember { mutableStateOf<SavedCustomMeal?>(null) }

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

    // ── Identifikacija uporabnika ──────────────────────────────────────────────
    // POMEMBNO: uid mora biti v remember{} — getCurrentUserDocId() se ne sme klicati
    // ob vsakem recomposition, ker bi to vsakič restartalo DisposableEffect(uid, todayId)
    // in ustvarjalo nove Firestore listenerje (memory leak + napačno obnašanje).
    val uid = remember { com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() }
    val todayId = remember { kotlinx.datetime.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date.toString() }

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
    val todayDayOfWeek = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).dayOfWeek.value } // 1=Mon, 7=Sun
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
        com.example.myapplication.utils.calculateDailyWaterMl(wKg, isMale, actLevel, isWorkoutDayToday)
    }

    // Prilagojene kalorije — workout day vs rest day (statični fallback)
    val adjustedTargetCalories = remember(targetCalories, isWorkoutDayToday, userProfile) {
        if (isWorkoutDayToday) {
            targetCalories
        } else {
            val isMale = userProfile.gender == "Male"
            val goal = userProfile.workoutGoal.ifBlank { null }
            com.example.myapplication.utils.calculateRestDayCalories(targetCalories.toDouble(), goal, isMale)
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
    val waterTarget = adjustedWaterTarget.toFloat()
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
                                onSegmentClick = { segment -> Log.d("DonutRing", "Clicked: $segment") }
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
                    // 🔥 Dinamični boost indikator — prikazuje se le, ko je aktivnost > 0
                    if (activityBoostKcal > 0) {
                        androidx.compose.foundation.layout.Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = "🔥 +$activityBoostKcal kcal boost",
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
                    showOtherMacros = true
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
                    showMakeCustom = true
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
                    onClick = { pendingCustomMeal = meal; askWhereToAdd = true },
                    onDelete = { confirmDelete = meal }
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
                            sheetMeal = mealType
                        }
                    },
                    onFoodClick = { showFoodDetailDialog = it },
                    onFoodDelete = { trackedFoods = trackedFoods.filterNot { t -> t.id == it.id } }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recipes search section
            RecipesSearchSection(
                onScanBarcode = onScanBarcode,
                userProfile = userProfile
            )

            Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for snackbar
        }
        // Faza 13.1: Loading overlay — prikazuje se med food log operacijami
        if (isLoading) {
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
    if (showMakeCustom) {
        MakeCustomMealsDialog(
            onDismiss = { showMakeCustom = false },
            onSaved = { saved, mealType ->
                // Po shranjevanju takoj vpraĹˇamo kam dodaĹˇ; ÄŤipi se sami posodobijo prek snapshot listenerja
                pendingCustomMeal = saved
                chooseMealForCustom = mealType
                showMakeCustom = false
                askWhereToAdd = true
            }
        )
    }

    // Food Detail Dialog - prikaĹľe podrobnosti o tracked food item
    showFoodDetailDialog?.let { trackedFood ->
        TrackedFoodDetailDialog(
            trackedFood = trackedFood,
            onDismiss = { showFoodDetailDialog = null },
            userProfile = userProfile
        )
    }

    // Dialog: izberi obrok in dodaj custom meal
    if (askWhereToAdd && pendingCustomMeal != null) {
        ChooseMealDialog(
            selected = chooseMealForCustom,
            onCancel = {
                askWhereToAdd = false
                pendingCustomMeal = null
            },
            onConfirmAsync = { mealChosen ->
                val cm = pendingCustomMeal!!
                val currentUid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()

                if (currentUid == null) {
                    // Removed redundant qualifier
                    android.widget.Toast.makeText(context, "Not logged in", android.widget.Toast.LENGTH_SHORT).show()
                    pendingCustomMeal = null
                    askWhereToAdd = false
                    return@ChooseMealDialog true
                }

                val scope = kotlinx.coroutines.MainScope()
                scope.launch {
                    try {
                        val items = nutritionViewModel.getCustomMealItems(currentUid, cm.id)

                        if (items == null) {
                            android.widget.Toast.makeText(context, "Meal not found", android.widget.Toast.LENGTH_SHORT).show()
                            pendingCustomMeal = null
                            askWhereToAdd = false
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
                        pendingCustomMeal = null
                        askWhereToAdd = false
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Failed to load meal: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        pendingCustomMeal = null
                        askWhereToAdd = false
                    }
                }

                true // Close dialog immediately, Firestore callback will handle the rest
            }
        )
    }

    // Dialog: potrdi brisanje custom meal
    confirmDelete?.let { mealToDelete ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            confirmButton = {
                Button(onClick = {
                    val uidDel = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                    if (uidDel != null) {
                        val scope = kotlinx.coroutines.MainScope()
                        scope.launch {
                            try {
                                com.example.myapplication.data.nutrition.FoodRepositoryImpl.deleteCustomMeal(mealToDelete.id)
                                confirmDelete = null
                                // Refresh quick meal widget after deletion
                                com.example.myapplication.widget.QuickMealWidgetProvider.forceRefresh(context)
                            } catch (e: Exception) {
                                // handle
                            }
                        }
                    } else confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
            title = { Text("Delete custom meal?") },
            text = { Text("This will remove '${mealToDelete.name}' from saved custom meals.") }
        )
    }

    // Dialog: prikaĹľi druge makre
    if (showOtherMacros) {
        val textColor = MaterialTheme.colorScheme.onSurface // Define textColor here
        AlertDialog(
            onDismissRequest = { showOtherMacros = false },
            confirmButton = {
                TextButton(onClick = { showOtherMacros = false }) {
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

fun startOfDayDiffDays(startDate: kotlinx.datetime.LocalDate, todayDate: kotlinx.datetime.LocalDate): Int {
    return (todayDate.toEpochDays() - startDate.toEpochDays())
}
