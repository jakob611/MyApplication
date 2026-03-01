package com.example.myapplication.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.myapplication.data.AdvancedExerciseRepository
import com.example.myapplication.data.RefinedExercise
import com.example.myapplication.domain.WorkoutGenerator
import com.example.myapplication.data.AlgorithmPreferences
import com.example.myapplication.domain.WorkoutGoal // Added import
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private fun getVideoUrlForExercise(exerciseName: String): String {
    val baseUrl = "https://storage.googleapis.com/fitness-videos-glowupp/"
    val normalizedName = exerciseName.trim().replace(Regex("[\\s-]+"), "_").replace(Regex("[^a-zA-Z0-9_]"), "")
    return "${baseUrl}${normalizedName}_Female.mp4"
}

private object ExerciseCache {
    fun initAdvancedRepo(context: Context) {
        AdvancedExerciseRepository.init(context)
    }
}

private sealed class WorkoutState {
    object Loading : WorkoutState()
    data class Exercise(val index: Int) : WorkoutState()
    data class Rest(val nextIndex: Int, val restSeconds: Int = 60) : WorkoutState()
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
    LaunchedEffect(Unit) { ExerciseCache.initAdvancedRepo(context) }

    val vm: BodyModuleHomeViewModel = viewModel(factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application))

    var userWeightKg by remember { mutableStateOf(70.0) }
    // uid v remember{} — ne kliči ob vsakem recomposition
    val uid = remember { com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() }
    LaunchedEffect(uid) {
        if (uid != null) {
            try {
                Firebase.firestore.collection("users").document(uid).collection("weightLogs")
                    .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(1).get()
                    .addOnSuccessListener {
                        val w = it.documents.firstOrNull()?.get("weightKg") as? Number
                        if (w != null) userWeightKg = w.toDouble()
                    }
            } catch(e: Exception) {}
        }
    }

    var currentSessionExercises by remember { mutableStateOf<List<ExerciseData>>(emptyList()) }
    var state by remember { mutableStateOf<WorkoutState>(WorkoutState.Loading) }

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

        val multiplier = AlgorithmPreferences.getExerciseMultiplier(context, normalizedName)

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
        AdvancedExerciseRepository.init(context)

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
        AlgorithmPreferences.initDifficultyForPlan(context, currentPlan.experience)

        // Določi ciljno težavnost glede na recovery/catchup/normalen način
        val isRecovery = AlgorithmPreferences.isRecoveryMode(context)
        val weeklyTarget = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
            .getInt("weekly_target", 3)
        val weeklyDone = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
            .getInt("weekly_done", 0)

        val targetDifficulty: Float = when {
            isRecovery -> {
                // Recovery: linearna interpolacija od 50% do 100% težavnosti skozi teden
                AlgorithmPreferences.getRecoveryDayDifficulty(context, weeklyDone, weeklyTarget)
            }
            else -> {
                AlgorithmPreferences.getCurrentDifficulty(context, currentPlan.experience)
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
                val email = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
                val profile = com.example.myapplication.data.UserPreferences.loadProfile(context, email)
                profile.focusAreas.ifEmpty { listOf("Full Body") }
            }
            val allFocus = if (rawFocus.any { it.equals("None", ignoreCase = true) }) {
                listOf("Full Body")
            } else {
                rawFocus
            }
            val planDay = context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)
                .getInt("plan_day", 1).coerceAtLeast(1)
            dailyFocus = if (allFocus.any {
                    it.equals("Full Body", ignoreCase = true) ||
                    it.equals("Balance", ignoreCase = true)
                } || allFocus.isEmpty()) {
                setOf("Full Body")
            } else {
                val index = (planDay - 1) % allFocus.size
                setOf(allFocus[index])
            }
        }

        // Equipment: za extra workout direktno iz parametra, sicer iz plana
        val equipment: Set<String>
        if (isExtra && extraEquipment.isNotEmpty()) {
            equipment = extraEquipment.map { it.trim().lowercase() }.toSet()
        } else {
            val rawEquipment = currentPlan.equipment.ifEmpty {
                val email = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
                val profile = com.example.myapplication.data.UserPreferences.loadProfile(context, email)
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
                            if (s.index < currentSessionExercises.size - 1) state = WorkoutState.Rest(s.index+1, ex.restSeconds) else state = WorkoutState.Report(results, skippedExercises)
                        }
                    )
                }
            }
            is WorkoutState.Report -> {
                val scope = rememberCoroutineScope()
                WorkoutReportScreen(s.results, s.skipped) {
                    // Uporabi uid iz remember{} (definiran zgoraj) — ne kliči getCurrentUserDocId() znova
                    if (uid != null) {
                       val totalKcal = results.sumOf { it.caloriesKcal }

                       // SAMO AchievementStore skrbi za XP in badge preverjanje
                       scope.launch {
                           val currentHour = java.time.LocalTime.now().hour
                           val email = Firebase.auth.currentUser?.email ?: ""
                           com.example.myapplication.persistence.AchievementStore.recordWorkoutCompletion(
                               context = context,
                               email = email,
                               caloriesBurned = totalKcal.toDouble(),
                               hour = currentHour
                           )

                           // Preveri badge-e enkrat — recordWorkoutCompletion je že posodobil profil
                           val updatedProfile = com.example.myapplication.data.UserPreferences.loadProfile(context, email)
                           val newBadges = com.example.myapplication.persistence.AchievementStore.checkAndUnlockBadges(context, updatedProfile)

                           // Prikaži animacijo za prvi odklenjen badge
                           newBadges.firstOrNull()?.let { badge ->
                               kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                   onBadgeUnlocked(badge)
                               }
                           }

                           kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                               onXPAdded()
                           }
                       }

                       results.forEach { r ->
                           AlgorithmPreferences.saveExerciseFeedback(context, r.name, r.difficultyRating)
                       }

                    }
                    vm.completeWorkoutSession(
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
                        totalTimeMin = results.sumOf { it.activeMinutes + it.restMinutes }
                    )
                    onFinished()
                }
            }
        }
    }
}

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
    onContinue: (ExerciseResult) -> Unit
) {
    val context = LocalContext.current
    var selectedDifficulty by remember { mutableStateOf(1) }
    var currentSet by remember(exercise.name) { mutableIntStateOf(1) }
    var isVideoPlaying by remember(exercise.name) { mutableStateOf(false) }
    var videoInitialized by remember(exercise.name) { mutableStateOf(false) }

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
    if (isTimed && exercise.durationSecondsOverride != null) {
        estimatedActMin = (exercise.sets * exercise.durationSecondsOverride) / 60.0
        estimatedRestMin = ((exercise.sets - 1) * exercise.restSeconds) / 60.0
    } else {
        val repSec = exercise.secondsPerRepOverride ?: 3.0
        val reps = if (exercise.reps > 0) exercise.reps else 12
        estimatedActMin = (exercise.sets * reps * repSec) / 60.0
        estimatedRestMin = ((exercise.sets - 1) * exercise.restSeconds) / 60.0
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        // Exercise type badge
        Text(
            if (exercise.isWarmup) "🔥 WARM-UP" else "💪 MAIN WORKOUT",
            style = MaterialTheme.typography.labelLarge,
            color = if (exercise.isWarmup) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Text("Exercise ${index+1}/$total")
        LinearProgressIndicator((index+1f)/total, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(8.dp))

        // Video player - compact button first, expands on click
        val vidUrl = exercise.videoUrl ?: getVideoUrlForExercise(exercise.name)

        if (videoInitialized) {
            // Expanded video player
            Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                val player = remember(vidUrl) {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri(vidUrl))
                        repeatMode = Player.REPEAT_MODE_ALL
                        prepare()
                        playWhenReady = true
                    }
                }

                LaunchedEffect(isVideoPlaying) {
                    player.playWhenReady = isVideoPlaying
                }

                DisposableEffect(player) { onDispose { player.release() } }

                AndroidView(
                    factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Compact play button - no big black box
            Button(
                onClick = {
                    videoInitialized = true
                    isVideoPlaying = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Blue
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("▶  Play Video", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "💡 Execution Tips:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    exercise.executionTips.forEach { tip ->
                        Text(
                            "• $tip",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
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
                        val currentMultiplier = AlgorithmPreferences.getExerciseMultiplier(context, exercise.name)
                        val newMultiplier = (currentMultiplier * adjustmentFactor).coerceIn(0.5f, 2.0f)
                        AlgorithmPreferences.saveExerciseMultiplier(context, exercise.name, newMultiplier)
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
private fun PreviewExerciseCard(ex: ExerciseData, number: Int? = null) {
    androidx.compose.material3.Card(
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
    val context = LocalContext.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Done!", style = MaterialTheme.typography.headlineLarge)
        results.forEach { Text("${it.name}: ${it.caloriesKcal} kcal") }

        Spacer(Modifier.height(20.dp))
        Text("Overall Feedback:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Too Short", "Just Right", "Too Long").forEach { feedback ->
                Button(
                    onClick = {
                        // Save feedback and notify user
                        val adjustment = when(feedback) {
                            "Too Short" -> 1
                            "Too Long" -> -1
                            else -> 0
                        }
                        AlgorithmPreferences.saveGlobalFeedback(context, adjustment)
                        android.widget.Toast.makeText(context, "Thanks! Adjusting next workout...", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedFeedback == feedback) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(feedback)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Finish") }
    }
}
