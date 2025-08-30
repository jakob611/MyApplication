@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screens

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.network.FatSecretApi
import com.example.myapplication.network.FoodDetail
import com.example.myapplication.network.FoodSummary
import com.example.myapplication.network.MealRanker
import com.example.myapplication.network.MealFeatures
import com.example.myapplication.ui.theme.DrawerBlue
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.util.Locale


// ========== MODELI & PARSER ==========

data class ParsedMacrosForNutrition(
    val calories: Int?,
    val proteinG: Int?,
    val carbsG: Int?,
    val fatG: Int?
)
fun parseMacroBreakdown(text: String?): ParsedMacros {
    // ... parser telo kot prej ...
    if (text.isNullOrBlank()) return ParsedMacros(null, null, null, null)
    val proteinTotalRe = Regex("""Protein:\s*[\d.]+g\/kg\s*\(([\d.]+)g total\)""", RegexOption.IGNORE_CASE)
    val proteinSimpleRe = Regex("""Protein:\s*([\d.]+)g(?:\b|,)""", RegexOption.IGNORE_CASE)
    val carbsRe = Regex("""Carbs:\s*([\d.]+)g""", RegexOption.IGNORE_CASE)
    val fatRe = Regex("""Fat:\s*([\d.]+)g""", RegexOption.IGNORE_CASE)
    val caloriesRe = Regex("""Calories:\s*([\d.]+)\s*kcal""", RegexOption.IGNORE_CASE)

    val protein = proteinTotalRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
        ?: proteinSimpleRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
    val carbs = carbsRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
    val fat = fatRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
    val calories = caloriesRe.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()

    return ParsedMacros(
        calories = calories,
        proteinG = protein,
        carbsG = carbs,
        fatG = fat
    )
}

private enum class MealType(val title: String) { Breakfast("Breakfast"), Lunch("Lunch"), Dinner("Dinner"), Snacks("Snacks") }

private data class TrackedFood(
    val id: String,
    val name: String,
    val meal: MealType,
    val amount: Double,
    val unit: String,
    val caloriesKcal: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?
)

private data class UserPrefs(
    val likes: List<String> = emptyList()
)

// ========== MAIN SCREEN ==========

@Composable
fun NutritionScreen(plan: PlanResult? = null) {
    val context = LocalContext.current
    val mealRanker = remember { MealRanker(context) }
    val scope = rememberCoroutineScope()
    var userPrefs by remember { mutableStateOf<UserPrefs?>(null) }
    var showFavDialog by remember { mutableStateOf(false) }
    var showEditFavDialog by remember { mutableStateOf(false) }

    // Branje favourites iz Firestore (glavni dokument "users/{uid}")
    LaunchedEffect(Unit) {
        val uid = Firebase.auth.currentUser?.uid
        if (uid != null) {
            Firebase.firestore.collection("users").document(uid)
                .get()
                .addOnSuccessListener { snap ->
                    val likes = (snap.data?.get("likes") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    userPrefs = UserPrefs(likes = likes)
                    if (likes.isEmpty()) showFavDialog = true
                }
        }
    }

    var trackedFoods by remember { mutableStateOf<List<TrackedFood>>(emptyList()) }
    val consumedKcal = trackedFoods.sumOf { it.caloriesKcal.roundToInt() }
    val consumedProtein = trackedFoods.sumOf { it.proteinG ?: 0.0 }
    val consumedCarbs = trackedFoods.sumOf { it.carbsG ?: 0.0 }
    val consumedFat = trackedFoods.sumOf { it.fatG ?: 0.0 }

    val parsedPlanMacros = remember(plan?.algorithmData?.macroBreakdown) {
        parseMacroBreakdown(plan?.algorithmData?.macroBreakdown)
    }
    val targetCalories = parsedPlanMacros.calories ?: plan?.calories ?: 2000
    val targetProtein = parsedPlanMacros.proteinG ?: plan?.protein ?: 100
    val targetCarbs = parsedPlanMacros.carbsG ?: plan?.carbs ?: 200
    val targetFat = parsedPlanMacros.fatG ?: plan?.fat ?: 60

    var sheetMeal by remember { mutableStateOf<MealType?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun autoFillAIWholeFatSecret() {
        scope.launch {
            val allFoods = mutableListOf<FoodSummary>()
            // Za vsako živilo posebej naredi search!
            userPrefs?.likes?.forEach { like ->
                val cleanQuery = like.trim().replace("\"", "")
                val foods = FatSecretApi.searchFoods(cleanQuery, 1, 20)
                allFoods.addAll(foods)
            }

            Log.d("FIREBASE_LIKES", userPrefs?.likes.toString())
            Log.d("ALL_FOODS", allFoods.map { it.name }.toString())

            // Če ni hrane, ne kliči MealRanker!
            if (allFoods.isEmpty()) {
                Toast.makeText(context, "No favourite foods for autofill!", Toast.LENGTH_LONG).show()
                return@launch
            }

            val filteredFoods = allFoods // tukaj filter ni več potreben

            val featuresList = filteredFoods.map { food ->
                val foodDetail = FatSecretApi.getFoodDetail(food.id.toString())
                MealFeatures(
                    kcalDiff = ((foodDetail.caloriesKcal ?: 0.0) - (targetCalories.toDouble() / filteredFoods.size)).toFloat(),
                    proteinDiff = ((foodDetail.proteinG ?: 0.0) - (targetProtein.toDouble() / filteredFoods.size)).toFloat(),
                    carbsDiff = ((foodDetail.carbsG ?: 0.0) - (targetCarbs.toDouble() / filteredFoods.size)).toFloat(),
                    fatDiff = ((foodDetail.fatG ?: 0.0) - (targetFat.toDouble() / filteredFoods.size)).toFloat(),
                    mealType = 0, timeOfDay = 12,
                    vegan = 0, vegetarian = 0, glutenFree = 0, halal = 0,
                    country = 0, like = 0, recentUsage = 0
                )
            }
            Log.d("API_FOOD_DEBUG", "All foods count: ${allFoods.size}")
            val scores = mealRanker.rankMeals(featuresList)
            val topFoods = filteredFoods.zip(scores)
                .sortedByDescending { it.second }
                .take(12)
            trackedFoods = topFoods.mapIndexed { i, (food, _) ->
                val detail = FatSecretApi.getFoodDetail(food.id)
                TrackedFood(
                    id = food.id,
                    name = food.name,
                    meal = MealType.values()[i % 4],
                    amount = 100.0,
                    unit = "g",
                    caloriesKcal = detail.caloriesKcal ?: 0.0,
                    proteinG = detail.proteinG ?: 0.0,
                    carbsG = detail.carbsG ?: 0.0,
                    fatG = detail.fatG ?: 0.0
                )
            }
        }
    }
    Surface(color = Color(0xFFF3F4F6)) {
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    TopCard(
                        consumedKcal = consumedKcal,
                        targetKcal = targetCalories,
                        consumedProtein = consumedProtein,
                        targetProtein = targetProtein,
                        consumedCarbs = consumedCarbs,
                        targetCarbs = targetCarbs,
                        consumedFat = consumedFat,
                        targetFat = targetFat
                    )
                }
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = { autoFillAIWholeFatSecret() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White)
                        ) { Text("AI Autofill (FatSecret)") }
                        Button(
                            onClick = { showEditFavDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB), contentColor = Color.White)
                        ) { Text("My favourites") }
                    }
                }
                items(MealType.values()) { meal ->
                    val itemsForMeal = trackedFoods.filter { it.meal == meal }
                    MealCard(
                        title = meal.title,
                        items = itemsForMeal,
                        onAdd = { sheetMeal = meal },
                        onDeleteItem = { del -> trackedFoods = trackedFoods.filterNot { it.id == del.id } }
                    )
                }
            }

            if (showFavDialog) {
                FavouritesDialog(
                    initial = userPrefs?.likes ?: emptyList(),
                    onDone = { selectedFoods ->
                        val uid = Firebase.auth.currentUser?.uid
                        if (uid != null) {
                            Firebase.firestore.collection("users").document(uid)
                                .set(mapOf("likes" to selectedFoods), SetOptions.merge())
                                .addOnSuccessListener {
                                    userPrefs = UserPrefs(likes = selectedFoods)
                                    showFavDialog = false
                                }
                        }
                    }
                )
            }

            if (showEditFavDialog) {
                FavouritesDialog(
                    initial = userPrefs?.likes ?: emptyList(),
                    onDone = { selectedFoods ->
                        val uid = Firebase.auth.currentUser?.uid
                        if (uid != null) {
                            Firebase.firestore.collection("users").document(uid)
                                .set(mapOf("likes" to selectedFoods), SetOptions.merge())
                                .addOnSuccessListener {
                                    userPrefs = UserPrefs(likes = selectedFoods)
                                    showEditFavDialog = false
                                }
                        }
                    },
                    onCancel = { showEditFavDialog = false }
                )
            }

            if (sheetMeal != null) {
                ModalBottomSheet(
                    onDismissRequest = { sheetMeal = null },
                    sheetState = sheetState,
                    containerColor = Color.White,
                    tonalElevation = 6.dp
                ) {
                    AddFoodSheet(
                        meal = sheetMeal!!,
                        onClose = { scope.launch { sheetState.hide() }.invokeOnCompletion { sheetMeal = null } },
                        onAddTracked = { tf -> trackedFoods = trackedFoods + tf }
                    )
                }
            }
        }
    }
}

// ===== UI helperji =====

@Composable
private fun TopCard(
    consumedKcal: Int, targetKcal: Int,
    consumedProtein: Double, targetProtein: Int,
    consumedCarbs: Double, targetCarbs: Int,
    consumedFat: Double, targetFat: Int
) {
    val ringColor = if (consumedKcal > targetKcal) Color(0xFFE11D48) else DrawerBlue
    val labelColor = if (consumedKcal > targetKcal) Color(0xFFE11D48) else Color(0xFF6B7280)
    val remaining = kotlin.math.abs(targetKcal - consumedKcal)
    val progress = (consumedKcal.toFloat() / targetKcal.toFloat()).coerceIn(0f, 1f)
    val titleText = if (consumedKcal > targetKcal) "Over by" else "Remaining"
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(progress = { 1f }, color = Color(0xFFE5E7EB), strokeWidth = 12.dp)
                    CircularProgressIndicator(progress = { progress }, color = ringColor, trackColor = Color.Transparent, strokeWidth = 12.dp, modifier = Modifier.matchParentSize())
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(remaining.toString(), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = ringColor)
                        Text(titleText, style = MaterialTheme.typography.bodySmall, color = labelColor)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Calories: $consumedKcal / $targetKcal kcal", style = MaterialTheme.typography.bodySmall, color = labelColor, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroPill("Protein", consumedProtein, targetProtein, "g")
                MacroPill("Carbs", consumedCarbs, targetCarbs, "g")
                MacroPill("Fat", consumedFat, targetFat, "g")
            }
        }
    }
}

@Composable
private fun MacroPill(label: String, consumed: Double, target: Int, unit: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text("$label:", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4B5563))
        val txt = if (target > 0) "${consumed.roundToInt()}/$target $unit" else "${consumed.roundToInt()} $unit"
        Text(txt, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF111827))
    }
}

@Composable
private fun MealCard(
    title: String,
    items: List<TrackedFood>,
    onAdd: () -> Unit,
    onDeleteItem: (TrackedFood) -> Unit
) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFFF1F5F9)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF111827), modifier = Modifier.weight(1f))
                FilledIconButton(onClick = onAdd, shape = CircleShape, colors = IconButtonDefaults.filledIconButtonColors(containerColor = DrawerBlue, contentColor = Color.White)) {
                    Icon(Icons.Filled.Add, contentDescription = "Add")
                }
            }
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { itx ->
                        Surface(shape = RoundedCornerShape(10.dp), color = Color.White) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(itx.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF111827))
                                    Text("${itx.amount.toInt()} ${itx.unit} • ${itx.caloriesKcal.roundToInt()} kcal", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                                }
                                IconButton(onClick = { onDeleteItem(itx) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color(0xFFF15A5A))
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text("No items added yet.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
            }
        }
    }
}

@Composable
fun FavouritesDialog(
    initial: List<String>,
    onDone: (List<String>) -> Unit,
    onCancel: (() -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<FoodSummary>>(emptyList()) }
    var selectedFoods by remember { mutableStateOf(initial) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { onCancel?.invoke() },
        confirmButton = {
            Button(
                onClick = { if (selectedFoods.size >= 1) onDone(selectedFoods) }, // Lahko shraniš že ko je samo en
                enabled = selectedFoods.size >= 1
            ) { Text("Save favourites") }
        },
        dismissButton = {
            if (onCancel != null) {
                TextButton(onClick = { onCancel() }) { Text("Cancel") }
            }
        },
        title = { Text("Choose your favourite foods") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search foods") }
                )
                Button(onClick = {
                    scope.launch {
                        val results = FatSecretApi.searchFoods(query, 1, 20)
                        searchResults = results
                    }
                }) { Text("Search") }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(searchResults) { food ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selectedFoods.contains(food.name),
                                onCheckedChange = { checked ->
                                    if (checked && selectedFoods.size < 20)
                                        selectedFoods = selectedFoods + food.name
                                    else if (!checked)
                                        selectedFoods = selectedFoods - food.name
                                }
                            )
                            Text(food.name)
                        }
                    }
                }
                // Seznam izbranih živil, vsak ima možnost za izbris (gumb X)
                Text("Selected: ${selectedFoods.size} of 20")
                LazyColumn {
                    items(selectedFoods) { food ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(food)
                            Spacer(Modifier.width(10.dp))
                            IconButton(onClick = {
                                selectedFoods = selectedFoods - food
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun AddFoodSheet(meal: MealType, onClose: () -> Unit, onAddTracked: (TrackedFood) -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<FoodSummary>>(emptyList()) }
    var showAmountDialogFor by remember { mutableStateOf<FoodDetail?>(null) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Add food to ${meal.title}", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Color(0xFF111827))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Search foods (e.g., chicken breast)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                scope.launch {
                    searching = true
                    searchError = null
                    runCatching { FatSecretApi.searchFoods(query, 1, 20) }
                        .onSuccess { results = it }
                        .onFailure { searchError = it.message ?: "Search failed" }
                    searching = false
                }
            }),
            trailingIcon = {
                TextButton(onClick = {
                    scope.launch {
                        searching = true
                        searchError = null
                        runCatching { FatSecretApi.searchFoods(query, 1, 20) }
                            .onSuccess { results = it }
                            .onFailure { searchError = it.message ?: "Search failed" }
                        searching = false
                    }
                }) { Text("Search") }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = DrawerBlue)
        }
        if (searchError != null) {
            Text(searchError!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(results) { item ->
                Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFF8FAFC), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = Color(0xFF111827))
                            Text(item.description ?: "", maxLines = 2, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                        }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching { FatSecretApi.getFoodDetail(item.id) }
                                        .onSuccess { d -> showAmountDialogFor = d }
                                        .onFailure { e -> searchError = e.message ?: "Failed to load food detail" }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DrawerBlue, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) { Text("Add") }
                    }
                }
            }
        }
    }

    showAmountDialogFor?.let { detail ->
        AmountDialog(detail = detail, meal = meal, onCancel = { showAmountDialogFor = null }) { tracked ->
            onAddTracked(tracked); showAmountDialogFor = null
        }
    }
}

@Composable
private fun AmountDialog(detail: FoodDetail, meal: MealType, onCancel: () -> Unit, onConfirm: (TrackedFood) -> Unit) {
    val metricUnit = detail.metricServingUnit?.lowercase(Locale.US)?.let {
        when (it) { "g", "ml" -> it; else -> null }
    }
    var unit by remember(detail) { mutableStateOf(metricUnit ?: "servings") }
    var amountText by remember(detail) { mutableStateOf(if (unit == "servings") "1" else "100") }
    val amount = amountText.toDoubleOrNull()

    val preview = remember(detail, unit, amount) {
        if (amount == null) detail else detail.copy(
            caloriesKcal = when (unit) {
                "g", "ml" -> (detail.caloriesKcal ?: 0.0) * (amount / 100.0)
                else -> (detail.caloriesKcal ?: 0.0) * amount
            },
            proteinG = when (unit) {
                "g", "ml" -> (detail.proteinG ?: 0.0) * (amount / 100.0)
                else -> (detail.proteinG ?: 0.0) * amount
            },
            carbsG = when (unit) {
                "g", "ml" -> (detail.carbsG ?: 0.0) * (amount / 100.0)
                else -> (detail.carbsG ?: 0.0) * amount
            },
            fatG = when (unit) {
                "g", "ml" -> (detail.fatG ?: 0.0) * (amount / 100.0)
                else -> (detail.fatG ?: 0.0) * amount
            }
        )
    }

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            Button(
                onClick = {
                    if (amount != null && amount > 0) {
                        onConfirm(
                            TrackedFood(
                                id = java.util.UUID.randomUUID().toString(),
                                name = detail.name,
                                meal = meal,
                                amount = amount,
                                unit = unit,
                                caloriesKcal = preview.caloriesKcal ?: 0.0,
                                proteinG = preview.proteinG,
                                carbsG = preview.carbsG,
                                fatG = preview.fatG
                            )
                        )
                    }
                },
                enabled = amount != null && amount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = DrawerBlue, contentColor = Color.White)
            ) { Text("Add to ${meal.title}") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        title = { Text(detail.name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (metricUnit != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = { unit = metricUnit }, label = { Text(metricUnit.uppercase()) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (unit == metricUnit) DrawerBlue else Color(0xFFE5E7EB),
                                labelColor = if (unit == metricUnit) Color.White else Color.Black
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        AssistChip(
                            onClick = { unit = "servings" }, label = { Text("Servings") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (unit == "servings") DrawerBlue else Color(0xFFE5E7EB),
                                labelColor = if (unit == "servings") Color.White else Color.Black
                            )
                        )
                    }
                } else {
                    Text("No metric weight/volume available. Using servings.", color = Color(0xFF6B7280), fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { txt -> amountText = txt.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.replace(',', '.') },
                    singleLine = true,
                    label = { Text(when (unit) { "g" -> "Amount (g)"; "ml" -> "Amount (ml)"; else -> "Amount (servings)" }) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Preview for $amountText $unit", fontWeight = FontWeight.SemiBold)
                    Text("Calories: ${preview.caloriesKcal?.roundToInt()} kcal")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Protein: ${preview.proteinG?.let { "%.1f".format(it) } ?: "-"} g")
                        Text("Carbs: ${preview.carbsG?.let { "%.1f".format(it) } ?: "-"} g")
                        Text("Fat: ${preview.fatG?.let { "%.1f".format(it) } ?: "-"} g")
                    }
                }
            }
        }
    )
}