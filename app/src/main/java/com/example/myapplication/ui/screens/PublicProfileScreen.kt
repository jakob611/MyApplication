package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.PublicProfile
import com.example.myapplication.persistence.FollowStore
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    profile: PublicProfile,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentUserId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: ""
    var isFollowing by remember { mutableStateOf(false) }
    var followerCount by remember { mutableIntStateOf(profile.followers ?: 0) }
    var isLoading by remember { mutableStateOf(false) }

    // Check if currently following
    LaunchedEffect(profile.userId) {
        isFollowing = FollowStore.isFollowing(currentUserId, profile.userId)
    }

    val accentGold = Color(0xFFFFD700)
    val accentBlue = Color(0xFF2563EB)
    val accentGreen = Color(0xFF13EF92)
    val darkBg = Color(0xFF17223B)
    val cardBg = Color(0xFF25304A)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = accentBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(darkBg, Color(0xFF1A2435), Color(0xFF25304A))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar placeholder
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(accentBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                profile.username.take(2).uppercase(),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            profile.username,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        profile.displayName?.let {
                            if (it.isNotBlank()) {
                                Text(
                                    it,
                                    fontSize = 16.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        // Follow/Unfollow Button ali "Your Profile" indicator
                        if (profile.userId == currentUserId) {
                            // Če je tvoj profil, prikaži indicator namesto gumba
                            Card(
                                modifier = Modifier.fillMaxWidth(0.8f),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF374151)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = accentGreen
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "This is your profile",
                                        fontWeight = FontWeight.Bold,
                                        color = accentGreen
                                    )
                                }
                            }
                        } else {
                            // Če je tuj profil, prikaži Follow/Unfollow gumb
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        val success = if (isFollowing) {
                                            FollowStore.unfollowUser(currentUserId, profile.userId)
                                        } else {
                                            FollowStore.followUser(currentUserId, profile.userId)
                                        }

                                        if (success) {
                                            isFollowing = !isFollowing
                                            followerCount += if (isFollowing) 1 else -1
                                        }
                                        isLoading = false
                                    }
                                },
                                enabled = !isLoading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isFollowing) Color.Gray else accentBlue
                                ),
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        if (isFollowing) Icons.Filled.PersonRemove else Icons.Filled.PersonAdd,
                                        contentDescription = null
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isFollowing) "Unfollow" else "Follow",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Stats
                        if (profile.followers != null || profile.following != null) {
                            Spacer(Modifier.height(24.dp))
                            HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                            Spacer(Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                profile.followers?.let {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "$followerCount",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentGreen
                                        )
                                        Text(
                                            "Followers",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                profile.following?.let {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "${it}",
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = accentBlue
                                        )
                                        Text(
                                            "Following",
                                            fontSize = 12.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Level (if public)
                profile.level?.let { level ->
                    Spacer(Modifier.height(16.dp))
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
                                "LEVEL",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Text(
                                "$level",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentGold
                            )
                        }
                    }
                }

                // Badges (if public)
                profile.badges?.let { badges ->
                    if (badges.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
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
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(3),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.heightIn(max = 400.dp)
                                ) {
                                    items(badges) { badge ->
                                        BadgeCardSimple(badge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeCardSimple(badge: com.example.myapplication.data.Badge) {
    Card(
        modifier = Modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2435)),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFFD700))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = getBadgeIconSimple(badge.iconName),
                contentDescription = badge.name,
                tint = Color(0xFFFFD700),
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                badge.name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

private fun getBadgeIconSimple(iconName: String): androidx.compose.ui.graphics.vector.ImageVector {
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
