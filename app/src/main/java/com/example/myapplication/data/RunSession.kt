package com.example.myapplication.data

import com.google.firebase.firestore.GeoPoint
import java.time.LocalDateTime

/**
 * Data class for a single GPS location point during a run.
 */
data class LocationPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speed: Float = 0.0f,  // in m/s
    val accuracy: Float = 0.0f,
    val timestamp: Long = 0L
)

/**
 * Data class for a complete run session.
 */
data class RunSession(
    val id: String = "",
    val userId: String = "",
    val startTime: Long = 0L,  // timestamp in ms
    val endTime: Long = 0L,    // timestamp in ms
    val durationSeconds: Int = 0,
    val distanceMeters: Double = 0.0,
    val maxSpeedMps: Float = 0.0f,  // max speed in m/s
    val avgSpeedMps: Float = 0.0f,  // avg speed in m/s
    val polylinePoints: List<LocationPoint> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val caloriesKcal: Int = 0 // NEW: total calories for this run
) {
    /**
     * Calculate average speed from distance and duration.
     */
    fun calculateAvgSpeed(): Float {
        return if (durationSeconds > 0) {
            (distanceMeters / durationSeconds).toFloat()
        } else {
            0.0f
        }
    }

    /**
     * Convert speed from m/s to km/h.
     */
    fun speedMpsToKmh(mps: Float): Float = mps * 3.6f

    /**
     * Convert speed from m/s to km/h for max and avg.
     */
    fun getMaxSpeedKmh(): Float = speedMpsToKmh(maxSpeedMps)
    fun getAvgSpeedKmh(): Float = speedMpsToKmh(avgSpeedMps)

    /**
     * Get distance in km.
     */
    fun getDistanceKm(): Double = distanceMeters / 1000.0

    /**
     * Get duration formatted as HH:MM:SS.
     */
    fun formatDuration(): String {
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val secs = durationSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    /**
     * Convert to Firestore-compatible map for storage.
     */
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "userId" to userId,
        "startTime" to startTime,
        "endTime" to endTime,
        "durationSeconds" to durationSeconds,
        "distanceMeters" to distanceMeters,
        "maxSpeedMps" to maxSpeedMps,
        "avgSpeedMps" to avgSpeedMps,
        "polylinePoints" to polylinePoints.map { point ->
            mapOf(
                "latitude" to point.latitude,
                "longitude" to point.longitude,
                "altitude" to point.altitude,
                "speed" to point.speed,
                "accuracy" to point.accuracy,
                "timestamp" to point.timestamp
            )
        },
        "createdAt" to createdAt,
        "caloriesKcal" to caloriesKcal // NEW
    )
}
