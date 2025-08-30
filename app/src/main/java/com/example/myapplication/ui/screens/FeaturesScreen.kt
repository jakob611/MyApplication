package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class FeatureData(
    val title: String,
    val description: String
)

@Composable
fun FeaturesScreen(
    onBack: () -> Unit = {}
) {
    val features = listOf(
        FeatureData("Goal-Oriented Plans", "Personalized plans based on your specific goals and current condition"),
        FeatureData("AI Analysis", "Advanced AI scans for body composition, skin health, and hair condition"),
        FeatureData("24/7 AI Coach", "Personal coaching chatbot available around the clock for guidance"),
        FeatureData("Smart Recommendations", "Curated product suggestions based on your analysis and goals"),
        FeatureData("Progress Tracking", "Track your transformation journey with detailed progress metrics"),
        FeatureData("Expert Access", "Connect with professional trainers, stylists, and dermatologists")
    )
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Button(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
            Text("← Back to Home")
        }
        Spacer(Modifier.height(16.dp))
        Text("Everything You Need to Level Up", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Transform yourself in 3 areas with planned workouts, AI analysis, personally recommended products and diets.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))
        // Features grid
        Column {
            features.forEach {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(it.title, style = MaterialTheme.typography.titleMedium)
                        Text(it.description, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}