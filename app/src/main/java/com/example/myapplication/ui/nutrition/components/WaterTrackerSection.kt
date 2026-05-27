package com.example.myapplication.ui.nutrition.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.myapplication.ui.screens.WaterControlsRow
import com.example.myapplication.utils.HapticFeedback

// -----------------------------------------------------------------------
// WaterTrackerSection — voda cilj + gumbi za dodajanje/odstranjevanje
// -----------------------------------------------------------------------
@Composable
fun WaterTrackerSection(
    waterConsumedMl: Int,
    lastClickState: MutableState<Long>,
    context: Context,
    onWaterUpdate: (Int) -> Unit,
    textPrimary: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        WaterControlsRow(
            waterConsumedMl = waterConsumedMl,
            textPrimary = textPrimary,
            lastClickState = lastClickState,
            onMinus = { newVal ->
                HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.LIGHT_CLICK)
                onWaterUpdate(newVal)
            },
            onPlus = { newVal ->
                HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.LIGHT_CLICK)
                onWaterUpdate(newVal)
            }
        )
    }
}

