package com.example.myapplication.ui.screens
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.myapplication.data.UserProfile
import com.example.myapplication.service.RunTrackingService
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider

@Composable
fun RunTrackerScreen(onBackPressed: () -> Unit, userProfile: UserProfile = UserProfile()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { Configuration.getInstance().userAgentValue = context.packageName }
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
    var showSummary by remember { mutableStateOf(false) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var finalDistance by remember { mutableStateOf(0.0) }
    var finalTime by remember { mutableStateOf(0L) }
    var finalMaxSpeed by remember { mutableStateOf(0f) }
    var finalAvgSpeed by remember { mutableStateOf(0f) }
    var finalElevationGain by remember { mutableStateOf(0f) }
    var finalElevationLoss by remember { mutableStateOf(0f) }

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

        // Stats card
        Card(
            Modifier.align(Alignment.TopStart).padding(16.dp).fillMaxWidth(0.55f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onBackPressed, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text("Distance: ${"%.2f".format(distanceMeters / 1000.0)} km", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Time: ${fmtTime(elapsedSeconds)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("Max: ${"%.1f".format(maxSpeed * 3.6)} km/h", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Avg: ${"%.1f".format(avgSpeed * 3.6)} km/h", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                if (elevationGain > 0f || elevationLoss > 0f) {
                    Text("↑ ${"%.0f".format(elevationGain)} m  ↓ ${"%.0f".format(elevationLoss)} m", fontSize = 13.sp, color = Color(0xFF4CAF50))
                }
                if (isTracking) Text(
                    if (isPaused) "Paused" else "Tracking...",
                    fontSize = 12.sp,
                    color = if (isPaused) Color(0xFFFF9800) else Color(0xFF4CAF50),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Controls card
        Card(
            Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    !isTracking && !showSummary -> Button(
                        onClick = {
                            when {
                                !hasLocationPermission -> locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else -> context.startForegroundService(Intent(context, RunTrackingService::class.java).apply { action = RunTrackingService.ACTION_START })
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) { Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start Run", fontSize = 18.sp, fontWeight = FontWeight.Bold) }

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
                            // Shrani tek v Firestore
                            val uid = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
                            val sessionId = java.util.UUID.randomUUID().toString()
                            if (uid != null) {
                                val avgSpeedMps = if (finalTime > 0) (finalDistance / finalTime).toFloat() else 0f
                                val durationSec = finalTime.toInt()
                                val weightKg = 70.0
                                val avgKmh = finalAvgSpeed * 3.6f
                                val coef = when { avgKmh < 5f -> 0.07; avgKmh < 9f -> 0.10; else -> 0.13 }
                                val calories = (durationSec / 60.0 * coef * weightKg).toInt()
                                val runMap = hashMapOf<String, Any>(
                                    "id" to sessionId, "userId" to uid,
                                    "startTime" to (System.currentTimeMillis() - finalTime * 1000L),
                                    "endTime" to System.currentTimeMillis(),
                                    "durationSeconds" to durationSec,
                                    "distanceMeters" to finalDistance,
                                    "maxSpeedMps" to finalMaxSpeed,
                                    "avgSpeedMps" to avgSpeedMps,
                                    "caloriesKcal" to calories,
                                    "elevationGainM" to finalElevationGain,
                                    "elevationLossM" to finalElevationLoss,
                                    "createdAt" to System.currentTimeMillis(),
                                    "polylinePoints" to emptyList<Any>() // točke so lokalne
                                )
                                Firebase.firestore
                                    .collection("users").document(uid)
                                    .collection("runSessions").document(sessionId)
                                    .set(runMap)
                            }
                            // Shrani GPS točke LOKALNO (ne v Firestore)
                            val routePoints = locationPoints.map { loc ->
                                Pair(loc.latitude, loc.longitude)
                            }
                            if (routePoints.isNotEmpty()) {
                                com.example.myapplication.persistence.RunRouteStore.saveRoute(
                                    context, sessionId, routePoints
                                )
                            }
                            val xp = ((finalDistance / 100) + (finalTime / 60)).toInt().coerceAtLeast(10)
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email?.let {
                                com.example.myapplication.data.UserPreferences.addXPWithCallback(context, it, xp) {
                                    android.widget.Toast.makeText(context, "+$xp XP!", android.widget.Toast.LENGTH_SHORT).show()
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

        // Summary overlay
        if (showSummary) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))) {
                Card(
                    Modifier.align(Alignment.Center).fillMaxWidth(0.85f).padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Run Complete!", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2196F3))
                        Spacer(Modifier.height(8.dp))
                        Text("Distance: ${"%.2f".format(finalDistance / 1000.0)} km", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Duration: ${fmtTime(finalTime)}", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                        Text("Max: ${"%.1f".format(finalMaxSpeed * 3.6)} km/h", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("Avg: ${"%.1f".format(finalAvgSpeed * 3.6)} km/h", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        if (finalElevationGain > 0f || finalElevationLoss > 0f) {
                            Text("↑ ${"%.0f".format(finalElevationGain)} m  ↓ ${"%.0f".format(finalElevationLoss)} m", fontSize = 16.sp, color = Color(0xFF4CAF50))
                        }
                        if (finalDistance > 0) {
                            val p = (finalTime / 60.0) / (finalDistance / 1000.0)
                            Text("Pace: ${p.toInt()}:${"%02d".format(((p - p.toInt()) * 60).toInt())} /km", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("+${((finalDistance / 100) + (finalTime / 60)).toInt().coerceAtLeast(10)} XP", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                    }
                }
            }
        }
    }
}

private fun fmtTime(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)

