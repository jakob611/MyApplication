package com.example.myapplication.ui.screens

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import com.example.myapplication.utils.HapticFeedback
import java.util.Locale
import java.time.temporal.ChronoUnit
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

@Composable
fun ProgressScreen(
    openWeightInput: Boolean = false,
    userProfile: UserProfile = UserProfile()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope() // DODANO: Proper coroutine scope namesto GlobalScope
    val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
    var dailyLogs by remember { mutableStateOf<List<DailyLogSummary>>(emptyList()) }
    var weightLogs by remember { mutableStateOf<List<WeightLog>>(emptyList()) }
    var burnedByDay by remember { mutableStateOf<List<Pair<LocalDate, Double>>>(emptyList()) } // ADDED
    var loading by remember { mutableStateOf(true) }
    var range by rememberSaveable { mutableStateOf(ProgressRange.WEEK) }
    var showWeightDialog by remember { mutableStateOf(openWeightInput) }
    var xpPopupAmount by remember { mutableStateOf(0) }
    var showXpPopup by remember { mutableStateOf(false) }

    LaunchedEffect(openWeightInput) {
        if (openWeightInput) {
            showWeightDialog = true
        }
    }
    var dailyListener: ListenerRegistration? by remember { mutableStateOf(null) }
    var weightListener: ListenerRegistration? by remember { mutableStateOf(null) }
    var sessionListener: ListenerRegistration? by remember { mutableStateOf(null) } // ADDED
    // No persistence for range while fixing build issues

    DisposableEffect(uid) {
        if (uid != null) {
            val db = Firebase.firestore
            loading = true
            weightListener = db.collection("users").document(uid).collection("weightLogs")
                .addSnapshotListener { snap, _ ->
                    weightLogs = snap?.documents?.mapNotNull { d ->
                        val dateStr = d.getString("date") ?: d.id
                        val w = (d.get("weightKg") as? Number)?.toDouble() ?: return@mapNotNull null
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrElse { return@mapNotNull null }
                        WeightLog(date, w)
                    }?.sortedBy { it.date } ?: emptyList()
                }
            dailyListener = db.collection("users").document(uid).collection("dailyLogs")
                .addSnapshotListener { snap, _ ->
                    dailyLogs = snap?.documents?.mapNotNull { d ->
                        val dateStr = d.getString("date") ?: d.id
                        val items = d.get("items") as? List<*> ?: emptyList<Any>()
                        val caloriesTotal = items.sumOf { any ->
                            val m = any as? Map<*, *> ?: return@sumOf 0.0
                            (m["caloriesKcal"] as? Number)?.toDouble() ?: 0.0
                        }
                        val water = (d.get("waterMl") as? Number)?.toInt() ?: 0
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrElse { return@mapNotNull null }
                        DailyLogSummary(date, caloriesTotal, water)
                    }?.sortedBy { it.date } ?: emptyList()
                    loading = false
                }

            // Listen to daily_health for burned calories
            sessionListener = db.collection("users").document(uid).collection("daily_health")
                .addSnapshotListener { snap, _ ->
                    val burnedMap = mutableMapOf<LocalDate, Double>()
                    snap?.documents?.forEach { d ->
                        val dateStr = d.getString("date") ?: d.id
                        val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: return@forEach
                        val kcal = (d.get("calories") as? Number)?.toDouble() ?: 0.0
                        if (kcal > 0) {
                            burnedMap[date] = kcal
                        }
                    }
                    burnedByDay = burnedMap.entries.sortedBy { it.key }.map { it.key to it.value }
                }
        }
        onDispose {
            dailyListener?.remove(); weightListener?.remove(); sessionListener?.remove()
        }
    }

    val weightPairs = remember(weightLogs) { weightLogs.sortedBy { it.date }.map { it.date to it.weightKg } }
    val dailyPairs = remember(dailyLogs) { dailyLogs.sortedBy { it.date }.map { it.date to it.calories } }
    val waterPairs = remember(dailyLogs) { dailyLogs.sortedBy { it.date }.map { it.date to it.waterMl.toDouble() } }
    val burnedPairs = remember(burnedByDay) { burnedByDay.sortedBy { it.first } }

    val filteredDaily = remember(dailyLogs, range) { filterRange(dailyLogs, range) }
    val filteredWeight = remember(weightLogs, range) { filterRange(weightLogs, range) }
    val filteredBurned = remember(burnedByDay, range) {
        val list = burnedByDay.map { Pair(it.first, it.second) }
        filterRange(list, range)
    }
    val filteredWater = remember(dailyLogs, range) { filterRange(dailyLogs, range) }

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
        // WEEK: always show 7 days ending at maxDate (or today if empty)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp)
        ) {
            item { Text("PROGRESS", fontWeight = FontWeight.Bold, fontSize = 22.sp) }
            item {
                RangeSelector(range = range, onSelect = { range = it })
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
                        ChartSectionWithAxis(
                            title = if (isLbs) "Weight (lb)" else "Weight (kg)",
                            unit = if (isLbs) "lb" else "kg",
                            color = Color(0xFF2563EB),
                            points = displayPoints,
                            addWeightButton = if (uid != null) { { showWeightDialog = true } } else null,
                            edgePrev = weightNeighbors.prev?.let { it.first to if (isLbs) it.second * 2.20462 else it.second },
                            edgeNext = weightNeighbors.next?.let { it.first to if (isLbs) it.second * 2.20462 else it.second },
                            minDate = viewMinDate,
                            maxDate = viewMaxDate,
                            rangeType = range
                        )
                    }
                    item {
                        ChartSectionWithAxis(
                            title = "Caloric intake", unit = "kcal", color = Color(0xFFF97316),
                            points = zeroFilledDaily,
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
                            Icon(Icons.Filled.Add, contentDescription = "Add weight")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Even if points are empty, we might want to show the grid if we have a valid range?
                // But usually "No data" is better if literally no data points in this graph.
                if (points.isEmpty()) {
                     // Note: You can choose to show empty chart with grid instead.
                     // For now, let's keep "No data" but you could simply call InteractiveLineChart with empty list
                     // if you want to see the synchronized axis.
                     // The user requested "Uskladi x-os... plotaj točke samo kjer obstajajo", which implies showing charts even if sparse.
                     // But if *no* points exist for a specific metric:
                    Text("No data", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    InteractiveLineChart(
                        points = points,
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
    val values = remember(sorted) { sorted.map { it.second } }
    val minV = remember(values) { values.minOrNull() ?: 0.0 }
    val maxV = remember(values) { values.maxOrNull() ?: 0.0 }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH) }
    val monthFormatter = remember { DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH) }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH) } // For Week view
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

            val totalDays = ChronoUnit.DAYS.between(minDate, maxDate).coerceAtLeast(1)

            // Compute xPositions for data points based on global minDate/maxDate
            xPositions = sorted.map { (d, _) ->
                val daysFromStart = ChronoUnit.DAYS.between(minDate, d).toFloat()
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
                    val daysFromStart = ChronoUnit.DAYS.between(minDate, current).toFloat()
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
                 drawContext.canvas.clipRect(0f, 0f, innerW, innerH)

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
                    val dxPrev = ChronoUnit.DAYS.between(minDate, prev.first).toFloat()
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
                    val dxNext = ChronoUnit.DAYS.between(minDate, next.first).toFloat()
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

            // Points
            sorted.forEachIndexed { idx, _ ->
                val x = xPositions.getOrNull(idx) ?: return@forEachIndexed
                val y = yPositions.getOrNull(idx) ?: return@forEachIndexed
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
            val days = ChronoUnit.DAYS.between(minDate, maxDate).toInt()
            for (i in 0..days) {
                val d = minDate.plusDays(i.toLong())
                val label = dayFormatter.format(d) // Mon, Tue...
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
                        .offset(x = xDp - 15.dp, y = 0.dp)
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

            val totalDays = ChronoUnit.DAYS.between(minDate, maxDate).coerceAtLeast(1)
            val innerW_approx = maxWidth.value * LocalDensity.current.density - with(LocalDensity.current) { paddingStartForAxis.toPx() + 16.dp.toPx() }

            if (current.isBefore(minDate)) current = current.plusMonths(1)

            while (!current.isAfter(maxDate)) {
                 val label = monthFormatter.format(current)
                 val daysFromStart = ChronoUnit.DAYS.between(minDate, current).toFloat()
                 val fraction = daysFromStart / totalDays.toFloat()
                 val px = fraction * innerW_approx
                 val xDp = with(LocalDensity.current) { px.toDp() } + paddingStartForAxis

                 // Shift label slightly to right of line
                 Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = xDp + 4.dp, y = 0.dp)
                        .width(40.dp)
                ) {
                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
                }
                current = current.plusMonths(1)
            }

            // Also label the very first month if we started in middle of it?
            if (minDate.dayOfMonth > 1) {
                 val label = monthFormatter.format(minDate)
                 Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = paddingStartForAxis, y = 0.dp)
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
                            Text(dateFormatter.format(date), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

                    db.collection("users").document(uid).collection("weightLogs").document(dateStr)
                        .set(mapOf("date" to dateStr, "weightKg" to wKg))
                        .addOnSuccessListener {
                            Log.d("ProgressScreen", "Saved weight $wKg kg to weightLogs")

                            // AchievementStore.awardXP sproži badge preverjanje
                            val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: return@addOnSuccessListener
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.example.myapplication.persistence.AchievementStore.awardXP(
                                    context, userEmail, 50,
                                    com.example.myapplication.data.XPSource.WEIGHT_ENTRY, "Weight logged"
                                )
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "+50 XP Earned!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }

                            // Avtomatsko posodobi nutrition plan z novo težo - UPORABLJA rememberCoroutineScope
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    Log.d("ProgressScreen", "🔥 Starting nutrition plan recalculation for uid=$uid, weight=$wKg")
                                    val success = com.example.myapplication.persistence.NutritionPlanStore.recalculateNutritionPlan(
                                        uid, wKg
                                    )
                                    Log.d("ProgressScreen", "🔥 Recalculation result: $success")
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        if (success) {
                                            android.widget.Toast.makeText(context, "✅ Nutrition plan updated!", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            // POMEMBNO: Prikaži error uporabniku!
                                            android.widget.Toast.makeText(context, "⚠️ Missing plan data - please create a plan first", android.widget.Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("ProgressScreen", "🔥 ERROR updating nutrition plan", e)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "❌ Failed to update nutrition plan: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            }

                            db.collection("users").document(uid).collection("dailyMetrics").document(dateStr)
                                .set(mapOf("date" to dateStr, "weight" to wKg.toFloat()), com.google.firebase.firestore.SetOptions.merge())
                                .addOnSuccessListener {
                                    Log.d("ProgressScreen", "Saved weight $wKg to dailyMetrics")
                                    HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.SUCCESS)
                                    com.example.myapplication.widget.WeightWidgetProvider.updateWidgetFromApp(context, wKg.toFloat())
                                    onSaved()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("ProgressScreen", "Failed to save to dailyMetrics", e)
                                    HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.ERROR)
                                    onSaved()
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("ProgressScreen", "Failed to save weight", e)
                            HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.ERROR)
                        }
                        .addOnCompleteListener { saving = false }
                }
            ) { Text(if (saving) "Saving..." else "Save") }
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
