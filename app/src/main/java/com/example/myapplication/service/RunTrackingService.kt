package com.example.myapplication.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.myapplication.MainActivity
import com.example.myapplication.R
import com.example.myapplication.data.ActivityType
import com.example.myapplication.data.LocationPoint
import com.example.myapplication.data.RunSession
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.local.OfflineFirstWorkoutRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Foreground Service for tracking running/walking activity in the background.
 * This service keeps GPS tracking active even when the phone is locked or the app is in background.
 *
 * Faza 15:
 *  - Android 14+ startForeground z FOREGROUND_SERVICE_TYPE_LOCATION
 *  - WakeLock timeout 4h
 *  - Checkpoint shranjevanje na vsakih 10 točk ali 50m (Room IN_PROGRESS)
 *  - OOM restart obnova iz Room IN_PROGRESS seje
 */
class RunTrackingService : Service() {

    companion object {
        private const val TAG = "RunTrackingService"
        private const val NOTIFICATION_CHANNEL_ID = "run_tracking_channel"
        private const val NOTIFICATION_ID = 1001

        // Actions
        const val ACTION_START = "com.example.myapplication.action.START_TRACKING"
        const val ACTION_STOP = "com.example.myapplication.action.STOP_TRACKING"
        const val ACTION_PAUSE = "com.example.myapplication.action.PAUSE_TRACKING"
        const val ACTION_RESUME = "com.example.myapplication.action.RESUME_TRACKING"
        const val EXTRA_ACTIVITY_TYPE = "extra_activity_type"

        // Faza 15: checkpoint parametri
        private const val CHECKPOINT_EVERY_N_POINTS = 10
        private const val CHECKPOINT_EVERY_M = 50.0

        // WakeLock timeout: 4 ure
        private const val WAKELOCK_TIMEOUT_MS = 4L * 60L * 60L * 1000L

        // Singleton instance for binding
        private var instance: RunTrackingService? = null
        fun getInstance(): RunTrackingService? = instance
    }

    // Binder for local binding
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): RunTrackingService = this@RunTrackingService
    }

    // Location tracking
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null

    // Barometer / elevation tracking
    private var sensorManager: SensorManager? = null
    private var pressureSensor: Sensor? = null
    private var lastAltitudeM: Float? = null
    private var totalElevationGainM = 0f
    private var totalElevationLossM = 0f
    private val ELEVATION_THRESHOLD_M = 3f

    private val barometerListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!_isTracking.value || _isPaused.value) return
            val pressureHpa = event.values[0]
            val altitudeM = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressureHpa)
            val last = lastAltitudeM
            if (last != null) {
                val diff = altitudeM - last
                if (diff >= ELEVATION_THRESHOLD_M) {
                    totalElevationGainM += diff
                    _elevationGain.value = totalElevationGainM
                    lastAltitudeM = altitudeM
                } else if (diff <= -ELEVATION_THRESHOLD_M) {
                    totalElevationLossM += (-diff)
                    _elevationLoss.value = totalElevationLossM
                    lastAltitudeM = altitudeM
                }
            } else {
                lastAltitudeM = altitudeM
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // Wake lock to prevent CPU from sleeping
    private var wakeLock: PowerManager.WakeLock? = null

    // Coroutine scope for timer
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var timerJob: Job? = null

    // State flows for UI updates
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val _locationPoints = MutableStateFlow<List<Location>>(emptyList())
    val locationPoints: StateFlow<List<Location>> = _locationPoints.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _distanceMeters = MutableStateFlow(0.0)
    val distanceMeters: StateFlow<Double> = _distanceMeters.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private val _maxSpeed = MutableStateFlow(0f)
    val maxSpeed: StateFlow<Float> = _maxSpeed.asStateFlow()

    private val _avgSpeed = MutableStateFlow(0f)
    val avgSpeed: StateFlow<Float> = _avgSpeed.asStateFlow()

    private val _elevationGain = MutableStateFlow(0f)
    val elevationGain: StateFlow<Float> = _elevationGain.asStateFlow()

    private val _elevationLoss = MutableStateFlow(0f)
    val elevationLoss: StateFlow<Float> = _elevationLoss.asStateFlow()

    // Tracking data
    private var lastLocation: Location? = null
    private var totalDistance = 0.0
    // Faza 15: omejen sliding window (max 500 vrednosti ≈ 15 min @ 2s intervalu)
    private val speedReadings = ArrayDeque<Float>(500)
    private var currentActivityType: ActivityType = ActivityType.RUN

    // Faza 15: Session tracking za checkpoints in OOM obnovo
    private lateinit var repository: OfflineFirstWorkoutRepository
    private var currentSessionId: String? = null
    private var sessionStartTime: Long = 0L
    // interni O(1) buffer za GPS točke (izognemo se O(N) kopiranju pri vsaki točki)
    private val locationBuffer = ArrayDeque<Location>()
    // Checkpoint sledenje
    private var pointsSinceLastCheckpoint: Int = 0
    private var distanceAtLastCheckpoint: Double = 0.0
    // Samo točke od zadnjega checkpointa (za delta vpis v Room)
    private val newPointsBuffer = ArrayDeque<Location>()

    private data class GpsProfile(
        val intervalMs: Long,
        val minIntervalMs: Long,
        val maxDelayMs: Long,
        val minDistanceM: Float
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        instance = this

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        createNotificationChannel()

        // Faza 15: repozitorij za checkpoint shranjevanje
        repository = OfflineFirstWorkoutRepository(AppDatabase.getInstance(this))
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: ${intent?.action}")

        if (intent == null) {
            // ── Faza 15: OOM restart ──────────────────────────────────────────
            // Android je ubil service in ga znova zagnal z intent=null.
            // Preberemo zadnjo IN_PROGRESS sejo iz Room in obnovimo stanje.
            Log.w(TAG, "OOM restart detected (intent=null) — trying to restore IN_PROGRESS session")
            serviceScope.launch {
                restoreFromInProgress()
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                currentActivityType = ActivityType.fromString(intent.getStringExtra(EXTRA_ACTIVITY_TYPE))
                startTracking()
            }
            ACTION_STOP -> stopTracking()
            ACTION_PAUSE -> pauseTracking()
            ACTION_RESUME -> resumeTracking()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service onDestroy")
        stopTracking()
        serviceJob.cancel()
        instance = null
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Run Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows notification while tracking your run"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RunTrackingService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseResumeIntent = PendingIntent.getService(
            this, 2,
            Intent(this, RunTrackingService::class.java).apply {
                action = if (_isPaused.value) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val distanceKm = _distanceMeters.value / 1000.0
        val timeFormatted = formatTime(_elapsedSeconds.value)
        val statusText = if (_isPaused.value) "Paused" else "Tracking"

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🏃 Run $statusText")
            .setContentText("Distance: ${"%.2f".format(distanceKm)} km • Time: $timeFormatted")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (_isPaused.value) "Resume" else "Pause",
                pauseResumeIntent
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    // Fix 1 & 2: odstranjen @SuppressLint("WakelockTimeout") + Android 14+ startForeground type
    @SuppressLint("MissingPermission")
    private fun startTracking() {
        if (_isTracking.value) {
            Log.d(TAG, "Already tracking, ignoring start request")
            return
        }

        Log.d(TAG, "Starting tracking")

        // Faza 15: nov session ID za to sejo
        currentSessionId = UUID.randomUUID().toString()
        sessionStartTime = System.currentTimeMillis()
        pointsSinceLastCheckpoint = 0
        distanceAtLastCheckpoint = 0.0
        locationBuffer.clear()
        newPointsBuffer.clear()

        // Fix 2: WakeLock timeout 4h (preprečuje Samsung battery kill)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RunTrackingService::WakeLock"
        ).apply {
            acquire(WAKELOCK_TIMEOUT_MS)
        }

        // Reset state
        _locationPoints.value = emptyList()
        _elapsedSeconds.value = 0
        _distanceMeters.value = 0.0
        _currentSpeed.value = 0f
        _maxSpeed.value = 0f
        _avgSpeed.value = 0f
        _elevationGain.value = 0f
        _elevationLoss.value = 0f
        lastLocation = null
        totalDistance = 0.0
        speedReadings.clear()
        lastAltitudeM = null
        totalElevationGainM = 0f
        totalElevationLossM = 0f

        // Register barometer sensor (if available)
        pressureSensor?.let { sensor ->
            sensorManager?.registerListener(barometerListener, sensor, SensorManager.SENSOR_DELAY_UI)
            Log.d(TAG, "Barometer sensor registered")
        } ?: Log.d(TAG, "No barometer sensor available")

        // Fix 1: Android 14+ (API 34) — obvezno navedi tip storitve
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        val gpsProfile = resolveGpsProfile(currentActivityType)
        Log.d(TAG, "GPS profile for ${currentActivityType.name}: interval=${gpsProfile.intervalMs}ms, min=${gpsProfile.minIntervalMs}ms, maxDelay=${gpsProfile.maxDelayMs}ms, minDistance=${gpsProfile.minDistanceM}m")

        val locationRequest = LocationRequest.Builder(gpsProfile.intervalMs)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(gpsProfile.minIntervalMs)
            .setMaxUpdateDelayMillis(gpsProfile.maxDelayMs)
            .setMinUpdateDistanceMeters(gpsProfile.minDistanceM)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (_isPaused.value) return
                for (location in locationResult.locations) {
                    processLocationUpdate(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, mainLooper)
        startTimer()

        _isTracking.value = true
        _isPaused.value = false

        Log.d(TAG, "Tracking started successfully for session $currentSessionId")
    }

    private fun stopTracking() {
        Log.d(TAG, "Stopping tracking")

        _isTracking.value = false
        _isPaused.value = false

        timerJob?.cancel()
        timerJob = null

        sensorManager?.unregisterListener(barometerListener)

        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null

        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null

        // Faza 15: ob normalnem zaustavitvi označi sejo kot COMPLETED
        currentSessionId?.let { sid ->
            serviceScope.launch(Dispatchers.IO) {
                try {
                    repository.markSessionCompleted(sid)
                    Log.d(TAG, "Session $sid označena kot COMPLETED")
                } catch (e: Exception) {
                    Log.e(TAG, "Napaka pri markSessionCompleted: ${e.message}")
                }
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.d(TAG, "Tracking stopped")
    }

    private fun pauseTracking() {
        Log.d(TAG, "Pausing tracking")
        _isPaused.value = true
        lastAltitudeM = null
        timerJob?.cancel()
        updateNotification()
    }

    private fun resumeTracking() {
        Log.d(TAG, "Resuming tracking")
        _isPaused.value = false
        startTimer()
        updateNotification()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && _isTracking.value && !_isPaused.value) {
                delay(1000)
                _elapsedSeconds.value++
                if (_elapsedSeconds.value % 5 == 0L) {
                    updateNotification()
                }
            }
        }
    }

    private fun processLocationUpdate(location: Location) {
        Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}")

        if (location.accuracy > 20) {
            Log.d(TAG, "Skipping inaccurate location: ${location.accuracy}m")
            return
        }

        // Faza 15: O(1) vpis v interni buffer
        locationBuffer.addLast(location)
        newPointsBuffer.addLast(location)
        // Posodobi StateFlow iz bufferja (UI potrebuje celotno listo za prikaz na karti)
        _locationPoints.value = locationBuffer.toList()

        // Calculate distance
        lastLocation?.let { last ->
            val distance = last.distanceTo(location)
            if (distance < 100) {
                totalDistance += distance
                _distanceMeters.value = totalDistance
            } else {
                Log.d(TAG, "Skipping unrealistic distance jump: ${distance}m")
            }
        }
        lastLocation = location

        // Update speed — sliding window max 500 vrednosti
        if (location.hasSpeed()) {
            val speed = location.speed
            _currentSpeed.value = speed
            if (speed > 0.5f) {
                if (speedReadings.size >= 500) speedReadings.removeFirst()
                speedReadings.addLast(speed)
                if (speed > _maxSpeed.value) _maxSpeed.value = speed
                if (speedReadings.isNotEmpty()) _avgSpeed.value = speedReadings.average().toFloat()
            }
        }

        // Fix 3: Checkpoint logika — vsake 10 točk ali vsakih 50m
        pointsSinceLastCheckpoint++
        val distanceSinceCheckpoint = totalDistance - distanceAtLastCheckpoint
        if (pointsSinceLastCheckpoint >= CHECKPOINT_EVERY_N_POINTS ||
            distanceSinceCheckpoint >= CHECKPOINT_EVERY_M) {
            triggerCheckpoint()
        }
    }

    // Fix 3: Checkpoint shranjevanje v Room (asinhrono, ne blokira GPS callback)
    private fun triggerCheckpoint() {
        val sessionId = currentSessionId ?: return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val now = System.currentTimeMillis()
        val elapsed = _elapsedSeconds.value

        // Kopiraj samo nove točke od zadnjega checkpointa
        val pointsToSave = newPointsBuffer.map { loc ->
            LocationPoint(
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = loc.altitude,
                speed = loc.speed,
                accuracy = loc.accuracy,
                timestamp = loc.time
            )
        }

        val session = RunSession(
            id = sessionId,
            userId = userId,
            startTime = sessionStartTime,
            endTime = now,
            durationSeconds = elapsed.toInt(),
            distanceMeters = totalDistance,
            maxSpeedMps = _maxSpeed.value,
            avgSpeedMps = _avgSpeed.value,
            polylinePoints = emptyList(), // GPS točke so v Room (GpsPointEntity)
            createdAt = sessionStartTime,
            caloriesKcal = 0, // izračuna se ob zaključku
            elevationGainM = totalElevationGainM,
            elevationLossM = totalElevationLossM,
            activityType = currentActivityType,
            isSmoothed = false
        )

        // Ponastavi checkpoint sledenje
        pointsSinceLastCheckpoint = 0
        distanceAtLastCheckpoint = totalDistance
        newPointsBuffer.clear()

        serviceScope.launch(Dispatchers.IO) {
            try {
                repository.saveCheckpoint(session, pointsToSave)
                Log.d(TAG, "Checkpoint OK: ${pointsToSave.size} točk, ${totalDistance.toInt()}m, ${elapsed}s")
            } catch (e: Exception) {
                Log.e(TAG, "Checkpoint napaka: ${e.message}")
            }
        }
    }

    // Fix 4: OOM restart — obnovi zadnjo IN_PROGRESS sejo iz Room
    private suspend fun restoreFromInProgress() {
        val entity = try {
            repository.getInProgressSession()
        } catch (e: Exception) {
            Log.e(TAG, "Napaka pri branju IN_PROGRESS: ${e.message}")
            null
        }

        if (entity == null) {
            Log.w(TAG, "OOM restart: ni IN_PROGRESS seje → service se ustavi")
            stopSelf()
            return
        }

        Log.d(TAG, "OOM restart: obnavljam sejo ${entity.id}, dist=${entity.distanceMeters}m")

        // Obnovi polja
        currentSessionId = entity.id
        sessionStartTime = entity.startTime
        currentActivityType = ActivityType.fromString(entity.activityType)
        totalDistance = entity.distanceMeters
        totalElevationGainM = entity.elevationGainM
        totalElevationLossM = entity.elevationLossM

        // Obnovi StateFlow vrednosti
        _distanceMeters.value = entity.distanceMeters
        _elevationGain.value = entity.elevationGainM
        _elevationLoss.value = entity.elevationLossM
        // Rekonstruiraj pretečen čas
        val restoredElapsed = (System.currentTimeMillis() - entity.startTime) / 1000L
        _elapsedSeconds.value = restoredElapsed

        // Obnovi GPS točke iz Room (za prikaz poti na karti)
        try {
            val storedPoints = repository.getGpsPoints(entity.id)
            val restoredLocations = storedPoints.map { lp ->
                Location("room_restored").apply {
                    latitude = lp.latitude
                    longitude = lp.longitude
                    altitude = lp.altitude
                    speed = lp.speed
                    accuracy = lp.accuracy
                    time = lp.timestamp
                }
            }
            locationBuffer.addAll(restoredLocations)
            _locationPoints.value = locationBuffer.toList()
            if (restoredLocations.isNotEmpty()) lastLocation = restoredLocations.last()
        } catch (e: Exception) {
            Log.e(TAG, "Napaka pri obnovi GPS točk: ${e.message}")
        }

        // Znova zaženi foreground service in GPS
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        @SuppressLint("MissingPermission")
        fun startGps() {
            val gpsProfile = resolveGpsProfile(currentActivityType)
            val locationRequest = LocationRequest.Builder(gpsProfile.intervalMs)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMinUpdateIntervalMillis(gpsProfile.minIntervalMs)
                .setMaxUpdateDelayMillis(gpsProfile.maxDelayMs)
                .setMinUpdateDistanceMeters(gpsProfile.minDistanceM)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    if (_isPaused.value) return
                    for (location in locationResult.locations) processLocationUpdate(location)
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, mainLooper)
        }
        startGps()
        startTimer()

        _isTracking.value = true
        _isPaused.value = false
        pointsSinceLastCheckpoint = 0
        distanceAtLastCheckpoint = totalDistance
        newPointsBuffer.clear()

        Log.d(TAG, "OOM obnova uspešna: seja ${entity.id}, ${restoredElapsed}s, ${totalDistance.toInt()}m")
    }

    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
        else "%02d:%02d".format(minutes, secs)
    }

    private fun resolveGpsProfile(activityType: ActivityType): GpsProfile {
        return when (activityType) {
            ActivityType.SPRINT -> GpsProfile(intervalMs = 1000, minIntervalMs = 500, maxDelayMs = 1500, minDistanceM = 0f)
            ActivityType.RUN -> GpsProfile(intervalMs = 2000, minIntervalMs = 1000, maxDelayMs = 3000, minDistanceM = 1.5f)
            ActivityType.CYCLING,
            ActivityType.SKATING -> GpsProfile(intervalMs = 3000, minIntervalMs = 1500, maxDelayMs = 5000, minDistanceM = 3f)
            ActivityType.WALK,
            ActivityType.HIKE,
            ActivityType.NORDIC -> GpsProfile(intervalMs = 6000, minIntervalMs = 3000, maxDelayMs = 9000, minDistanceM = 6f)
            ActivityType.SKIING,
            ActivityType.SNOWBOARD -> GpsProfile(intervalMs = 4000, minIntervalMs = 2000, maxDelayMs = 6000, minDistanceM = 4f)
        }
    }

    // Public methods for UI to call
    fun getTrackingData(): TrackingData {
        return TrackingData(
            isTracking = _isTracking.value,
            isPaused = _isPaused.value,
            locationPoints = _locationPoints.value,
            elapsedSeconds = _elapsedSeconds.value,
            distanceMeters = _distanceMeters.value,
            currentSpeed = _currentSpeed.value,
            maxSpeed = _maxSpeed.value,
            avgSpeed = _avgSpeed.value,
            elevationGainM = _elevationGain.value,
            elevationLossM = _elevationLoss.value
        )
    }
}

/**
 * Data class to hold all tracking data
 */
data class TrackingData(
    val isTracking: Boolean,
    val isPaused: Boolean,
    val locationPoints: List<Location>,
    val elapsedSeconds: Long,
    val distanceMeters: Double,
    val currentSpeed: Float,
    val maxSpeed: Float,
    val avgSpeed: Float,
    val elevationGainM: Float = 0f,
    val elevationLossM: Float = 0f
)
