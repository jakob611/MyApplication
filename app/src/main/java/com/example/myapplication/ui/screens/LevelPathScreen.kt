package com.example.myapplication.ui.screens

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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
    val PrimaryBlue = Color(0xFF2563EB)
    val Yellow = Color(0xFFFEE440)
    val GreenSuccess = Color(0xFF10B981)
    val GrayLocked = Color(0xFF6B7280)
    val GoldBadge = Color(0xFFFFD700)

    var selectedBadge by remember { mutableStateOf<Badge?>(null) }

    // Followers/Following dialog state
    var showFollowersDialog by remember { mutableStateOf(false) }
    var showFollowingDialog by remember { mutableStateOf(false) }
    var followersList by remember { mutableStateOf<List<FollowUserInfo>>(emptyList()) }
    var followingList by remember { mutableStateOf<List<FollowUserInfo>>(emptyList()) }
    var isLoadingFollowList by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentUserId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: ""

    // Pridobi vse badge-e in jih pravilno označi kot odklenjene
    val allBadges = BadgeDefinitions.ALL_BADGES
    val unlockedBadgeIds = userProfile.badges.toSet()

    // Dodaj tudi avtomatsko odklenjevanje glede na napredek
    val autoUnlockedIds = mutableSetOf<String>()

    // Check workout badges
    if (userProfile.totalWorkoutsCompleted >= 1) autoUnlockedIds.add("first_workout")
    if (userProfile.totalWorkoutsCompleted >= 10) autoUnlockedIds.add("committed_10")
    if (userProfile.totalWorkoutsCompleted >= 50) autoUnlockedIds.add("committed_50")
    if (userProfile.totalWorkoutsCompleted >= 100) autoUnlockedIds.add("committed_100")

    // Check level badges
    if (userProfile.level >= 5) autoUnlockedIds.add("level_5")
    if (userProfile.level >= 10) autoUnlockedIds.add("level_10")
    if (userProfile.level >= 25) autoUnlockedIds.add("level_25")

    // Check follower badges
    if (userProfile.followers >= 1) autoUnlockedIds.add("first_follower")
    if (userProfile.followers >= 10) autoUnlockedIds.add("social_butterfly")
    if (userProfile.followers >= 50) autoUnlockedIds.add("influencer")

    // Check streak badges
    if (userProfile.currentLoginStreak >= 7) autoUnlockedIds.add("week_warrior")
    if (userProfile.currentLoginStreak >= 30) autoUnlockedIds.add("month_master")

    // Check early bird / night owl
    if (userProfile.earlyBirdWorkouts >= 5) autoUnlockedIds.add("early_bird")
    if (userProfile.nightOwlWorkouts >= 5) autoUnlockedIds.add("night_owl")

    // Check plans
    if (userProfile.totalPlansCreated >= 1) autoUnlockedIds.add("first_plan")
    if (userProfile.totalPlansCreated >= 5) autoUnlockedIds.add("plan_master")

    val allUnlockedIds = unlockedBadgeIds + autoUnlockedIds

    val badgesWithStatus = allBadges.map { badge ->
        badge.copy(
            unlocked = allUnlockedIds.contains(badge.id),
            progress = calculateBadgeProgress(badge, userProfile),
            requirement = getBadgeRequirement(badge)
        )
    }

    // Sortiraj - odklenjeni najprej
    val sortedBadges = badgesWithStatus.sortedByDescending { it.unlocked }
    val unlockedCount = sortedBadges.count { it.unlocked }

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
                    Text("LEVEL", fontSize = 14.sp, color = Color.Gray)
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
                            Text("Progress to Level ${userProfile.level + 1}", fontSize = 12.sp, color = Color.Gray)
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
                            color = Color.Gray,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    // Followers/Following row
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
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
                            Text("Followers", fontSize = 12.sp, color = Color.Gray)
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(Color.Gray.copy(alpha = 0.3f))
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
                            Text("Following", fontSize = 12.sp, color = Color.Gray)
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
                        Text("$unlockedCount / ${allBadges.size}", fontSize = 14.sp, color = Color.Gray)
                    }

                    Spacer(Modifier.height(16.dp))

                    // Badge grid (6 columns, compact)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.height(220.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sortedBadges) { badge ->
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
                            color = Color.Gray
                        )

                        Spacer(Modifier.height(16.dp))

                        // Read activityLevel from SharedPreferences (synced from Firestore on app startup)
                        val bmPrefs = androidx.compose.ui.platform.LocalContext.current
                            .getSharedPreferences("bm_prefs", android.content.Context.MODE_PRIVATE)
                        val localActivityDays = bmPrefs.getInt("weekly_target", 0)

                        // Priority: SharedPrefs weekly_target > plan.trainingDays > default 4
                        val trainingDaysPerWeek = when {
                            localActivityDays > 0 -> localActivityDays
                            activePlan.trainingDays > 0 -> activePlan.trainingDays
                            else -> 4
                        }
                        val totalWeeks = 4
                        val totalDays = trainingDaysPerWeek * totalWeeks

                        PlanPathVisualization(
                            currentDay = currentPlanDay,
                            totalDays = totalDays,
                            daysPerWeek = trainingDaysPerWeek,
                            primaryColor = PrimaryBlue,
                            successColor = GreenSuccess,
                            lockedColor = GrayLocked
                        )
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
                            tint = Color.Gray
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
                            color = Color.Gray,
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
                            trackColor = Color.Gray.copy(alpha = 0.3f)
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

@Composable
private fun PlanPathVisualization(
    currentDay: Int,
    totalDays: Int,
    daysPerWeek: Int,
    primaryColor: Color,
    successColor: Color,
    lockedColor: Color
) {
    val totalWeeks = 4

    Column(modifier = Modifier.fillMaxWidth()) {
        // Progress info
        Text(
            "Day $currentDay of $totalDays",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = primaryColor
        )

        Spacer(Modifier.height(8.dp))

        // Overall progress bar
        LinearProgressIndicator(
            progress = { (currentDay.toFloat() / totalDays).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = successColor,
            trackColor = lockedColor.copy(alpha = 0.3f)
        )

        Spacer(Modifier.height(16.dp))

        // Weekly breakdown
        for (week in 1..totalWeeks) {
            val weekStartDay = (week - 1) * daysPerWeek + 1
            val weekEndDay = week * daysPerWeek
            val weekProgress = when {
                currentDay > weekEndDay -> 1f
                currentDay >= weekStartDay -> ((currentDay - weekStartDay + 1).toFloat() / daysPerWeek).coerceIn(0f, 1f)
                else -> 0f
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Week $week",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(60.dp)
                )

                // Day indicators
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (day in weekStartDay..weekEndDay) {
                        val isDone = day < currentDay
                        val isCurrent = day == currentDay

                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isDone -> successColor
                                        isCurrent -> primaryColor
                                        else -> lockedColor.copy(alpha = 0.3f)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isDone) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    "${day}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) Color.White else Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            if (week < totalWeeks) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = Color.Gray.copy(alpha = 0.2f)
                )
            }
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

private fun calculateBadgeProgress(badge: Badge, userProfile: UserProfile): Int {
    return when (badge.id) {
        "first_workout", "committed_10", "committed_50", "committed_100" ->
            userProfile.totalWorkoutsCompleted
        "calorie_crusher_1k", "calorie_crusher_5k", "calorie_crusher_10k" ->
            userProfile.totalCaloriesBurned.toInt()
        "level_5", "level_10", "level_25" ->
            userProfile.level
        "first_follower", "social_butterfly", "influencer" ->
            userProfile.followers
        "early_bird" -> userProfile.earlyBirdWorkouts
        "night_owl" -> userProfile.nightOwlWorkouts
        "week_warrior", "month_master", "year_champion" ->
            userProfile.currentLoginStreak
        "first_plan", "plan_master" ->
            userProfile.totalPlansCreated
        else -> 0
    }
}

private fun getBadgeRequirement(badge: Badge): Int {
    return when (badge.id) {
        "first_workout" -> 1
        "committed_10" -> 10
        "committed_50" -> 50
        "committed_100" -> 100
        "calorie_crusher_1k" -> 1000
        "calorie_crusher_5k" -> 5000
        "calorie_crusher_10k" -> 10000
        "level_5" -> 5
        "level_10" -> 10
        "level_25" -> 25
        "first_follower" -> 1
        "social_butterfly" -> 10
        "influencer" -> 50
        "early_bird" -> 5
        "night_owl" -> 5
        "week_warrior" -> 7
        "month_master" -> 30
        "year_champion" -> 365
        "first_plan" -> 1
        "plan_master" -> 5
        else -> 1
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
        val doc = Firebase.firestore.collection("users")
            .document(userId)
            .get()
            .await()

        if (!doc.exists()) return null

        val username = doc.getString("username") ?: doc.getString("first_name") ?: userId.take(8)
        val displayName = doc.getString("first_name")

        FollowUserInfo(
            uid = userId,
            username = username,
            displayName = displayName
        )
    } catch (e: Exception) {
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
            colors = CardDefaults.cardColors(containerColor = Color(0xFF25304A))
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
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(users) { user ->
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
                                            color = Color.Gray,
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

