@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screens

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.core.view.isVisible
import com.example.myapplication.databinding.ItemTrackedFoodBinding
import com.example.myapplication.databinding.NutritionScreenBinding
import com.example.myapplication.network.FatSecretApi
import com.example.myapplication.network.FoodDetail
import com.example.myapplication.network.FoodSummary
import com.example.myapplication.network.MealFeatures
import com.example.myapplication.network.MealRanker
import com.example.myapplication.ui.theme.DrawerBlue
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// ========== Custom donut view (MORA biti public, ne private) ==========
class DonutProgressView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    var progress: Float = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }

    @ColorInt var baseColor: Int = 0xFFE5E7EB.toInt()
        set(value) { field = value; basePaint.color = value; invalidate() }

    @ColorInt var progressColor: Int = 0xFF1976F6.toInt()
        set(value) { field = value; progressPaint.color = value; invalidate() }

    var thicknessPx: Float = dp(16f)
        set(value) {
            field = value
            basePaint.strokeWidth = value
            progressPaint.strokeWidth = value
            updateRect()
            invalidate()
        }

    var startAngle: Float = 135f
    var sweepAngle: Float = 270f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = thicknessPx
        color = baseColor
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = thicknessPx
        color = progressColor
    }
    private val arcRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val size = if (w == 0 || h == 0) maxOf(w, h) else minOf(w, h)
        val finalSize = if (size == 0) dp(190f).toInt() else size
        setMeasuredDimension(finalSize, finalSize)
        updateRect()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateRect()
    }

    private fun updateRect() {
        val half = thicknessPx / 2f
        arcRect.set(
            paddingLeft + half,
            paddingTop + half,
            width - paddingRight - half,
            height - paddingBottom - half
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(arcRect, startAngle, sweepAngle, false, basePaint)
        canvas.drawArc(arcRect, startAngle, sweepAngle * progress, false, progressPaint)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}

// ========== MODELI & PARSER (ne spreminjamo) ==========

private data class ParsedMacrosForNutrition(
    val calories: Int?,
    val proteinG: Int?,
    val carbsG: Int?,
    val fatG: Int?
)

private fun parseMacroBreakdown(text: String?): ParsedMacrosForNutrition {
    if (text.isNullOrBlank()) return ParsedMacrosForNutrition(null, null, null, null)
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

    return ParsedMacrosForNutrition(calories, protein, carbs, fat)
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

private data class UserPrefs(val likes: List<String> = emptyList())

// ========== MAIN SCREEN ==========

@Composable
fun NutritionScreen(plan: PlanResult? = null) {
    val context = LocalContext.current
    val mealRanker = remember(context) { MealRanker(context) }
    val scope = rememberCoroutineScope()
    var userPrefs by remember { mutableStateOf<UserPrefs?>(null) }
    var showFavDialog by remember { mutableStateOf(false) }
    var showEditFavDialog by remember { mutableStateOf(false) }

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
            userPrefs?.likes?.forEach { like ->
                val cleanQuery = like.trim().replace("\"", "")
                val foods = FatSecretApi.searchFoods(cleanQuery, 1, 20)
                allFoods.addAll(foods)
            }

            if (allFoods.isEmpty()) {
                Toast.makeText(context, "No favourite foods for autofill!", Toast.LENGTH_LONG).show()
                return@launch
            }

            val filteredFoods = allFoods
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
            val scores = mealRanker.rankMeals(featuresList)
            val topFoods = filteredFoods.zip(scores).sortedByDescending { it.second }.take(12)
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

    // DESIGN je v XML; tukaj ga inflatamo in vežemo podatke.
    AndroidViewBinding(factory = NutritionScreenBinding::inflate) {
        // Donut krog – identičen figmi (startAngle 135°, sweep 270°, zaobljeni konci)
        val progress = (consumedKcal.toFloat() / targetCalories.toFloat()).coerceIn(0f, 1f)
        val remaining = kotlin.math.abs(targetCalories - consumedKcal)

        val ringColor = if (consumedKcal > targetCalories) Color(0xFFE11D48) else DrawerBlue
        val grayBg = Color(0xFFE5E7EB)

// Donut: gap spodaj-desno kot na sliki
        donutRing.apply {
            thicknessPx = resources.displayMetrics.density * 16f
            startAngle = 0f          // iz 135f -> 0f (gap se premakne na spodnje-desno)
            sweepAngle = 270f        // ali 300f, če želiš manjši gap (~60°)
            baseColor = grayBg.toArgb()
            progressColor = ringColor.toArgb()
            this.progress = progress
        }

        tvRemaining.text = remaining.toString()
        tvRemainingLabel.text = "Remaining"

        // Makroji – večja pisava je v XML; tu nastavimo besedila
        tvMacrosProtein.text = "Protein: ${consumedProtein.roundToInt()}g"
        tvMacrosFat.text = "Fat: ${consumedFat.roundToInt()}g"
        tvMacrosCarbs.text = "Carbs: ${consumedCarbs.roundToInt()}g"
        tvMacrosSugar.text = "Sugar: \u2013g"

        // Gumbi ADD – odprejo Compose bottom sheet
        btnAddBreakfast.setOnClickListener { sheetMeal = MealType.Breakfast }
        btnAddLunch.setOnClickListener { sheetMeal = MealType.Lunch }
        btnAddDinner.setOnClickListener { sheetMeal = MealType.Dinner }
        btnAddSnacks.setOnClickListener { sheetMeal = MealType.Snacks }

        // Render seznama v sekcije
        fun renderMeal(meal: MealType) {
            val parent = when (meal) {
                MealType.Breakfast -> containerBreakfast
                MealType.Lunch -> containerLunch
                MealType.Dinner -> containerDinner
                MealType.Snacks -> containerSnacks
            }
            parent.removeAllViews()
            val inflater = LayoutInflater.from(root.context)
            val itemsForMeal = trackedFoods.filter { it.meal == meal }
            itemsForMeal.forEach { itx ->
                val row = ItemTrackedFoodBinding.inflate(inflater, parent, false)
                row.tvItemName.text = itx.name
                row.tvItemInfo.text = "${itx.amount.toInt()} ${itx.unit} • ${itx.caloriesKcal.roundToInt()} kcal"
                row.btnDelete.setOnClickListener {
                    trackedFoods = trackedFoods.filterNot { t -> t.id == itx.id }
                }
                parent.addView(row.root)
            }
        }
        renderMeal(MealType.Breakfast)
        renderMeal(MealType.Lunch)
        renderMeal(MealType.Dinner)
        renderMeal(MealType.Snacks)

        tvRecommended.isVisible = true
    }

    // Compose dialogi/sheet ostanejo
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

// ===== Dialogi/sheet (Compose) – nespremenjeno od tu naprej =====

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
                onClick = { if (selectedFoods.size >= 1) onDone(selectedFoods) },
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
            androidx.compose.foundation.layout.Column {
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
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
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
                Text("Selected: ${selectedFoods.size} of 20")
                LazyColumn {
                    items(selectedFoods) { food ->
                        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(food)
                            androidx.compose.foundation.layout.Spacer(Modifier.padding(end = 10.dp))
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

    fun doSearch() {
        scope.launch {
            searching = true
            searchError = null
            runCatching { FatSecretApi.searchFoods(query, 1, 20) }
                .onSuccess { results = it }
                .onFailure { e -> searchError = e.message ?: "Search failed" }
            searching = false
        }
    }

    androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        androidx.compose.foundation.layout.Row(Modifier, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Add food to ${meal.title}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }

        // TextField + gumb Search
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search foods (e.g., chicken breast)") },
                modifier = Modifier.weight(1f),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { doSearch() })
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 6.dp))
            Button(onClick = { doSearch() }) { Text("Search") }
        }

        if (searching) {
            LinearProgressIndicator(Modifier.padding(top = 6.dp), color = DrawerBlue)
        }
        if (searchError != null) {
            Text(searchError!!, color = Color.Red, style = MaterialTheme.typography.bodySmall)
        }

        LazyColumn(
            modifier = Modifier.heightIn(min = 100.dp, max = 420.dp).padding(top = 8.dp)
        ) {
            items(results) { item ->
                androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                            Text(item.description ?: "", maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = {
                            scope.launch {
                                runCatching { FatSecretApi.getFoodDetail(item.id) }
                                    .onSuccess { d -> showAmountDialogFor = d }
                                    .onFailure { e -> searchError = e.message ?: "Failed to load food detail" }
                            }
                        }) { Text("Add") }
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
    val metricUnit = detail.metricServingUnit?.lowercase(Locale.US)?.let { if (it == "g" || it == "ml") it else null }
    var unit by remember(detail) { mutableStateOf(metricUnit ?: "servings") }
    var amountText by remember(detail) { mutableStateOf(if (unit == "servings") "1" else "100") }
    val amount = amountText.toDoubleOrNull()

    val preview = remember(detail, unit, amount) {
        if (amount == null) detail else detail.copy(
            caloriesKcal = when (unit) { "g", "ml" -> (detail.caloriesKcal ?: 0.0) * (amount / 100.0) else -> (detail.caloriesKcal ?: 0.0) * amount },
            proteinG = when (unit) { "g", "ml" -> (detail.proteinG ?: 0.0) * (amount / 100.0) else -> (detail.proteinG ?: 0.0) * amount },
            carbsG = when (unit) { "g", "ml" -> (detail.carbsG ?: 0.0) * (amount / 100.0) else -> (detail.carbsG ?: 0.0) * amount },
            fatG = when (unit) { "g", "ml" -> (detail.fatG ?: 0.0) * (amount / 100.0) else -> (detail.fatG ?: 0.0) * amount }
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
                                proteinG = preview.proteinG, carbsG = preview.carbsG, fatG = preview.fatG
                            )
                        )
                    }
                },
                enabled = amount != null && amount > 0,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = DrawerBlue, contentColor = Color.White
                )
            ) { Text("Add to ${meal.title}") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        title = { Text(detail.name, style = MaterialTheme.typography.titleLarge) },
        text = {
            androidx.compose.foundation.layout.Column {
                if (metricUnit != null) {
                    androidx.compose.foundation.layout.Row {
                        AssistChip(onClick = { unit = metricUnit }, label = { Text(metricUnit.uppercase()) })
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(end = 8.dp))
                        AssistChip(onClick = { unit = "servings" }, label = { Text("Servings") })
                    }
                } else {
                    Text("No metric weight/volume available. Using servings.", fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { txt -> amountText = txt.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.') },
                    singleLine = true,
                    label = { Text(when (unit) { "g" -> "Amount (g)"; "ml" -> "Amount (ml)"; else -> "Amount (servings)" }) },
                    modifier = Modifier
                )

                Text("Calories: ${preview.caloriesKcal?.roundToInt()} kcal")
            }
        }
    )
}