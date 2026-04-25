package com.example.myapplication.ui.screens

import com.example.myapplication.data.PlanResult
import com.example.myapplication.data.WeekPlan
import com.example.myapplication.data.DayPlan
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.activity.compose.BackHandler
import com.example.myapplication.data.AdvancedExerciseRepository
import com.example.myapplication.data.RefinedExercise
import com.example.myapplication.domain.WorkoutGenerator
import com.example.myapplication.data.AlgorithmPreferences
import com.example.myapplication.domain.WorkoutGoal
import com.example.myapplication.viewmodels.BodyModuleHomeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.platform.LocalDensity
import com.example.myapplication.data.settings.UserProfileManager

private fun getVideoUrlForExercise(exerciseName: String): String {
    val baseUrl = "https://storage.googleapis.com/fitness-videos-glowupp/"
    val normalizedName = exerciseName.trim().replace(Regex("[\\s-]+"), "_").replace(Regex("[^a-zA-Z0-9_]"), "")
    return "${baseUrl}${normalizedName}_Female.mp4"
}

private sealed class WorkoutState {
    object Loading : WorkoutState()
    data class Exercise(val index: Int) : WorkoutState()
    data class Rest(val nextIndex: Int, val restSeconds: Int = 60) : WorkoutState()
    data class Celebration(val results: List<ExerciseResult>, val skipped: List<String>, val isExtra: Boolean) : WorkoutState()
    data class Report(val results: List<ExerciseResult>, val skipped: List<String>) : WorkoutState()
    data class Preview(val warmup: List<ExerciseData>, val main: List<ExerciseData>) : WorkoutState()
    data class Error(val message: String) : WorkoutState()
}

private data class ExerciseResult(
    val name: String,
    val activeMinutes: Double,
    val restMinutes: Double,
    val caloriesKcal: Int,
    val difficultyRating: Int = 1
)

private data class ExerciseData(
    val name: String,
    val description: String,
    val caloriesPerMinPerKg: Double,
    val difficulty: Int, // Exercise difficulty level (1-10)
    val sets: Int,
    val reps: Int,
    val repsDisplay: String = "12",
    val restSeconds: Int,
    val durationMinutesOverride: Double? = null,
    val durationSecondsOverride: Int? = null,
    val secondsPerRepOverride: Double? = null,
    val videoUrl: String? = null,
    val isWarmup: Boolean = false,
    val executionTips: List<String> = emptyList() // Execution tips to display
)

// Helper: Calculate dynamic rest time based on difficulty & user experience level
// Reference: difficulty=10, experience=10 → 60s
// Less experienced → more rest, easier exercise → less rest
private fun calculateDynamicRestTime(
    baseDifficulty: Int, // 1-10 from exercise
    userExperienceLevel: Int, // Beginner=3, Intermediate=6, Advanced=9
    baseRestSeconds: Int // From JSON (used as fallback reference)
): Int {
    // Base rest scales linearly with difficulty: diff 10 → 60s, diff 5 → 30s, diff 1 → 6s
    val difficultyBase = baseDifficulty * 6.0

    // Experience adjustment: exp 10 → 1.0x (no change), exp 1 → 1.5x (50% more rest)
    // Formula: multiplier = 1.0 + (10 - experience) * 0.055
    val experienceMultiplier = 1.0 + (10 - userExperienceLevel.coerceIn(1, 10)) * 0.055

    val dynamicRest = (difficultyBase * experienceMultiplier).toInt()

    // Cap between 20-120 seconds
    return dynamicRest.coerceIn(20, 120)
}

// Helper: Calculate calories based on ACTUAL time spent on exercise
private fun calculateActualCalories(
    caloriesPerKgPerHour: Double,
    userWeightKg: Double,
    actualMinutes: Double // ACTUAL time user spent on exercise
): Int {
    // Convert per-hour to per-minute
    val caloriesPerKgPerMin = caloriesPerKgPerHour / 60.0
    val totalCalories = caloriesPerKgPerMin * userWeightKg * actualMinutes
    return totalCalories.toInt().coerceAtLeast(1)
}

@Composable
fun WorkoutSessionScreen(
    currentPlan: PlanResult?,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    onXPAdded: () -> Unit = {},
    isExtra: Boolean = false,
    extraFocusAreas: List<String> = emptyList(),   // fokus izbran v GenerateWorkoutScreen
    extraEquipment: Set<String> = emptySet(),       // oprema izbrana v GenerateWorkoutScreen
    onBadgeUnlocked: (com.example.myapplication.data.Badge) -> Unit = {}
) {
    val context = LocalContext.current
    // Replaced ExerciseCache
    LaunchedEffect(Unit) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { AdvancedExerciseRepository.init(context.assets.open("exercises.json").bufferedReader().use { it.readText() }) } }

    val vm: BodyModuleHomeViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application))
    // ✅ Firestore SSOT: collectiramo state enkrat za celoten composable
    val vmUiState by vm.ui.collectAsState()

    var userWeightKg by remember { mutableStateOf(70.0) }
    val uid = remember { com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() }
    LaunchedEffect(Unit) {
        try {
            val weight = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.example.myapplication.persistence.FirestoreHelper.fetchLatestWeightKg()
            }
            if (weight != null && weight > 0) {
                userWeightKg = weight
            }
        } catch(e: Exception) {}
    }

    var currentSessionExercises by remember { mutableStateOf<List<ExerciseData>>(emptyList()) }
    var state by remember { mutableStateOf<WorkoutState>(WorkoutState.Loading) }

    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = state is WorkoutState.Exercise || state is WorkoutState.Rest || state is WorkoutState.Preview) {
        showExitDialog = true
    }

    if (showExitDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Exit Workout?") },
            text = { Text("Are you sure you want to exit? Your progress will be lost.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showExitDialog = false
                        onBack()
                    }
                ) {
                    Text("Exit", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showExitDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    // Determine user experience level from plan
    val userExperienceLevel = when (currentPlan?.experience) {
        "Beginner" -> 3
        "Intermediate" -> 6
        "Advanced" -> 9
        else -> 5
    }

    fun mapRefinedToExerciseData(refined: RefinedExercise): ExerciseData {
        // Normalize name: replace underscores with spaces and capitalize words
        val normalizedName = refined.name
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { it.uppercase() }
            }

        val multiplier = AlgorithmPreferences.getExerciseMultiplier(context.getAlgoSettings(), normalizedName)

        val adjustedSets = (refined.parsedSets * multiplier).toInt().coerceAtLeast(1)
        val adjustedReps = if (refined.parsedReps > 0) (refined.parsedReps * multiplier).toInt().coerceAtLeast(1) else 0

        // Calculate dynamic rest time based on difficulty and user level
        val dynamicRestTime = calculateDynamicRestTime(
            baseDifficulty = refined.difficulty,
            userExperienceLevel = userExperienceLevel,
            baseRestSeconds = refined.recommendedRestSeconds
        )

        return ExerciseData(
            name = normalizedName,
            description = refined.description,
            caloriesPerMinPerKg = refined.caloriesPerKgPerHour / 60.0,
            difficulty = refined.difficulty,
            sets = adjustedSets,
            reps = adjustedReps,
            // Ohrani originalni range string (npr "10-12"), ne zamenjuj z int
            repsDisplay = refined.repsDisplay,
            restSeconds = dynamicRestTime,
            durationSecondsOverride = if (refined.parsedReps == 0) ((60 * multiplier).toInt()) else null,
            videoUrl = refined.videoUrl.takeIf { it.isNotEmpty() },
            executionTips = refined.executionTips
        )
    }

    LaunchedEffect(currentPlan, isExtra, extraFocusAreas) {
        state = WorkoutState.Loading
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            AdvancedExerciseRepository.init(context.assets.open("exercises.json").bufferedReader().use { it.readText() })
        }

        // Check if plan exists - if not, immediately go back
        if (currentPlan == null) {
            onBack() // Navigate back to home screen
            return@LaunchedEffect
        }

        // Determine parameters from PlanResult
        val experienceLevel = when (currentPlan.experience) {
            "Beginner" -> 3
            "Intermediate" -> 6
            "Advanced" -> 9
            else -> 5
        }

        // Zagotovi da je začetna težavnost inicializirana
        AlgorithmPreferences.initDifficultyForPlan(context.getAlgoSettings(), currentPlan.experience)

        // Določi ciljno težavnost glede na recovery/catchup/normalen način
        val isRecovery = AlgorithmPreferences.isRecoveryMode(context.getAlgoSettings())
        val weeklyTarget = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
            .getInt("weekly_target", 3)
        val weeklyDone = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
            .getInt("weekly_done", 0)

        val targetDifficulty: Float = when {
            isRecovery -> {
                // Recovery: linearna interpolacija od 50% do 100% težavnosti skozi teden
                AlgorithmPreferences.getRecoveryDayDifficulty(context.getAlgoSettings(), weeklyDone, weeklyTarget)
            }
            else -> {
                AlgorithmPreferences.getCurrentDifficulty(context.getAlgoSettings(), currentPlan.experience)
            }
        }

        val goal = when (currentPlan.goal) {
            "Build muscle" -> WorkoutGoal.MUSCLE_GAIN
            "Lose fat" -> WorkoutGoal.WEIGHT_LOSS
            "Recomposition" -> WorkoutGoal.STRENGTH
            "Improve endurance" -> WorkoutGoal.ENDURANCE
            else -> WorkoutGoal.GENERAL_FITNESS
        }

        // ── Focus določitev ──────────────────────────────────────────────────
        val dailyFocus: Set<String>

        if (isExtra) {
            // Extra workout: fokus DIREKTNO iz parametra — kar je uporabnik izbral v GenerateWorkoutScreen
            // Ne beremo iz plana, ne beremo iz Firestorea — SAMO to kar je prišlo kot parameter
            val focus = extraFocusAreas
                .filter { !it.equals("None", ignoreCase = true) }
                .ifEmpty { listOf("Full Body") }
            dailyFocus = focus.toSet()
        } else {
            // Navaden workout: rotacija fokusa po planDay
            val rawFocus = currentPlan.focusAreas.ifEmpty {
                val profile = UserProfileManager.loadProfile(uid ?: "")
                profile.focusAreas.ifEmpty { listOf("Full Body") }
            }
            val allFocus = if (rawFocus.any { it.equals("None", ignoreCase = true) }) {
                listOf("Full Body")
            } else {
                rawFocus
            }
            // ✅ SSOT: beri plan_day iz BodyModuleHomeViewModel (Firestore) namesto bm_prefs
            val planDay = vm.ui.value.planDay.coerceAtLeast(1)
            dailyFocus = if (allFocus.any {
                    it.equals("Full Body", ignoreCase = true) ||
                    it.equals("Balance", ignoreCase = true)
                } || allFocus.isEmpty()) {
                setOf("Full Body")
            } else {
                // Štej samo workout dni (ne rest dni) do planDay — beri iz currentPlan.weeks
                val workoutDayIndex: Int = run {
                    val planWeeks = currentPlan.weeks
                    if (planWeeks.isNotEmpty()) {
                        var count = 0
                        for (week in planWeeks) {
                            for (day in week.days) {
                                if (day.dayNumber >= planDay) break
                                if (!day.isRestDay) count++
                            }
                        }
                        count
                    } else {
                        (planDay - 1)
                    }
                }
                val index = workoutDayIndex % allFocus.size
                setOf(allFocus[index])
            }
        }

        // Equipment: za extra workout direktno iz parametra, sicer iz plana
        val equipment: Set<String>
        if (isExtra && extraEquipment.isNotEmpty()) {
            equipment = extraEquipment.map { it.trim().lowercase() }.toSet()
        } else {
            val rawEquipment = currentPlan.equipment.ifEmpty {
                val profile = UserProfileManager.loadProfile(uid ?: "")
                profile.equipment
            }
            equipment = if (rawEquipment.isNotEmpty()) {
                rawEquipment.map { it.trim().lowercase() }.toSet()
            } else if (currentPlan.trainingLocation.equals("Gym", ignoreCase = true)) {
                AdvancedExerciseRepository.getAllEquipment()
            } else {
                setOf("bodyweight")
            }
        }

        android.util.Log.d("WorkoutSession", "Focus: $dailyFocus (from plan: ${currentPlan.focusAreas})")
        android.util.Log.d("WorkoutSession", "Equipment: $equipment (from plan: ${currentPlan.equipment})")

        val params = com.example.myapplication.domain.WorkoutGenerationParams(
            userExperienceLevel = experienceLevel,
            targetDifficultyLevel = targetDifficulty,
            availableEquipment = equipment,
            goal = goal,
            focusAreas = dailyFocus.toSet(),
            exerciseCount = (currentPlan.sessionLength / 4).coerceAtLeast(4).coerceAtMost(15),
            durationMinutes = currentPlan.sessionLength
        )

        val generator = WorkoutGenerator()
        val result = generator.generateWorkout(params)
        val generatedList = result.map { mapRefinedToExerciseData(it) }

        if (generatedList.isEmpty()) {
             state = WorkoutState.Error("Could not generate workout. Please check your settings.")
        } else {
            // Mark exercises as warmup (first 2) or main
            currentSessionExercises = generatedList.mapIndexed { index, ex ->
                ex.copy(isWarmup = index < 2)
            }
            val warmup = currentSessionExercises.filter { it.isWarmup }
            val main = currentSessionExercises.filter { !it.isWarmup }
            state = WorkoutState.Preview(warmup, main)
        }
    }

    val results = remember { mutableStateListOf<ExerciseResult>() }
    val skippedExercises = remember { mutableStateListOf<String>() }
    var skipCount by remember { mutableIntStateOf(0) }
    val maxSkips = 3

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        when (val s = state) {
            is WorkoutState.Loading -> {
                CircularProgressIndicator()
            }
            is WorkoutState.Error -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onBack) { Text("Back") }
                }
            }
            is WorkoutState.Preview -> {
                PreviewScreen(1, s.warmup, s.main, onStart = { state = WorkoutState.Exercise(0) }, onBack = onBack)
            }
            is WorkoutState.Rest -> {
                RestScreen(s.restSeconds, onSkip = { state = WorkoutState.Exercise(s.nextIndex) }) {
                    state = WorkoutState.Exercise(s.nextIndex)
                }
            }
            is WorkoutState.Exercise -> {
                val ex = currentSessionExercises.getOrNull(s.index)
                if (ex != null) {
                    ExerciseScreen(ex, s.index, currentSessionExercises.size, userWeightKg, skipCount, maxSkips,
                        onPrevious = { if (s.index > 0) state = WorkoutState.Exercise(s.index - 1) },
                        onSkip = {
                            skippedExercises.add(ex.name); skipCount++
                            if (s.index < currentSessionExercises.size - 1) state = WorkoutState.Rest(s.index+1, ex.restSeconds) else state = WorkoutState.Report(results, skippedExercises)
                        },
                        onContinue = { r ->
                            results.add(r)
                            if (s.index < currentSessionExercises.size - 1) state = WorkoutState.Rest(s.index+1, ex.restSeconds) else state = WorkoutState.Celebration(results, skippedExercises, isExtra)
                        },
                        onEndWorkout = { onBack() }
                    )
                }
            }
            is WorkoutState.Celebration -> {
                // ADDED: P1 UX Improvement - Next day preview
                // ✅ SSOT: beri plan_day iz BodyModuleHomeViewModel (Firestore) namesto bm_prefs
                val currentDay = vmUiState.planDay
                val nextDayNumber = currentDay + 1
                val nextDay = currentPlan?.weeks?.flatMap { it.days }?.firstOrNull { it.dayNumber == nextDayNumber }
                val nextDayPreview = when {
                    nextDay == null -> null
                    nextDay.isRestDay -> "Tomorrow: Rest Day 💤"
                    else -> "Tomorrow: ${nextDay.focusLabel} Focus 🔥"
                }

                WorkoutCelebrationScreen(
                    isExtra = s.isExtra,
                    nextDayPreview = nextDayPreview,
                    planDay = vmUiState.planDay,  // ✅ Firestore SSOT prek ViewModel
                    onContinue = { state = WorkoutState.Report(s.results, s.skipped) }
                )
            }
            is WorkoutState.Report -> {
                val scope = rememberCoroutineScope()
                WorkoutReportScreen(s.results, s.skipped) {
                    // Uporabi uid iz remember{} (definiran zgoraj) — ne kliči getCurrentUserDocId() znova
                    if (uid != null) {
                       val totalKcal = results.sumOf { it.caloriesKcal }

                        // SAMO AchievementStore skrbi za XP in badge preverjanje
                       // Logic moved to ViewModel to prevent double-counting and ensure transactional safety
                       // scope.launch { ... } removed

                       results.forEach { r ->
                           AlgorithmPreferences.saveExerciseFeedback(context.getAlgoSettings(), r.name, r.difficultyRating)
                       }

                    }
                    vm.handleIntent(com.example.myapplication.viewmodels.BodyHomeIntent.CompleteWorkoutSession(
                        email = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "",
                        isExtraWorkout = isExtra,
                        exerciseResults = results.map { r ->
                            mapOf(
                                "name" to r.name,
                                "activeMinutes" to r.activeMinutes,
                                "restMinutes" to r.restMinutes,
                                "caloriesKcal" to r.caloriesKcal,
                                "difficulty" to r.difficultyRating
                            )
                        },
                        totalKcal = results.sumOf { it.caloriesKcal },
                        totalTimeMin = results.sumOf { it.activeMinutes + it.restMinutes },
                        onCompletion = { result: com.example.myapplication.domain.gamification.WorkoutCompletionResult? ->
                            // Handle badges and navigation only AFTER save is complete
                            if (result != null) {
                                result.unlockedBadges.firstOrNull()?.let { badge -> onBadgeUnlocked(badge) }
                            }
                            onXPAdded()
                            onFinished()
                        }
                    ))
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ExerciseScreen(
    exercise: ExerciseData,
    index: Int,
    total: Int,
    userWeightKg: Double,
    skipCount: Int,
    maxSkips: Int,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    onContinue: (ExerciseResult) -> Unit,
    onEndWorkout: () -> Unit = {}
) {
    val context = LocalContext.current
    var selectedDifficulty by remember { mutableStateOf(1) }
    var currentSet by remember(exercise.name) { mutableIntStateOf(1) }
    var videoInitialized by remember { mutableStateOf(true) }
    var isVideoPlaying by remember { mutableStateOf(true) }
    var skipCount by remember { mutableStateOf(0) }

    // TRACK ACTUAL TIME SPENT ON EXERCISE
    var actualTimeSeconds by remember(exercise.name) { mutableStateOf(0) }
    var isTimerRunning by remember(exercise.name) { mutableStateOf(true) }

    // Timer that tracks actual time spent on this exercise
    LaunchedEffect(exercise.name, isTimerRunning) {
        while (isTimerRunning) {
            delay(1000) // 1 second
            actualTimeSeconds++
        }
    }

    val isTimed = (exercise.reps <= 0 && exercise.durationSecondsOverride != null) || (exercise.durationSecondsOverride != null && exercise.reps == 0)

    // Calculate ESTIMATED time (for display purposes only)
    var estimatedActMin = 0.0
    var estimatedRestMin = 0.0
    val durOverride = exercise.durationSecondsOverride
    if (isTimed && durOverride != null) {
        estimatedActMin = (exercise.sets * durOverride) / 60.0
        estimatedRestMin = ((exercise.sets - 1) * exercise.restSeconds) / 60.0
    } else {
        val repSec = exercise.secondsPerRepOverride ?: 3.0
        val reps = if (exercise.reps > 0) exercise.reps else 12
        estimatedActMin = (exercise.sets * reps * repSec) / 60.0
        estimatedRestMin = ((exercise.sets - 1) * exercise.restSeconds) / 60.0
    }

    var showEndConfirm by remember { mutableStateOf(false) }

    if (showEndConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showEndConfirm = false },
            title = { Text("End workout?") },
            text = { Text("Are you sure you want to end the workout early?") },
            confirmButton = {
                Button(
                    onClick = { showEndConfirm = false; onEndWorkout() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("End", color = MaterialTheme.colorScheme.onError) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showEndConfirm = false }) { Text("Continue") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        // Top row: workout badge + End Workout gumb
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (exercise.isWarmup) "🔥 WARM-UP" else "💪 MAIN WORKOUT",
                style = MaterialTheme.typography.labelLarge,
                color = if (exercise.isWarmup) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            androidx.compose.material3.TextButton(
                onClick = { showEndConfirm = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("End workout", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Text("Exercise ${index+1}/$total")
        LinearProgressIndicator((index+1f)/total, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))

        // Video player - auto plays by default, no toggle button
        val vidUrl = exercise.videoUrl ?: getVideoUrlForExercise(exercise.name)

        // Expanded video player
        Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
            var isBuffering by remember(vidUrl) { mutableStateOf(true) }
            var isVideoReady by remember(vidUrl) { mutableStateOf(false) }
            val player = remember(vidUrl) {
                androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(vidUrl))
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                    addListener(object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            isBuffering = playbackState == androidx.media3.common.Player.STATE_BUFFERING
                            if (playbackState == androidx.media3.common.Player.STATE_READY) {
                                isVideoReady = true
                            }
                        }
                    })
                    prepare()
                    playWhenReady = true
                }
            }

            LaunchedEffect(isVideoPlaying) {
                player.playWhenReady = isVideoPlaying
            }

            DisposableEffect(player) { onDispose { player.release() } }

            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx -> androidx.media3.ui.PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                } },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .graphicsLayer(alpha = if (isVideoReady) 1f else 0f)
            )

            if (isBuffering || !isVideoReady) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(exercise.name, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)

        // Show current set progress
        Text(
            "Set $currentSet / ${exercise.sets}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Show actual time spent on exercise
        Text(
            "Time: ${actualTimeSeconds / 60}:${(actualTimeSeconds % 60).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold
        )

        if (isTimed) {
             Text("Duration: ${exercise.durationSecondsOverride}s per set")
        } else {
             Text("${exercise.repsDisplay} reps per set")
        }

        Spacer(Modifier.height(8.dp))
        Text(exercise.description, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

        // Display execution tips if available
        if (exercise.executionTips.isNotEmpty()) {
            var showTips by remember { mutableStateOf(false) }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = { showTips = !showTips }) {
                Text(if (showTips) "Hide Execution Tips ↑" else "Show Execution Tips ↓", color = MaterialTheme.colorScheme.primary)
            }
            if (showTips) {
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "💡 Execution Tips:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        exercise.executionTips.forEach { tip ->
                            Text(
                                "• $tip",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // Action buttons: Prev / Set Done / Skip
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = {
                com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK)
                onPrevious()
            }) { Text("Prev") }
            Button(onClick = {
                if (currentSet < exercise.sets) {
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.SUCCESS)
                    currentSet++
                } else {
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.HEAVY_CLICK)
                    // Stop timer
                    isTimerRunning = false

                    // Calculate ACTUAL time spent
                    val actualMinutes = actualTimeSeconds / 60.0

                    // Assume 80% was active time, 20% was rest (rough estimate)
                    val actualActiveMin = actualMinutes * 0.8
                    val actualRestMin = actualMinutes * 0.2

                    // Calculate calories based on ACTUAL time spent
                    val actualKcal = calculateActualCalories(
                        caloriesPerKgPerHour = exercise.caloriesPerMinPerKg * 60.0,
                        userWeightKg = userWeightKg,
                        actualMinutes = actualActiveMin
                    ) + (actualRestMin * 1.0 * userWeightKg).toInt()

                    // Apply weight/difficulty adjustment based on feedback
                    // 0=Too Easy → increase multiplier, 1=OK → keep, 2=Too Hard → decrease
                    val adjustmentFactor = when (selectedDifficulty) {
                        0 -> 1.05f  // Too Easy: increase by 5%
                        2 -> 0.95f  // Too Hard: decrease by 5%
                        else -> 1.0f // OK: no change
                    }
                    if (adjustmentFactor != 1.0f) {
                        val currentMultiplier = AlgorithmPreferences.getExerciseMultiplier(context.getAlgoSettings(), exercise.name)
                        val newMultiplier = (currentMultiplier * adjustmentFactor).coerceIn(0.5f, 2.0f)
                        AlgorithmPreferences.saveExerciseMultiplier(context.getAlgoSettings(), exercise.name, newMultiplier)
                    }

                    // Finish exercise with ACTUAL data
                    onContinue(ExerciseResult(
                        name = exercise.name,
                        activeMinutes = actualActiveMin,
                        restMinutes = actualRestMin,
                        caloriesKcal = actualKcal,
                        difficultyRating = selectedDifficulty
                    ))
                }
            }) {
                Text(if (currentSet < exercise.sets) "Set Done" else "Finish")
            }
            Button(onClick = {
                com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK)
                onSkip()
            }, enabled = skipCount < maxSkips) { Text("Skip") }
        }

        Spacer(Modifier.height(12.dp))

        // Feedback section - BELOW action buttons, with text labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "How was it?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            listOf("Too Easy", "OK", "Too Hard").forEachIndexed { i, label ->
                androidx.compose.material3.FilterChip(
                    selected = selectedDifficulty == i,
                    onClick = { selectedDifficulty = i },
                    label = { Text(label, fontSize = 12.sp) },
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun RestScreen(restSeconds: Int, onSkip: () -> Unit, onComplete: () -> Unit) {
    val context = LocalContext.current
    var time by remember { mutableIntStateOf(restSeconds) }
    LaunchedEffect(time) {
        if (time > 0) { delay(1000); time-- } else onComplete()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("REST", style = MaterialTheme.typography.displayLarge)
            Text("$time s", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(8.dp))
            Text("${restSeconds}s based on exercise difficulty", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK)
                onSkip()
            }) { Text("Skip") }
            Button(onClick = {
                com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK)
                time += 10
            }) { Text("+10s") }
        }
    }
}

@Composable
private fun PreviewScreen(day: Int, warmup: List<ExerciseData>, main: List<ExerciseData>, onStart: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("Workout Preview", style = MaterialTheme.typography.headlineMedium)
        }

        if (warmup.isNotEmpty()) {
            Text("Warm-up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            warmup.forEach { ex -> PreviewExerciseCard(ex) }
        }

        if (main.isNotEmpty()) {
            Text("Main Workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            main.forEachIndexed { i, ex -> PreviewExerciseCard(ex, number = i + 1) }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Start Workout", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PreviewExerciseCard(ex: ExerciseData, number: Int? = null) {    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Optional number
            if (number != null) {
                Text(
                    text = "$number.",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(ex.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                val details = if (ex.durationSecondsOverride != null && ex.reps == 0) {
                     if (ex.durationSecondsOverride > 0) "${ex.sets} sets × ${ex.durationSecondsOverride}s" else "${ex.sets} sets"
                } else {
                    "${ex.sets} sets × ${ex.repsDisplay}"
                }
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun WorkoutReportScreen(results: List<ExerciseResult>, skipped: List<String>, onDone: () -> Unit) {
    var selectedFeedback by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Done! 🎉", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        // ADDED: Confirmation banner (P0 UX Improvement)
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = Color(0xFF4CAF50).copy(alpha = 0.15f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✅", fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Workout Saved", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Saved to history • +XP • Streak updated", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        val totalKcal = results.sumOf { it.caloriesKcal }
        Text("Total: $totalKcal kcal burned", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        results.forEach { Text("• ${it.name}: ${it.caloriesKcal} kcal") }

        Spacer(Modifier.height(24.dp))
        Text("How was the workout?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Too Short", "Just Right", "Too Long").forEach { feedback ->
                val isSelected = selectedFeedback == feedback
                Button(
                    onClick = {
                        selectedFeedback = feedback
                        val adjustment = when (feedback) {
                            "Too Short" -> 1
                            "Too Long" -> -1
                            else -> 0
                        }
                        AlgorithmPreferences.saveGlobalFeedback(context.getAlgoSettings(), adjustment)
                        com.example.myapplication.utils.AppToast.showSuccess(context, "Thanks! Adjusting next workout...")
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(feedback)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                if (!isSubmitting) {
                    isSubmitting = true
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !isSubmitting
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Finish")
            }
        }
    }
}

@Composable
private fun WorkoutCelebrationScreen(
    isExtra: Boolean,
    nextDayPreview: String? = null,
    planDay: Int = 0,         // ✅ Prejeto iz BodyModuleHomeViewModel (Firestore SSOT, ne bm_prefs)
    onContinue: () -> Unit
) {
    val scale = remember { Animatable(0f) }
    val streakScale = remember { Animatable(0f) }
    val confettiAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1.2f, tween(400, easing = FastOutSlowInEasing))
        scale.animateTo(0.9f, tween(150))
        scale.animateTo(1.0f, tween(150))
        delay(300)
        confettiAlpha.animateTo(1f, tween(500))
        streakScale.animateTo(1.3f, tween(400, easing = FastOutSlowInEasing))
        streakScale.animateTo(1.0f, tween(200))
    }

    val context = LocalContext.current
    val density = LocalDensity.current.density
    val prefs = context.getSharedPreferences("bm_prefs", android.content.Context.MODE_PRIVATE)
    // streak_days se bere iz bm_prefs (posodobljeno ob zaključku vadbe prek MainActivity/AchievementStore)
    val currentStreak = prefs.getInt("streak_days", 0)
    // planDay prejmemo kot parameter iz BodyModuleHomeViewModel (Firestore SSOT)

    // Animate from (current - 1) to (current)
    val startStreak = (currentStreak - 1).coerceAtLeast(0)
    val targetStreak = currentStreak
    val streakAnim = remember { Animatable(startStreak.toFloat()) }
    
    // Firework animation state for the moment streak changes
    val streakBump = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        delay(1000) // Wait for initial popup to settle
        streakAnim.animateTo(
            targetValue = targetStreak.toFloat(),
            animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
        )
    }

    // Haptics & Bump logic
    LaunchedEffect(streakAnim) {
        var lastVal = startStreak
        snapshotFlow { streakAnim.value.toInt() }
            .collectLatest { current ->
                if (current != lastVal) {
                    lastVal = current
                    // Haptic feedback
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                        context,
                        com.example.myapplication.utils.HapticFeedback.FeedbackType.HEAVY_CLICK
                    )
                    // Visual bump
                    streakBump.snapTo(1.5f)
                    streakBump.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        // Confetti for regular workout
        if (!isExtra) {
            val infiniteTransition = rememberInfiniteTransition(label = "confetti")
            val confettiOffset by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000),
                    repeatMode = RepeatMode.Restart
                ), label = "confettiOff"
            )
            val confettiColors = listOf(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.primary, Color(0xFF4CAF50), MaterialTheme.colorScheme.tertiary, Color(0xFF03A9F4))
            repeat(30) { i -> // Increased confetti count
                val xPos = (i * 83 % 100) / 100f
                val startY = ((i * 47 + confettiOffset * 100) % 110) / 100f
                Box(modifier = Modifier.fillMaxSize().graphicsLayer(alpha = confettiAlpha.value)) {
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = (xPos * 400 - 20).dp, y = (startY * 900 - 50).dp)
                            .size(if(i % 3 == 0) 10.dp else 6.dp)
                            .background(confettiColors[i % confettiColors.size], androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = if (isExtra) "💪" else "🔥",
                fontSize = 80.sp,
                modifier = Modifier.graphicsLayer(scaleX = scale.value, scaleY = scale.value)
            )
            Spacer(Modifier.height(24.dp))

            if (isExtra) {
                Text(
                    "Extra Workout Done!",
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.graphicsLayer(scaleX = streakScale.value, scaleY = streakScale.value)
                )
                Spacer(Modifier.height(8.dp))
                Text("You went beyond today's plan! 💥", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                val armScale = remember { Animatable(0.8f) }
                LaunchedEffect(Unit) {
                    delay(600)
                    while (true) {
                        armScale.animateTo(1.15f, tween(700))
                        armScale.animateTo(0.95f, tween(500))
                    }
                }
                Text("💪→🦾", fontSize = 48.sp, modifier = Modifier.graphicsLayer(scaleX = armScale.value, scaleY = armScale.value))
            } else {
                Text("Workout Complete!", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                androidx.compose.material3.Card(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().graphicsLayer(scaleX = streakScale.value, scaleY = streakScale.value)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔥", fontSize = 36.sp)
                            
                            // Animated streak number
                            Text(
                                text = "${streakAnim.value.toInt()}", 
                                fontSize = 32.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = streakBump.value
                                    scaleY = streakBump.value
                                }
                            )
                            Text("Day streak", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(modifier = Modifier.height(60.dp).width(1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.3f)))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // NO CALENDAR ICON - Just text
                            Spacer(Modifier.height(4.dp))
                            Text("Day $planDay", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("of your plan", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                val motivations = listOf("Keep it up! 🚀", "You're unstoppable! ⚡", "One day at a time! 🎯", "Consistency is key! 🔑", "You're crushing it! 💥")
                Text(motivations[targetStreak % motivations.size], fontSize = 16.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium)
                
                // ADDED: P1 Next Day Preview
                if (!isExtra && nextDayPreview != null) {
                    Spacer(Modifier.height(24.dp))
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = nextDayPreview,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
            ) {
                Text("Continue", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

private fun Context.getAlgoSettings() = com.russhwolf.settings.SharedPreferencesSettings(this.getSharedPreferences("algorithm_prefs", Context.MODE_PRIVATE))
