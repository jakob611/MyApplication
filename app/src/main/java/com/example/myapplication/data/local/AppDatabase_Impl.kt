package com.example.myapplication.data.local

// =============================================================================
// AppDatabase_Impl.kt — ročno pisana Room implementacija
//
// Zakaj ročno: KSP nima objavljene verzije za Kotlin 2.2.x, kapt pa ne dela
// z Kotlin 2.2.x (K1 odstranjem). Room pri .build() poišče ta razred prek
// Class.forName("...AppDatabase_Impl") — dokler ta datoteka obstaja, Room deluje.
//
// Ko bo KSP objavljen za tvojo verzijo Kotlina:
//   1. Daj v build.gradle.kts: id("com.google.devtools.ksp") version "2.x.x-1.0.Y"
//   2. Dodaj ksp("androidx.room:room-compiler:2.6.1")
//   3. Izbriši to datoteko ročno (Right-click → Delete v Android Studiu)
//
// Velja za Room 2.6.1.
// =============================================================================

import android.database.Cursor
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────────────────────
// AppDatabase_Impl
// ─────────────────────────────────────────────────────────────────────────────

class AppDatabase_Impl : AppDatabase() {

    private val _invalidationSignal = MutableStateFlow(0L)

    private val _workoutSessionDao: WorkoutSessionDao_Impl by lazy {
        WorkoutSessionDao_Impl(this)
    }

    private val _gpsPointDao: GpsPointDao_Impl by lazy {
        GpsPointDao_Impl(this)
    }

    override fun workoutSessionDao(): WorkoutSessionDao = _workoutSessionDao
    override fun gpsPointDao(): GpsPointDao = _gpsPointDao

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
        val cb = object : SupportSQLiteOpenHelper.Callback(version = 1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `workout_sessions` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `startTime` INTEGER NOT NULL,
                        `endTime` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `distanceMeters` REAL NOT NULL,
                        `maxSpeedMps` REAL NOT NULL,
                        `avgSpeedMps` REAL NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `caloriesKcal` INTEGER NOT NULL,
                        `elevationGainM` REAL NOT NULL,
                        `elevationLossM` REAL NOT NULL,
                        `activityType` TEXT NOT NULL,
                        `isSmoothed` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `gps_points` (
                        `uid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `altitude` REAL NOT NULL,
                        `speed` REAL NOT NULL,
                        `accuracy` REAL NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `isRaw` INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_points_sessionId` ON `gps_points` (`sessionId`)")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                // V1 beta: destructive migration prek fallbackToDestructiveMigration() v AppDatabase.kt
            }
        }
        return config.sqliteOpenHelperFactory.create(
            SupportSQLiteOpenHelper.Configuration.builder(config.context)
                .name(config.name)
                .callback(cb)
                .build()
        )
    }

    override fun createInvalidationTracker(): InvalidationTracker =
        InvalidationTracker(this, "workout_sessions", "gps_points")

    override fun clearAllTables() {
        val db = openHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM `gps_points`")
            db.execSQL("DELETE FROM `workout_sessions`")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun getAutoMigrations(
        autoMigrationSpecs: Map<Class<out AutoMigrationSpec>, AutoMigrationSpec>
    ): List<Migration> = emptyList()

    fun notifyInvalidation() { _invalidationSignal.value = System.currentTimeMillis() }

    internal fun getReadableDb(): SupportSQLiteDatabase = openHelper.readableDatabase
    internal fun getWritableDb(): SupportSQLiteDatabase = openHelper.writableDatabase
    internal fun invalidationFlow(): Flow<Long> = _invalidationSignal
}

// ─────────────────────────────────────────────────────────────────────────────
// WorkoutSessionDao_Impl
// ─────────────────────────────────────────────────────────────────────────────

class WorkoutSessionDao_Impl(private val db: AppDatabase_Impl) : WorkoutSessionDao {

    override fun getSessionsFlow(): Flow<List<WorkoutSessionEntity>> =
        db.invalidationFlow()
            .onStart { emit(0L) }
            .map { querySessions() }

    private fun querySessions(): List<WorkoutSessionEntity> {
        val c = db.getReadableDb().query("SELECT * FROM workout_sessions ORDER BY createdAt DESC")
        return c.use { cur ->
            buildList {
                while (cur.moveToNext()) add(cur.toWorkoutSessionEntity())
            }
        }
    }

    private fun Cursor.toWorkoutSessionEntity() = WorkoutSessionEntity(
        id              = getString(getColumnIndexOrThrow("id")),
        userId          = getString(getColumnIndexOrThrow("userId")),
        startTime       = getLong(getColumnIndexOrThrow("startTime")),
        endTime         = getLong(getColumnIndexOrThrow("endTime")),
        durationSeconds = getInt(getColumnIndexOrThrow("durationSeconds")),
        distanceMeters  = getDouble(getColumnIndexOrThrow("distanceMeters")),
        maxSpeedMps     = getFloat(getColumnIndexOrThrow("maxSpeedMps")),
        avgSpeedMps     = getFloat(getColumnIndexOrThrow("avgSpeedMps")),
        createdAt       = getLong(getColumnIndexOrThrow("createdAt")),
        caloriesKcal    = getInt(getColumnIndexOrThrow("caloriesKcal")),
        elevationGainM  = getFloat(getColumnIndexOrThrow("elevationGainM")),
        elevationLossM  = getFloat(getColumnIndexOrThrow("elevationLossM")),
        activityType    = getString(getColumnIndexOrThrow("activityType")),
        isSmoothed      = getInt(getColumnIndexOrThrow("isSmoothed")) != 0
    )

    override suspend fun upsertAll(sessions: List<WorkoutSessionEntity>) = withContext(Dispatchers.IO) {
        if (sessions.isEmpty()) return@withContext
        val wdb = db.getWritableDb()
        wdb.beginTransaction()
        try {
            sessions.forEach { upsertInTransaction(wdb, it) }
            wdb.setTransactionSuccessful()
        } finally {
            wdb.endTransaction()
        }
        db.notifyInvalidation()
    }

    override suspend fun upsert(session: WorkoutSessionEntity) = withContext(Dispatchers.IO) {
        val wdb = db.getWritableDb()
        wdb.beginTransaction()
        try {
            upsertInTransaction(wdb, session)
            wdb.setTransactionSuccessful()
        } finally {
            wdb.endTransaction()
        }
        db.notifyInvalidation()
    }

    private fun upsertInTransaction(wdb: SupportSQLiteDatabase, s: WorkoutSessionEntity) {
        val stmt: SupportSQLiteStatement = wdb.compileStatement(
            "INSERT OR REPLACE INTO workout_sessions " +
            "(id,userId,startTime,endTime,durationSeconds,distanceMeters,maxSpeedMps,avgSpeedMps," +
            "createdAt,caloriesKcal,elevationGainM,elevationLossM,activityType,isSmoothed) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        )
        stmt.use {
            it.bindString(1,  s.id)
            it.bindString(2,  s.userId)
            it.bindLong(3,    s.startTime)
            it.bindLong(4,    s.endTime)
            it.bindLong(5,    s.durationSeconds.toLong())
            it.bindDouble(6,  s.distanceMeters)
            it.bindDouble(7,  s.maxSpeedMps.toDouble())
            it.bindDouble(8,  s.avgSpeedMps.toDouble())
            it.bindLong(9,    s.createdAt)
            it.bindLong(10,   s.caloriesKcal.toLong())
            it.bindDouble(11, s.elevationGainM.toDouble())
            it.bindDouble(12, s.elevationLossM.toDouble())
            it.bindString(13, s.activityType)
            it.bindLong(14,   if (s.isSmoothed) 1L else 0L)
            it.executeInsert()
        }
    }

    override suspend fun getLatestCreatedAt(userId: String): Long? = withContext(Dispatchers.IO) {
        db.getReadableDb().query(
            "SELECT MAX(createdAt) FROM workout_sessions WHERE userId = ?", arrayOf(userId)
        ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }
    }

    override suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        db.getWritableDb().execSQL("DELETE FROM workout_sessions WHERE id = ?", arrayOf(id))
        db.notifyInvalidation()
    }

    override suspend fun getSessionCount(userId: String): Int = withContext(Dispatchers.IO) {
        db.getReadableDb().query(
            "SELECT COUNT(*) FROM workout_sessions WHERE userId = ?", arrayOf(userId)
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GpsPointDao_Impl
// ─────────────────────────────────────────────────────────────────────────────

class GpsPointDao_Impl(private val db: AppDatabase_Impl) : GpsPointDao {

    private fun Cursor.toGpsPoints(): List<GpsPointEntity> = buildList {
        while (moveToNext()) add(GpsPointEntity(
            uid       = getLong(getColumnIndexOrThrow("uid")),
            sessionId = getString(getColumnIndexOrThrow("sessionId")),
            latitude  = getDouble(getColumnIndexOrThrow("latitude")),
            longitude = getDouble(getColumnIndexOrThrow("longitude")),
            altitude  = getDouble(getColumnIndexOrThrow("altitude")),
            speed     = getFloat(getColumnIndexOrThrow("speed")),
            accuracy  = getFloat(getColumnIndexOrThrow("accuracy")),
            timestamp = getLong(getColumnIndexOrThrow("timestamp")),
            isRaw     = getInt(getColumnIndexOrThrow("isRaw")) != 0
        ))
    }

    override suspend fun getPointsForSession(sessionId: String): List<GpsPointEntity> = withContext(Dispatchers.IO) {
        db.getReadableDb().query(
            "SELECT * FROM gps_points WHERE sessionId = ? ORDER BY timestamp ASC", arrayOf(sessionId)
        ).use { it.toGpsPoints() }
    }

    override suspend fun getPointsPreferRaw(sessionId: String): List<GpsPointEntity> = withContext(Dispatchers.IO) {
        db.getReadableDb().query(
            "SELECT * FROM gps_points WHERE sessionId = ? ORDER BY isRaw DESC, timestamp ASC", arrayOf(sessionId)
        ).use { it.toGpsPoints() }
    }

    override suspend fun insertAll(points: List<GpsPointEntity>) = withContext(Dispatchers.IO) {
        if (points.isEmpty()) return@withContext
        val wdb = db.getWritableDb()
        wdb.beginTransaction()
        try {
            val stmt: SupportSQLiteStatement = wdb.compileStatement(
                "INSERT OR IGNORE INTO gps_points " +
                "(sessionId,latitude,longitude,altitude,speed,accuracy,timestamp,isRaw) " +
                "VALUES (?,?,?,?,?,?,?,?)"
            )
            stmt.use { s ->
                points.forEach { p ->
                    s.clearBindings()
                    s.bindString(1, p.sessionId)
                    s.bindDouble(2, p.latitude)
                    s.bindDouble(3, p.longitude)
                    s.bindDouble(4, p.altitude)
                    s.bindDouble(5, p.speed.toDouble())
                    s.bindDouble(6, p.accuracy.toDouble())
                    s.bindLong(7,   p.timestamp)
                    s.bindLong(8,   if (p.isRaw) 1L else 0L)
                    s.executeInsert()
                }
            }
            wdb.setTransactionSuccessful()
        } finally {
            wdb.endTransaction()
        }
    }

    override suspend fun deleteBySessionId(sessionId: String) = withContext(Dispatchers.IO) {
        db.getWritableDb().execSQL(
            "DELETE FROM gps_points WHERE sessionId = ?", arrayOf(sessionId)
        )
    }

    override suspend fun getPointCount(sessionId: String): Int = withContext(Dispatchers.IO) {
        db.getReadableDb().query(
            "SELECT COUNT(*) FROM gps_points WHERE sessionId = ?", arrayOf(sessionId)
        ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
    }
}
