package com.example.myapplication.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Health Connect Manager - upravlja povezavo s Samsung Health in drugimi health aplikacijami
 * preko Android Health Connect API-ja
 */
class HealthConnectManager(private val context: Context) {

    // Singleton pattern
    companion object {
        private const val TAG = "HealthConnectManager"

        @Volatile
        private var INSTANCE: HealthConnectManager? = null

        fun getInstance(context: Context): HealthConnectManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: HealthConnectManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Health Connect Client
    private val healthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    // Read permissions (required for main screen)
    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
    )

    // Write permission for manual sleep entry (requested separately)
    val sleepWritePermission = setOf(
        HealthPermission.getWritePermission(SleepSessionRecord::class),
    )

    // All permissions combined (for requesting both read + write at once)
    val allPermissions = permissions + sleepWritePermission

    /**
     * Preveri če je Health Connect na voljo na napravi
     */
    fun isAvailable(): Boolean {
        return try {
            val availability = HealthConnectClient.getSdkStatus(context)
            availability == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Health Connect availability", e)
            false
        }
    }

    /**
     * Preveri če so dovoljenja že odobrena
     */
    suspend fun hasAllPermissions(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            // Only check READ permissions for main screen access
            permissions.all { it in granted }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }

    /**
     * Preveri če je write permission za sleep odobren
     */
    suspend fun hasSleepWritePermission(): Boolean {
        return try {
            val granted = healthConnectClient.permissionController.getGrantedPermissions()
            sleepWritePermission.all { it in granted }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Pridobi seznam odobrenih permissions
     */
    suspend fun getGrantedPermissions(): Set<String> {
        return try {
            healthConnectClient.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting granted permissions", e)
            emptySet()
        }
    }

    /**
     * ActivityResultContract za pridobivanje permissions
     */
    fun createRequestPermissionsContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    /**
     * Odpre Health Connect aplikacijo za nastavitve
     */
    fun openHealthConnectSettings() {
        try {
            val intent = Intent().apply {
                action = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Če Health Connect ni nameščen, odpri Play Store
            try {
                val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                    setPackage("com.android.vending")
                }
                context.startActivity(playStoreIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Cannot open Health Connect settings or Play Store", ex)
            }
        }
    }

    // ========== DATA READING FUNCTIONS ==========

    /**
     * Preberi steps za določen časovni period
     * Spremenjeno 2026-02-16: Uporablja ReadRecords namesto Aggregate, da prepreči "fantomsko" rast korakov
     * zaradi interpolacije v Health Connectu.
     */
    suspend fun readSteps(startTime: Instant, endTime: Instant): Long {
        return try {
            var totalSteps = 0L
            var pageToken: String? = null

            do {
                val request = ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                    pageToken = pageToken
                )
                val response = healthConnectClient.readRecords(request)
                totalSteps += response.records.sumOf { (it.count) }
                pageToken = response.pageToken
            } while (pageToken != null)

            totalSteps
        } catch (e: Exception) {
            Log.e(TAG, "Error reading steps", e)
            0
        }
    }

    /**
     * Preberi steps za danes
     */
    suspend fun readTodaySteps(): Long {
        val now = Instant.now()
        val startOfDay = LocalDateTime.now()
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()

        return readSteps(startOfDay, now)
    }

    /**
     * Preberi steps za zadnjih X dni (vrne seznam po dnevih)
     */
    suspend fun readStepsLastDays(days: Int): List<DailySteps> {
        return try {
            val now = Instant.now()
            val startTime = now.minus(days.toLong(), ChronoUnit.DAYS)

            val request = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, now)
            )
            val response = healthConnectClient.readRecords(request)

            // Group by day
            response.records
                .groupBy {
                    it.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
                }
                .map { (date, records) ->
                    DailySteps(
                        date = date.toString(),
                        steps = records.sumOf { it.count }
                    )
                }
                .sortedByDescending { it.date }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading daily steps", e)
            emptyList()
        }
    }

    /**
     * Preberi heart rate za določen časovni period
     */
    suspend fun readHeartRate(startTime: Instant, endTime: Instant): List<HeartRateData> {
        return try {
            val request = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            response.records.flatMap { record ->
                record.samples.map { sample ->
                    HeartRateData(
                        timestamp = sample.time,
                        bpm = sample.beatsPerMinute
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading heart rate", e)
            emptyList()
        }
    }

    /**
     * Preberi sleep sessions za zadnjih X dni
     */
    suspend fun readSleepSessions(days: Int): List<SleepData> {
        return try {
            val now = Instant.now()
            val startTime = now.minus(days.toLong(), ChronoUnit.DAYS)

            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, now)
            )
            val response = healthConnectClient.readRecords(request)

            response.records.map { record ->
                val durationHours = ChronoUnit.MINUTES.between(
                    record.startTime,
                    record.endTime
                ) / 60.0

                // Parse sleep stages
                val stages = record.stages.groupBy { it.stage }.map { (stage, stageRecords) ->
                    val totalMinutes = stageRecords.sumOf { s ->
                        ChronoUnit.MINUTES.between(s.startTime, s.endTime)
                    }
                    val stageName = when (stage) {
                        SleepSessionRecord.STAGE_TYPE_AWAKE -> "Awake"
                        SleepSessionRecord.STAGE_TYPE_LIGHT -> "Light"
                        SleepSessionRecord.STAGE_TYPE_DEEP -> "Deep"
                        SleepSessionRecord.STAGE_TYPE_REM -> "REM"
                        SleepSessionRecord.STAGE_TYPE_SLEEPING -> "Sleeping"
                        else -> "Unknown"
                    }
                    SleepStage(stage = stageName, durationMinutes = totalMinutes)
                }

                SleepData(
                    date = record.endTime.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                    durationHours = durationHours,
                    startTime = record.startTime,
                    endTime = record.endTime,
                    stages = stages
                )
            }.sortedByDescending { it.date }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading sleep sessions", e)
            emptyList()
        }
    }

    /**
     * Preberi distance za določen časovni period
     */
    suspend fun readDistance(startTime: Instant, endTime: Instant): Double {
        return try {
            val request = ReadRecordsRequest(
                recordType = DistanceRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val response = healthConnectClient.readRecords(request)
            response.records.sumOf { it.distance.inMeters } / 1000.0 // Convert to km
        } catch (e: Exception) {
            Log.e(TAG, "Error reading distance", e)
            0.0
        }
    }

    /**
     * Preberi calories burned za določen časovni period
     * Bere OBA: TotalCaloriesBurned (Google Fit) IN ActiveCaloriesBurned (Samsung Health)
     */
    suspend fun readCalories(startTime: Instant, endTime: Instant): Int {
        try {
            Log.d(TAG, "readCalories: requesting data from $startTime to $endTime")

            // 1. Try reading Active Calories records directly
            val activeRequest = ReadRecordsRequest(
                recordType = ActiveCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val activeResponse = healthConnectClient.readRecords(activeRequest)
            val activeSum = activeResponse.records.sumOf { it.energy.inKilocalories }
            Log.d(TAG, "readCalories: Found ${activeResponse.records.size} Active records, sum = $activeSum kcal")

            // 2. Try reading Total Calories records directly (just for logging/debug)
            val totalRequest = ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val totalResponse = healthConnectClient.readRecords(totalRequest)
            val totalSum = totalResponse.records.sumOf { it.energy.inKilocalories }
            Log.d(TAG, "readCalories: Found ${totalResponse.records.size} Total records, sum = $totalSum kcal")

            // LOGIC CHANGE: User wants ONLY ACTIVE calories (like Samsung Health main screen)
            // If we have active calories explicitely, return them.
            if (activeSum > 1.0) {
                return activeSum.toInt()
            }

            // If no explicit active calories record, we DO NOT want to return Total (which includes BMR ~1500+).
            // Instead, we fall back to estimating ACTIVE calories from steps.

            // FALLBACK: Estimate ACTIVE calories from steps.
            val steps = readSteps(startTime, endTime)
            if (steps > 0) {
                 // 0.045 kcal per step is a good average for active burn (walking)
                 val estimated = steps * 0.045
                 Log.d(TAG, "readCalories: No direct active calorie data, estimated from $steps steps = $estimated kcal")
                 return estimated.toInt()
            }

            return 0
        } catch (e: Exception) {
            Log.e(TAG, "Error reading calories", e)
            return 0
        }
    }

    /**
     * Preberi weight za zadnjih X dni
     */
    suspend fun readWeightHistory(days: Int): List<WeightData> {
        return try {
            val now = Instant.now()
            val startTime = now.minus(days.toLong(), ChronoUnit.DAYS)

            val request = ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, now)
            )
            val response = healthConnectClient.readRecords(request)

            response.records.map { record ->
                WeightData(
                    date = record.time.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                    weightKg = record.weight.inKilograms,
                    timestamp = record.time
                )
            }.sortedByDescending { it.date }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading weight history", e)
            emptyList()
        }
    }

    /**
     * Preberi exercise sessions za danes (od polnoči)
     */
    suspend fun readTodayExerciseSessions(): List<ExerciseData> {
        val now = Instant.now()
        val startOfDay = LocalDateTime.now()
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()

        return try {
            Log.d(TAG, "readTodayExerciseSessions: requesting from $startOfDay to $now")

            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
            )
            val response = healthConnectClient.readRecords(request)
            Log.d(TAG, "readTodayExerciseSessions: received ${response.records.size} records")

            response.records.map { record ->
                val durationMinutes = ChronoUnit.MINUTES.between(
                    record.startTime,
                    record.endTime
                )

                Log.d(TAG, "  - ${record.title ?: "Exercise"} (${record.exerciseType}): ${record.startTime} to ${record.endTime} = $durationMinutes min")

                ExerciseData(
                    date = record.startTime.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                    exerciseType = record.exerciseType.toString(),
                    durationMinutes = durationMinutes.toInt(),
                    startTime = record.startTime,
                    endTime = record.endTime,
                    title = record.title ?: "Exercise"
                )
            }.sortedByDescending { it.date }.also {
                Log.d(TAG, "readTodayExerciseSessions: total ${it.sumOf { e -> e.durationMinutes }} minutes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading today exercise sessions", e)
            emptyList()
        }
    }

    /**
     * Preberi exercise sessions za zadnjih X dni
     */
    suspend fun readExerciseSessions(days: Int): List<ExerciseData> {
        return try {
            val now = Instant.now()
            val startTime = now.minus(days.toLong(), ChronoUnit.DAYS)
            Log.d(TAG, "readExerciseSessions: requesting last $days days from $startTime to $now")

            val request = ReadRecordsRequest(
                recordType = ExerciseSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, now)
            )
            val response = healthConnectClient.readRecords(request)
            Log.d(TAG, "readExerciseSessions: received ${response.records.size} records")

            response.records.map { record ->
                val durationMinutes = ChronoUnit.MINUTES.between(
                    record.startTime,
                    record.endTime
                )

                Log.d(TAG, "  - ${record.title ?: "Exercise"} (${record.exerciseType}): ${record.startTime} to ${record.endTime} = $durationMinutes min")

                ExerciseData(
                    date = record.startTime.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                    exerciseType = record.exerciseType.toString(),
                    durationMinutes = durationMinutes.toInt(),
                    startTime = record.startTime,
                    endTime = record.endTime,
                    title = record.title ?: "Exercise"
                )
            }.sortedByDescending { it.date }.also {
                Log.d(TAG, "readExerciseSessions: total ${it.sumOf { e -> e.durationMinutes }} minutes")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading exercise sessions", e)
            emptyList()
        }
    }

    /**
     * Preberi celoten health summary za danes
     */
    suspend fun readTodayHealthSummary(): HealthSummary {
        val now = Instant.now()
        val startOfDay = LocalDateTime.now()
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()

        return HealthSummary(
            date = LocalDateTime.now().toLocalDate().toString(),
            steps = readSteps(startOfDay, now),
            distanceKm = readDistance(startOfDay, now),
            caloriesBurned = readCalories(startOfDay, now),
            heartRateData = readHeartRate(startOfDay, now),
            exerciseSessions = readExerciseSessions(1)
        )
    }

    /**
     * Flow za real-time monitoring steps (preverja vsakih 60 sekund)
     */
    fun observeTodaySteps(): Flow<Long> = flow {
        while (true) {
            try {
                val steps = readTodaySteps()
                emit(steps)
                kotlinx.coroutines.delay(60000) // Update every minute
            } catch (e: Exception) {
                Log.e(TAG, "Error in steps flow", e)
                emit(0)
            }
        }
    }
    /**
     * Write a manual sleep session to Health Connect.
     * Deletes any existing sleep sessions for the same wake-up date first (replacement).
     */
    suspend fun writeSleepSession(startTime: Instant, endTime: Instant): Boolean {
        return try {
            // Delete existing sleep sessions that end on the same day (= same wake-up date)
            val wakeDate = endTime.atZone(ZoneId.systemDefault()).toLocalDate()
            val dayStart = wakeDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val dayEnd = wakeDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            // Read existing sessions that overlap with this day
            val existingRequest = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    dayStart.minus(1, ChronoUnit.DAYS), // include sessions starting yesterday evening
                    dayEnd
                )
            )
            val existing = healthConnectClient.readRecords(existingRequest)

            // Delete sessions whose endTime falls on the same wake-up date
            val toDelete = existing.records.filter { record ->
                record.endTime.atZone(ZoneId.systemDefault()).toLocalDate() == wakeDate
            }
            if (toDelete.isNotEmpty()) {
                for (record in toDelete) {
                    healthConnectClient.deleteRecords(
                        SleepSessionRecord::class,
                        TimeRangeFilter.between(record.startTime, record.endTime)
                    )
                }
                Log.d(TAG, "Deleted ${toDelete.size} existing sleep records for $wakeDate")
            }

            val sleepRecord = SleepSessionRecord(
                startTime = startTime,
                startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
                endTime = endTime,
                endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime)
            )
            healthConnectClient.insertRecords(listOf(sleepRecord))
            Log.d(TAG, "Successfully wrote sleep session: $startTime to $endTime")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error writing sleep session", e)
            false
        }
    }

    /**
     * Delete all sleep sessions that end on the given date (wake-up date)
     */
    suspend fun deleteSleepByDate(wakeDate: java.time.LocalDate): Boolean {
        return try {
            val dayStart = wakeDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val dayEnd = wakeDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            val request = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(
                    dayStart.minus(1, ChronoUnit.DAYS),
                    dayEnd
                )
            )
            val existing = healthConnectClient.readRecords(request)

            val toDelete = existing.records.filter { record ->
                record.endTime.atZone(ZoneId.systemDefault()).toLocalDate() == wakeDate
            }

            if (toDelete.isNotEmpty()) {
                for (record in toDelete) {
                    healthConnectClient.deleteRecords(
                        SleepSessionRecord::class,
                        TimeRangeFilter.between(record.startTime, record.endTime)
                    )
                }
                Log.d(TAG, "Deleted ${toDelete.size} sleep records for $wakeDate")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting sleep sessions", e)
            false
        }
    }
}

// ========== DATA CLASSES ==========

data class DailySteps(
    val date: String,
    val steps: Long
)

data class HeartRateData(
    val timestamp: Instant,
    val bpm: Long
)

data class SleepData(
    val date: String,
    val durationHours: Double,
    val startTime: Instant,
    val endTime: Instant,
    val stages: List<SleepStage> = emptyList()
)

data class SleepStage(
    val stage: String,  // "Awake", "Light", "Deep", "REM", "Unknown"
    val durationMinutes: Long
)

data class WeightData(
    val date: String,
    val weightKg: Double,
    val timestamp: Instant
)

data class ExerciseData(
    val date: String,
    val exerciseType: String,
    val durationMinutes: Int,
    val startTime: Instant,
    val endTime: Instant,
    val title: String
)

data class HealthSummary(
    val date: String,
    val steps: Long,
    val distanceKm: Double,
    val caloriesBurned: Int,
    val heartRateData: List<HeartRateData>,
    val exerciseSessions: List<ExerciseData>
)
