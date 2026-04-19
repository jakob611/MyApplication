package com.example.myapplication.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.utils.HapticFeedback
import kotlinx.coroutines.delay

@Composable
fun FaceModuleScreen(
    onBack: () -> Unit = {},

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
                cardColor = MaterialTheme.colorScheme.surfaceVariant,
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
    val context = LocalContext.current
    var isMorning by remember { mutableStateOf(true) }

    val morningRoutine = remember { mutableStateListOf(
        Pair("1. Cleanser", false), Pair("2. Vitamin C", false), Pair("3. Moisturizer", false), Pair("4. SPF 50+", false)
    ) }
    val eveningRoutine = remember { mutableStateListOf(
        Pair("1. Double Cleanse", false), Pair("2. Retinol/AHA", false), Pair("3. Night Cream", false)
    ) }

    val currentRoutine = if (isMorning) morningRoutine else eveningRoutine
    val allDone = currentRoutine.all { it.second }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2A24))
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isMorning) "Morning Routine" else "Evening Routine", style = MaterialTheme.typography.titleLarge, color = Color(0xFF7be0c7))
                    TextButton(onClick = { isMorning = !isMorning }) {
                        Text(if (isMorning) "Switch to PM" else "Switch to AM", color = Color(0xFF7be0c7))
                    }
                }
                Spacer(Modifier.height(16.dp))

                currentRoutine.forEachIndexed { index, pair ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                currentRoutine[index] = pair.copy(second = !pair.second)
                                if (!pair.second) HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.SUCCESS)
                            }
                            .padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (pair.second) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (pair.second) Color(0xFF7be0c7) else Color.LightGray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        RoutineItem(pair.first, "Complete step ${index + 1}")
                    }
                }

                Spacer(Modifier.height(24.dp))
                if (allDone) {
                    Text("Routine Complete! 🌟", color = Color(0xFF7be0c7), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(12.dp))
                }

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
    var timerRunning by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableIntStateOf(60) }
    val context = LocalContext.current

    LaunchedEffect(timerRunning) {
        if (timerRunning) {
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft--
            }
            if (timeLeft == 0) {
                timerRunning = false
                HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.SUCCESS)
            }
        }
    }

     Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Jawline Sharpener", style = MaterialTheme.typography.titleLarge, color = Color(0xFF33aaff))
                Spacer(Modifier.height(16.dp))

                // Timer UI
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp).background(Color(0xFF33aaff).copy(alpha=0.1f), shape = RoundedCornerShape(60.dp))
                ) {
                    Text(
                        "${timeLeft}s",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Color(0xFF33aaff),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(16.dp))

                RoutineItem("1. Chin Tucks", "Pull your chin straight back. Hold 5s. Repeat 10x.")
                RoutineItem("2. Tongue Press", "Press entire tongue roof of mouth. Open/close mouth. 15x.")
                RoutineItem("3. Jaw Jut", "Push lower jaw forward slightly and look up. Hold 10s.")
                
                Spacer(Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = { timerRunning = !timerRunning },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF33aaff))
                    ) { Text(if (timerRunning) "Pause" else if (timeLeft == 60) "Start Session" else "Resume", color = Color.White) }

                    if (timeLeft < 60) {
                        TextButton(onClick = { timeLeft = 60; timerRunning = false }) {
                            Text("Reset", color = Color(0xFF33aaff))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}
