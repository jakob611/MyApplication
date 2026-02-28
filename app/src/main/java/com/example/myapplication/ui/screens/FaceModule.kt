package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FaceModuleScreen(
    onBack: () -> Unit = {},
    onGoldenRatioClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Inline header with back button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "FACE MODULE",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Title
            Text(
                "Face Enhancement",
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                "Enhance your facial aesthetics with science-backed methods",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Golden Ratio Card
            FaceFeatureCard(
                title = "Golden Ratio Analysis",
                description = "Discover your facial beauty score using the ancient golden ratio technique from medieval times",
                icon = {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = "Golden Ratio",
                        tint = Color(0xFFFEE440),
                        modifier = Modifier.size(50.dp)
                    )
                },
                onClick = onGoldenRatioClick,
                cardColor = MaterialTheme.colorScheme.surface,
                borderColor = Color(0xFFFEE440)
            )

            // Skincare Card
            FaceFeatureCard(
                title = "Skincare Routine",
                description = "Custom skincare routine builder based on your skin type and concerns",
                icon = {
                    Icon(
                        Icons.Filled.Spa,
                        contentDescription = "Skincare",
                        tint = Color(0xFF7be0c7),
                        modifier = Modifier.size(50.dp)
                    )
                },
                onClick = { /* TODO: Implement skincare */ },
                cardColor = Color(0xFF1A2A24),
                borderColor = Color(0xFF7be0c7),
                comingSoon = true
            )

            // Face Exercises Card
            FaceFeatureCard(
                title = "Face Exercises",
                description = "Jawline improvement exercises and facial muscle training",
                icon = {
                    Icon(
                        Icons.Filled.Face,
                        contentDescription = "Face Exercises",
                        tint = Color(0xFF33aaff),
                        modifier = Modifier.size(50.dp)
                    )
                },
                onClick = { /* TODO: Implement face exercises */ },
                cardColor = Color(0xFF1A2435),
                borderColor = Color(0xFF33aaff),
                comingSoon = true
            )
        }
    }
}

@Composable
fun FaceFeatureCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    cardColor: Color,
    borderColor: Color,
    comingSoon: Boolean = false
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .shadow(8.dp, RoundedCornerShape(20.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon()
            Spacer(Modifier.height(16.dp))

            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                color = borderColor
            )

            Spacer(Modifier.height(8.dp))

            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            if (comingSoon) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Coming Soon",
                    color = borderColor,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}