package com.example.myapplication.ui.screens
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
@Composable
fun ProSubscriptionScreen(
    onBack: () -> Unit = {},
    onSubscribed: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize().background(Color(0xFFF1F6FF)), contentAlignment = Alignment.Center) {
        Card(modifier = Modifier.fillMaxWidth(0.95f).padding(16.dp), elevation = CardDefaults.cardElevation(8.dp)) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    repeat(3) { Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(32.dp).padding(horizontal = 6.dp)) }
                }
                Spacer(Modifier.height(12.dp))
                Text("Nadgradi na PRO", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                Text("Odkleni napredne AI funkcije:\n* Personalizirani AI trener\n* Premium workout generator\n* Napredno sledenje napredku in se vec!", style = MaterialTheme.typography.bodyLarge, fontSize = 18.sp)
                Spacer(Modifier.height(20.dp))
                Text("Samo 4,99 EUR / mesec", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { onSubscribed() }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("NADGRADI NA PRO")
                }
                TextButton(onClick = onBack, modifier = Modifier.padding(top = 10.dp)) { Text("Nazaj") }
            }
        }
    }
}