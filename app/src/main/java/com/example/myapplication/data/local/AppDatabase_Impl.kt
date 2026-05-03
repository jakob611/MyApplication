package com.example.myapplication.data.local

// =============================================================================
// AppDatabase_Impl.kt — ročno pisana Room implementacija
//
// Zakaj ročno: KSP nima objavljene verzije za Kotlin 2.2.10, kapt pa ne dela
// z Kotlin 2.2.x (K1 odstranjem). Room pri .build() poišče ta razred prek
// Class.forName("...AppDatabase_Impl") — dokler ta datoteka obstaja, Room deluje.
//
// Velja za Room 2.6.1. Ob nadgradnji Room-a ali zamenjavi entitet: posodobi
// CREATE TABLE SQL stavke in cursor mapperje spodaj.
// =============================================================================

import android.database.Cursor
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// AppDatabase_Impl
// ─────────────────────────────────────────────────────────────────────────────

class AppDatabase_Impl : AppDatabase() {

    @Volatile private var _workoutSessionDao: WorkoutSessionDao? = null
    @Volatile private var _gpsPointDao: GpsPointDao?        = null

    // ── Room lifecycle hooks ──────────────────────────────────────────────────

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(SQL_CREATE_WORKOUT_SESSIONS)
                db.execSQL(SQL_CREATE_GPS_POINTS)
                db.execSQL(SQL_CREATE_IDX_GPS_SESSION)
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // Version 1 — ni migracij
            }
        }
        val helperCfg = SupportSQLiteOpenHelper.Configuration
            .builder(config.context)
            .name(config.name)
            .callback(callback)
            .build()
        return config.sqliteOpenHelperFactory.create(helperCfg)
    }

    @Suppress("DEPRECATION")
    override fun createInvalidationTracker(): InvalidationTracker =
        // @RestrictTo(LIBRARY_GROUP_PREFIX) — dostopno za kompilacijo, samo lint opozorilo.
        // Naš Flow mehanizem (_dbWriteTrigger) ne uporablja tega trackerja, toda Room
        // zahteva veljaven (ne-null) primerek ob inicializaciji.
        InvalidationTracker(
            this,
            emptyMap<String, String>(),
            emptyMap<String, Set<String>>(),
            "workout_sessions",
            "gps_points"
        )

    override fun clearAllTables() {
        val sqliteDb = openHelper.writableDatabase
        try {
            sqliteDb.beginTransaction()
            sqliteDb.execSQL("DELETE FROM `gps_points`")
            sqliteDb.execSQL("DELETE FROM `workout_sessions`")
            sqliteDb.setTransactionSuccessful()
        } finally {
            sqliteDb.endTransaction()
        }
    }

    // ── DAO factory ──────────────────────────────────────────────────────────

    override fun workoutSessionDao(): WorkoutSessionDao =
        _workoutSessionDao ?: synchronized(this) {
            _workoutSessionDao ?: WorkoutSessionDaoImpl(this).also { _workoutSessionDao = it }
        }

    override fun gpsPointDao(): GpsPointDao =
        _gpsPointDao ?: synchronized(this) {
            _gpsPointDao ?: GpsPointDaoImpl(this).also { _gpsPointDao = it }
        }

    // ── Shared write-notification bus (reaktivni Flows) ───────────────────────

    companion object {
        /**
         * Vsak писanjski DAO klic pošlje Unit v ta SharedFlow.
         * [WorkoutSessionDaoImpl.getSessionsFlow] posluša in ob každem signalu
         * znova poizveduje bazo → UI se posodobi brez Room InvalidationTracker-ja.
         */
        val _dbWriteTrigger = MutableSharedFlow<Unit>(
            replay        = 0,
            extraBufferCapacity = 4,
            onBufferOverflow    = BufferOverflow.DROP_OLDEST
        )

        // ── DDL stavki ────────────────────────────────────────────────────────

        private const val SQL_CREATE_WORKOUT_SESSIONS = """
            CREATE TABLE IF NOT EXISTS `workout_sessions` (
                `id`              TEXT    NOT NULL,
                `userId`          TEXT    NOT NULL,
                `startTime`       INTEGER NOT NULL,
                `endTime`         INTEGER NOT NULL,
                `durationSeconds` INTEGER NOT NULL,
                `distanceMeters`  REAL    NOT NULL,
                `maxSpeedMps`     REAL    NOT NULL,
                `avgSpeedMps`     REAL    NOT NULL,
                `createdAt`       INTEGER NOT NULL,
                `caloriesKcal`    INTEGER NOT NULL,
                `elevationGainM`  REAL    NOT NULL,
                `elevationLossM`  REAL    NOT NULL,
                `activityType`    TEXT    NOT NULL,
                `isSmoothed`      INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )"""

        private const val SQL_CREATE_GPS_POINTS = """
            CREATE TABLE IF NOT EXISTS `gps_points` (
                `uid`       INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `sessionId` TEXT    NOT NULL,
                `latitude`  REAL    NOT NULL,
                `longitude` REAL    NOT NULL,
                `altitude`  REAL    NOT NULL,
                `speed`     REAL    NOT NULL,
                `accuracy`  REAL    NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `isRaw`     INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON DELETE CASCADE
            )"""

        private const val SQL_CREATE_IDX_GPS_SESSION =
            "CREATE INDEX IF NOT EXISTS `index_gps_points_sessionId` ON `gps_points` (`sessionId`)"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WorkoutSessionDaoImpl
// ─────────────────────────────────────────────────────────────────────────────

private class WorkoutSessionDaoImpl(private val db: AppDatabase_Impl) : WorkoutSessionDao {

    // ── Reaktivni Flow ───────────────────────────────────────────────────────

    override fun getSessionsFlow(): Flow<List<WorkoutSessionEntity>> =
        AppDatabase_Impl._dbWriteTrigger
            .onStart { emit(Unit) }          // prva emisija = takoj ob zbiranju
            .map { fetchAllSessions() }

    private fun fetchAllSessions(): List<WorkoutSessionEntity> {
        val result = mutableListOf<WorkoutSessionEntity>()
        val cursor: Cursor = db.openHelper.readableDatabase.query(
            "SELECT * FROM workout_sessions ORDER BY createdAt DESC"
        )
        try {
            while (cursor.moveToNext()) result.add(mapSession(cursor))
        } finally {
            cursor.close()
        }
        return result
    }

    // ── Pisalne operacije ────────────────────────────────────────────────────

    override suspend fun upsertAll(sessions: List<WorkoutSessionEntity>): Unit = withContext(Dispatchers.IO) {
        val sqliteDb = db.openHelper.writableDatabase
        try {
            sqliteDb.beginTransaction()
            sessions.forEach { s -> sqliteDb.execSQL(UPSERT_SESSION_SQL, sessionBindArgs(s)) }
            sqliteDb.setTransactionSuccessful()
        } finally {
            sqliteDb.endTransaction()
        }
        AppDatabase_Impl._dbWriteTrigger.tryEmit(Unit)
    }

    override suspend fun upsert(session: WorkoutSessionEntity): Unit = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.execSQL(UPSERT_SESSION_SQL, sessionBindArgs(session))
        AppDatabase_Impl._dbWriteTrigger.tryEmit(Unit)
    }

    override suspend fun deleteById(id: String): Unit = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM workout_sessions WHERE id = ?", arrayOf<Any>(id)
        )
        AppDatabase_Impl._dbWriteTrigger.tryEmit(Unit)
    }

    // ── Bralne operacije ─────────────────────────────────────────────────────

    override suspend fun getLatestCreatedAt(userId: String): Long? = withContext(Dispatchers.IO) {
        val cursor: Cursor = db.openHelper.readableDatabase.query(
            "SELECT MAX(createdAt) FROM workout_sessions WHERE userId = ?",
            arrayOf<Any>(userId)
        )
        try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        } finally {
            cursor.close()
        }
    }

    override suspend fun getSessionCount(userId: String): Int = withContext(Dispatchers.IO) {
        val cursor: Cursor = db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM workout_sessions WHERE userId = ?",
            arrayOf<Any>(userId)
        )
        try {
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } finally {
            cursor.close()
        }
    }

    // ── Pomočniki ────────────────────────────────────────────────────────────

    private fun sessionBindArgs(s: WorkoutSessionEntity): Array<Any> = arrayOf(
        s.id,
        s.userId,
        s.startTime,
        s.endTime,
        s.durationSeconds.toLong(),
        s.distanceMeters,
        s.maxSpeedMps.toDouble(),
        s.avgSpeedMps.toDouble(),
        s.createdAt,
        s.caloriesKcal.toLong(),
        s.elevationGainM.toDouble(),
        s.elevationLossM.toDouble(),
        s.activityType,
        if (s.isSmoothed) 1L else 0L
    )

    private fun mapSession(c: Cursor) = WorkoutSessionEntity(
        id              = c.getString(c.getColumnIndexOrThrow("id")),
        userId          = c.getString(c.getColumnIndexOrThrow("userId")),
        startTime       = c.getLong  (c.getColumnIndexOrThrow("startTime")),
        endTime         = c.getLong  (c.getColumnIndexOrThrow("endTime")),
        durationSeconds = c.getInt   (c.getColumnIndexOrThrow("durationSeconds")),
        distanceMeters  = c.getDouble(c.getColumnIndexOrThrow("distanceMeters")),
        maxSpeedMps     = c.getFloat (c.getColumnIndexOrThrow("maxSpeedMps")),
        avgSpeedMps     = c.getFloat (c.getColumnIndexOrThrow("avgSpeedMps")),
        createdAt       = c.getLong  (c.getColumnIndexOrThrow("createdAt")),
        caloriesKcal    = c.getInt   (c.getColumnIndexOrThrow("caloriesKcal")),
        elevationGainM  = c.getFloat (c.getColumnIndexOrThrow("elevationGainM")),
        elevationLossM  = c.getFloat (c.getColumnIndexOrThrow("elevationLossM")),
        activityType    = c.getString(c.getColumnIndexOrThrow("activityType")),
        isSmoothed      = c.getInt   (c.getColumnIndexOrThrow("isSmoothed")) != 0
    )

    companion object {
        private const val UPSERT_SESSION_SQL =
            "INSERT OR REPLACE INTO workout_sessions " +
            "(id, userId, startTime, endTime, durationSeconds, distanceMeters, " +
            " maxSpeedMps, avgSpeedMps, createdAt, caloriesKcal, " +
            " elevationGainM, elevationLossM, activityType, isSmoothed) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GpsPointDaoImpl
// ─────────────────────────────────────────────────────────────────────────────

private class GpsPointDaoImpl(private val db: AppDatabase_Impl) : GpsPointDao {

    override suspend fun getPointsForSession(sessionId: String): List<GpsPointEntity> = withContext(Dispatchers.IO) {
        query("SELECT * FROM gps_points WHERE sessionId = ? ORDER BY timestamp ASC", arrayOf<Any>(sessionId))
    }

    override suspend fun getPointsPreferRaw(sessionId: String): List<GpsPointEntity> = withContext(Dispatchers.IO) {
        query("SELECT * FROM gps_points WHERE sessionId = ? ORDER BY isRaw DESC, timestamp ASC", arrayOf<Any>(sessionId))
    }

    override suspend fun insertAll(points: List<GpsPointEntity>): Unit = withContext(Dispatchers.IO) {
        val sqliteDb = db.openHelper.writableDatabase
        try {
            sqliteDb.beginTransaction()
            points.forEach { p ->
                sqliteDb.execSQL(INSERT_GPS_SQL, gpsBindArgs(p))
            }
            sqliteDb.setTransactionSuccessful()
        } finally {
            sqliteDb.endTransaction()
        }
    }

    override suspend fun deleteBySessionId(sessionId: String): Unit = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.execSQL(
            "DELETE FROM gps_points WHERE sessionId = ?", arrayOf<Any>(sessionId)
        )
    }

    override suspend fun getPointCount(sessionId: String): Int = withContext(Dispatchers.IO) {
        val cursor: Cursor = db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM gps_points WHERE sessionId = ?", arrayOf<Any>(sessionId)
        )
        try {
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        } finally {
            cursor.close()
        }
    }

    // ── Pomočniki ────────────────────────────────────────────────────────────

    private fun query(sql: String, args: Array<Any>): List<GpsPointEntity> {
        val result = mutableListOf<GpsPointEntity>()
        val cursor: Cursor = db.openHelper.readableDatabase.query(sql, args)
        try {
            while (cursor.moveToNext()) result.add(mapGps(cursor))
        } finally {
            cursor.close()
        }
        return result
    }

    private fun gpsBindArgs(p: GpsPointEntity): Array<Any> = arrayOf(
        p.sessionId,
        p.latitude,
        p.longitude,
        p.altitude,
        p.speed.toDouble(),
        p.accuracy.toDouble(),
        p.timestamp,
        if (p.isRaw) 1L else 0L
    )

    private fun mapGps(c: Cursor) = GpsPointEntity(
        uid       = c.getLong  (c.getColumnIndexOrThrow("uid")),
        sessionId = c.getString(c.getColumnIndexOrThrow("sessionId")),
        latitude  = c.getDouble(c.getColumnIndexOrThrow("latitude")),
        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
        altitude  = c.getDouble(c.getColumnIndexOrThrow("altitude")),
        speed     = c.getFloat (c.getColumnIndexOrThrow("speed")),
        accuracy  = c.getFloat (c.getColumnIndexOrThrow("accuracy")),
        timestamp = c.getLong  (c.getColumnIndexOrThrow("timestamp")),
        isRaw     = c.getInt   (c.getColumnIndexOrThrow("isRaw")) != 0
    )

    companion object {
        private const val INSERT_GPS_SQL =
            "INSERT OR IGNORE INTO gps_points " +
            "(sessionId, latitude, longitude, altitude, speed, accuracy, timestamp, isRaw) " +
            "VALUES (?,?,?,?,?,?,?,?)"
    }
}

