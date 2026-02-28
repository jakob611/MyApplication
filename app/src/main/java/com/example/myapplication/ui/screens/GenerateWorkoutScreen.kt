package com.example.myapplication.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.domain.WorkoutGenerator
import com.example.myapplication.data.AdvancedExerciseRepository
import com.example.myapplication.domain.WorkoutGenerationParams
import com.example.myapplication.domain.WorkoutGoal
import com.example.myapplication.utils.HapticFeedback
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GeneratedWorkout(
    val id: Int,
    val name: String,
    val duration: Int,
    val exercises: List<String>,
    val focus: String,
    val equipment: Set<String> = emptySet() // oprema izbrana v extra workout screenu
)

@Composable
fun GenerateWorkoutScreen(
    onBack: () -> Unit,
    onSelectWorkout: (GeneratedWorkout) -> Unit,
    currentPlan: PlanResult? = null  // Add current plan parameter
) {
    val context = LocalContext.current
    var exerciseCountInput by remember { mutableStateOf("8") }
    var selectedFocus by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedEquipment by remember {
        // Initialize from plan equipment
        val planEquipment = currentPlan?.equipment ?: emptyList()
        val mappedEquipment = mutableSetOf<String>()
        mappedEquipment.add("bodyweight") // Always include bodyweight

        planEquipment.forEach { eq ->
            when (eq.lowercase()) {
                "dumbbells" -> mappedEquipment.add("dumbbells")
                "barbell" -> mappedEquipment.add("barbell")
                "kettlebell" -> mappedEquipment.add("kettlebells")
                "pull-up bar" -> mappedEquipment.add("pull_up_bar")
                "resistance bands" -> mappedEquipment.add("bands")
                "bench" -> mappedEquipment.add("bench")
                "treadmill" -> mappedEquipment.add("treadmill")
            }
        }

        mutableStateOf(mappedEquipment.ifEmpty { setOf("bodyweight") })
    }
    var availableEquipmentList by remember { mutableStateOf(emptyList<String>()) }

    // Init advanced repo za seznam opreme
    LaunchedEffect(Unit) {
        AdvancedExerciseRepository.init(context)
        availableEquipmentList = AdvancedExerciseRepository.getAllEquipment().toList()
    }

    fun generateWorkouts() {
        // Ne generiramo vaj tukaj — samo pošljemo parametre naprej.
        // WorkoutSessionScreen bo generiral vaje sam z AlgorithmPreferences (pravilna težavnost itd.)
        val focusList = selectedFocus.ifEmpty { setOf("Full Body") }
        val focusLabel = focusList.joinToString(", ")
        val exerciseCount = exerciseCountInput.toIntOrNull()?.coerceIn(4, 15) ?: 8

        val workout = GeneratedWorkout(
            id = 1,
            name = "$focusLabel Workout",
            duration = exerciseCount * 4,
            exercises = emptyList(),
            focus = focusLabel,
            equipment = selectedEquipment
        )

        HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.SUCCESS)
        onSelectWorkout(workout)
    }

    val focusOptions = listOf("Arms", "Legs", "Back", "Chest", "Abs", "Shoulders", "Full Body")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
                    // Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            "Generate Workout",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text("You've already worked out today!", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Create a different workout plan", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        item {
                            Text("Number of exercises", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = exerciseCountInput,
                                onValueChange = { exerciseCountInput = it },
                                placeholder = { Text("8") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                                ),
                                singleLine = true
                            )
                        }

                        item {
                            Text("Focus Area", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }

                        items(focusOptions) { focus ->
                            val isSelected = selectedFocus.contains(focus)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clickable {
                                        selectedFocus = if (isSelected) selectedFocus - focus else selectedFocus + focus
                                        HapticFeedback.performHapticFeedback(
                                            context,
                                            HapticFeedback.FeedbackType.CLICK
                                        )
                                    }
                                    .background(
                                        if (isSelected)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else
                                            MaterialTheme.colorScheme.surface
                                    ),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(focus)
                                    Spacer(Modifier.weight(1f))
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Text("Equipment (override for this workout)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("These settings are temporary and won't affect your plan", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        items(availableEquipmentList) { eq ->
                            val isSelected = selectedEquipment.contains(eq)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .clickable {
                                        val current = selectedEquipment.toMutableSet()
                                        if (current.contains(eq)) current.remove(eq) else current.add(eq)
                                        selectedEquipment = current
                                        HapticFeedback.performHapticFeedback(
                                            context,
                                            HapticFeedback.FeedbackType.CLICK
                                        )
                                    }
                                    .background(
                                        if (isSelected)
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                        else
                                            MaterialTheme.colorScheme.surface
                                    ),
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)
                                else
                                    MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(eq)
                                    Spacer(Modifier.weight(1f))
                                    if (isSelected) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { generateWorkouts() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = selectedFocus.isNotEmpty() && exerciseCountInput.isNotBlank() && selectedEquipment.isNotEmpty()
                            ) {
                                Text("Generate Workout")
                            }
                        }
                    }
                }
    }
}

@Composable
private fun LoadingWorkoutUI() {
    // Dark background gradient
    val backgroundGradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(
            Color(0xFF17223B),
            Color(0xFF25304A),
            Color(0xFF193446)
        )
    )

    // Rotating animation for the spinner
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "spinner")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Pulsing animation for text
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Spinning loader
            androidx.compose.foundation.Canvas(modifier = Modifier.size(120.dp)) {
                drawArc(
                    color = Color(0xFF6366F1),
                    startAngle = rotation,
                    sweepAngle = 280f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 12.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Main text with pulsing effect
            Text(
                text = "Advanced Algorithm\nWorking...",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = pulseAlpha),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 32.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle
            Text(
                text = "Preparing your personalized workout",
                fontSize = 14.sp,
                color = Color(0xFFB0B8C4),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Animated dots
            LoadingDots()
        }
    }
}

@Composable
private fun LoadingDots() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")

    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(8.dp)
    ) {
        repeat(3) { index ->
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(600, easing = androidx.compose.animation.core.LinearEasing, delayMillis = index * 200),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "dot$index"
            )

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .padding(2.dp)
                    .background(
                        color = Color(0xFF6366F1).copy(alpha = alpha),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )

            if (index < 2) {
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}
