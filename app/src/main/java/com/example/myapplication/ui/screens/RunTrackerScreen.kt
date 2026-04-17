package com.example.myapplication.ui.screens
import com.example.myapplication.domain.run.CompressRouteUseCase
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
import android.util.Log
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
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.luminance
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
import kotlinx.coroutines.tasks.await
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
    // 1. Omejimo nerealne hitrosti do 35 km/h za tek (GPS skoki)
    val safeSpeed = avgSpeedKmh.coerceIn(0f, 35f)

    // Prilagodi MET glede na hitrost z linearno interpolacijo,
    // da preprečimo stopničaste "GPS skoke" ki umetno dvignejo MET.
    val met = when (activityType) {
        ActivityType.RUN -> when {
            safeSpeed < 4f  -> safeSpeed // Linearno od 0 do 4
            safeSpeed < 8f  -> 4.0 + (safeSpeed - 4f) * 0.5 // 4 do 6
            safeSpeed < 10f -> 6.0 + (safeSpeed - 8f) * 1.0 // 6 do 8
            safeSpeed < 12f -> 8.0 + (safeSpeed - 10f) * 1.0 // 8 do 10
            safeSpeed < 14f -> 10.0 + (safeSpeed - 12f) * 0.75 // 10 do 11.5
            else            -> 11.5 + (safeSpeed - 14f) * 0.4
        }
        ActivityType.WALK -> when {
            safeSpeed < 3f -> 2.0 + (safeSpeed / 3f) * 0.5 // do 2.5
            safeSpeed < 5f -> 2.5 + (safeSpeed - 3f) * 0.5 // 2.5 do 3.5
            safeSpeed < 7f -> 3.5 + (safeSpeed - 5f) * 0.5 // 3.5 do 4.5
            else           -> 4.5 + (safeSpeed - 7f) * 0.3
        }
        ActivityType.SPRINT -> when {
            safeSpeed < 10f -> 8.0
            safeSpeed < 16f -> 8.0 + (safeSpeed - 10f) * 0.83 // 8 do 13
            safeSpeed < 20f -> 13.0 + (safeSpeed - 16f) * 0.75 // 13 do 16
            else            -> 16.0 + (safeSpeed - 20f) * 0.5
        }
        ActivityType.HIKE -> 6.0 + (elevationGainM / 100f) * 0.5 // Base 6.0 + gain
        ActivityType.CYCLING -> when {
            safeSpeed < 15f -> 4.0
            safeSpeed < 20f -> 6.0
            safeSpeed < 25f -> 8.0
            else            -> 10.0
        }
        else -> 5.0
    }.toDouble()

    // 2. Trajanje v urah
    val hours = durationSeconds / 3600.0
    // 3. Težo omejimo med 30kg in 200kg zaradi napak v profilu
    val safeWeight = weightKg.coerceIn(30.0, 200.0)

    val base = met * safeWeight * hours
    // Vzpon: ~0.8 kcal/m za povprečno težo 70 kg, skalira z dejansko težo. Omejen poskok GPS baz (max 2000m na log).
    val safeElevation = elevationGainM.coerceIn(0f, 2000f)
    val elevBonus = safeElevation * 0.8 * (safeWeight / 70.0)
    return (base + elevBonus).toInt().coerceAtLeast(0)
}

private fun calculateXP(activityType: ActivityType, distanceMeters: Double, timeSeconds: Long): Int {
    val baseFromDistance = distanceMeters / 100.0
    val baseFromTime = timeSeconds / 60.0
    val multiplier = when (activityType) {
        ActivityType.RUN, ActivityType.SPRINT -> 1.5
        ActivityType.HIKE, ActivityType.NORDIC -> 1.3
        ActivityType.WALK -> 1.0
        ActivityType.CYCLING -> 0.6
        ActivityType.SKIING, ActivityType.SNOWBOARD -> 0.5
        ActivityType.SKATING -> 1.0
    }
    return ((baseFromDistance + baseFromTime) * multiplier).toInt().coerceAtLeast(10)
}

@Composable
fun RunTrackerScreen(onBackPressed: () -> Unit, userProfile: UserProfile = UserProfile()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val config = Configuration.getInstance()
        config.userAgentValue = context.packageName
        config.cacheMapTileCount = 16.toShort()
        config.cacheMapTileOvershoot = 16.toShort()
    }

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
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showSummary by remember { mutableStateOf(false) }
    var isSavingActivity by remember { mutableStateOf(false) }
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var isMapFollowingTarget by remember { mutableStateOf(true) }
    var finalDistance by remember { mutableStateOf(0.0) }
    var finalTime by remember { mutableStateOf(0L) }
    var finalMaxSpeed by remember { mutableStateOf(0f) }
    var finalAvgSpeed by remember { mutableStateOf(0f) }
    var finalElevationGain by remember { mutableStateOf(0f) }
    var finalElevationLoss by remember { mutableStateOf(0f) }
    var actualWeightKg by remember { mutableStateOf(70.0) }
    var userDocRef by remember { mutableStateOf<com.google.firebase.firestore.DocumentReference?>(null) }

    LaunchedEffect(Unit) {
        try {
            val resolvedRef = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
            userDocRef = resolvedRef
            val snap = resolvedRef
                .collection("weightLogs")
                .orderBy("date", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            val w = snap.documents.firstOrNull()?.get("weightKg") as? Number
            if (w != null) actualWeightKg = w.toDouble()
        } catch (_: Exception) {}
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

    val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    var routeSmoothingEnabled by remember { mutableStateOf(prefs.getBoolean("route_smoothing", true)) }

    var isTopoMap by remember { mutableStateOf(prefs.getBoolean("topo_map", false)) }

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
                
                if (isMapFollowingTarget) {
                    map.controller.animateTo(GeoPoint(locationPoints.last().latitude, locationPoints.last().longitude))
                }
                
                if (map.zoomLevelDouble < 16.0) map.controller.setZoom(17.0)
                map.invalidate()
            }
        }
    }

    val onMapTouch = {
        if (isMapFollowingTarget) isMapFollowingTarget = false
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Mapa
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(if (isTopoMap) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true); controller.setZoom(17.0)
                    val overlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        enableMyLocation(); enableFollowLocation(); setDrawAccuracyEnabled(true)
                    }
                    overlays.add(overlay); myLocationOverlay = overlay
                    controller.setCenter(GeoPoint(46.0569, 14.5058))
                    overlay.runOnFirstFix { post { overlay.myLocation?.let { controller.animateTo(it) } } }
                    mapView = this

                    setOnTouchListener { v, event ->
                        if (event.action == android.view.MotionEvent.ACTION_DOWN || event.action == android.view.MotionEvent.ACTION_MOVE) {
                            onMapTouch()
                            overlay.disableFollowLocation()
                        }
                        v.performClick()
                        false
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.onResume()
                val currentSource = if (isTopoMap) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK
                if (map.tileProvider.tileSource != currentSource) {
                    map.setTileSource(currentSource)
                }

                map.overlayManager.tilesOverlay.setColorFilter(null)
            }
        )

        // Map Type Toggle (vedno na istem mestu, zgoraj desno, nad re-center)
        SmallFloatingActionButton(
            onClick = {
                isTopoMap = !isTopoMap
                prefs.edit().putBoolean("topo_map", isTopoMap).apply()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Icon(Icons.Filled.Map, contentDescription = "Toggle Map Type")
        }

        // Re-center button (pod gumbom za tip mape)
        if (!isMapFollowingTarget) {
            SmallFloatingActionButton(
                onClick = {
                    isMapFollowingTarget = true
                    myLocationOverlay?.enableFollowLocation()
                    locationPoints.lastOrNull()?.let {
                        mapView?.controller?.animateTo(GeoPoint(it.latitude, it.longitude))
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 16.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Re-center map")
            }
        }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selectedActivity.emoji} ${selectedActivity.label}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (isTracking) {
                        Spacer(Modifier.width(8.dp))
                        val (gpsProfile, gpsColor) = when (selectedActivity) {
                            ActivityType.SPRINT, ActivityType.RUN -> "High accuracy" to Color(0xFF4CAF50)
                            ActivityType.CYCLING, ActivityType.SKIING, ActivityType.SNOWBOARD, ActivityType.SKATING -> "Balanced" to MaterialTheme.colorScheme.tertiary
                            ActivityType.WALK, ActivityType.HIKE, ActivityType.NORDIC -> "Battery saver" to Color(0xFF9E9E9E)
                        }
                        Surface(
                            color = gpsColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, gpsColor.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "GPS: $gpsProfile",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = gpsColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

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
                    Text("🔥 $liveCalories kcal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary)
                    Text(
                        if (isPaused) "Paused" else "Tracking...",
                        fontSize = 12.sp,
                        color = if (isPaused) MaterialTheme.colorScheme.tertiary else Color(0xFF4CAF50),
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
                        Icon(Icons.Filled.ArrowDropDown, "Izberi aktivnost", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Picker lista — horizontalni scroll
                    if (showActivityPicker) {
                        Spacer(Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(items = ActivityType.entries, key = { it.name }) { type ->
                                val isSelected = type == selectedActivity
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
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
                                colors = ButtonDefaults.buttonColors(containerColor = if (isPaused) Color(0xFF4CAF50) else MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(12.dp)
                            ) { Icon(if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null); Spacer(Modifier.width(8.dp)); Text(if (isPaused) "Resume" else "Pause", fontSize = 16.sp, fontWeight = FontWeight.Bold) }

                            Button(
                                onClick = {
                                    if (distanceMeters < 20.0) {
                                        context.startService(Intent(context, RunTrackingService::class.java).apply { action = RunTrackingService.ACTION_PAUSE })
                                        showDiscardDialog = true
                                        return@Button
                                    }

                                    finalDistance = distanceMeters; finalTime = elapsedSeconds
                                    finalMaxSpeed = maxSpeed; finalAvgSpeed = avgSpeed
                                    finalElevationGain = elevationGain; finalElevationLoss = elevationLoss
                                    
                                    // ADDED: Samodejni predlog aktivnosti na podlagi hitrosti
                                    val currentAvgKmh = avgSpeed * 3.6f
                                    if (finalDistance > 100 && currentAvgKmh > 0) {
                                        if (selectedActivity == ActivityType.RUN && currentAvgKmh < 6.0f) {
                                            selectedActivity = ActivityType.WALK
                                            com.example.myapplication.utils.AppToast.showSuccess(context, "Activity changed to Walk based on speed (${String.format(java.util.Locale.US, "%.1f", currentAvgKmh)} km/h)")
                                        } else if (selectedActivity == ActivityType.WALK && currentAvgKmh > 7.5f) {
                                            selectedActivity = ActivityType.RUN
                                            com.example.myapplication.utils.AppToast.showSuccess(context, "Activity changed to Run based on speed (${String.format(java.util.Locale.US, "%.1f", currentAvgKmh)} km/h)")
                                        }
                                    }

                                    context.startService(Intent(context, RunTrackingService::class.java).apply { action = RunTrackingService.ACTION_STOP })

                                    showSummary = true
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                                shape = RoundedCornerShape(12.dp)
                            ) { Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(8.dp)); Text("Stop", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }

                        showSummary -> Spacer(Modifier.height(56.dp))
                    }
                }
            }
        }

        // Summary overlay
        if (showSummary) {
            val xpEarned = calculateXP(selectedActivity, finalDistance, finalTime)

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
                            color = MaterialTheme.colorScheme.primary
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

                        SummaryRow("🌟 XP Earned", "$xpEarned XP", highlight = true)

                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = routeSmoothingEnabled,
                                onCheckedChange = {
                                    routeSmoothingEnabled = it
                                    prefs.edit().putBoolean("route_smoothing", it).apply()
                                }
                            )
                            Text("Route smoothing (Snap-to-road)", fontSize = 14.sp)
                        }

                        Button(
                            onClick = {
                                if (isSavingActivity) return@Button
                                isSavingActivity = true
                                val sessionId = java.util.UUID.randomUUID().toString()
                                val avgKmh = finalAvgSpeed * 3.6f
                                val calories = calculateCaloriesMet(selectedActivity, finalTime, actualWeightKg, finalElevationGain, avgKmh)

                                scope.launch(Dispatchers.IO) {
                                    val mappedLocationPoints = locationPoints.map { loc ->
                                        com.example.myapplication.data.LocationPoint(
                                            latitude = loc.latitude,
                                            longitude = loc.longitude,
                                            altitude = loc.altitude,
                                            speed = loc.speed,
                                            accuracy = loc.accuracy,
                                            timestamp = loc.time
                                        )
                                    }

                                    var finalLocationPoints = mappedLocationPoints
                                    var successfullySmoothed = false
                                    if (routeSmoothingEnabled) {
                                        val isWalkingProfile = selectedActivity == ActivityType.RUN || selectedActivity == ActivityType.WALK || selectedActivity == ActivityType.HIKE
                                        // Opravimo glajenje poti za vgrajene tipe
                                        if (isWalkingProfile || selectedActivity == ActivityType.CYCLING || selectedActivity == ActivityType.SPRINT) {
                                            finalLocationPoints = com.example.myapplication.map.MapboxMapMatcher.matchRoute(mappedLocationPoints, isWalkingProfile)
                                            if (finalLocationPoints !== mappedLocationPoints) {
                                                successfullySmoothed = true
                                            }
                                        }
                                    }

                                    val resolvedDocRef = userDocRef ?: runCatching {
                                        com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocRef()
                                    }.getOrNull()

                                    if (resolvedDocRef != null) {
                                        val avgSpeedMps = if (finalTime > 0) (finalDistance / finalTime).toFloat() else 0f
                                        val runMap = hashMapOf<String, Any>(
                                            "id" to sessionId, "userId" to resolvedDocRef.id,
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
                                            "polylinePoints" to finalLocationPoints.map { mapOf("lat" to it.latitude, "lng" to it.longitude, "alt" to it.altitude, "spd" to it.speed, "acc" to it.accuracy, "ts" to it.timestamp) },
                                            "isSmoothed" to successfullySmoothed
                                        )
                                        Log.d("RunTrackerSave", "WRITE runSessions doc=${resolvedDocRef.id} session=$sessionId points=${finalLocationPoints.size}")
                                        com.example.myapplication.persistence.FirestoreHelper.withRetry {
                                            resolvedDocRef.collection("runSessions").document(sessionId).set(runMap).await()
                                        }
                                        Log.i("RunTrackerSave", "WRITE_OK runSessions doc=${resolvedDocRef.id} session=$sessionId")

                                        val shareActSnap = resolvedDocRef.get().await()
                                        val shareAct = shareActSnap.getBoolean("share_activities") ?: false
                                        if (shareAct && finalLocationPoints.isNotEmpty()) {
                                            val rawPts = finalLocationPoints.map { Pair(it.latitude, it.longitude) }
                                            val compressRoute = CompressRouteUseCase()
                                        val compressed = compressRoute(rawPts)
                                            val routeList = compressed.map { mapOf("lat" to it.first, "lng" to it.second) }
                                            val pubMap = hashMapOf<String, Any>(
                                                "activityType" to selectedActivity.name,
                                                "distanceMeters" to finalDistance,
                                                "elevationGainM" to finalElevationGain,
                                                "elevationLossM" to finalElevationLoss,
                                                "avgSpeedMps" to avgSpeedMps,
                                                "maxSpeedMps" to finalMaxSpeed,
                                                "startTime" to (System.currentTimeMillis() - finalTime * 1000L),
                                                "routePoints" to routeList
                                            )
                                            Log.d("RunTrackerSave", "WRITE publicActivities doc=${resolvedDocRef.id} session=$sessionId routePts=${routeList.size}")
                                            com.example.myapplication.persistence.FirestoreHelper.withRetry {
                                                resolvedDocRef.collection("publicActivities").document(sessionId).set(pubMap).await()
                                            }
                                            Log.i("RunTrackerSave", "WRITE_OK publicActivities doc=${resolvedDocRef.id} session=$sessionId")
                                        }
                                    }

                                    // VEDNO shranimo originalne (raw) točke, da ohranimo pravilen hitrostni graf in meritve!
                                    if (mappedLocationPoints.isNotEmpty()) {
                                        com.example.myapplication.persistence.RunRouteStore.saveRoute(context, sessionId, mappedLocationPoints)
                                    }
                                    // Če je glajenje uspelo, shranimo dodaten file samo za risanje čudovite poti na mapi
                                    if (successfullySmoothed && finalLocationPoints.isNotEmpty()) {
                                        com.example.myapplication.persistence.RunRouteStore.saveRoute(context, sessionId + "_smoothed", finalLocationPoints)
                                    }

                                    val xp = calculateXP(selectedActivity, finalDistance, finalTime)
                                    val runEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                                    if (runEmail != null) {
                                        val useCase = com.example.myapplication.domain.gamification.ManageGamificationUseCase(com.example.myapplication.data.gamification.FirestoreGamificationRepository())
                                        useCase.awardXP(xp, "RUN_COMPLETED")

                                        val bodyVm = androidx.lifecycle.ViewModelProvider(
                                            context as androidx.lifecycle.ViewModelStoreOwner,
                                            com.example.myapplication.ui.screens.MyViewModelFactory(context.applicationContext)
                                        ).get(com.example.myapplication.viewmodels.BodyModuleHomeViewModel::class.java)
                                        val uiState = bodyVm.ui.value
                                        val currentDay = uiState.planDay
                                        if (!uiState.isWorkoutDoneToday && !uiState.todayIsRest && finalDistance > 1000) {
                                            bodyVm.handleIntent(com.example.myapplication.viewmodels.BodyHomeIntent.CompleteWorkoutSession(
                                                email = uiState.errorMessage ?: "", // TODO: proper email if needed
                                                isExtraWorkout = false,
                                                totalKcal = calories,
                                                totalTimeMin = finalTime / 60.0
                                            ))
                                            withContext(Dispatchers.Main) {
                                                com.example.myapplication.utils.AppToast.showSuccess(context, "Workout Day $currentDay Complete! +$xp XP!")
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                com.example.myapplication.utils.AppToast.showSuccess(context, "+$xp XP!")
                                            }
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        showSummary = false; onBackPressed()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
                            enabled = !isSavingActivity,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isSavingActivity) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text("Done", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
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

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Activity Too Short", fontWeight = FontWeight.Bold) },
            text = { Text("You have recorded less than 20m. Your activity will not be saved if you finish now. Do you want to discard it?") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    context.startService(Intent(context, RunTrackingService::class.java).apply { action = RunTrackingService.ACTION_STOP })
                    onBackPressed()
                }) { Text("Discard", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Cancel") }
            }
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
            color = if (highlight) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun fmtTime(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
