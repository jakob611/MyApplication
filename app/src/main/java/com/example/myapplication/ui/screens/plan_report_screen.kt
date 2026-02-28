package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

@Composable
fun PlanReportScreen(plan: PlanResult, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(plan.name, style = MaterialTheme.typography.titleLarge)
                Text("Created on ${plan.createdAt.formatPrettyDate()}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))

            // ONLY Algorithm block
            plan.algorithmData?.let { data ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF232D4B))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Algorithm Analysis", style = MaterialTheme.typography.titleMedium, color = Color(0xFFFEE440))
                        Spacer(Modifier.height(8.dp))
                        Text("BMI: ${"%.1f".format(data.bmi)}", style = MaterialTheme.typography.bodyMedium)
                        Text("BMR: ${data.bmr?.toInt() ?: 0} kcal", style = MaterialTheme.typography.bodyMedium)
                        Text("TDEE: ${data.tdee?.toInt() ?: 0} kcal", style = MaterialTheme.typography.bodyMedium)
                        Text("Protein/kg: ${"%.1f".format(data.proteinPerKg ?: 0.0)}g", style = MaterialTheme.typography.bodyMedium)
                        Text("Calories/kg: ${"%.1f".format(data.caloriesPerKg ?: 0.0)}", style = MaterialTheme.typography.bodyMedium)
                        if (!data.caloricStrategy.isNullOrBlank()) {
                            Text("Strategy: ${data.caloricStrategy}", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(Modifier.height(12.dp))
                        if (data.detailedTips?.isNotEmpty() == true) {
                            Text("Detailed Analysis:", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFEE440))
                            data.detailedTips.forEach { tip ->
                                Text("• $tip", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        if (!data.macroBreakdown.isNullOrBlank()) {
                            Text("Macro Breakdown:", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFEE440))
                            Text(data.macroBreakdown, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                            Spacer(Modifier.height(8.dp))
                        }
                        if (!data.trainingStrategy.isNullOrBlank()) {
                            Text("Training Strategy:", style = MaterialTheme.typography.titleSmall, color = Color(0xFFFEE440))
                            Text(data.trainingStrategy, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 2.dp))
                        }
                    }
                }
                HorizontalDivider(color = Color.Gray, thickness = 1.dp)
                Spacer(Modifier.height(8.dp))
            }

            // No AI data below!
            // No calories/macros/trainingPlan/tips (from AI PlanResult) shown.

            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Back") }
        }
    }
}