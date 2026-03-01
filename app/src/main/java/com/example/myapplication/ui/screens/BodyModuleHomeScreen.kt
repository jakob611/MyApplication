package com.example.myapplication.ui.screens

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate


data class Challenge(
    val id: String,
    val title: String,
    val description: String,
    val accepted: Boolean = false
)

data class BodyHomeUiState(
    val streakDays: Int = 0,
    val weeklyDone: Int = 0,
    val weeklyTarget: Int = 4,
    val planDay: Int = 1,
    val totalWorkoutsCompleted: Int = 0, // Skupno število workoutov
    val workoutsToday: Int = 0, // Deprecated but kept for compatibility
    val isWorkoutDoneToday: Boolean = false, // Added for plan view logic
    val showCompletionAnimation: Boolean = false, // Flag for triggering animation once
    val challenges: List<Challenge> = listOf(
        Challenge("c1", "30 days sixpack", "Get a sixpack in 30 days. Short description here..."),
        Challenge("c2", "30 days pushups", "Improve your pushups in 30 days."),
        Challenge("c3", "Mobility week", "Increase your ROM in 7 days.")
    )
)

class BodyModuleHomeViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE)

    // Helper property to get current user email
    private val userEmail: String
        get() = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""

    private val _ui = MutableStateFlow(BodyHomeUiState())
    val ui: StateFlow<BodyHomeUiState> = _ui

    init {
        // OPTIMIZED: Launch only once, and combine both operations into single launch
        viewModelScope.launch {
            updateStreak()
            refreshFromPrefs()
        }
    }

    // Call this after animation finishes
    fun onCompletionAnimationShown() {
        _ui.value = _ui.value.copy(showCompletionAnimation = false)
    }

    private suspend fun refreshFromPrefs() {
        // Premaknjeno na IO dispatcher - ne blokira UI thread
        withContext(Dispatchers.IO) {
            // Load from Firestore to sync
            if (userEmail.isNotEmpty()) {
                try {
                    val remoteStats = com.example.myapplication.data.UserPreferences.getWorkoutStats(userEmail)
                    if (remoteStats != null) {
                        val remoteStreak = remoteStats["streak_days"] as Int
                        val remoteTotal = remoteStats["total_workouts_completed"] as Int
                        val remoteWeekly = remoteStats["weekly_done"] as Int
                        val remoteLastEpoch = remoteStats["last_workout_epoch"] as Long
                        val remotePlanDay = (remoteStats["plan_day"] as? Number)?.toInt() ?: 1 // Read planDay

                        // Determine if remote is newer or better?
                        // Generally, we want to maximize progress or trust the latest sync.
                        val currentLocalTotal = prefs.getInt("total_workouts_completed", 0)
                        val currentLocalPlanDay = prefs.getInt("plan_day", 1)

                        // Sinciraj če: Firestore ima več workoutov, ali če je lokalni planDay še na 1
                        // (npr. po reinstall-u) in Firestore ima napredek
                        val shouldSyncFromRemote = remoteTotal > currentLocalTotal ||
                            (currentLocalPlanDay <= 1 && remotePlanDay > 1)

                        if (shouldSyncFromRemote) {
                            prefs.edit {
                                putInt("streak_days", remoteStreak)
                                putInt("total_workouts_completed", remoteTotal)
                                putInt("weekly_done", remoteWeekly)
                                putInt("plan_day", remotePlanDay)
                                putLong("last_workout_epoch", remoteLastEpoch)
                            }
                        }

                        // Sinciraj weeklyTarget iz Firestorea — local prefs ga nastavi samo ob
                        // kreiranju plana, ampak po reinstall-u ali zamenjavi plana bi bil napačen
                        val remoteWeeklyTarget = (remoteStats["weekly_target"] as? Number)?.toInt()
                        if (remoteWeeklyTarget != null && remoteWeeklyTarget > 0) {
                            val localTarget = prefs.getInt("weekly_target", 0)
                            if (localTarget == 0 || shouldSyncFromRemote) {
                                prefs.edit { putInt("weekly_target", remoteWeeklyTarget) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val day = prefs.getInt("plan_day", 1) // Default to 1 if not set
            val totalWorkouts = prefs.getInt("total_workouts_completed", 0)
            val weeklyTarget = prefs.getInt("weekly_target", 4)
            android.util.Log.d("BodyModuleHomeVM", "📊 weeklyTarget from SharedPrefs: $weeklyTarget")
            val workoutsToday = prefs.getInt("workouts_today", _ui.value.workoutsToday)
            val streak = prefs.getInt("streak_days", 0)

            // Load user preference for start of week
            if (userEmail.isNotEmpty()) {
                val userProfile = com.example.myapplication.data.UserPreferences.loadProfile(getApplication(), userEmail)
                val startDayOfWeek = when (userProfile.startOfWeek) {
                    "Sunday" -> java.time.DayOfWeek.SUNDAY
                    "Saturday" -> java.time.DayOfWeek.SATURDAY
                    else -> java.time.DayOfWeek.MONDAY
                }

                // Check if we need to reset weekly counter
                val today = LocalDate.now()
                val lastWeekReset = prefs.getLong("last_week_reset", 0L)
                val lastResetDate = if (lastWeekReset == 0L) null else LocalDate.ofEpochDay(lastWeekReset)

                val lastWorkoutEpoch = prefs.getLong("last_workout_epoch", 0L)
                val isWorkoutDoneToday = if (lastWorkoutEpoch == 0L) false else LocalDate.ofEpochDay(lastWorkoutEpoch) == today

                // Determine the start of the current week window
                val currentWeekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(startDayOfWeek))

                // If we have never reset, or the last reset happened before the current week started
                val shouldReset = lastResetDate == null || lastResetDate.isBefore(currentWeekStart)

                val weeklyDone = if (shouldReset) {
                    prefs.edit {
                        putInt("weekly_done", 0)
                        // FIX: zapisujemo začetek tedna, ne today, da se reset vezuje na tedenski cikel
                        putLong("last_week_reset", currentWeekStart.toEpochDay())
                    }
                    0
                } else {
                    prefs.getInt("weekly_done", 0)
                }

                // Posodobi state samo če se je kaj spremenilo (distinctUntilChanged filtrera duplikate)
                _ui.value = _ui.value.copy(
                    planDay = day,
                    weeklyDone = weeklyDone,
                    weeklyTarget = weeklyTarget,
                    workoutsToday = workoutsToday, // Keep simplified
                    streakDays = streak,
                    totalWorkoutsCompleted = totalWorkouts,
                    isWorkoutDoneToday = isWorkoutDoneToday // Sync new field
                )
            }
        }
    }

    fun completeWorkoutSession(
        isExtraWorkout: Boolean = false,
        onXPAdded: () -> Unit = {},
        exerciseResults: List<Map<String, Any>> = emptyList(), // vaje za shranjevanje skupaj
        totalKcal: Int = 0,
        totalTimeMin: Double = 0.0
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val today = LocalDate.now()
            val lastWorkoutEpoch = prefs.getLong("last_workout_epoch", 0L)
            val lastWorkoutDate = if (lastWorkoutEpoch == 0L) null else LocalDate.ofEpochDay(lastWorkoutEpoch)
            val isFirstToday = lastWorkoutDate != today

            val userProfile = com.example.myapplication.data.UserPreferences.loadProfile(getApplication(), userEmail)
            val startDayOfWeek = when (userProfile.startOfWeek) {
                "Sunday" -> java.time.DayOfWeek.SUNDAY
                "Saturday" -> java.time.DayOfWeek.SATURDAY
                else -> java.time.DayOfWeek.MONDAY
            }
            val currentWeekStart = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(startDayOfWeek))

            val lastWeekReset = prefs.getLong("last_week_reset", 0L)
            val lastResetDate = if (lastWeekReset == 0L) null else LocalDate.ofEpochDay(lastWeekReset)
            val shouldReset = lastResetDate == null || lastResetDate.isBefore(currentWeekStart)

            val currentWeeklyDone = if (shouldReset) 0 else prefs.getInt("weekly_done", 0)
            val weeklyTarget = prefs.getInt("weekly_target", 4)

            // Extra workout — nima tedenskega locka, shrani takoj
            if (isExtraWorkout) {
                val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                if (uid != null) {
                    val workoutDoc = hashMapOf(
                        "date" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "type" to "extra",
                        "totalKcal" to totalKcal,
                        "totalTimeMin" to totalTimeMin,
                        "exercisesCount" to exerciseResults.size,
                        "planDay" to _ui.value.planDay,
                        "exercises" to exerciseResults
                    )
                    try { com.google.firebase.ktx.Firebase.firestore.collection("users").document(uid).collection("workoutSessions").add(workoutDoc) } catch (_: Exception) {}
                }
                val newTotal = _ui.value.totalWorkoutsCompleted + 1
                val extraCount = prefs.getInt("total_extra_workouts", 0) + 1
                prefs.edit { putInt("total_workouts_completed", newTotal); putInt("total_extra_workouts", extraCount) }
                _ui.value = _ui.value.copy(totalWorkoutsCompleted = newTotal)
                // XP za extra workout skrbi AchievementStore.recordWorkoutCompletion v WorkoutSessionScreen
                // (enako kot regular workout) — tukaj ga ne prištejemo, da ni dvojni XP
                return@launch
            }

            // Regular workout — preveri pogoje PREDEN shranimo karkoli
            if (!isFirstToday) return@launch

            // WEEKLY LOCK — tedenski cilj dosežen
            if (currentWeeklyDone >= weeklyTarget && !shouldReset) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        getApplication(),
                        "Weekly goal reached! Next workout unlocks on ${currentWeekStart.plusWeeks(1)}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            // ─── Vsi pogoji OK — zdaj shrani workout v Firestore ─────────
            val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
            if (uid != null) {
                val workoutDoc = hashMapOf(
                    "date" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "type" to "regular",
                    "totalKcal" to totalKcal,
                    "totalTimeMin" to totalTimeMin,
                    "exercisesCount" to exerciseResults.size,
                    "planDay" to _ui.value.planDay,
                    "exercises" to exerciseResults
                )
                try { com.google.firebase.ktx.Firebase.firestore.collection("users").document(uid).collection("workoutSessions").add(workoutDoc) } catch (_: Exception) {}
            }

            // ─── Regular plan workout ─────────────────────────────────────
            val newWeeklyDone = if (shouldReset) 1 else currentWeeklyDone + 1
            val newPlanDay = _ui.value.planDay + 1
            val newTotal = _ui.value.totalWorkoutsCompleted + 1

            // STREAK in TEŽAVNOST se ne spremenita tukaj —
            // to naredi izključno WeeklyStreakWorker ob polnoči konca tedna.
            // S tem preprečimo dvojni increment (enkrat tukaj + enkrat v Workerju).
            val currentStreak = prefs.getInt("streak_days", 0)
            val context = getApplication<android.app.Application>().applicationContext

            prefs.edit {
                putLong("last_workout_epoch", today.toEpochDay())
                putInt("weekly_done", newWeeklyDone)
                putInt("plan_day", newPlanDay)
                putInt("total_workouts_completed", newTotal)
                // FIX: zapisujemo currentWeekStart (ne today) da je reset vedno vezan na začetek tedna
                if (shouldReset) putLong("last_week_reset", currentWeekStart.toEpochDay())
            }

            if (userEmail.isNotEmpty()) {
                com.example.myapplication.data.UserPreferences.saveWorkoutStats(
                    email = userEmail,
                    streak = currentStreak,
                    totalWorkouts = newTotal,
                    weeklyDone = newWeeklyDone,
                    lastWorkoutEpoch = today.toEpochDay(),
                    planDay = newPlanDay,
                    weeklyTarget = weeklyTarget
                )
            }

            // XP za workout doda izključno WorkoutSessionScreen prek AchievementStore — ne dodajamo tukaj

            // Posodobi streak widget takoj po zaključku vadbe
            val trainingDays = prefs.getInt("weekly_target", 4)
            val planDayLabel = com.example.myapplication.widget.StreakWidgetProvider.planDayToLabel(newPlanDay, trainingDays)
            com.example.myapplication.widget.StreakWidgetProvider.updateWidgetFromApp(context, currentStreak, planDayLabel)

            _ui.value = _ui.value.copy(
                streakDays = currentStreak,
                weeklyDone = newWeeklyDone,
                planDay = newPlanDay,
                totalWorkoutsCompleted = newTotal,
                isWorkoutDoneToday = true,
                showCompletionAnimation = true
            )
            withContext(Dispatchers.Main) { onXPAdded() }
        }
    }
    private suspend fun updateStreak() {
        withContext(Dispatchers.IO) {
            val currentStreak = prefs.getInt("streak_days", 0)
            _ui.value = _ui.value.copy(streakDays = currentStreak)
        }
    }

    fun acceptChallenge(id: String) {
        val updated = _ui.value.challenges.map {
            if (it.id == id) it.copy(accepted = true) else it
        }
        _ui.value = _ui.value.copy(challenges = updated)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyModuleHomeScreen(
    onBack: () -> Unit,
    onStartPlan: () -> Unit,
    onStartWorkout: (PlanResult?) -> Unit,
    onStartAdditionalWorkout: () -> Unit = {}, // Navigate to GenerateWorkout screen
    currentPlan: PlanResult?,
    onOpenHistory: () -> Unit,
    onOpenManualLog: () -> Unit,
    onStartRun: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm: BodyModuleHomeViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )
    val ui by vm.ui.collectAsState()

    val showKnowledge = remember { mutableStateOf(false) }
    val showPlanPath = remember { mutableStateOf(false) } // State for Plan Path Dialog
    val knowledgeQuery = remember { mutableStateOf("") }

    val headerColor = MaterialTheme.colorScheme.onBackground
    val buttonBlue = Color(0xFF6366F1)
    val planCardBg = Color(0xFF2A2D3E)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp)
        ) {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                IconButton(
                    onClick = {
                        com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                            context,
                            com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK
                        )
                        onBack()
                    },
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = headerColor
                    )
                }
                Text(
                    text = "BODY MODULE",
                    color = headerColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }


            // Weekly goal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekly goal",
                    color = headerColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${ui.weeklyDone} / ${ui.weeklyTarget}",
                    color = buttonBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val weeklyProgress = (ui.weeklyDone.toFloat() / ui.weeklyTarget.coerceAtLeast(1))
                .coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { weeklyProgress },
                color = buttonBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )
            Spacer(Modifier.height(16.dp))

            // Your plan card
            Card(
                colors = CardDefaults.cardColors(containerColor = planCardBg),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = "Your plan",
                            color = buttonBlue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val targetDay = if (ui.isWorkoutDoneToday) (if (ui.planDay > 1) ui.planDay - 1 else 1) else ui.planDay
                            EpicCounter(
                                targetValue = targetDay,
                                animate = ui.showCompletionAnimation, // Pass animation flag
                                onAnimationEnd = { vm.onCompletionAnimationShown() }, // Reset flag
                                color = Color.White,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            // Animating Checkmark scale
                            val doneScale = remember { androidx.compose.animation.core.Animatable(0.8f) }
                            LaunchedEffect(ui.isWorkoutDoneToday) {
                                if (ui.isWorkoutDoneToday) {
                                    doneScale.animateTo(1.2f, androidx.compose.animation.core.spring(dampingRatio = 0.5f))
                                    doneScale.animateTo(1f)
                                }
                            }

                            Text(
                                text = if (ui.isWorkoutDoneToday) "Completed" else "Not yet\ncompleted",
                                color = if (ui.isWorkoutDoneToday) Color(0xFF4CAF50) else Color.Gray,
                                fontSize = 14.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.scale(if (ui.isWorkoutDoneToday) doneScale.value else 1f)
                            )
                        }
                        Text(
                            text = "${ui.streakDays} 🔥",
                            color = buttonBlue,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = {
                                com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                                    context,
                                    com.example.myapplication.utils.HapticFeedback.FeedbackType.HEAVY_CLICK
                                )
                                // Check if plan exists before opening PlanPath
                                if (currentPlan == null) {
                                    // No plan - redirect to create plan screen
                                    onStartPlan()
                                } else {
                                    // Plan exists - open path view
                                    showPlanPath.value = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = buttonBlue),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) { Text("Start workout", color = Color.White, fontSize = 16.sp) }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                        context,
                        com.example.myapplication.utils.HapticFeedback.FeedbackType.HEAVY_CLICK
                    )
                    onStartRun()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Start run", color = Color.White, fontSize = 16.sp) }

            Spacer(Modifier.height(20.dp))

            // Challenges
            Text(
                text = "Challenges",
                color = headerColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            val challengesScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(challengesScroll)
            ) {
                ui.challenges.forEach { ch ->
                    ChallengeCard(
                        title = ch.title,
                        description = ch.description,
                        accepted = ch.accepted,
                        onAccept = {
                            com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                                context,
                                com.example.myapplication.utils.HapticFeedback.FeedbackType.SUCCESS
                            )
                            vm.acceptChallenge(ch.id)
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Exercises - klikni za iskanje vaj
            Text(
                text = "Exercises",
                color = headerColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Search bar - takoj odpre manual log
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = "",
                    onValueChange = { },
                    placeholder = { Text("Search exercises...") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Transparent clickable box overlay
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable {
                            com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                                context,
                                com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK
                            )
                            onOpenManualLog()
                        }
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                        context,
                        com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK
                    )
                    onOpenHistory()
                },
                colors = ButtonDefaults.buttonColors(containerColor = buttonBlue),
                shape = RoundedCornerShape(18.dp), // Using a bit more rounded
                modifier = Modifier.fillMaxWidth()
            ) { Text("Exercise history", color = Color.White, fontSize = 17.sp) }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                        context,
                        com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK
                    )
                    showKnowledge.value = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = buttonBlue),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Workout knowledge hub", color = Color.White, fontSize = 17.sp) }
        }

        if (showPlanPath.value) {
            PlanPathDialog(
                currentDay = ui.planDay, // Use planDay instead of totalWorkoutsCompleted
                isTodayDone = ui.isWorkoutDoneToday,
                weeklyGoal = ui.weeklyTarget, // Added weeklyGoal
                onClose = { showPlanPath.value = false },
                onStartToday = {
                    // Navigate directly to workout via loading screen (avoids BodyModuleHome flash)
                    onStartWorkout(currentPlan)
                },
                onStartAdditional = {
                        // Navigate directly to generate workout screen
                        showPlanPath.value = false
                        onStartAdditionalWorkout()
                    },
                    onMyPlan = onStartPlan,
                    currentPlan = currentPlan // Pass the actual plan
                )
        }

        if (showKnowledge.value) {
            KnowledgeHubFullScreen(
                query = knowledgeQuery.value,
                onQueryChange = { knowledgeQuery.value = it },
                onClose = { showKnowledge.value = false }
            )
        }
    }
}

@Composable
private fun ChallengeCard(
    title: String,
    description: String,
    accepted: Boolean,
    onAccept: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(260.dp)
            .padding(end = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color(0xFF757575),
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Button(
                    onClick = onAccept,
                    enabled = !accepted,
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text(if (accepted) "ACCEPTED" else "ACCEPT")
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KnowledgeHubFullScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    // Handle back button
    androidx.activity.compose.BackHandler { onClose() }

    // FIXED: remember must be called at composable function level, not inside LazyColumn items
    val items = remember { KNOWLEDGE_ITEMS }
    val filtered = remember(query, items) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) items else items.filter { item ->
            item.searchTokens.any { token -> token.contains(q) }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Knowledge hub",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search gear, theory, tips…") }
                )
            }


            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No tips found. Try a different keyword.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filtered) { item ->
                    KnowledgeCard(item)
                }
            }
        }
    }
}

@Composable
private fun KnowledgeCard(item: KnowledgeItem) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(item.description, style = MaterialTheme.typography.bodyMedium)
            if (item.suggestedExercises.isNotEmpty()) {
                Text("Try:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                item.suggestedExercises.forEach { ex ->
                    Text("• $ex", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (item.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.tags.forEach { tag ->
                        AssistChip(onClick = { /* no-op */ }, label = { Text(tag) })
                    }
                }
            }
            Text(
                "Images are fetched online when available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

private data class KnowledgeItem(
    val title: String,
    val description: String,
    val suggestedExercises: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val imageUrl: String? = null,
    val searchTokens: List<String> = emptyList()
)

// OPTIMIZED: Static knowledge items - loaded once, not recreated on recomposition
private val KNOWLEDGE_ITEMS = listOf(
    KnowledgeItem(
        title = "Dumbbell",
        description = "Versatile free weight useful for unilateral strength, stability, and hypertrophy work. Start with weight that keeps form solid.",
        suggestedExercises = listOf("Dumbbell press", "Goblet squat", "Dumbbell row", "Farmer's walk"),
        tags = listOf("equipment", "strength"),
        searchTokens = listOf("dumbbell", "versatile free weight useful for unilateral strength, stability, and hypertrophy work. start with weight that keeps form solid.", "equipment", "strength")
    ),
    KnowledgeItem(
        title = "Kettlebell",
        description = "Great for power and conditioning. Hip-dominant moves teach hinges and explosiveness.",
        suggestedExercises = listOf("Kettlebell swing", "Turkish get-up", "Goblet lunge"),
        tags = listOf("equipment", "conditioning"),
        searchTokens = listOf("kettlebell", "great for power and conditioning. hip-dominant moves teach hinges and explosiveness.", "equipment", "conditioning")
    ),
    KnowledgeItem(
        title = "Workout split basics",
        description = "Plan 2–4 full-body days if busy. Upper/lower splits work well 3–4x weekly. Prioritize compound lifts, sprinkle accessories.",
        suggestedExercises = listOf("Squat", "Bench/Push-up", "Row/Pull-up", "Hinge (DL/hip thrust)", "Carry"),
        tags = listOf("theory", "programming"),
        searchTokens = listOf("workout split basics", "plan 2–4 full-body days if busy. upper/lower splits work well 3–4x weekly. prioritize compound lifts, sprinkle accessories.", "theory", "programming")
    ),
    KnowledgeItem(
        title = "Warm-up blueprint",
        description = "Start with 3–5 min easy cardio, then dynamic mobility for joints you train, finish with 1–2 ramp-up sets of the first lift.",
        suggestedExercises = listOf("Arm circles", "World's greatest stretch", "Glute bridge", "Bodyweight squats"),
        tags = listOf("warmup", "mobility"),
        searchTokens = listOf("warm-up blueprint", "start with 3–5 min easy cardio, then dynamic mobility for joints you train, finish with 1–2 ramp-up sets of the first lift.", "warmup", "mobility")
    ),
    KnowledgeItem(
        title = "Home cardio options",
        description = "Mix steady pacing with intervals. Keep knee-friendly choices if needed.",
        suggestedExercises = listOf("Jog or brisk walk", "Jump rope", "Shadow boxing", "Step-ups"),
        tags = listOf("cardio", "conditioning"),
        searchTokens = listOf("home cardio options", "mix steady pacing with intervals. keep knee-friendly choices if needed.", "cardio", "conditioning")
    )
)

@Composable
fun PlanPathDialog(
    currentDay: Int,
    isTodayDone: Boolean,
    weeklyGoal: Int,
    onClose: () -> Unit,
    onStartToday: () -> Unit,
    onStartAdditional: () -> Unit,
    onMyPlan: () -> Unit,
    currentPlan: PlanResult? // Added to get actual plan structure
) {
    // Handle back button
    androidx.activity.compose.BackHandler { onClose() }

    val context = LocalContext.current

    // Read activityLevel from SharedPreferences (synced from Firestore on app startup)
    val bmPrefs = context.getSharedPreferences("bm_prefs", android.content.Context.MODE_PRIVATE)
    val localActivityDays = bmPrefs.getInt("weekly_target", 0)

    // Priority: 1) SharedPrefs weekly_target (synced from Firestore), 2) plan.trainingDays, 3) weeklyGoal param
    val planFrequency = when {
        localActivityDays > 0 -> localActivityDays
        currentPlan?.trainingDays != null && currentPlan.trainingDays > 0 -> currentPlan.trainingDays
        else -> weeklyGoal
    }
    val safeGoal = if (planFrequency > 0) planFrequency else 4


    // Calculate total days: ALWAYS 4 weeks × user's chosen activity level
    // Don't use plan.weeks because determineOptimalTrainingDays may have reduced it
    val totalPlanDays = 4 * safeGoal
    // startWeek je vedno 1 — PlanPathVisualizer prikaže vse dni od 1 naprej
    val blockStartWeek = 1

    // Dark mode support
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor = if (isDark) MaterialTheme.colorScheme.background else Color(0xFFF5F5F5)
    val textColor = if (isDark) MaterialTheme.colorScheme.onBackground else Color.Black
    val subtitleColor = if (isDark) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f) else Color.Gray

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Weeks $blockStartWeek - ${blockStartWeek + 3}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            "Keep the consistency!",
                            fontSize = 14.sp,
                            color = subtitleColor
                        )
                    }

                    // Info / My Plan button
                    IconButton(onClick = onMyPlan) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = "My Plan",
                            tint = textColor
                        )
                    }
                }

                // Glavna scrollable vsebina brez footerja
                PlanPathVisualizer(
                    currentDayGlobal = currentDay,
                    isTodayDone = isTodayDone,
                    weeklyGoal = safeGoal,
                    totalDays = totalPlanDays,
                    startWeek = blockStartWeek,
                    isDarkMode = isDark,
                    onNodeClick = { clickedGlobalDay ->
                        if (clickedGlobalDay == currentDay) {
                            if (!isTodayDone) {
                                onStartToday()
                            } else {
                                android.widget.Toast.makeText(context, "Workout for today already done!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else if (clickedGlobalDay < currentDay) {
                            // Past
                        } else {
                            // Future
                            android.widget.Toast.makeText(context, "Locked until you reach this day!", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    footerContent = {}  // Prazno – footer je zdaj zunaj
                )
            }

            // Floating footer vedno na dnu zaslona
            if (isTodayDone) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Good job! Today is done. ✅",
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.Black,
                            fontSize = 18.sp
                        )
                        Text(
                            "Locked for next day. Want to do more?",
                            color = if (isDark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f) else Color.Gray,
                            fontSize = 14.sp
                        )
                        Button(
                            onClick = onStartAdditional,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                        ) {
                            Text("Extra Workout", color = Color.White)
                        }
                    }
                }
            } else {
                Button(
                    onClick = onStartToday,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.BottomCenter)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    shape = RoundedCornerShape(28.dp)
                ) {
                    Text("START DAY $currentDay", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun PlanPathVisualizer(
    currentDayGlobal: Int,
    isTodayDone: Boolean,
    weeklyGoal: Int,
    totalDays: Int, // Total training days from actual plan
    startWeek: Int,
    isDarkMode: Boolean,
    onNodeClick: (Int) -> Unit,
    footerContent: @Composable () -> Unit
) {
    val totalNodes = totalDays
    val scrollState = rememberScrollState()

    // Calculate which week the user is currently "in"
    val currentWeek = ((currentDayGlobal - 1) / weeklyGoal) + 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
        val containerWidth = this@BoxWithConstraints.maxWidth

        // Estimate height
        val nodeVerticalSpacing = 100f // dp
        val totalHeightDp = (totalNodes * (nodeVerticalSpacing / 2f)) + 650
        val totalHeight = totalHeightDp.dp

        // Generate normalized points
        val points = remember(weeklyGoal, totalNodes) {
            val list = mutableListOf<Pair<Float, Float>>()
            for (i in 0 until totalNodes) {
                val x = 0.5f + 0.35f * kotlin.math.sin(i * 0.8).toFloat()
                val y = 50f + (i * 80f)
                list.add(Pair(x, y))
            }
            list
        }

        // Draw Lines (Connections) - Background Layer
        androidx.compose.foundation.Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(totalHeight)) {

            val canvasWidth = size.width

            // 1. Draw Week Separators
            if (totalNodes > 0 && weeklyGoal > 0) {
                val pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f)
                for (i in weeklyGoal until totalNodes step weeklyGoal) {
                    val separatorY = (50f + (i * 80f) - 40f).dp.toPx()

                    drawLine(
                        color = if (isDarkMode) Color.DarkGray else Color.LightGray,
                        start = androidx.compose.ui.geometry.Offset(0f, separatorY),
                        end = androidx.compose.ui.geometry.Offset(canvasWidth, separatorY),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = pathEffect
                    )
                }
            }

            if (points.size > 1) {
                for (i in 0 until points.size - 1) {
                    val start = points[i]
                    val end = points[i+1]

                    val startX = start.first * canvasWidth
                    val startY = start.second.dp.toPx()
                    val endX = end.first * canvasWidth
                    val endY = end.second.dp.toPx()

                    val dayStart = (startWeek - 1) * weeklyGoal + (i + 1)
                    val isStartNodeFinished = dayStart < currentDayGlobal

                    val lineColor = if (isStartNodeFinished) Color(0xFF4CAF50) else Color.Gray

                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(startX, startY),
                        end = androidx.compose.ui.geometry.Offset(endX, endY),
                        strokeWidth = 4.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }
        }

        // Draw Nodes
        Box(modifier = Modifier.fillMaxWidth().height(totalHeight)) {
            points.forEachIndexed { index, point ->
                val dayInBlock = index + 1
                val globalDay = (startWeek - 1) * weeklyGoal + dayInBlock

                val weekNumOfNode = ((globalDay - 1) / weeklyGoal) + 1
                val isLastDayOfWeek = index % weeklyGoal == weeklyGoal - 1

                val isPast = globalDay < currentDayGlobal
                val isToday = globalDay == currentDayGlobal
                val isLockedToday = isToday && isTodayDone

                // Future states
                val isFuture = globalDay > currentDayGlobal
                val isFutureWeek = weekNumOfNode > currentWeek
                val isFutureInCurrentWeek = isFuture && !isFutureWeek

                val isCompleted = isPast

                // Colors
                val nodeColor = when {
                    isCompleted -> if (isLastDayOfWeek) Color(0xFFFFD700) else Color(0xFF4CAF50)
                    isLockedToday -> if (isDarkMode) Color(0xFF424242) else Color(0xFFEEEEEE) // Locked State
                    isToday -> if (isDarkMode) Color.DarkGray else Color(0xFFE0E0E0) // Active State (Not Blue)
                    isFutureWeek -> if (isDarkMode) Color.DarkGray else Color.LightGray
                    isFutureInCurrentWeek -> if (isDarkMode) Color(0xFF424242) else Color(0xFFEEEEEE)
                    else -> Color(0xFFE0E0E0)
                }

                val nodeSize = 56.dp
                val leftOffset = (containerWidth * point.first) - (nodeSize / 2)
                val topOffset = point.second.dp - (nodeSize / 2)

                Box(
                    modifier = Modifier
                        .absoluteOffset(x = leftOffset, y = topOffset)
                        .size(nodeSize)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(nodeColor)
                        .clickable {
                            onNodeClick(globalDay)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isCompleted) {
                            if (isLastDayOfWeek) {
                                // Trophy for last day
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "Trophy",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = "$globalDay",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "✓",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else if (isLockedToday || isFuture) {
                            if (isLastDayOfWeek) {
                                 Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "Trophy Locked",
                                    tint = if (isLockedToday) Color.White.copy(alpha=0.5f) else Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Locked",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        } else {
                            // Active Today (Not Locked)
                            val textColor = if (isToday) (if (isDarkMode) Color.White else Color.Black) else (if (isDarkMode) Color.White else Color.Black)
                            Text(
                                text = "$globalDay",
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }

                // Week Labels
                if (index % weeklyGoal == 0 && index > 0) {
                    val weekNum = (index / weeklyGoal) + 1

                    val labelLeft = if (point.first > 0.5f)
                        (containerWidth * point.first) - 120.dp
                    else
                        (containerWidth * point.first) + 40.dp

                    // Label position: below line (which is ~ topOffset - 40dp)
                    // Move label closer to node
                    val labelTop = topOffset + 25.dp

                    Column(modifier = Modifier.absoluteOffset(x = labelLeft, y = labelTop)) {
                        Text(
                            text = "WEEK $weekNum",
                            color = if (isDarkMode) Color.LightGray else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        )
                        if (weekNum > currentWeek) {
                             Text(
                                text = "Locked",
                                color = if (isDarkMode) Color.DarkGray else Color.LightGray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
        footerContent()
    }
}

}

@Composable
fun EpicCounter(
    targetValue: Int,
    animate: Boolean,
    onAnimationEnd: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 34.sp,
    fontWeight: FontWeight = FontWeight.Bold
) {
    // Start at target if not animating, otherwise start at 0 (or appropriate start)
    // Actually, remember initialization runs once.
    // If animate becomes true later, launchedEffect handles it.
    // Ideally we start at target to avoid flash of 0 on first load.
    val count = remember { androidx.compose.animation.core.Animatable(targetValue.toFloat()) }

    LaunchedEffect(animate, targetValue) {
        if (animate) {
            // Reset to 0 (or start point) and animate to target
            count.snapTo(0f)
            val result = count.animateTo(
                targetValue = targetValue.toFloat(),
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 1500, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            )
            // Animation finished
            if (result.endReason == androidx.compose.animation.core.AnimationEndReason.Finished) {
                onAnimationEnd()
            }
        } else {
            // Ensure we are at target (if state restored or changed without animation)
            count.snapTo(targetValue.toFloat())
        }
    }

    val scale = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(count.value) {
        if (count.value == targetValue.toFloat() && animate) { // Scale only if animating
            scale.animateTo(1.2f, androidx.compose.animation.core.tween(150))
            scale.animateTo(1f, androidx.compose.animation.core.tween(150))
        }
    }

    Text(
        text = "DAY ${count.value.toInt()}",
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier.scale(scale.value)
    )
}
// Ensure file ends correctly

