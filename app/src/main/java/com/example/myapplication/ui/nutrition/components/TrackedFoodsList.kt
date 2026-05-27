package com.example.myapplication.ui.nutrition.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.model.UserProfile
import com.example.myapplication.ui.screens.MealCard
import com.example.myapplication.ui.screens.MealType
import com.example.myapplication.ui.screens.TrackedFood
import com.example.myapplication.ui.screens.TrackedFoodDetailDialog

// -----------------------------------------------------------------------
// TrackedFoodsList — 4 meal kartice (Breakfast/Lunch/Dinner/Snacks)
// + inline TrackedFoodDetailDialog pri kliku na jed
// -----------------------------------------------------------------------
@Composable
fun TrackedFoodsList(
    trackedFoods: List<TrackedFood>,
    surfaceVariantColor: Color,
    textPrimary: Color,
    userProfile: UserProfile,
    onAddFood: (MealType) -> Unit,
    onFoodDelete: (TrackedFood) -> Unit
) {
    val showFoodDetailDialog = remember { mutableStateOf<TrackedFood?>(null) }

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
            onAddFood = { onAddFood(mealType) },
            onFoodClick = { showFoodDetailDialog.value = it },
            onFoodDelete = onFoodDelete
        )
    }

    Spacer(Modifier.height(4.dp))

    // Inline detail dialog — stanje je lokalno (odpre/zapre samo tu)
    showFoodDetailDialog.value?.let { trackedFood ->
        TrackedFoodDetailDialog(
            trackedFood = trackedFood,
            onDismiss = { showFoodDetailDialog.value = null },
            userProfile = userProfile
        )
    }
}

