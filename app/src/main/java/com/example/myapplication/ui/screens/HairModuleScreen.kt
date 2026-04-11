package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.utils.HapticFeedback

@Composable
fun HairModuleScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val PrimaryBlue = MaterialTheme.colorScheme.primary

    var showQuiz by remember { mutableStateOf(false) }
    var hairType by remember { mutableStateOf("Unknown") }

    val dailyActions = remember { mutableStateListOf(
        Pair("Scalp massage (5 min)", false),
        Pair("Apply growth serum", false),
        Pair("Wash with sulfate-free shampoo", false),
        Pair("Hydration goal reached", false)
    ) }

    val completedCount = dailyActions.count { it.second }
    val progress = if (dailyActions.isEmpty()) 0f else completedCount.toFloat() / dailyActions.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Inline header with back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                "Hair Module",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Your Hair Profile", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hair Type: $hairType",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { showQuiz = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text(if (hairType == "Unknown") "Take Hair Quiz" else "Retake Quiz", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }

        ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Daily Routine", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Track your daily actions to build healthy hair habits.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = PrimaryBlue,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text("Routine adherence: ${(progress * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(Modifier.height(16.dp))

                dailyActions.forEachIndexed { index, pair ->
                    ActionCheckItem(
                        text = pair.first,
                        checked = pair.second,
                        onCheckedChange = { isChecked ->
                            dailyActions[index] = pair.copy(second = isChecked)
                            if (isChecked) {
                                HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.SUCCESS)
                            }
                        }
                    )
                }

                if (completedCount == dailyActions.size) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "All done for today! Fantastic job! 🎉",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }

    if (showQuiz) {
        HairQuizDialog(
            onDismiss = { showQuiz = false },
            onFinish = { type ->
                hairType = type
                showQuiz = false
                HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.SUCCESS)
            }
        )
    }
}

@Composable
private fun ActionCheckItem(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (checked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (checked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (checked) FontWeight.Normal else FontWeight.Medium
        )
    }
}

@Composable
fun HairQuizDialog(onDismiss: () -> Unit, onFinish: (String) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var porosity by remember { mutableStateOf("") }
    var density by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                if (step == 0) {
                    Text("Hair Porosity Test", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text("Drop a clean hair strand in a glass of water. What happens after 2-4 minutes?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { porosity = "Low Porosity"; step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("It floats on top") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { porosity = "Medium Porosity"; step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("It sinks slowly") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { porosity = "High Porosity"; step = 1 }, modifier = Modifier.fillMaxWidth()) { Text("It sinks immediately") }
                } else if (step == 1) {
                    Text("Hair Density Test", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text("Tie your hair in a ponytail. How thick is it?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { density = "Fine"; onFinish("$porosity, Fine") }, modifier = Modifier.fillMaxWidth()) { Text("Less than 2 inches / Very thin") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { density = "Medium"; onFinish("$porosity, Medium") }, modifier = Modifier.fillMaxWidth()) { Text("2-3 inches / Normal") }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { density = "Thick"; onFinish("$porosity, Thick") }, modifier = Modifier.fillMaxWidth()) { Text("More than 3 inches / Very thick") }
                }
            }
        }
    }
}
