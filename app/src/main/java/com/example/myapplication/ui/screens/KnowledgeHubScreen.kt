package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeHubFullScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    androidx.activity.compose.BackHandler { onClose() }

    val items = remember { KNOWLEDGE_ITEMS }
    val filtered = remember(query, items) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) items else items.filter { item ->
            item.searchTokens.any { token -> token.contains(q) }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        "Knowledge hub",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search gear, theory, tips…") }
                )
            }

            if (filtered.isEmpty()) {
                item {
                    Text(
                        "No tips found. Try a different keyword.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(items = filtered, key = { it.title }) { item ->
                    KnowledgeCard(item)
                }
            }
        }
    }
}

@Composable
private fun KnowledgeCard(item: KnowledgeItem) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(item.description, style = MaterialTheme.typography.bodyMedium)
            if (item.suggestedExercises.isNotEmpty()) {
                Text("Try:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                item.suggestedExercises.forEach { ex ->
                    Text("• $ex", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (item.tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.tags.forEach { tag ->
                        AssistChip(onClick = { /* no-op */ }, label = { Text(tag) })
                    }
                }
            }
            Text(
                "Images are fetched online when available.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )
        }
    }
}

internal data class KnowledgeItem(
    val title: String,
    val description: String,
    val suggestedExercises: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val imageUrl: String? = null,
    val searchTokens: List<String> = emptyList()
)

internal val KNOWLEDGE_ITEMS = listOf(
    KnowledgeItem(
        title = "Dumbbell",
        description = "Versatile free weight useful for unilateral strength, stability, and hypertrophy work. Start with weight that keeps form solid.",
        suggestedExercises = listOf("Dumbbell press", "Goblet squat", "Dumbbell row", "Farmer's walk"),
        tags = listOf("equipment", "strength"),
        searchTokens = listOf("dumbbell", "versatile free weight useful for unilateral strength, stability, and hypertrophy work. start with weight that keeps form solid.", "equipment", "strength")
    ),
    KnowledgeItem(
        title = "Kettlebell",
        description = "Great for power and conditioning. Hip-dominant moves teach hinges and explosiveness.",
        suggestedExercises = listOf("Kettlebell swing", "Turkish get-up", "Goblet lunge"),
        tags = listOf("equipment", "conditioning"),
        searchTokens = listOf("kettlebell", "great for power and conditioning. hip-dominant moves teach hinges and explosiveness.", "equipment", "conditioning")
    ),
    KnowledgeItem(
        title = "Workout split basics",
        description = "Plan 2–4 full-body days if busy. Upper/lower splits work well 3–4x weekly. Prioritize compound lifts, sprinkle accessories.",
        suggestedExercises = listOf("Squat", "Bench/Push-up", "Row/Pull-up", "Hinge (DL/hip thrust)", "Carry"),
        tags = listOf("theory", "programming"),
        searchTokens = listOf("workout split basics", "plan 2–4 full-body days if busy. upper/lower splits work well 3–4x weekly. prioritize compound lifts, sprinkle accessories.", "theory", "programming")
    ),
    KnowledgeItem(
        title = "Warm-up blueprint",
        description = "Start with 3–5 min easy cardio, then dynamic mobility for joints you train, finish with 1–2 ramp-up sets of the first lift.",
        suggestedExercises = listOf("Arm circles", "World's greatest stretch", "Glute bridge", "Bodyweight squats"),
        tags = listOf("warmup", "mobility"),
        searchTokens = listOf("warm-up blueprint", "start with 3–5 min easy cardio, then dynamic mobility for joints you train, finish with 1–2 ramp-up sets of the first lift.", "warmup", "mobility")
    ),
    KnowledgeItem(
        title = "Home cardio options",
        description = "Mix steady pacing with intervals. Keep knee-friendly choices if needed.",
        suggestedExercises = listOf("Jog or brisk walk", "Jump rope", "Shadow boxing", "Step-ups"),
        tags = listOf("cardio", "conditioning"),
        searchTokens = listOf("home cardio options", "mix steady pacing with intervals. keep knee-friendly choices if needed.", "cardio", "conditioning")
    )
)

