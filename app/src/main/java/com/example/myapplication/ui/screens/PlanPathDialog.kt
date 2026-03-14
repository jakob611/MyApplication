package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.AlgorithmPreferences
import com.example.myapplication.data.DayPlan
import com.example.myapplication.data.PlanResult
import com.example.myapplication.viewmodels.BodyModuleHomeViewModel

@Composable
fun PlanPathDialog(
    currentDay: Int,
    isTodayDone: Boolean,
    weeklyGoal: Int,
    onClose: () -> Unit,
    onStartToday: () -> Unit,
    onStartAdditional: () -> Unit,
    onMyPlan: () -> Unit,
    currentPlan: PlanResult?,
    vm: BodyModuleHomeViewModel? = null,
    onPlanUpdated: ((PlanResult) -> Unit)? = null
) {
    androidx.activity.compose.BackHandler { onClose() }

    val context = LocalContext.current

    var localPlan by remember { mutableStateOf(currentPlan) }
    var selectedDayInfo by remember { mutableStateOf<DayPlan?>(null) }
    var selectedDayNumber by remember { mutableStateOf(0) }

    val planFrequency = when {
        weeklyGoal > 0 -> weeklyGoal
        currentPlan?.trainingDays != null && currentPlan.trainingDays > 0 -> currentPlan.trainingDays
        else -> 3
    }
    val safeGoal = if (planFrequency > 0) planFrequency else 3

    val totalPlanDays = 28
    val blockStartWeek = 1

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF5F5F5)
    val textColor = if (isDark) MaterialTheme.colorScheme.onBackground else Color.Black
    val subtitleColor = if (isDark) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f) else Color.Gray

    val todayIsRestDay = remember(localPlan, currentDay) {
        localPlan?.weeks
            ?.flatMap { it.days }
            ?.firstOrNull { it.dayNumber == currentDay }
            ?.isRestDay ?: false
    }

    Surface(modifier = Modifier.fillMaxSize(), color = bgColor) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = textColor)
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Weeks $blockStartWeek - ${blockStartWeek + 3}",
                            fontSize = 24.sp, fontWeight = FontWeight.Bold, color = textColor
                        )
                        Text("Keep the consistency!", fontSize = 14.sp, color = subtitleColor)
                    }
                    IconButton(onClick = onMyPlan) {
                        Icon(Icons.Filled.Info, contentDescription = "My Plan", tint = textColor)
                    }
                }

                PlanPathVisualizer(
                    currentDayGlobal = currentDay,
                    isTodayDone = isTodayDone,
                    weeklyGoal = safeGoal,
                    totalDays = totalPlanDays,
                    startWeek = blockStartWeek,
                    isDarkMode = isDark,
                    planWeeks = localPlan?.weeks ?: emptyList(),
                    swapSourceDay = null,
                    onNodeClick = { clickedGlobalDay ->
                        val plan = localPlan
                        val dayPlan = plan?.weeks?.flatMap { it.days }
                            ?.firstOrNull { it.dayNumber == clickedGlobalDay }
                            ?: DayPlan(dayNumber = clickedGlobalDay)
                        selectedDayInfo = dayPlan
                        selectedDayNumber = clickedGlobalDay
                    },
                    onDragSwap = { fromDay, toDay ->
                        val plan = localPlan
                        if (plan != null && vm != null) {
                            com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                                context,
                                com.example.myapplication.utils.HapticFeedback.FeedbackType.SUCCESS
                            )
                            vm.swapDaysInPlan(plan, fromDay, toDay) { updated ->
                                localPlan = updated
                                onPlanUpdated?.invoke(updated)
                                android.widget.Toast.makeText(context, "✅ Swapped + all future weeks updated!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    footerContent = {}
                )

                val selectedDay = selectedDayInfo
                if (selectedDay != null) {
                    DayInfoDialog(
                        dayPlan = selectedDay,
                        dayNumber = selectedDayNumber,
                        currentDay = currentDay,
                        isTodayDone = isTodayDone,
                        plan = localPlan,
                        onDismiss = { selectedDayInfo = null },
                        onStartToday = { selectedDayInfo = null; onStartToday() }
                    )
                }
            }

            if (isTodayDone) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Good job! Today is done. ✅",
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            "Locked for next day. Want to do more?",
                            color = if (isDark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else Color.Gray,
                            fontSize = 14.sp
                        )
                        Button(
                            onClick = onStartAdditional,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                        ) {
                            Text("Extra Workout", color = Color.White)
                        }
                    }
                }
            } else if (todayIsRestDay) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) Color(0xFF1E2A3A) else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(8.dp),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("😴", fontSize = 24.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "REST DAY",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF6366F1),
                                fontSize = 18.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Recovery is just as important as training! Focus on sleep, hydration and light stretching.",
                            color = if (isDark) Color(0xFF94A3B8) else Color.Gray,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onStartAdditional,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF374151)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Extra workout (optional)", color = Color(0xFF94A3B8), fontSize = 14.sp)
                        }
                    }
                }
            } else {
                Button(
                    onClick = onStartToday,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("START DAY $currentDay", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun DayInfoDialog(
    dayPlan: DayPlan,
    dayNumber: Int,
    currentDay: Int,
    isTodayDone: Boolean,
    plan: PlanResult?,
    onDismiss: () -> Unit,
    onStartToday: () -> Unit
) {
    val context = LocalContext.current
    val week = ((dayNumber - 1) / 7) + 1
    val dayInWeek = ((dayNumber - 1) % 7) + 1
    val isPast = dayNumber < currentDay
    val isToday = dayNumber == currentDay
    val isFuture = dayNumber > currentDay

    val difficultyLevel: Int = remember(context) {
        val raw = AlgorithmPreferences.getCurrentDifficulty(context)
        Math.round(raw).coerceIn(1, 10)
    }
    val difficultyLabel: String = when {
        difficultyLevel <= 2 -> "Very Easy"
        difficultyLevel <= 4 -> "Easy"
        difficultyLevel <= 6 -> "Moderate"
        difficultyLevel <= 8 -> "Hard"
        else -> "Very Hard"
    }
    val difficultyColor: Color = when {
        difficultyLevel <= 2 -> Color(0xFF4CAF50)
        difficultyLevel <= 4 -> Color(0xFF8BC34A)
        difficultyLevel <= 6 -> Color(0xFFFFEB3B)
        difficultyLevel <= 8 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
    val difficultyDisplay = "$difficultyLevel/10 — $difficultyLabel"

    val estimatedTime = when {
        dayPlan.isRestDay -> null
        plan?.sessionLength != null && plan.sessionLength > 0 -> plan.sessionLength
        else -> 45
    }

    val statusLabel = when {
        dayPlan.isRestDay -> "💤 Rest Day"
        isPast -> "✅ Completed"
        isToday && isTodayDone -> "✅ Done Today"
        isToday -> "▶️ Today"
        isFuture -> "🔒 Upcoming"
        else -> ""
    }

    val headerColor = when {
        dayPlan.isRestDay -> Color(0xFF546E7A)
        isPast -> Color(0xFF2E7D32)
        isToday -> Color(0xFF6366F1)
        else -> Color(0xFF374151)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2A3A),
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Week $week • Day $dayInWeek", fontSize = 13.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Medium)
                        Text("Day $dayNumber", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Surface(color = headerColor.copy(alpha = 0.25f), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            statusLabel,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = Color(0xFF2D3F55))
                if (dayPlan.isRestDay) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1A2740), RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("💤", fontSize = 32.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Rest Day", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Recovery & regeneration", fontSize = 13.sp, color = Color(0xFF94A3B8))
                        }
                    }
                    Text(
                        "Rest days are an essential part of your training. Your muscles recover and grow stronger during rest.",
                        fontSize = 13.sp, color = Color(0xFF94A3B8), lineHeight = 18.sp
                    )
                } else {
                    if (dayPlan.focusLabel.isNotBlank() && dayPlan.focusLabel != "Rest") {
                        DayInfoRow(icon = "🎯", label = "Focus Area", value = dayPlan.focusLabel)
                    }
                    if (estimatedTime != null) {
                        DayInfoRow(icon = "⏱️", label = "Estimated Time", value = "$estimatedTime min")
                    }
                    DayInfoRow(icon = "💪", label = "Workout Difficulty", value = difficultyDisplay, valueColor = difficultyColor)
                    if (!plan?.goal.isNullOrBlank()) {
                        DayInfoRow(icon = "🏆", label = "Goal", value = plan!!.goal!!)
                    }
                }
            }
        },
        confirmButton = {
            if (isToday && !isTodayDone && !dayPlan.isRestDay) {
                Button(
                    onClick = onStartToday,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("▶️ Start Today's Workout", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF6366F1)) }
            }
        },
        dismissButton = {
            if (isToday && !isTodayDone && !dayPlan.isRestDay) {
                TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF94A3B8)) }
            }
        }
    )
}

@Composable
private fun DayInfoRow(icon: String, label: String, value: String, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A2740), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(icon, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, color = Color(0xFF94A3B8))
        }
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

