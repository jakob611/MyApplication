package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Badge data class
 */
private data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val requirement: String
)

/**
 * Vsi razpoložljivi badges v aplikaciji
 */
private val allBadges = listOf(
    Badge(
        id = "first_workout",
        name = "First Steps",
        description = "Complete your first workout",
        emoji = "🏃",
        requirement = "Complete 1 workout"
    ),
    Badge(
        id = "workout_5",
        name = "Getting Started",
        description = "Complete 5 workouts",
        emoji = "💪",
        requirement = "Complete 5 workouts"
    ),
    Badge(
        id = "workout_10",
        name = "Dedicated",
        description = "Complete 10 workouts",
        emoji = "🔥",
        requirement = "Complete 10 workouts"
    ),
    Badge(
        id = "workout_25",
        name = "Committed",
        description = "Complete 25 workouts",
        emoji = "⚡",
        requirement = "Complete 25 workouts"
    ),
    Badge(
        id = "workout_50",
        name = "Unstoppable",
        description = "Complete 50 workouts",
        emoji = "🚀",
        requirement = "Complete 50 workouts"
    ),
    Badge(
        id = "workout_100",
        name = "Century Club",
        description = "Complete 100 workouts",
        emoji = "💯",
        requirement = "Complete 100 workouts"
    ),
    Badge(
        id = "streak_7",
        name = "Weekly Warrior",
        description = "Maintain 7-day streak",
        emoji = "📅",
        requirement = "7-day workout streak"
    ),
    Badge(
        id = "streak_30",
        name = "Monthly Master",
        description = "Maintain 30-day streak",
        emoji = "🎯",
        requirement = "30-day workout streak"
    ),
    Badge(
        id = "streak_100",
        name = "Century Streak",
        description = "Maintain 100-day streak",
        emoji = "👑",
        requirement = "100-day workout streak"
    ),
    Badge(
        id = "level_5",
        name = "Rising Star",
        description = "Reach Level 5",
        emoji = "⭐",
        requirement = "Reach Level 5"
    ),
    Badge(
        id = "level_10",
        name = "Expert",
        description = "Reach Level 10",
        emoji = "🌟",
        requirement = "Reach Level 10"
    ),
    Badge(
        id = "level_20",
        name = "Master",
        description = "Reach Level 20",
        emoji = "✨",
        requirement = "Reach Level 20"
    ),
    Badge(
        id = "nutrition_perfect",
        name = "Nutrition Pro",
        description = "Perfect nutrition week",
        emoji = "🥗",
        requirement = "Within calorie goal 7 days straight"
    ),
    Badge(
        id = "weight_tracker",
        name = "Weight Watcher",
        description = "Log weight 30 times",
        emoji = "⚖️",
        requirement = "Log weight 30 times"
    ),
    Badge(
        id = "early_bird",
        name = "Early Bird",
        description = "Complete workout before 8 AM",
        emoji = "🌅",
        requirement = "Workout before 8 AM"
    ),
    Badge(
        id = "night_owl",
        name = "Night Owl",
        description = "Complete workout after 10 PM",
        emoji = "🦉",
        requirement = "Workout after 10 PM"
    ),
    Badge(
        id = "runner",
        name = "Runner",
        description = "Complete 10 runs",
        emoji = "🏃‍♂️",
        requirement = "Complete 10 runs"
    ),
    Badge(
        id = "marathon",
        name = "Marathon Ready",
        description = "Run total of 42 km",
        emoji = "🏅",
        requirement = "Run 42km total"
    ),
    Badge(
        id = "social",
        name = "Social Butterfly",
        description = "Follow 10 users",
        emoji = "🦋",
        requirement = "Follow 10 users"
    ),
    Badge(
        id = "golden_ratio",
        name = "Golden Face",
        description = "Complete facial analysis",
        emoji = "😊",
        requirement = "Complete Golden Ratio analysis"
    )
)

/**
 * BadgesScreenContent prikazuje vse badges - pridobljene in zaklenjene
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BadgesScreenContent(
    userProfile: com.example.myapplication.data.UserProfile,
    onBack: () -> Unit
) {
    val PrimaryBlue = Color(0xFF2563EB)
    val GreenSuccess = Color(0xFF10B981)
    val GrayLocked = Color(0xFF9CA3AF)

    val earnedBadges = userProfile.badges
    val earnedCount = earnedBadges.size
    val totalCount = allBadges.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Badges", fontWeight = FontWeight.Bold) },
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
        ) {
            // Header stats
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = PrimaryBlue.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "🏆",
                        fontSize = 48.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "$earnedCount / $totalCount",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryBlue
                    )
                    Text(
                        "Badges Earned",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { earnedCount.toFloat() / totalCount.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = GreenSuccess,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${((earnedCount.toFloat() / totalCount.toFloat()) * 100).toInt()}% Complete",
                        fontSize = 12.sp,
                        color = GreenSuccess,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Badges grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(allBadges) { badge ->
                    val isEarned = earnedBadges.contains(badge.id)
                    BadgeCard(
                        badge = badge,
                        isEarned = isEarned,
                        greenSuccess = GreenSuccess,
                        grayLocked = GrayLocked
                    )
                }
            }
        }
    }
}

/**
 * Single badge card
 */
@Composable
private fun BadgeCard(
    badge: Badge,
    isEarned: Boolean,
    greenSuccess: Color,
    grayLocked: Color
) {
    val cardColor = if (isEarned) {
        greenSuccess.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentAlpha = if (isEarned) 1f else 0.4f

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Badge icon/emoji
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = if (isEarned) greenSuccess else grayLocked,
                        shape = CircleShape
                    )
            ) {
                if (isEarned) {
                    Text(
                        badge.emoji,
                        fontSize = 32.sp
                    )
                } else {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Badge name
            Text(
                badge.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                textAlign = TextAlign.Center,
                maxLines = 2
            )

            Spacer(Modifier.height(4.dp))

            // Badge description
            Text(
                if (isEarned) badge.description else badge.requirement,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha * 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 3
            )

            if (isEarned) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "✓",
                    fontSize = 16.sp,
                    color = greenSuccess,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
