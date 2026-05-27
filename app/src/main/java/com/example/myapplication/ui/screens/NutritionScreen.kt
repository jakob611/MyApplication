@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screens

// =====================================================================
// NutritionScreen.kt — Koordinator layouta (Faza 50 — modularizacija)
// CalorieProgressHeader  -> ui/nutrition/components/CalorieProgressHeader.kt
// WaterTrackerSection    -> ui/nutrition/components/WaterTrackerSection.kt
// TrackedFoodsList       -> ui/nutrition/components/TrackedFoodsList.kt
// CustomMealSection      -> ui/nutrition/components/CustomMealDialogs.kt
// =====================================================================

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.network.OpenFoodFactsProduct
import com.example.myapplication.ui.nutrition.NutritionViewModel
import com.example.myapplication.ui.nutrition.components.CalorieProgressHeader
import com.example.myapplication.ui.nutrition.components.CustomMealSection
import com.example.myapplication.ui.nutrition.components.TrackedFoodsList
import com.example.myapplication.ui.nutrition.components.WaterTrackerSection
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.roundToInt

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
        factory = MyViewModelFactory(context)
    )

    // ── State collection ─────────────────────────────────────────────────
    val uiState                   by nutritionViewModel.uiState.collectAsState()
    val dynamicTargetCalories     by nutritionViewModel.dynamicTargetCalories.collectAsState()
    val effectiveWaterMl          by nutritionViewModel.waterDisplayMl.collectAsState()
    val isLoading                 by nutritionViewModel.isLoading.collectAsState()
    val isNavigating              by nutritionViewModel.isNavigating.collectAsState()
    val targets                   by nutritionViewModel.nutritionTargets.collectAsState()
    val nutritionContext          by nutritionViewModel.todayNutritionContext.collectAsState()
    val macroTotals               by nutritionViewModel.macroTotals.collectAsState()
    val firestoreFoods            by nutritionViewModel.firestoreFoods.collectAsState()
    val nutritionPlanLoadComplete by nutritionViewModel.nutritionPlanLoadComplete.collectAsState()
    val todayId                   by nutritionViewModel.currentDate.collectAsState()

    // ── Derived / computed values ────────────────────────────────────────
    val isWorkoutDayToday       = nutritionContext.isWorkoutDay
    val adjustedWaterTarget     = nutritionContext.adjustedWaterTargetMl.toFloat()
    val adjustedTargetCalories  = nutritionContext.adjustedCalorieTarget
    val effectiveTargetCalories = if (dynamicTargetCalories > 0) dynamicTargetCalories else adjustedTargetCalories
    val activityBoostKcal       = if (dynamicTargetCalories > 0 && uiState.burned > 0) uiState.burned else 0
    val waterProgress           = (effectiveWaterMl.toFloat() / adjustedWaterTarget.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val fatCals     = (macroTotals.fat     * 9).roundToInt()
    val proteinCals = (macroTotals.protein * 4).roundToInt()
    val carbsCals   = (macroTotals.carbs   * 4).roundToInt()
    val totalMacroCals = (fatCals + proteinCals + carbsCals).coerceAtLeast(1)
    val fatProp     = (fatCals.toFloat()     / totalMacroCals).coerceIn(0f, 1f)
    val proteinProp = (proteinCals.toFloat() / totalMacroCals).coerceIn(0f, 1f)
    val carbsProp   = (carbsCals.toFloat()   / totalMacroCals).coerceIn(0f, 1f)

    // ── Lokalni UI state ─────────────────────────────────────────────────
    var trackedFoods by remember { mutableStateOf<List<TrackedFood>>(emptyList()) }
    val lastWaterClickState  = remember { mutableStateOf(0L) }
    var showAddedMessage     by remember { mutableStateOf<String?>(null) }
    var sheetMeal            by remember { mutableStateOf<MealType?>(null) }
    val addSheetState        = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showOtherMacros      = remember { mutableStateOf(false) }
    val backgroundColor      = MaterialTheme.colorScheme.background
    val textPrimary          = MaterialTheme.colorScheme.onBackground
    val surfaceVariantColor  = MaterialTheme.colorScheme.surfaceVariant

    // ── Effects ──────────────────────────────────────────────────────────
    LaunchedEffect(nutritionViewModel) {
        nutritionViewModel.xpAwardedEvent.collect { onXPAdded() }
    }
    LaunchedEffect(showAddedMessage) {
        showAddedMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
            showAddedMessage = null
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) nutritionViewModel.syncHealthConnectNow(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(scannedProduct)  { if (scannedProduct != null) sheetMeal = MealType.Snacks }
    LaunchedEffect(openBarcodeScan) { if (openBarcodeScan) { delay(100); onScanBarcode() } }
    LaunchedEffect(openFoodSearch) {
        if (openFoodSearch) {
            val cal = java.util.Calendar.getInstance()
            val t   = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            sheetMeal = when {
                t in 300..599   -> MealType.Breakfast
                t in 690..899   -> MealType.Lunch
                t in 1080..1199 -> MealType.Dinner
                else            -> MealType.Snacks
            }
        }
    }
    LaunchedEffect(firestoreFoods)            { if (firestoreFoods.isNotEmpty()) trackedFoods = firestoreFoods }
    LaunchedEffect(firestoreFoods, todayId)   { nutritionViewModel.verifyAndAwardNutritionXP() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            val newDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            if (newDate != todayId) {
                nutritionViewModel.onDayTransition(newDate)
                Log.d("NutritionScreen", "Midnight transition: $todayId -> $newDate")
            }
        }
    }
    LaunchedEffect(targets.calories, targets.protein, targets.carbs, targets.fat, nutritionPlanLoadComplete) {
        if (targets.calories > 0) {
            nutritionViewModel.ensureDayInitialized(
                date           = todayId,
                targetCalories = targets.calories,
                targetProtein  = targets.protein,
                targetCarbs    = targets.carbs,
                targetFat      = targets.fat,
                isPlanLoaded   = nutritionPlanLoadComplete
            )
        }
    }

    // ── Scaffold + main layout ───────────────────────────────────────────
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            com.example.myapplication.GlobalHeaderBar(
                isOnline = isOnline, onOpenMenu = onOpenMenu,
                onProClick = onProClick, scrollBehavior = scrollBehavior
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
                // Kalorični progress + aktivnostni indikator
                CalorieProgressHeader(
                    plan = plan, isWorkoutDay = isWorkoutDayToday,
                    consumed = uiState.consumed, burned = uiState.burned,
                    adjustedTargetCalories = adjustedTargetCalories,
                    effectiveTargetCalories = effectiveTargetCalories,
                    dynamicTargetCalories = dynamicTargetCalories,
                    effectiveWaterMl = effectiveWaterMl, waterProgress = waterProgress,
                    waterTargetMl = adjustedWaterTarget,
                    showWaterGoal = userProfile.activityLevel != null || userProfile.gender != null,
                    fatProp = fatProp, proteinProp = proteinProp, carbsProp = carbsProp,
                    fatCals = fatCals, proteinCals = proteinCals, carbsCals = carbsCals,
                    detailedCalories = userProfile.detailedCalories,
                    weightUnit = userProfile.weightUnit,
                    textPrimary = textPrimary, activityBoostKcal = activityBoostKcal
                )
                Spacer(Modifier.height(8.dp))

                // Gumb za druge makre
                Button(
                    onClick = {
                        com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                            context, com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK
                        )
                        showOtherMacros.value = true
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_info_details),
                        contentDescription = null, tint = backgroundColor
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("OTHER MACROS", color = backgroundColor, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))

                // Sledilnik vode
                WaterTrackerSection(
                    waterConsumedMl = effectiveWaterMl,
                    lastClickState = lastWaterClickState,
                    context = context,
                    onWaterUpdate = { newVal -> nutritionViewModel.updateWaterOptimistic(newVal, todayId) },
                    textPrimary = textPrimary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(12.dp))

                // Makro vrednosti (P/F/C)
                MacroTextRow(
                    consumedProtein = macroTotals.protein, consumedFat = macroTotals.fat,
                    consumedCarbs = macroTotals.carbs,
                    targetProtein = targets.protein, targetFat = targets.fat,
                    targetCarbs = targets.carbs,
                    textPrimary = textPrimary, weightUnit = userProfile.weightUnit
                )
                Spacer(Modifier.height(8.dp))
                HintText("Tap colored segments for details")
                Spacer(Modifier.height(12.dp))

                // Custom obroki (gumb + chips + dialogi)
                CustomMealSection(
                    customMeals = nutritionContext.customMeals,
                    textPrimary = textPrimary, surfaceVariantColor = surfaceVariantColor,
                    context = context, nutritionViewModel = nutritionViewModel,
                    trackedFoods = trackedFoods,
                    onTrackedFoodsChange = { trackedFoods = it },
                    onShowMessage = { showAddedMessage = it },
                    consumedMacros = macroTotals, weightUnit = userProfile.weightUnit,
                    showOtherMacros = showOtherMacros
                )
                Spacer(Modifier.height(12.dp))

                // Zasledovane jedi po obrokih (Breakfast / Lunch / Dinner / Snacks)
                TrackedFoodsList(
                    trackedFoods = trackedFoods,
                    surfaceVariantColor = surfaceVariantColor, textPrimary = textPrimary,
                    userProfile = userProfile,
                    onAddFood = { mealType ->
                        if (!isLoading) {
                            com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                                context, com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK
                            )
                            nutritionViewModel.setNavigating(true)
                            sheetMeal = mealType
                        }
                    },
                    onFoodDelete = { food -> trackedFoods = trackedFoods.filterNot { it.id == food.id } }
                )
                Spacer(Modifier.height(16.dp))

                // Iskanje receptov + skeniranje
                RecipesSearchSection(
                    onScanBarcode = onScanBarcode,
                    userProfile = userProfile,
                    onOpenAdditives = onOpenAdditives
                )
                Spacer(Modifier.height(80.dp))
            }

            // Loading / navigating overlay
            if (isLoading || isNavigating) {
                Box(
                    modifier = Modifier.fillMaxSize().background(backgroundColor.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            }
        }
    }

    // ── ModalBottomSheet: dodajanje hrane ────────────────────────────────
    if (sheetMeal != null) {
        LaunchedEffect(sheetMeal) { if (sheetMeal != null) nutritionViewModel.setNavigating(false) }
        ModalBottomSheet(
            onDismissRequest = { sheetMeal = null; onProductConsumed() },
            sheetState = addSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            AddFoodSheet(
                meal = sheetMeal!!,
                onClose = { sheetMeal = null; onProductConsumed() },
                onAddTracked = { tf ->
                    trackedFoods = trackedFoods + tf
                    showAddedMessage = "Added ${tf.name} to ${tf.meal.title}"
                    val map = mutableMapOf<String, Any>(
                        "id" to tf.id, "name" to tf.name, "meal" to tf.meal.name,
                        "amount" to tf.amount, "unit" to tf.unit, "caloriesKcal" to tf.caloriesKcal
                    )
                    tf.proteinG?.let { map["proteinG"] = it }
                    tf.carbsG?.let  { map["carbsG"]   = it }
                    tf.fatG?.let    { map["fatG"]     = it }
                    nutritionViewModel.logFoodAsync(map, todayId) {
                        Log.d("NutritionScreen", "Food persisted: ${tf.name}")
                    }
                    onProductConsumed()
                },
                scannedProduct = scannedProduct,
                onProductConsumed = onProductConsumed,
                isImperial = userProfile.weightUnit == "lb" || userProfile.speedUnit == "mph"
            )
        }
    }
} // End NutritionScreen

