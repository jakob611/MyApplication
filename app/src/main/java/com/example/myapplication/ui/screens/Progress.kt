package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProgressScreen() {
    // Outer column for whole screen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(44.dp)) // space under top bar

        // Chart Card
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(210.dp)
                .border(1.5.dp, Color(0xFF111111), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.BottomStart
        ) {
            // Bar chart
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp, start = 18.dp, end = 18.dp, top = 22.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                BarColumn(label = "A", value = 16, color = Color(0xFFEF4444), maxValue = 45)
                Spacer(Modifier.width(12.dp))
                BarColumn(label = "B", value = 45, color = Color(0xFFF97316), maxValue = 45)
                Spacer(Modifier.width(12.dp))
                BarColumn(label = "C", value = 27, color = Color(0xFFFBBF24), maxValue = 45)
                Spacer(Modifier.width(12.dp))
                BarColumn(label = "D", value = 23, color = Color(0xFFFDE047), maxValue = 45)
            }
        }

        Spacer(Modifier.height(22.dp))

        // Description text
        Text(
            "bdapčvjuibpvbpvbp...",
            fontSize = 16.sp,
            color = Color.Black
        )

        Spacer(Modifier.height(24.dp))

        Text(
            "ofbhrewikgb\npdfnbpčrfeubvprue gpue gfprew gf",
            fontSize = 15.sp,
            color = Color.Black
        )
    }
}

@Composable
private fun BarColumn(label: String, value: Int, color: Color, maxValue: Int) {
    // Calculate height relative to maxValue (max bar height: 110dp)
    val barMaxHeight = 110.dp
    val barHeight = (value.toFloat() / maxValue) * barMaxHeight.value
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier.width(42.dp)
    ) {
        // Value label above bar
        Text(
            value.toString(),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = Color.Black,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        // Bar
        Box(
            modifier = Modifier
                .height(barHeight.dp)
                .width(38.dp)
                .background(color = color, shape = RoundedCornerShape(8.dp))
        )
        // Label below bar
        Spacer(Modifier.height(7.dp))
        Text(
            label,
            fontSize = 15.sp,
            color = Color.Black
        )
    }
}