package com.example.myapplication.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.data.RunSession
import com.example.myapplication.viewmodels.RunTrackerViewModel
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExerciseHistoryScreen(
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Workouts", "Runs", "Exercises")
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBack) { Text("Back") }
                Spacer(Modifier.width(12.dp))
                Text("Exercise history", style = MaterialTheme.typography.titleLarge)
            }
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> WorkoutsTab()
                1 -> RunsTab()
                2 -> ExercisesTab()
            }
        }
    }
}

@Composable
private fun ExercisesTab() {
    val uid = remember { com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() }
    var entries by remember { mutableStateOf<List<ExerciseLog>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        Firebase.firestore
            .collection("users").document(uid).collection("exerciseLogs")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(200)
            .get()
            .addOnSuccessListener { snap ->
                entries = snap.documents.mapNotNull { d ->
                    try {
                        ExerciseLog(
                            name = d.getString("name") ?: "?",
                            date = d.getTimestamp("date")?.toDate() ?: Date(),
                            caloriesKcal = (d.get("caloriesKcal") as? Number)?.toInt() ?: 0,
                            sets = (d.get("sets") as? Number)?.toInt(),
                            reps = (d.get("reps") as? Number)?.toInt(),
                            durationSeconds = (d.get("durationSeconds") as? Number)?.toInt()
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                loading = false
            }
            .addOnFailureListener { loading = false }
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No individual exercises yet")
        }
    } else {
        val fmt = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { ex ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(ex.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(fmt.format(ex.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val details = when {
                                ex.durationSeconds != null -> "Duration: ${ex.durationSeconds}s"
                                ex.sets != null && ex.reps != null -> "${ex.sets} x ${ex.reps} reps"
                                ex.sets != null -> "${ex.sets} sets"
                                else -> ""
                            }
                            if (details.isNotBlank()) {
                                Text(
                                    details,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }

                            Text(
                                "${ex.caloriesKcal} kcal",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutsTab() {
    val uid = remember { com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() }
    var entries by remember { mutableStateOf(listOf<WorkoutEntry>()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        Firebase.firestore
            .collection("users").document(uid).collection("workoutSessions")
            .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .get()
            .addOnSuccessListener { snap ->
                entries = snap.documents.mapNotNull { d ->
                    try {
                        WorkoutEntry(
                            id = d.id,
                            date = d.getTimestamp("date")?.toDate() ?: Date(),
                            totalKcal = (d.get("totalKcal") as? Number)?.toInt() ?: 0,
                            totalTimeMin = (d.get("totalTimeMin") as? Number)?.toDouble() ?: 0.0,
                            exercisesCount = (d.get("exercisesCount") as? Number)?.toInt() ?: 0
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                loading = false
            }
            .addOnFailureListener {
                loading = false
            }
    }
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No workouts yet")
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { entry ->
                WorkoutCard(uid = uid!!, entry = entry)
            }
        }
    }
}

@Composable
private fun RunsTab() {
    val viewModel: RunTrackerViewModel = viewModel()
    var runs by remember { mutableStateOf<List<RunSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        viewModel.loadRunSessions { sessions ->
            runs = sessions
            loading = false
        }
    }
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (runs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No runs yet. Start your first run!")
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(runs) { run ->
                RunCard(run)
            }
        }
    }
}

private fun formatWorkoutTime(totalMinutes: Double): String {
    val totalSeconds = (totalMinutes * 60).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

data class WorkoutEntry(val id: String, val date: Date, val totalKcal: Int, val totalTimeMin: Double, val exercisesCount: Int)
data class WorkoutExercise(val name: String, val activeMinutes: Double, val restMinutes: Double, val caloriesKcal: Int)
data class ExerciseLog(
    val name: String,
    val date: Date,
    val caloriesKcal: Int,
    val sets: Int? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null
)

@Composable
private fun WorkoutCard(uid: String, entry: WorkoutEntry) {
    val fmt = remember { SimpleDateFormat("EEE, dd MMM yyyy", Locale.ENGLISH) }
    var expanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var exList by remember { mutableStateOf<List<WorkoutExercise>>(emptyList()) }
    fun loadExercisesIfNeeded() {
        if (exList.isNotEmpty()) return
        loading = true
        // Vaje so shranjene kot polje "exercises" znotraj dokumenta workoutSession (ne kot podkolekcija)
        Firebase.firestore
            .collection("users").document(uid)
            .collection("workoutSessions").document(entry.id)
            .get()
            .addOnSuccessListener { doc ->
                @Suppress("UNCHECKED_CAST")
                val rawList = doc.get("exercises") as? List<Map<String, Any>> ?: emptyList()
                exList = rawList.mapNotNull { d ->
                    try {
                        WorkoutExercise(
                            name = (d["name"] as? String)?.replace("_", " ") ?: "?",
                            activeMinutes = (d["activeMinutes"] as? Number)?.toDouble() ?: 0.0,
                            restMinutes = (d["restMinutes"] as? Number)?.toDouble() ?: 0.0,
                            caloriesKcal = (d["caloriesKcal"] as? Number)?.toInt() ?: 0
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
                loading = false
            }
            .addOnFailureListener { loading = false }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!expanded) loadExercisesIfNeeded()
                expanded = !expanded
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(fmt.format(entry.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${entry.totalKcal} kcal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Text("${entry.exercisesCount} exercises • ${formatWorkoutTime(entry.totalTimeMin)}", style = MaterialTheme.typography.bodyMedium)
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (exList.isEmpty()) {
                    Text("No exercises found", style = MaterialTheme.typography.bodySmall)
                } else {
                    exList.forEach { ex ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(ex.name, fontWeight = FontWeight.Medium)
                            Text(
                                "Active: ${String.format("%.1f", ex.activeMinutes)}m, Rest: ${String.format("%.1f", ex.restMinutes)}m, ${ex.caloriesKcal} kcal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunCard(run: RunSession) {
    val fmt = remember { SimpleDateFormat("EEE, dd MMM yyyy HH:mm", Locale.ENGLISH) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var routePoints by remember { mutableStateOf<List<Pair<Double, Double>>>(emptyList()) }

    // Naloži lokalno shranjeno traso
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header vrstica
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("🏃 Run", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(fmt.format(java.util.Date(run.startTime)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${"%.2f".format(run.getDistanceKm())} km",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))

            // Stats vrstica
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RunStatItem("Duration", run.formatDuration())
                RunStatItem("Avg", "${"%.1f".format(run.getAvgSpeedKmh())} km/h")
                RunStatItem("Max", "${"%.1f".format(run.getMaxSpeedKmh())} km/h")
                RunStatItem("Calories", "${run.caloriesKcal} kcal")
            }

            // Razširi — prikaži zemljevid
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                if (routePoints.isEmpty()) {
                    // Ni lokalno shranjene trase (star tek ali brez GPS)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🗺️", style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "No route available\n(GPS data not saved locally)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    // OSM zemljevid z traso
                    RunMapView(
                        points = routePoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun RunStatItem(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun RunMapView(
    points: List<Pair<Double, Double>>,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isFullscreen by remember { mutableStateOf(false) }

    if (isFullscreen) {
        // Full screen overlay
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                RunOsmMap(
                    context = context,
                    points = points,
                    interactive = true,
                    modifier = Modifier.fillMaxSize()
                )
                // Zapri gumb
                IconButton(
                    onClick = { isFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            RoundedCornerShape(8.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close fullscreen",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    // Miniaturni prikaz (ni interaktiven)
    Box(modifier = modifier) {
        RunOsmMap(
            context = context,
            points = points,
            interactive = false,
            modifier = Modifier.fillMaxSize()
        )
        // Full screen gumb
        IconButton(
            onClick = { isFullscreen = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    RoundedCornerShape(8.dp)
                )
                .size(36.dp)
        ) {
            Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = "Full screen",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
        }
    }
}

@Composable
private fun RunOsmMap(
    context: android.content.Context,
    points: List<Pair<Double, Double>>,
    interactive: Boolean,
    modifier: Modifier = Modifier
) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            org.osmdroid.config.Configuration.getInstance().userAgentValue = ctx.packageName
            val mapView = org.osmdroid.views.MapView(ctx).apply {
                setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)
                // Touch kontrole samo v full screen
                setMultiTouchControls(interactive)
                isClickable = interactive
                isFocusable = interactive
                // Skrij zoom gumbe (+/-) — niso potrebni
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
                )

                if (points.isNotEmpty()) {
                    val geoPoints = points.map { (lat, lng) -> org.osmdroid.util.GeoPoint(lat, lng) }

                    // Nariši traso
                    val polyline = org.osmdroid.views.overlay.Polyline(this).apply {
                        outlinePaint.color = android.graphics.Color.rgb(33, 150, 243)
                        outlinePaint.strokeWidth = if (interactive) 12f else 8f
                        outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                        outlinePaint.isAntiAlias = true
                        setPoints(geoPoints)
                    }
                    overlays.add(polyline)

                    // Start marker — zelena zastavica s tekstom "Start"
                    val startMarker = org.osmdroid.views.overlay.Marker(this).apply {
                        position = geoPoints.first()
                        setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                        title = "Start"
                        snippet = "Starting point"
                        icon = createColoredMarkerDrawable(ctx, android.graphics.Color.rgb(76, 175, 80), "S")
                    }
                    overlays.add(startMarker)

                    // Finish marker — rdeča zastivca s tekstom "F"
                    if (geoPoints.size > 1) {
                        val endMarker = org.osmdroid.views.overlay.Marker(this).apply {
                            position = geoPoints.last()
                            setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                            title = "Finish"
                            snippet = "End point"
                            icon = createColoredMarkerDrawable(ctx, android.graphics.Color.rgb(244, 67, 54), "F")
                        }
                        overlays.add(endMarker)
                    }

                    // Zoom na bounding box trase z paddingom
                    val bbox = org.osmdroid.util.BoundingBox.fromGeoPoints(geoPoints)
                    // Padding 30% da trasa ne gre do robov
                    val padded = bbox.increaseByScale(1.4f)
                    post {
                        zoomToBoundingBox(padded, false)
                        // Če je samo ena točka ali zelo kratka trasa
                        if (zoomLevelDouble > 18.0) controller.setZoom(17.0)
                    }
                }
            }
            mapView
        },
        update = { it.onResume() },
        modifier = modifier
    )
}

private fun Double.toStringWithDecimals(decimals: Int): String {
    return String.format("%.${decimals}f", this)
}

private fun Float.toStringWithDecimals(decimals: Int): String {
    return String.format("%.${decimals}f", this)
}

/** Ustvari barvni krog z črko — za start/finish markerje na OSM zemljevidu */
private fun createColoredMarkerDrawable(
    context: android.content.Context,
    color: Int,
    letter: String
): android.graphics.drawable.Drawable {
    val sizePx = 80
    val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    // Krog ozadje
    val paintCircle = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = android.graphics.Paint.Style.FILL
    }
    val radius = sizePx / 2f - 4f
    canvas.drawCircle(sizePx / 2f, sizePx / 2f - 8f, radius, paintCircle)

    // Bela obroba
    val paintBorder = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 5f
    }
    canvas.drawCircle(sizePx / 2f, sizePx / 2f - 8f, radius, paintBorder)

    // Trikotnik "rep" spodaj
    val paintTri = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = android.graphics.Paint.Style.FILL
    }
    val triPath = android.graphics.Path().apply {
        moveTo(sizePx / 2f - 10f, sizePx / 2f + radius - 10f)
        lineTo(sizePx / 2f + 10f, sizePx / 2f + radius - 10f)
        lineTo(sizePx / 2f, sizePx.toFloat() - 2f)
        close()
    }
    canvas.drawPath(triPath, paintTri)

    // Črka
    val paintText = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        textSize = 34f
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textY = sizePx / 2f - 8f - (paintText.descent() + paintText.ascent()) / 2f
    canvas.drawText(letter, sizePx / 2f, textY, paintText)

    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}

