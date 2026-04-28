@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screens

import androidx.compose.material3.ExperimentalMaterial3Api

// =====================================================================
// NutritionDialogs.kt
// Vsebuje: vse dialoge za Nutrition ekran.
//   - ChooseMealDialog     — izbira obroka za custom meal
//   - AmountDialog         — vnos količine/enote pri dodajanju hrane
//   - TrackedFoodDetailDialog — podrobnosti sledene hrane
//   - MakeCustomMealsDialog — ustvarjanje custom obrokov
// =====================================================================

import android.util.Log
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.network.FatSecretApi
import com.example.myapplication.network.FoodDetail
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// -----------------------------------------------------------------------
// ChooseMealDialog — izbira obroka pri dodajanju custom meal
// -----------------------------------------------------------------------
@Composable
internal fun ChooseMealDialog(
    selected: MealType,
    onCancel: () -> Unit,
    onConfirmAsync: suspend (MealType) -> Boolean
) {
    var pick by remember { mutableStateOf(selected) }
    var loading by remember { mutableStateOf(false) }
    var showSpinner by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!loading) onCancel() },
        confirmButton = {
            Button(
                onClick = {
                    if (loading) return@Button
                    loading = true
                    showSpinner = false
                    scope.launch {
                        val trigger = launch { kotlinx.coroutines.delay(500); showSpinner = true }
                        try {
                            onConfirmAsync(pick)
                        } finally {
                            loading = false
                            showSpinner = false
                        }
                        trigger.cancel()
                    }
                },
                enabled = !loading
            ) {
                if (showSpinner) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.tertiary)
                else Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = { if (!loading) onCancel() }, enabled = !loading) { Text("Cancel") }
        },
        title = { Text("Add created meal to") },
        text = {
            Column {
                listOf(MealType.Breakfast, MealType.Lunch, MealType.Dinner, MealType.Snacks).forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = pick == m, onClick = { if (!loading) pick = m })
                        Spacer(Modifier.width(8.dp))
                        Text(m.title)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}

// -----------------------------------------------------------------------
// AmountDialog — vnos količine pri dodajanju hrane iz iskanja
// -----------------------------------------------------------------------
@Composable
internal fun AmountDialog(
    detail: FoodDetail,
    meal: MealType,
    barcode: String? = null,
    customButtonText: String? = null,
    onCancel: () -> Unit,
    isImperial: Boolean,
    onConfirm: (TrackedFood) -> Unit
) {
    val metricUnit = detail.metricServingUnit?.lowercase(Locale.US)
        ?.let { if (it == "g" || it == "ml") it else null }

    // User request: Default to servings (1), set proper defaults when switching
    var unit by remember { mutableStateOf("servings") }
    var amount by remember { mutableStateOf("1") }
    
    // Auto-select defaults on init (redundant but safe)
    LaunchedEffect(Unit) {
        amount = "1"
        unit = "servings"
    }

    val amountDouble = amount.toDoubleOrNull()
    val baseServingSize = detail.metricServingAmount ?: 100.0

    val scaleFactor = remember(detail, unit, amountDouble, baseServingSize) {
        when {
            amountDouble == null -> 1.0
            unit == "g" || unit == "ml" -> amountDouble / baseServingSize
            unit == "oz" -> (amountDouble * 28.3495) / baseServingSize
            else -> amountDouble
        }
    }
    val preview = remember(detail, scaleFactor) {
        detail.copy(
            caloriesKcal = (detail.caloriesKcal ?: 0.0) * scaleFactor,
            proteinG = (detail.proteinG ?: 0.0) * scaleFactor,
            carbsG = (detail.carbsG ?: 0.0) * scaleFactor,
            fatG = (detail.fatG ?: 0.0) * scaleFactor,
            fiberG = detail.fiberG?.times(scaleFactor),
            sugarG = detail.sugarG?.times(scaleFactor),
            saturatedFatG = detail.saturatedFatG?.times(scaleFactor),
            sodiumMg = detail.sodiumMg?.times(scaleFactor),
            potassiumMg = detail.potassiumMg?.times(scaleFactor),
            cholesterolMg = detail.cholesterolMg?.times(scaleFactor)
        )
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val availableUnits = remember(metricUnit, isImperial) {
        val list = mutableListOf<String>()
        // Always include main units
        list.add("servings")
        list.add("g")
        list.add("oz")
        if (metricUnit != null && metricUnit != "g" && metricUnit != "ml" && !list.contains(metricUnit))
            list.add(metricUnit)
        list
    }

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Button(
                onClick = {
                    if (amountDouble != null && amountDouble > 0) {
                        onConfirm(TrackedFood(
                            id = java.util.UUID.randomUUID().toString(),
                            name = detail.name,
                            meal = meal,
                            amount = amountDouble,
                            unit = unit, 
                            caloriesKcal = preview.caloriesKcal ?: 0.0,
                            proteinG = preview.proteinG,
                            carbsG = preview.carbsG,
                            fatG = preview.fatG,
                            fiberG = preview.fiberG,
                            sugarG = preview.sugarG,
                            saturatedFatG = preview.saturatedFatG,
                            sodiumMg = preview.sodiumMg,
                            potassiumMg = preview.potassiumMg,
                            cholesterolMg = preview.cholesterolMg,
                            barcode = barcode
                        ))
                    }
                },
                enabled = amountDouble != null && amountDouble > 0,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.background)
            ) { Text(customButtonText ?: "Add to ${meal.title}") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        title = {
            Column {
                Text(detail.name, style = MaterialTheme.typography.titleLarge, color = textColor)
                Spacer(Modifier.height(4.dp))
                // User request: "Naj piše le na vrhu" - showing 1 serving nutrition
                Text("Nutrition for 1 serving", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } 
        },
        text = {
            Column {
                // User request: "pri količinah pri vseh živilih je zmedeno. Naj piše le na vrhu"
                // Removed complex labels, simplified input
                
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") }, // Simplified label
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    availableUnits.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = {
                                unit = u
                                // User request: Defaults -> servings=1, g/ml=100, oz=10
                                amount = when (u) {
                                    "servings" -> "1"
                                    "g", "ml" -> "100"
                                    "oz" -> "10"
                                    else -> "1"
                                }
                            },
                            label = { Text(u) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cal: ${preview.caloriesKcal?.roundToInt() ?: 0}")
                    Text("Protein: ${String.format(Locale.US, "%.1f", preview.proteinG ?: 0.0)}g")
                    Text("Carbs: ${String.format(Locale.US, "%.1f", preview.carbsG ?: 0.0)}g")
                    Text("Fat: ${String.format(Locale.US, "%.1f", preview.fatG ?: 0.0)}g")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

// -----------------------------------------------------------------------
// TrackedFoodDetailDialog — podrobnosti sledene hrane (klik na item)
// -----------------------------------------------------------------------
@Composable
internal fun TrackedFoodDetailDialog(
    trackedFood: TrackedFood,
    onDismiss: () -> Unit,
    userProfile: com.example.myapplication.data.UserProfile
) {
    var isLoading by remember { mutableStateOf(true) }
    var foodDetail by remember { mutableStateOf<FoodDetail?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val PrimaryBlue = MaterialTheme.colorScheme.primary

    LaunchedEffect(trackedFood) {
        isLoading = true
        errorMessage = null
        try {
            if (trackedFood.barcode != null) {
                Log.d("TrackedFoodDetail", "Using OpenFoodFacts with barcode: ${trackedFood.barcode}")
                val offResponse = com.example.myapplication.network.OpenFoodFactsAPI.getProductByBarcode(trackedFood.barcode)
                val offProduct = offResponse?.product
                if (offProduct != null && offResponse.status == 1) {
                    foodDetail = FoodDetail(
                        id = trackedFood.barcode,
                        name = offProduct.productName ?: trackedFood.name,
                        caloriesKcal = offProduct.nutriments?.energyKcal100g,
                        proteinG = offProduct.nutriments?.proteins100g,
                        carbsG = offProduct.nutriments?.carbohydrates100g,
                        fatG = offProduct.nutriments?.fat100g,
                        servingDescription = offProduct.servingSize,
                        metricServingAmount = 100.0,
                        metricServingUnit = "g",
                        fiberG = offProduct.nutriments?.fiber100g,
                        sugarG = offProduct.nutriments?.sugars100g,
                        saturatedFatG = offProduct.nutriments?.saturatedFat100g,
                        sodiumMg = offProduct.nutriments?.sodium100g?.times(1000),
                        potassiumMg = offProduct.nutriments?.potassium100g?.times(1000),
                        cholesterolMg = offProduct.nutriments?.cholesterol100g?.times(1000)
                    )
                } else {
                    errorMessage = "Product not found on OpenFoodFacts database."
                }
            } else {
                Log.d("TrackedFoodDetail", "No barcode, searching FatSecret: ${trackedFood.name}")
                val searchResults = FatSecretApi.searchFoods(trackedFood.name, 1, 5)
                if (searchResults.isNotEmpty()) {
                    foodDetail = FatSecretApi.getFoodDetail(searchResults.first().id)
                } else {
                    errorMessage = "Product not found on FatSecret database."
                }
            }
        } catch (e: Exception) {
            Log.e("TrackedFoodDetail", "Error loading food details", e)
            errorMessage = "Error: ${e.localizedMessage}"
        } finally {
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(trackedFood.name, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                when {
                    isLoading -> Box(
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(color = PrimaryBlue) }
                    errorMessage != null -> Text(
                        errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    foodDetail != null -> {
                        Text("Nutrition for your serving:", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("${trackedFood.amount.toInt()} ${trackedFood.unit}",
                                    fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PrimaryBlue)
                                Spacer(Modifier.height(12.dp))
                                NutritionDetailRow("Calories", "${trackedFood.caloriesKcal.toInt()} kcal",
                                    PrimaryBlue, MaterialTheme.colorScheme.onSurface)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                trackedFood.proteinG?.let {
                                    NutritionDetailRow("🥩 Protein", formatMacroWeight(it, userProfile.weightUnit),
                                        Color(0xFF10B981), MaterialTheme.colorScheme.onSurface)
                                }
                                trackedFood.carbsG?.let {
                                    NutritionDetailRow("🍞 Carbs", formatMacroWeight(it, userProfile.weightUnit),
                                        Color(0xFFF59E0B), MaterialTheme.colorScheme.onSurface)
                                }
                                trackedFood.fatG?.let {
                                    NutritionDetailRow("🥑 Fat", formatMacroWeight(it, userProfile.weightUnit),
                                        Color(0xFFEF4444), MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        // Additional nutrition
                        if (trackedFood.fiberG != null || trackedFood.sugarG != null ||
                            trackedFood.saturatedFatG != null || trackedFood.sodiumMg != null ||
                            trackedFood.potassiumMg != null || trackedFood.cholesterolMg != null) {
                            Spacer(Modifier.height(12.dp))
                            Text("Additional Nutrition:", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                modifier = Modifier.padding(bottom = 8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    trackedFood.fiberG?.takeIf { it > 0 }?.let {
                                        NutritionDetailRow("🌾 Fiber", formatMacroWeight(it, userProfile.weightUnit),
                                            Color(0xFF10B981), MaterialTheme.colorScheme.onSurface)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                    trackedFood.sugarG?.takeIf { it > 0 }?.let {
                                        NutritionDetailRow("🍬 Sugar", formatMacroWeight(it, userProfile.weightUnit),
                                            Color(0xFFEC4899), MaterialTheme.colorScheme.onSurface)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                    trackedFood.saturatedFatG?.takeIf { it > 0 }?.let {
                                        NutritionDetailRow("🥓 Saturated Fat", formatMacroWeight(it, userProfile.weightUnit),
                                            Color(0xFFEF4444), MaterialTheme.colorScheme.onSurface)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                    trackedFood.sodiumMg?.takeIf { it > 0 }?.let {
                                        NutritionDetailRow("🧂 Sodium", String.format(Locale.US, "%.0f mg", it),
                                            Color(0xFFF59E0B), MaterialTheme.colorScheme.onSurface)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                    trackedFood.potassiumMg?.takeIf { it > 0 }?.let {
                                        NutritionDetailRow("🍌 Potassium", String.format(Locale.US, "%.0f mg", it),
                                            MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface)
                                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                                    }
                                    trackedFood.cholesterolMg?.takeIf { it > 0 }?.let {
                                        NutritionDetailRow("🥚 Cholesterol", String.format(Locale.US, "%.0f mg", it),
                                            Color(0xFF8B5CF6), MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = PrimaryBlue) } }
    )
}

// -----------------------------------------------------------------------
// MakeCustomMealsDialog — ustvarjanje in shranjevanje custom obrokov
// -----------------------------------------------------------------------
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun MakeCustomMealsDialog(
    onDismiss: () -> Unit,
    onSaved: (SavedCustomMeal, MealType, isSaveOnly: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(1) } // 1: Ingredients, 2: Name, 3: Destination
    var selectedMeal by remember { mutableStateOf(MealType.Lunch) }
    var name by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf<List<TrackedFood>>(emptyList()) }
    var showFoodSearch by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val totalKcal = ingredients.sumOf { it.caloriesKcal }
    val totalProtein = ingredients.sumOf { it.proteinG ?: 0.0 }
    val totalCarbs = ingredients.sumOf { it.carbsG ?: 0.0 }
    val totalFat = ingredients.sumOf { it.fatG ?: 0.0 }

    // ── Ko je showFoodSearch=true, AlertDialog postane neviden (ne lovi klikov s scrimom).
    // Stanje (ingredients, name, step) se ohrani, ker je deklarirano zunaj obeh composablov.
    if (!showFoodSearch) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    1 -> "Step 1: Add Ingredients"
                    2 -> "Step 2: Name Your Meal"
                    else -> "Step 3: Where to Add?"
                }
            )
        },
        text = {
            androidx.compose.animation.AnimatedContent(targetState = step, label = "wizard_step") { targetStep ->
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
                    when (targetStep) {
                        1 -> {
                            Text("Search and add ingredients to your custom meal.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(16.dp))
                            ingredients.forEach { item ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Text("${item.name} (${item.amount.toInt()} ${item.unit})", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    IconButton(onClick = { ingredients = ingredients - item }) {
                                        Icon(Icons.Filled.Close, "Remove")
                                    }
                                }
                            }
                            Button(onClick = { showFoodSearch = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text("Add Ingredient")
                            }
                            Spacer(Modifier.height(16.dp))
                            if (ingredients.isNotEmpty()) {
                                Text("Total: ${totalKcal.roundToInt()} kcal", fontWeight = FontWeight.Bold)
                                Text("Protein: ${totalProtein.roundToInt()}g, Carbs: ${totalCarbs.roundToInt()}g, Fat: ${totalFat.roundToInt()}g", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        2 -> {
                            Text("Give your meal a name so you can reuse it with one tap.", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(
                                value = name, onValueChange = { name = it },
                                label = { Text("Meal Name (e.g. Morning Oats)") }, modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Spacer(Modifier.height(16.dp))
                            Text("Summary: ${ingredients.size} items, ${totalKcal.roundToInt()} kcal", style = MaterialTheme.typography.bodyMedium)
                            Text("Protein: ${totalProtein.roundToInt()}g | Carbs: ${totalCarbs.roundToInt()}g | Fat: ${totalFat.roundToInt()}g", style = MaterialTheme.typography.bodySmall)
                        }
                        3 -> {
                            Text("Where would you like to add \"${name}\" right now?", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(16.dp))
                            MealType.entries.forEach { mt ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedMeal = mt }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedMeal == mt, onClick = { selectedMeal = mt })
                                    Spacer(Modifier.width(8.dp))
                                    Text(mt.title, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Or tap \"Save Only\" to save without adding to today's log.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                1 -> Button(onClick = { step = 2 }, enabled = ingredients.isNotEmpty()) { Text("Next") }
                2 -> Button(onClick = { step = 3 }, enabled = name.isNotBlank()) { Text("Next") }
                3 -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Save only — don't add to today
                        OutlinedButton(
                            onClick = {
                                if (isSaving) return@OutlinedButton
                                val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                                if (uid != null) {
                                    isSaving = true
                                    val itemsList = ingredients.map { tf ->
                                        mapOf(
                                            "id" to tf.id, "name" to tf.name,
                                            "amt" to tf.amount.toString(), "unit" to tf.unit,
                                            "caloriesKcal" to tf.caloriesKcal,
                                            "proteinG" to (tf.proteinG ?: 0.0), "carbsG" to (tf.carbsG ?: 0.0),
                                            "fatG" to (tf.fatG ?: 0.0), "fiberG" to (tf.fiberG ?: 0.0),
                                            "sugarG" to (tf.sugarG ?: 0.0), "saturatedFatG" to (tf.saturatedFatG ?: 0.0),
                                            "sodiumMg" to (tf.sodiumMg ?: 0.0), "potassiumMg" to (tf.potassiumMg ?: 0.0),
                                            "cholesterolMg" to (tf.cholesterolMg ?: 0.0)
                                        )
                                    }
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val newId = com.example.myapplication.data.nutrition.FoodRepositoryImpl.logCustomMeal(name, itemsList)
                                            // isSaveOnly = true → NutritionScreen will NOT open ChooseMealDialog
                                            onSaved(SavedCustomMeal(newId, name, itemsList), MealType.Breakfast, true)
                                            isSaving = false
                                        } catch (e: Exception) { isSaving = false }
                                    }
                                }
                            },
                            enabled = !isSaving
                        ) { Text("Save Only") }
                        // Save & add now
                        Button(
                            onClick = {
                                if (isSaving) return@Button
                                val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                                if (uid != null) {
                                    isSaving = true
                                    val itemsList = ingredients.map { tf ->
                                        mapOf(
                                            "id" to tf.id, "name" to tf.name,
                                            "amt" to tf.amount.toString(), "unit" to tf.unit,
                                            "caloriesKcal" to tf.caloriesKcal,
                                            "proteinG" to (tf.proteinG ?: 0.0), "carbsG" to (tf.carbsG ?: 0.0),
                                            "fatG" to (tf.fatG ?: 0.0), "fiberG" to (tf.fiberG ?: 0.0),
                                            "sugarG" to (tf.sugarG ?: 0.0), "saturatedFatG" to (tf.saturatedFatG ?: 0.0),
                                            "sodiumMg" to (tf.sodiumMg ?: 0.0), "potassiumMg" to (tf.potassiumMg ?: 0.0),
                                            "cholesterolMg" to (tf.cholesterolMg ?: 0.0)
                                        )
                                    }
                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val newId = com.example.myapplication.data.nutrition.FoodRepositoryImpl.logCustomMeal(name, itemsList)
                                            onSaved(SavedCustomMeal(newId, name, itemsList), selectedMeal, false)
                                            isSaving = false
                                        } catch (e: Exception) { isSaving = false }
                                    }
                                }
                            },
                            enabled = !isSaving
                        ) {
                            if (isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            else Text("Save & Add")
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (step > 1) {
                TextButton(onClick = { step -= 1 }) { Text("Back") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
    } // end if (!showFoodSearch) — AlertDialog skrit med iskanjem sestavin

    if (showFoodSearch) {
        ModalBottomSheet(
            onDismissRequest = { showFoodSearch = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            AddFoodSheet(
                meal = MealType.Lunch, // placeholder — ingredient is not bound to a meal
                titleOverride = "Add Ingredient",
                onClose = { showFoodSearch = false },
                onAddTracked = { tf -> 
                    ingredients = ingredients + tf
                    showFoodSearch = false 
                }
            )
        }
    }
}

// -----------------------------------------------------------------------
// NutritionDetailRow — vrstica z label + value za prikaz makrov
// -----------------------------------------------------------------------
@Composable
fun NutritionDetailRow(
    label: String,
    value: String,
    valueColor: Color,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = textColor)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
