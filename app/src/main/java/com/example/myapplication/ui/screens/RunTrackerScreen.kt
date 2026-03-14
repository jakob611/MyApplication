package com.example.myapplication.ui.screens
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.myapplication.data.ActivityType
import com.example.myapplication.data.UserProfile
import com.example.myapplication.service.RunTrackingService
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider

/**
 * Izračun kalorij z MET formulo:
 *   kcal = MET × težaKg × čas_v_urah
 * Doda pribitek za vzpon: ~1 kcal/m vzpona (standardna ocena).
 */
private fun calculateCaloriesMet(
    activityType: ActivityType,
    durationSeconds: Long,
    weightKg: Double,
    elevationGainM: Float,
    avgSpeedKmh: Float
): Int {
    // Prilagodi MET glede na hitrost (teče hitreje = višji MET)
    val met = when (activityType) {
        ActivityType.RUN -> when {
            avgSpeedKmh < 8f  -> 6.0
            avgSpeedKmh < 10f -> 8.0
            avgSpeedKmh < 12f -> 10.0
            avgSpeedKmh < 14f -> 11.5
            else              -> 13.5
        }
        ActivityType.WALK -> when {
            avgSpeedKmh < 3f -> 2.5
            avgSpeedKmh < 5f -> 3.5
            else             -> 4.5
        }
        ActivityType.SPRINT -> when {
            avgSpeedKmh < 16f -> 13.0
            avgSpeedKmh < 20f -> 16.0
            else              -> 19.0
        }
        else -> activityType.metValue
    }

    val durationHours = durationSeconds / 3600.0
    val base = met * weightKg * durationHours
    // Vzpon: ~0.8 kcal/m za povprečno težo 70 kg, skalira z dejansko težo
    val elevBonus = elevationGainM * 0.8 * (weightKg / 70.0)
    return (base + elevBonus).toInt().coerceAtLeast(0)
}

@Composable
fun RunTrackerScreen(onBackPressed: () -> Unit, userProfile: UserProfile = UserProfile()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { Configuration.getInstance().userAgentValue = context.packageName }

    // --- Tip aktivnosti ---
    var selectedActivity by remember { mutableStateOf(ActivityType.RUN) }
    var showActivityPicker by remember { mutableStateOf(false) }

    var service by remember { mutableStateOf<RunTrackingService?>(null) }
    var isBound by remember { mutableStateOf(false) }
    var isTracking by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var locationPoints by remember { mutableStateOf<List<Location>>(emptyList()) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var distanceMeters by remember { mutableStateOf(0.0) }
    var maxSpeed by remember { mutableStateOf(0f) }
    var avgSpeed by remember { mutableStateOf(0f) }
    var elevationGain by remember { mutableStateOf(0f) }
    var elevationLoss by remember { mutableStateOf(0f) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var myLocationOverlay by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember { mutableStateOf(true) }
    var showGpsDialog by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var finalDistance by remember { mutableStateOf(0.0) }
    var finalTime by remember { mutableStateOf(0L) }
    var finalMaxSpeed by remember { mutableStateOf(0f) }
    var finalAvgSpeed by remember { mutableStateOf(0f) }
    var finalElevationGain by remember { mutableStateOf(0f) }
    var finalElevationLoss by remember { mutableStateOf(0f) }
    var actualWeightKg by remember { mutableStateOf(70.0) }
    val uid = remember { com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() }

    LaunchedEffect(uid) {
        if (uid != null) {
            try {
                Firebase.firestore.collection("users").document(uid)
                    .collection("weightLogs")
                    .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(1).get()
                    .addOnSuccessListener { snap ->
                        val w = snap.documents.firstOrNull()?.get("weightKg") as? Number
                        if (w != null) actualWeightKg = w.toDouble()
                    }
            } catch (_: Exception) {}
        }
    }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as? RunTrackingService.LocalBinder)?.getService(); isBound = true
            }
            override fun onServiceDisconnected(name: ComponentName?) { service = null; isBound = false }
        }
    }

    LaunchedEffect(Unit) { context.bindService(Intent(context, RunTrackingService::class.java), serviceConnection, Context.BIND_AUTO_CREATE) }
    DisposableEffect(Unit) {
        onDispose {
            if (isBound) try { context.unbindService(serviceConnection) } catch (_: Exception) {}
            myLocationOverlay?.disableMyLocation(); myLocationOverlay?.disableFollowLocation(); mapView?.onDetach()
        }
    }
    LaunchedEffect(service) { service?.isTracking?.collect { isTracking = it } }
    LaunchedEffect(service) { service?.isPaused?.collect { isPaused = it } }
    LaunchedEffect(service) { service?.locationPoints?.collect { locationPoints = it } }
    LaunchedEffect(service) { service?.elapsedSeconds?.collect { elapsedSeconds = it } }
    LaunchedEffect(service) { service?.distanceMeters?.collect { distanceMeters = it } }
    LaunchedEffect(service) { service?.maxSpeed?.collect { maxSpeed = it } }
    LaunchedEffect(service) { service?.avgSpeed?.collect { avgSpeed = it } }
    LaunchedEffect(service) { service?.elevationGain?.collect { elevationGain = it } }
    LaunchedEffect(service) { service?.elevationLoss?.collect { elevationLoss = it } }

    LaunchedEffect(Unit) {
        hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED else true
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasLocationPermission = it }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasNotificationPermission = it }

    // Live kcal (prikazano med sledenjem)
    val liveCalories = remember(elapsedSeconds, avgSpeed, elevationGain, selectedActivity, actualWeightKg) {
        calculateCaloriesMet(selectedActivity, elapsedSeconds, actualWeightKg, elevationGain, avgSpeed * 3.6f)
    }

    LaunchedEffect(locationPoints) {
        mapView?.let { map ->
            routePolyline?.let { map.overlays.remove(it) }
            if (locationPoints.isNotEmpty()) {
                val polyline = Polyline(map).apply {
                    outlinePaint.color = android.graphics.Color.rgb(33, 150, 243)
                    outlinePaint.strokeWidth = 12f
                    outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                    outlinePaint.isAntiAlias = true
                    setPoints(locationPoints.map { GeoPoint(it.latitude, it.longitude) })
                }
                map.overlays.add(polyline); routePolyline = polyline
                map.controller.animateTo(GeoPoint(locationPoints.last().latitude, locationPoints.last().longitude))
                if (map.zoomLevelDouble < 16.0) map.controller.setZoom(17.0)
                map.invalidate()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Mapa
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK); setMultiTouchControls(true); controller.setZoom(17.0)
                    val overlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        enableMyLocation(); enableFollowLocation(); setDrawAccuracyEnabled(true)
                    }
                    overlays.add(overlay); myLocationOverlay = overlay
                    controller.setCenter(GeoPoint(46.0569, 14.5058))
                    overlay.runOnFirstFix { post { overlay.myLocation?.let { controller.animateTo(it) } } }
                    mapView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { it.onResume() }
        )

        // Stats card (levo zgoraj)
        Card(
            Modifier.align(Alignment.TopStart).padding(16.dp).fillMaxWidth(0.58f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                IconButton(onClick = onBackPressed, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }

                // Tip aktivnosti oznaka (med sledenjem — ne gumb)
                Text(
                    "${selectedActivity.emoji} ${selectedActivity.label}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6366F1)
                )

                Text("${"%.2f".format(distanceMeters / 1000.0)} km", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(fmtTime(elapsedSeconds), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

                if (selectedActivity.showSpeed) {
                    Text("Max: ${"%.1f".format(maxSpeed * 3.6)} km/h", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Avg: ${"%.1f".format(avgSpeed * 3.6)} km/h", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                if (selectedActivity.showPace && distanceMeters > 50) {
                    val paceMin = if (avgSpeed > 0.1f) (1000.0 / (avgSpeed * 60.0)) else 0.0
                    Text("Pace: ${paceMin.toInt()}:${"%02d".format(((paceMin - paceMin.toInt()) * 60).toInt())} /km", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                if (selectedActivity.showElevation && (elevationGain > 0f || elevationLoss > 0f)) {
                    Text("↑ ${"%.0f".format(elevationGain)} m  ↓ ${"%.0f".format(elevationLoss)} m", fontSize = 13.sp, color = Color(0xFF4CAF50))
                }
                if (isTracking) {
                    Text("🔥 $liveCalories kcal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFFF9800))
                    Text(
                        if (isPaused) "Paused" else "Tracking...",
                        fontSize = 12.sp,
                        color = if (isPaused) Color(0xFFFF9800) else Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Controls card (spodaj)
        Card(
            Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // --- Picker aktivnosti (samo ko ne sledimo) ---
                if (!isTracking && !showSummary) {
                    // Izbrani tip + gumb za spremembo
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showActivityPicker = !showActivityPicker }
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${selectedActivity.emoji} ${selectedActivity.label}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ArrowDropDown, "Izberi aktivnost", tint = Color(0xFF6366F1))
                    }

                    // Picker lista — horizontalni scroll
                    if (showActivityPicker) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(ActivityType.entries) { type ->
                                val isSelected = type == selectedActivity
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) Color(0xFF6366F1) else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable {
                                        selectedActivity = type
                                        showActivityPicker = false
                                    }
                                ) {
                                    Text(
                                        "${type.emoji} ${type.label}",
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Gumbi
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        !isTracking && !showSummary -> Button(
                            onClick = {
                                when {
                                    !hasLocationPermission -> locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    else -> {
                                        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                                        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                                            showGpsDialog = true
                                        } else {
                                            showActivityPicker = false
                                            context.startForegroundService(Intent(context, RunTrackingService::class.java).apply { action = RunTrackingService.ACTION_START })
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Start ${selectedActivity.label}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }

                        isTracking -> {
                            Button(
                                onClick = { context.startService(Intent(context, RunTrackingService::class.java).apply { action = if (isPaused) RunTrackingService.ACTION_RESUME else RunTrackingService.ACTION_PAUSE }) },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isPaused) Color(0xFF4CAF50) else Color(0xFFFF9800)),
                                shape = RoundedCornerShape(12.dp)
                            ) { Icon(if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null); Spacer(Modifier.width(8.dp)); Text(if (isPaused) "Resume" else "Pause", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

                            Button(
                                onClick = {
                                    finalDistance = distanceMeters; finalTime = elapsedSeconds
                                    finalMaxSpeed = maxSpeed; finalAvgSpeed = avgSpeed
                                    finalElevationGain = elevationGain; finalElevationLoss = elevationLoss
                                    context.startService(Intent(context, RunTrackingService::class.java).apply { action = RunTrackingService.ACTION_STOP })
                                    showSummary = true
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                                shape = RoundedCornerShape(12.dp)
                            ) { Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp)); Text("Stop", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }

                        showSummary -> Button(
                            onClick = {
                                val sessionId = java.util.UUID.randomUUID().toString()
                                val avgKmh = finalAvgSpeed * 3.6f
                                val calories = calculateCaloriesMet(selectedActivity, finalTime, actualWeightKg, finalElevationGain, avgKmh)

                                if (uid != null) {
                                    val avgSpeedMps = if (finalTime > 0) (finalDistance / finalTime).toFloat() else 0f
                                    val runMap = hashMapOf<String, Any>(
                                        "id" to sessionId, "userId" to uid,
                                        "startTime" to (System.currentTimeMillis() - finalTime * 1000L),
                                        "endTime" to System.currentTimeMillis(),
                                        "durationSeconds" to finalTime.toInt(),
                                        "distanceMeters" to finalDistance,
                                        "maxSpeedMps" to finalMaxSpeed,
                                        "avgSpeedMps" to avgSpeedMps,
                                        "caloriesKcal" to calories,
                                        "elevationGainM" to finalElevationGain,
                                        "elevationLossM" to finalElevationLoss,
                                        "activityType" to selectedActivity.name,
                                        "createdAt" to System.currentTimeMillis(),
                                        "polylinePoints" to emptyList<Any>()
                                    )
                                    Firebase.firestore
                                        .collection("users").document(uid)
                                        .collection("runSessions").document(sessionId)
                                        .set(runMap)

                                    // Shrani komprimirano ruto v publicActivities (za deljenje s followerji)
                                    // Samo če ima uporabnik shareActivities=true
                                    val email = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                                    if (email != null) {
                                        Firebase.firestore.collection("users").document(uid)
                                            .get()
                                            .addOnSuccessListener { userDoc ->
                                                val shareAct = userDoc.getBoolean("share_activities") ?: false
                                                if (shareAct && locationPoints.isNotEmpty()) {
                                                    val rawPts = locationPoints.map { Pair(it.latitude, it.longitude) }
                                                    // RDP kompresija: ~450 točk → ~35 točk
                                                    val compressed = com.example.myapplication.utils.RouteCompressor.compress(rawPts)
                                                    val routeList = compressed.map { (lat, lng) ->
                                                        mapOf("lat" to lat, "lng" to lng)
                                                    }
                                                    val pubMap = hashMapOf<String, Any>(
                                                        "activityType" to selectedActivity.name,
                                                        "distanceMeters" to finalDistance,
                                                        "durationSeconds" to finalTime.toInt(),
                                                        "caloriesKcal" to calories,
                                                        "elevationGainM" to finalElevationGain,
                                                        "elevationLossM" to finalElevationLoss,
                                                        "avgSpeedMps" to avgSpeedMps,
                                                        "maxSpeedMps" to finalMaxSpeed,
                                                        "startTime" to (System.currentTimeMillis() - finalTime * 1000L),
                                                        "routePoints" to routeList
                                                    )
                                                    Firebase.firestore
                                                        .collection("users").document(uid)
                                                        .collection("publicActivities").document(sessionId)
                                                        .set(pubMap)
                                                }
                                            }
                                    }
                                }

                                val routePoints = locationPoints.map { loc -> Pair(loc.latitude, loc.longitude) }
                                if (routePoints.isNotEmpty()) {
                                    com.example.myapplication.persistence.RunRouteStore.saveRoute(context, sessionId, routePoints)
                                }

                                val xp = ((finalDistance / 100) + (finalTime / 60)).toInt().coerceAtLeast(10)
                                val runEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                                if (runEmail != null) {
                                    scope.launch {
                                        com.example.myapplication.persistence.AchievementStore.awardXP(
                                            context, runEmail, xp,
                                            com.example.myapplication.data.XPSource.RUN_COMPLETED,
                                            "Completed ${selectedActivity.label}: ${finalDistance.toInt()}m"
                                        )
                                        withContext(Dispatchers.Main) {
                                            android.widget.Toast.makeText(context, "+$xp XP!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                showSummary = false; onBackPressed()
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Done", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // Summary overlay
        if (showSummary) {
            val summaryCalories = calculateCaloriesMet(selectedActivity, finalTime, actualWeightKg, finalElevationGain, finalAvgSpeed * 3.6f)
            val xpEarned = ((finalDistance / 100) + (finalTime / 60)).toInt().coerceAtLeast(10)

            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
                Card(
                    Modifier.align(Alignment.Center).fillMaxWidth(0.88f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "${selectedActivity.emoji} ${selectedActivity.label} Complete!",
                            fontSize = 22.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF2196F3)
                        )
                        HorizontalDivider()

                        SummaryRow("📏 Distance", "${"%.2f".format(finalDistance / 1000.0)} km")
                        SummaryRow("⏱️ Duration", fmtTime(finalTime))

                        if (selectedActivity.showSpeed) {
                            SummaryRow("⚡ Max speed", "${"%.1f".format(finalMaxSpeed * 3.6)} km/h")
                            SummaryRow("📈 Avg speed", "${"%.1f".format(finalAvgSpeed * 3.6)} km/h")
                        }
                        if (selectedActivity.showPace && finalDistance > 50) {
                            val p = (finalTime / 60.0) / (finalDistance / 1000.0)
                            SummaryRow("🏃 Pace", "${p.toInt()}:${"%02d".format(((p - p.toInt()) * 60).toInt())} /km")
                        }
                        if (selectedActivity.showElevation && (finalElevationGain > 0f || finalElevationLoss > 0f)) {
                            SummaryRow("⛰️ Ascent", "${"%.0f".format(finalElevationGain)} m")
                            SummaryRow("↘️ Descent", "${"%.0f".format(finalElevationLoss)} m")
                        }

                        HorizontalDivider()
                        SummaryRow("🔥 Calories", "$summaryCalories kcal", highlight = true)
                        Text("+$xpEarned XP", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    }
                }
            }
        }
    }

    if (showGpsDialog) {
        AlertDialog(
            onDismissRequest = { showGpsDialog = false },
            title = { Text("GPS ni vklopljen", fontWeight = FontWeight.Bold) },
            text = { Text("Za sledenje aktivnosti potrebuješ vklopljeno lokacijo (GPS). Prosimo vklopi lokacijo v nastavitvah naprave.") },
            confirmButton = {
                TextButton(onClick = {
                    showGpsDialog = false
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }) { Text("Odpri nastavitve") }
            },
            dismissButton = { TextButton(onClick = { showGpsDialog = false }) { Text("Prekliči") } }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = if (highlight) 17.sp else 15.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun fmtTime(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
