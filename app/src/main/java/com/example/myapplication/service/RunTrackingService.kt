package com.example.myapplication.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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

/**
 * Foreground Service for tracking running/walking activity in the background.
 * This service keeps GPS tracking active even when the phone is locked or the app is in background.
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
    private val ELEVATION_THRESHOLD_M = 3f // min change to count

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
    private var speedReadings = mutableListOf<Float>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        instance = this

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        pressureSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service onBind")
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_START -> startTracking()
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
        // Intent to open the app when notification is clicked
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RunTrackingService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Pause/Resume action
        val pauseResumeIntent = PendingIntent.getService(
            this,
            2,
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
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopIntent
            )
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    @SuppressLint("MissingPermission", "WakelockTimeout")
    private fun startTracking() {
        if (_isTracking.value) {
            Log.d(TAG, "Already tracking, ignoring start request")
            return
        }

        Log.d(TAG, "Starting tracking")

        // Acquire wake lock to keep CPU running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RunTrackingService::WakeLock"
        ).apply {
            acquire()
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

        // Start foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Create location request - HIGH ACCURACY for running
        val locationRequest = LocationRequest.Builder(2000) // Update every 2 seconds
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(1000) // Minimum 1 second between updates
            .setMaxUpdateDelayMillis(3000) // Max delay 3 seconds
            .build()

        // Create location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (_isPaused.value) return

                for (location in locationResult.locations) {
                    processLocationUpdate(location)
                }
            }
        }

        // Start location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            mainLooper
        )

        // Start timer
        startTimer()

        _isTracking.value = true
        _isPaused.value = false

        Log.d(TAG, "Tracking started successfully")
    }

    private fun stopTracking() {
        Log.d(TAG, "Stopping tracking")

        _isTracking.value = false
        _isPaused.value = false

        // Stop timer
        timerJob?.cancel()
        timerJob = null

        // Unregister barometer
        sensorManager?.unregisterListener(barometerListener)

        // Stop location updates
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null

        // Release wake lock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null

        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.d(TAG, "Tracking stopped")
    }

    private fun pauseTracking() {
        Log.d(TAG, "Pausing tracking")
        _isPaused.value = true
        lastAltitudeM = null // reset barometer baseline on pause to avoid false elevation on resume
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

                // Update notification every 5 seconds to save battery
                if (_elapsedSeconds.value % 5 == 0L) {
                    updateNotification()
                }
            }
        }
    }

    private fun processLocationUpdate(location: Location) {
        Log.d(TAG, "Location update: ${location.latitude}, ${location.longitude}, accuracy: ${location.accuracy}")

        // Filter out inaccurate readings (accuracy > 20 meters)
        if (location.accuracy > 20) {
            Log.d(TAG, "Skipping inaccurate location: ${location.accuracy}m")
            return
        }

        // Add to location points
        val currentPoints = _locationPoints.value.toMutableList()
        currentPoints.add(location)
        _locationPoints.value = currentPoints

        // Calculate distance from last point
        lastLocation?.let { last ->
            val distance = last.distanceTo(location)

            // Filter out unrealistic jumps (> 100 meters between updates could be GPS error)
            if (distance < 100) {
                totalDistance += distance
                _distanceMeters.value = totalDistance
            } else {
                Log.d(TAG, "Skipping unrealistic distance jump: ${distance}m")
            }
        }
        lastLocation = location

        // Update speed
        if (location.hasSpeed()) {
            val speed = location.speed // m/s
            _currentSpeed.value = speed

            // Only count valid speeds (moving)
            if (speed > 0.5f) { // More than 0.5 m/s
                speedReadings.add(speed)

                // Update max speed
                if (speed > _maxSpeed.value) {
                    _maxSpeed.value = speed
                }

                // Update average speed
                if (speedReadings.isNotEmpty()) {
                    _avgSpeed.value = speedReadings.average().toFloat()
                }
            }
        }
    }

    private fun formatTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%02d:%02d".format(minutes, secs)
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
