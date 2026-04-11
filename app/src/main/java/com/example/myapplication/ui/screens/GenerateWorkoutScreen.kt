package com.example.myapplication.ui.screens

import com.example.myapplication.data.PlanResult
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

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Generate Workout", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp) // space for button
            ) {
                item {
                    Text(
                        "You've already worked out today!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Create a different extra workout plan",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                }

                item {
                    Text("Number of exercises", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exerciseCountInput,
                        onValueChange = { exerciseCountInput = it },
                        placeholder = { Text("8") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                item {
                    Spacer(Modifier.height(8.dp))
                    Text("Focus Area", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(4.dp))
                }

                items(focusOptions) { focus ->
                    val isSelected = selectedFocus.contains(focus)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable {
                                selectedFocus = if (isSelected) selectedFocus - focus else selectedFocus + focus
                                HapticFeedback.performHapticFeedback(
                                    context,
                                    HapticFeedback.FeedbackType.CLICK
                                )
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                focus,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                    Spacer(Modifier.height(16.dp))
                    Text("Equipment (override for this workout)", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
                    Text("These settings are temporary and won't affect your main plan", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                }

                items(availableEquipmentList) { eq ->
                    val isSelected = selectedEquipment.contains(eq)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clickable {
                                val current = selectedEquipment.toMutableSet()
                                if (current.contains(eq)) current.remove(eq) else current.add(eq)
                                selectedEquipment = current
                                HapticFeedback.performHapticFeedback(
                                    context,
                                    HapticFeedback.FeedbackType.CLICK
                                )
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                eq.replaceFirstChar { it.uppercase() },
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
            }
        }

        // Sticky button at the bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Button(
                onClick = { generateWorkouts() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = selectedFocus.isNotEmpty() && exerciseCountInput.isNotBlank() && selectedEquipment.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Generate Workout", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
