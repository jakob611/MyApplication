package com.example.myapplication.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.data.*
import com.example.myapplication.persistence.FollowStore
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AchievementsScreen(
    userProfile: UserProfile,
    activePlan: PlanResult? = null,
    currentPlanDay: Int = 1,
    weeklyDone: Int = 0,
    onRefresh: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val accentGold = Color(0xFFFFD700)
    val accentBlue = Color(0xFF2563EB)
    val accentGreen = Color(0xFF13EF92)
    val darkBg = Color(0xFF17223B)
    val cardBg = Color(0xFF25304A)

    val currentLevel = userProfile.level
    val totalXP = userProfile.xp
    val currentLevelXP = totalXP - userProfile.xpForCurrentLevel
    val xpNeededForLevel = userProfile.xpForNextLevel - userProfile.xpForCurrentLevel
    val progressPercentage = userProfile.progressToNextLevel

    var showAllBadges by remember { mutableStateOf(false) }
    var selectedBadge by remember { mutableStateOf<Badge?>(null) }

    // Followers/Following dialog state
    var showFollowersDialog by remember { mutableStateOf(false) }
    var showFollowingDialog by remember { mutableStateOf(false) }
    var followersList by remember { mutableStateOf<List<FollowUserInfo>>(emptyList()) }
    var followingList by remember { mutableStateOf<List<FollowUserInfo>>(emptyList()) }
    var isLoadingFollowList by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentUserId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: ""

    // Convert badge IDs to Badge objects with LIVE progress calculation
    val allBadges = BadgeDefinitions.ALL_BADGES
    val badgesWithStatus = allBadges.map { badge ->
        val progress = com.example.myapplication.persistence.AchievementStore.getBadgeProgress(badge.id, userProfile)
        val req = getBadgeRequirement(badge)
        // Badge is unlocked if: it's in the saved badges list (Firestore) OR progress meets threshold
        val isUnlocked = userProfile.badges.contains(badge.id) || progress >= req
        badge.copy(
            unlocked = isUnlocked,
            progress = progress,
            requirement = req
        )
    }

    // Sort: unlocked first (most recent progress first), then locked sorted by closest to completion
    val sortedBadges = badgesWithStatus.sortedWith(
        compareByDescending<Badge> { it.unlocked }
            .thenByDescending { it.progress.toFloat() / it.requirement.coerceAtLeast(1).toFloat() }
    )

    // Show first 6 by default, all when expanded
    val displayedBadges = if (showAllBadges) sortedBadges else sortedBadges.take(6)
    val unlockedCount = badgesWithStatus.count { it.unlocked }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(darkBg, Color(0xFF1A2435), Color(0xFF25304A))
                )
            )
    ) {
        // Handle system back button
        androidx.activity.compose.BackHandler { onBack() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))

            // Back arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Title
            Text(
                "Achievements",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = accentGold
            )

            Spacer(Modifier.height(24.dp))

            // BADGES Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "BADGES",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 4.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "$unlockedCount / ${allBadges.size} unlocked",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )

                    // Badge Grid (3 columns)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.heightIn(max = 600.dp)
                    ) {
                        items(displayedBadges) { badge ->
                            BadgeCard(
                                badge = badge,
                                onClick = { selectedBadge = badge }
                            )
                        }
                    }

                    // More/Less button
                    if (!showAllBadges && sortedBadges.size > 6) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { showAllBadges = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accentGold)
                        ) {
                            Text("More (${sortedBadges.size - 6} more)", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else if (showAllBadges) {
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { showAllBadges = false }
                        ) {
                            Text("Show Less", color = Color.White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // LEVEL PROGRESS Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "LEVEL PATH",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Current Level
                    Text(
                        "Current level",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        "Lvl $currentLevel",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accentGold
                    )

                    Spacer(Modifier.height(16.dp))

                    // XP Progress Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$currentLevelXP/$xpNeededForLevel xp",
                            fontSize = 14.sp,
                            color = accentBlue,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Lvl ${currentLevel + 1}",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = progressPercentage,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = accentBlue,
                        trackColor = Color(0xFF1A2435)
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "${(progressPercentage * 100).toInt()}%",
                        fontSize = 16.sp,
                        color = accentBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // FOLLOWERS Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    isLoadingFollowList = true
                                    val followers = FollowStore.getFollowers(currentUserId)
                                    followersList = followers.mapNotNull { loadUserInfo(it) }
                                    isLoadingFollowList = false
                                    showFollowersDialog = true
                                }
                            }
                        ) {
                            Text(
                                "${userProfile.followers}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentGreen
                            )
                            Text(
                                "Followers",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(50.dp)
                                .background(Color.Gray)
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable {
                                scope.launch {
                                    isLoadingFollowList = true
                                    val following = FollowStore.getFollowing(currentUserId)
                                    followingList = following.mapNotNull { loadUserInfo(it) }
                                    isLoadingFollowList = false
                                    showFollowingDialog = true
                                }
                            }
                        ) {
                            Text(
                                "${userProfile.following}",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentBlue
                            )
                            Text(
                                "Following",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // PLAN PATH Section - same visualizer as PlanPathDialog
            if (activePlan != null) {
                // Read activityLevel from SharedPreferences (synced from Firestore on app startup)
                val bmPrefs = androidx.compose.ui.platform.LocalContext.current
                    .getSharedPreferences("bm_prefs", android.content.Context.MODE_PRIVATE)
                val localActivityDays = bmPrefs.getInt("weekly_target", 0)

                val safeGoal = when {
                    localActivityDays > 0 -> localActivityDays
                    activePlan.trainingDays > 0 -> activePlan.trainingDays
                    else -> 4
                }
                val totalPlanDays = 4 * safeGoal
                val currentWeekGlobal = ((currentPlanDay - 1) / safeGoal) + 1
                val blockStartWeek = ((currentWeekGlobal - 1) / 4) * 4 + 1

                // Read if today's workout is done
                val prefs = androidx.compose.ui.platform.LocalContext.current
                    .getSharedPreferences("bm_prefs", android.content.Context.MODE_PRIVATE)
                val lastWorkoutEpoch = prefs.getLong("last_workout_epoch", 0L)
                val isTodayDone = if (lastWorkoutEpoch == 0L) false
                    else java.time.LocalDate.ofEpochDay(lastWorkoutEpoch) == java.time.LocalDate.now()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "PLAN PATH",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 8.dp),
                            textAlign = TextAlign.Center
                        )

                        Text(
                            activePlan.name,
                            fontSize = 14.sp,
                            color = Color.Gray
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            InfoChip("${activePlan.calories} kcal", accentGold)
                            InfoChip("${safeGoal}x/week", accentBlue)
                            InfoChip("Week $currentWeekGlobal", accentGreen)
                        }

                        Spacer(Modifier.height(16.dp))

                        // Same PlanPathVisualizer from BodyModuleHomeScreen
                        // Wrap in fixed-height Box to avoid nested verticalScroll crash
                        val pathHeight = ((totalPlanDays * 50) + 700).dp
                        Box(modifier = Modifier.fillMaxWidth().height(pathHeight)) {
                            PlanPathVisualizer(
                                currentDayGlobal = currentPlanDay,
                                isTodayDone = isTodayDone,
                                weeklyGoal = safeGoal,
                                totalDays = totalPlanDays,
                                startWeek = blockStartWeek,
                                isDarkMode = true, // Achievements is always dark
                                onNodeClick = { /* read-only in achievements */ },
                                footerContent = {}
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Followers Dialog
    if (showFollowersDialog) {
        FollowListDialog(
            title = "Followers",
            users = followersList,
            isLoading = isLoadingFollowList,
            accentColor = accentGreen,
            onDismiss = { showFollowersDialog = false }
        )
    }

    // Following Dialog
    if (showFollowingDialog) {
        FollowListDialog(
            title = "Following",
            users = followingList,
            isLoading = isLoadingFollowList,
            accentColor = accentBlue,
            onDismiss = { showFollowingDialog = false }
        )
    }

    // Badge Detail Dialog
    selectedBadge?.let { badge ->
        AlertDialog(
            onDismissRequest = { selectedBadge = null },
            icon = {
                Icon(
                    imageVector = getBadgeIcon(badge.iconName),
                    contentDescription = badge.name,
                    tint = if (badge.unlocked) accentGold else Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
            },
            title = {
                Text(
                    badge.name,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        badge.description,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    if (badge.unlocked) {
                        Text(
                            "✓ Unlocked!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentGreen
                        )
                    } else {
                        val progressPercent = ((badge.progress.toFloat() / badge.requirement.toFloat()) * 100).toInt().coerceIn(0, 100)
                        Text(
                            "Progress: ${badge.progress} / ${badge.requirement}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentBlue
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (badge.progress.toFloat() / badge.requirement.toFloat()).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = accentBlue,
                            trackColor = Color.Gray.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$progressPercent% complete",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBadge = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun BadgeCard(badge: Badge, onClick: () -> Unit = {}) {
    val icon = getBadgeIcon(badge.iconName)
    val isUnlocked = badge.unlocked

    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(0.8f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) Color(0xFF1A2435) else Color(0xFF0F1419)
        ),
        border = if (isUnlocked) {
            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFD700))
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = badge.name,
                tint = if (isUnlocked) Color(0xFFFFD700) else Color(0xFF445566),
                modifier = Modifier.size(32.dp)
            )

            Spacer(Modifier.height(6.dp))

            Text(
                badge.name,
                fontSize = 11.sp,
                fontWeight = if (isUnlocked) FontWeight.Bold else FontWeight.Normal,
                color = if (isUnlocked) Color.White else Color(0xFF667788),
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun InfoChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
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

// Helper funkcija za nalaganje info o uporabniku
private suspend fun loadUserInfo(userId: String): FollowUserInfo? {
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


