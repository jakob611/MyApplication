package com.example.myapplication.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

@Composable
fun CommunityScreen(
    onUpgrade: () -> Unit = {},
    onAccept: (CommunityNotification) -> Unit = {}
) {
    val notifications = remember {
        listOf(
            CommunityNotification(
                userName = "nebulanomad",
                time = "5d",
                message = "Shared a post you might like",
                unread = false
            ),
            CommunityNotification(
                userName = "starryskies23",
                time = "1d",
                message = "Started following you",
                unread = true
            ),
            CommunityNotification(
                userName = "nebulanomad",
                time = "5d",
                message = "Shared a post you might like",
                unread = false
            )
        )
    }

    var search by remember { mutableStateOf("") }

    Surface(color = Color(0xFFF3F4F6)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = Color.White)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Search
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                        placeholder = { Text("Friends...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )

                    // List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(
                            items = notifications.filter {
                                search.isBlank() ||
                                        it.userName.contains(search, ignoreCase = true) ||
                                        it.message.contains(search, ignoreCase = true)
                            }
                        ) { n ->
                            NotificationRow(
                                notification = n,
                                onAccept = { onAccept(n) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: CommunityNotification,
    onAccept: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(42.dp)) {
            // Avatar
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(color = Color(0xFFD1D5DB))
                    .border(1.dp, Color(0xFFE5E7EB), CircleShape)
            )
            if (notification.unread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color = Color(0xFFE11D48)) // rdeča pikica
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = notification.userName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color(0xFF111827)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = notification.time,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280)
                )
            }
            Text(
                text = notification.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280)
            )
        }

        Spacer(Modifier.width(12.dp))

        Button(
            onClick = onAccept,
            shape = RoundedCornerShape(10.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            )
        ) {
            Text("Accept", style = MaterialTheme.typography.labelLarge)
        }
    }
}

// Public, ker se uporablja v CommunityScreen
data class CommunityNotification(
    val userName: String,
    val time: String,
    val message: String,
    val unread: Boolean
)