package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.launch
import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.example.myapplication.utils.calculateAdvancedBMR
import com.example.myapplication.utils.calculateEnhancedTDEE
import com.example.myapplication.utils.calculateSmartCalories
import com.example.myapplication.utils.calculateOptimalMacros
import com.example.myapplication.data.AlgorithmData
import com.example.myapplication.data.PlanResult
import com.example.myapplication.domain.generateAdvancedCustomPlan
import com.example.myapplication.domain.generatePlanWeeks
import com.example.myapplication.domain.determineOptimalTrainingDays
import com.example.myapplication.domain.generateIntelligentTrainingPlan
import com.example.myapplication.domain.calculateOptimalSessionLength
import com.example.myapplication.domain.generatePersonalizedTips
import kotlin.math.*

@Composable
fun BodyPlanQuizScreen(
    onBack: () -> Unit = {},
    onFinish: (PlanResult) -> Unit = {},
    onQuizDataCollected: ((Map<String, Any>) -> Unit)? = null
) {
    // State for all fields
    var step by remember { mutableStateOf(0) }
    val totalSteps = 14 // Increased for workout duration step

    var gender by remember { mutableStateOf<String?>(null) }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf<String?>(null) }
    var experience by remember { mutableStateOf<String?>(null) }
    var trainingLocation by remember { mutableStateOf<String?>(null) }
    var frequency by remember { mutableStateOf<String?>(null) }
    var workoutDuration by remember { mutableStateOf<String?>(null) } // Added workout duration state
    var equipment by remember { mutableStateOf<List<String>>(emptyList()) } // Added equipment state
    var focusAreas by remember { mutableStateOf<List<String>>(emptyList()) } // Added focus areas state
    var limitations by remember { mutableStateOf<List<String>>(emptyList()) }
    var nutrition by remember { mutableStateOf<String?>(null) }
    var sleep by remember { mutableStateOf<String?>(null) }

    // UI colors
    val buttonBlue = MaterialTheme.colorScheme.primary
    val accentYellow = MaterialTheme.colorScheme.secondary
    val softGreen = Color(0xFF13EF92)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val scroll = rememberScrollState()
        Card(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
                .align(Alignment.Center)
                .verticalScroll(scroll),
            elevation = CardDefaults.cardElevation(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                Modifier.padding(vertical = 32.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Stepper
                LinearProgressIndicator(
                    progress = { (step + 1f) / (totalSteps + 1f) },
                    color = buttonBlue,
                    trackColor = Color(0xFF2A3553),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .padding(bottom = 14.dp)
                )
                StepHeader(step, accentYellow, buttonBlue)
                Spacer(Modifier.height(12.dp))
                // STEPS
                when (step) {
                    0 -> GenderStep(gender, onGender = { gender = it; step++ }, accentYellow, buttonBlue)
                    1 -> AgeStep(age, onAge = { age = it }, onNext = { if (age.isNotBlank() && age.toIntOrNull() in 5..99) step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    2 -> HeightStep(height, onHeight = { height = it }, onNext = { if (height.isNotBlank() && height.toIntOrNull() in 100..230) step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    3 -> WeightStep(weight, onWeight = { weight = it }, onNext = { if (weight.isNotBlank() && weight.toIntOrNull() in 20..200) step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    4 -> BodyFatStep(bodyFat, onBodyFat = { bodyFat = it }, onNext = { step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    5 -> GoalStep(goal, onGoal = { goal = it; step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    6 -> ExperienceStep(experience, onExperience = { experience = it; step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    7 -> TrainingLocationStep(trainingLocation, onLocation = { trainingLocation = it; step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    8 -> FrequencyStep(frequency, onFrequency = { frequency = it; step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    9 -> WorkoutDurationStep(workoutDuration, onDuration = { workoutDuration = it; step++ }, onBack = { step-- }, accentYellow, buttonBlue) // New step
                    10 -> EquipmentStep(equipment, onEquipment = { equipment = it; step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    11 -> FocusAreaStep(focusAreas, onFocus = { focusAreas = it; step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    12 -> LimitationStep(limitations, onLimitations = { limitations = it }, onNext = { step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    13 -> NutritionSleepStep(nutrition, sleep, onNutrition = { nutrition = it }, onSleep = { sleep = it }, onNext = { step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    else -> PlanResultStep(
                        gender = gender, age = age, height = height, weight = weight, bodyFat = bodyFat, goal = goal, experience = experience,
                        trainingLocation = trainingLocation, frequency = frequency, workoutDuration = workoutDuration, equipment = equipment, focusAreas = focusAreas,
                        limitations = limitations, nutrition = nutrition, sleep = sleep,
                        accentYellow = accentYellow, buttonBlue = buttonBlue, softGreen = softGreen, onBack = onBack, onFinish = onFinish,
                        onQuizDataCollected = onQuizDataCollected
                    )
                }
            }
        }
    }
}

// --- Stepper Header ---
@Composable
private fun StepHeader(step: Int, accentYellow: Color, buttonBlue: Color) {
    val stepTitles = listOf(
        "Gender", "Age", "Height (cm)", "Weight (kg)", "Body Fat %", "Main goal", "Experience", "Training location", "Training frequency", "Workout duration", "Equipment", "Focus Areas", "Limitations", "Nutrition & Sleep", "Your plan"
    )
    Text(
        text = "Step ${step + 1}/${stepTitles.size}: ${stepTitles.getOrElse(step) { "" }}",
        color = accentYellow,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    )
}

// --- INDIVIDUAL STEPS ---

@Composable
private fun GenderStep(selected: String?, onGender: (String) -> Unit, accentYellow: Color, buttonBlue: Color) {
    Text("Select your gender", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        GenderChip(selected == "Male", "Male", Icons.Filled.Male, onGender, buttonBlue, accentYellow)
        Spacer(Modifier.width(18.dp))
        GenderChip(selected == "Female", "Female", Icons.Filled.Female, onGender, buttonBlue, accentYellow)
    }
}

@Composable
private fun GenderChip(selected: Boolean, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: (String) -> Unit, blue: Color, yellow: Color) {
    Button(
        onClick = { onClick(label) },
        colors = ButtonDefaults.buttonColors(containerColor = if (selected) yellow else blue),
        shape = RoundedCornerShape(50),
        modifier = Modifier.height(54.dp)
    ) {
        Icon(icon, null, tint = if (selected) blue else yellow, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 17.sp, color = if (selected) blue else Color.White)
    }
}

@Composable
private fun AgeStep(
    age: String, onAge: (String) -> Unit, onNext: () -> Unit, onBack: () -> Unit, yellow: Color, blue: Color
) {
    Text("How old are you?", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = age,
        onValueChange = { if (it.length <= 2) onAge(it.filter { c -> c.isDigit() }) },
        label = { Text("Age (e.g. 24)", color = yellow) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = blue,
            unfocusedBorderColor = yellow,
            focusedLabelColor = blue,
            cursorColor = yellow
        )
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
        Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = blue)) {
            Text("Next", color = Color.White)
        }
    }
}

@Composable
private fun HeightStep(
    height: String, onHeight: (String) -> Unit, onNext: () -> Unit, onBack: () -> Unit, yellow: Color, blue: Color
) {
    Text("What is your height?", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = height,
        onValueChange = { if (it.length <= 3) onHeight(it.filter { c -> c.isDigit() }) },
        label = { Text("Height in cm (e.g. 180)", color = yellow) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = blue,
            unfocusedBorderColor = yellow,
            focusedLabelColor = blue,
            cursorColor = yellow
        )
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
        Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = blue)) {
            Text("Next", color = Color.White)
        }
    }
}

@Composable
private fun WeightStep(
    weight: String, onWeight: (String) -> Unit, onNext: () -> Unit, onBack: () -> Unit, yellow: Color, blue: Color
) {
    Text("What is your weight?", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = weight,
        onValueChange = { if (it.length <= 3) onWeight(it.filter { c -> c.isDigit() }) },
        label = { Text("Weight in kg (e.g. 75)", color = yellow) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = blue,
            unfocusedBorderColor = yellow,
            focusedLabelColor = blue,
            cursorColor = yellow
        )
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
        Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = blue)) {
            Text("Next", color = Color.White)
        }
    }
}

@Composable
private fun BodyFatStep(
    bodyFat: String, onBodyFat: (String) -> Unit, onNext: () -> Unit, onBack: () -> Unit, yellow: Color, blue: Color
) {
    Text("What is your body fat percentage?", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text("(Optional - leave empty if unknown)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    Spacer(Modifier.height(20.dp))
    OutlinedTextField(
        value = bodyFat,
        onValueChange = {
            if (it.length <= 2 && (it.isEmpty() || it.toIntOrNull() in 1..50)) {
                onBodyFat(it.filter { c -> c.isDigit() })
            }
        },
        label = { Text("Body fat % (e.g. 15)", color = yellow) },
        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = blue,
            unfocusedBorderColor = yellow,
            focusedLabelColor = blue,
            cursorColor = yellow
        )
    )
    Spacer(Modifier.height(12.dp))
    Text("Body fat estimation guide:", color = yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    Text("• Male: 6-13% (athlete), 14-17% (fit), 18-24% (average)", color = Color.DarkGray, fontSize = 13.sp)
    Text("• Female: 16-20% (athlete), 21-24% (fit), 25-31% (average)", color = Color.DarkGray, fontSize = 13.sp)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
        Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = blue)) {
            Text("Next", color = Color.White)
        }
    }
}

@Composable
private fun GoalStep(goal: String?, onGoal: (String) -> Unit, onBack: () -> Unit, yellow: Color, blue: Color) {
    Text("What is your main goal?", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(18.dp))
    Column {
        listOf(
            "Build muscle" to "Gain lean mass and strength",
            "Lose fat" to "Reduce body fat while preserving muscle",
            "Recomposition" to "Build muscle and lose fat simultaneously",
            "Improve endurance" to "Boost cardiovascular fitness",
            "General health" to "Overall fitness and wellness"
        ).forEach { (goalText, description) ->
            Button(
                onClick = { onGoal(goalText) },
                colors = ButtonDefaults.buttonColors(containerColor = if (goal == goalText) yellow else blue),
                shape = RoundedCornerShape(25),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Column {
                    Text(goalText, color = if (goal == goalText) blue else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(description, color = if (goal == goalText) blue.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
    }
}

@Composable
private fun ExperienceStep(experience: String?, onExperience: (String) -> Unit, onBack: () -> Unit, yellow: Color, blue: Color) {
    Text("What is your training experience?", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    Column {
        listOf(
            "Beginner" to "Less than 1 year of consistent training",
            "Intermediate" to "1-3 years of regular training",
            "Advanced" to "3+ years with structured programs"
        ).forEach { (level, description) ->
            Button(
                onClick = { onExperience(level) },
                colors = ButtonDefaults.buttonColors(containerColor = if (experience == level) yellow else blue),
                shape = RoundedCornerShape(25),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
            ) {
                Column {
                    Text(level, color = if (experience == level) blue else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(description, color = if (experience == level) blue.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
    }
}

@Composable
private fun TrainingLocationStep(selected: String?, onLocation: (String) -> Unit, onBack: () -> Unit, yellow: Color, blue: Color) {
    Text("Where will you train?", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { onLocation("Gym") },
            colors = ButtonDefaults.buttonColors(containerColor = if (selected == "Gym") yellow else blue),
            shape = RoundedCornerShape(50),
            modifier = Modifier.height(54.dp)
        ) {
            Text("At the gym", color = if (selected == "Gym") blue else Color.White, fontSize = 17.sp)
        }
        Spacer(Modifier.width(18.dp))
        Button(
            onClick = { onLocation("Home") },
            colors = ButtonDefaults.buttonColors(containerColor = if (selected == "Home") yellow else blue),
            shape = RoundedCornerShape(50),
            modifier = Modifier.height(54.dp)
        ) {
            Text("At home", color = if (selected == "Home") blue else Color.White, fontSize = 17.sp)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
    }
}

@Composable
private fun FrequencyStep(frequency: String?, onFrequency: (String) -> Unit, onBack: () -> Unit, yellow: Color, blue: Color) {
    Text("How many times per week do you want to train?", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    Column {
        listOf("2x", "3x", "4x", "5x", "6x").forEach {
            Button(
                onClick = { onFrequency(it) },
                colors = ButtonDefaults.buttonColors(containerColor = if (frequency == it) yellow else blue),
                shape = RoundedCornerShape(25),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Text(it, color = if (frequency == it) blue else Color.White, fontSize = 16.sp)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
    }
}

@Composable
private fun WorkoutDurationStep(duration: String?, onDuration: (String) -> Unit, onBack: () -> Unit, yellow: Color, blue: Color) {
    Text("How long should each workout be?", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Choose the time you can dedicate per session", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
    Spacer(Modifier.height(14.dp))
    Column {
        listOf(
            "15-30 min" to "Quick workout",
            "30-45 min" to "Standard workout",
            "45-60 min" to "Extended workout",
            "60+ min" to "Full session"
        ).forEach { (time, description) ->
            Button(
                onClick = { onDuration(time) },
                colors = ButtonDefaults.buttonColors(containerColor = if (duration == time) yellow else blue),
                shape = RoundedCornerShape(25),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(time, color = if (duration == time) blue else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(description, color = if (duration == time) blue.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
    }
}

@Composable
private fun LimitationStep(
    selected: List<String>,
    onLimitations: (List<String>) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    yellow: Color, blue: Color
) {
    val allLimitations = listOf("None", "Knee injury", "Shoulder injury", "Back pain", "Asthma", "High blood pressure", "Diabetes", "Other")
    val selectedSet = selected.toMutableSet()
    Text("Do you have any limitations?", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(14.dp))
    Column {
        allLimitations.forEach {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = selectedSet.contains(it),
                    onCheckedChange = { checked ->
                        if (checked) selectedSet.add(it) else selectedSet.remove(it)
                        onLimitations(selectedSet.toList())
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = yellow,
                        uncheckedColor = blue
                    )
                )
                Text(it, color = Color.Black)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = blue)
        ) {
            Text("Next", color = Color.White)
        }
    }
}

@Composable
private fun EquipmentStep(
    selected: List<String>, onEquipment: (List<String>) -> Unit, onBack: () -> Unit, yellow: Color, blue: Color
) {
    var currentSelection by remember { mutableStateOf(selected) }
    val options = listOf("Dumbbells", "Barbell", "Kettlebell", "Pull-up Bar", "Resistance Bands", "Bench", "Treadmill", "None (Bodyweight)")

    Text("What equipment do you have?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Select all that apply", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)

    Spacer(Modifier.height(20.dp))

    Column {
        options.forEach { option ->
            Button(
                onClick = {
                    currentSelection = if (option == "None (Bodyweight)") {
                        if (currentSelection.contains(option)) emptyList() else listOf(option)
                    } else {
                        val newSet = currentSelection.toMutableSet()
                        newSet.remove("None (Bodyweight)")
                        if (newSet.contains(option)) newSet.remove(option) else newSet.add(option)
                        newSet.toList()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(0.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = currentSelection.contains(option),
                        onCheckedChange = { isChecked ->
                            currentSelection = if (option == "None (Bodyweight)") {
                                if (isChecked) listOf(option) else emptyList()
                            } else {
                                val newSet = currentSelection.toMutableSet()
                                newSet.remove("None (Bodyweight)")
                                if (isChecked) newSet.add(option) else newSet.remove(option)
                                newSet.toList()
                            }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = blue, uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(option, modifier = Modifier.padding(start = 8.dp), color = Color.White)
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
        Button(
            onClick = { onEquipment(currentSelection) },
            colors = ButtonDefaults.buttonColors(containerColor = blue)
        ) {
            Text("Next", color = Color.White)
        }
    }
}

@Composable
private fun FocusAreaStep(
    selected: List<String>, onFocus: (List<String>) -> Unit, onBack: () -> Unit, yellow: Color, blue: Color
) {
    var currentSelection by remember { mutableStateOf(selected) }
    val options = listOf("Upper body", "Lower body", "Core", "Cardio", "Flexibility", "Balance", "None")

    Text("What are your focus areas?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text("Select all that apply", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)

    Spacer(Modifier.height(20.dp))

    Column {
        options.forEach { option ->
            Button(
                onClick = {
                    currentSelection = if (option == "None") {
                        if (currentSelection.contains(option)) emptyList() else listOf(option)
                    } else {
                        val newSet = currentSelection.toMutableSet()
                        newSet.remove("None")
                        if (newSet.contains(option)) newSet.remove(option) else newSet.add(option)
                        newSet.toList()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(0.dp),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = currentSelection.contains(option),
                        onCheckedChange = { isChecked ->
                            currentSelection = if (option == "None") {
                                if (isChecked) listOf(option) else emptyList()
                            } else {
                                val newSet = currentSelection.toMutableSet()
                                newSet.remove("None")
                                if (isChecked) newSet.add(option) else newSet.remove(option)
                                newSet.toList()
                            }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = blue, uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Text(option, modifier = Modifier.padding(start = 8.dp), color = Color.White)
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
        Button(
            onClick = {
                // "None" pomeni brez specifičnega fokusa → Full Body
                val toSave = if (currentSelection.isEmpty() ||
                    currentSelection.any { it.equals("None", ignoreCase = true) }
                ) {
                    listOf("Full Body")
                } else {
                    currentSelection
                }
                onFocus(toSave)
            },
            colors = ButtonDefaults.buttonColors(containerColor = blue)
        ) {
            Text("Next", color = Color.White)
        }
    }
}

@Composable
private fun NutritionSleepStep(
    nutrition: String?,
    sleep: String?,
    onNutrition: (String) -> Unit,
    onSleep: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    yellow: Color, blue: Color
) {
    Text("Nutrition preferences & sleep", color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text("Any dietary restrictions?", color = Color.Black)
    Column {
        listOf("None", "Vegetarian", "Vegan", "Gluten-free", "Keto/LCHF", "Intermittent fasting", "Other").forEach {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = nutrition == it,
                    onClick = { onNutrition(it) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = yellow,
                        unselectedColor = blue
                    )
                )
                Text(it, color = Color.White)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Text("How many hours do you sleep per night on average?", color = Color.White)
    Column {
        listOf("Less than 6", "6-7", "7-8", "8-9", "9+").forEach {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    selected = sleep == it,
                    onClick = { onSleep(it) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = yellow,
                        unselectedColor = blue
                    )
                )
                Text(it, color = Color.White)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = blue)
        ) {
            Text("Finish", color = Color.White)
        }
    }
}

// --- UPGRADED PLAN RESULT + ALGORITHM ---

@Composable
private fun PlanResultStep(
    gender: String?, age: String, height: String, weight: String, bodyFat: String?,
    goal: String?, experience: String?, trainingLocation: String?, frequency: String?,
    workoutDuration: String?, // Added parameter
    equipment: List<String>,
    focusAreas: List<String>, // Added parameter
    limitations: List<String>, nutrition: String?, sleep: String?,
    accentYellow: Color, buttonBlue: Color, softGreen: Color,
    onBack: () -> Unit, onFinish: (PlanResult) -> Unit,
    onQuizDataCollected: ((Map<String, Any>) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val plan = remember(gender, age, height, weight, bodyFat, goal, experience, trainingLocation, frequency, workoutDuration, equipment, focusAreas, limitations, nutrition, sleep) {
        generateAdvancedCustomPlan(
            gender, age, height, weight, bodyFat, goal, experience, trainingLocation, frequency, workoutDuration, equipment, focusAreas, limitations, nutrition, sleep
        )
    }

    // ✅ NAMESTO TEGA DAJ:
    val algorithmData = remember(height, weight, age, gender, goal, experience, bodyFat) {
        val heightInt = height.toIntOrNull()
        val weightInt = weight.toIntOrNull()
        val ageInt = age.toIntOrNull()

        if (heightInt != null && weightInt != null && ageInt != null && gender != null) {
            val weightKg = weightInt.toDouble()
            val heightCm = heightInt.toDouble()
            val heightM = heightCm / 100.0
            val bmi = weightKg / (heightM * heightM)
            val isMale = gender == "Male"
            val ageYears = ageInt
            val bodyFatPercent = bodyFat?.toDoubleOrNull()

            // Izračuni
            val bmr = calculateAdvancedBMR(weightKg, heightCm, ageYears, isMale, bodyFatPercent)
            val tdee = calculateEnhancedTDEE(bmr, frequency, experience, ageYears, limitations, sleep)
            val targetCalories = calculateSmartCalories(tdee, goal, experience, bmi, ageYears, isMale, bodyFatPercent, limitations)
            val macros = calculateOptimalMacros(targetCalories, weightKg, goal, experience, ageYears, isMale, bodyFatPercent, nutrition, limitations)
            val proteinPerKg = macros.first.toDouble() / weightKg
            val caloriesPerKg = targetCalories / weightKg
            val trainingDays = frequency?.replace("x", "")?.toIntOrNull() ?: 3
            val trainingPlan = generateIntelligentTrainingPlan(goal, experience, trainingLocation, trainingDays, limitations, ageYears, isMale, bodyFatPercent)

            fun getBMICategory(bmi: Double): String = when {
                bmi < 18.5 -> "Underweight"
                bmi < 25.0 -> "Normal weight"
                bmi < 30.0 -> "Overweight"
                else -> "Obese"
            }

            AlgorithmData(
                bmi = bmi,
                bmr = bmr,
                tdee = tdee,
                proteinPerKg = proteinPerKg,
                caloriesPerKg = caloriesPerKg,
                caloricStrategy = "Calculated deficit/surplus: ${"%.0f".format(tdee - targetCalories)} kcal",
                detailedTips = listOf(
                    "BMI: ${"%.1f".format(bmi)} - ${getBMICategory(bmi)}",
                    "BMR: ${bmr.toInt()} kcal (basal metabolic rate)",
                    "TDEE: ${tdee.toInt()} kcal (total daily energy expenditure)",
                    "Protein goal: ${"%.1f".format(proteinPerKg)}g per kg body weight",
                    "Daily caloric need: ${"%.0f".format(targetCalories)} kcal",
                    "Recommended intake: ${"%.0f".format(targetCalories)} kcal/day",
                    "Training frequency: ${frequency} days per week optimal for $experience level",
                    "Sleep optimization: 8-9 hours recommended for recovery"
                ),
                macroBreakdown = "Protein: ${"%.1f".format(proteinPerKg)}g/kg (${macros.first}g total), " +
                        "Carbs: ${macros.second}g, Fat: ${macros.third}g, " +
                        "Calories: ${"%.0f".format(targetCalories)} kcal/day, " +
                        "Fat loss deficit: ${"%.0f".format(tdee - targetCalories)} kcal/day",
                trainingStrategy = trainingPlan
            )
        } else null
    }

    var planName by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Your personalized plan", color = accentYellow, fontWeight = FontWeight.Bold, fontSize = 23.sp)
        Spacer(Modifier.height(14.dp))
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = softGreen, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(16.dp))

        // Enhanced plan display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF232D4B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Daily Nutrition Target", color = accentYellow, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                Text("🔥 Calories: ${plan.calories} kcal", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("🥩 Protein: ${plan.protein}g", color = Color(0xFF13EF92), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("🍞 Carbs: ${plan.carbs}g", color = Color(0xFF33aaff), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("🥑 Fat: ${plan.fat}g", color = Color(0xFFF04C4C), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }

                Spacer(Modifier.height(12.dp))
                val proteinPerKg = (plan.protein.toFloat() / weight.toIntOrNull()!!).let { "%.1f".format(it) }
                val caloriesPerKg = (plan.calories.toFloat() / weight.toIntOrNull()!!).let { "%.1f".format(it) }
                Text("📊 ${proteinPerKg}g protein/kg • ${caloriesPerKg} kcal/kg", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

                // ✅ NOVO - PRIKAŽI ALGORITHM DATA
                algorithmData?.let { data ->
                    Spacer(Modifier.height(8.dp))
                    Text("📈 BMI: ${"%.1f".format(data.bmi ?: 0.0)} • BMR: ${data.bmr?.toInt() ?: 0} kcal • TDEE: ${data.tdee?.toInt() ?: 0} kcal",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF232D4B)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Training Program", color = accentYellow, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("📍 Location: ${plan.trainingLocation}", color = softGreen, fontSize = 15.sp)
                Text("🗓️ Frequency: ${plan.trainingDays}x per week", color = Color.White, fontSize = 15.sp)
                Text("⏱️ Session length: ${plan.sessionLength} min", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Text(plan.trainingPlan, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = planName,
            onValueChange = {
                planName = it
                nameError = false
            },
            label = { Text("Plan name") },
            isError = nameError,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (nameError) {
            Text("Name is required.", color = Color.Red, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))

        if (plan.tips.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF232D4B)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("💡 Key Recommendations", color = softGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    plan.tips.take(5).forEach { tip ->
                        Text("• $tip", color = Color.White, fontSize = 14.sp)
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (aiLoading) {
            CircularProgressIndicator(color = accentYellow, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(10.dp))
            Text("Generating your plan...", color = accentYellow)
        } else {
            Button(
                onClick = {
                    if (planName.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    if (com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() == null) {
                        aiError = "Please log in to generate plan"
                        return@Button
                    }

                    aiLoading = true
                    aiError = null

                    // Generate plan locally (NO AI call)
                    scope.launch {
                        try {
                            // Zberi quiz podatke za shranjevanje v UserProfile
                            val quizData = mapOf(
                                "gender" to (gender ?: ""),
                                "age" to age,
                                "height" to height,
                                "weight" to weight,
                                "bodyFat" to (bodyFat ?: ""),
                                "goal" to (goal ?: ""),
                                "experience" to (experience ?: ""),
                                "trainingLocation" to (trainingLocation ?: ""),
                                "frequency" to (frequency ?: ""),
                                "equipment" to equipment,
                                "focusAreas" to focusAreas,
                                "limitations" to limitations,
                                "nutrition" to (nutrition ?: ""),
                                "sleep" to (sleep ?: "")
                            )
                            onQuizDataCollected?.invoke(quizData)

                            val localPlan = generateAdvancedCustomPlan(
                                gender, age, height, weight, bodyFat, goal, experience,
                                trainingLocation, frequency, workoutDuration, equipment, focusAreas, limitations, nutrition, sleep
                            ).copy(
                                name = planName,
                                createdAt = System.currentTimeMillis(),
                                algorithmData = algorithmData
                            )
                            aiLoading = false
                            aiError = null
                            onFinish(localPlan)

                            // Save weight to weightLogs for Progress graph
                            val weightKg = weight.toDoubleOrNull()
                            val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                            if (weightKg != null && uid != null) {
                                try {
                                    val dateStr = java.time.LocalDate.now().toString()
                                    Firebase.firestore
                                        .collection("users").document(uid)
                                        .collection("weightLogs").document(dateStr)
                                        .set(mapOf("date" to dateStr, "weightKg" to weightKg))
                                    Log.d("BodyPlanQuiz", "Saved initial weight $weightKg kg to weightLogs")
                                } catch (e: Exception) {
                                    Log.e("BodyPlanQuiz", "Failed to save weight to weightLogs", e)
                                }
                            }
                        } catch (e: Exception) {
                            aiLoading = false
                            aiError = "Error generating plan: ${e.message}"
                            Log.e("BodyPlanQuiz", "Plan generation error", e)
                        }
                    }
                },
                enabled = planName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = buttonBlue),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save plan", color = Color.White, fontSize = 17.sp)
            }
            if (aiError != null) {
                Text(aiError ?: "", color = Color.Red)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) { Text("Back", color = accentYellow) }
    }
}



