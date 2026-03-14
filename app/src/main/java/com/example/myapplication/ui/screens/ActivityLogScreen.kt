package com.example.myapplication.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.ActivityType
import com.example.myapplication.data.RunSession
import com.example.myapplication.viewmodels.RunTrackerViewModel
import java.text.SimpleDateFormat
import java.util.Locale

// ---------------------------------------------------------------
// Barve po tipu aktivnosti — za kartice
// ---------------------------------------------------------------
internal fun activityColor(type: ActivityType): Color = when (type) {
    ActivityType.RUN      -> Color(0xFF2196F3)   // modra
    ActivityType.WALK     -> Color(0xFF4CAF50)   // zelena
    ActivityType.HIKE     -> Color(0xFF795548)   // rjava
    ActivityType.SPRINT   -> Color(0xFFF44336)   // rdeča
    ActivityType.CYCLING  -> Color(0xFFFF9800)   // oranžna
    ActivityType.SKIING   -> Color(0xFF00BCD4)   // svetlo modra
    ActivityType.SNOWBOARD-> Color(0xFF9C27B0)   // vijolična
    ActivityType.SKATING  -> Color(0xFF00ACC1)   // turkizna
    ActivityType.NORDIC   -> Color(0xFF8BC34A)   // svetlo zelena
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = androidx.lifecycle.viewmodel.compose.viewModel<RunTrackerViewModel>()

    var runs by remember { mutableStateOf<List<RunSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        viewModel.loadRunSessions { sessions ->
            runs = sessions
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Log", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2563EB),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                runs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🗺️", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No activities yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Start your first run or hike!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(runs) { run ->
                        ActivityLogCard(run = run, context = context)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityLogCard(run: RunSession, context: android.content.Context) {
    val fmt = remember { SimpleDateFormat("EEE, dd MMM yyyy · HH:mm", Locale.ENGLISH) }
    var expanded by remember { mutableStateOf(false) }
    var routePoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }

    val color = activityColor(run.activityType)

    LaunchedEffect(run.id, expanded) {
        if (expanded && routePoints.isEmpty() && run.id.isNotBlank()) {
            val loaded = com.example.myapplication.persistence.RunRouteStore.loadRoute(context, run.id)
            if (loaded != null) routePoints = loaded
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Barvna header vrstica
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(color.copy(alpha = 0.15f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Barvna pika tipa aktivnosti
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(color, RoundedCornerShape(50))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${run.activityType.emoji} ${run.activityType.label}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = color
                        )
                    }
                    Text(
                        "${"%.2f".format(run.getDistanceKm())} km",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Body
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    fmt.format(java.util.Date(run.startTime)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // Stats chips v vrstici
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActivityChip("⏱️ ${run.formatDuration()}", color)
                    if (run.activityType.showSpeed)
                        ActivityChip("${"%.1f".format(run.getAvgSpeedKmh())} km/h", color)
                    if (run.activityType.showPace && run.distanceMeters > 50 && run.avgSpeedMps > 0.1f) {
                        val pace = 1000.0 / (run.avgSpeedMps * 60.0)
                        ActivityChip("${pace.toInt()}:${"%02d".format(((pace - pace.toInt()) * 60).toInt())} /km", color)
                    }
                    ActivityChip("🔥 ${run.caloriesKcal} kcal", Color(0xFFFF9800))
                }

                // Vzpon/spust
                if (run.activityType.showElevation && (run.elevationGainM > 0f || run.elevationLossM > 0f)) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⛰️ ↑ ${"%.0f".format(run.elevationGainM)} m  ↓ ${"%.0f".format(run.elevationLossM)} m",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }

                // Razširjen pogled — mini mapa
                if (expanded) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = color.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))

                    if (routePoints.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🗺️", fontSize = 32.sp)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Route not available",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // OSM mapa z barvno linijo glede na tip aktivnosti
                        RunOsmMapColored(
                            context = context,
                            points = routePoints,
                            lineColor = color,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                }
            }
        }
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * OSM mapa z nastavljivo barvo linije (glede na tip aktivnosti).
 */
@Composable
internal fun RunOsmMapColored(
    context: android.content.Context,
    points: List<Pair<Double, Double>>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    val androidColor = android.graphics.Color.rgb(
        (lineColor.red * 255).toInt(),
        (lineColor.green * 255).toInt(),
        (lineColor.blue * 255).toInt()
    )

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            org.osmdroid.views.MapView(ctx).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                setMultiTouchControls(false)
                isClickable = false
                isFocusable = false
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                )

                if (points.isNotEmpty()) {
                    val geoPoints = points.map { (lat, lng) -> org.osmdroid.util.GeoPoint(lat, lng) }

                    val polyline = org.osmdroid.views.overlay.Polyline(this).apply {
                        outlinePaint.color = androidColor
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.isAntiAlias = true
                        setPoints(geoPoints)
                    }
                    overlays.add(polyline)

                    // Start marker
                    val startMarker = org.osmdroid.views.overlay.Marker(this).apply {
                        position = geoPoints.first()
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                        icon = createColoredMarkerDrawable(ctx, android.graphics.Color.rgb(76, 175, 80), "S")
                        title = "Start"
                    }
                    overlays.add(startMarker)

                    if (geoPoints.size > 1) {
                        val endMarker = org.osmdroid.views.overlay.Marker(this).apply {
                            position = geoPoints.last()
                            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                            icon = createColoredMarkerDrawable(ctx, android.graphics.Color.rgb(244, 67, 54), "F")
                            title = "Finish"
                        }
                        overlays.add(endMarker)
                    }

                    val bbox = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)
                    val padded = bbox.increaseByScale(1.4f)
                    post {
                        zoomToBoundingBox(padded, false)
                        if (zoomLevelDouble > 18.0) controller.setZoom(17.0)
                    }
                }
            }
        },
        update = { it.onResume() },
        modifier = modifier
    )
}

