package com.example.myapplication.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.myapplication.data.ActivityType
import com.example.myapplication.data.LocationPoint
import com.example.myapplication.data.RunSession

// ─── WorkoutSessionEntity ──────────────────────────────────────────────────
// @PrimaryKey val id = Firestore doc.id → upsert prepreči podvajanje
// GPS točke so v ločeni GpsPointEntity tabeli (Data Splitting Strategy, Faza 3)

@Entity(tableName = "workout_sessions")
data class WorkoutSessionEntity(
    @field:PrimaryKey val id: String,  // = Firestore doc.id → @Upsert prepreči podvajanje
    val userId: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Int,
    val distanceMeters: Double,
    val maxSpeedMps: Float,
    val avgSpeedMps: Float,
    val createdAt: Long,
    val caloriesKcal: Int,
    val elevationGainM: Float,
    val elevationLossM: Float,
    val activityType: String,   // shranjeno kot enum.name → ActivityType.RUN.name = "RUN"
    val isSmoothed: Boolean
)

// ─── GpsPointEntity ────────────────────────────────────────────────────────
// Data Splitting Strategy:
//   isRaw = true  → polna surova GPS pot (shranjena lokalno iz RunTrackerScreen)
//   isRaw = false → RDP-kompresirana pot (prišla iz Firestore sinhronizacije)
// S24 Ultra prikazuje raw točke za maksimalno kakovost prikaza.

@Entity(
    tableName = "gps_points",
    foreignKeys = [ForeignKey(
        entity = WorkoutSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class GpsPointEntity(
    @field:PrimaryKey(autoGenerate = true) val uid: Long = 0,
    val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val accuracy: Float,
    val timestamp: Long,
    val isRaw: Boolean = true   // true = lokalni surovi podatki; false = Firestore kompresiran
)

// ─── Mapper funkcije ────────────────────────────────────────────────────────

fun WorkoutSessionEntity.toRunSession(): RunSession = RunSession(
    id = id,
    userId = userId,
    startTime = startTime,
    endTime = endTime,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    maxSpeedMps = maxSpeedMps,
    avgSpeedMps = avgSpeedMps,
    polylinePoints = emptyList(), // GPS se naloži ločeno iz GpsPointEntity ali RunRouteStore
    createdAt = createdAt,
    caloriesKcal = caloriesKcal,
    elevationGainM = elevationGainM,
    elevationLossM = elevationLossM,
    activityType = ActivityType.fromString(activityType),
    isSmoothed = isSmoothed
)

fun RunSession.toEntity(): WorkoutSessionEntity = WorkoutSessionEntity(
    id = id,
    userId = userId,
    startTime = startTime,
    endTime = endTime,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    maxSpeedMps = maxSpeedMps,
    avgSpeedMps = avgSpeedMps,
    createdAt = createdAt,
    caloriesKcal = caloriesKcal,
    elevationGainM = elevationGainM,
    elevationLossM = elevationLossM,
    activityType = activityType.name,
    isSmoothed = isSmoothed
)

fun GpsPointEntity.toLocationPoint(): LocationPoint = LocationPoint(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    speed = speed,
    accuracy = accuracy,
    timestamp = timestamp
)

fun LocationPoint.toGpsPointEntity(sessionId: String, isRaw: Boolean = true): GpsPointEntity =
    GpsPointEntity(
        sessionId = sessionId,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        speed = speed,
        accuracy = accuracy,
        timestamp = timestamp,
        isRaw = isRaw
    )



