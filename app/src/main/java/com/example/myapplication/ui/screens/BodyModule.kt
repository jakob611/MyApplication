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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay
import android.util.Log
import com.example.myapplication.persistence.PlanDataStore
import kotlin.math.*

@Composable
fun BodyPlanQuizScreen(
    onBack: () -> Unit = {},
    onFinish: (PlanResult) -> Unit = {}
) {
    // State for all fields
    var step by remember { mutableStateOf(0) }
    val totalSteps = 12 // Increased for body fat step

    var gender by remember { mutableStateOf<String?>(null) }
    var age by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf<String?>(null) }
    var experience by remember { mutableStateOf<String?>(null) }
    var trainingLocation by remember { mutableStateOf<String?>(null) }
    var frequency by remember { mutableStateOf<String?>(null) }
    var limitations by remember { mutableStateOf<List<String>>(emptyList()) }
    var nutrition by remember { mutableStateOf<String?>(null) }
    var sleep by remember { mutableStateOf<String?>(null) }

    // UI colors
    val backgroundGradient = Brush.verticalGradient(
        listOf(
            Color(0xFF17223B), Color(0xFF25304A), Color(0xFF193446), Color(0xFF1E2D24)
        )
    )
    val buttonBlue = Color(0xFF2563EB)
    val accentYellow = Color(0xFFFEE440)
    val softGreen = Color(0xFF13EF92)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        val scroll = rememberScrollState()
        Card(
            modifier = Modifier
                .padding(18.dp)
                .fillMaxWidth()
                .align(Alignment.Center)
                .verticalScroll(scroll),
            elevation = CardDefaults.cardElevation(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C274C)),
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                Modifier.padding(vertical = 32.dp, horizontal = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Stepper
                LinearProgressIndicator(
                    progress = (step + 1f) / (totalSteps + 1f),
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
                    9 -> LimitationStep(limitations, onLimitations = { limitations = it }, onNext = { step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    10 -> NutritionSleepStep(nutrition, sleep, onNutrition = { nutrition = it }, onSleep = { sleep = it }, onNext = { step++ }, onBack = { step-- }, accentYellow, buttonBlue)
                    else -> PlanResultStep(
                        gender, age, height, weight, bodyFat, goal, experience, trainingLocation, frequency, limitations, nutrition, sleep,
                        accentYellow, buttonBlue, softGreen, onBack, onFinish
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
        "Gender", "Age", "Height (cm)", "Weight (kg)", "Body Fat %", "Main goal", "Experience", "Training location", "Training frequency", "Limitations", "Nutrition & Sleep", "Your plan"
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
    Text("Select your gender", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    Text("How old are you?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    Text("What is your height?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    Text("What is your weight?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    Text("What is your body fat percentage?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text("(Optional - leave empty if unknown)", color = Color.Gray, fontSize = 14.sp)
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
    Text("• Male: 6-13% (athlete), 14-17% (fit), 18-24% (average)", color = Color.White, fontSize = 13.sp)
    Text("• Female: 16-20% (athlete), 21-24% (fit), 25-31% (average)", color = Color.White, fontSize = 13.sp)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = onBack) { Text("Back", color = yellow) }
        Button(onClick = onNext, colors = ButtonDefaults.buttonColors(containerColor = blue)) {
            Text("Next", color = Color.White)
        }
    }
}

@Composable
private fun GoalStep(goal: String?, onGoal: (String) -> Unit, onBack: () -> Unit, yellow: Color, blue: Color) {
    Text("What is your main goal?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    Text("What is your training experience?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    Text("Where will you train?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    Text("How many times per week do you want to train?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
private fun LimitationStep(
    selected: List<String>,
    onLimitations: (List<String>) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    yellow: Color, blue: Color
) {
    val allLimitations = listOf("None", "Knee injury", "Shoulder injury", "Back pain", "Asthma", "High blood pressure", "Diabetes", "Other")
    val selectedSet = selected.toMutableSet()
    Text("Do you have any limitations?", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
    Text("Nutrition preferences & sleep", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text("Any dietary restrictions?", color = Color.White)
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
    limitations: List<String>, nutrition: String?, sleep: String?,
    accentYellow: Color, buttonBlue: Color, softGreen: Color,
    onBack: () -> Unit, onFinish: (PlanResult) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val plan = remember(gender, age, height, weight, bodyFat, goal, experience, trainingLocation, frequency, limitations, nutrition, sleep) {
        generateAdvancedCustomPlan(
            gender, age, height, weight, bodyFat, goal, experience, trainingLocation, frequency, limitations, nutrition, sleep
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
                Text("📊 ${proteinPerKg}g protein/kg • ${caloriesPerKg} kcal/kg", color = Color.Gray, fontSize = 13.sp)

                // ✅ NOVO - PRIKAŽI ALGORITHM DATA
                algorithmData?.let { data ->
                    Spacer(Modifier.height(8.dp))
                    Text("📈 BMI: ${"%.1f".format(data.bmi)} • BMR: ${data.bmr.toInt()} kcal • TDEE: ${data.tdee.toInt()} kcal",
                        color = Color.Gray, fontSize = 12.sp)
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
            Text("AI is creating your advanced plan...", color = accentYellow)
        } else {
            Button(
                onClick = {
                    if (planName.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    if (PlanDataStore.getCurrentUserId() == null) {
                        aiError = "Please log in to generate AI plan"
                        return@Button
                    }

                    aiLoading = true
                    aiError = null
                    val trainingDaysInt = when(frequency) {
                        "2x" -> 2
                        "3x" -> 3
                        "4x" -> 4
                        "5x" -> 5
                        "6x" -> 6
                        else -> 3
                    }
                    val quizData = mapOf(
                        "gender" to (gender ?: ""),
                        "age" to age,
                        "height" to height,
                        "weight" to weight,
                        "bodyFat" to (bodyFat ?: ""),
                        "goal" to (goal ?: ""),
                        "experience" to (experience ?: ""),
                        "training_location" to (trainingLocation ?: ""),
                        "trainingDays" to trainingDaysInt,
                        "limitations" to limitations,
                        "nutrition" to (nutrition ?: ""),
                        "sleep" to (sleep ?: "")
                    )

                    Log.d("BodyPlanQuiz", "Sending quiz data: $quizData")

                    PlanDataStore.requestAIPlan(
                        quizData = quizData,
                        onResult = { aiPlan ->
                            aiLoading = false
                            // ✅ DODAJ TA LOG:
                            Log.d("DEBUG_ALGORITHM", "algorithmData before saving: $algorithmData")
                            // ✅ DODAJ ALGORITHM DATA V AI PLAN
                            // V PlanResultStep funkciji kjer je:
                            onFinish(aiPlan.copy(
                                name = planName,
                                createdAt = System.currentTimeMillis(),
                                algorithmData = algorithmData
                            ))

// DODAJ TA LOG PRED onFinish:
                            Log.d("DEBUG_SAVE", "Sending to onFinish: algorithmData = $algorithmData")
                            Log.d("DEBUG_SAVE", "algorithmData is null: ${algorithmData == null}")
                            if (algorithmData != null) {
                                Log.d("DEBUG_SAVE", "BMI: ${algorithmData.bmi}, BMR: ${algorithmData.bmr}")
                            }
                        },
                        onError = { err ->
                            aiLoading = false
                            aiError = err
                            Log.e("BodyPlanQuiz", "AI Plan error: $err")

                            if (
                                err?.contains("timeout") == true ||
                                err?.contains("AI is taking longer") == true ||
                                err?.contains("Server error") == true ||
                                err?.contains("Failed to parse") == true ||
                                err?.contains("AI did not return valid plan") == true
                            ) {
                                aiError = "Error generating AI plan. Using local plan..."
                                scope.launch {
                                    delay(2000)
                                    val localPlan = generateAdvancedCustomPlan(
                                        gender, age, height, weight, bodyFat, goal, experience,
                                        trainingLocation, frequency, limitations, nutrition, sleep
                                    ).copy(
                                        name = planName,
                                        createdAt = System.currentTimeMillis(),
                                        algorithmData = algorithmData  // ✅ DODAJ TUDI V LOKALNI PLAN
                                    )
                                    aiLoading = false
                                    aiError = null
                                    onFinish(localPlan)
                                }
                            }
                        }
                    )
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

// --- COMPLETELY UPGRADED ALGORITHM ---

fun generateAdvancedCustomPlan(
    gender: String?, age: String, height: String, weight: String, bodyFat: String?,
    goal: String?, experience: String?, trainingLocation: String?, frequency: String?,
    limitations: List<String>, nutrition: String?, sleep: String?
): PlanResult {

    // Parse basic data with enhanced validation
    val weightKg = weight.toDoubleOrNull() ?: 70.0
    val heightCm = height.toDoubleOrNull() ?: 175.0
    val ageYears = age.toIntOrNull() ?: 25
    val bodyFatPercent = bodyFat?.toDoubleOrNull()
    val isMale = gender == "Male"

    // Advanced body composition analysis
    val heightM = heightCm / 100.0
    val bmi = weightKg / (heightM * heightM)

    // Enhanced BMR calculation with body fat consideration
    val bmr = calculateAdvancedBMR(weightKg, heightCm, ageYears, isMale, bodyFatPercent)

    // Sophisticated TDEE calculation
    val tdee = calculateEnhancedTDEE(bmr, frequency, experience, ageYears, limitations, sleep)

    // Advanced caloric target with multiple factors
    val targetCalories = calculateSmartCalories(tdee, goal, experience, bmi, ageYears, isMale, bodyFatPercent, limitations)

    // Precision macronutrient distribution
    val macros = calculateOptimalMacros(targetCalories, weightKg, goal, experience, ageYears, isMale, bodyFatPercent, nutrition, limitations)

    // Intelligent training program design
    val trainingDays = determineOptimalTrainingDays(frequency, experience, ageYears, goal, limitations)
    val trainingPlan = generateIntelligentTrainingPlan(goal, experience, trainingLocation, trainingDays, limitations, ageYears, isMale, bodyFatPercent)

    // Dynamic session length calculation
    val sessionLength = calculateOptimalSessionLength(experience, goal, trainingLocation, ageYears, limitations, trainingDays)

    // Comprehensive, personalized recommendations
    val tips = generatePersonalizedTips(
        goal, experience, trainingLocation, limitations, nutrition, sleep,
        ageYears, isMale, bmi, trainingDays, macros.first, macros.second, macros.third,
        bodyFatPercent, targetCalories, tdee
    )

    return PlanResult(
        weeks = emptyList(),
        id = java.util.UUID.randomUUID().toString(),
        name = "",
        calories = targetCalories.toInt(),
        protein = macros.first,
        carbs = macros.second,
        fat = macros.third,
        trainingPlan = trainingPlan,
        trainingDays = trainingDays,
        sessionLength = sessionLength,
        tips = tips,
        createdAt = System.currentTimeMillis(),
        trainingLocation = trainingLocation ?: "Home",
        experience = experience,
        goal = goal
    )
}

// --- ENHANCED CALCULATION FUNCTIONS ---

fun calculateAdvancedBMR(weight: Double, height: Double, age: Int, isMale: Boolean, bodyFat: Double?): Double {
    return if (bodyFat != null && bodyFat > 0) {
        // Katch-McArdle formula (more accurate with body fat)
        val leanBodyMass = weight * (1 - bodyFat / 100)
        370 + (21.6 * leanBodyMass)
    } else {
        // Enhanced Mifflin-St Jeor with age adjustments
        val baseBMR = if (isMale) {
            10 * weight + 6.25 * height - 5 * age + 5
        } else {
            10 * weight + 6.25 * height - 5 * age - 161
        }

        // Age-based metabolic adjustments (more precise)
        when {
            age < 18 -> baseBMR * 1.12
            age in 18..25 -> baseBMR * 1.05
            age in 26..35 -> baseBMR * 1.0
            age in 36..45 -> baseBMR * 0.97
            age in 46..55 -> baseBMR * 0.94
            age in 56..65 -> baseBMR * 0.91
            else -> baseBMR * 0.87
        }
    }
}

fun calculateEnhancedTDEE(bmr: Double, frequency: String?, experience: String?, age: Int, limitations: List<String>, sleep: String?): Double {
    // Base activity multiplier (more nuanced)
    val baseMultiplier = when (frequency) {
        "2x" -> 1.375
        "3x" -> 1.55
        "4x" -> 1.725
        "5x" -> 1.9
        "6x" -> 2.0
        else -> 1.2
    }

    // Experience efficiency factor
    val experienceMultiplier = when (experience) {
        "Beginner" -> 1.08  // Higher energy expenditure due to inefficiency
        "Intermediate" -> 1.0
        "Advanced" -> 0.96  // More efficient movement patterns
        else -> 1.0
    }

    // Age-based activity adjustment
    val ageMultiplier = when {
        age < 25 -> 1.02
        age in 25..35 -> 1.0
        age in 36..50 -> 0.98
        age > 50 -> 0.95
        else -> 1.0
    }

    // Sleep quality significantly affects metabolism
    val sleepMultiplier = when (sleep) {
        "Less than 6" -> 0.90  // Poor recovery, reduced metabolism
        "6-7" -> 0.97
        "7-8" -> 1.0  // Optimal
        "8-9" -> 1.02
        "9+" -> 1.01  // Diminishing returns
        else -> 1.0
    }

    // Medical limitations adjustment
    val limitationMultiplier = when {
        limitations.contains("Asthma") -> 0.92
        limitations.any { it in listOf("High blood pressure", "Diabetes") } -> 0.94
        limitations.any { it in listOf("Knee injury", "Shoulder injury", "Back pain") } -> 0.96
        else -> 1.0
    }

    return bmr * baseMultiplier * experienceMultiplier * ageMultiplier * sleepMultiplier * limitationMultiplier
}

fun calculateSmartCalories(tdee: Double, goal: String?, experience: String?, bmi: Double, age: Int, isMale: Boolean, bodyFat: Double?, limitations: List<String>): Double {
    val baseCalories = when (goal) {
        "Build muscle" -> {
            val baseSurplus = when (experience) {
                "Beginner" -> 450
                "Intermediate" -> 350
                "Advanced" -> 250
                else -> 350
            }

            // Age and body fat adjustments for muscle building
            val ageFactor = when {
                age < 25 -> 1.0
                age in 25..35 -> 0.95
                age in 36..45 -> 0.85
                age in 46..55 -> 0.75
                else -> 0.65
            }

            val bodyFatFactor = if (bodyFat != null) {
                when {
                    bodyFat < 10 && isMale -> 1.1  // Very lean, can gain more aggressively
                    bodyFat < 18 && !isMale -> 1.1
                    bodyFat > 20 && isMale -> 0.8   // Higher body fat, smaller surplus
                    bodyFat > 28 && !isMale -> 0.8
                    else -> 1.0
                }
            } else 1.0

            tdee + (baseSurplus * ageFactor * bodyFatFactor)
        }

        "Lose fat" -> {
            val baseDeficit = when {
                bmi > 35 -> 750
                bmi > 30 -> 650
                bmi > 27 -> 550
                bmi > 25 -> 450
                else -> 350
            }

            // Gender-specific fat loss adjustments
            val genderFactor = if (isMale) 1.0 else 0.85

            // Age-based metabolic considerations
            val ageFactor = when {
                age > 50 -> 0.85  // Slower fat loss for older adults
                age < 25 -> 1.1   // Faster metabolism in younger people
                else -> 1.0
            }

            val adjustedDeficit = baseDeficit * genderFactor * ageFactor
            val minCalories = if (isMale) 1500.0 else 1200.0

            maxOf(tdee - adjustedDeficit, minCalories)
        }

        "Recomposition" -> {
            when {
                experience == "Beginner" && bmi < 25 -> tdee + 150  // Slight surplus for beginners
                bmi > 25 -> tdee - 200  // Slight deficit for overweight
                bodyFat != null && bodyFat > (if (isMale) 15 else 25) -> tdee - 150
                else -> tdee  // Maintenance
            }
        }

        "Improve endurance" -> {
            val baseSurplus = when (experience) {
                "Advanced" -> 300  // Advanced endurance athletes need more fuel
                "Intermediate" -> 250
                else -> 200
            }
            tdee + baseSurplus
        }

        "General health" -> {
            when {
                bmi > 25 -> tdee - 250  // Gentle deficit for overweight
                bmi < 20 -> tdee + 200  // Slight surplus for underweight
                else -> tdee  // Maintenance for healthy weight
            }
        }

        else -> tdee
    }

    // Additional adjustments for medical conditions
    return when {
        limitations.contains("Diabetes") -> baseCalories * 0.98
        limitations.contains("High blood pressure") -> baseCalories * 0.97
        else -> baseCalories
    }
}

fun calculateOptimalMacros(calories: Double, weight: Double, goal: String?, experience: String?, age: Int, isMale: Boolean, bodyFat: Double?, nutrition: String?, limitations: List<String>): Triple<Int, Int, Int> {

    // Protein calculation with multiple factors
    val baseProteinPerKg = when (goal) {
        "Build muscle" -> when (experience) {
            "Beginner" -> 1.8
            "Intermediate" -> 2.0
            "Advanced" -> 2.2
            else -> 1.9
        }
        "Lose fat" -> when {
            bodyFat != null && bodyFat > (if (isMale) 20 else 30) -> 2.4  // Higher protein for aggressive cuts
            else -> 2.0
        }
        "Recomposition" -> 2.2
        "Improve endurance" -> 1.4
        "General health" -> 1.6
        else -> 1.7
    }

    // Age and gender protein adjustments
    val ageProteinFactor = when {
        age < 25 -> 1.0
        age in 25..40 -> 1.05
        age in 41..55 -> 1.15
        age in 56..70 -> 1.25
        else -> 1.35  // Increased protein needs for elderly
    }

    val genderProteinFactor = if (isMale) 1.0 else 0.95

    // Nutrition style adjustments
    val nutritionProteinFactor = when (nutrition) {
        "Vegetarian", "Vegan" -> 1.15  // Higher total to ensure complete amino acids
        "Keto/LCHF" -> 1.1
        else -> 1.0
    }

    val totalProtein = (baseProteinPerKg * weight * ageProteinFactor * genderProteinFactor * nutritionProteinFactor).toInt()

    // Fat calculation based on goals and health
    val fatPerKg = when {
        nutrition == "Keto/LCHF" -> when (goal) {
            "Build muscle" -> 1.8
            "Lose fat" -> 1.5
            else -> 1.6
        }
        goal == "Lose fat" && bodyFat != null && bodyFat > 25 -> 0.7  // Lower fat for aggressive cuts
        limitations.contains("High blood pressure") -> 0.8  // Lower saturated fat
        isMale -> when {
            age < 30 -> 0.9
            age < 50 -> 1.0
            else -> 1.1
        }
        else -> when {  // Female
            age < 30 -> 1.1  // Higher fat needs for hormones
            age < 50 -> 1.2
            else -> 1.3
        }
    }

    val totalFat = (fatPerKg * weight).toInt()

    // Carbohydrate calculation (remaining calories)
    val proteinCalories = totalProtein * 4
    val fatCalories = totalFat * 9
    val remainingCalories = calories - proteinCalories - fatCalories

    val totalCarbs = when (nutrition) {
        "Keto/LCHF" -> minOf(50, (remainingCalories / 4).toInt())  // Very low carb
        "Intermittent fasting" -> maxOf(100, (remainingCalories / 4).toInt())  // Moderate carb
        else -> maxOf(80, (remainingCalories / 4).toInt())  // Minimum for brain function
    }

    return Triple(totalProtein, totalCarbs, totalFat)
}

fun determineOptimalTrainingDays(frequency: String?, experience: String?, age: Int, goal: String?, limitations: List<String>): Int {
    val baseFrequency = when (frequency) {
        "2x" -> 2
        "3x" -> 3
        "4x" -> 4
        "5x" -> 5
        "6x" -> 6
        else -> 3
    }

    // Age-based frequency adjustments
    val maxRecommended = when {
        age > 60 -> minOf(baseFrequency, 4)
        age > 50 -> minOf(baseFrequency, 5)
        age < 25 && experience == "Advanced" -> baseFrequency
        else -> baseFrequency
    }

    // Experience-based caps
    val experienceCap = when (experience) {
        "Beginner" -> minOf(maxRecommended, 4)
        "Intermediate" -> minOf(maxRecommended, 5)
        "Advanced" -> maxRecommended
        else -> minOf(maxRecommended, 4)
    }

    // Limitations adjustments
    val finalFrequency = if (limitations.any { it in listOf("Asthma", "High blood pressure", "Diabetes") }) {
        minOf(experienceCap, 4)
    } else {
        experienceCap
    }

    return maxOf(finalFrequency, 2)  // Minimum 2 days
}

fun generateIntelligentTrainingPlan(
    goal: String?, experience: String?, location: String?, trainingDays: Int,
    limitations: List<String>, age: Int, isMale: Boolean, bodyFat: Double?
): String {

    val hasKneeIssues = limitations.contains("Knee injury")
    val hasShoulderIssues = limitations.contains("Shoulder injury")
    val hasBackPain = limitations.contains("Back pain")
    val hasAsthma = limitations.contains("Asthma")
    val hasCardiovascular = limitations.any { it in listOf("High blood pressure", "Diabetes") }
    val isHome = location == "Home"

    val cardioModification = when {
        hasAsthma -> "low-intensity steady-state with breathing focus"
        hasCardiovascular -> "moderate-intensity with heart rate monitoring"
        age > 55 -> "low-impact steady-state"
        else -> "mixed intensity (HIIT and steady-state)"
    }

    return when (goal) {
        "Build muscle" -> when {
            experience == "Beginner" && trainingDays <= 3 -> {
                if (isHome) {
                    buildString {
                        append("Beginner full-body routine 3x/week with bodyweight and basic equipment. ")
                        append("Focus: progressive push-ups, squats, lunges, planks. ")
                        if (hasBackPain) append("Emphasize core stability and avoid spinal loading. ")
                        if (hasKneeIssues) append("Use supported squats and avoid deep ranges. ")
                        append("Start with 2 sets, progress to 3 sets over 4 weeks.")
                    }
                } else {
                    buildString {
                        append("Full-body beginner strength program 3x/week. ")
                        append("Master: squats, deadlifts")
                        if (hasBackPain) append(" (rack pulls or trap bar)")
                        append(", bench press")
                        if (hasShoulderIssues) append(" (incline or machine press)")
                        append(", rows, overhead press. Progressive overload focus.")
                    }
                }
            }

            experience == "Intermediate" && trainingDays == 4 -> {
                buildString {
                    append("Upper/Lower split 4x/week. Upper: push/pull supersets. Lower: ")
                    if (hasKneeIssues) {
                        append("machine-based quad/glute work, ")
                    } else {
                        append("squats, deadlift variations, ")
                    }
                    append("unilateral training. Periodized progression.")
                }
            }

            experience == "Advanced" && trainingDays >= 5 -> {
                if (isHome) {
                    "Advanced bodyweight progressions: weighted calisthenics, gymnastic progressions, unilateral strength work. 5-6x/week with skill development focus."
                } else {
                    buildString {
                        append("Push/Pull/Legs/Arms split or body-part specialization. ")
                        append("Advanced techniques: cluster sets, rest-pause, periodization. ")
                        if (bodyFat != null && bodyFat > (if (isMale) 15 else 25)) {
                            append("Include metabolic finishers.")
                        }
                    }
                }
            }

            else -> {
                "Progressive overload strength training with compound focus. Adjust volume based on recovery capacity and age."
            }
        }

        "Lose fat" -> {
            buildString {
                append("Fat loss program combining strength training with $cardioModification. ")
                when {
                    experience == "Beginner" -> append("Circuit training 3x/week with metabolic focus. ")
                    trainingDays >= 4 -> append("Upper/lower split with cardio acceleration. ")
                    else -> append("Full-body strength with superset format. ")
                }
                if (hasCardiovascular) {
                    append("Monitor heart rate, stay in moderate zones.")
                } else if (!hasAsthma && age < 40) {
                    append("Include 2x HIIT sessions weekly.")
                }
            }
        }

        "Recomposition" -> {
            buildString {
                append("Body recomposition program balancing strength and conditioning. ")
                when {
                    experience == "Beginner" -> append("Full-body strength 3x/week with strategic cardio placement. ")
                    trainingDays >= 4 -> append("Push/pull/legs with targeted cardio. Strength priority. ")
                    else -> append("Compound-focused training with minimal but effective cardio. ")
                }
                append("Requires patience and consistent nutrition.")
            }
        }

        "Improve endurance" -> {
            buildString {
                append("Endurance-focused program with $cardioModification. ")
                when {
                    trainingDays <= 3 -> append("2 days strength for injury prevention, 2-3 days aerobic base building. ")
                    trainingDays >= 5 -> append("Periodized approach: 70% aerobic base, 20% tempo, 10% intervals. 2-3 strength days. ")
                    else -> append("3 days aerobic work, 2 days strength training. ")
                }
                if (hasAsthma) append("Focus on breathing patterns and gradual progression.")
            }
        }

        "General health" -> {
            buildString {
                append("Balanced health and wellness program. ")
                append("Combination of strength training (${trainingDays/2 + 1}x), ")
                append("cardiovascular exercise (${trainingDays/2}x), and mobility work. ")
                if (age > 50) append("Emphasis on functional movements and bone health. ")
                if (hasCardiovascular) append("Heart-healthy moderate intensity focus.")
            }
        }

        else -> {
            "Balanced fitness routine combining strength and cardiovascular training based on individual needs and limitations."
        }
    }
}

fun calculateOptimalSessionLength(experience: String?, goal: String?, location: String?, age: Int, limitations: List<String>, trainingDays: Int): Int {
    val baseLength = when (experience) {
        "Beginner" -> 40
        "Intermediate" -> 60
        "Advanced" -> 75
        else -> 55
    }

    val goalAdjustment = when (goal) {
        "Build muscle" -> when (experience) {
            "Advanced" -> 20
            "Intermediate" -> 15
            else -> 5
        }
        "Lose fat" -> -10  // Shorter, more intense
        "Improve endurance" -> 25
        "General health" -> 0
        else -> 0
    }

    val locationAdjustment = when (location) {
        "Home" -> -10  // Less equipment transition time
        "Gym" -> 5     // Travel time between equipment
        else -> 0
    }

    val ageAdjustment = when {
        age < 25 -> 5
        age in 25..40 -> 0
        age in 41..55 -> -5
        age > 55 -> -15
        else -> 0
    }

    val frequencyAdjustment = when {
        trainingDays >= 6 -> -15  // Shorter sessions for high frequency
        trainingDays <= 2 -> 15   // Longer sessions for low frequency
        else -> 0
    }

    val limitationAdjustment = when {
        limitations.any { it in listOf("Asthma", "High blood pressure") } -> -10
        limitations.any { it in listOf("Knee injury", "Shoulder injury", "Back pain") } -> -5
        else -> 0
    }

    val totalLength = baseLength + goalAdjustment + locationAdjustment + ageAdjustment + frequencyAdjustment + limitationAdjustment

    return maxOf(30, minOf(120, totalLength))  // Cap between 30-120 minutes
}

fun generatePersonalizedTips(
    goal: String?, experience: String?, location: String?, limitations: List<String>,
    nutrition: String?, sleep: String?, age: Int, isMale: Boolean, bmi: Double,
    trainingDays: Int, protein: Int, carbs: Int, fat: Int, bodyFat: Double?,
    calories: Double, tdee: Double
): List<String> {

    val tips = mutableListOf<String>()

    // Caloric and metabolic insights
    val deficit = tdee - calories
    when {
        deficit > 300 -> tips.add("⚡ You're in a ${deficit.toInt()} calorie deficit - expect 0.5-0.7kg fat loss per week")
        deficit > 0 -> tips.add("⚡ Moderate ${deficit.toInt()} calorie deficit - sustainable fat loss of 0.3-0.5kg per week")
        deficit < -200 -> tips.add("⚡ ${(-deficit).toInt()} calorie surplus for muscle gain - expect 0.25-0.5kg per week")
        else -> tips.add("⚡ Maintenance calories - ideal for body recomposition")
    }

    // Body composition specific advice
    if (bodyFat != null) {
        when {
            bodyFat < (if (isMale) 10 else 16) -> {
                tips.add("🎯 Very lean physique - focus on performance and muscle quality over further fat loss")
                tips.add("💪 Consider reverse dieting to improve metabolic health")
            }
            bodyFat > (if (isMale) 20 else 30) -> {
                tips.add("🎯 Prioritize fat loss with strength training to preserve muscle mass")
                tips.add("📊 Track measurements and progress photos over scale weight")
            }
            else -> {
                tips.add("🎯 Good body composition range - ideal for recomposition goals")
            }
        }
    }

    // Goal-specific advanced tips
    when (goal) {
        "Build muscle" -> {
            tips.add("🥩 Distribute ${protein}g protein across 4-5 meals (20-40g per meal) for optimal synthesis")
            tips.add("⏰ Consume 20-40g protein within 2 hours post-workout")
            if (experience == "Beginner") {
                tips.add("📈 Linear progression: add 2.5-5kg to compounds weekly for first 3 months")
            } else {
                tips.add("📊 Track volume (sets × reps × weight) and aim for 5-10% weekly increases")
            }
        }

        "Lose fat" -> {
            tips.add("🔥 High protein (${protein}g) preserves muscle in deficit - aim for 0.8-1.2g per lb bodyweight")
            tips.add("🏃‍♂️ Mix strength training with cardio - strength maintains muscle, cardio burns calories")
            if (bmi > 30) {
                tips.add("🚶‍♂️ Start with low-impact activities (walking, swimming) to protect joints")
            } else {
                tips.add("⚡ Include 2-3 HIIT sessions weekly for enhanced fat oxidation")
            }
        }

        "Recomposition" -> {
            tips.add("⚖️ Recomp is slow - track strength gains and body measurements over scale weight")
            tips.add("🎯 Ideal for your experience level - maintain calories around ${calories.toInt()}")
            tips.add("⏳ Expect visible changes in 8-12 weeks with consistent training and nutrition")
        }
    }

    // Age-specific recommendations
    // Age-specific recommendations
    when {
        age < 25 -> {
            tips.add("🚀 Peak anabolic years - take advantage with consistent training and nutrition")
            tips.add("💪 Your recovery is excellent - you can handle higher training volumes")
        }
        age in 25..35 -> {
            tips.add("⚖️ Metabolism starts slowing - pay attention to portion control and food quality")
            tips.add("🧘‍♂️ Stress management becomes more important for recovery")
        }
        age in 36..50 -> {
            tips.add("🔧 Recovery takes longer - prioritize sleep and stress management")
            tips.add("💀 Bone density focus: include weight-bearing exercises")
            tips.add("🔄 Consider deload weeks every 4-6 weeks")
        }
        age > 50 -> {
            tips.add("🦴 Bone health priority: resistance training 2-3x/week minimum")
            tips.add("🤸‍♂️ Include balance and mobility work to prevent falls")
            tips.add("💊 Consider vitamin D and calcium supplementation")
            tips.add("👨‍⚕️ Regular health check-ups become increasingly important")
        }
    }

    // Training frequency and recovery insights
    when (trainingDays) {
        2 -> {
            tips.add("💯 Full-body workouts maximize efficiency with 2 sessions")
            tips.add("🎯 Focus on compound movements for maximum muscle activation")
        }
        in 3..4 -> {
            tips.add("✅ Optimal training frequency for most goals and recovery")
            tips.add("📅 Allow at least one full rest day between sessions")
        }
        in 5..6 -> {
            tips.add("📊 High frequency requires excellent recovery: 8+ hours sleep, proper nutrition")
            tips.add("🔄 Consider periodizing intensity - not every session should be maximal")
            tips.add("👂 Listen to your body - reduce volume if constantly fatigued")
        }
    }

    // Nutrition-specific advanced guidance
    when (nutrition) {
        "Vegetarian" -> {
            tips.add("🌱 Combine legumes + grains for complete amino acid profiles")
            tips.add("💊 Monitor B12, iron, zinc levels - consider supplementation")
            tips.add("🥛 Include dairy/eggs for high-quality protein if tolerated")
        }
        "Vegan" -> {
            tips.add("🌿 Plan protein carefully: legumes, quinoa, hemp seeds, plant proteins")
            tips.add("💊 Essential supplements: B12, D3, algae omega-3s, possibly iron/zinc")
            tips.add("🌈 Eat rainbow variety for micronutrient completeness")
        }
        "Keto/LCHF" -> {
            tips.add("⚡ Time remaining carbs (${carbs}g) around workouts for performance")
            tips.add("🥑 Focus on healthy fats: avocados, nuts, olive oil, fatty fish")
            tips.add("💧 Increase water and electrolyte intake on low-carb")
        }
        "Intermittent fasting" -> {
            tips.add("⏰ Train either fasted or during feeding window based on preference")
            tips.add("🍽️ Break fast with protein-rich meal for muscle preservation")
            tips.add("💧 Stay hydrated during fasting periods")
        }
    }

    // Sleep optimization (crucial for results)
    when (sleep) {
        "Less than 6" -> {
            tips.add("😴 CRITICAL: Poor sleep severely impacts muscle growth and fat loss")
            tips.add("🌙 Sleep hygiene: dark room, cool temp, no screens 1hr before bed")
            tips.add("☕ Avoid caffeine 6+ hours before bedtime")
            tips.add("📉 You may need to reduce training intensity until sleep improves")
        }
        "6-7" -> {
            tips.add("😴 Aim for 7-9 hours for optimal recovery and performance")
            tips.add("⏰ Consistent sleep/wake times even on weekends")
        }
        "7-8" -> {
            tips.add("✅ Excellent sleep! This significantly supports your fitness goals")
            tips.add("🔄 Maintain this sleep quality for continued progress")
        }
        in listOf("8-9", "9+") -> {
            tips.add("🌟 Outstanding sleep quality - your recovery is optimized")
            tips.add("💪 This gives you a significant advantage in reaching your goals")
        }
    }

    // Medical condition considerations
    limitations.forEach { limitation ->
        when (limitation) {
            "Knee injury" -> {
                tips.add("🦵 Avoid deep squats and jumping - focus on quad/glute strengthening")
                tips.add("🚴‍♂️ Low-impact cardio: cycling, swimming, elliptical")
                tips.add("🔥 Always warm up thoroughly and consider knee support")
            }
            "Shoulder injury" -> {
                tips.add("💪 Avoid overhead movements - focus on horizontal push/pull patterns")
                tips.add("🏋️‍♂️ Strengthen rotator cuffs with light resistance band work")
                tips.add("🌡️ Thorough shoulder warm-up before any upper body training")
            }
            "Back pain" -> {
                tips.add("🧘‍♂️ Core strengthening and hip mobility are priorities")
                tips.add("🏋️‍♂️ Use machines or dumbbells instead of barbell initially")
                tips.add("📐 Focus on neutral spine positioning in all exercises")
            }
            "Asthma" -> {
                tips.add("💨 Always have inhaler available during workouts")
                tips.add("🌡️ Warm up gradually, avoid sudden intense bursts")
                tips.add("🏊‍♂️ Swimming often triggers fewer asthma symptoms")
                tips.add("❄️ Cold weather exercise may trigger symptoms")
            }
            "High blood pressure" -> {
                tips.add("📈 Monitor heart rate during exercise - stay in moderate zones")
                tips.add("🧘‍♂️ Include relaxation techniques and stress management")
                tips.add("🧂 Reduce sodium intake and focus on whole foods")
            }
            "Diabetes" -> {
                tips.add("📊 Monitor blood glucose before, during, and after exercise")
                tips.add("🍎 Time carbohydrate intake around workouts carefully")
                tips.add("👨‍⚕️ Work with healthcare provider on exercise prescription")
            }
        }
    }

    // Advanced macronutrient timing
    tips.add("🍽️ Spread protein intake: ${(protein/4.0).toInt()}-${(protein/3.0).toInt()}g per meal for optimal absorption")

    when {
        carbs < 100 -> {
            tips.add("⚡ Low carb may affect high-intensity performance - monitor energy levels")
            tips.add("🥑 Ensure adequate fat intake for hormone production")
        }
        carbs > 300 -> {
            tips.add("🍞 High carb intake great for performance - time around workouts")
            tips.add("🏃‍♂️ Consider carb cycling: higher on training days, lower on rest days")
        }
        else -> {
            tips.add("⚖️ Balanced carb intake - adjust timing based on training schedule")
        }
    }

    // BMI-specific guidance
    when {
        bmi < 18.5 -> {
            tips.add("📈 Underweight: Focus on gradual weight gain with strength training")
            tips.add("🍽️ Eat calorie-dense, nutritious foods frequently")
        }
        bmi > 30 -> {
            tips.add("🎯 Obesity range: Prioritize sustainable lifestyle changes over quick fixes")
            tips.add("👨‍⚕️ Consider working with healthcare provider for comprehensive approach")
        }
        bmi in 25.0..29.9 -> {
            tips.add("⚖️ Overweight range: Small, sustainable changes yield big results")
            tips.add("📊 Focus on body composition over scale weight")
        }
    }

    // Progressive overload and tracking
    when (experience) {
        "Beginner" -> {
            tips.add("📈 Track workouts: aim to add weight, reps, or sets each week")
            tips.add("🎯 Focus on form over weight - build movement patterns first")
            tips.add("📚 Learn proper exercise technique from reliable sources")
        }
        "Intermediate" -> {
            tips.add("📊 Periodize training: vary intensity and volume every 4-6 weeks")
            tips.add("🔍 Track key metrics: strength gains, body measurements, energy levels")
            tips.add("🧘‍♂️ Recovery becomes more important - manage stress actively")
        }
        "Advanced" -> {
            tips.add("🔬 Fine-tune based on individual response and weak points")
            tips.add("📈 Consider advanced techniques: cluster sets, rest-pause, autoregulation")
            tips.add("🧠 Mental training becomes crucial - visualization and mindset work")
        }
    }

    // Hydration and general health
    tips.add("💧 Hydration goal: clear/pale yellow urine as indicator")
    tips.add("🥗 Include 5-7 servings fruits/vegetables daily for micronutrients")
    tips.add("🧘‍♂️ Manage stress: meditation, yoga, or other relaxation techniques")
    tips.add("📸 Track progress photos and measurements, not just scale weight")
    tips.add("🔄 Consistency over perfection - sustainable habits win long-term")

    // Location-specific equipment recommendations
    if (location == "Home") {
        tips.add("🏠 Essential home equipment: adjustable dumbbells, resistance bands, pull-up bar")
        tips.add("📱 Use fitness apps or online videos for guidance and motivation")
        tips.add("🏠 Create dedicated workout space for consistency")
    } else if (location == "Gym") {
        tips.add("🏋️‍♂️ Learn gym etiquette and don't hesitate to ask for equipment help")
        tips.add("📋 Have a plan before entering - maximize efficiency and focus")
        tips.add("🎵 Use variety of equipment to prevent boredom and plateaus")
    }

    // Final motivational and practical advice
    tips.add("📅 Schedule workouts like important appointments - consistency is key")
    tips.add("🎯 Set both process goals (workout frequency) and outcome goals (strength/weight)")
    tips.add("👥 Consider finding a workout partner or trainer for accountability")
    tips.add("📖 Continuously educate yourself about nutrition and training")
    tips.add("🏆 Celebrate small wins - progress isn't always linear")

    // Return the most relevant tips (limit to prevent overwhelming the user)
    return tips.distinct().take(15)
}