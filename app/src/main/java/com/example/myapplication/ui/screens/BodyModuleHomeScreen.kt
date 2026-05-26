package com.example.myapplication.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape // ADDED
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.viewmodels.BodyModuleHomeViewModel
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.setValue // Add this
import com.example.myapplication.ui.run.EpicCounter
import com.example.myapplication.ui.run.PlanPathDialog
import com.example.myapplication.viewmodels.BodyHomeIntent
import com.example.myapplication.viewmodels.BodyUiEvent
import com.example.myapplication.ui.theme.UppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyModuleHomeScreen(
    onBack: () -> Unit,
    onStartPlan: () -> Unit,
    onStartWorkout: (PlanResult?) -> Unit,
    onStartAdditionalWorkout: () -> Unit = {},
    currentPlan: PlanResult? = null,
    onOpenHistory: () -> Unit = {},
    onOpenManualLog: () -> Unit = {},
    onStartRun: () -> Unit = {},
    onOpenActivityLog: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm: BodyModuleHomeViewModel = viewModel(
        factory = com.example.myapplication.ui.screens.MyViewModelFactory(context.applicationContext)
    )
    // Faza 30.5: collectAsStateWithLifecycle — prihrani Firestore kvoto in baterijo v ozadju
    val ui by vm.ui.collectAsStateWithLifecycle()
    // Faza 38 — Unified UI State: priročni alias za domenski snapshot.
    // Null-safe dostop: metrics?.field ?: default prepreči "Day 1" glitch med loading.
    val metrics = ui.metrics
    // Faza 39 — showCompletionAnimation je ločen StateFlow (ne del BodyUiState).
    val showCompletionAnimation by vm.showCompletionAnimation.collectAsStateWithLifecycle()

    // Faza 33 — BUG-12: SnackbarHostState za Scaffold infrastrukturo
    val snackbarHostState = remember { SnackbarHostState() }

    // Faza 33 — BUG-08 (CRITICAL): Konsumacija uiEvents Channela.
    // Prej so bili vsi ShowSnackbar eventi tiho zavrženi — UI ni bil naročen.
    // LaunchedEffect(Unit) je lifecycle-aware: prekine se ob izhodu iz kompozicije.
    LaunchedEffect(Unit) {
        vm.uiEvents.collect { event ->
            when (event) {
                is BodyUiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(
                    message     = "⚠️ ${event.message}",
                    actionLabel = "Zapri",
                    duration    = SnackbarDuration.Long
                )
                is BodyUiEvent.SaveSuccess  -> snackbarHostState.showSnackbar("✅ Shranjeno")
                is BodyUiEvent.Error        -> snackbarHostState.showSnackbar("❌ ${event.message}")
                // Faza 35 — Auth expiration: seja potekla ali token ni veljaven.
                // Prikaži opozorilo in navigiraj nazaj — MainAppContent bo reaktivno zaznal
                // odjavo ali prazen auth stanje in preusmeril na login.
                is BodyUiEvent.AuthExpired  -> {
                    snackbarHostState.showSnackbar(
                        message     = "⚠️ Seja je potekla. Prijavite se znova.",
                        actionLabel = "OK",
                        duration    = SnackbarDuration.Long
                    )
                    onBack()
                }
            }
        }
    }

    // Faza 23: En sam LaunchedEffect (currentPlan) za LoadMetrics.
    // Faza 33 — BUG-11: Firebase Auth ODSTRANJEN iz Composable-a.
    // ViewModel resolvi email interno prek authStateRepository.observeCurrentUserEmail().first()
    LaunchedEffect(currentPlan) {
        if (currentPlan == null) {
            onStartPlan()
        } else {
            vm.handleIntent(com.example.myapplication.viewmodels.BodyHomeIntent.LoadMetrics(currentPlan))
        }
    }

    // Faza 4b: Toast + HapticFeedback ko se streak poveča (workout ali stretching)
    // Faza 33 — BUG-09: LaunchedEffect(Unit) je lifecycle-aware (prekine se ob izhodu iz kompozicije).
    // flowWithLifecycle ni potreben znotraj LaunchedEffect — Compose ga avtomatično upravlja.
    LaunchedEffect(Unit) {
        vm.streakUpdatedEvent.collect { event ->
            // S24 Ultra natančna vibracija — SUCCESS tip
            com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                context,
                com.example.myapplication.utils.HapticFeedback.FeedbackType.SUCCESS
            )
            val msg = "Daily Goal Met! Streak: ${event.newStreak} days "
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

    // Faza 33 — BUG-12: Scaffold z SnackbarHost infrastrukturo.
    // Nadomesti zunanji Box — Scaffold obvladuje padding in Snackbar prikaz.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = UppColors.Background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { paddingValues ->
    // FIX #2: Eksplicitno UppColors.Background za VSA stanja (loading, error, content).
    // MaterialTheme.colorScheme.background bi utripal med theme init — hardkodirano prepreči glitch.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UppColors.Background)
            .padding(paddingValues)
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
                    text = "${metrics?.weeklyDone ?: 0} / ${metrics?.weeklyTarget ?: 3}",
                    color = buttonBlue,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            val target = (metrics?.weeklyTarget ?: 3).coerceAtLeast(1)
            val rawProgress = ((metrics?.weeklyDone ?: 0).toFloat() / target).coerceIn(0f, 1f)
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

            // Rest Activity Card:
            // FIX #2 + #3: Pogoji — rest dan, raztezanje še ni opravljeno, NISO v loading stanju,
            // in danes NI bil že opravljen redni trening (prepreči Stretching Loop).
            // Ref: ManageGamificationUseCase.restDayInitiated() ima server-side guard za dvojno varnost.
            // Faza 21: UserDayStatus.isDoneToday nadomešča trojno String primerjavo
            if ((metrics?.todayIsRest ?: false) && !ui.isLoading && !(metrics?.todayStatus?.isDoneToday ?: false)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("", fontSize = 28.sp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Rest Day: Mobility & Stretching", color = MaterialTheme.colorScheme.onSecondary, fontWeight = FontWeight.Bold)
                                Text("Take 5 mins to stretch and mark day complete", color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        // FIX: Namenski gumb — EDINI način za streak +1 na rest dnevu (ne Extra Workout!)
                        Button(
                            onClick = {
                                com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                                    context,
                                    com.example.myapplication.utils.HapticFeedback.FeedbackType.SUCCESS
                                )
                                vm.handleIntent(com.example.myapplication.viewmodels.BodyHomeIntent.CompleteRestDay)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSecondary),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("✅ Start Stretching (+10 XP)", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Your plan card
            val targetDay = if (metrics?.isWorkoutDoneToday == true) (if ((metrics.planDay) > 1) metrics.planDay - 1 else 1) else (metrics?.planDay ?: 1)

            Card(
                colors = CardDefaults.cardColors(containerColor = planCardBg),
                shape = MaterialTheme.shapes.large,
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Faza 33 — BUG-06: Dokler Firestore ni vrnil pravih podatkov, prikaži skeleton
                // namesto privzetih vrednosti (planDay=1). Prepreči dual-animation glitch:
                // EpicCounter NE sme animirati s privzetimi vrednostmi ob nalaganju.
                // Faza 39 — metrics != null nadomešča zastareli isDataLoaded boolean.
                if (ui.metrics == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = buttonBlue,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                } else {
                androidx.compose.animation.AnimatedContent(
                    targetState = targetDay to (metrics?.todayIsRest ?: false),
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
                                .background(if (animIsRestDay) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) else UppColors.Orange.copy(alpha = 0.15f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (animIsRestDay) MaterialTheme.colorScheme.secondary else UppColors.Orange)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (animIsRestDay) "TODAY IS A REST DAY" else "TODAY IS A WORKOUT DAY",
                                color = if (animIsRestDay) MaterialTheme.colorScheme.secondary else UppColors.Orange,
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
                                        animate = showCompletionAnimation, // Pass animation flag
                                        onAnimationEnd = { vm.handleIntent(BodyHomeIntent.HideCompletionAnimation) }, // Reset flag
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 34.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    // Animating Checkmark scale
                                    val doneScale = remember { androidx.compose.animation.core.Animatable(0.8f) }
                                    LaunchedEffect(metrics?.isWorkoutDoneToday) {
                                        if (metrics?.isWorkoutDoneToday == true) {
                                            doneScale.animateTo(1.2f, androidx.compose.animation.core.spring(dampingRatio = 0.5f))
                                            doneScale.animateTo(1f)
                                        }
                                    }

                                    Text(
                                        text = if (metrics?.isWorkoutDoneToday == true) "Completed" else "Not yet\ncompleted",
                                        color = if (metrics?.isWorkoutDoneToday == true) UppColors.Orange else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 14.sp,
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.scale(if (metrics?.isWorkoutDoneToday == true) doneScale.value else 1f)
                                    )
                                }
                                
                                StreakCounter(
                                    targetValue = metrics?.streakDays ?: 0,
                                animate = showCompletionAnimation,
                                    color = buttonBlue,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                // Faza 13.3: Streak Freeze prikaži, če ima uporabnik zamrznitve
                                if ((metrics?.streakFreezes ?: 0) > 0) {
                                    Spacer(Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "❄️ × ${metrics?.streakFreezes ?: 0}",
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
                                    // Faza 33 — BUG-13: Onemogoči gumb dokler Firestore ni vrnil
                                    // pravih podatkov. metrics==null pomeni planDay=1 je še
                                    // privzeta vrednost — ne smemo sprožiti transakcije z napačnim dayom.
                                    // Faza 39 — metrics != null nadomešča zastareli isDataLoaded boolean.
                                    enabled = ui.metrics != null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp)
                ) { Text("Start workout", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp) }
                            }
                        }
                    }
                }
                } // konec else (isDataLoaded)
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

                // ️ Gumb za Activity Log
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
                    Text("️", fontSize = 22.sp)
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
                // Faza 39 — challenges je nespremenljiv val na VM (ne del BodyUiState).
                vm.challenges.forEach { ch ->
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
                currentDay = metrics?.planDay ?: 1,
                isTodayDone = metrics?.isWorkoutDoneToday ?: false,
                weeklyGoal = metrics?.weeklyTarget ?: 3,
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
    } // konec Box
    } // konec Scaffold
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
                        color = if (challenge.completed) UppColors.Orange else MaterialTheme.colorScheme.primary,
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
                Text(text = "", fontSize = 48.sp)
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
        text = "${count.value.toInt()} ",
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
