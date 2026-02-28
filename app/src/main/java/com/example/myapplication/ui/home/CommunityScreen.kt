package com.example.myapplication.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

@Composable
fun CommunityScreen(
    onViewProfile: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val currentUserId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: ""
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<com.example.myapplication.data.PublicProfile>>(emptyList()) }
    var topUsers by remember { mutableStateOf<List<com.example.myapplication.data.PublicProfile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    // Load top users on start
    LaunchedEffect(Unit) {
        isSearching = true
        topUsers = com.example.myapplication.persistence.ProfileStore.getTopUsers(10)
            .filter { it.userId != currentUserId } // Filter out own profile
        isSearching = false
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                // Discover Users Content (No tabs)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title
                    Text(
                        "Discover Users",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Search bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (it.isNotBlank()) {
                                scope.launch {
                                    isSearching = true
                                    searchResults = com.example.myapplication.persistence.ProfileStore.searchPublicProfiles(it)
                                    isSearching = false
                                }
                            } else {
                                searchResults = emptyList()
                            }
                        },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                        placeholder = { Text("Search users...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    )

                    if (isSearching) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (searchQuery.isBlank() && topUsers.isNotEmpty()) {
                                item {
                                    Text(
                                        "Top Users",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(topUsers) { profile ->
                                    UserSearchResultCard(
                                        profile = profile,
                                        onClick = { onViewProfile(profile.userId) }
                                    )
                                }
                            } else if (searchResults.isNotEmpty()) {
                                item {
                                    Text(
                                        "Search Results",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                items(searchResults) { profile ->
                                    UserSearchResultCard(
                                        profile = profile,
                                        onClick = { onViewProfile(profile.userId) }
                                    )
                                }
                            } else if (searchQuery.isNotBlank()) {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("No users found", color = Color.Gray)
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
private fun UserSearchResultCard(
    profile: com.example.myapplication.data.PublicProfile,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    profile.username.take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    profile.username,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                profile.level?.let {
                    Text(
                        "Level $it",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            profile.followers?.let {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$it",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF13EF92)
                    )
                    Text(
                        "Followers",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

