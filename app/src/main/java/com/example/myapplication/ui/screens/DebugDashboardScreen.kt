package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.daily.TransactionRecord
import com.example.myapplication.viewmodels.DebugViewModel
import com.example.myapplication.viewmodels.TdeeDebugInputs
import com.example.myapplication.viewmodels.WeightPredictorDebugInputs
import java.text.SimpleDateFormat
import java.util.*

/**
 * Debug Dashboard — skrit zaslon, dostopen s 5-kratnim klikom na profilno sliko.
 * Prikazuje: transakcijski log, cache indikator, TDEE surovine, hard reset gumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugDashboardScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vm: DebugViewModel = viewModel()

    val transactions by vm.transactions.collectAsState()
    val isFromCache by vm.isFromCache.collectAsState()
    val tdeeInputs by vm.tdeeInputs.collectAsState()
    val hardResetStatus by vm.hardResetStatus.collectAsState()
    val weightPredictorInputs by vm.weightPredictorInputs.collectAsState()

    var showHardResetDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "🛠️ Debug Dashboard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "Samo za razvijalce",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazaj")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.checkCacheStatus() }) {
                        Icon(Icons.Default.Refresh, "Osveži", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF0F0F1A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── 1. Cache vs Server ─────────────────────────────────────────────
            DebugCard(title = "📡 Cache vs. Server (dailyLog)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = when (isFromCache) {
                                true  -> "💾 Iz lokalnega cache-a"
                                false -> "🌐 Direktno s strežnika"
                                null  -> "⏳ Preverjanje..."
                            },
                            color = when (isFromCache) {
                                true  -> Color(0xFFFFD700)
                                false -> Color(0xFF4CAF50)
                                null  -> Color.Gray
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = when (isFromCache) {
                                true  -> "Firestore Persistence deluje ✅"
                                false -> "Sveži podatki s strežnika"
                                null  -> "—"
                            },
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                    TextButton(onClick = { vm.checkCacheStatus() }) {
                        Text("Preveri", color = Color(0xFF64B5F6))
                    }
                }
            }

            // ── 2. TDEE Algoritemska surovina ─────────────────────────────────
            DebugCard(title = "🔢 TDEE Algoritemska surovina") {
                TdeeRawInputsTable(tdeeInputs)
            }

            // ── 3. Weight Predictor surovina ──────────────────────────────────
            DebugCard(title = "⚖️ Weight Predictor — Algoritemska surovina") {
                WeightPredictorDebugTable(weightPredictorInputs)
            }

            // ── 4. Sledilnik transakcij ────────────────────────────────────────
            DebugCard(title = "📋 Sledilnik transakcij (zadnjih 5)") {
                if (transactions.isEmpty()) {
                    Text(
                        "Ni zabeleženih transakcij v tej seji.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        transactions.forEach { record ->
                            TransactionRow(record)
                        }
                    }
                }
            }

            // ── 5. Hard Reset gumb ─────────────────────────────────────────────
            DebugCard(title = "🚨 Nasilna sinhronizacija") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Pobriše lokalni Firestore SQLite cache in znova zažene aplikacijo. " +
                        "Vsi podatki bodo pridobljeni direktno s strežnika.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    if (hardResetStatus.isNotBlank()) {
                        Text(
                            hardResetStatus,
                            color = if (hardResetStatus.startsWith("✅")) Color(0xFF4CAF50)
                                    else if (hardResetStatus.startsWith("❌")) Color(0xFFFF5252)
                                    else Color(0xFFFFD700),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { showHardResetDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF3D00)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Warning, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Hard Reset Cache + Ponovni zagon", color = Color.White)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Potrditveni dialog za Hard Reset
    if (showHardResetDialog) {
        AlertDialog(
            onDismissRequest = { showHardResetDialog = false },
            icon = { Icon(Icons.Default.Warning, null, tint = Color(0xFFFF3D00)) },
            title = { Text("Pobriši Firestore Cache?") },
            text = {
                Text(
                    "To bo:\n" +
                    "• Izbrisalo lokalni SQLite Firestore cache\n" +
                    "• Znova zagnalo aplikacijo\n" +
                    "• Ob prvem zagonu vse pridobilo s strežnika\n\n" +
                    "Shranjeni podatki v Firestore-u ostanejo nedotaknjeni."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showHardResetDialog = false
                        vm.hardResetFirestoreCache(context)
                    }
                ) {
                    Text("Potrdi", color = Color(0xFFFF3D00), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHardResetDialog = false }) {
                    Text("Prekliči")
                }
            }
        )
    }
}

// ── Helper composables ─────────────────────────────────────────────────────────

@Composable
private fun DebugCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                color = Color(0xFF64B5F6),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            HorizontalDivider(color = Color(0xFF2A2A3E))
            content()
        }
    }
}

@Composable
private fun TdeeRawInputsTable(inputs: TdeeDebugInputs) {
    val rows = listOf(
        Triple("BMR",                    "${"%.1f".format(inputs.bmr)} kcal", Color.White),
        Triple("Activity Multiplier",    "× ${"%.1f".format(inputs.activityMultiplier)} (sedentarno)", Color.Gray),
        Triple("Base TDEE",              "${"%.1f".format(inputs.baseTdee)} kcal", Color(0xFF64B5F6)),
        Triple("Burned Calories Delta",  "+ ${inputs.burnedCaloriesDelta} kcal", Color(0xFF4CAF50)),
        Triple("Goal Adjustment",        "${if (inputs.goalAdjustment >= 0) "+" else ""}${inputs.goalAdjustment} kcal", Color(0xFFFFD700)),
        Triple("─────────────────",      "──────────", Color(0xFF3A3A5E)),
        Triple("🎯 Dynamic Target",      "${inputs.dynamicTarget} kcal", Color(0xFFFF9800)),
        Triple("🍽️ Consumed",            "${inputs.consumedCalories} kcal", Color(0xFF9C27B0)),
        Triple("💧 Water",               "${inputs.waterMl} ml", Color(0xFF03A9F4)),
        Triple("🏷️ Goal",                inputs.goal.ifBlank { "—" }, Color.Gray),
    )
    rows.forEach { (label, value, color) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.Gray, fontSize = 12.sp)
            Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun WeightPredictorDebugTable(inputs: WeightPredictorDebugInputs) {
    if (!inputs.isReady) {
        Text(
            "⏳ Napoved ni izračunana. Odpri Progress zaslon z vnosi za zadnjih 7 dni.",
            color = Color.Gray,
            fontSize = 12.sp
        )
        return
    }
    val balance = inputs.avgDailyBalanceKcal
    val balanceColor = when {
        balance < -50 -> Color(0xFF4CAF50)
        balance > 50  -> Color(0xFFFF5252)
        else          -> Color.Gray
    }
    val rows = listOf(
        Triple("EMA Teža (7-day)",       "${"%.2f".format(inputs.emaWeightKg)} kg",       Color.White),
        Triple("Avg dnevni balans",       "${if (balance >= 0) "+" else ""}${balance.toInt()} kcal",  balanceColor),
        Triple("Formula",                "7700 kcal ≈ 1 kg",                               Color(0xFF888888)),
        Triple("Napoved čez 30 dni",     "${"%.2f".format(inputs.predicted30DayKg)} kg",  Color(0xFF64B5F6)),
        Triple("Aktivni dnevi (7d okno)", "${inputs.activeDaysInLastWeek}/7",              Color.White),
        Triple("─────────────────",      "──────────",                                    Color(0xFF3A3A5E)),
        Triple("Ciljna teža",            inputs.goalWeightKg?.let { "${"%.1f".format(it)} kg" } ?: "— (ni nastavljena)", Color(0xFFFFD700)),
        Triple("Dnevi do cilja",         inputs.daysToGoal?.toString() ?: "—",            Color(0xFFFF9800)),
        Triple("Datum cilja",            inputs.goalDateStr ?: "—",                       Color(0xFFFF9800)),
    )
    rows.forEach { (label, value, color) ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.Gray, fontSize = 12.sp)
            Text(value, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun TransactionRow(record: TransactionRecord) {    val isSuccess = record.status.startsWith("✅")
    val timeStr = remember(record.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(record.timestamp))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSuccess) Color(0xFF1B2E1B) else Color(0xFF2E1B1B))
            .border(1.dp,
                if (isSuccess) Color(0xFF2E5A2E) else Color(0xFF5A2E2E),
                RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            record.status.take(2),  // Samo emoji
            fontSize = 16.sp,
            modifier = Modifier.width(28.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                record.operation,
                color = Color(0xFFE0E0E0),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Text(
                timeStr,
                color = Color.Gray,
                fontSize = 10.sp
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${record.durationMs} ms",
                color = when {
                    record.durationMs < 300  -> Color(0xFF4CAF50)
                    record.durationMs < 1000 -> Color(0xFFFFD700)
                    else                     -> Color(0xFFFF5252)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

