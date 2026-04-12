package com.example.myapplication.data

import kotlinx.datetime.Clock

/**
 * Tip aktivnosti — vpliva na prikaz statistik in izračun kalorij.
 * MET vrednosti (Metabolic Equivalent of Task) iz standardnih tabel.
 */
enum class ActivityType(
    val label: String,
    val emoji: String,
    val metValue: Double,       // osnova pri zmerni intenzivnosti
    val showElevation: Boolean, // prikaži vzpon/spust
    val showPace: Boolean,      // prikaži tempo (min/km)
    val showSpeed: Boolean,     // prikaži hitrost (km/h)
    val unitLabel: String       // "km", "km" ali "m" za smučanje
) {
    RUN(       "Run",        "🏃", 8.0,   true,  true,  true,  "km"),
    WALK(      "Walk",       "🚶", 3.5,   false, true,  true,  "km"),
    HIKE(      "Hike",       "🥾", 5.3,   true,  false, true, "km"),
    SPRINT(    "Sprint",     "⚡", 14.0,  false, true,  true,  "km"),
    CYCLING(   "Cycling",    "🚴", 6.8,   true,  false, true,  "km"),
    SKIING(    "Skiing",     "⛷️", 7.0,   true,  false, true,  "km"),
    SNOWBOARD( "Snowboard",  "🏂", 5.5,   true,  false, true,  "km"),
    SKATING(   "Skating",    "⛸️", 7.5,   false, false, true,  "km"),
    NORDIC(    "Nordic Walk","🎿", 4.8,   true,  true,  true, "km");

    companion object {
        fun fromString(s: String?): ActivityType =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: RUN
    }
}

/**
 * Data class for a single GPS location point during a run.
 */
data class LocationPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speed: Float = 0.0f,
    val accuracy: Float = 0.0f,
    val timestamp: Long = 0L
)

/**
 * Data class for a complete run/activity session.
 */
data class RunSession(
    val id: String = "",
    val userId: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val durationSeconds: Int = 0,
    val distanceMeters: Double = 0.0,
    val maxSpeedMps: Float = 0.0f,
    val avgSpeedMps: Float = 0.0f,
    val polylinePoints: List<LocationPoint> = emptyList(),
    val createdAt: Long = Clock.System.now().toEpochMilliseconds(),
    val caloriesKcal: Int = 0,
    val elevationGainM: Float = 0f,
    val elevationLossM: Float = 0f,
    val activityType: ActivityType = ActivityType.RUN,
    val isSmoothed: Boolean = false
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
        "caloriesKcal" to caloriesKcal,
        "elevationGainM" to elevationGainM,
        "elevationLossM" to elevationLossM,
        "activityType" to activityType.name,
        "isSmoothed" to isSmoothed
    )
}
