package com.example.myapplication.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MyPlansScreen(
    plans: List<PlanResult>,
    onPlanClick: (PlanResult) -> Unit,
    onPlanDelete: (PlanResult) -> Unit
) {
    var planToDelete by remember { mutableStateOf<PlanResult?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Plans", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (plans.isEmpty()) {
            Text("You have no saved plans yet.")
        } else {
            plans.forEach { plan ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onPlanClick(plan) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F7FA))
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(plan.name, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Created on ${plan.createdAt.formatPrettyDate()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                        IconButton(onClick = { planToDelete = plan }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = Color(0xFFF15A5A))
                        }
                    }
                }
            }
        }
    }

    if (planToDelete != null) {
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text("Delete Plan") },
            text = { Text("Are you sure you want to delete this plan? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onPlanDelete(planToDelete!!)
                        planToDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { planToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Helper to format date as "August 1, 2025"
