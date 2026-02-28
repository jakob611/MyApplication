package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.data.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAccountScreen(
    userProfile: UserProfile,
    onNavigateToDevSettings: () -> Unit,
    onDeleteAllData: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: () -> Unit,
    onProfileUpdate: (UserProfile) -> Unit = {}
) {
    val PrimaryBlue = Color(0xFF2563EB)

    var showDeleteDataDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingData by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }

    // Show loading dialog when deleting
    if (isDeletingData) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss while deleting */ },
            title = { Text("Deleting Data...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Please wait while we delete your data.")
                }
            },
            confirmButton = { /* No buttons while loading */ }
        )
    }

    if (isDeletingAccount) {
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss while deleting */ },
            title = { Text("Deleting Account...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Please wait while we delete your account.")
                }
            },
            confirmButton = { /* No buttons while loading */ }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Account", fontWeight = FontWeight.Bold) },
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
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Account Info", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Stored Data (Firestore)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Username: ${userProfile.username.ifBlank { "N/A" }}")
                Text("Email: ${userProfile.email}")
                Text("Name: ${userProfile.firstName} ${userProfile.lastName}")
                Text("Address: ${userProfile.address.ifBlank { "N/A" }}")
                Text("XP: ${userProfile.xp}")
                Text("Level: ${userProfile.level}")
                Text("Weight Unit: ${userProfile.weightUnit}")
                Text("Speed Unit: ${userProfile.speedUnit}")
                Text("Followers: ${userProfile.followers}")
                Text("Equipment: ${userProfile.equipment.joinToString(", ").ifBlank { "None" }}")
            }
        }

        Spacer(Modifier.height(32.dp))


        Button(onClick = onNavigateToDevSettings, modifier = Modifier.fillMaxWidth()) {
            Text("DEV: Algorithm Debug")
        }

        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        Text("Danger Zone", style = MaterialTheme.typography.titleMedium, color = Color.Red)
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { showDeleteDataDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Warning, null)
            Spacer(Modifier.width(8.dp))
            Text("Delete All My Data")
        }
        Text("Deletes all your plans, stats, and profile data, but keeps your account.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { showDeleteAccountDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Warning, null)
            Spacer(Modifier.width(8.dp))
            Text("Delete Account")
        }
         Text("Permanently deletes your account and all associated data.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }

    if (showDeleteDataDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDataDialog = false },
            title = { Text("Delete All Data?") },
            text = { Text("This will permanently remove all your plans, progress, and profile information from our servers. This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDataDialog = false
                        isDeletingData = true
                        onDeleteAllData()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDataDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Delete Account?") },
            text = { Text("Are you sure you want to delete your account? You will lose access immediately and all data will be wiped.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountDialog = false
                        isDeletingAccount = true
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Cancel") }
            }
        )
    }
}