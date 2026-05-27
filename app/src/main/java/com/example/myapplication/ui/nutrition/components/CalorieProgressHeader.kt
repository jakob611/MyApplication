package com.example.myapplication.ui.nutrition.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.ui.screens.DonutProgressView

// -----------------------------------------------------------------------
// CalorieProgressHeader — donut kalorični graf + Active Calories Bar
// -----------------------------------------------------------------------
@Composable
fun CalorieProgressHeader(
    plan: PlanResult?,
    isWorkoutDay: Boolean,
    consumed: Int,
    burned: Int,
    adjustedTargetCalories: Int,
    effectiveTargetCalories: Int,
    dynamicTargetCalories: Int,
    effectiveWaterMl: Int,
    waterProgress: Float,
    waterTargetMl: Float,
    showWaterGoal: Boolean,
    fatProp: Float,
    proteinProp: Float,
    carbsProp: Float,
    fatCals: Int,
    proteinCals: Int,
    carbsCals: Int,
    detailedCalories: Boolean,
    weightUnit: String,
    textPrimary: Color,
    activityBoostKcal: Int
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (plan != null) {
                val dayLabel = if (isWorkoutDay) "️ Workout day" else " Rest day"
                val labelColor = if (isWorkoutDay) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    text = dayLabel,
                    color = labelColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            AndroidView(
                factory = { ctx ->
                    DonutProgressView(ctx).apply {
                        simpleMode = !detailedCalories
                        fatProportion = fatProp
                        proteinProportion = proteinProp
                        carbsProportion = carbsProp
                        this.fatCalories = fatCals
                        this.proteinCalories = proteinCals
                        this.carbsCalories = carbsCals
                        this.consumedCalories = consumed
                        this.targetCalories = adjustedTargetCalories
                        innerProgress = waterProgress
                        textColor = textPrimary.toArgb()
                        waterColor = textPrimary.toArgb()
                        innerValue = effectiveWaterMl.toString()
                        innerLabel = "ml"
                        this.weightUnit = weightUnit
                        centerValue = "$consumed/$adjustedTargetCalories"
                        centerLabel = "kcal"
                        startAngle = 135f
                        sweepAngle = 270f
                        onSegmentClick = { _ -> }
                    }
                },
                modifier = Modifier.size(240.dp),
                update = { view ->
                    view.simpleMode = !detailedCalories
                    view.fatProportion = fatProp
                    view.proteinProportion = proteinProp
                    view.carbsProportion = carbsProp
                    view.fatCalories = fatCals
                    view.proteinCalories = proteinCals
                    view.carbsCalories = carbsCals
                    view.consumedCalories = consumed
                    view.targetCalories = effectiveTargetCalories
                    view.innerProgress = waterProgress
                    view.textColor = textPrimary.toArgb()
                    view.waterColor = textPrimary.toArgb()
                    view.innerValue = effectiveWaterMl.toString()
                    view.weightUnit = weightUnit
                    view.centerValue = "$consumed/$effectiveTargetCalories"
                    view.onSegmentClick = { clicked ->
                        view.clickedSegment = clicked
                        view.invalidate()
                    }
                }
            )
            if (showWaterGoal) {
                Text(
                    text = " Cilj: ${waterTargetMl.toInt()} ml",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (activityBoostKcal > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = " +$activityBoostKcal kcal boost",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Box(modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)) {
            val burnedGoal = if (dynamicTargetCalories > 0)
                (dynamicTargetCalories - (dynamicTargetCalories * 0.8).toInt()).coerceAtLeast(300)
            else 500
            ActiveCaloriesBar(currentCalories = burned, goal = burnedGoal)
        }
    }
}

// -----------------------------------------------------------------------
// ActiveCaloriesBar — navpična progresna vrstica za porabljene kalorije
// -----------------------------------------------------------------------
@Composable
fun ActiveCaloriesBar(
    currentCalories: Int,
    goal: Int = 800
) {
    val progress = (currentCalories.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.height(240.dp)
    ) {
        Text("", fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(16.dp)
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress)
                    .align(Alignment.BottomCenter)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "$currentCalories",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}


