package com.example.myapplication.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.ActivityType
import com.example.myapplication.data.RunSession
import com.example.myapplication.viewmodels.RunTrackerViewModel


import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.tasks.await

// ---------------------------------------------------------------
// Pomožna funkcija za razdaljo (Haversine)
// ---------------------------------------------------------------
internal fun haversineDist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val r = 6371e3 // meters
    val p1 = Math.toRadians(lat1)
    val p2 = Math.toRadians(lat2)
    val dp = Math.toRadians(lat2 - lat1)
    val dl = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dp / 2) * Math.sin(dp / 2) +
            Math.cos(p1) * Math.cos(p2) *
            Math.sin(dl / 2) * Math.sin(dl / 2)
    val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    return (r * c).toFloat()
}

// ---------------------------------------------------------------
// Barve po tipu aktivnosti
// ---------------------------------------------------------------
internal fun activityColor(type: ActivityType): Color = when (type) {
    ActivityType.RUN      -> Color(0xFF2196F3)
    ActivityType.WALK     -> Color(0xFF4CAF50)
    ActivityType.HIKE     -> Color(0xFF795548)
    ActivityType.SPRINT   -> Color(0xFFF44336)
    ActivityType.CYCLING  -> Color(0xFFFF9800)
    ActivityType.SKIING   -> Color(0xFF00BCD4)
    ActivityType.SNOWBOARD-> Color(0xFF9C27B0)
    ActivityType.SKATING  -> Color(0xFF00ACC1)
    ActivityType.NORDIC   -> Color(0xFF8BC34A)
}

@Composable
fun ActivityLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<RunTrackerViewModel>(
        factory = com.example.myapplication.ui.screens.MyViewModelFactory(context.applicationContext)
    )

    var runs by remember { mutableStateOf<List<RunSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var allRoutes by remember { mutableStateOf<Map<String, List<Pair<Double, Double>>>>(emptyMap()) }
    var allRawPoints by remember { mutableStateOf<Map<String, List<com.example.myapplication.data.LocationPoint>>>(emptyMap()) }
    var selectedRun by remember { mutableStateOf<RunSession?>(null) }
    var deleteCandidate by remember { mutableStateOf<RunSession?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun markRunAsSmoothed(runId: String) {
        val userRef = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
        Log.d("ActivityLog", "UPDATE isSmoothed doc=${userRef.id} runId=$runId")
        userRef.collection("runSessions").document(runId).update("isSmoothed", true).await()
    }

    // Samodejno glajenje po naknadni izbiri teka (ce prej ni bil zglajen in ni bilo povezave)
    LaunchedEffect(selectedRun) {
        val crun = selectedRun
        if (crun != null && !crun.isSmoothed && (crun.activityType == ActivityType.RUN || crun.activityType == ActivityType.WALK || crun.activityType == ActivityType.HIKE || crun.activityType == ActivityType.CYCLING)) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val currentPoints = allRawPoints[crun.id] ?: emptyList()
                if (currentPoints.isNotEmpty()) {
                    val isWalkingProfile = crun.activityType == ActivityType.RUN || crun.activityType == ActivityType.WALK || crun.activityType == ActivityType.HIKE
                    val finalPoints = com.example.myapplication.map.MapboxMapMatcher.matchRoute(currentPoints, isWalkingProfile)
                    if (finalPoints !== currentPoints) {
                        // Uspesno zglajeno, shranimo v _smoothed file, original ostane cel!
                        com.example.myapplication.persistence.RunRouteStore.saveRoute(context, "${crun.id}_smoothed", finalPoints)
                        runCatching { markRunAsSmoothed(crun.id) }
                            .onFailure { Log.w("ActivityLog", "Failed to mark isSmoothed for ${crun.id}", it) }
                        // Posodobimo lokalno stanje DVOJNO
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            // Ohranimo original v allRawPoints za grafe "hitrosti"!!!
                            // Popravimo samo "allRoutes" za OSM Mapo
                            allRoutes = allRoutes.toMutableMap().apply { put(crun.id, finalPoints.map { Pair(it.latitude, it.longitude) }) }
                            // Posodobimo selectedRun z novim flagom (za ta render)
                            val idx = runs.indexOfFirst { it.id == crun.id }
                            if (idx >= 0) {
                                val updatedRun = crun.copy(isSmoothed = true)
                                val mutRuns = runs.toMutableList()
                                mutRuns[idx] = updatedRun
                                runs = mutRuns
                                if (selectedRun?.id == crun.id) selectedRun = updatedRun
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadRunSessions { sessions ->
            runs = sessions
            loading = false

            val routesBuilder = mutableMapOf<String, List<Pair<Double, Double>>>()
            val rawBuilder = mutableMapOf<String, List<com.example.myapplication.data.LocationPoint>>()

            sessions.forEach { run ->
                if (run.id.isNotBlank()) {
                    var rawLoaded = com.example.myapplication.persistence.RunRouteStore.loadRoute(context, run.id)
                    if (rawLoaded.isNullOrEmpty() && run.polylinePoints.isNotEmpty()) {
                        rawLoaded = run.polylinePoints
                    }

                    // Tudi poskusimo naložiti dodaten `_smoothed` file!
                    val smoothedLoaded = com.example.myapplication.persistence.RunRouteStore.loadRoute(context, "${run.id}_smoothed")

                    if (!rawLoaded.isNullOrEmpty()) {
                        rawBuilder[run.id] = rawLoaded
                        // Če je tek `isSmoothed`, poženemo zglajene točke NA MAPI;
                        // Če ne, pa surove "raw". Za grafe hitrosti ostanejo "raw".
                        if (run.isSmoothed && !smoothedLoaded.isNullOrEmpty()) {
                            routesBuilder[run.id] = smoothedLoaded.map { Pair(it.latitude, it.longitude) }
                        } else {
                            routesBuilder[run.id] = rawLoaded.map { Pair(it.latitude, it.longitude) }
                        }
                    }
                }
            }
            allRoutes = routesBuilder
            allRawPoints = rawBuilder

            // Samodejno tiho zgladi VSE še nepo-glajene teke v ozadju, takoj ob nalaganju aktivnosti (če imamo internet)
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                sessions.forEach { run ->
                    if (!run.isSmoothed && (run.activityType == ActivityType.RUN || run.activityType == ActivityType.WALK || run.activityType == ActivityType.HIKE || run.activityType == ActivityType.CYCLING)) {
                        val currentPoints = rawBuilder[run.id] ?: emptyList()
                        if (currentPoints.size > 2) {
                            val isWalkingProfile = run.activityType == ActivityType.RUN || run.activityType == ActivityType.WALK || run.activityType == ActivityType.HIKE
                            val finalPoints = com.example.myapplication.map.MapboxMapMatcher.matchRoute(currentPoints, isWalkingProfile)
                            if (finalPoints !== currentPoints) {
                                com.example.myapplication.persistence.RunRouteStore.saveRoute(context, "${run.id}_smoothed", finalPoints)
                                runCatching { markRunAsSmoothed(run.id) }
                                    .onFailure { Log.w("ActivityLog", "Failed to mark isSmoothed for ${run.id}", it) }
                                withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    allRoutes = allRoutes.toMutableMap().apply { put(run.id, finalPoints.map { Pair(it.latitude, it.longitude) }) }
                                    // Posodobi seznam runs, da dobi kljukico wasSmoothed=true
                                    val idx = runs.indexOfFirst { it.id == run.id }
                                    if (idx >= 0) {
                                        val mutRuns = runs.toMutableList()
                                        mutRuns[idx] = run.copy(isSmoothed = true)
                                        runs = mutRuns
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (runs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🗺️", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No activities yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            FloatingBackButton(onBack)
        } else {
            GlobalActivityOsmMap(
                context = context,
                runs = runs,
                allRoutes = allRoutes,
                selectedRunId = selectedRun?.id,
                onRunSelected = { selectedRun = it },
                modifier = Modifier.fillMaxSize()
            )
            
            FloatingBackButton(onBack)

            // Vodoravni seznam tekov za lažje klikanje (če posnetki prekrivajo drug drugega na mapi)
            AnimatedVisibility(
                visible = selectedRun == null && runs.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
            ) {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(runs) { run ->
                        val color = activityColor(run.activityType)
                        
                        Card(
                            modifier = Modifier
                                .width(140.dp)
                                .shadow(4.dp, RoundedCornerShape(16.dp))
                                .clickable { selectedRun = run },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${run.activityType.emoji} ${run.activityType.label}", fontWeight = FontWeight.Bold, color = color)
                                Text(com.example.myapplication.domain.DateFormatter.formatEpoch(run.startTime, "dd MMM"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text("${"%.2f".format(run.getDistanceKm())} km", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selectedRun != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                selectedRun?.let { run ->
                    val color = activityColor(run.activityType)
                    val rawPoints = allRawPoints[run.id] ?: emptyList()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .fillMaxHeight(0.6f)
                            .shadow(8.dp, RoundedCornerShape(20.dp)),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(color.copy(alpha = 0.15f))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            Modifier
                                                .size(12.dp)
                                                .background(color, CircleShape)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "${run.activityType.emoji} ${run.activityType.label}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = color
                                        )
                                    }
                                    Row {
                                        IconButton(
                                            onClick = { deleteCandidate = run },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                        IconButton(
                                            onClick = { selectedRun = null },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            Column(Modifier.padding(16.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        com.example.myapplication.domain.DateFormatter.formatEpoch(run.startTime, "dd MMM"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${"%.2f".format(run.getDistanceKm())} km",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ActivityChip("⏱️ ${run.formatDuration()}", color)
                                    ActivityChip("🔥 ${run.caloriesKcal} kcal", Color(0xFFFF9800))
                                    if (run.activityType.showSpeed) {
                                        ActivityChip("${"%.1f".format(run.getAvgSpeedKmh())} km/h avg", color)
                                    }
                                }

                                if (rawPoints.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    HorizontalDivider()
                                    Spacer(Modifier.height(16.dp))

                                    val t0 = rawPoints.first().timestamp
                                    val tLast = rawPoints.last().timestamp
                                    val timeSpanSec = (tLast - t0) / 1000f

                                    // If recorded timestamps span less than 10% of total run duration, they are corrupted (e.g. old Mapbox snap points)
                                    val isBogus = timeSpanSec < run.durationSeconds * 0.1f

                                    val rawSpeedData = mutableListOf<Pair<Float, Float>>()
                                    var hasAltitudeData = false
                                    
                                    var lastPt = rawPoints.first()
                                    rawSpeedData.add(Pair(0f, 0f))
                                    if (lastPt.altitude != 0.0) hasAltitudeData = true

                                    for (i in 1 until rawPoints.size) {
                                        val pt = rawPoints[i]
                                        val distM = haversineDist(lastPt.latitude, lastPt.longitude, pt.latitude, pt.longitude)

                                        var timeOffsetSec = if (isBogus && run.durationSeconds > 0) {
                                            // Distribute points evenly over the total recorded duration to stretch X-axis properly
                                            // This preserves the mapbox "wiggly" artificial speed variations without causing a flat graph
                                            (i.toFloat() / rawPoints.size) * run.durationSeconds.toFloat()
                                        } else {
                                            (pt.timestamp - t0) / 1000f
                                        }

                                        if (timeOffsetSec <= rawSpeedData.last().first) {
                                            timeOffsetSec = rawSpeedData.last().first + 1f
                                        }

                                        val timeDiffSec = timeOffsetSec - rawSpeedData.last().first

                                        var spdMps = pt.speed

                                        if (spdMps <= 0f) {
                                            spdMps = if (timeDiffSec > 0f) distM / timeDiffSec else 0f
                                        }

                                        val maxAllowedSpd = if (run.activityType == ActivityType.CYCLING || run.activityType == ActivityType.SPRINT) 65f else 30f
                                        val spdKmh = (spdMps * 3.6f).coerceIn(0f, maxAllowedSpd)

                                        // Stop-detection fallback for valid GPS with real timestamps:
                                        // If distM is extremely small over a normal time span, assume stopped
                                        val finalSpdMps = if (!isBogus && distM < 2f && timeDiffSec > 5f) 0f else spdKmh

                                        rawSpeedData.add(Pair(timeOffsetSec, finalSpdMps))

                                        if (pt.altitude != 0.0) hasAltitudeData = true
                                        lastPt = pt
                                    }

                                    // Very light smoothing: moving average over 3 points (instead of 9), so actual stops are visible!
                                    val speedData = rawSpeedData.mapIndexed { index, pair ->
                                        val window = rawSpeedData.subList(maxOf(0, index - 1), minOf(rawSpeedData.size, index + 2))
                                        val avgSpd = window.map { it.second }.average().toFloat()
                                        Pair(pair.first, avgSpd)
                                    }

                                    if (speedData.size > 2 && run.activityType.showSpeed) {
                                        SimpleLineChart(data = speedData, lineColor = color, label = "Hitrost (km/h) / Čas")
                                        Spacer(Modifier.height(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (deleteCandidate != null) {
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Delete activity", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete this activity?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val runToDel = deleteCandidate
                        if (runToDel != null) {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                runCatching {
                                    val userRef = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                                    Log.d("ActivityLog", "DELETE run session doc=${userRef.id} runId=${runToDel.id}")
                                    userRef.collection("runSessions").document(runToDel.id).delete().await()
                                    userRef.collection("publicActivities").document(runToDel.id).delete().await()
                                }.onFailure {
                                    Log.e("ActivityLog", "Delete failed for runId=${runToDel.id}", it)
                                }
                            }
                            com.example.myapplication.persistence.RunRouteStore.deleteRoute(context, runToDel.id)
                            runs = runs.filter { it.id != runToDel.id }
                            if (selectedRun?.id == runToDel.id) selectedRun = null
                        }
                        deleteCandidate = null
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteCandidate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FloatingBackButton(onBack: () -> Unit) {
    Box(
        Modifier
            .padding(top = 40.dp, start = 16.dp)
            .size(48.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
            .clip(CircleShape)
            .clickable { onBack() },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ActivityChip(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun SimpleLineChart(
    data: List<Pair<Float, Float>>,
    lineColor: Color,
    label: String
) {
    var touchXReal by remember { mutableStateOf<Float?>(null) }

    Column(Modifier.fillMaxWidth().height(140.dp).padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> touchXReal = offset.x },
                        onDrag = { change, _ -> touchXReal = change.position.x },
                        onDragEnd = { touchXReal = null },
                        onDragCancel = { touchXReal = null }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            touchXReal = offset.x
                            tryAwaitRelease()
                            touchXReal = null
                        }
                    )
                }
        ) {
            val minX = data.minOf { it.first }
            val maxX = data.maxOf { it.first }
            val minY = data.minOf { it.second }.let { if (it > 0f) 0f else it }
            val maxY = data.maxOf { it.second }.let { if (it == 0f) 5f else it * 1.2f }

            val rangeX = (maxX - minX).coerceAtLeast(1f)
            val rangeY = (maxY - minY).coerceAtLeast(1f)

            val path = Path()
            var first = true

            data.forEach { (x, y) ->
                val px = ((x - minX) / rangeX) * size.width
                val py = size.height - (((y - minY) / rangeY) * size.height)
                if (first) {
                    path.moveTo(px, py)
                    first = false
                } else {
                    path.lineTo(px, py)
                }
            }

            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(
                    width = 4f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )

            touchXReal?.let { tx ->
                val coercedTx = tx.coerceIn(0f, size.width)
                val targetX = minX + (coercedTx / size.width) * rangeX
                val closest = data.minByOrNull { Math.abs(it.first - targetX) }
                if (closest != null) {
                    val px = ((closest.first - minX) / rangeX) * size.width
                    val py = size.height - (((closest.second - minY) / rangeY) * size.height)

                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = androidx.compose.ui.geometry.Offset(px, 0f),
                        end = androidx.compose.ui.geometry.Offset(px, size.height),
                        strokeWidth = 2f
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 6f,
                        center = androidx.compose.ui.geometry.Offset(px, py)
                    )

                    val textCanvas = drawContext.canvas.nativeCanvas
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 28f
                        isAntiAlias = true
                        textAlign = android.graphics.Paint.Align.CENTER
                        setShadowLayer(4f, 0f, 0f, android.graphics.Color.BLACK)
                    }
                    val timeMin = (closest.first / 60f).toInt()
                    val timeSec = (closest.first % 60f).toInt()
                    val text = String.format("%d:%02d - %.1f km/h", timeMin, timeSec, closest.second)

                    val textY = if (py < 40f) py + 40f else py - 20f
                    val rx = px.coerceIn(60f, size.width - 60f)

                    textCanvas.drawText(text, rx, textY, paint)
                }
            }
        }
    }
}

@Composable
internal fun GlobalActivityOsmMap(
    context: android.content.Context,
    runs: List<RunSession>,
    allRoutes: Map<String, List<Pair<Double, Double>>>,
    selectedRunId: String? = null,
    onRunSelected: (RunSession) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            org.osmdroid.views.MapView(ctx).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isClickable = true
                isFocusable = true
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                )

                val allGeoPoints = mutableListOf<org.osmdroid.util.GeoPoint>()

                runs.forEach { run ->
                    val points = allRoutes[run.id]
                    if (!points.isNullOrEmpty()) {
                        val geoPoints = points.map { (lat, lng) -> org.osmdroid.util.GeoPoint(lat, lng) }
                        allGeoPoints.addAll(geoPoints)

                        val lineColor = activityColor(run.activityType)
                        val androidColor = android.graphics.Color.rgb(
                            (lineColor.red * 255).toInt(),
                            (lineColor.green * 255).toInt(),
                            (lineColor.blue * 255).toInt()
                        )

                        val polyline = org.osmdroid.views.overlay.Polyline(this).apply {
                            outlinePaint.color = androidColor
                            outlinePaint.strokeWidth = 10f
                            outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                            outlinePaint.isAntiAlias = true
                            setPoints(geoPoints)

                            setOnClickListener { poly, mapv, eventPos ->
                                onRunSelected(run)
                                true
                            }
                        }
                        overlays.add(polyline)

                        val midIndex = geoPoints.size / 2
                        val midMarker = org.osmdroid.views.overlay.Marker(this).apply {
                            position = geoPoints[midIndex]
                            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                            var bmp = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
                            var canvas = android.graphics.Canvas(bmp)
                            val paint = android.graphics.Paint().apply {
                                color = androidColor
                                isAntiAlias = true
                            }
                            canvas.drawCircle(20f, 20f, 15f, paint)
                            val innerPaint = android.graphics.Paint().apply { color = android.graphics.Color.WHITE }
                            canvas.drawCircle(20f, 20f, 7f, innerPaint)
                            icon = android.graphics.drawable.BitmapDrawable(ctx.resources, bmp)
                            title = "${run.activityType.emoji} Details"

                            setOnMarkerClickListener { marker, map ->
                                onRunSelected(run)
                                true
                            }
                        }
                        overlays.add(midMarker)
                    }
                }

                if (allGeoPoints.isNotEmpty()) {
                    val bbox = org.osmdroid.util.BoundingBox.fromGeoPoints(allGeoPoints)
                    val padded = bbox.increaseByScale(1.2f)
                    post {
                        zoomToBoundingBox(padded, true)
                        if (zoomLevelDouble > 15.0) controller.setZoom(15.0)
                    }
                }
            }
        },
        update = { mapv ->
            mapv.onResume()
            mapv.overlays.clear()

            val allGeoPoints = mutableListOf<org.osmdroid.util.GeoPoint>()
            val sortedRuns = runs.sortedBy { it.id == selectedRunId }
            sortedRuns.forEach { run ->
                val points = allRoutes[run.id]
                if (!points.isNullOrEmpty()) {
                    val geoPoints = points.map { (lat, lng) -> org.osmdroid.util.GeoPoint(lat, lng) }
                    allGeoPoints.addAll(geoPoints)

                    val isSelected = run.id == selectedRunId
                    val lineColor = activityColor(run.activityType)

                    val androidColor = if (isSelected) {
                        android.graphics.Color.rgb(255, 215, 0) // Distinct Yellow/Gold for selected
                    } else {
                        android.graphics.Color.rgb(
                            (lineColor.red * 255).toInt(),
                            (lineColor.green * 255).toInt(),
                            (lineColor.blue * 255).toInt()
                        )
                    }

                    val polyline = org.osmdroid.views.overlay.Polyline(mapv).apply {
                        outlinePaint.color = androidColor
                        outlinePaint.strokeWidth = if (isSelected) 22f else 10f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.isAntiAlias = true

                        if (isSelected) {
                            // Add a black border effect by drawing shadow
                            outlinePaint.setShadowLayer(5f, 0f, 0f, android.graphics.Color.BLACK)
                        }

                        setPoints(geoPoints)

                        setOnClickListener { _, _, _ ->
                            onRunSelected(run)
                            true
                        }
                    }
                    mapv.overlays.add(polyline)

                    val midIndex = geoPoints.size / 2
                    val midMarker = org.osmdroid.views.overlay.Marker(mapv).apply {
                        position = geoPoints[midIndex]
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_CENTER)
                        val bmp = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
                        val canvas = android.graphics.Canvas(bmp)
                        val paint = android.graphics.Paint().apply {
                            color = if (isSelected) android.graphics.Color.BLACK else android.graphics.Color.rgb((lineColor.red * 255).toInt(), (lineColor.green * 255).toInt(), (lineColor.blue * 255).toInt())
                            isAntiAlias = true
                        }
                        canvas.drawCircle(20f, 20f, 15f, paint)
                        val innerPaint = android.graphics.Paint().apply { color = if (isSelected) android.graphics.Color.YELLOW else android.graphics.Color.WHITE }
                        canvas.drawCircle(20f, 20f, 7f, innerPaint)
                        icon = android.graphics.drawable.BitmapDrawable(context.resources, bmp)
                        title = "${run.activityType.emoji} Details"

                        setOnMarkerClickListener { _, _ ->
                            onRunSelected(run)
                            true
                        }
                    }
                    mapv.overlays.add(midMarker)
                }
            }
            mapv.invalidate()
        },
        modifier = modifier
    )
}
