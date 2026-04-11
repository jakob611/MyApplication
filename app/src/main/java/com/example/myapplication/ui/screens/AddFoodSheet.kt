package com.example.myapplication.ui.screens

// =====================================================================
// AddFoodSheet.kt
// Vsebuje: iskanje hrane in receptov znotraj Nutrition ekrana.
//   - AddFoodSheet          — bottom sheet za iskanje in dodajanje hrane
//   - RecipesSearchSection  — iskanje receptov (FatSecret)
//   - RecipeCard            — prikaz enega recepta v listi
//   - RecipeDetailDialog    — podrobnosti recepta
// =====================================================================

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.example.myapplication.network.FatSecretApi
import com.example.myapplication.network.FoodDetail
import com.example.myapplication.network.FoodSummary
import com.example.myapplication.network.OpenFoodFactsProduct
import com.example.myapplication.network.RecipeDetail
import com.example.myapplication.network.RecipeSummary
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalSoftwareKeyboardController // Added import
import com.example.myapplication.persistence.RecentFoodStore // Added import
import androidx.compose.ui.platform.LocalContext // Added import

// -----------------------------------------------------------------------
// AddFoodSheet — bottom sheet za iskanje in dodajanje hrane
// -----------------------------------------------------------------------
@Composable
internal fun AddFoodSheet(    meal: MealType,
    onClose: () -> Unit,
    onAddTracked: (TrackedFood) -> Unit,
    scannedProduct: Pair<OpenFoodFactsProduct, String>? = null,
    onProductConsumed: () -> Unit = {},
    isImperial: Boolean = false,
    titleOverride: String? = null // Added parameter
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current // Added controller
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<FoodSummary>>(emptyList()) }
    var showAmountDialogFor by remember { mutableStateOf<FoodDetail?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var clickedFoodSummary by remember { mutableStateOf<FoodSummary?>(null) } // Track which was clicked

    // Load recent foods immediately if query is empty
    LaunchedEffect(Unit) {
        if (query.isEmpty() && !hasSearched && scannedProduct == null) {
            results = RecentFoodStore.getRecentFoods(context)
        }
    }

    // Avtomatsko prikaži scanned product
    LaunchedEffect(scannedProduct) {
        if (scannedProduct != null) {
            val (product, barcode) = scannedProduct
            showAmountDialogFor = FoodDetail(
                id = barcode,
                name = product.productName ?: "Scanned Product",
                caloriesKcal = product.nutriments?.energyKcal100g,
                proteinG = product.nutriments?.proteins100g,
                carbsG = product.nutriments?.carbohydrates100g,
                fatG = product.nutriments?.fat100g,
                servingDescription = "100 g",
                numberOfUnits = 100.0,
                measurementDescription = "g",
                metricServingAmount = 100.0,
                metricServingUnit = "g",
                fiberG = product.nutriments?.fiber100g,
                sugarG = product.nutriments?.sugars100g,
                saturatedFatG = product.nutriments?.saturatedFat100g,
                sodiumMg = product.nutriments?.sodium100g?.times(1000),
                potassiumMg = product.nutriments?.potassium100g?.times(1000),
                cholesterolMg = product.nutriments?.cholesterol100g?.times(1000)
            )
        }
    }

    var autocompleteSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    fun doSearch() {
        keyboardController?.hide() // Hide keyboard
        autocompleteSuggestions = emptyList()
        scope.launch {
            searching = true; searchError = null; hasSearched = true
            runCatching { com.example.myapplication.data.nutrition.FoodRepositoryImpl.searchFoodByName(query, 20) }
                .onSuccess { results = it }
                .onFailure { e -> searchError = e.message ?: "Search failed" }
            searching = false
        }
    }

    val textColor = MaterialTheme.colorScheme.onSurface
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(titleOverride ?: "Add food to ${meal.title}", // Use override
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), color = textColor)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = textColor) }
        }

        // Autocomplete
        LaunchedEffect(query) {
            if (query.length >= 2 && !hasSearched) {
                runCatching {
                    autocompleteSuggestions = com.example.myapplication.data.nutrition.FoodRepositoryImpl.getFoodAutocomplete(query, 5)
                }.onFailure { autocompleteSuggestions = emptyList() }
            } else {
                autocompleteSuggestions = emptyList()
            }
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search foods (e.g., chicken breast)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
                    modifier = Modifier.weight(1f).height(50.dp),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor, unfocusedTextColor = textColor,
                        cursorColor = MaterialTheme.colorScheme.tertiary, focusedContainerColor = surfaceColor,
                        unfocusedContainerColor = surfaceColor, focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    textStyle = TextStyle(fontSize = 14.sp)
                )
                Spacer(Modifier.width(6.dp))
                Button(onClick = { doSearch() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.height(50.dp)) {
                    Text("Search", fontSize = 14.sp)
                }
            }
            if (autocompleteSuggestions.isNotEmpty()) {
                Surface(modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        autocompleteSuggestions.forEach { suggestion ->
                            TextButton(onClick = { query = suggestion; doSearch() },
                                modifier = Modifier.fillMaxWidth()) {
                                Text(suggestion, modifier = Modifier.fillMaxWidth(), color = textColor)
                            }
                        }
                    }
                }
            }
            
            // QUICK ADD P2 Feature
            if (query.isEmpty() && !hasSearched && scannedProduct == null) {
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Banana đźŤ", "Apple đźŤŽ", "Egg đźĄš").forEach { food ->
                        OutlinedButton(
                            onClick = {
                                query = food.split(" ")[0]
                                doSearch()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(food, fontSize = 12.sp, color = textColor)
                        }
                    }
                }
            }
        }

        if (searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = MaterialTheme.colorScheme.tertiary)
        searchError?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }

        if (hasSearched && !searching && results.isEmpty() && searchError == null && scannedProduct == null) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 32.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Text("No items found", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = textColor)
                    Text("Try searching with different keywords",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        LazyColumn(modifier = Modifier.heightIn(min = 100.dp, max = 420.dp).padding(top = 8.dp)) {
            if (!hasSearched && query.isEmpty() && results.isNotEmpty()) {
                item {
                    Text(
                        text = "Recent Foods",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            }
            items(items = results, key = { it.id }) { item ->
                Surface(shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp) // Added slight padding
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        item.imageUrl?.let { imageUrl ->
                            Image(painter = rememberAsyncImagePainter(imageUrl),
                                contentDescription = item.name,
                                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(item.name, style = MaterialTheme.typography.titleSmall, color = textColor)
                            Text(item.description ?: "", maxLines = 2,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = {
                            clickedFoodSummary = item // Save this for recent cache
                            scope.launch {
                                runCatching { com.example.myapplication.data.nutrition.FoodRepositoryImpl.getFoodDetail(item.id) }
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
        val barcodeToPass = if (scannedProduct != null && detail.id == scannedProduct.second)
            scannedProduct.second else null
        AmountDialog(
            detail = detail, meal = meal, barcode = barcodeToPass,
            onCancel = { showAmountDialogFor = null }, isImperial = isImperial
        ) { tracked ->
            // Update Recent Foods
            clickedFoodSummary?.let { RecentFoodStore.addRecentFood(context, it) }
            
            onAddTracked(tracked)
            showAmountDialogFor = null
            clickedFoodSummary = null
        }
    }
}

// -----------------------------------------------------------------------
// RecipesSearchSection — iskanje receptov
// -----------------------------------------------------------------------
@Composable
fun RecipesSearchSection(
    onScanBarcode: () -> Unit = {},
    onOpenEAdditives: () -> Unit = {},
    userProfile: com.example.myapplication.data.UserProfile
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<RecipeSummary>>(emptyList()) }
    var selectedRecipe by remember { mutableStateOf<RecipeDetail?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var hasSearched by remember { mutableStateOf(false) }

    val textColor = MaterialTheme.colorScheme.onBackground
    val surfaceColor = MaterialTheme.colorScheme.surface

    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    fun doSearch() {
        if (query.isBlank()) return
        keyboardController?.hide() // Auto-hide keyboard
        scope.launch {
            searching = true; errorMsg = null; hasSearched = true
            runCatching { com.example.myapplication.data.nutrition.FoodRepositoryImpl.searchRecipes(query, 20) }
                .onSuccess { results = it; Log.d("RecipesSearch", "Found ${it.size} recipes") }
                .onFailure { e -> errorMsg = e.message ?: "Search failed"; Log.e("RecipesSearch", "Failed", e) }
            searching = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        // Gumba: Scan Barcode + E-Additives
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onScanBarcode, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(painter = painterResource(android.R.drawable.ic_menu_camera),
                        contentDescription = "Scan", tint = MaterialTheme.colorScheme.background, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text("Scan Barcode", color = MaterialTheme.colorScheme.background, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
            Button(onClick = onOpenEAdditives, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                shape = RoundedCornerShape(12.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🧪", fontSize = 24.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("E-Additives", color = MaterialTheme.colorScheme.background, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }

        Text("Recommended Recipes", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = textColor, modifier = Modifier.padding(vertical = 8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Search recipes (e.g., Chicken Salad)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
                modifier = Modifier.weight(1f).height(50.dp),
                keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor, unfocusedTextColor = textColor,
                    cursorColor = MaterialTheme.colorScheme.tertiary, focusedContainerColor = surfaceColor,
                    unfocusedContainerColor = surfaceColor, focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                textStyle = TextStyle(fontSize = 14.sp)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { doSearch() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                shape = RoundedCornerShape(16.dp), modifier = Modifier.height(50.dp)) {
                Text("Search", fontSize = 14.sp)
            }
        }

        if (searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = MaterialTheme.colorScheme.tertiary)
        errorMsg?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }

        if (hasSearched && !searching && results.isEmpty() && errorMsg == null) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 32.sp, modifier = Modifier.padding(bottom = 8.dp))
                    Text("No items found", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = textColor)
                    Text("Try searching with different keywords",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        if (results.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                results.forEach { recipe ->
                    RecipeCard(recipe = recipe,
                        onClick = {
                            scope.launch {
                                runCatching { com.example.myapplication.data.nutrition.FoodRepositoryImpl.getRecipeDetail(recipe.id) }
                                    .onSuccess { selectedRecipe = it }
                            }
                        },
                        userProfile = userProfile)
                }
            }
        }
    }

    selectedRecipe?.let { recipe ->
        RecipeDetailDialog(recipe = recipe, onDismiss = { selectedRecipe = null }, userProfile = userProfile)
    }
}

// -----------------------------------------------------------------------
// RecipeCard — prikaz recepta v listi
// -----------------------------------------------------------------------
@Composable
private fun RecipeCard(
    recipe: RecipeSummary,
    onClick: () -> Unit,
    userProfile: com.example.myapplication.data.UserProfile
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            recipe.imageUrl?.let { imageUrl ->
                Image(painter = rememberAsyncImagePainter(imageUrl), contentDescription = recipe.name,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(recipe.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                recipe.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    recipe.caloriesKcal?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔥", fontSize = 14.sp); Spacer(Modifier.width(3.dp))
                            Text("$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                    recipe.proteinG?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🥩", fontSize = 13.sp); Spacer(Modifier.width(3.dp))
                            Text(formatMacroWeight(it, userProfile.weightUnit), style = MaterialTheme.typography.labelSmall, color = textColor)
                        }
                    }
                    recipe.carbsG?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🍞", fontSize = 13.sp); Spacer(Modifier.width(3.dp))
                            Text(formatMacroWeight(it, userProfile.weightUnit), style = MaterialTheme.typography.labelSmall, color = textColor)
                        }
                    }
                    recipe.fatG?.let {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🥑", fontSize = 13.sp); Spacer(Modifier.width(3.dp))
                            Text(formatMacroWeight(it, userProfile.weightUnit), style = MaterialTheme.typography.labelSmall, color = textColor)
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Filled.ChevronRight, "View details", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(28.dp))
        }
    }
}

// -----------------------------------------------------------------------
// RecipeDetailDialog — podrobnosti recepta
// -----------------------------------------------------------------------
@Composable
private fun RecipeDetailDialog(
    recipe: RecipeDetail,
    onDismiss: () -> Unit,
    userProfile: com.example.myapplication.data.UserProfile
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.tertiary)) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Column {
                Text(recipe.name, color = textColor, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                recipe.description?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Info badges
                item {
                    Surface(shape = MaterialTheme.shapes.medium, color = surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            recipe.caloriesKcal?.let { InfoBadge("🔥", "$it", "kcal", MaterialTheme.colorScheme.tertiary) }
                            recipe.servings?.let { InfoBadge("🍽️", "$it", "servings", textColor) }
                            recipe.prepTimeMin?.let { InfoBadge("⏱️", "$it", "prep min", textColor) }
                            recipe.cookTimeMin?.let { InfoBadge("🔥", "$it", "cook min", textColor) }
                        }
                    }
                }
                // Macros
                if (recipe.proteinG != null || recipe.carbsG != null || recipe.fatG != null) {
                    item {
                        Surface(shape = MaterialTheme.shapes.medium, color = surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                recipe.proteinG?.let { InfoBadge("🥩", formatMacroWeight(it, userProfile.weightUnit), "Protein", textColor) }
                                recipe.carbsG?.let { InfoBadge("🍞", formatMacroWeight(it, userProfile.weightUnit), "Carbs", textColor) }
                                recipe.fatG?.let { InfoBadge("🥑", formatMacroWeight(it, userProfile.weightUnit), "Fat", textColor) }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
                // Ingredients
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📝", fontSize = 20.sp); Spacer(Modifier.width(6.dp))
                        Text("Ingredients", fontWeight = FontWeight.Bold, color = textColor, fontSize = 18.sp)
                    }
                }
                item {
                    if (recipe.ingredients.isNullOrEmpty()) {
                        Text("No ingredients available", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Surface(shape = MaterialTheme.shapes.medium, color = surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                recipe.ingredients.forEach { ingredient ->
                                    Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                                        Text("•", color = MaterialTheme.colorScheme.tertiary, fontSize = 16.sp, modifier = Modifier.padding(end = 6.dp))
                                        Text(ingredient, color = textColor, fontSize = 14.sp, lineHeight = 20.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
                // Directions
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("👨‍🍳", fontSize = 20.sp); Spacer(Modifier.width(6.dp))
                        Text("Directions", fontWeight = FontWeight.Bold, color = textColor, fontSize = 18.sp)
                    }
                }
                item {
                    if (recipe.directions.isNullOrEmpty()) {
                        Text("No directions available", color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.padding(start = 8.dp))
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recipe.directions.forEachIndexed { index, direction ->
                                Surface(shape = MaterialTheme.shapes.medium, color = surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp)) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("${index + 1}", color = MaterialTheme.colorScheme.background, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Text(direction.removePrefix("${index + 1}. "), color = textColor,
                                            fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge
    )
}

// Pomožen composable za info badge v receptu
@Composable
private fun InfoBadge(emoji: String, value: String, label: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 24.sp)
        Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
