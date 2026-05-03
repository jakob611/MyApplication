package com.example.myapplication.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape // ADDED
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.PlanResult
import com.example.myapplication.viewmodels.BodyModuleHomeViewModel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.setValue // Add this

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyModuleHomeScreen(
    onBack: () -> Unit,
    onStartPlan: () -> Unit,
    onStartWorkout: (PlanResult?) -> Unit,
    onStartAdditionalWorkout: () -> Unit = {},
    currentPlan: com.example.myapplication.data.PlanResult? = null,
    onOpenHistory: () -> Unit = {},
    onOpenManualLog: () -> Unit = {},
    onStartRun: () -> Unit = {},
    onOpenActivityLog: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm: BodyModuleHomeViewModel = viewModel(
        factory = com.example.myapplication.ui.screens.MyViewModelFactory(context.applicationContext)
    )
    val ui by vm.ui.collectAsState()

    // Ob vstopu brez plana → takoj preusmeri na "No plans yet" (BodyOverview)
    LaunchedEffect(currentPlan) {
        if (currentPlan == null) {
            onStartPlan()
        }
    }

    // Ob vsakem prikazu zaslona osveži stats (weekly_target, streak itd.)
    // Faza 13.2: LaunchedEffect(Unit) se požene ob vsaki re-kompoziciji zaslona (po navigaciji nazaj).
    LaunchedEffect(Unit) {
        val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
        vm.handleIntent(com.example.myapplication.viewmodels.BodyHomeIntent.LoadMetrics(userEmail))
    }

    // Faza 4b: Toast + HapticFeedback ko se streak poveča (workout ali stretching)
    LaunchedEffect(vm) {
        vm.streakUpdatedEvent.collect { event ->
            // S24 Ultra natančna vibracija — SUCCESS tip
            com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                context,
                com.example.myapplication.utils.HapticFeedback.FeedbackType.SUCCESS
            )
            val msg = "Daily Goal Met! Streak: ${event.newStreak} days 🔥"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val showKnowledge = remember { mutableStateOf(false) }
    val showPlanPath = remember { mutableStateOf(false) } // State for Plan Path Dialog
    val knowledgeQuery = remember { mutableStateOf("") }
    var selectedChallenge by remember { mutableStateOf<com.example.myapplication.viewmodels.Challenge?>(null) } // State for challenge detail

    val headerColor = MaterialTheme.colorScheme.onBackground
    val buttonBlue = MaterialTheme.colorScheme.primary
    val planCardBg = MaterialTheme.colorScheme.surface

    // Onboarding Hint state
    val prefs = context.getSharedPreferences("app_flags", android.content.Context.MODE_PRIVATE)
    var showOnboarding by remember { mutableStateOf(!prefs.getBoolean("hide_body_hint", false)) }

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

            // Onboarding Hint
            if (showOnboarding) {
                com.example.myapplication.ui.components.OnboardingHint(
                    title = "Welcome to Body Module",
                    message = "Here you can track your workout plans, manage daily goals, and check your streak. Your plan automatically updates each day you complete a session.",
                    onDismiss = {
                        showOnboarding = false
                        prefs.edit().putBoolean("hide_body_hint", true).apply()
                    }
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

            val target = ui.weeklyTarget.coerceAtLeast(1)
            val rawProgress = (ui.weeklyDone.toFloat() / target).coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = rawProgress,
                animationSpec = tween(durationMillis = 1000),
                label = "weeklyProgress"
            )

            LinearProgressIndicator(
                progress = { animatedProgress },
                color = buttonBlue,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(MaterialTheme.shapes.small) // Lepši zaobljeni robovi
            )
            Spacer(Modifier.height(16.dp))

            // Rest Activity Card (only shows on rest days)
            if (ui.todayIsRest && !ui.isWorkoutDoneToday) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable {
                        com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.SUCCESS)
                        vm.handleIntent(com.example.myapplication.viewmodels.BodyHomeIntent.CompleteRestDay)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🧘", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Rest Day: Mobility & Stretching", color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
                            Text("Take 5 mins to stretch +10 XP", color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Your plan card
            val targetDay = if (ui.isWorkoutDoneToday) (if (ui.planDay > 1) ui.planDay - 1 else 1) else ui.planDay
            
            Card(
                colors = CardDefaults.cardColors(containerColor = planCardBg),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = targetDay to ui.todayIsRest,
                    transitionSpec = {
                        androidx.compose.animation.fadeIn(animationSpec = tween(500)) togetherWith 
                        androidx.compose.animation.fadeOut(animationSpec = tween(500))
                    },
                    label = "plan_day_transition"
                ) { (animTargetDay, animIsRestDay) ->
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // ADDED: Day Type Badge (P0 UX Improvement)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (animIsRestDay) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else Color(0xFF4CAF50).copy(alpha = 0.15f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (animIsRestDay) MaterialTheme.colorScheme.secondary else Color(0xFF4CAF50))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (animIsRestDay) "TODAY IS A REST DAY" else "TODAY IS A WORKOUT DAY",
                                color = if (animIsRestDay) MaterialTheme.colorScheme.secondary else Color(0xFF4CAF50),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

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
                                    EpicCounter(
                                        targetValue = animTargetDay,
                                        animate = ui.showCompletionAnimation, // Pass animation flag
                                        onAnimationEnd = { vm.handleIntent(com.example.myapplication.viewmodels.BodyHomeIntent.HideCompletionAnimation) }, // Reset flag
                                        color = MaterialTheme.colorScheme.onSurface,
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
                                        color = if (ui.isWorkoutDoneToday) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.scale(if (ui.isWorkoutDoneToday) doneScale.value else 1f)
                                    )
                                }
                                
                                StreakCounter(
                                    targetValue = ui.streakDays,
                                    animate = ui.showCompletionAnimation,
                                    color = buttonBlue,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                // Faza 13.3: Streak Freeze prikaži, če ima uporabnik zamrznitve
                                if (ui.streakFreezes > 0) {
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "❄️ × ${ui.streakFreezes}",
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Streak Freeze",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

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
                                    shape = MaterialTheme.shapes.large,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                                ) { Text("Start workout", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp) }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "Start run" gumb (razširi se čez celo širino minus gumb zemljevida)
                Button(
                    onClick = {
                        com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                            context,
                            com.example.myapplication.utils.HapticFeedback.FeedbackType.HEAVY_CLICK
                        )
                        onStartRun()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f)
                ) { Text("Start run", color = MaterialTheme.colorScheme.onTertiary, fontSize = 16.sp) }

                // 🗺️ Gumb za Activity Log
                IconButton(
                    onClick = {
                        com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                            context,
                            com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK
                        )
                        onOpenActivityLog()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                ) {
                    Text("🗺️", fontSize = 22.sp)
                }
            }

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
                        challenge = ch,
                        onClick = {
                             com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                                context,
                                com.example.myapplication.utils.HapticFeedback.FeedbackType.CLICK
                            )
                            selectedChallenge = ch
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
                shape = MaterialTheme.shapes.large, // Using a bit more rounded
                modifier = Modifier.fillMaxWidth()
            ) { Text("Exercise history", color = MaterialTheme.colorScheme.onPrimary, fontSize = 17.sp) }

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
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Workout knowledge hub", color = MaterialTheme.colorScheme.onPrimary, fontSize = 17.sp) }
        }

        if (showPlanPath.value) {
            PlanPathDialog(
                currentDay = ui.planDay,
                isTodayDone = ui.isWorkoutDoneToday,
                weeklyGoal = ui.weeklyTarget,
                onClose = { showPlanPath.value = false },
                onStartToday = {
                    onStartWorkout(currentPlan)
                },
                onStartAdditional = {
                    showPlanPath.value = false
                    onStartAdditionalWorkout()
                },
                onMyPlan = onStartPlan,
                currentPlan = currentPlan,
                vm = vm,
                onPlanUpdated = { /* plan posodobi se v localPlan znotraj dialoga */ }
            )
        }

        if (showKnowledge.value) {
            KnowledgeHubFullScreen(
                query = knowledgeQuery.value,
                onQueryChange = { knowledgeQuery.value = it },
                onClose = { showKnowledge.value = false }
            )
        }
        
        selectedChallenge?.let { ch ->
            ChallengeDetailDialog(
                challenge = ch,
                onDismiss = { selectedChallenge = null },
                onAccept = {
                    vm.handleIntent(com.example.myapplication.viewmodels.BodyHomeIntent.AcceptChallenge(ch.id))
                    selectedChallenge = null
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                        context,
                        com.example.myapplication.utils.HapticFeedback.FeedbackType.SUCCESS
                    )
                },
                onComplete = {
                    vm.handleIntent(com.example.myapplication.viewmodels.BodyHomeIntent.CompleteChallenge(ch.id))
                    selectedChallenge = null
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                        context,
                        com.example.myapplication.utils.HapticFeedback.FeedbackType.SUCCESS
                    )
                }
            )
        }
    }
}

@Composable
private fun ChallengeCard(
    challenge: com.example.myapplication.viewmodels.Challenge,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(260.dp)
            .padding(end = 12.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = challenge.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = challenge.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (challenge.accepted) {
                    Text(
                        text = if (challenge.completed) "COMPLETED" else "ACCEPTED",
                        color = if (challenge.completed) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChallengeDetailDialog(
    challenge: com.example.myapplication.viewmodels.Challenge,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
    onComplete: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🏆", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = challenge.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = challenge.description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text(
                        text = "Reward: +${challenge.xpReward} XP",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                
                Spacer(Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (!challenge.accepted) {
                        Button(
                            onClick = onAccept,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("ACCEPT CHALLENGE")
                        }
                    } else if (!challenge.completed) {
                        Button(
                            onClick = onComplete,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("COMPLETE CHALLENGE")
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        ) {
                            Text("Generate New") // Placeholder
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreakCounter(
    targetValue: Int,
    animate: Boolean,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight
) {
    val startValue = (targetValue - 1).coerceAtLeast(0)
    // Always create Animatable. Note: we snap inside LaunchedEffect based on animate flag.
    val count = remember { androidx.compose.animation.core.Animatable(startValue.toFloat()) }
    val rotationX = remember { androidx.compose.animation.core.Animatable(0f) }
    val context = LocalContext.current

    // Handle Animation Trigger
    LaunchedEffect(animate, targetValue) {
        if (animate) {
            count.snapTo(startValue.toFloat())
            rotationX.snapTo(0f)
            
            // Launch parallel animations
            launch {
                count.animateTo(
                    targetValue = targetValue.toFloat(),
                    animationSpec = tween(durationMillis = 2000, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            }
            launch {
                 rotationX.animateTo(
                    targetValue = 360f,
                    animationSpec = tween(durationMillis = 2000, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                )
            }
        } else {
            count.snapTo(targetValue.toFloat())
            rotationX.snapTo(0f)
        }
    }

    // Handle Haptics safely using snapshotFlow
    LaunchedEffect(count) {
        var lastEmittedInt = startValue
        snapshotFlow { count.value.toInt() }
            .collectLatest { currentInt ->
                if (currentInt != lastEmittedInt && animate) {
                    lastEmittedInt = currentInt
                    com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                        context, 
                        com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK
                    )
                }
            }
    }

    Text(
        text = "${count.value.toInt()} 🔥",
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = Modifier.graphicsLayer {
            this.rotationX = rotationX.value
            this.cameraDistance = 12f * density
        }
    )
}

// END OF FILE - KnowledgeHub koda je preseljena v KnowledgeHubScreen.kt
