package com.example.myapplication.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyModuleHomeScreen(
    onBack: () -> Unit,
    onStartPlan: () -> Unit,
    onStartWorkout: (PlanResult?) -> Unit,
    onStartAdditionalWorkout: () -> Unit = {},
    currentPlan: PlanResult?,
    onOpenHistory: () -> Unit,
    onOpenManualLog: () -> Unit,
    onStartRun: () -> Unit = {},
    onOpenActivityLog: () -> Unit = {},
    onOpenShop: () -> Unit = {}
) {
    val context = LocalContext.current
    val vm: BodyModuleHomeViewModel = viewModel(
        factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as android.app.Application
        )
    )
    val ui by vm.ui.collectAsState()

    // Ob vstopu brez plana → takoj preusmeri na "No plans yet" (BodyOverview)
    LaunchedEffect(currentPlan) {
        if (currentPlan == null) {
            onStartPlan()
        }
    }

    // Ob vsakem prikazu zaslona osveži stats (weekly_target, streak itd.)
    LaunchedEffect(Unit) {
        vm.refreshStats()
    }

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
                    .clip(RoundedCornerShape(5.dp)) // Lepši zaobljeni robovi
            )
            Spacer(Modifier.height(16.dp))

            // Rest Activity Card (only shows on rest days)
            if (ui.todayIsRest && !ui.isWorkoutDoneToday) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF673AB7)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).clickable {
                        com.example.myapplication.utils.HapticFeedback.performHapticFeedback(context, com.example.myapplication.utils.HapticFeedback.FeedbackType.SUCCESS)
                        vm.completeRestDayActivity()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🧘", fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Rest Day: Mobility & Stretching", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Take 5 mins to stretch +20 XP", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        }
                    }
                }
            }

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
                        
                        StreakCounter(
                            targetValue = ui.streakDays,
                            animate = ui.showCompletionAnimation,
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
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Start run", color = Color.White, fontSize = 16.sp) }

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
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                ) {
                    Text("🗺️", fontSize = 22.sp)
                }

                // 🛒 Gumb za Shop
                IconButton(
                    onClick = {
                        com.example.myapplication.utils.HapticFeedback.performHapticFeedback(
                            context,
                            com.example.myapplication.utils.HapticFeedback.FeedbackType.LIGHT_CLICK
                        )
                        onOpenShop()
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFC107).copy(alpha = 0.15f))
                ) {
                    Text("🛒", fontSize = 22.sp)
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
