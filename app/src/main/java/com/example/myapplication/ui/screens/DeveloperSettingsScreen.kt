package com.example.myapplication.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.example.myapplication.data.AdvancedExerciseRepository
import com.example.myapplication.data.RefinedExercise
import com.example.myapplication.data.UserProfile
import com.example.myapplication.domain.WorkoutGenerationParams
import com.example.myapplication.domain.WorkoutGenerator
import com.example.myapplication.domain.WorkoutGoal

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DeveloperSettingsScreen(
    onBack: () -> Unit,
    userProfile: UserProfile?,
    currentPlan: PlanResult?
) {
    val context = LocalContext.current

    // Initialize repo if needed (it handles single init internally)
    LaunchedEffect(Unit) {
        AdvancedExerciseRepository.init(context)
    }

    // Initialize defaults from PlanResult and UserProfile
    var userLevel by remember {
        val experienceStr = currentPlan?.experience ?: "Intermediate"
        val level = when(experienceStr) {
            "Beginner" -> 3f
            "Intermediate" -> 6f
            "Advanced" -> 9f
            else -> 5f
        }
        mutableFloatStateOf(level)
    }

    var selectedGoal by remember {
        val goalStr = currentPlan?.goal ?: "General health"
        val goal = when(goalStr) {
            "Build muscle" -> WorkoutGoal.MUSCLE_GAIN
            "Lose fat" -> WorkoutGoal.WEIGHT_LOSS
            "Recomposition" -> WorkoutGoal.STRENGTH
            "Improve endurance" -> WorkoutGoal.ENDURANCE
            else -> WorkoutGoal.GENERAL_FITNESS
        }
        mutableStateOf(goal)
    }

    // Equipment selection
    val availableEquipmentList = remember {
        AdvancedExerciseRepository.getAllEquipment().toList()
    }

    // Default select from plan or bodyweight
    var selectedEquipment by remember {
        val planEquipment = currentPlan?.equipment ?: userProfile?.equipment ?: emptyList()
        // Map plan strings to repository IDs
        val mappedEquipment = mutableSetOf<String>()

        // Always include bodyweight
        mappedEquipment.add("bodyweight")

        // Map equipment names
        planEquipment.forEach { eq ->
            when (eq.lowercase()) {
                "dumbbells" -> mappedEquipment.add("dumbbells")
                "barbell" -> mappedEquipment.add("barbell")
                "kettlebell" -> mappedEquipment.add("kettlebells")
                "pull-up bar" -> mappedEquipment.add("pull_up_bar")
                "resistance bands" -> mappedEquipment.add("bands")
                "bench" -> mappedEquipment.add("bench")
                "treadmill" -> mappedEquipment.add("treadmill")
                "none (bodyweight)" -> { /* only bodyweight */ }
            }
        }

        mutableStateOf(mappedEquipment.ifEmpty { setOf("bodyweight") })
    }

    // Focus areas
    val focusOptions = listOf("Full Body", "Chest", "Back", "Legs", "Arms", "Abs", "Shoulders")
    var selectedFocus by remember {
        val planFocusAreas = currentPlan?.focusAreas ?: userProfile?.focusAreas ?: emptyList()
        val mappedFocus = mutableSetOf<String>()

        if (planFocusAreas.isEmpty()) {
            mappedFocus.add("Full Body")
        } else {
            planFocusAreas.forEach { area ->
                // Map from plan focus areas to debug screen options
                when (area.lowercase()) {
                    "upper body" -> {
                        mappedFocus.add("Chest")
                        mappedFocus.add("Back")
                        mappedFocus.add("Arms")
                        mappedFocus.add("Shoulders")
                    }
                    "lower body" -> {
                        mappedFocus.add("Legs")
                    }
                    "core" -> mappedFocus.add("Abs")
                    "full body" -> mappedFocus.add("Full Body")
                    else -> {
                        // Direct mapping for matching names
                        focusOptions.forEach { option ->
                            if (option.lowercase() == area.lowercase()) {
                                mappedFocus.add(option)
                            }
                        }
                    }
                }
            }
        }

        mutableStateOf(mappedFocus.ifEmpty { setOf("Full Body") })
    }

    var generatedWorkout by remember { mutableStateOf<List<RefinedExercise>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Generator Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 1. User Level
            Text("User Experience Level: ${userLevel.toInt()}", fontWeight = FontWeight.Bold)
            Slider(
                value = userLevel,
                onValueChange = { userLevel = it },
                valueRange = 1f..10f,
                steps = 9
            )

            Spacer(Modifier.height(16.dp))

            // 2. Goal Selector
            Text("Workout Goal", fontWeight = FontWeight.Bold)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                WorkoutGoal.values().forEach { goal ->
                    FilterChip(
                        selected = (goal == selectedGoal),
                        onClick = { selectedGoal = goal },
                        label = { Text(goal.name.replace("_", " ")) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 3. Focus Areas
            Text("Focus Areas", fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                focusOptions.forEach { focus ->
                    FilterChip(
                        selected = selectedFocus.contains(focus),
                        onClick = {
                            if (focus == "Full Body") {
                                selectedFocus = setOf("Full Body")
                            } else {
                                val current = selectedFocus.toMutableSet()
                                current.remove("Full Body")
                                if (current.contains(focus)) current.remove(focus) else current.add(focus)
                                if (current.isEmpty()) current.add("Full Body")
                                selectedFocus = current
                            }
                        },
                        label = { Text(focus) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 4. Equipment
            Text("Available Equipment", fontWeight = FontWeight.Bold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableEquipmentList.forEach { eq ->
                    FilterChip(
                        selected = selectedEquipment.contains(eq),
                        onClick = {
                            val current = selectedEquipment.toMutableSet()
                            if (current.contains(eq)) current.remove(eq) else current.add(eq)
                            selectedEquipment = current
                        },
                        label = { Text(eq) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Generate Button
            Button(
                onClick = {
                    val gen = WorkoutGenerator()
                    val params = WorkoutGenerationParams(
                        userExperienceLevel = userLevel.toInt(),
                        availableEquipment = selectedEquipment,
                        goal = selectedGoal,
                        focusAreas = selectedFocus
                    )
                    generatedWorkout = gen.generateWorkout(params)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("GENERATE WORKOUT")
            }

            Spacer(Modifier.height(16.dp))

            if (generatedWorkout.isNotEmpty()) {
                Text("Generated Workout (${generatedWorkout.size} exercises):", fontWeight = FontWeight.Bold)
                generatedWorkout.forEachIndexed { index, ex ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${index + 1}. ${ex.name}", fontWeight = FontWeight.Bold)
                            Text("Diff: ${ex.difficulty} | Equip: ${ex.equipment}", fontSize = 12.sp)
                            Text("Primary: ${ex.primaryMuscle}", fontSize = 12.sp)
                            Text("Sets/Reps: ${ex.typicalSetsReps}", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
