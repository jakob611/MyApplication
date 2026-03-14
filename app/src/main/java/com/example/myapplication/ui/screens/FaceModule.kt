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
import androidx.compose.ui.window.Dialog

@Composable
fun FaceModuleScreen(
    onBack: () -> Unit = {},
    onGoldenRatioClick: () -> Unit = {}
) {
    var showSkincare by remember { mutableStateOf(false) }
    var showExercises by remember { mutableStateOf(false) }

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
                onClick = { showSkincare = true },
                cardColor = Color(0xFF1A2A24),
                borderColor = Color(0xFF7be0c7),
                comingSoon = false
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
                onClick = { showExercises = true },
                cardColor = Color(0xFF1A2435),
                borderColor = Color(0xFF33aaff),
                comingSoon = false
            )
        }

        if (showSkincare) {
             SkincareDialog(onDismiss = { showSkincare = false })
        }

        if (showExercises) {
            FaceExerciseDialog(onDismiss = { showExercises = false })
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

@Composable
fun SkincareDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A24))
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Your Morning Routine", style = MaterialTheme.typography.titleLarge, color = Color(0xFF7be0c7))
                Spacer(Modifier.height(16.dp))
                RoutineItem("1. Cleanser", "Use a gentle foaming cleanser. Massage for 60s.")
                RoutineItem("2. Vitamin C", "Apply for brightness and antioxidant protection.")
                RoutineItem("3. Moisturizer", "Lock in hydration with a lightweight gel-cream.")
                RoutineItem("4. SPF 50+", "Essential protection against UV aging.")

                Spacer(Modifier.height(24.dp))
                
                Text("Your Evening Routine", style = MaterialTheme.typography.titleLarge, color = Color(0xFF7be0c7))
                Spacer(Modifier.height(16.dp))
                RoutineItem("1. Double Cleanse", "Oil cleanser first, then water-based cleanser.")
                RoutineItem("2. Retinol/AHA", "Apply active treatment (3x/week).")
                RoutineItem("3. Night Cream", "Richer moisturizer to repair skin barrier.")

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7be0c7)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Close", color = Color.Black) }
            }
        }
    }
}

@Composable
fun RoutineItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(bottom = 12.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Color.White)
        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.LightGray)
    }
}

@Composable
fun FaceExerciseDialog(onDismiss: () -> Unit) {
     Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2435))
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text("Jawline Sharpener", style = MaterialTheme.typography.titleLarge, color = Color(0xFF33aaff))
                Spacer(Modifier.height(16.dp))
                RoutineItem("1. Chin Tucks", "Pull your chin straight back. Hold 5s. Repeat 10x.")
                RoutineItem("2. Tongue Press", "Press entire tongue roof of mouth. Open/close mouth. 15x.")
                RoutineItem("3. Jaw Jut", "Push lower jaw forward slightly and look up. Hold 10s.")
                
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33aaff)),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Start Session", color = Color.White) }
            }
        }
    }
}
