package com.example.myapplication.viewmodels

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.LocationPoint
import com.example.myapplication.data.RunSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import java.util.UUID

/**
 * ViewModel for managing run tracking state and operations.
 */
class RunTrackerViewModel : ViewModel() {

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking

    private val _locationPoints = MutableStateFlow<List<LocationPoint>>(emptyList())
    val locationPoints: StateFlow<List<LocationPoint>> = _locationPoints

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds

    private val _distanceMeters = MutableStateFlow(0.0)
    val distanceMeters: StateFlow<Double> = _distanceMeters

    private val _maxSpeed = MutableStateFlow(0.0f)
    val maxSpeed: StateFlow<Float> = _maxSpeed

    private val _avgSpeed = MutableStateFlow(0.0f)
    val avgSpeed: StateFlow<Float> = _avgSpeed

    private val _currentSession = MutableStateFlow<RunSession?>(null)
    val currentSession: StateFlow<RunSession?> = _currentSession

    private var startTimeMs = 0L
    private var timerJob: kotlinx.coroutines.Job? = null

    /**
     * Start a new tracking session.
     */
    fun startTracking() {
        if (_isTracking.value) return

        _isTracking.value = true
        _locationPoints.value = emptyList()
        _elapsedSeconds.value = 0
        _distanceMeters.value = 0.0
        _maxSpeed.value = 0.0f
        _avgSpeed.value = 0.0f
        startTimeMs = System.currentTimeMillis()

        // Start timer
        startTimer()
    }

    private suspend fun loadRunCoefficients(context: android.content.Context): Map<String, Double> {
        return try {
            val input = context.assets.open("runs_coefficients.json")
            val json = input.bufferedReader().use { it.readText() }
            val obj = org.json.JSONObject(json)
            // Expecting keys like "slow_kmh", "moderate_kmh", "fast_kmh" mapping to kcal per kg per minute
            mapOf(
                "slow" to obj.optDouble("slow_kmh", 0.07),
                "moderate" to obj.optDouble("moderate_kmh", 0.10),
                "fast" to obj.optDouble("fast_kmh", 0.13)
            )
        } catch (e: Exception) {
            // Fallback coefficients (approx MET 5/8/10 scaled): kcal/min = coef * weightKg
            mapOf(
                "slow" to 0.07,   // ~4.2 kcal/min @ 60kg
                "moderate" to 0.10, // ~6 kcal/min @ 60kg
                "fast" to 0.13    // ~7.8 kcal/min @ 60kg
            )
        }
    }

    private fun categorizeSpeedKmh(kmh: Float): String {
        return when {
            kmh < 5.0f -> "slow"
            kmh < 9.0f -> "moderate"
            else -> "fast"
        }
    }

    private fun getUserWeightKg(): Double {
        // TODO: read from user preferences or Firestore; fallback to 70kg
        return 70.0
    }

    /**
     * Stop tracking and save session to Firestore.
     */
    fun stopTracking() {
        if (!_isTracking.value) return

        _isTracking.value = false
        timerJob?.cancel()

        val userId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: return
        val endTimeMs = System.currentTimeMillis()
        val durationSecs = _elapsedSeconds.value

        // Calculate calories from avg speed and duration
        val avgSpeedKmh = (avgSpeed.value) * 3.6f
        val category = categorizeSpeedKmh(avgSpeedKmh)
        val weightKg = getUserWeightKg()

        // Since we need context for assets, use FirebaseApp context fallback
        val appContext = com.google.firebase.FirebaseApp.getInstance().applicationContext

        viewModelScope.launch {
            val coefs = loadRunCoefficients(appContext)
            val coefPerMin = coefs[category] ?: 0.10
            val minutes = durationSecs / 60.0
            val calories = kotlin.math.round(minutes * coefPerMin * weightKg).toInt()

            val session = RunSession(
                id = java.util.UUID.randomUUID().toString(),
                userId = userId,
                startTime = startTimeMs,
                endTime = endTimeMs,
                durationSeconds = durationSecs,
                distanceMeters = _distanceMeters.value,
                maxSpeedMps = _maxSpeed.value,
                avgSpeedMps = _avgSpeed.value,
                polylinePoints = _locationPoints.value,
                caloriesKcal = calories
            )

            _currentSession.value = session
            saveSessionToFirestore(session)
        }
    }

    /**
     * Add a new location point to the current session.
     */
    fun addLocationPoint(location: Location) {
        if (!_isTracking.value) return

        val newPoint = LocationPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            speed = location.speed,
            accuracy = location.accuracy,
            timestamp = location.time
        )

        val currentPoints = _locationPoints.value.toMutableList()
        currentPoints.add(newPoint)
        _locationPoints.value = currentPoints

        // Update distance and speeds
        updateStats(currentPoints)
    }

    /**
     * Calculate distance and speed stats from location points.
     */
    private fun updateStats(points: List<LocationPoint>) {
        if (points.isEmpty()) return

        // Calculate total distance
        var totalDistance = 0.0
        var maxSpeedVal = 0.0f

        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val curr = points[i]
            val distance = haversineDistance(
                prev.latitude, prev.longitude,
                curr.latitude, curr.longitude
            )
            totalDistance += distance
            maxSpeedVal = maxOf(maxSpeedVal, curr.speed)
        }

        _distanceMeters.value = totalDistance
        _maxSpeed.value = maxSpeedVal

        // Calculate average speed
        if (_elapsedSeconds.value > 0) {
            _avgSpeed.value = (totalDistance / _elapsedSeconds.value).toFloat()
        }
    }

    /**
     * Haversine formula to calculate distance between two lat/lng points.
     * Returns distance in meters.
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0  // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    /**
     * Start the elapsed time timer.
     */
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_isTracking.value) {
                _elapsedSeconds.value += 1
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    /**
     * Save the run session to Firestore.
     */
    private fun saveSessionToFirestore(session: RunSession) {
        val userId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: return
        val appContext = com.google.firebase.FirebaseApp.getInstance().applicationContext

        firestore.collection("users")
            .document(userId)
            .collection("runSessions")
            .document(session.id)
            .set(session.toFirestoreMap())
            .addOnSuccessListener {
                // XP za tek prek AchievementStore — sproži badge preverjanje
                val xpForRun = (session.caloriesKcal / 5).toInt().coerceAtLeast(50)
                val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: return@addOnSuccessListener
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.myapplication.persistence.AchievementStore.awardXP(
                        appContext, userEmail, xpForRun,
                        com.example.myapplication.data.XPSource.RUN_COMPLETED,
                        "Run: ${"%.1f".format(session.distanceMeters / 1000.0)} km"
                    )
                }
            }
            .addOnFailureListener { e ->
                // Handle error
                e.printStackTrace()
            }
    }

    /**
     * Load past run sessions from Firestore.
     */
    fun loadRunSessions(onResult: (List<RunSession>) -> Unit) {
        val userId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: return

        firestore.collection("users")
            .document(userId)
            .collection("runSessions")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val sessions = snapshot.documents.mapNotNull { doc ->
                    try {
                        val data = doc.data ?: return@mapNotNull null
                        RunSession(
                            id = data["id"] as? String ?: "",
                            userId = data["userId"] as? String ?: "",
                            startTime = (data["startTime"] as? Number)?.toLong() ?: 0L,
                            endTime = (data["endTime"] as? Number)?.toLong() ?: 0L,
                            durationSeconds = (data["durationSeconds"] as? Number)?.toInt() ?: 0,
                            distanceMeters = (data["distanceMeters"] as? Number)?.toDouble() ?: 0.0,
                            maxSpeedMps = (data["maxSpeedMps"] as? Number)?.toFloat() ?: 0.0f,
                            avgSpeedMps = (data["avgSpeedMps"] as? Number)?.toFloat() ?: 0.0f,
                            polylinePoints = emptyList(),
                            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                            caloriesKcal = (data["caloriesKcal"] as? Number)?.toInt() ?: 0
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
                onResult(sessions)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                onResult(emptyList())
            }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }
}
