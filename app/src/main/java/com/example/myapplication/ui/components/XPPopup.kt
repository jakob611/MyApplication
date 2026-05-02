package com.example.myapplication.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun XPPopup(
    xpAmount: Int,
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    var shouldFadeOut by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(2500) // Prikaži pop-up 2.5 sekunde
            shouldFadeOut = true
            delay(300) // Čakaj za animacijo
            onDismiss()
        }
    }

    val alpha by animateFloatAsState(if (shouldFadeOut) 0f else 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .alpha(alpha),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "✨ +$xpAmount XP",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    // onPrimary zagotovi kontrast v obeh temah:
                    // Light: kremasto bela (#FCFBF8) na temno vijolični → ✅
                    // Dark:  temno vijolična (#38305A) na svetli pastelni → ✅
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

