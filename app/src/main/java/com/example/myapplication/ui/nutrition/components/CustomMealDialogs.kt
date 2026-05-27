@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.myapplication.ui.nutrition.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.nutrition.MacroTotals
import com.example.myapplication.ui.nutrition.NutritionViewModel
import com.example.myapplication.ui.screens.*
import com.example.myapplication.utils.HapticFeedback
import com.example.myapplication.widget.QuickMealWidgetProvider
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.util.Locale

// -----------------------------------------------------------------------
// CustomMealSection — "MAKE CUSTOM MEALS" gumb + shranjeni custom obroki
//                    + vsi dialogi za upravljanje custom obrokov
//                    + "Other Macros" dialog
// -----------------------------------------------------------------------
@Composable
fun CustomMealSection(
    customMeals: List<SavedCustomMeal>,
    textPrimary: Color,
    surfaceVariantColor: Color,
    context: Context,
    nutritionViewModel: NutritionViewModel,
    trackedFoods: List<TrackedFood>,
    onTrackedFoodsChange: (List<TrackedFood>) -> Unit,
    onShowMessage: (String) -> Unit,
    consumedMacros: MacroTotals,
    weightUnit: String,
    showOtherMacros: MutableState<Boolean>
) {
    // ── Dialog/state management (lokalno) ────────────────────────────────
    val showMakeCustom     = remember { mutableStateOf(false) }
    val pendingCustomMeal  = remember { mutableStateOf<SavedCustomMeal?>(null) }
    val askWhereToAdd      = remember { mutableStateOf(false) }
    val chooseMealForCustom = remember { mutableStateOf(MealType.Breakfast) }
    val confirmDelete      = remember { mutableStateOf<SavedCustomMeal?>(null) }

    // ── "MAKE CUSTOM MEALS" gumb ─────────────────────────────────────────
    Button(
        onClick = {
            HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.CLICK)
            showMakeCustom.value = true
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            "MAKE CUSTOM MEALS",
            color = MaterialTheme.colorScheme.background,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }

    Spacer(Modifier.height(8.dp))

    // ── Shranjeni custom obroki ──────────────────────────────────────────
    customMeals.forEach { meal ->
        SavedMealChip(
            meal = meal,
            textPrimary = textPrimary,
            surfaceVariantColor = surfaceVariantColor,
            onClick = { pendingCustomMeal.value = meal; askWhereToAdd.value = true },
            onDelete = { confirmDelete.value = meal }
        )
    }

    // ── MakeCustomMealsDialog ────────────────────────────────────────────
    if (showMakeCustom.value) {
        MakeCustomMealsDialog(
            onDismiss = { showMakeCustom.value = false },
            onSaved = { saved, mealType, isSaveOnly ->
                if (isSaveOnly) {
                    showMakeCustom.value = false
                    Toast.makeText(context, "Meal Saved", Toast.LENGTH_SHORT).show()
                } else {
                    pendingCustomMeal.value = saved
                    chooseMealForCustom.value = mealType
                    showMakeCustom.value = false
                    askWhereToAdd.value = true
                }
            }
        )
    }

    // ── ChooseMealDialog (za custom meal → dodaj v obrok) ────────────────
    if (askWhereToAdd.value && pendingCustomMeal.value != null) {
        ChooseMealDialog(
            selected = chooseMealForCustom.value,
            onCancel = {
                askWhereToAdd.value = false
                pendingCustomMeal.value = null
            },
            onConfirmAsync = { mealChosen ->
                val cm = pendingCustomMeal.value!!
                val scope = MainScope()
                scope.launch {
                    try {
                        val items = nutritionViewModel.getCustomMealItems(cm.id)
                        if (items == null) {
                            Toast.makeText(context, "Meal not found", Toast.LENGTH_SHORT).show()
                        } else {
                            val newItems = items.mapNotNull { m ->
                                val name  = m["name"] as? String ?: return@mapNotNull null
                                val amtStr = m["amt"] as? String ?: return@mapNotNull null
                                val amt   = amtStr.toDoubleOrNull() ?: 1.0
                                val unit  = m["unit"] as? String ?: "servings"
                                TrackedFood(
                                    id            = java.util.UUID.randomUUID().toString(),
                                    name          = name,
                                    meal          = mealChosen,
                                    amount        = amt,
                                    unit          = unit,
                                    caloriesKcal  = (m["caloriesKcal"] as? Number)?.toDouble() ?: 0.0,
                                    proteinG      = (m["proteinG"] as? Number)?.toDouble() ?: 0.0,
                                    carbsG        = (m["carbsG"] as? Number)?.toDouble() ?: 0.0,
                                    fatG          = (m["fatG"] as? Number)?.toDouble() ?: 0.0,
                                    fiberG        = (m["fiberG"] as? Number)?.toDouble(),
                                    sugarG        = (m["sugarG"] as? Number)?.toDouble(),
                                    saturatedFatG = (m["saturatedFatG"] as? Number)?.toDouble(),
                                    sodiumMg      = (m["sodiumMg"] as? Number)?.toDouble(),
                                    potassiumMg   = (m["potassiumMg"] as? Number)?.toDouble(),
                                    cholesterolMg = (m["cholesterolMg"] as? Number)?.toDouble()
                                )
                            }
                            if (newItems.isNotEmpty()) {
                                onTrackedFoodsChange(trackedFoods + newItems)
                                onShowMessage("Added custom meal: ${cm.name}")
                            } else {
                                Toast.makeText(context, "No items found in custom meal.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to load meal: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        pendingCustomMeal.value = null
                        askWhereToAdd.value = false
                    }
                }
                true
            }
        )
    }

    // ── Potrdi brisanje custom meala ─────────────────────────────────────
    confirmDelete.value?.let { mealToDelete ->
        AlertDialog(
            onDismissRequest = { confirmDelete.value = null },
            confirmButton = {
                Button(onClick = {
                    nutritionViewModel.deleteCustomMealAsync(mealToDelete.id) {
                        confirmDelete.value = null
                        QuickMealWidgetProvider.forceRefresh(context)
                    }
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete.value = null }) { Text("Cancel") }
            },
            title = { Text("Delete custom meal?") },
            text = { Text("This will remove '${mealToDelete.name}' from saved custom meals.") }
        )
    }

    // ── Other Macros dialog ──────────────────────────────────────────────
    if (showOtherMacros.value) {
        val textColor = MaterialTheme.colorScheme.onSurface
        AlertDialog(
            onDismissRequest = { showOtherMacros.value = false },
            confirmButton = {
                TextButton(onClick = { showOtherMacros.value = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            title = {
                Text("Other Macros", fontWeight = FontWeight.Bold, color = materialColor())
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            NutritionDetailRow("🌾 Fiber",
                                formatMacroWeight(consumedMacros.fiber, weightUnit), Color(0xFF10B981), textColor)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            NutritionDetailRow("🍬 Sugar",
                                formatMacroWeight(consumedMacros.sugar, weightUnit), Color(0xFFEC4899), textColor)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            NutritionDetailRow("🥓 Saturated Fat",
                                formatMacroWeight(consumedMacros.satFat, weightUnit), Color(0xFFEF4444), textColor)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            NutritionDetailRow("🧂 Sodium",
                                String.format(Locale.US, "%.0f mg", consumedMacros.sodium), Color(0xFFF59E0B), textColor)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            NutritionDetailRow("🍌 Potassium",
                                String.format(Locale.US, "%.0f mg", consumedMacros.potassium), MaterialTheme.colorScheme.primary, textColor)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            NutritionDetailRow("🥚 Cholesterol",
                                String.format(Locale.US, "%.0f mg", consumedMacros.cholesterol), Color(0xFF8B5CF6), textColor)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "These values are calculated from all foods consumed today.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

// Helper — lokalni alias za barvni dostop v lambda-i
@Composable
private fun materialColor() = MaterialTheme.colorScheme.onSurface

