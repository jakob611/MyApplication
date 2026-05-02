package com.example.myapplication.ui.screens

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.datetime.LocalDate
import com.example.myapplication.domain.*
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import com.example.myapplication.utils.HapticFeedback
import com.example.myapplication.utils.calculateEMA
import com.example.myapplication.utils.calculateAdaptiveTDEE
import java.util.Locale
import com.example.myapplication.data.UserProfile

// Data holders
private data class DailyLogSummary(val date: LocalDate, val calories: Double, val waterMl: Int)
private data class WeightLog(val date: LocalDate, val weightKg: Double)
enum class ProgressRange(val label: String) { WEEK("Week"), MONTH("Month"), YEAR("Year"), ALL("All") }

private data class NeighborEdges(
    val prev: Pair<LocalDate, Double>?,
    val next: Pair<LocalDate, Double>?
)

// Zero-fill: for every day in range without an entry, adds a 0.0 value
private fun zeroFillDateRange(
    points: List<Pair<LocalDate, Double>>,
    fromDate: LocalDate,
    toDate: LocalDate
): List<Pair<LocalDate, Double>> {
    val today = LocalDate.now()
    val existing = points.associateBy { it.first }
    val result = mutableListOf<Pair<LocalDate, Double>>()
    var cur = fromDate
    while (!cur.isAfter(toDate)) {
        if (!cur.isAfter(today)) { // Don't fill future days
            result.add(existing[cur] ?: (cur to 0.0))
        }
        cur = cur.plusDays(1)
    }
    return result
}

private fun boundaryNeighbors(full: List<Pair<LocalDate, Double>>, filtered: List<Pair<LocalDate, Double>>): NeighborEdges {
    if (full.isEmpty() || filtered.isEmpty()) return NeighborEdges(null, null)
    val first = filtered.first().first
    val last = filtered.last().first
    val prev = full.filter { it.first < first }.maxByOrNull { it.first }
    val next = full.filter { it.first > last }.minByOrNull { it.first }
    return NeighborEdges(prev, next)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    openWeightInput: Boolean = false,
    userProfile: UserProfile = UserProfile(),
    isOnline: Boolean = true,
    onOpenMenu: () -> Unit = {},
    onProClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // DODANO: Proper coroutine scope namesto GlobalScope
    val uid = remember { com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() }
    var dailyLogs by remember { mutableStateOf<List<DailyLogSummary>>(emptyList()) }
    var weightLogs by remember { mutableStateOf<List<WeightLog>>(emptyList()) }
    var burnedByDay by remember { mutableStateOf<List<Pair<LocalDate, Double>>>(emptyList()) } // ADDED
    var loading by remember { mutableStateOf(true) }
    var range by rememberSaveable { mutableStateOf(ProgressRange.WEEK) }
    var showPreviousProgress by rememberSaveable { mutableStateOf(false) }
    var showWeightDialog by remember { mutableStateOf(openWeightInput) }
    var xpPopupAmount by remember { mutableStateOf(0) }
    var showXpPopup by remember { mutableStateOf(false) }

    LaunchedEffect(openWeightInput) {
        if (openWeightInput) {
            showWeightDialog = true
        }
    }
    // ── Data Budgeting (Faza 6) ────────────────────────────────────────────────────────────────
    // PRED: 3 ločeni listenerji (weightLogs + dailyLogs + daily_health)
    // PO:   2 listenerja (weightLogs + dailyLogs)
    //
    // Spremembe:
    // 1. dailyLogs: consumedCalories bere direktno iz polja (ne iterira 'items' arraija)
    // 2. dailyLogs: burnedCalories bere direktno iz polja (ne potrebuje separate daily_health)
    // 3. sessionListener (daily_health) ODSTRANJEN — ta kolekcija se po Fazi 5 ne piše več,
    //    burnedCalories je sedaj shranjeno v dailyLogs.burnedCalories
    // Rezultat: -1 Firestore listener = -33% branj za Progress screen
    // ─────────────────────────────────────────────────────────────────────────────────────────
    var dailyListener: ListenerRegistration? by remember { mutableStateOf(null) }
    var weightListener: ListenerRegistration? by remember { mutableStateOf(null) }
    // No persistence for range while fixing build issues

    DisposableEffect(uid) {
        if (uid != null) {
            val userRef = com.example.myapplication.persistence.FirestoreHelper.getUserRef(uid)
            loading = true
            weightListener = userRef.collection("weightLogs")
                .addSnapshotListener { snap, _ ->
                    weightLogs = snap?.documents?.mapNotNull { d ->
                        val dateStr = d.getString("date") ?: d.id
                        val w = (d.get("weightKg") as? Number)?.toDouble() ?: return@mapNotNull null
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrElse { return@mapNotNull null }
                        WeightLog(date, w)
                    }?.sortedBy { it.date } ?: emptyList()
                }
            dailyListener = userRef.collection("dailyLogs")
                .addSnapshotListener { snap, _ ->
                    // Data Budgeting: beri consumedCalories direktno (ne iteriramo items!)
                    val parsedLogs = mutableListOf<DailyLogSummary>()
                    val burnedMap = mutableMapOf<LocalDate, Double>()
                    snap?.documents?.forEach { d ->
                        val dateStr = d.getString("date") ?: d.id
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrElse { return@forEach }
                        // ⚡ Direktno polje — ne iteriramo items arraija
                        val caloriesTotal = (d.get("consumedCalories") as? Number)?.toDouble() ?: 0.0
                        val water = (d.get("waterMl") as? Number)?.toInt() ?: 0
                        parsedLogs.add(DailyLogSummary(date, caloriesTotal, water))
                        // ⚡ burnedCalories iz dailyLogs (ne daily_health — ki se po Fazi 5 ne piše več)
                        val burned = (d.get("burnedCalories") as? Number)?.toDouble() ?: 0.0
                        if (burned > 0) burnedMap[date] = burned
                    }
                    dailyLogs = parsedLogs.sortedBy { it.date }
                    burnedByDay = burnedMap.entries.sortedBy { it.key }.map { it.key to it.value }
                    loading = false
                }
        }
        onDispose {
            dailyListener?.remove(); weightListener?.remove()
        }
    }

    val weightPairs = remember(weightLogs) { weightLogs.sortedBy { it.date }.map { it.date to it.weightKg } }
    val dailyPairs = remember(dailyLogs) { dailyLogs.sortedBy { it.date }.map { it.date to it.calories } }
    val waterPairs = remember(dailyLogs) { dailyLogs.sortedBy { it.date }.map { it.date to it.waterMl.toDouble() } }
    val burnedPairs = remember(burnedByDay) { burnedByDay.sortedBy { it.first } }

    // ── Faza 7: Weight Predictor ──────────────────────────────────────────────────────────────────
    // Podatki: weightLogs (EMA) + dailyLogs zadnjih 7 dni (avg balance)
    val weightPrediction: WeightPredictionDisplay? = remember(weightLogs, dailyLogs, burnedByDay, userProfile) {
        computeWeightPrediction(weightLogs, dailyLogs, burnedByDay, userProfile)
    }
    // ─────────────────────────────────────────────────────────────────────────────────────────────

    val filteredDaily: List<DailyLogSummary> = remember(dailyLogs, range) { filterRange(dailyLogs, range) }
    val filteredWeight: List<WeightLog> = remember(weightLogs, range) { filterRange(weightLogs, range) }
    val filteredBurned: List<Pair<LocalDate, Double>> = remember(burnedByDay, range) {
        val list = burnedByDay.map { Pair(it.first, it.second) }
        filterRange(list, range)
    }
    val filteredWater: List<DailyLogSummary> = remember(dailyLogs, range) { filterRange(dailyLogs, range) }

    val weightNeighbors = remember(weightPairs, filteredWeight) {
        boundaryNeighbors(weightPairs, filteredWeight.map { it.date to it.weightKg })
    }
    val dailyNeighbors = remember(dailyPairs, filteredDaily) {
        boundaryNeighbors(dailyPairs, filteredDaily.map { it.date to it.calories })
    }
    val waterNeighbors = remember(waterPairs, filteredWater) {
        boundaryNeighbors(waterPairs, filteredWater.map { it.date to it.waterMl.toDouble() })
    }
    val burnedNeighbors = remember(burnedPairs, filteredBurned) {
        boundaryNeighbors(burnedPairs, filteredBurned)
    }

    // --- ZERO-FILL: add 0 entries for days without data (not for weight) ---
    // These are computed AFTER we know viewMinDate/viewMaxDate (see below), so we defer

    // --- Global Date Range Calculation for Aligned Axis ---
    val allFilteredDates = remember(filteredDaily, filteredWeight, filteredBurned, filteredWater) {
        val d1 = filteredDaily.map { it.date }
        val d2 = filteredWeight.map { it.date }
        val d3 = filteredBurned.map { it.first }
        val d4 = filteredWater.map { it.date }
        (d1 + d2 + d3 + d4).sorted()
    }

    val viewMinDate: LocalDate
    val viewMaxDate: LocalDate

    if (allFilteredDates.isNotEmpty()) {
         // If we have data, we use the actual data range, but we might want to extend it to fill the "Range" view
        // logic:
        // WEEK: always show 7 days ending at dataMax (or today if empty)
        // MONTH: always show at least one month or the range of data
        // YEAR: always show at least one year or range
        // ALL: range of data
        val dataMin = allFilteredDates.first()
        val dataMax = allFilteredDates.last()

        if (range == ProgressRange.WEEK) {
            // For week, we want a fixed 7 day window ending at dataMax
            viewMaxDate = dataMax
            viewMinDate = dataMax.minusDays(6)
        } else {
            viewMinDate = dataMin
            viewMaxDate = dataMax
        }
    } else {
        // Fallback if absolutely no data in filtered range
        viewMaxDate = LocalDate.now()
        viewMinDate = when(range) {
            ProgressRange.WEEK -> viewMaxDate.minusDays(6)
            ProgressRange.MONTH -> viewMaxDate.minusMonths(1)
            ProgressRange.YEAR -> viewMaxDate.minusYears(1)
            ProgressRange.ALL -> viewMaxDate.minusMonths(1)
        }
    }
    // ----------------------------------------------------

    // Zero-fill for non-weight charts (calories, water, burned)
    val zeroFilledDaily = remember(filteredDaily, viewMinDate, viewMaxDate) {
        zeroFillDateRange(filteredDaily.map { it.date to it.calories }, viewMinDate, viewMaxDate)
    }
    val zeroFilledWater = remember(filteredWater, viewMinDate, viewMaxDate) {
        zeroFillDateRange(filteredWater.map { it.date to it.waterMl.toDouble() }, viewMinDate, viewMaxDate)
    }
    val zeroFilledBurned = remember(filteredBurned, viewMinDate, viewMaxDate) {
        zeroFillDateRange(filteredBurned, viewMinDate, viewMaxDate)
    }

    // Previous period data for comparison overlay
    val prevMinDate = remember(viewMinDate, range) {
        when(range) {
            ProgressRange.WEEK -> viewMinDate.minusWeeks(1)
            ProgressRange.MONTH -> viewMinDate.minusMonths(1)
            ProgressRange.YEAR -> viewMinDate.minusYears(1)
            ProgressRange.ALL -> viewMinDate
        }
    }
    val prevMaxDate = remember(viewMaxDate, range) {
        when(range) {
            ProgressRange.WEEK -> viewMaxDate.minusWeeks(1)
            ProgressRange.MONTH -> viewMaxDate.minusMonths(1)
            ProgressRange.YEAR -> viewMaxDate.minusYears(1)
            ProgressRange.ALL -> viewMaxDate
        }
    }

    fun shiftForward(date: LocalDate): LocalDate {
        return when(range) {
            ProgressRange.WEEK -> date.plusWeeks(1)
            ProgressRange.MONTH -> date.plusMonths(1)
            ProgressRange.YEAR -> date.plusYears(1)
            ProgressRange.ALL -> date
        }
    }

    val prevWeightPairs = remember(weightPairs, prevMinDate, prevMaxDate, showPreviousProgress, range) {
        if (!showPreviousProgress || range == ProgressRange.ALL) null
        else weightPairs.filter { !it.first.isBefore(prevMinDate) && !it.first.isAfter(prevMaxDate) }.map { shiftForward(it.first) to it.second }
    }
    val prevZeroFilledDaily = remember(dailyPairs, prevMinDate, prevMaxDate, showPreviousProgress, range) {
        if (!showPreviousProgress || range == ProgressRange.ALL) null
        else zeroFillDateRange(dailyPairs, prevMinDate, prevMaxDate).map { shiftForward(it.first) to it.second }
    }
    val prevZeroFilledWater = remember(waterPairs, prevMinDate, prevMaxDate, showPreviousProgress, range) {
        if (!showPreviousProgress || range == ProgressRange.ALL) null
        else zeroFillDateRange(waterPairs, prevMinDate, prevMaxDate).map { shiftForward(it.first) to it.second }
    }
    val prevZeroFilledBurned = remember(burnedPairs, prevMinDate, prevMaxDate, showPreviousProgress, range) {
        if (!showPreviousProgress || range == ProgressRange.ALL) null
        else zeroFillDateRange(burnedPairs, prevMinDate, prevMaxDate).map { shiftForward(it.first) to it.second }
    }

    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior(androidx.compose.material3.rememberTopAppBarState())

    androidx.compose.material3.Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            com.example.myapplication.GlobalHeaderBar(
                isOnline = isOnline,
                onOpenMenu = onOpenMenu,
                onProClick = onProClick,
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 4.dp,
                    bottom = innerPadding.calculateBottomPadding() + 100.dp
                )
            ) {
            item {
                RangeSelector(range = range, onSelect = { range = it })
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Compare previous period",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Switch(
                        checked = showPreviousProgress,
                        onCheckedChange = { showPreviousProgress = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
            if (loading) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else {
                if (weightLogs.isEmpty() && dailyLogs.isEmpty()) {
                    item { Text("No data yet. Log foods, water, and weight to see progress charts.") }
                } else {
                    item {
                        val isLbs = userProfile.weightUnit == "lb"
                        val displayPoints = filteredWeight.map {
                            it.date to if (isLbs) it.weightKg * 2.20462 else it.weightKg
                        }
                        val prevDisplayPoints = prevWeightPairs?.map {
                            it.first to if (isLbs) it.second * 2.20462 else it.second
                        }
                        ChartSectionWithAxis(
                            title = if (isLbs) "Weight (lb)" else "Weight (kg)",
                            unit = if (isLbs) "lb" else "kg",
                            color = Color(0xFF2563EB),
                            points = displayPoints,
                            previousPoints = prevDisplayPoints,
                            addWeightButton = if (uid != null) { { showWeightDialog = true } } else null,
                            edgePrev = weightNeighbors.prev?.let { it.first to if (isLbs) it.second * 2.20462 else it.second },
                            edgeNext = weightNeighbors.next?.let { it.first to if (isLbs) it.second * 2.20462 else it.second },
                            minDate = viewMinDate,
                            maxDate = viewMaxDate,
                            rangeType = range
                        )
                    }
                    // ── Faza 7: Weight Destiny kartica (pod Weight grafom) ────────
                    if (weightPrediction != null) {
                        item {
                            WeightDestinyCard(
                                prediction = weightPrediction,
                                isLbs = userProfile.weightUnit == "lb"
                            )
                        }
                    }
                    // ──────────────────────────────────────────────────────────────
                    item {
                        ChartSectionWithAxis(
                            title = "Caloric intake", unit = "kcal", color = Color(0xFFF97316),
                            points = zeroFilledDaily,
                            previousPoints = prevZeroFilledDaily,
                            edgePrev = dailyNeighbors.prev,
                            edgeNext = dailyNeighbors.next,
                            minDate = viewMinDate,
                            maxDate = viewMaxDate,
                            rangeType = range
                        )
                    }
                    item {
                        ChartSectionWithAxis(
                            title = "Water (ml)", unit = "ml", color = Color(0xFF10B981),
                            points = zeroFilledWater,
                            previousPoints = prevZeroFilledWater,
                            edgePrev = waterNeighbors.prev,
                            edgeNext = waterNeighbors.next,
                            minDate = viewMinDate,
                            maxDate = viewMaxDate,
                            rangeType = range
                        )
                    }
                    // New burned calories chart at the bottom
                    if (burnedByDay.isNotEmpty()) {
                        item {
                            ChartSectionWithAxis(
                                title = "Calories burned", unit = "kcal", color = Color(0xFFE11D48),
                                points = zeroFilledBurned,
                                previousPoints = prevZeroFilledBurned,
                                edgePrev = burnedNeighbors.prev,
                                edgeNext = burnedNeighbors.next,
                                minDate = viewMinDate,
                                maxDate = viewMaxDate,
                                rangeType = range
                            )
                        }
                    }
                }
            }
        }
        if (showWeightDialog && uid != null) {
            WeightEntryDialog(uid = uid, weightUnit = userProfile.weightUnit, onDismiss = { showWeightDialog = false }, onSaved = {
                showWeightDialog = false
                xpPopupAmount = 50
                showXpPopup = true
            })
        }
    }

    // XP Popup
    com.example.myapplication.ui.components.XPPopup(
        xpAmount = xpPopupAmount,
        isVisible = showXpPopup,
        onDismiss = { showXpPopup = false }
    )
}
}

@Composable
private fun RangeSelector(range: ProgressRange, onSelect: (ProgressRange) -> Unit) {
    val context = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProgressRange.entries.forEach { r ->
            val selected = r == range
            Surface(
                shape = RoundedCornerShape(40.dp),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.height(36.dp).clickable {
                    HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.SELECTION)
                    onSelect(r)
                }
            ) { Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) { Text(r.label, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold) } }
        }
    }
}

@Composable
private fun ChartSectionWithAxis(
    title: String,
    unit: String,
    color: Color,
    points: List<Pair<LocalDate, Double>>,
    previousPoints: List<Pair<LocalDate, Double>>? = null,
    addWeightButton: (() -> Unit)? = null,
    edgePrev: Pair<LocalDate, Double>? = null,
    edgeNext: Pair<LocalDate, Double>? = null,
    minDate: LocalDate,
    maxDate: LocalDate,
    rangeType: ProgressRange
) {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Box {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (addWeightButton != null) {
                        FilledIconButton(onClick = {
                            HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.CLICK)
                            addWeightButton()
                        }, modifier = Modifier.size(40.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = "Add")
                        }
                    }
                }
                
                // Add Before/After Metric for Weight Graph
                if (title.contains("Weight") && points.isNotEmpty()) {
                    val firstVal = points.first().second
                    val lastVal = points.last().second
                    val diff = lastVal - firstVal
                    
                    if (abs(diff) >= 0.1) {
                        val trendText = if (diff < 0) "Lost" else "Gained"
                        val trendColor = if (diff < 0) Color(0xFF10B981) else Color(0xFFE11D48)
                        val periodText = when(rangeType) {
                            ProgressRange.WEEK -> "this week"
                            ProgressRange.MONTH -> "this month"
                            ProgressRange.YEAR -> "this year"
                            ProgressRange.ALL -> "all time"
                        }
                        
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$trendText ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(
                                String.format(java.util.Locale.US, "%.1f %s", abs(diff), unit),
                                color = trendColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(" $periodText", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                if (points.isEmpty()) {
                    Text("No data", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    InteractiveLineChart(
                        points = points,
                        previousPoints = previousPoints,
                        strokeColor = color,
                        unit = unit,
                        edgePrev = edgePrev,
                        edgeNext = edgeNext,
                        minDate = minDate,
                        maxDate = maxDate,
                        rangeType = rangeType
                    )
                }
            }
        }
    }
}

@Composable
private fun InteractiveLineChart(
    points: List<Pair<LocalDate, Double>>,
    previousPoints: List<Pair<LocalDate, Double>>? = null,
    strokeColor: Color,
    unit: String,
    edgePrev: Pair<LocalDate, Double>? = null,
    edgeNext: Pair<LocalDate, Double>? = null,
    minDate: LocalDate,
    maxDate: LocalDate,
    rangeType: ProgressRange
) {
    val context = LocalContext.current

    // Sort points
    val sorted = remember(points) { points.sortedBy { it.first } }

    // Calculate Y-axis range based on visible points
    val values = remember(sorted, previousPoints) { sorted.map { it.second } + (previousPoints?.map { it.second } ?: emptyList()) }
    val minV = remember(values) { values.minOrNull() ?: 0.0 }
    val maxV = remember(values) { values.maxOrNull() ?: 0.0 }

    val dateFormatter = "dd MMM"
    val monthFormatter = "MMM"
    val dayFormatter = "EEE" // For Week view
    val yTicks = 5

    fun roundToNice(value: Double, roundUp: Boolean): Double {
        val step = when {
            value < 10.0 -> 2.0
            value < 50.0 -> 5.0
            value < 100.0 -> 10.0
            value < 500.0 -> 50.0
            value < 1000.0 -> 100.0
            value < 2000.0 -> 200.0
            value < 5000.0 -> 500.0
            else -> 1000.0
        }
        return if (roundUp) ceil(value / step) * step else floor(value / step) * step
    }

    val niceMin = 0.0
    val niceMax = remember(maxV) {
        val extended = maxV * 1.2 // 20% above max value
        roundToNice(extended.coerceAtLeast(1.0), true)
    }
    val span = remember(niceMin, niceMax) { (niceMax - niceMin).coerceAtLeast(1.0) }
    val actualStep = remember(span) { span / (yTicks - 1).toDouble() }

    val tickValues = remember(niceMin, span) {
        (0 until yTicks).map { i -> niceMin + (actualStep * i.toDouble()) }
    }

    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    val selectedColor = MaterialTheme.colorScheme.tertiary
    var xPositions by remember { mutableStateOf<List<Float>>(emptyList()) } // X positions for actual data points
    var yPositions by remember { mutableStateOf<List<Float>>(emptyList()) }
    var canvasPaddingStart by remember { mutableStateOf(0f) }
    var canvasPaddingTop by remember { mutableStateOf(0f) }

    BoxWithConstraints(
        modifier = Modifier
            .height(240.dp)
            .fillMaxWidth()
            .pointerInput(selectedIndex, xPositions, yPositions, canvasPaddingStart, canvasPaddingTop) {
                detectTapGestures(onTap = { offset ->
                    if (xPositions.isEmpty()) {
                        selectedIndex = null
                        return@detectTapGestures
                    }
                    val adjustedX = offset.x - canvasPaddingStart
                    // val adjustedY = offset.y - canvasPaddingTop

                    // Find nearest point
                    val nearest = xPositions.withIndex().minByOrNull { (_, x) -> abs(x - adjustedX) }
                    if (nearest != null) {
                        val idx = nearest.index
                        val dx = abs(xPositions[idx] - adjustedX)
                        // Allow larger hit area vertically
                        // val dy = if (yPositions.isNotEmpty()) abs(yPositions[idx] - adjustedY) else Float.MAX_VALUE
                        val thresholdPx = 120f
                        val newSelection = if (dx <= thresholdPx) idx else null // Relaxed Y check

                        if (newSelection != null && newSelection != selectedIndex) {
                            HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.LIGHT_CLICK)
                        }
                        selectedIndex = newSelection
                    } else {
                        selectedIndex = null
                    }
                })
            }
    ) {
        val maxW = this.maxWidth
        val maxH = this.maxHeight
        val paddingStartDp = 48.dp
        val paddingTopDp = 8.dp
        canvasPaddingStart = with(LocalDensity.current) { paddingStartDp.toPx() }
        canvasPaddingTop = with(LocalDensity.current) { paddingTopDp.toPx() }

        androidx.compose.foundation.Canvas(
            Modifier
                .fillMaxSize()
                .padding(start = paddingStartDp, bottom = 40.dp, top = paddingTopDp, end = 16.dp)
        ) {
            val innerW = size.width
            val innerH = size.height

            val totalDays = daysBetween(minDate, maxDate).coerceAtLeast(1)

            // Compute xPositions for data points based on global minDate/maxDate
            xPositions = sorted.map { (d, _) ->
                val daysFromStart = daysBetween(minDate, d).toFloat()
                (daysFromStart / totalDays.toFloat()) * innerW
            }

            // Precompute yPositions from normalized values
            yPositions = sorted.map { (_, v) ->
                val norm = (v - niceMin) / span
                (innerH - (norm * innerH)).toFloat()
            }

            // --- AXES & GRID ---

            // X & Y Axis lines
            drawLine(Color.Gray, androidx.compose.ui.geometry.Offset(0f, innerH), androidx.compose.ui.geometry.Offset(innerW, innerH), 2f)
            drawLine(Color.Gray, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(0f, innerH), 2f)

            // Horizontal grid lines (Values)
            tickValues.forEach { tv ->
                val y = (innerH - ((tv - niceMin) / span * innerH)).toFloat()
                drawLine(Color(0xFF4B5563), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(innerW, y), 1f)
            }

            // Vertical Month Separators (if not week view)
            if (rangeType != ProgressRange.WEEK) {
                var current = minDate.withDayOfMonth(1)
                if (current.isBefore(minDate)) current = current.plusMonths(1)

                while (!current.isAfter(maxDate)) {
                    val daysFromStart = daysBetween(minDate, current).toFloat()
                    val x = (daysFromStart / totalDays.toFloat()) * innerW

                    if (x >= 0 && x <= innerW) {
                        drawLine(
                            color = Color.Gray,
                            start = androidx.compose.ui.geometry.Offset(x, 0f),
                            end = androidx.compose.ui.geometry.Offset(x, innerH),
                            strokeWidth = 2f
                        )
                    }
                    current = current.plusMonths(1)
                }
            }

            // Dashed lines for data points
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            xPositions.forEach { x ->
                drawLine(
                    color = Color(0xFF3D4754),
                    start = androidx.compose.ui.geometry.Offset(x, innerH),
                    end = androidx.compose.ui.geometry.Offset(x, 0f),
                    strokeWidth = 1.1f,
                    pathEffect = dashEffect
                )
            }

            // --- DATA LINE ---
            if (sorted.isNotEmpty() && xPositions.size == sorted.size && yPositions.size == sorted.size) {
                 // Clip to bounds to prevent drawing outside chart area
                 drawContext.canvas.save()
                 drawContext.canvas.clipRect(0f, -60f, innerW, innerH + 30f)

                 // PREVIOUS PERIOD DASHED LINE
                 previousPoints?.let { prevPts ->
                     val prevSorted = prevPts.sortedBy { it.first }.filter { !it.first.isBefore(minDate) && !it.first.isAfter(maxDate) }
                     val prevXP = prevSorted.map { (d, _) ->
                         val daysFromStart = daysBetween(minDate, d).toFloat()
                         (daysFromStart / totalDays.toFloat()) * innerW
                     }
                     val prevYP = prevSorted.map { (_, v) ->
                         val norm = (v - niceMin) / span
                         (innerH - (norm * innerH)).toFloat()
                     }

                     if (prevSorted.size > 1) {
                         val prevPath = Path().apply {
                             prevSorted.forEachIndexed { idx, _ ->
                                 if (idx == 0) moveTo(prevXP[idx], prevYP[idx]) else lineTo(prevXP[idx], prevYP[idx])
                             }
                         }
                         val prevDashEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)
                         drawPath(prevPath, strokeColor.copy(alpha = 0.35f), style = Stroke(width = 3.5f, pathEffect = prevDashEffect))
                     }

                     prevSorted.forEachIndexed { idx, _ ->
                         if (prevXP[idx] in 0f..innerW) {
                             drawCircle(color = strokeColor.copy(alpha = 0.35f), radius = 5.5f, center = androidx.compose.ui.geometry.Offset(prevXP[idx], prevYP[idx]))
                         }
                     }
                 }

                 // BEFORE-AFTER DASHED TRENDLINE FOR WEIGHT
                 if (unit.contains("lb") || unit.contains("kg")) {
                     val firstY = yPositions.first()
                     val firstX = xPositions.first()
                     val lastY = yPositions.last()
                     val lastX = xPositions.last()
                     
                     if (firstX != lastX) {
                         val trendEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                         val trendColor = if (lastY > firstY) Color(0xFF10B981).copy(alpha=0.6f) else Color(0xFFE11D48).copy(alpha=0.6f) // Note: higher Y means lower value.
                         drawLine(
                             color = trendColor,
                             start = androidx.compose.ui.geometry.Offset(firstX, firstY),
                             end = androidx.compose.ui.geometry.Offset(lastX, lastY),
                             strokeWidth = 2.5f,
                             pathEffect = trendEffect
                         )
                     }
                 }

                 if (sorted.size > 1) {
                    val path = Path().apply {
                        sorted.forEachIndexed { idx, _ ->
                            val x = xPositions[idx]
                            val y = yPositions[idx]
                            if (idx == 0) moveTo(x, y) else lineTo(x, y)
                        }
                    }
                    drawPath(path, strokeColor, style = Stroke(width = 4f))
                }
                // else if sorted.size == 1: Single point, drawn as circle below

                // Edge prev
                edgePrev?.let { prev ->
                    val dxPrev = daysBetween(minDate, prev.first).toFloat()
                    val xPrev = (dxPrev / totalDays.toFloat()) * innerW
                    val yPrev = run {
                        val norm = (prev.second - niceMin) / span
                        (innerH - (norm * innerH)).toFloat()
                    }
                    val xFirst = xPositions.first()
                    val yFirst = yPositions.first()

                    if (xPrev < 0) {
                         // Find intersection with x=0
                         val slope = (yFirst - yPrev) / (xFirst - xPrev)
                         val yAtZero = yPrev + slope * (0 - xPrev)
                         if (!yAtZero.isNaN()) {
                             drawLine(strokeColor, androidx.compose.ui.geometry.Offset(0f, yAtZero), androidx.compose.ui.geometry.Offset(xFirst, yFirst), 4f)
                         }
                     }
                }

                 // Edge next
                edgeNext?.let { next ->
                    val dxNext = daysBetween(minDate, next.first).toFloat()
                    val xNext = (dxNext / totalDays.toFloat()) * innerW
                    val yNext = run {
                        val norm = (next.second - niceMin) / span
                        (innerH - (norm * innerH)).toFloat()
                    }
                    val xLast = xPositions.last()
                    val yLast = yPositions.last()

                    if (xNext > innerW) {
                         val slope = (yNext - yLast) / (xNext - xLast)
                         val yAtMax = yLast + slope * (innerW - xLast)
                         if (!yAtMax.isNaN()) {
                             drawLine(strokeColor, androidx.compose.ui.geometry.Offset(xLast, yLast), androidx.compose.ui.geometry.Offset(innerW, yAtMax), 4f)
                         }
                    }
                }

                drawContext.canvas.restore()
            }

            // Points (drawn AFTER restricting canvas clip so points don't overflow graph lines!)
            sorted.forEachIndexed { idx, _ ->
                val x = xPositions.getOrNull(idx) ?: return@forEachIndexed
                val y = yPositions.getOrNull(idx) ?: return@forEachIndexed
                
                // Skip drawing points outside the canvas width bounds to fix glitch where dot flies off graph edge
                if (x < 0 || x > innerW) return@forEachIndexed
                
                val isSelected = selectedIndex == idx
                if (isSelected) {
                    drawCircle(color = selectedColor, radius = 11f, center = androidx.compose.ui.geometry.Offset(x, y), style = Stroke(width = 4f))
                } else {
                    drawCircle(color = strokeColor, radius = 8.5f, center = androidx.compose.ui.geometry.Offset(x, y))
                }
            }
        }

        // Y-axis labels
        Column(
            Modifier
                .fillMaxHeight()
                .width(48.dp)
                .padding(bottom = 40.dp, top = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            tickValues.sortedByDescending { it }.forEach { tv ->
                Text(String.format(java.util.Locale.US, "%.0f", tv), fontSize = 11.sp, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            }
        }

        // X-AXIS LABELS
        val paddingStartForAxis = 48.dp

        if (rangeType == ProgressRange.WEEK) {
            // Draw day labels for the week
            val days = daysBetween(minDate, maxDate).toInt()
            for (i in 0..days) {
                val d = minDate.plusDays(i)
                val label = DateFormatter.format(d, dayFormatter) // Mon, Tue...
                val fraction = i.toFloat() / days.coerceAtLeast(1).toFloat()

                // Position logic using box with weight or constraint
                // Since this is a Box, we calculate offset
                // Width of chart area is approximated by constraints or we used weight in Canvas.
                // We don't have direct pixel width here outside Canvas easily without BoxWithConstraints scope (which we have).
                // innerW from canvas is roughly maxWidth - padding.

                val innerW_approx = maxWidth.value * LocalDensity.current.density - with(LocalDensity.current) { paddingStartForAxis.toPx() + 16.dp.toPx() } // Start padding + End padding
                val px = fraction * innerW_approx
                val xDp = with(LocalDensity.current) { px.toDp() } + paddingStartForAxis

                 Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = xDp - 15.dp, y = (-4).dp)
                        .width(30.dp)
                ) {
                    Text(label, fontSize = 10.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
             // Draw Month labels at the START of the month (aligned with grid lines)
            var current = minDate.withDayOfMonth(1)
            // If minDate is mid-month, the first "start of month" is next month.
            // BUT we might want to label the current month at minDate position if it's the start?
            // Let's just iterate months that fall within range.

            // Should we show the label for the first visible month even if start is cut off?
            // User requested: "Added vertical full lines... at x-positions of the start of each month... similar to dashed grid."
            // Labels usually go between lines or at lines.
            // Let's put label next to the line.

            val totalDays = daysBetween(minDate, maxDate).coerceAtLeast(1)
            val innerW_approx = maxWidth.value * LocalDensity.current.density - with(LocalDensity.current) { paddingStartForAxis.toPx() + 16.dp.toPx() }

            if (current.isBefore(minDate)) current = current.plusMonths(1)

            while (!current.isAfter(maxDate)) {
                 val label = DateFormatter.format(current, monthFormatter)
                 val daysFromStart = daysBetween(minDate, current).toFloat()
                 val fraction = daysFromStart / totalDays.toFloat()
                 val px = fraction * innerW_approx
                 val xDp = with(LocalDensity.current) { px.toDp() } + paddingStartForAxis

                 // Shift label slightly to right of line
                 Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = xDp + 4.dp, y = (-4).dp)
                        .width(40.dp)
                ) {
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
                }
                current = current.plusMonths(1)
            }

            // Also label the very first month if we started in middle of it?
            if (minDate.dayOfMonth > 1) {
                 val label = DateFormatter.format(minDate, monthFormatter)
                 Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = paddingStartForAxis, y = (-4).dp)
                        .width(40.dp)
                ) {
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
                }
            }
        }

        // Tooltip
        selectedIndex?.let { idx ->
            if (idx in sorted.indices) {
                val value = sorted[idx].second
                val date = sorted[idx].first
                val x = xPositions[idx]
                val y = yPositions[idx]
                val tooltipWidthDp = 150.dp
                val tooltipHeightDp = 80.dp
                val margin = 8.dp
                val xDpRaw = with(LocalDensity.current) { x.toDp() }
                val yDpRaw = with(LocalDensity.current) { y.toDp() }
                var xDp = xDpRaw - (tooltipWidthDp / 2)
                var yDp = yDpRaw - tooltipHeightDp - 12.dp
                val minX = margin
                val maxX = maxW - tooltipWidthDp - margin
                val minY = margin
                val maxY = maxH - tooltipHeightDp - margin
                if (xDp < minX) xDp = minX
                if (xDp > maxX) xDp = maxX
                if (yDp < minY) yDp = yDpRaw + 12.dp
                if (yDp > maxY) yDp = maxY

                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { selectedIndex = null }) }) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.offset(x = xDp, y = yDp).width(tooltipWidthDp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(DateFormatter.format(date, dateFormatter), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val formatPattern = if (unit == "kg") "%.1f" else "%.0f"
                            Text("${String.format(java.util.Locale.US, formatPattern, value)} $unit", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightEntryDialog(uid: String, weightUnit: String, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val progressViewModel: com.example.myapplication.viewmodels.ProgressViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = com.example.myapplication.ui.screens.MyViewModelFactory(context)
    )
    val scope = rememberCoroutineScope() // DODANO: Proper scope namesto GlobalScope
    val isLbs = weightUnit == "lb"
    var weightInput by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val db = Firebase.firestore
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Add Weight (${if (isLbs) "lb" else "kg"})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weightInput,
                    onValueChange = {
                        val filtered = it.filter { c -> c.isDigit() || c == '.' }.take(6)
                        if (filtered != weightInput && filtered.isNotEmpty()) {
                            HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.LIGHT_CLICK)
                        }
                        weightInput = filtered
                    },
                    placeholder = { Text(if (isLbs) "e.g. 165.5 lb" else "e.g. 74.2 kg") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text("Date: ${today}", fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && weightInput.isNotBlank(),
                onClick = {
                    val inputVal = weightInput.toDoubleOrNull() ?: return@TextButton
                    HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.CLICK)
                    saving = true
                    val dateStr = today.toString()

                    // Convert to kg for storage if input is lbs
                    val wKg = if (isLbs) inputVal / 2.20462 else inputVal

                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            progressViewModel.saveWeightLog(uid, dateStr, wKg) {}
                            Log.d("ProgressScreen", "Saved weight $wKg kg to weightLogs")

                            progressViewModel.awardWeightLogXP()
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(context, context.getString(com.example.myapplication.R.string.toast_xp_earned), android.widget.Toast.LENGTH_SHORT).show()
                            }

                            // Avtomatsko posodobi nutrition plan z novo težo
                            // PII varnost: uid in telesna teža se NE izpisujeta v log
                            Log.d("ProgressScreen", "🔥 Starting nutrition plan recalculation")
                            val success = com.example.myapplication.persistence.NutritionPlanStore.recalculateNutritionPlan(
                                uid, wKg
                            )
                            Log.d("ProgressScreen", "🔥 Recalculation result: $success")
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (success) {
                                    android.widget.Toast.makeText(context, context.getString(com.example.myapplication.R.string.toast_nutrition_plan_updated), android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    android.widget.Toast.makeText(context, context.getString(com.example.myapplication.R.string.toast_missing_plan_data), android.widget.Toast.LENGTH_LONG).show()
                                }
                                onSaved()
                                onDismiss()
                            }
                        } catch (e: Exception) {
                            Log.e("ProgressScreen", "🔥 ERROR updating nutrition plan", e)
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "❌ ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                saving = false
                            }
                        }
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = {
                if (!saving) {
                    HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.LIGHT_CLICK)
                    onDismiss()
                }
            }) { Text("Cancel") }
        }
    )
}

@Suppress("UNCHECKED_CAST")
private fun <T> filterRange(list: List<T>, range: ProgressRange): List<T> where T : Any {
    if (list.isEmpty()) return emptyList()
    if (range == ProgressRange.ALL) return list

    val lastDate: LocalDate = when {
        list.firstOrNull() is DailyLogSummary -> (list as List<DailyLogSummary>).maxOfOrNull { it.date }
        list.firstOrNull() is WeightLog -> (list as List<WeightLog>).maxOfOrNull { it.date }
        list.firstOrNull() is Pair<*, *> -> {
            val pairs = list as List<Pair<*, *>>
            pairs.mapNotNull { (it.first as? LocalDate) }.maxOrNull()
        }
        else -> null
    } ?: return list

    val threshold = when (range) {
        ProgressRange.WEEK -> lastDate.minusDays(6)
        ProgressRange.MONTH -> lastDate.minusMonths(1)
        ProgressRange.YEAR -> lastDate.minusYears(1)
        else -> lastDate // Should ideally not happen due to early return
    }

    return list.filter { item ->
        when (item) {
            is DailyLogSummary -> item.date >= threshold
            is WeightLog -> item.date >= threshold
            is Pair<*, *> -> {
                val date = item.first as? LocalDate
                date != null && date >= threshold
            }
            else -> true
        }
    }
}

// ── Faza 7: Weight Predictor ──────────────────────────────────────────────────

/**
 * Podatki za prikaz napovedi teže v UI kartici.
 */
private data class WeightPredictionDisplay(
    val emaWeightKg: Double,
    val avgDailyBalanceKcal: Double,
    val predictedWeightIn30Days: Double,
    val goalWeightKg: Double?,         // null = ni cilja nastavljenega
    val daysToGoal: Int?,              // null = ni dosegljivo
    val goalDateStr: String?,          // npr. "15 Jul 2026"
    val activeDaysInLastWeek: Int,
    val confidenceFactor: Double = 0.0 // C ∈ {0.0, 0.5, 1.0}
)

/**
 * Izračuna napoved teže iz obstoječe podatkovne baze stranke.
 * Pokliče se v remember() bloku — samo ob spremembi podatkov.
 */
private fun computeWeightPrediction(
    weightLogs: List<WeightLog>,
    dailyLogs: List<DailyLogSummary>,
    burnedByDay: List<Pair<LocalDate, Double>>,
    userProfile: UserProfile
): WeightPredictionDisplay? {
    if (weightLogs.isEmpty()) return null

    // ── EMA teže (7-dnevno okno) — delegira na utils/NutritionCalculations.kt ──
    val sortedWeights = weightLogs.sortedBy { it.date }.map { it.weightKg }
    val emaWeightKg = calculateEMA(sortedWeights, period = 7)

    // ── Povprečni kalorični balans zadnjih 7 dni ──────────────────────────
    val today = LocalDate.now()
    val sevenDaysAgo = today.minusDays(6)
    val burnedMap = burnedByDay.associate { it.first to it.second }
    val last7Days = dailyLogs.filter { it.date >= sevenDaysAgo && it.date <= today }
    val activeDaysWithData = last7Days.filter { it.calories > 0.0 }

    if (activeDaysWithData.isEmpty()) return null

    // ── Effective TDEE: hybridTDEE (from previous run) or theoretical fallback ──
    // Formula: balance = calories_consumed − TDEE  (negative = deficit → weight loss)
    val theoreticalTDEEEarly = (com.example.myapplication.debug.NutritionDebugStore.lastBmr * 1.2).toInt()
    val prevHybridTDEE = com.example.myapplication.debug.WeightPredictorStore.lastHybridTDEE
    val effectiveTDEE: Double = when {
        prevHybridTDEE > 800 -> prevHybridTDEE.toDouble()
        theoreticalTDEEEarly > 800 -> theoreticalTDEEEarly.toDouble()
        else -> 2000.0 // safe default if no profile data yet
    }

    val avgDailyBalance = activeDaysWithData.map { log ->
        // Formula: consumed - (TDEE + exerciseBurned)
        // burnedMap vsebuje Health Connect podatke za ta dan (iz dailyLogs.burnedCalories)
        val dayBurned = burnedMap[log.date] ?: 0.0
        log.calories - effectiveTDEE - dayBurned
    }.average()

    // ── Napoved za 30 dni: 7700 kcal ≈ 1 kg ─────────────────────────────
    val predictedChangeIn30Days = (avgDailyBalance * 30.0) / 7700.0
    val predictedWeightIn30Days = emaWeightKg + predictedChangeIn30Days

    // ── Datum dosega cilja ────────────────────────────────────────────────
    val goalWeightKg = userProfile.goalWeightKg
    val daysToGoal: Int?
    val goalDateStr: String?

    if (goalWeightKg != null && goalWeightKg > 0.0 && avgDailyBalance != 0.0) {
        val kgDiff = goalWeightKg - emaWeightKg
        val dailyKgChange = avgDailyBalance / 7700.0
        val correctDirection = (kgDiff < 0.0 && dailyKgChange < 0.0) || (kgDiff > 0.0 && dailyKgChange > 0.0)
        if (correctDirection && abs(dailyKgChange) > 0.00001) {
            val days = (kgDiff / dailyKgChange).toInt().coerceIn(1, 3650)
            daysToGoal = days
            val goalDate = today.plusDays(days)
            goalDateStr = "${goalDate.dayOfMonth} ${monthName(goalDate.monthNumber)} ${goalDate.year}"
        } else {
            daysToGoal = null
            goalDateStr = null
        }
    } else {
        daysToGoal = null
        goalDateStr = null
    }

    // 5. Shrani v WeightPredictorStore za Debug Dashboard
    val prevEmaWeightKg = if (sortedWeights.size >= 2)
        calculateEMA(sortedWeights.dropLast(1), period = 7)
    else
        emaWeightKg
    // Teoretični TDEE = BMR × 1.2 iz zadnjega nalaganja profila (NutritionDebugStore)
    val theoreticalTDEE = theoreticalTDEEEarly // reuse already-computed value
    val tdeeResult = calculateAdaptiveTDEE(
        last7DaysCalories = activeDaysWithData.map { it.calories.toInt() },
        emaWeightChangeDelta = emaWeightKg - prevEmaWeightKg,
        theoreticalTDEE = theoreticalTDEE
    )
    com.example.myapplication.debug.WeightPredictorStore.lastEmaWeightKg = emaWeightKg
    com.example.myapplication.debug.WeightPredictorStore.lastAvgDailyBalanceKcal = avgDailyBalance
    com.example.myapplication.debug.WeightPredictorStore.last30DayPredictionKg = predictedWeightIn30Days
    com.example.myapplication.debug.WeightPredictorStore.lastGoalWeightKg = goalWeightKg
    com.example.myapplication.debug.WeightPredictorStore.lastGoalDateStr = goalDateStr
    com.example.myapplication.debug.WeightPredictorStore.lastDaysToGoal = daysToGoal
    com.example.myapplication.debug.WeightPredictorStore.lastActiveDaysCount = activeDaysWithData.size
    com.example.myapplication.debug.WeightPredictorStore.lastHybridTDEE = tdeeResult.hybridTDEE
    com.example.myapplication.debug.WeightPredictorStore.lastAdaptiveTDEE = tdeeResult.adaptiveTDEE
    com.example.myapplication.debug.WeightPredictorStore.lastConfidenceFactor = tdeeResult.confidenceFactor
    com.example.myapplication.debug.WeightPredictorStore.isReady = true

    return WeightPredictionDisplay(
        emaWeightKg = emaWeightKg,
        avgDailyBalanceKcal = avgDailyBalance,
        predictedWeightIn30Days = predictedWeightIn30Days,
        goalWeightKg = goalWeightKg,
        daysToGoal = daysToGoal,
        goalDateStr = goalDateStr,
        activeDaysInLastWeek = activeDaysWithData.size,
        confidenceFactor = when {
            activeDaysWithData.size < 3 -> 0.0
            activeDaysWithData.size <= 5 -> 0.5
            else -> 1.0
        }
    )
}

private fun monthName(month: Int): String = when (month) {
    1 -> "Jan"; 2 -> "Feb"; 3 -> "Mar"; 4 -> "Apr"; 5 -> "May"; 6 -> "Jun"
    7 -> "Jul"; 8 -> "Aug"; 9 -> "Sep"; 10 -> "Oct"; 11 -> "Nov"; else -> "Dec"
}

/**
 * 🔮 Weight Destiny — motivacijska kartica z vizualnim trendom in What-if simulatorjem.
 * Nadomešča staro WeightPredictionCard (Faza 7.1).
 */
@Composable
private fun WeightDestinyCard(
    prediction: WeightPredictionDisplay,
    isLbs: Boolean
) {
    val convert: (Double) -> Double = { if (isLbs) it * 2.20462 else it }
    val unit = if (isLbs) "lb" else "kg"
    val balance = prediction.avgDailyBalanceKcal
    val confidence = prediction.confidenceFactor

    val trendColor = when {
        balance < -50 -> Color(0xFF10B981)
        balance > 50  -> Color(0xFFE11D48)
        else          -> Color(0xFF6B7280)
    }

    // Dynamic message based on C (confidence) and trend
    val (msgEmoji, msgText) = when {
        confidence < 0.5 ->
            "🧪" to "Learning your metabolism… (${prediction.activeDaysInLastWeek}/7 days of data)"
        balance < -50 && prediction.goalDateStr != null ->
            "🎯" to "Great progress! Predicted goal date: ${prediction.goalDateStr}"
        balance < -50 ->
            "✅" to "You're on track! Keep up this pace."
        balance > 50 && prediction.goalDateStr != null ->
            "📈" to "Gaining mass. Predicted goal date: ${prediction.goalDateStr}"
        balance > 50 ->
            "📈" to "You are gaining body mass. Adjust nutrition to match your goal."
        else ->
            "⚖️" to "You are in energy balance. Keep it up!"
    }

    // What-if stanje
    var whatIfDelta by remember { mutableStateOf(0f) }
    val adjustedBalance = balance + whatIfDelta
    val whatIfDaysToGoal: Int? = prediction.goalWeightKg?.let { goalKg ->
        if (goalKg <= 0.0) return@let null
        val kgDiff = goalKg - prediction.emaWeightKg
        val adjKgPerDay = adjustedBalance / 7700.0
        val correct = (kgDiff < 0.0 && adjKgPerDay < 0.0) || (kgDiff > 0.0 && adjKgPerDay > 0.0)
        if (correct && abs(adjKgPerDay) > 0.00001) (kgDiff / adjKgPerDay).toInt().coerceIn(1, 3650) else null
    }
    val daysDelta: Int? = if (whatIfDaysToGoal != null && prediction.daysToGoal != null)
        prediction.daysToGoal - whatIfDaysToGoal else null

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Header ──────────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔮", fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Weight Destiny",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                ConfidenceIndicator(confidence = confidence)
            }

            // ── Dinamično sporočilo ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(trendColor.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(msgEmoji, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    msgText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Vizualizacija trenda ─────────────────────────────────────────────
            WeightTrendLine(
                currentWeightKg  = convert(prediction.emaWeightKg),
                projectedWeightKg = convert(prediction.predictedWeightIn30Days),
                goalWeightKg     = prediction.goalWeightKg?.let { convert(it) },
                trendColor       = trendColor,
                isDashed         = confidence < 0.5,
                unit             = unit
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── What-if Simulator ────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎲", fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "What-if Simulator",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                    val deltaLabel = when {
                        whatIfDelta > 0f  -> "+${whatIfDelta.toInt()} kcal/dan"
                        whatIfDelta < 0f  -> "${whatIfDelta.toInt()} kcal/dan"
                        else              -> "0 kcal (zdaj)"
                    }
                    Text(
                        deltaLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            whatIfDelta < 0f -> Color(0xFF10B981)
                            whatIfDelta > 0f -> Color(0xFFE11D48)
                            else             -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Slider(
                    value = whatIfDelta,
                    onValueChange = { whatIfDelta = (it / 50f).toInt() * 50f },
                    valueRange = -500f..500f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = trendColor,
                        activeTrackColor = trendColor,
                        inactiveTrackColor = trendColor.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Rezultat what-if
                val whatIfMsg = when {
                    whatIfDelta == 0f ->
                        "← Premakni drsnik za simulacijo scenarija"
                    daysDelta != null && daysDelta > 0 -> {
                        val goalStr = prediction.goalWeightKg?.let {
                            String.format(Locale.US, "%.1f %s", convert(it), unit)
                        } ?: ""
                        "✅ Cilj $goalStr bi dosegel $daysDelta dni prej!"
                    }
                    daysDelta != null && daysDelta < 0 ->
                        "⚠️ Cilj bi dosegel ${-daysDelta} dni pozneje."
                    whatIfDaysToGoal != null ->
                        "🎯 Nov čas do cilja: ~$whatIfDaysToGoal dni"
                    else -> {
                        val adj30 = prediction.emaWeightKg + (adjustedBalance * 30.0) / 7700.0
                        "📊 Čez 30 dni: ${String.format(Locale.US, "%.1f %s", convert(adj30), unit)}"
                    }
                }
                Text(
                    whatIfMsg,
                    fontSize = 12.sp,
                    color = when {
                        whatIfDelta == 0f                       -> MaterialTheme.colorScheme.onSurfaceVariant
                        daysDelta != null && daysDelta > 0      -> Color(0xFF10B981)
                        daysDelta != null && daysDelta < 0      -> Color(0xFFE11D48)
                        else                                    -> MaterialTheme.colorScheme.onSurface
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )
            }
        }
    }
}

/**
 * Vizualni trendni graf: dve točki (zdaj → čez 30 dni) + ciljna linija.
 */
@Composable
private fun WeightTrendLine(
    currentWeightKg: Double,
    projectedWeightKg: Double,
    goalWeightKg: Double?,
    trendColor: Color,
    isDashed: Boolean,
    unit: String
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val goalColor  = Color(0xFFF59E0B)
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            val w = size.width
            val h = size.height
            val padH = 36f
            val padV = 16f

            val allW = listOfNotNull(currentWeightKg, projectedWeightKg, goalWeightKg)
            val minW = (allW.min() - 2.5)
            val maxW = (allW.max() + 2.5)
            val range = (maxW - minW).coerceAtLeast(0.1)

            fun wy(kg: Double): Float =
                (h - padV - ((kg - minW) / range * (h - 2 * padV))).toFloat()

            val startX = padH
            val endX   = w - padH
            val startY = wy(currentWeightKg)
            val endY   = wy(projectedWeightKg)
            val midX   = (startX + endX) / 2f
            val midY   = wy((currentWeightKg + projectedWeightKg) / 2.0)

            // Horizontalna pomožna linija (sredina razpona)
            val midRangeY = wy((minW + maxW) / 2.0)
            drawLine(
                color = androidx.compose.ui.graphics.Color.Gray.copy(alpha = 0.12f),
                start = Offset(padH, midRangeY),
                end   = Offset(w - padH, midRangeY),
                strokeWidth = 1f
            )

            // Ciljna linija (dashed, rumena)
            if (goalWeightKg != null) {
                val goalY = wy(goalWeightKg)
                drawLine(
                    color = goalColor.copy(alpha = 0.55f),
                    start = Offset(padH, goalY),
                    end   = Offset(w - padH, goalY),
                    strokeWidth = 1.8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))
                )
            }

            // Senčni trak pod glavno linijo
            val shadowPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(startX, startY)
                quadraticBezierTo(midX, midY, endX, endY)
                lineTo(endX, h)
                lineTo(startX, h)
                close()
            }
            drawPath(
                path = shadowPath,
                color = trendColor.copy(alpha = 0.07f)
            )

            // Glavna bezier krivulja
            val mainPathEffect = if (isDashed) PathEffect.dashPathEffect(floatArrayOf(14f, 9f)) else null
            val mainPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(startX, startY)
                quadraticBezierTo(midX, midY, endX, endY)
            }
            drawPath(
                path = mainPath,
                color = trendColor,
                style = Stroke(width = 3.2f, cap = StrokeCap.Round, pathEffect = mainPathEffect)
            )

            // Točki na robovih
            // Leva (zdaj)
            drawCircle(color = trendColor,              radius = 9f,  center = Offset(startX, startY))
            drawCircle(color = surfaceColor,            radius = 5f,  center = Offset(startX, startY))
            // Desna (30 dni)
            drawCircle(color = trendColor.copy(alpha = 0.7f), radius = 9f,  center = Offset(endX, endY))
            drawCircle(color = surfaceColor,            radius = 5f,  center = Offset(endX, endY))
        }

        // Oznake pod grafom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text("Zdaj (EMA)", fontSize = 10.sp, color = labelColor)
                Text(
                    String.format(Locale.US, "%.1f %s", currentWeightKg, unit),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
            if (goalWeightKg != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎯 Cilj", fontSize = 10.sp, color = goalColor)
                    Text(
                        String.format(Locale.US, "%.1f %s", goalWeightKg, unit),
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = goalColor
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Čez 30 dni", fontSize = 10.sp, color = labelColor)
                Text(
                    String.format(Locale.US, "%.1f %s", projectedWeightKg, unit),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Tri pike, ki vizualno kažejo zaupanje v napoved (C=0.0/0.5/1.0).
 */
@Composable
private fun ConfidenceIndicator(confidence: Double) {
    val activeHigh   = Color(0xFF10B981)
    val activeMid    = Color(0xFFF59E0B)
    val activeLow    = Color(0xFF9CA3AF)
    val inactive     = Color(0xFFE5E7EB)

    val dotColors = listOf(
        if (confidence >= 0.5) activeMid else if (confidence > 0.0) activeLow else inactive,
        if (confidence >= 0.5) activeMid else inactive,
        if (confidence >= 1.0) activeHigh else inactive
    )
    val label = when {
        confidence >= 1.0 -> "Visoko zaupanje"
        confidence >= 0.5 -> "Srednje zaupanje"
        else              -> "Malo podatkov"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, fontSize = 9.sp, color = dotColors.last())
        Spacer(Modifier.width(2.dp))
        dotColors.forEach { c ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(c, RoundedCornerShape(50))
            )
        }
    }
}

