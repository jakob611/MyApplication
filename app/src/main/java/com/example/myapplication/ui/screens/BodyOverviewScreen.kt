package com.example.myapplication.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp



@Composable
fun BodyOverviewScreen(
    plans: List<PlanResult>,
    onCreateNewPlan: () -> Unit,
) {
    // Novo: dialog za potrditve, če že obstaja plan
    var showReplaceDialog by remember { mutableStateOf(false) }

    val backgroundGradient = Brush.verticalGradient(
        listOf(Color(0xFF17223B), Color(0xFF25304A), Color(0xFF193446), Color(0xFF1E2D24))
    )
    val accentGreen = Color(0xFF13EF92)
    val accentBlue = Color(0xFF2563EB)
    val accentYellow = Color(0xFFFEE440)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
        ) {
            Icon(
                Icons.Filled.FitnessCenter,
                contentDescription = "Body",
                tint = accentGreen,
                modifier = Modifier
                    .size(54.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Body",
                color = accentGreen,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))

            if (plans.isEmpty()) {
                // Ni planov
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No plans yet!",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(30.dp))
                    Button(
                        onClick = onCreateNewPlan, // brez opozorila, ker ni planov
                        colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(60.dp)
                    ) {
                        Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Create New Plan", fontSize = 18.sp, color = Color.White)
                    }
                }
            } else {
                // Plani obstajajo - prikazuj vse
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    val sortedPlans = plans.sortedByDescending { it.createdAt }
                    sortedPlans.forEach { plan ->
                        PlanCard(
                            plan = plan,
                            accentGreen = accentGreen,
                            accentBlue = accentBlue,
                            accentYellow = accentYellow
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // Gumb za nov plan vedno na dnu (z opozorilom, če že obstaja)
                Button(
                    onClick = {
                        if (plans.isNotEmpty()) {
                            showReplaceDialog = true
                        } else {
                            onCreateNewPlan()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentBlue),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(52.dp)
                        .align(Alignment.CenterHorizontally)
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Another Plan", fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }

    // Potrditveni dialog (opozorilo o brisanju starega plana)
    if (showReplaceDialog) {
        AlertDialog(
            onDismissRequest = { showReplaceDialog = false },
            title = { Text("Replace existing plan?") },
            text = {
                Text("You already have a saved plan. If you proceed, the old plan will be deleted after you finish creating the new one.")
            },
            confirmButton = {
                Button(onClick = {
                    showReplaceDialog = false
                    onCreateNewPlan() // nadaljuj v kviz; brisanje se zgodi ob shranjevanju novega
                }) { Text("Continue") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReplaceDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlanCard(
    plan: PlanResult,
    accentGreen: Color,
    accentBlue: Color,
    accentYellow: Color
) {
    var expandedPlan by remember { mutableStateOf(false) }

    val parsed = remember(plan.algorithmData?.macroBreakdown) {
        plan.algorithmData?.macroBreakdown?.let { parseMacroBreakdown(it) }
    }
    val caloriesToShow = parsed?.calories ?: plan.calories
    val proteinToShow = parsed?.proteinG ?: plan.protein
    val carbsToShow = parsed?.carbsG ?: plan.carbs
    val fatToShow = parsed?.fatG ?: plan.fat

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expandedPlan = !expandedPlan }
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF232D4B)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        plan.name,
                        fontSize = 23.sp,
                        color = accentGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Created on ${plan.createdAt.formatPrettyDate()}",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
                Icon(
                    if (expandedPlan) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = accentGreen
                )
            }

            Spacer(Modifier.height(8.dp))

            caloriesToShow?.let {
                Text(
                    "Calories: ${it} kcal",
                    color = accentYellow,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                proteinToShow?.let {
                    Text("Protein: ${it}g", color = Color(0xFF13EF92), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(12.dp))
                carbsToShow?.let {
                    Text("Carbs: ${it}g", color = Color(0xFF33aaff), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(12.dp))
                fatToShow?.let {
                    Text("Fat: ${it}g", color = Color(0xFFF04C4C), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            AnimatedVisibility(visible = expandedPlan) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    if (!plan.goal.isNullOrBlank()) {
                        Text("Goal: ${plan.goal}", color = accentGreen, fontSize = 15.sp)
                    }
                    if (!plan.experience.isNullOrBlank()) {
                        Text("Experience: ${plan.experience}", color = accentGreen, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(12.dp))

                    if (plan.weeks.isEmpty()) {
                        Text(
                            "This plan doesn't have weekly schedule yet.\nCreate a new plan or wait for AI to generate it.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        plan.weeks.forEach { week ->
                            WeekCard(week, accentBlue, accentGreen, accentYellow)
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    plan.algorithmData?.let { data ->
                        Spacer(Modifier.height(12.dp))
                        AlgorithmDataSection(
                            data = data,
                            accentGreen = accentGreen,
                            accentYellow = accentYellow
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeekCard(
    week: WeekPlan,
    accentBlue: Color,
    accentGreen: Color,
    accentYellow: Color
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF203154))
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 13.dp, horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.SportsGymnastics, contentDescription = null, tint = accentYellow)
                Spacer(Modifier.width(10.dp))
                Text(
                    "Week ${week.weekNumber}",
                    fontWeight = FontWeight.Bold,
                    color = accentBlue,
                    fontSize = 19.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = accentGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
            AnimatedVisibility(expanded) {
                Column(
                    Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                ) {
                    week.days.forEach { day ->
                        DayCard(day, accentGreen, accentYellow)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DayCard(
    day: DayPlan,
    accentGreen: Color,
    accentYellow: Color
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF283B62))
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = accentGreen, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Day ${day.dayNumber}",
                    color = accentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = accentYellow,
                    modifier = Modifier.size(23.dp)
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 16.dp, end = 12.dp, bottom = 8.dp)) {
                    day.exercises.forEach { ex ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = accentYellow, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(ex, color = Color.White, fontSize = 15.sp)
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

// --- NOVO: Algorithm sekcija na dnu ---
@Composable
private fun AlgorithmDataSection(
    data: AlgorithmData,
    accentGreen: Color,
    accentYellow: Color
) {
    val labelColor = Color(0xFFCBD5E1)
    val valueColor = Color.White
    val cardBg = Color(0xFF1F2A4D)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Algorithm Analysis",
                style = MaterialTheme.typography.titleMedium,
                color = accentYellow,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))

            StatRow("BMI", String.format("%.1f", data.bmi), labelColor, valueColor)
            StatRow("BMR", "${data.bmr.toInt()} kcal", labelColor, valueColor)
            StatRow("TDEE", "${data.tdee.toInt()} kcal", labelColor, valueColor)
            StatRow("Protein/kg", String.format("%.1f g", data.proteinPerKg), labelColor, valueColor)
            StatRow("Calories/kg", String.format("%.1f", data.caloriesPerKg), labelColor, valueColor)

            data.caloricStrategy.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text("Caloric strategy", style = MaterialTheme.typography.labelLarge, color = labelColor)
                Text(it, color = valueColor)
            }

            data.macroBreakdown.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text("Macro breakdown", style = MaterialTheme.typography.labelLarge, color = labelColor)
                Text(it, color = valueColor)
            }

            data.trainingStrategy.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(10.dp))
                Text("Training strategy", style = MaterialTheme.typography.labelLarge, color = labelColor)
                Text(it, color = valueColor)
            }

            if (data.detailedTips.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Tips", style = MaterialTheme.typography.labelLarge, color = labelColor)
                Column {
                    data.detailedTips.forEach { tip ->
                        Text("• $tip", color = valueColor, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, labelColor: Color, valueColor: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = labelColor)
        Text(value, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

// --- Datum helper ---
fun Long.formatPrettyDate(): String {
    val locale = java.util.Locale.ENGLISH
    val sdf = java.text.SimpleDateFormat("MMMM d, yyyy", locale)
    return sdf.format(java.util.Date(this))
}

/* ===== PARSER: AlgorithmData.macroBreakdown -> številke (protein g total, carbs g, fat g, calories kcal) ===== */

data class ParsedMacros(
    val calories: Int?,
    val proteinG: Int?,
    val carbsG: Int?,
    val fatG: Int?
)

private fun parseMacroBreakdown(text: String): ParsedMacros {
    // Primeri niza:
    // "Protein: 1.8g/kg (100g total), Carbs: 583g, Fat: 50g, Calories: 3182 kcal/day, Fat loss deficit: -450 kcal/day"
    // "Protein: 2.0g/kg (120g total), Carbs: 589g, Fat: 54g, Calories: 3323 kcal/day, Fat loss deficit: -350 kcal/day"

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