package com.example.myapplication.ui.screens

import com.example.myapplication.data.PlanResult
import com.example.myapplication.data.WeekPlan
import com.example.myapplication.data.DayPlan
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.data.Badge
import com.example.myapplication.data.BadgeDefinitions
import com.example.myapplication.data.UserProfile
import com.example.myapplication.persistence.FollowStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Združen LevelPathScreen - prikazuje Level, Badges in Plan Path
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LevelPathScreen(
    userProfile: UserProfile,
    activePlan: PlanResult? = null,
    currentPlanDay: Int = 1,
    onBack: () -> Unit
) {
    val PrimaryBlue = MaterialTheme.colorScheme.primary
    val Yellow = MaterialTheme.colorScheme.secondary
    val GreenSuccess = Color(0xFF10B981)
    val GrayLocked = Color(0xFF6B7280)
    val GoldBadge = MaterialTheme.colorScheme.secondary

    var selectedBadge by remember { mutableStateOf<Badge?>(null) }

    // Followers/Following dialog state
    var showFollowersDialog by remember { mutableStateOf(false) }
    var showFollowingDialog by remember { mutableStateOf(false) }
    var followersList by remember { mutableStateOf<List<FollowUserInfo>>(emptyList()) }
    var followingList by remember { mutableStateOf<List<FollowUserInfo>>(emptyList()) }
    var isLoadingFollowList by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentUserId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: ""

    val useCase = com.example.myapplication.domain.gamification.ManageGamificationUseCase(
        com.example.myapplication.data.gamification.FirestoreGamificationRepository()
    )
    val allBadges = BadgeDefinitions.ALL_BADGES

    val badgesWithStatus = allBadges.map { badge ->
        val progress = useCase.getBadgeProgress(badge.id, userProfile)
        val req = badge.requirement
        val isUnlocked = userProfile.badges.contains(badge.id) || progress >= req
        badge.copy(
            unlocked = isUnlocked,
            progress = progress,
            requirement = req
        )
    }

    // Sortiraj - odklenjeni najprej
    val sortedBadges = badgesWithStatus.sortedWith(
        compareByDescending<Badge> { it.unlocked }
            .thenByDescending { it.progress.toFloat() / it.requirement.coerceAtLeast(1).toFloat() }
    )
    val unlockedCount = badgesWithStatus.count { it.unlocked }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Progress & Achievements", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ===== LEVEL SECTION =====
            Card(
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("LEVEL", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${userProfile.level}",
                        fontSize = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryBlue
                    )
                    Text(
                        "⭐ ${userProfile.xp} Total XP",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Yellow
                    )
                    Spacer(Modifier.height(16.dp))

                    // Progress bar
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Progress to Level ${userProfile.level + 1}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${(userProfile.progressToNextLevel * 100).toInt()}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { userProfile.progressToNextLevel },
                            modifier = Modifier.fillMaxWidth().height(12.dp),
                            color = PrimaryBlue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${userProfile.xp - userProfile.xpForCurrentLevel} / ${userProfile.xpForNextLevel - userProfile.xpForCurrentLevel} XP",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    // Followers/Following row
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Followers - klikabilno
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    isLoadingFollowList = true
                                    val followers = FollowStore.getFollowers(currentUserId)
                                    followersList = followers.mapNotNull { loadFollowUserInfo(it) }
                                    isLoadingFollowList = false
                                    showFollowersDialog = true
                                }
                            }
                        ) {
                            Text(
                                "${userProfile.followers}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = GreenSuccess
                            )
                            Text("Followers", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        )

                        // Following - klikabilno
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    isLoadingFollowList = true
                                    val following = FollowStore.getFollowing(currentUserId)
                                    followingList = following.mapNotNull { loadFollowUserInfo(it) }
                                    isLoadingFollowList = false
                                    showFollowingDialog = true
                                }
                            }
                        ) {
                            Text(
                                "${userProfile.following}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                            Text("Following", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ===== BADGES SECTION =====
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("BADGES", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("$unlockedCount / ${allBadges.size}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Badge grid (6 columns, compact)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.height(220.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = sortedBadges, key = { it.id }) { badge ->
                            BadgeItem(
                                badge = badge,
                                goldColor = GoldBadge,
                                grayColor = GrayLocked,
                                onClick = { selectedBadge = badge }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ===== PLAN PATH SECTION =====
            if (activePlan != null) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val useCase = com.example.myapplication.domain.gamification.ManageGamificationUseCase(
                    repository = com.example.myapplication.data.gamification.FirestoreGamificationRepository(),
                    workoutDoneProvider = { com.example.myapplication.data.settings.UserPreferencesRepository(context).isWorkoutDoneToday() },
                    weeklyTargetProvider = { com.example.myapplication.data.settings.UserPreferencesRepository(context).getWeeklyTargetFlow() }
                )
                val gamificationState by useCase.getGamificationStateFlow().collectAsState(
                    initial = com.example.myapplication.domain.gamification.GamificationState(),
                    context = kotlin.coroutines.EmptyCoroutineContext
                )

                val safeGoal = when {
                    gamificationState.weeklyTarget > 0 -> gamificationState.weeklyTarget
                    activePlan.trainingDays > 0 -> activePlan.trainingDays
                    else -> 4
                }

                val isTodayDone = gamificationState.workoutDoneToday

                // Plan: 4 tedne × 7 dni = 28 (vključno z rest dnevi)
                val totalDays = 28

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("PLAN PATH", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(
                            activePlan.name,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))

                        // Plan: 4 tedne × 7 dni = 28 (vključno z rest dnevi)
                        val totalDays = 28

                        val pathHeight = ((totalDays * 80) + 200).dp
                        Box(modifier = Modifier.fillMaxWidth().height(pathHeight)) {
                            PlanPathVisualizer(
                                currentDayGlobal = currentPlanDay,
                                isTodayDone = isTodayDone,
                                weeklyGoal = safeGoal,
                                totalDays = totalDays,
                                startWeek = 1,
                                isDarkMode = MaterialTheme.colorScheme.background.luminance() < 0.5f,
                                planWeeks = activePlan.weeks,
                                onNodeClick = { /* read-only */ },
                                footerContent = {}
                            )
                        }
                    }
                }
            } else {
                // No active plan
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No Active Plan",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Create a workout plan to track your progress",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ===== XP INFO CARD =====
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("💡 How to Earn XP", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("• Complete workouts: +50 XP", fontSize = 14.sp)
                    Text("• Log weight: +10 XP", fontSize = 14.sp)
                    Text("• Maintain streak: +5 XP/day", fontSize = 14.sp)
                    Text("• Track runs: +20 XP", fontSize = 14.sp)
                }
            }
        }
    }

    // Badge detail dialog
    selectedBadge?.let { badge ->
        AlertDialog(
            onDismissRequest = { selectedBadge = null },
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            if (badge.unlocked) GoldBadge else GrayLocked,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getBadgeIcon(badge.iconName),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            },
            title = {
                Text(badge.name, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(badge.description, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    if (!badge.unlocked) {
                        Text(
                            "Progress: ${badge.progress}/${badge.requirement}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                        LinearProgressIndicator(
                            progress = { (badge.progress.toFloat() / badge.requirement.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(8.dp),
                            color = PrimaryBlue,
                            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    } else {
                        Text("✓ Unlocked", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GreenSuccess)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBadge = null }) { Text("Close") }
            }
        )
    }

    // Followers Dialog
    if (showFollowersDialog) {
        FollowListDialog(
            title = "Followers",
            users = followersList,
            isLoading = isLoadingFollowList,
            accentColor = GreenSuccess,
            onDismiss = { showFollowersDialog = false }
        )
    }

    // Following Dialog
    if (showFollowingDialog) {
        FollowListDialog(
            title = "Following",
            users = followingList,
            isLoading = isLoadingFollowList,
            accentColor = PrimaryBlue,
            onDismiss = { showFollowingDialog = false }
        )
    }
}

@Composable
private fun BadgeItem(
    badge: Badge,
    goldColor: Color,
    grayColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (badge.unlocked) goldColor.copy(alpha = 0.2f) else grayColor.copy(alpha = 0.1f))
            .then(
                if (badge.unlocked) Modifier.border(2.dp, goldColor, CircleShape)
                else Modifier
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = getBadgeIcon(badge.iconName),
                contentDescription = badge.name,
                tint = if (badge.unlocked) goldColor else grayColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}


private fun getBadgeIcon(iconName: String): ImageVector {
    return when (iconName) {
        "EmojiEvents" -> Icons.Filled.EmojiEvents
        "FitnessCenter" -> Icons.Filled.FitnessCenter
        "LocalFireDepartment" -> Icons.Filled.LocalFireDepartment
        "Star" -> Icons.Filled.Star
        "Person" -> Icons.Filled.Person
        "Group" -> Icons.Filled.Group
        "WbSunny" -> Icons.Filled.WbSunny
        "NightsStay" -> Icons.Filled.NightsStay
        "CalendarToday" -> Icons.Filled.CalendarToday
        else -> Icons.Filled.EmojiEvents
    }
}

// Data class za prikaz uporabnika v follow listi
data class FollowUserInfo(
    val uid: String,
    val username: String,
    val displayName: String? = null
)

// Helper funkcija za nalaganje info o uporabniku
private suspend fun loadFollowUserInfo(userId: String): FollowUserInfo? {
    return try {
        val doc = com.example.myapplication.persistence.FirestoreHelper.getUserRef(userId)
            .get()
            .await()

        if (!doc.exists()) return null

        val usernameStr = doc.getString("username")
        val firstNameStr = doc.getString("first_name")
        val username = usernameStr?.takeIf { it.isNotBlank() } 
            ?: firstNameStr?.takeIf { it.isNotBlank() } 
            ?: userId.take(8)
        val displayName = firstNameStr?.takeIf { it.isNotBlank() }

        FollowUserInfo(
            uid = userId,
            username = username,
            displayName = displayName
        )
    } catch (_: Exception) {
        null
    }
}

// Dialog za prikaz followers/following liste
@Composable
fun FollowListDialog(
    title: String,
    users: List<FollowUserInfo>,
    isLoading: Boolean,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 400.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accentColor)
                    }
                } else if (users.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No users yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = users, key = { it.uid }) { user ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF374151), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Avatar
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(accentColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        user.username.take(2).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column {
                                    Text(
                                        user.username,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    user.displayName?.let {
                                        Text(
                                            it,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Close")
                }
            }
        }
    }
}


