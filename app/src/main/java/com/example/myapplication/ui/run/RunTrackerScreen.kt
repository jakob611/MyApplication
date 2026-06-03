package com.example.myapplication.ui.run
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Paint
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.domain.model.ActivityType
import com.example.myapplication.domain.model.LocationPoint
import com.example.myapplication.domain.model.ProfileConstants
import com.example.myapplication.data.settings.UserPreferencesRepository
import com.example.myapplication.domain.run.MapboxMapMatcher
import com.example.myapplication.data.store.FirestoreHelper
import com.example.myapplication.data.store.RunRouteStore
import com.example.myapplication.core.service.RunTrackingService
import com.example.myapplication.ui.screens.MyViewModelFactory
import com.example.myapplication.utils.AppToast
import com.example.myapplication.domain.run.RouteCompressor
import com.example.myapplication.viewmodels.BodyHomeIntent
import com.example.myapplication.ui.theme.UppColors
import com.example.myapplication.viewmodels.BodyModuleHomeViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * K-3 Fix: calculateCaloriesMet() ODSTRANJENA iz UI plasti.
 * Kalorije se zdaj izračunavajo IZKLJUČNO prek RunTrackerViewModel.calculateLiveCalories()
 * in RunTrackerViewModel.saveCurrentRunSession() → CalculateRunCaloriesUseCase.
 * Screen ne vsebuje nobene poslovne logike za energetske izračune.
 */

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
fun RunTrackerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel: RunTrackerViewModel = viewModel(
        factory = MyViewModelFactory(context)
    )
    val preferencesRepo = remember { UserPreferencesRepository(context) }

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
    var routePolyline by remember { mutableStateOf<Polyline?>(null) }
    var isMapFollowingTarget by remember { mutableStateOf(true) }
    var finalDistance by remember { mutableStateOf(0.0) }
    var finalTime by remember { mutableStateOf(0L) }
    var finalMaxSpeed by remember { mutableStateOf(0f) }
    var finalAvgSpeed by remember { mutableStateOf(0f) }
    var finalElevationGain by remember { mutableStateOf(0f) }
    var finalElevationLoss by remember { mutableStateOf(0f) }
    // V-3 Fix: DEFAULT_WEIGHT_KG (75.0 — WHO median) namesto hardcoded 70.0
    var actualWeightKg by remember { mutableStateOf(ProfileConstants.DEFAULT_WEIGHT_KG) }
    // Pre-loaded iz Firestore — posredovano v ViewModel.saveCurrentRunSession()
    var shareActivities by remember { mutableStateOf(false) }
    // isSavingActivity iz ViewModel StateFlow (UDF) — ne lokalni remember state
    val isSavingActivity by viewModel.isSaving.collectAsState()

    LaunchedEffect(Unit) {
        try {
            val resolvedRef = FirestoreHelper.getCurrentUserDocRef()
            // Naloži zadnjo telesno težo iz weightLogs za kalorični izračun
            val snap = resolvedRef
                .collection("weightLogs")
                .orderBy("date", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            val w = snap.documents.firstOrNull()?.get("weightKg") as? Number
            if (w != null) actualWeightKg = w.toDouble()
            // Naloži preference za deljenje aktivnosti (potrebno za ViewModel.saveCurrentRunSession)
            val userSnap = resolvedRef.get().await()
            shareActivities = userSnap.getBoolean("share_activities") ?: false
        } catch (_: Exception) {}
    }

    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val svc = (binder as? RunTrackingService.LocalBinder)?.getService()
                service = svc; isBound = true
                // K-1 Fix: ViewModel dobi referenco na Service za branje activeSessionId
                if (svc != null) viewModel.onServiceConnected(svc)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null; isBound = false
                viewModel.onServiceDisconnected()
            }
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

    // Live kcal (prikazano med sledenjem) — K-3 Fix: delegirano v ViewModel UseCase
    val liveCalories = remember(elapsedSeconds, distanceMeters, avgSpeed, elevationGain, selectedActivity, actualWeightKg) {
        viewModel.calculateLiveCalories(
            activityType = selectedActivity,
            durationSeconds = elapsedSeconds,
            distanceKm = distanceMeters / 1000.0,
            elevationGainM = elevationGain,
            userWeightKg = actualWeightKg
        )
    }

    LaunchedEffect(locationPoints) {
        mapView?.let { map ->
            routePolyline?.let { map.overlays.remove(it) }
            if (locationPoints.isNotEmpty()) {
                val polyline = Polyline(map).apply {
                    // UPP Design: oranžna pot (#FF6411) — Figma spec
                    outlinePaint.color = android.graphics.Color.rgb(255, 100, 17)
                    outlinePaint.strokeWidth = 12f
                    outlinePaint.strokeCap = Paint.Cap.ROUND
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

    // ✅ Faza 15: Lifecycle-usklajeno upravljanje MapView.onResume/onPause
    // Prej je bil map.onResume() v `update` lambdi → klical se je ob VSAKI rekomposiciji → glitch
    // Zdaj: DisposableEffect(lifecycleOwner, mapView) doda observer ENKRAT ko mapView postane non-null
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, mapView) {
        val map = mapView
        if (map != null) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> map.onResume()
                    Lifecycle.Event.ON_PAUSE  -> map.onPause()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            // Pokliči onResume takoj, če je lifecycle že v RESUMED stanju (ob prvem vstopu)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                map.onResume()
            }
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        } else {
            onDispose {}
        }
    }

    Box(Modifier.fillMaxSize().background(UppColors.Background)) {
        // Mapa
        AndroidView(
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(if (isTopoMap) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    // ✅ Nastavi zoom in center TAKOJ ob kreaciji — preprečimo "skok kamere čez cel globus"
                    val lastLat = prefs.getFloat("last_run_lat", 46.0569f).toDouble()
                    val lastLng = prefs.getFloat("last_run_lng", 14.5058f).toDouble()
                    controller.setZoom(16.0)
                    controller.setCenter(GeoPoint(lastLat, lastLng))

                    val overlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                        enableMyLocation(); enableFollowLocation(); setDrawAccuracyEnabled(true)
                    }
                    overlays.add(overlay); myLocationOverlay = overlay

                    overlay.runOnFirstFix { post { overlay.myLocation?.let { controller.animateTo(it) } } }
                    mapView = this

                    setOnTouchListener { v, event ->
                        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
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
                // ❌ map.onResume() tukaj ODSTRANJEN — lifecycle observer (zgoraj) skrbi za to
                val currentSource = if (isTopoMap) TileSourceFactory.OpenTopo else TileSourceFactory.MAPNIK
                if (map.tileProvider.tileSource != currentSource) {
                    map.setTileSource(currentSource)
                }

                map.overlayManager.tilesOverlay.setColorFilter(
                    android.graphics.ColorMatrixColorFilter(
                        android.graphics.ColorMatrix(floatArrayOf(
                            -1f,  0f,  0f, 0f, 255f,
                             0f, -1f,  0f, 0f, 255f,
                             0f,  0f, -1f, 0f, 255f,
                             0f,  0f,  0f, 1f,   0f
                        ))
                    )
                ) // OSMDroid temna tema — invertirani barvni filter (UPP Dark Theme)
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
            colors = CardDefaults.cardColors(containerColor = UppColors.CardSurface.copy(alpha = 0.95f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) {
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
                            ActivityType.SPRINT, ActivityType.RUN -> "High accuracy" to UppColors.Orange
                            ActivityType.CYCLING, ActivityType.SKIING, ActivityType.SNOWBOARD, ActivityType.SKATING -> "Balanced" to MaterialTheme.colorScheme.tertiary
                            ActivityType.WALK, ActivityType.HIKE, ActivityType.NORDIC -> "Battery saver" to UppColors.MutedText
                        }
                        Surface(
                            color = gpsColor.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, gpsColor.copy(alpha = 0.5f))
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
                    Text("↑ ${"%.0f".format(elevationGain)} m  ↓ ${"%.0f".format(elevationLoss)} m", fontSize = 13.sp, color = UppColors.Blue)
                }
                if (isTracking) {
                    Text("🔥 $liveCalories kcal", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = UppColors.Orange)
                    Text(
                        if (isPaused) "Paused" else "Tracking...",
                        fontSize = 12.sp,
                        color = if (isPaused) UppColors.LightGray else UppColors.Orange,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Controls card (spodaj)
        Card(
            Modifier.align(Alignment.BottomCenter).padding(16.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = UppColors.CardSurface.copy(alpha = 0.95f)),
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
                                        color = if (isSelected) UppColors.White else MaterialTheme.colorScheme.onSurface
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
                            colors = ButtonDefaults.buttonColors(containerColor = UppColors.Orange),
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
                                colors = ButtonDefaults.buttonColors(containerColor = if (isPaused) UppColors.Orange else UppColors.LightGray.copy(alpha = 0.25f)),
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
                                            AppToast.showSuccess(context, "Activity changed to Walk based on speed (${String.format(
                                                Locale.US, "%.1f", currentAvgKmh)} km/h)")
                                        } else if (selectedActivity == ActivityType.WALK && currentAvgKmh > 7.5f) {
                                            selectedActivity = ActivityType.RUN
                                            AppToast.showSuccess(context, "Activity changed to Run based on speed (${String.format(
                                                Locale.US, "%.1f", currentAvgKmh)} km/h)")
                                        }
                                    }

                                    context.startService(Intent(context, RunTrackingService::class.java).apply { action = RunTrackingService.ACTION_STOP })

                                    showSummary = true
                                },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = UppColors.Error),
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
                        colors = CardDefaults.cardColors(containerColor = UppColors.CardSurface),
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
                                scope.launch {
                                    // ── 1. Pripravi surove GPS točke ─────────────────────────────
                                    val mappedLocationPoints = locationPoints.map { loc ->
                                        LocationPoint(
                                            latitude = loc.latitude,
                                            longitude = loc.longitude,
                                            altitude = loc.altitude,
                                            speed = loc.speed,
                                            accuracy = loc.accuracy,
                                            timestamp = loc.time
                                        )
                                    }

                                    // ── 2. Route smoothing — UI-side priprava podatkov ────────────
                                    var finalLocationPoints = mappedLocationPoints
                                    var successfullySmoothed = false
                                    if (routeSmoothingEnabled) {
                                        val isWalkingProfile = selectedActivity == ActivityType.RUN ||
                                                selectedActivity == ActivityType.WALK ||
                                                selectedActivity == ActivityType.HIKE
                                        if (isWalkingProfile || selectedActivity == ActivityType.CYCLING || selectedActivity == ActivityType.SPRINT) {
                                            withContext(Dispatchers.IO) {
                                                val smoothed = MapboxMapMatcher.matchRoute(mappedLocationPoints, isWalkingProfile)
                                                if (smoothed !== mappedLocationPoints) {
                                                    finalLocationPoints = smoothed
                                                    successfullySmoothed = true
                                                }
                                            }
                                        }
                                    }

                                    // ── 3. RDP kompresija za Firestore ────────────────────────────
                                    // Maratón (14.400 točk) → ≤500 točk (~25 KB), pod 1 MB limitom.
                                    val compressedPoints = withContext(Dispatchers.IO) {
                                        RouteCompressor.compress(finalLocationPoints)
                                            .map { mapOf("lat" to it.latitude, "lng" to it.longitude, "alt" to it.altitude, "spd" to it.speed, "acc" to it.accuracy, "ts" to it.timestamp) }
                                    }

                                    // ── 4. Delegate shranjevanja v ViewModel (K-1 + K-3 + S-5 Fix) ─
                                    // K-1: sessionId prihaja iz service.activeSessionId (prek ViewModel)
                                    // K-3: nobenega direktnega Firestore klica iz Screen-a
                                    // S-5: sessionStartTime prihaja iz service.activeSessionStartTime
                                    val sessionStartTime = service?.activeSessionStartTime
                                        ?: (System.currentTimeMillis() - finalTime * 1000L)
                                    val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                    val userId = FirestoreHelper.getCurrentUserDocId() ?: ""
                                    val avgSpeedMps = if (finalTime > 0) (finalDistance / finalTime).toFloat() else 0f

                                    val result = viewModel.saveCurrentRunSession(
                                        activityType = selectedActivity,
                                        distanceMeters = finalDistance,
                                        durationSeconds = finalTime,
                                        maxSpeedMps = finalMaxSpeed,
                                        avgSpeedMps = avgSpeedMps,
                                        elevationGainM = finalElevationGain,
                                        elevationLossM = finalElevationLoss,
                                        userWeightKg = actualWeightKg,
                                        compressedPoints = compressedPoints,
                                        rawPointCount = finalLocationPoints.size,
                                        isSmoothed = successfullySmoothed,
                                        userId = userId,
                                        sessionStartTime = sessionStartTime,
                                        date = todayDate,
                                        shareActivity = shareActivities
                                    )

                                    result.onSuccess { savedResult ->
                                        // ── 5. Shrani lokalne pot datoteke z definitinim sessionId ──
                                        // RunRouteStore je lokalna persistenca — ostane v Screen-u
                                        withContext(Dispatchers.IO) {
                                            if (mappedLocationPoints.isNotEmpty()) {
                                                RunRouteStore.saveRoute(context, savedResult.sessionId, mappedLocationPoints)
                                            }
                                            if (successfullySmoothed && finalLocationPoints.isNotEmpty()) {
                                                RunRouteStore.saveRoute(context, savedResult.sessionId + "_smoothed", finalLocationPoints)
                                            }
                                        }

                                        // ── 6. XP nagrajevanje ───────────────────────────────────────
                                        val xp = calculateXP(selectedActivity, finalDistance, finalTime)
                                        viewModel.awardRunXP(xp)

                                        val runEmail = FirebaseAuth.getInstance().currentUser?.email
                                        if (runEmail != null) {
                                            val bodyVm = ViewModelProvider(
                                                context as ViewModelStoreOwner,
                                                MyViewModelFactory(context.applicationContext)
                                            ).get(BodyModuleHomeViewModel::class.java)
                                            val uiState = bodyVm.ui.value
                                            val currentDay = uiState.metrics?.planDay ?: 1
                                            if (uiState.metrics?.isWorkoutDoneToday != true && uiState.metrics?.todayIsRest != true && finalDistance > 1000) {
                                                bodyVm.handleIntent(
                                                    BodyHomeIntent.CompleteWorkoutSession(
                                                    email = runEmail,
                                                    isExtraWorkout = false,
                                                    totalKcal = savedResult.caloriesKcal,
                                                    totalTimeMin = finalTime / 60.0
                                                ))
                                                withContext(Dispatchers.Main) {
                                                    AppToast.showSuccess(context, "Workout Day $currentDay Complete! +$xp XP!")
                                                }
                                            } else {
                                                withContext(Dispatchers.Main) {
                                                    AppToast.showSuccess(context, "+$xp XP!")
                                                }
                                            }
                                        }

                                        // ── 7. Shrani zadnjo GPS lokacijo za hiter začetni center mape ─
                                        val lastPt = finalLocationPoints.lastOrNull() ?: mappedLocationPoints.lastOrNull()
                                        if (lastPt != null) {
                                            prefs.edit()
                                                .putFloat("last_run_lat", lastPt.latitude.toFloat())
                                                .putFloat("last_run_lng", lastPt.longitude.toFloat())
                                                .apply()
                                        }

                                        withContext(Dispatchers.Main) {
                                            showSummary = false; onBack()
                                        }
                                    }.onFailure { e ->
                                        Log.e("RunTrackerSave", "❌ Shranjevanje spodletelo: ${e.message}", e)
                                        withContext(Dispatchers.Main) {
                                            AppToast.showSuccess(context, "Save failed — try again")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.8f).height(50.dp),
                            enabled = !isSavingActivity,
                            colors = ButtonDefaults.buttonColors(containerColor = UppColors.Orange),
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
                    onBack()
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
            color = if (highlight) UppColors.Orange else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun fmtTime(s: Long) = if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
