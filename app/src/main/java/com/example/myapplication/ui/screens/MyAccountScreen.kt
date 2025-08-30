package com.example.myapplication.ui.screens
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.*

@Composable
fun MyAccountScreen(userEmail: String) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("My Account", style = MaterialTheme.typography.titleLarge)
        Text("\nEmail: $userEmail")
        // Dodaj še ostalo po želji
    }
}