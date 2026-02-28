@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.screens

// =====================================================================
// NutritionComponents.kt
// Vsebuje: manjše, ponovno-uporabne UI komponente za NutritionScreen.
//   - WaterControlsRow    — gumbi za dodajanje/odstranjevanje vode
//   - MacroTextRow        — vrstica z makro vrednostmi (P/F/C)
//   - SavedMealChip       — kartica za en shranjen custom meal
//   - MealCard            — kartica za en obrok (Breakfast/Lunch/...)
//   - TrackedFoodItem     — en tracked food item znotraj MealCard
// =====================================================================

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.DrawerBlue
import kotlin.math.roundToInt

// -----------------------------------------------------------------------
// WaterControlsRow — +50ml / -50ml gumbi z debounce zaščito
// -----------------------------------------------------------------------
@Composable
internal fun WaterControlsRow(
    waterConsumedMl: Int,
    textPrimary: Color,
    lastClickState: MutableState<Long>,
    onMinus: (Int) -> Unit,
    onPlus: (Int) -> Unit
) {
    Row(
        modifier = Modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FloatingActionButton(
            onClick = {
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickState.value < 80) return@FloatingActionButton
                lastClickState.value = now
                onMinus(maxOf(0, waterConsumedMl - 50))
            },
            containerColor = Color(0xFF2563EB),
            contentColor = Color.White,
            modifier = Modifier.size(48.dp)
        ) { Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold) }

        Text("💧 50ml", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)

        FloatingActionButton(
            onClick = {
                val now = SystemClock.elapsedRealtime()
                if (now - lastClickState.value < 80) return@FloatingActionButton
                lastClickState.value = now
                onPlus(waterConsumedMl + 50)
            },
            containerColor = Color(0xFF2563EB),
            contentColor = Color.White,
            modifier = Modifier.size(48.dp)
        ) { Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
    }
}

// -----------------------------------------------------------------------
// MacroTextRow — vrstica s P/F/C vrednostmi in barvnim feedbackom
// -----------------------------------------------------------------------
@Composable
internal fun MacroTextRow(
    consumedProtein: Double,
    consumedFat: Double,
    consumedCarbs: Double,
    targetProtein: Int,
    targetFat: Int,
    targetCarbs: Int,
    textPrimary: Color,
    weightUnit: String
) {
    fun macroColor(consumed: Double, target: Int): Color {
        if (target <= 0) return textPrimary
        val deviation = kotlin.math.abs((consumed - target) / target)
        return when {
            deviation <= 0.10 -> Color(0xFF10B981)
            deviation <= 0.20 -> Color(0xFFF59E0B)
            else -> Color(0xFFEF4444)
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Text(
            text = macroLabel("Protein", consumedProtein, targetProtein, weightUnit),
            color = macroColor(consumedProtein, targetProtein),
            fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center
        )
        Text(
            text = macroLabel("Fat", consumedFat, targetFat, weightUnit),
            color = macroColor(consumedFat, targetFat),
            fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center
        )
        Text(
            text = macroLabel("Carbs", consumedCarbs, targetCarbs, weightUnit),
            color = macroColor(consumedCarbs, targetCarbs),
            fontSize = 13.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center
        )
    }
}

// -----------------------------------------------------------------------
// SavedMealChip — kartica za en shranjen custom meal
// -----------------------------------------------------------------------
@Composable
internal fun SavedMealChip(
    meal: SavedCustomMeal,
    textPrimary: Color,
    surfaceVariantColor: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, DrawerBlue)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp, 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(meal.name, fontSize = 14.sp, color = textPrimary, modifier = Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_delete),
                    contentDescription = "Delete", tint = textPrimary
                )
            }
        }
    }
}

// -----------------------------------------------------------------------
// TrackedFoodItem — en food item znotraj meal karte
// -----------------------------------------------------------------------
@Composable
internal fun TrackedFoodItem(
    food: TrackedFood,
    surfaceVariantColor: Color,
    textPrimary: Color,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textPrimary)
                Text(
                    "${food.amount.toInt()} ${food.unit} • ${food.caloriesKcal.roundToInt()} kcal",
                    fontSize = 12.sp, color = Color(0xFF6B7280)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_delete),
                    contentDescription = "Delete", tint = Color(0xFFEF4444)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------
// MealCard — kartica za en obrok (Breakfast / Lunch / Dinner / Snacks)
// -----------------------------------------------------------------------
@Composable
internal fun MealCard(
    mealType: MealType,
    title: String,
    trackedFoods: List<TrackedFood>,
    surfaceVariantColor: Color,
    textPrimary: Color,
    onAddFood: () -> Unit,
    onFoodClick: (TrackedFood) -> Unit,
    onFoodDelete: (TrackedFood) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = surfaceVariantColor),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: title + add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                SmallFloatingActionButton(
                    onClick = onAddFood,
                    containerColor = Color(0xFF1976F6),
                    contentColor = Color.White
                ) {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_input_add),
                        contentDescription = "Add"
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Decorative line separator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFF1976F6), shape = CircleShape)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .padding(start = 8.dp)
                        .background(Color(0xFFCCCCCC), shape = RoundedCornerShape(2.dp))
                )
            }

            Spacer(Modifier.height(10.dp))

            // Food items za ta meal
            trackedFoods.filter { it.meal == mealType }.forEach { food ->
                TrackedFoodItem(
                    food = food,
                    surfaceVariantColor = surfaceVariantColor,
                    textPrimary = textPrimary,
                    onClick = { onFoodClick(food) },
                    onDelete = { onFoodDelete(food) }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------
// HintText — opis pod donut grafom
// -----------------------------------------------------------------------
@Composable
internal fun HintText(text: String) {
    Text(
        text = text,
        color = Color(0xFF6B7280),
        fontSize = 12.sp,
        fontStyle = FontStyle.Italic,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

