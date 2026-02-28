package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.DrawerBlue
import kotlinx.coroutines.launch

// Data model za E-aditiv
data class EAdditive(
    val code: String,
    val name: String,
    val description: String,
    val origin: String,
    val function: String,
    val healthRisks: String = "",
    val usage: String = "",
    val adi: String = "",
    val otherDetails: String = "",
    val riskLevel: RiskLevel = deriveRiskLevel(healthRisks)
)

private fun deriveRiskLevel(healthRisks: String): RiskLevel {
    val txt = healthRisks.lowercase()
    return when {
        txt.contains("carcin") || txt.contains("tumor") || txt.contains("banned") -> RiskLevel.HIGH
        txt.contains("hyperactivity") || txt.contains("allerg") || txt.contains("nausea") -> RiskLevel.MODERATE
        txt.isBlank() || txt.contains("not specified") -> RiskLevel.UNKNOWN
        else -> RiskLevel.LOW
    }
}

enum class RiskLevel(val displayName: String, val color: Color) {
    LOW("Low Risk", Color(0xFF10B981)),
    MODERATE("Moderate Risk", Color(0xFFF59E0B)),
    HIGH("High Risk", Color(0xFFEF4444)),
    UNKNOWN("Unknown", Color(0xFF6B7280))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EAdditivesScreen(
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<EAdditive>>(emptyList()) }
    var selectedAdditive by remember { mutableStateOf<EAdditive?>(null) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Preload entire additives list once for faster repeated searches
    val allAdditivesState = remember { mutableStateOf<List<EAdditive>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            val jsonString = context.assets.open("e_additives_database.json").bufferedReader().use { it.readText() }
            val gson = com.google.gson.Gson()
            val additiveType = object : com.google.gson.reflect.TypeToken<List<EAdditive>>() {}.type
            allAdditivesState.value = gson.fromJson(jsonString, additiveType)
            android.util.Log.d("EAdditivesDB", "Preloaded ${allAdditivesState.value.size} additives")
        } catch (e: Exception) {
            android.util.Log.e("EAdditivesDB", "Failed preload: ${e.message}", e)
        }
    }

    val textColor = MaterialTheme.colorScheme.onBackground

    fun performSearch() {
        scope.launch {
            searching = true
            errorMessage = null
            hasSearched = true
            try {
                val term = searchQuery.trim().lowercase()
                val base = allAdditivesState.value
                searchResults = if (term.isBlank()) emptyList() else base.filter { a ->
                    a.code.lowercase().contains(term) ||
                            a.name.lowercase().contains(term) ||
                            a.function.lowercase().contains(term)
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Search failed"
            } finally {
                searching = false
            }
        }
    }

    Scaffold(
        // Removed local TopAppBar to avoid double headers; use inline header below
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Inline header with back button (Body Module style)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "E-Additives Info",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Search bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            "E100, E250, Curcumin...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    keyboardActions = KeyboardActions(
                        onSearch = { performSearch() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textColor,
                        unfocusedTextColor = textColor,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        cursorColor = DrawerBlue,
                        focusedBorderColor = DrawerBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { performSearch() },
                    modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DrawerBlue),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Search", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Loading indicator
            if (searching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = DrawerBlue
                )
                Spacer(Modifier.height(16.dp))
            }

            // Error message
            errorMessage?.let { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // No results message
            if (hasSearched && !searching && searchResults.isEmpty() && errorMessage == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔍", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No additives found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Try searching with E-number or name",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Results list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(searchResults) { additive ->
                    EAdditiveCard(
                        additive = additive,
                        onClick = { selectedAdditive = additive }
                    )
                }

                // Educational content section - always show at the bottom
                if (true) {
                    item {
                        Spacer(Modifier.height(8.dp))

                        // What are additives section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "ℹ️ What Are Food Additives?",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = DrawerBlue
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "According to food regulations, an additive is any substance that is not usually consumed as food itself, but is intentionally added to food for technological reasons during production, processing, preparation, or storage. These substances can end up in the final food product either directly or indirectly.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))

                        // Categories section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "📋 Common Categories",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = DrawerBlue
                                )
                                Spacer(Modifier.height(12.dp))

                                AdditiveCategoryItem(
                                    "E100 Series - Colors",
                                    "Natural or synthetic colors added to foods that don't withstand processing. Commonly found in snacks, cereals, candies, and beverages."
                                )
                                Spacer(Modifier.height(8.dp))

                                AdditiveCategoryItem(
                                    "E200 Series - Preservatives",
                                    "Extend shelf life by preventing microbial growth that would spoil food."
                                )
                                Spacer(Modifier.height(8.dp))

                                AdditiveCategoryItem(
                                    "E300 Series - Antioxidants",
                                    "Protect foods from oxidation, preserving nutritional value and color. Prevent fat rancidity."
                                )
                                Spacer(Modifier.height(8.dp))

                                AdditiveCategoryItem(
                                    "E400 Series - Stabilizers",
                                    "Include thickeners, gelling agents, emulsifiers. Help mix ingredients and prevent separation."
                                )
                                Spacer(Modifier.height(8.dp))

                                AdditiveCategoryItem(
                                    "E600 Series - Flavor Enhancers",
                                    "Improve or intensify the taste of foods."
                                )
                                Spacer(Modifier.height(8.dp))

                                AdditiveCategoryItem(
                                    "E900 Series - Sweeteners",
                                    "Provide sweet taste with fewer calories than sugar."
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))

                        // Why are they added section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "❓ Why Are Additives Added?",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = DrawerBlue
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Additives are officially added to improve quality, safety, and extend shelf life. However, they often replace more expensive ingredients, potentially reducing product quality.\n\n" +
                                    "Uses include: coloring, thickening, mixing water with fats, preservation, flavor enhancement, sweetening without sugar, preventing oxidation, and assisting in food processing.\n\n" +
                                    "Important: Additives must not be dangerous to health or conceal poor product quality.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))

                        // ADI section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFEF3C7)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "⚠️ Acceptable Daily Intake (ADI)",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "ADI represents the amount of a substance a person may consume daily throughout their life without endangering health. It's expressed in milligrams per kilogram of body weight.\n\n" +
                                    "Note: Adults can tolerate more than children. Current ADI values are the scientific basis for risk assessment, but long-term consequences aren't fully known.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp,
                                    color = Color(0xFF78350F)
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))

                        // Production section
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "🧬 How Are They Produced?",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = DrawerBlue
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Additives are produced through:\n\n" +
                                    "• Plant extraction (chlorophyll, carotenes, anthocyanin, lecithin)\n" +
                                    "• Synthesis (phosphates, canthaxanthin)\n" +
                                    "• Animal sources (carmine, bone phosphate, beeswax, lanolin, lactitol, lysozyme)\n\n" +
                                    "The EU has around 1,700 registered additives, plus approximately 4,500 registered aromas which are treated separately.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(8.dp))

                        // Source citation
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "📚 Source",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Information adapted from: ninamvseeno.si/o-aditivih.aspx",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // Detail dialog
    selectedAdditive?.let { additive ->
        EAdditiveDetailDialog(
            additive = additive,
            onDismiss = { selectedAdditive = null }
        )
    }
}

@Composable
private fun EAdditiveCard(
    additive: EAdditive,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Risk indicator
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = additive.riskLevel.color,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        additive.code,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    additive.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    additive.function,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = additive.riskLevel.color.copy(alpha = 0.2f)
                ) {
                    Text(
                        additive.riskLevel.displayName,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = additive.riskLevel.color
                    )
                }
            }
        }
    }
}

@Composable
private fun EAdditiveDetailDialog(
    additive: EAdditive,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(additive.code + " – " + (additive.name.ifBlank { "No name" }), fontWeight = FontWeight.Bold) },
        text = {
            // Use LazyColumn instead of verticalScroll to avoid unresolved import issues
            LazyColumn(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { DetailSection("Description", additive.description.ifBlank { "Not specified" }) }
                item { DetailSection("Function", additive.function.ifBlank { "Not specified" }) }
                item { DetailSection("Origin", additive.origin.ifBlank { "Not specified" }) }
                item { DetailSection("Health risks", additive.healthRisks.ifBlank { "Not specified" }) }
                item { DetailSection("Usage in foods", additive.usage.ifBlank { "Not specified" }) }
                item { DetailSection("Acceptable daily intake (ADI)", additive.adi.ifBlank { "Not specified" }) }
                item { DetailSection("Other details", additive.otherDetails.ifBlank { "Not specified" }) }
                item {
                    Surface(shape = RoundedCornerShape(8.dp), color = additive.riskLevel.color, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            additive.riskLevel.displayName,
                            modifier = Modifier.padding(8.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun DetailSection(title: String, content: String) {
    Column {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = DrawerBlue
        )
        Spacer(Modifier.height(4.dp))
        Text(
            content,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun AdditiveCategoryItem(title: String, description: String) {
    Column {
        Text(
            title,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Offline function to search E-additives from local JSON database
suspend fun searchEAdditives(query: String, context: android.content.Context): List<EAdditive> {
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val searchTerm = query.trim().lowercase()

            // Load JSON from assets
            val jsonString = context.assets.open("e_additives_database.json").bufferedReader().use { it.readText() }

            // Parse JSON
            val gson = com.google.gson.Gson()
            val additiveType = object : com.google.gson.reflect.TypeToken<List<EAdditive>>() {}.type
            val allAdditives: List<EAdditive> = gson.fromJson(jsonString, additiveType)

            android.util.Log.d("EAdditivesDB", "Loaded ${allAdditives.size} additives from database")

            // Filter based on search query
            allAdditives.filter { additive ->
                additive.code.contains(searchTerm, ignoreCase = true) ||
                additive.name.contains(searchTerm, ignoreCase = true) ||
                additive.function.contains(searchTerm, ignoreCase = true)
            }
        } catch (e: Exception) {
            android.util.Log.e("EAdditivesDB", "Error loading database: ${e.message}", e)
            throw e
        }
    }
}
