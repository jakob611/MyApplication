package com.example.myapplication.ui.screens

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.example.myapplication.data.DailyHealthStats
import com.example.myapplication.data.HealthGoals
import com.example.myapplication.data.HealthStorage
import com.example.myapplication.health.HealthConnectManager
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun StatCard(
    icon: ImageVector,
    title: String,
    value: String,
    progress: Float,
    progressColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2D3E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = progressColor,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(title, fontSize = 12.sp, color = Color(0xFFB0B8C4))
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = progressColor,
                trackColor = Color(0xFF1F2231)
            )
        }
    }
}

@Composable
fun WeightCard(weight: com.example.myapplication.health.WeightData) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2D3E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Scale,
                contentDescription = null,
                tint = Color(0xFF6366F1),
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(weight.date, fontSize = 14.sp, color = Color(0xFFB0B8C4))
                Text(
                    "%.1f kg".format(weight.weightKg),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HealthConnectScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val healthManager = remember { HealthConnectManager.getInstance(context) }

    var isAvailable by remember { mutableStateOf(false) }
    var hasPermissions by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showData by remember { mutableStateOf(false) }
    var todaySteps by remember { mutableStateOf(0L) }
    var todayCalories by remember { mutableStateOf(0) }
    var exerciseDuration by remember { mutableStateOf(0) }
    var todayHeartRate by remember { mutableStateOf<List<com.example.myapplication.health.HeartRateData>>(emptyList()) }
    var todayDistanceKm by remember { mutableStateOf(0.0) }

    // Goals and UI state
    var healthGoals by remember { mutableStateOf(HealthGoals()) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    // Cache previous values to avoid unnecessary updates
    var lastSteps by remember { mutableStateOf(-1L) }
    var lastCalories by remember { mutableStateOf(-1) }
    var lastExercise by remember { mutableStateOf(-1) }
    var secondaryLoaded by remember { mutableStateOf(false) }

    // Load goals on start
    LaunchedEffect(Unit) {
        healthGoals = HealthStorage.getHealthGoals()
    }

    fun loadHealthData() {
        scope.launch {
            try {
                android.util.Log.d("HealthConnectScreen", "=== Starting data load ===")

                val newSteps = healthManager.readTodaySteps()
                val now = Instant.now()
                val startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant()
                val newCalories = healthManager.readCalories(startOfDay, now)

                val todayExercises = healthManager.readTodayExerciseSessions()
                var newExercise = todayExercises.sumOf { it.durationMinutes }
                if (newExercise < 5 && newSteps > 100) {
                    newExercise = (newSteps / 100).toInt()
                }

                // Update main stats if changed
                if (newSteps != lastSteps || newCalories != lastCalories || newExercise != lastExercise) {
                    // Notify when goals are crossed (from below to above)
                    if (lastSteps >= 0 && newSteps >= healthGoals.stepsGoal && lastSteps < healthGoals.stepsGoal) {
                        sendHealthGoalNotification(context, "Steps Goal Reached! 🎉", "You reached ${healthGoals.stepsGoal} steps today!")
                    }
                    if (lastCalories >= 0 && newCalories >= healthGoals.caloriesGoal && lastCalories < healthGoals.caloriesGoal) {
                        sendHealthGoalNotification(context, "Calories Goal Reached! 🔥", "You burned ${healthGoals.caloriesGoal} kcal today!")
                    }
                    if (lastExercise >= 0 && newExercise >= healthGoals.exerciseMinutesGoal && lastExercise < healthGoals.exerciseMinutesGoal) {
                        sendHealthGoalNotification(context, "Exercise Goal Reached! 💪", "You exercised ${healthGoals.exerciseMinutesGoal} min today!")
                    }

                    todaySteps = newSteps
                    todayCalories = newCalories
                    exerciseDuration = newExercise
                    lastSteps = newSteps
                    lastCalories = newCalories
                    lastExercise = newExercise

                    HealthStorage.saveDailyStats(DailyHealthStats(
                        date = LocalDate.now().toString(),
                        steps = newSteps,
                        calories = newCalories,
                        exerciseMinutes = newExercise
                    ))
                }

                // Always load secondary data on first call, then only on refresh
                if (!secondaryLoaded) {
                    todayHeartRate = healthManager.readHeartRate(startOfDay, now)
                    todayDistanceKm = healthManager.readDistance(startOfDay, now)
                    secondaryLoaded = true
                    android.util.Log.d("HealthConnectScreen", "Secondary data loaded - HR: ${todayHeartRate.size}, Dist: ${"%.2f".format(todayDistanceKm)} km")
                }

                showData = true
            } catch (e: Exception) {
                android.util.Log.e("HealthConnectScreen", "Error loading data", e)
            } finally {
                isLoading = false
            }
        }
    }

    var isRefreshing by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullRefreshState(
        refreshing = isRefreshing,
        onRefresh = {
            scope.launch {
                isRefreshing = true
                secondaryLoaded = false  // Force reload of sleep, HR, distance
                loadHealthData()
                delay(500)
                isRefreshing = false
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = healthManager.createRequestPermissionsContract()
    ) { _ ->
        scope.launch {
            hasPermissions = healthManager.hasAllPermissions()
            if (hasPermissions) {
                showData = false
                loadHealthData()
            }
        }
    }

    LaunchedEffect(Unit) {
        // Try loading from FIRESTORE CACHE first (so user sees steps immediately)
        val today = LocalDate.now().toString()
        val cached = HealthStorage.getDailyStats(today)
        if (cached != null) {
            todaySteps = cached.steps
            todayCalories = cached.calories
            exerciseDuration = cached.exerciseMinutes
            showData = true
            isLoading = false
        }

        isAvailable = healthManager.isAvailable()
        if (isAvailable) {
            // Check if we have ALL required permissions (updated 2026-02-16)
            hasPermissions = healthManager.hasAllPermissions()
            if (hasPermissions) {
                loadHealthData()
                while (true) {
                    delay(10000)
                    loadHealthData()
                }
            } else {
                isLoading = false
                permissionLauncher.launch(healthManager.allPermissions)
            }
        } else {
            isLoading = false
        }
    }

    val backgroundGradient = Brush.verticalGradient(
        listOf(Color(0xFF17223B), Color(0xFF25304A), Color(0xFF193446))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health Connect") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showHistory = true }) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "History",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { showGoalDialog = true }) {
                        Icon(
                            imageVector = Icons.Filled.Flag,
                            contentDescription = "Set Goal",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF17223B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().background(backgroundGradient).padding(padding)
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF6366F1))
                    }
                }
                !isAvailable -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFEE440), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Health Connect Not Available", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("Requires Android 14+ or install from Play Store.", fontSize = 14.sp, color = Color(0xFFB0B8C4), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { healthManager.openHealthConnectSettings() }, colors = ButtonDefaults.buttonColors(Color(0xFF6366F1))) {
                            Text("Install Health Connect")
                        }
                    }
                }
                !hasPermissions -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.HealthAndSafety, contentDescription = null, tint = Color(0xFFFEE440), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("Permissions Required", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("Grant at least one permission.", fontSize = 14.sp, color = Color(0xFFB0B8C4), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { permissionLauncher.launch(healthManager.allPermissions) },
                            colors = ButtonDefaults.buttonColors(Color(0xFF6366F1)),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Grant Permissions", fontSize = 16.sp) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                            Text("Go Back")
                        }
                    }
                }
                hasPermissions && showData -> {
                    Box(Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
                        LazyColumn(
                            Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item { Text("Today's Summary", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    StatCard(
                                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                                        title = "Steps (goal: ${healthGoals.stepsGoal})",
                                        value = todaySteps.toString(),
                                        progress = (todaySteps / healthGoals.stepsGoal.toFloat()).coerceIn(0f, 1f),
                                        progressColor = Color(0xFF4CAF50),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        icon = Icons.Filled.LocalFireDepartment,
                                        title = "Calories (goal: ${healthGoals.caloriesGoal})",
                                        value = "$todayCalories kcal",
                                        progress = (todayCalories / healthGoals.caloriesGoal.toFloat()).coerceIn(0f, 1f),
                                        progressColor = Color(0xFFFF5252),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            item {
                                StatCard(
                                    icon = Icons.Filled.FitnessCenter,
                                    title = "Exercise (goal: ${healthGoals.exerciseMinutesGoal} min)",
                                    value = "$exerciseDuration min",
                                    progress = (exerciseDuration / healthGoals.exerciseMinutesGoal.toFloat()).coerceIn(0f, 1f),
                                    progressColor = Color(0xFF448AFF),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            // Heart Rate and Distance row
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    val avgBpm = if (todayHeartRate.isNotEmpty()) {
                                        todayHeartRate.map { it.bpm }.average().toInt()
                                    } else 0
                                    StatCard(
                                        icon = Icons.Filled.MonitorHeart,
                                        title = "Avg Heart Rate",
                                        value = if (avgBpm > 0) "$avgBpm bpm" else "–",
                                        progress = if (avgBpm > 0) avgBpm / 200f else 0f,
                                        progressColor = Color(0xFFE91E63),
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatCard(
                                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                                        title = "Distance",
                                        value = if (todayDistanceKm > 0.01) "${"%.2f".format(todayDistanceKm)} km" else "–",
                                        progress = (todayDistanceKm / 5.0).toFloat(),
                                        progressColor = Color(0xFFFF9800),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            // Heart Rate detail (min/max) if data available
                            if (todayHeartRate.isNotEmpty()) {
                                item {
                                    val minBpm = todayHeartRate.minOf { it.bpm }
                                    val maxBpm = todayHeartRate.maxOf { it.bpm }
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2D3E)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                            Text("Heart Rate Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Spacer(Modifier.height(8.dp))
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("$minBpm", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                                    Text("Min bpm", fontSize = 12.sp, color = Color(0xFFB0B8C4))
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    val avg = todayHeartRate.map { it.bpm }.average().toInt()
                                                    Text("$avg", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF9800))
                                                    Text("Avg bpm", fontSize = 12.sp, color = Color(0xFFB0B8C4))
                                                }
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("$maxBpm", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE91E63))
                                                    Text("Max bpm", fontSize = 12.sp, color = Color(0xFFB0B8C4))
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text("${todayHeartRate.size} measurements today", fontSize = 12.sp, color = Color(0xFFB0B8C4))
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }

                        PullRefreshIndicator(
                            refreshing = isRefreshing,
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter),
                            backgroundColor = Color(0xFF2A2D3E),
                            contentColor = Color(0xFF6366F1)
                        )
                    }
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFF6366F1))
                            Spacer(Modifier.height(16.dp))
                            Text("Loading health data...", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }

    // Set Goal dialog
    if (showGoalDialog) {
        SetGoalDialog(
            current = healthGoals,
            onDismiss = { showGoalDialog = false },
            onSave = { newGoals ->
                healthGoals = newGoals
                showGoalDialog = false
                scope.launch { HealthStorage.saveHealthGoals(newGoals) }
            }
        )
    }

    // History overlay (full screen)
    if (showHistory) {
        HealthHistoryScreen(onBack = { showHistory = false })
    }
}

// ===== SET GOAL DIALOG =====
@Composable
fun SetGoalDialog(
    current: HealthGoals,
    onDismiss: () -> Unit,
    onSave: (HealthGoals) -> Unit
) {
    var steps by remember { mutableStateOf(current.stepsGoal.toString()) }
    var calories by remember { mutableStateOf(current.caloriesGoal.toString()) }
    var exercise by remember { mutableStateOf(current.exerciseMinutesGoal.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Daily Goals", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Set your daily targets for Health Connect tracking.", fontSize = 13.sp, color = Color(0xFF6B7280))
                OutlinedTextField(
                    value = steps,
                    onValueChange = { steps = it.filter(Char::isDigit) },
                    label = { Text("👟 Steps goal") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = calories,
                    onValueChange = { calories = it.filter(Char::isDigit) },
                    label = { Text("🔥 Calories burned goal (kcal)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = exercise,
                    onValueChange = { exercise = it.filter(Char::isDigit) },
                    label = { Text("⏱ Exercise goal (minutes)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        HealthGoals(
                            stepsGoal = steps.toLongOrNull() ?: current.stepsGoal,
                            caloriesGoal = calories.toIntOrNull() ?: current.caloriesGoal,
                            exerciseMinutesGoal = exercise.toIntOrNull() ?: current.exerciseMinutesGoal
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ===== HEALTH HISTORY SCREEN =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthHistoryScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<DailyHealthStats>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        history = HealthStorage.getAllDailyStats()
        loading = false
    }

    val backgroundGradient = Brush.verticalGradient(
        listOf(Color(0xFF17223B), Color(0xFF25304A), Color(0xFF193446))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Health History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF17223B),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundGradient)
                .padding(padding)
        ) {
            when {
                loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF6366F1))
                    }
                }
                history.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.History, contentDescription = null, tint = Color(0xFFB0B8C4), modifier = Modifier.size(56.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No history yet.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("Data will appear here once Health Connect syncs.", color = Color(0xFFB0B8C4), fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                "History (${history.size} days)",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(history) { stat ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2D3E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        stat.date,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("👟", fontSize = 20.sp)
                                            Text("${stat.steps}", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text("steps", color = Color(0xFFB0B8C4), fontSize = 11.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("🔥", fontSize = 20.sp)
                                            Text("${stat.calories}", color = Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text("kcal", color = Color(0xFFB0B8C4), fontSize = 11.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("⏱", fontSize = 20.sp)
                                            Text("${stat.exerciseMinutes}", color = Color(0xFF448AFF), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Text("min", color = Color(0xFFB0B8C4), fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

// ===== NOTIFICATION HELPER =====
private fun sendHealthGoalNotification(context: Context, title: String, message: String) {
    val channelId = "health_goals_channel"
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Health Goals",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "Notifications when you reach your daily health goals" }
        manager.createNotificationChannel(channel)
    }
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setAutoCancel(true)
        .build()
    try {
        manager.notify(title.hashCode(), notification)
    } catch (e: SecurityException) {
        android.util.Log.w("HealthConnect", "Notification permission denied: ${e.message}")
    }
}