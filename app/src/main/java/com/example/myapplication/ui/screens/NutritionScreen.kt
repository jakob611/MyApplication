@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screens

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
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.data.HealthStorage
import com.example.myapplication.health.HealthConnectManager
import com.example.myapplication.network.OpenFoodFactsProduct
import com.example.myapplication.ui.theme.DrawerBlue
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
        Text("🔥", fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(16.dp)
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE5E7EB))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFFF5722), Color(0xFFFF9800))
                        )
                    )
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$currentCalories",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFF5722)
        )
    }
}


@Composable
fun NutritionScreen(
    plan: PlanResult?,
    onScanBarcode: () -> Unit = {},
    onOpenEAdditives: () -> Unit = {},
    scannedProduct: Pair<OpenFoodFactsProduct, String>? = null,
    onProductConsumed: () -> Unit = {},
    openBarcodeScan: Boolean = false,
    openFoodSearch: Boolean = false,
    onXPAdded: () -> Unit = {},
    snackbarHostState: SnackbarHostState,
    userProfile: com.example.myapplication.data.UserProfile = com.example.myapplication.data.UserProfile()
) {
    // Snackbar feedback state
    var showAddedMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(showAddedMessage) {
        showAddedMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
            showAddedMessage = null
        }
    }

    // Context for widget updates
    val context = LocalContext.current

    // Active Calories (Health Connect) - load from SharedPreferences INSTANTLY
    val healthManager = remember { HealthConnectManager.getInstance(context) }
    val burnedPrefs = remember { context.getSharedPreferences("burned_cache", android.content.Context.MODE_PRIVATE) }
    val todayBurnedKey = remember { "burned_${java.time.LocalDate.now()}" }
    var activeCaloriesBurned by remember { mutableStateOf(burnedPrefs.getInt(todayBurnedKey, 0)) }

    LaunchedEffect(Unit) {
        // Check permissions and load data
        if (healthManager.isAvailable() && healthManager.hasAllPermissions()) {
            while (true) {
                val now = Instant.now()
                val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()

                // 1. Health Connect (Active)
                val healthConnectCalories = healthManager.readCalories(startOfDay, now)

                // 2. App Exercises (Workouts + Logs)
                val appExercisesCalories = HealthStorage.getTodayAppExercisesCalories()

                // Sum -> Total Active Calories
                val newBurned = healthConnectCalories + appExercisesCalories
                activeCaloriesBurned = newBurned

                // Save to SharedPreferences immediately
                burnedPrefs.edit().putInt(todayBurnedKey, newBurned).apply()

                kotlinx.coroutines.delay(10000) // Refresh every 10s
            }
        }
    }

    // DanaĹˇnji vnosi (lokalni state) â€” takoj naloĹľi iz lokalnega cache-a
    val initialFoods = remember {
        val cacheDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        try {
            val json = com.example.myapplication.persistence.DailySyncManager.loadFoodsJson(context, cacheDate)
            if (!json.isNullOrBlank()) {
                val arr = org.json.JSONArray(json)
                (0 until arr.length()).mapNotNull { i ->
                    try {
                        val obj = arr.getJSONObject(i)
                        val mealStr = obj.optString("meal", "Breakfast")
                        val meal = runCatching { MealType.valueOf(mealStr) }.getOrNull() ?: MealType.Breakfast
                        TrackedFood(
                            id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                            name = obj.optString("name", ""),
                            meal = meal,
                            amount = obj.optDouble("amount", 1.0),
                            unit = obj.optString("unit", "servings"),
                            caloriesKcal = obj.optDouble("caloriesKcal", 0.0),
                            proteinG = obj.optDouble("proteinG", 0.0).takeIf { it > 0 },
                            carbsG = obj.optDouble("carbsG", 0.0).takeIf { it > 0 },
                            fatG = obj.optDouble("fatG", 0.0).takeIf { it > 0 },
                            fiberG = obj.optDouble("fiberG", 0.0).takeIf { it > 0 },
                            sugarG = obj.optDouble("sugarG", 0.0).takeIf { it > 0 },
                            saturatedFatG = obj.optDouble("saturatedFatG", 0.0).takeIf { it > 0 },
                            sodiumMg = obj.optDouble("sodiumMg", 0.0).takeIf { it > 0 },
                            potassiumMg = obj.optDouble("potassiumMg", 0.0).takeIf { it > 0 },
                            cholesterolMg = obj.optDouble("cholesterolMg", 0.0).takeIf { it > 0 },
                            barcode = obj.optString("barcode", "").takeIf { it.isNotBlank() }
                        )
                    } catch (e: Exception) { null }
                }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }
    var trackedFoods by remember { mutableStateOf<List<TrackedFood>>(initialFoods) }

    // Water tracking - load from SharedPreferences INSTANTLY for no flicker
    val waterPrefs = remember { context.getSharedPreferences("water_cache", android.content.Context.MODE_PRIVATE) }
    val todayWaterKey = remember { "water_${java.time.LocalDate.now()}" }
    val cachedWater = remember { waterPrefs.getInt(todayWaterKey, 0) }
    var waterConsumedMl by remember { mutableStateOf(cachedWater) }
    // waterLoaded = true means Firestore initial read happened (ne overwritamo lokalnih sprememb)
    var waterLoaded by remember { mutableStateOf(false) }
    // UI debounce za vode gumbe (prepreÄŤuje double-tap)
    val lastWaterClickState = remember { mutableStateOf(0L) }

    // Poraba (izraÄŤuni)
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

    // Snapshot listener za customMeals
    DisposableEffect(Unit) {
        val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
        var reg: ListenerRegistration? = null
        if (uid != null) {
            reg = Firebase.firestore.collection("users").document(uid)
                .collection("customMeals")
                .addSnapshotListener { snaps, _ ->
                    val list = snaps?.documents?.mapNotNull { doc ->
                        val name = doc.getString("name") ?: "Custom meal"
                        @Suppress("UNCHECKED_CAST")
                        val itemsAny = doc.get("items") as? List<Map<String, Any?>>
                        val items: List<Map<String, Any>> = itemsAny?.mapNotNull { m ->
                            try {
                                // Create mutable map with Any type
                                val map = mutableMapOf<String, Any>()
                                map["id"] = m["id"] as? String ?: ""
                                map["name"] = m["name"] as? String ?: ""
                                map["amt"] = (m["amt"] as? String) ?: (m["amt"]?.toString() ?: "")
                                map["unit"] = m["unit"] as? String ?: ""
                                map // Return as immutable
                            } catch (_: Exception) { // Param e unused
                                null
                            }
                        } ?: emptyList()
                        SavedCustomMeal(id = doc.id, name = name, items = items)
                    } ?: emptyList()
                    savedMeals = list
                }
        }
        onDispose { reg?.remove() }
    }

    // ── Identifikacija uporabnika ──────────────────────────────────────────────
    // POMEMBNO: uid mora biti v remember{} — getCurrentUserDocId() se ne sme klicati
    // ob vsakem recomposition, ker bi to vsakič restartalo DisposableEffect(uid, todayId)
    // in ustvarjalo nove Firestore listenerje (memory leak + napačno obnašanje).
    val uid = remember { com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() }

    // todayId — skupni ključ za vse lokalne cache-e (food, water, burned)
    val todayId = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var lastSyncedSignature by remember { mutableStateOf("") }
    fun foodsSignature(list: List<TrackedFood>) = list.joinToString("|") { tf ->
        listOf(
            tf.name,
            tf.meal.name,
            tf.amount,
            tf.unit,
            tf.caloriesKcal,
            tf.proteinG ?: 0.0,
            tf.carbsG ?: 0.0,
            tf.fatG ?: 0.0,
            tf.fiberG ?: 0.0,
            tf.sugarG ?: 0.0,
            tf.saturatedFatG ?: 0.0,
            tf.sodiumMg ?: 0.0,
            tf.potassiumMg ?: 0.0,
            tf.cholesterolMg ?: 0.0
        ).joinToString(";")
    }

    // Restore ob zagonu â€” enkratno branje iz Firestore (samo ÄŤe je lokalni cache prazen)
    var firestoreFoodsLoaded by remember { mutableStateOf(false) }
    DisposableEffect(uid, todayId) {
        var reg: ListenerRegistration? = null
        if (uid != null) {
            reg = Firebase.firestore.collection("users").document(uid)
                .collection("dailyLogs").document(todayId)
                .addSnapshotListener { doc, _ ->
                    // â”€â”€ HRANA: Firestore prevzame samo ob prvem nalaganju in SAMO ÄŤe je lokalni cache prazen â”€â”€
                    if (!firestoreFoodsLoaded) {
                        firestoreFoodsLoaded = true
                        val items = doc?.get("items") as? List<*>
                        if (items != null && trackedFoods.isEmpty()) {
                            val parsedFoods = items.mapNotNull { any ->
                                val m = any as? Map<*, *> ?: return@mapNotNull null
                                val name = m["name"] as? String ?: return@mapNotNull null
                                val mealStr = m["meal"] as? String ?: "Breakfast"
                                val meal = runCatching { MealType.valueOf(mealStr) }.getOrNull() ?: MealType.Breakfast
                                val amount = (m["amount"] as? Number)?.toDouble()
                                    ?: (m["amount"] as? String)?.toDoubleOrNull() ?: 1.0
                                val unit = m["unit"] as? String ?: "servings"
                                val kcal = (m["caloriesKcal"] as? Number)?.toDouble()
                                    ?: (m["caloriesKcal"] as? String)?.toDoubleOrNull() ?: 0.0
                                val p = (m["proteinG"] as? Number)?.toDouble()
                                    ?: (m["proteinG"] as? String)?.toDoubleOrNull()
                                val c = (m["carbsG"] as? Number)?.toDouble()
                                    ?: (m["carbsG"] as? String)?.toDoubleOrNull()
                                val f = (m["fatG"] as? Number)?.toDouble()
                                    ?: (m["fatG"] as? String)?.toDoubleOrNull()
                                val barcode = m["barcode"] as? String
                                TrackedFood(
                                    id = (m["id"] as? String) ?: java.util.UUID.randomUUID().toString(),
                                    name = name,
                                    meal = meal,
                                    amount = amount,
                                    unit = unit,
                                    caloriesKcal = kcal,
                                    proteinG = p,
                                    carbsG = c,
                                    fatG = f,
                                    fiberG = (m["fiberG"] as? Number)?.toDouble()
                                        ?: (m["fiberG"] as? String)?.toDoubleOrNull(),
                                    sugarG = (m["sugarG"] as? Number)?.toDouble()
                                        ?: (m["sugarG"] as? String)?.toDoubleOrNull(),
                                    saturatedFatG = (m["saturatedFatG"] as? Number)?.toDouble()
                                        ?: (m["saturatedFatG"] as? String)?.toDoubleOrNull(),
                                    sodiumMg = (m["sodiumMg"] as? Number)?.toDouble()
                                        ?: (m["sodiumMg"] as? String)?.toDoubleOrNull(),
                                    potassiumMg = (m["potassiumMg"] as? Number)?.toDouble()
                                        ?: (m["potassiumMg"] as? String)?.toDoubleOrNull(),
                                    cholesterolMg = (m["cholesterolMg"] as? Number)?.toDouble()
                                        ?: (m["cholesterolMg"] as? String)?.toDoubleOrNull(),
                                    barcode = barcode
                                )
                            }
                            if (parsedFoods.isNotEmpty()) {
                                trackedFoods = parsedFoods
                                lastSyncedSignature = foodsSignature(parsedFoods)
                                Log.d("NutritionLocal", "Loaded ${parsedFoods.size} foods from Firestore (local was empty)")
                            }
                        }
                    }
                    // â”€â”€ VODA: samo ob prvem nalaganju, prevzame Firestore vrednost ÄŤe je viĹˇja â”€â”€
                    val serverWater = (doc?.get("waterMl") as? Number)?.toInt() ?: 0
                    if (!waterLoaded) {
                        if (serverWater > waterConsumedMl) {
                            waterConsumedMl = serverWater
                            waterPrefs.edit().putInt(todayWaterKey, serverWater).apply()
                        }
                        waterLoaded = true
                    }
                    // â”€â”€ BURNED: samo ob prvem nalaganju, prevzame Firestore vrednost ÄŤe je viĹˇja â”€â”€
                    val serverBurned = (doc?.get("burnedCalories") as? Number)?.toInt()
                    if (serverBurned != null && serverBurned > activeCaloriesBurned) {
                        activeCaloriesBurned = serverBurned
                        burnedPrefs.edit().putInt(todayBurnedKey, serverBurned).apply()
                    }
                }
        }
        onDispose { reg?.remove() }
    }

    // Lokalni zapis hrane â€” TAKOJ ob vsaki spremembi, brez ÄŤakanja na Firestore
    LaunchedEffect(trackedFoods, todayId) {
        val sig = foodsSignature(trackedFoods)
        if (sig == lastSyncedSignature) return@LaunchedEffect
        lastSyncedSignature = sig

        // Serializiraj v JSON in shrani lokalno
        try {
            val arr = org.json.JSONArray()
            trackedFoods.forEach { tf ->
                val obj = org.json.JSONObject().apply {
                    put("id", tf.id)
                    put("name", tf.name)
                    put("meal", tf.meal.name)
                    put("amount", tf.amount)
                    put("unit", tf.unit)
                    put("caloriesKcal", tf.caloriesKcal)
                    put("proteinG", tf.proteinG ?: 0.0)
                    put("carbsG", tf.carbsG ?: 0.0)
                    put("fatG", tf.fatG ?: 0.0)
                    put("fiberG", tf.fiberG ?: 0.0)
                    put("sugarG", tf.sugarG ?: 0.0)
                    put("saturatedFatG", tf.saturatedFatG ?: 0.0)
                    put("sodiumMg", tf.sodiumMg ?: 0.0)
                    put("potassiumMg", tf.potassiumMg ?: 0.0)
                    put("cholesterolMg", tf.cholesterolMg ?: 0.0)
                    if (tf.barcode != null) put("barcode", tf.barcode)
                }
                arr.put(obj)
            }
            com.example.myapplication.persistence.DailySyncManager.saveFoodsLocally(context, arr.toString(), todayId)
        } catch (e: Exception) {
            Log.e("NutritionLocal", "Failed to save foods locally", e)
        }

        // Preverimo XP nagrado lokalno (ne ÄŤakamo na Firestore)
        val targetCal = targetCalories
        val consumedCal = consumedKcal
        val difference = kotlin.math.abs(targetCal - consumedCal)
        val percentageDiff = if (targetCal > 0) (difference.toDouble() / targetCal.toDouble()) else 1.0
        if (percentageDiff <= 0.20) {
            val xpKey = "nutrition_xp_$todayId"
            val prefs = context.getSharedPreferences("nutrition_xp", Context.MODE_PRIVATE)
            if (!prefs.getBoolean(xpKey, false)) {
                val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                if (userEmail != null) {
                    com.example.myapplication.data.UserPreferences.addXPWithCallback(context, userEmail, 100) { _ ->
                        onXPAdded()
                    }
                    prefs.edit().putBoolean(xpKey, true).apply()
                }
            }
        }
    }

    // Lokalni zapis vode â€” TAKOJ ob vsaki spremembi, brez Firestore
    LaunchedEffect(waterConsumedMl, todayId) {
        // Shrani lokalno takoj
        waterPrefs.edit().putInt(todayWaterKey, waterConsumedMl).apply()
        com.example.myapplication.persistence.DailySyncManager.saveWaterLocally(context, waterConsumedMl, todayId)
        // Posodobi water widget takoj
        com.example.myapplication.widget.WaterWidgetProvider.updateWidgetFromApp(context, waterConsumedMl)
    }

    // Lokalni zapis porabljenih kalorij â€” TAKOJ, brez Firestore
    LaunchedEffect(activeCaloriesBurned, todayId) {
        burnedPrefs.edit().putInt(todayBurnedKey, activeCaloriesBurned).apply()
        com.example.myapplication.persistence.DailySyncManager.saveBurnedLocally(context, activeCaloriesBurned, todayId)
    }

    // Read theme colors in Composable context
    val backgroundColor = MaterialTheme.colorScheme.background
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    // Water tracking (2000ml target)
    val waterTarget = 2000f
    val waterProgress = (waterConsumedMl.toFloat() / waterTarget).coerceIn(0f, 1f)

    // Calculate macro calories
    val fatCals = (consumedFat * 9).roundToInt()
    val proteinCals = (consumedProtein * 4).roundToInt()
    val carbsCals = (consumedCarbs * 4).roundToInt()
    val totalMacroCalories = fatCals + proteinCals + carbsCals

    // Calculate proportions for each segment
    val fatProp = if (totalMacroCalories > 0) (fatCals.toFloat() / totalMacroCalories.toFloat()).coerceIn(0f, 1f) else 0f
    val proteinProp = if (totalMacroCalories > 0) (proteinCals.toFloat() / totalMacroCalories.toFloat()).coerceIn(0f, 1f) else 0f
    val carbsProp = if (totalMacroCalories > 0) (carbsCals.toFloat() / totalMacroCalories.toFloat()).coerceIn(0f, 1f) else 0f

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Donut Progress View
            // Donut Progress View + Active Calories Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AndroidView(
                    factory = { ctx ->
                        DonutProgressView(ctx).apply {
                            // Simple mode = !detailedCalories (privzeto enostaven)
                            simpleMode = !userProfile.detailedCalories
                            fatProportion = fatProp
                            proteinProportion = proteinProp
                            carbsProportion = carbsProp
                            this.fatCalories = fatCals
                            this.proteinCalories = proteinCals
                            this.carbsCalories = carbsCals
                            this.consumedCalories = consumedKcal
                            this.targetCalories = targetCalories
                            innerProgress = waterProgress
                            textColor = textPrimary.toArgb()
                            waterColor = textPrimary.toArgb()
                            innerValue = waterConsumedMl.toString()
                            innerLabel = "ml"
                            weightUnit = userProfile.weightUnit // Pass unit
                            centerValue = "$consumedKcal/$targetCalories"
                            centerLabel = "kcal"
                            startAngle = 135f
                            sweepAngle = 270f
                            onSegmentClick = { segment -> Log.d("DonutRing", "Clicked: $segment") }
                        }
                    },
                    modifier = Modifier.size(240.dp),
                    update = { view ->
                        // Update simpleMode when setting changes
                        view.simpleMode = !userProfile.detailedCalories
                        view.fatProportion = fatProp
                        view.proteinProportion = proteinProp
                        view.carbsProportion = carbsProp
                        view.fatCalories = fatCals
                        view.proteinCalories = proteinCals
                        view.carbsCalories = carbsCals
                        view.consumedCalories = consumedKcal
                        view.targetCalories = targetCalories
                        view.innerProgress = waterProgress
                        view.textColor = textPrimary.toArgb()
                        view.waterColor = textPrimary.toArgb()
                        view.innerValue = waterConsumedMl.toString()
                        view.weightUnit = userProfile.weightUnit // Update unit
                        view.centerValue = "$consumedKcal/$targetCalories"

                        // Update click listener to use correct units for tooltip
                        view.onSegmentClick = { clicked ->
                            view.clickedSegment = clicked
                            view.invalidate()
                        }
                    }
                )

                Spacer(modifier = Modifier.width(16.dp))

                // Active Calories Bar (Goal: 800 kcal)
                ActiveCaloriesBar(currentCalories = activeCaloriesBurned, goal = 800)
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
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("OTHER MACROS", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Water controls
            WaterControlsRow(
                waterConsumedMl = waterConsumedMl,
                textPrimary = textPrimary,
                lastClickState = lastWaterClickState,
                onMinus = { newVal ->
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK)
                    waterConsumedMl = newVal
                },
                onPlus = { newVal ->
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK)
                    waterConsumedMl = newVal
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976F6)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("MAKE CUSTOM MEALS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                        com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK)
                        sheetMeal = mealType
                    },
                    onFoodClick = { showFoodDetailDialog = it },
                    onFoodDelete = { trackedFoods = trackedFoods.filterNot { t -> t.id == it.id } }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Recipes search section
            RecipesSearchSection(
                onScanBarcode = onScanBarcode,
                onOpenEAdditives = onOpenEAdditives,
                userProfile = userProfile
            )

            Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for snackbar
        }
    }

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
                    onProductConsumed() // PoÄŤisti scanned product po dodajanju
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
            onSaved = { saved ->
                // Po shranjevanju takoj vpraĹˇamo kam dodaĹˇ; ÄŤipi se sami posodobijo prek snapshot listenerja
                pendingCustomMeal = saved
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

                // CRITICAL FIX: Re-fetch from Firestore to get ALL nutritional data
                Firebase.firestore.collection("users").document(currentUid)
                    .collection("customMeals").document(cm.id)
                    .get()
                    .addOnSuccessListener { doc ->
                        if (!doc.exists()) {
                            android.widget.Toast.makeText(context, "Meal not found", android.widget.Toast.LENGTH_SHORT).show()
                            pendingCustomMeal = null
                            askWhereToAdd = false
                            return@addOnSuccessListener
                        }

                        val items = doc.get("items") as? List<*> ?: emptyList<Any>()

                        val newItems = items.mapNotNull { any ->
                            val m = any as? Map<*, *> ?: return@mapNotNull null
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
                    }
                    .addOnFailureListener { e ->
                        android.widget.Toast.makeText(context, "Failed to load meal: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                        pendingCustomMeal = null
                        askWhereToAdd = false
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
                        Firebase.firestore.collection("users").document(uidDel)
                            .collection("customMeals").document(mealToDelete.id)
                            .delete()
                            .addOnCompleteListener {
                                confirmDelete = null
                                // Refresh quick meal widget after deletion
                                com.example.myapplication.widget.QuickMealWidgetProvider.forceRefresh(context)
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
                    Text("Close", color = DrawerBlue)
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
                                Color(0xFF3B82F6),
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
