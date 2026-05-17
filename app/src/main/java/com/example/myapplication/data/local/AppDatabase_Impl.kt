@file:Suppress("RestrictedApi", "DEPRECATION")
package com.example.myapplication.data.local

// =====================================================================
// AppDatabase_Impl.kt — ROČNO NAPISANA Room implementacija.
//
// ⚠️  Ta datoteka nadomešča auto-generirani AppDatabase_Impl ker KSP
//     za Kotlin 2.2.x še ni uradno objavljen na Maven repozitoriju.
//
// ⛔  KO BO KSP NA VOLJO (preverite: https://github.com/google/ksp/releases):
//     1. V app/build.gradle.kts: odkomentirajte blok "KSP SETUP"
//     2. V build.gradle.kts (root): odkomentirajte KSP plugin
//     3. ROČNO ZBRIŠI TA DATOTEKO iz Android Studia (Right-click → Delete)
//     4. Zaženite: ./gradlew clean assembleDebug
//
// Paketi (po refaktoriranju):
//   Entitete : com.example.myapplication.data.local.entity
//   DAO-ji   : com.example.myapplication.data.local.doo
// =====================================================================

import android.content.ContentValues
import android.database.Cursor
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.RoomOpenHelper
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.example.myapplication.data.local.doo.GpsPointDao
import com.example.myapplication.data.local.doo.WorkoutSessionDao
import com.example.myapplication.data.local.entity.GpsPointEntity
import com.example.myapplication.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Konfliktne konstante (skladno z SQLiteDatabase)
private const val CONFLICT_REPLACE = 5
private const val CONFLICT_IGNORE  = 4

// ── Implementacija WorkoutSessionDao ──────────────────────────────────────────

private class WorkoutSessionDao_Impl(private val db: AppDatabase) : WorkoutSessionDao {

    private val _sessionsFlow = MutableStateFlow<List<WorkoutSessionEntity>>(emptyList())

    // FIX: prevents duplicate observer registration on repeated getSessionsFlow() calls
    @Volatile private var _observerRegistered = false

    // FIX: refreshSessions is now always dispatched on IO — never blocks the main thread
    private fun refreshSessions() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val cursor = db.openHelper.writableDatabase
                    .query("SELECT * FROM workout_sessions ORDER BY createdAt DESC")
                val list = mutableListOf<WorkoutSessionEntity>()
                cursor.use { while (it.moveToNext()) list.add(it.toWorkoutSessionEntity()) }
                _sessionsFlow.value = list
            } catch (_: Exception) {
                // DB not yet ready — skip; observer will retry on next invalidation
            }
        }
    }

    override fun getSessionsFlow(): Flow<List<WorkoutSessionEntity>> {
        // Register invalidation observer only once per DAO instance
        if (!_observerRegistered) {
            _observerRegistered = true
            db.invalidationTracker.addObserver(object : InvalidationTracker.Observer("workout_sessions") {
                override fun onInvalidated(tables: Set<String>) { refreshSessions() }
            })
        }
        // Load initial data asynchronously (IO thread)
        refreshSessions()
        return _sessionsFlow
    }

    override suspend fun upsertAll(sessions: List<WorkoutSessionEntity>) = withContext(Dispatchers.IO) {
        val sqLite = db.openHelper.writableDatabase
        sqLite.beginTransaction()
        try {
            sessions.forEach { s ->
                sqLite.insert("workout_sessions", CONFLICT_REPLACE, s.toContentValues())
            }
            sqLite.setTransactionSuccessful()
        } finally {
            sqLite.endTransaction()
        }
        refreshSessions()
    }

    override suspend fun upsert(session: WorkoutSessionEntity) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.insert("workout_sessions", CONFLICT_REPLACE, session.toContentValues())
        refreshSessions()
    }

    override suspend fun getLatestCreatedAt(userId: String): Long? = withContext(Dispatchers.IO) {
        val cursor = db.openHelper.writableDatabase.query(
            "SELECT MAX(createdAt) FROM workout_sessions WHERE userId = ?", arrayOf(userId)
        )
        cursor.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }
    }

    override suspend fun deleteById(id: String) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.delete("workout_sessions", "id = ?", arrayOf(id))
        refreshSessions()
        Unit
    }

    override suspend fun getSessionCount(userId: String): Int = withContext(Dispatchers.IO) {
        val cursor = db.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM workout_sessions WHERE userId = ?", arrayOf(userId)
        )
        cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    override suspend fun getInProgressSession(): WorkoutSessionEntity? = withContext(Dispatchers.IO) {
        val cursor = db.openHelper.writableDatabase
            .query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' ORDER BY startTime DESC LIMIT 1")
        cursor.use { if (it.moveToFirst()) it.toWorkoutSessionEntity() else null }
    }

    override suspend fun updateStatus(id: String, status: String) = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply { put("status", status) }
        db.openHelper.writableDatabase.update("workout_sessions", CONFLICT_REPLACE, cv, "id = ?", arrayOf(id))
        refreshSessions()
        Unit
    }
}

// ── Implementacija GpsPointDao ────────────────────────────────────────────────

private class GpsPointDao_Impl(private val db: AppDatabase) : GpsPointDao {

    override suspend fun getPointsForSession(sessionId: String): List<GpsPointEntity> =
        withContext(Dispatchers.IO) {
            val cursor = db.openHelper.writableDatabase.query(
                "SELECT * FROM gps_points WHERE sessionId = ? ORDER BY timestamp ASC", arrayOf(sessionId)
            )
            val list = mutableListOf<GpsPointEntity>()
            cursor.use { while (it.moveToNext()) list.add(it.toGpsPointEntity()) }
            list
        }

    override suspend fun getPointsPreferRaw(sessionId: String): List<GpsPointEntity> =
        withContext(Dispatchers.IO) {
            val cursor = db.openHelper.writableDatabase.query(
                "SELECT * FROM gps_points WHERE sessionId = ? ORDER BY isRaw DESC, timestamp ASC",
                arrayOf(sessionId)
            )
            val list = mutableListOf<GpsPointEntity>()
            cursor.use { while (it.moveToNext()) list.add(it.toGpsPointEntity()) }
            list
        }

    override suspend fun insertAll(points: List<GpsPointEntity>) = withContext(Dispatchers.IO) {
        val sqLite = db.openHelper.writableDatabase
        sqLite.beginTransaction()
        try {
            points.forEach { p ->
                sqLite.insert("gps_points", CONFLICT_IGNORE, p.toContentValues())
            }
            sqLite.setTransactionSuccessful()
        } finally {
            sqLite.endTransaction()
        }
    }

    override suspend fun deleteBySessionId(sessionId: String) = withContext(Dispatchers.IO) {
        db.openHelper.writableDatabase.delete("gps_points", "sessionId = ?", arrayOf(sessionId))
        Unit
    }

    override suspend fun getPointCount(sessionId: String): Int = withContext(Dispatchers.IO) {
        val cursor = db.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM gps_points WHERE sessionId = ?", arrayOf(sessionId)
        )
        cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }
}

// ── AppDatabase_Impl ─────────────────────────────────────────────────────────

class AppDatabase_Impl : AppDatabase() {

    @Volatile private var _workoutSessionDao: WorkoutSessionDao? = null
    @Volatile private var _gpsPointDao: GpsPointDao? = null

    override fun workoutSessionDao(): WorkoutSessionDao =
        _workoutSessionDao ?: synchronized(this) {
            _workoutSessionDao ?: WorkoutSessionDao_Impl(this).also { _workoutSessionDao = it }
        }

    override fun gpsPointDao(): GpsPointDao =
        _gpsPointDao ?: synchronized(this) {
            _gpsPointDao ?: GpsPointDao_Impl(this).also { _gpsPointDao = it }
        }

    override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
        val callback = object : RoomOpenHelper(config, createOpenDelegate(), IDENTITY_HASH, IDENTITY_HASH) {}
        return config.sqliteOpenHelperFactory.create(
            SupportSQLiteOpenHelper.Configuration.builder(config.context)
                .name(config.name)
                .callback(callback)
                .build()
        )
    }

    override fun createInvalidationTracker(): InvalidationTracker =
        // Room 2.6.1 podpis: (RoomDatabase, Map<String,String>, Map<String,Set<String>>, vararg String)
        // shadowTablesMap = empty (ni FTS tabel), viewTables = empty (ni pogledov)
        InvalidationTracker(this, emptyMap<String, String>(), emptyMap<String, Set<String>>(), "workout_sessions", "gps_points")

    override fun clearAllTables() {
        val sqLite = openHelper.writableDatabase
        sqLite.beginTransaction()
        try {
            sqLite.execSQL("DELETE FROM gps_points")
            sqLite.execSQL("DELETE FROM workout_sessions")
            sqLite.setTransactionSuccessful()
        } finally {
            sqLite.endTransaction()
        }
    }

    override fun getRequiredTypeConverters(): MutableMap<Class<*>, List<Class<*>>> = mutableMapOf()

    override fun getRequiredAutoMigrationSpecs(): MutableSet<Class<out AutoMigrationSpec>> = mutableSetOf()

    override fun getAutoMigrations(
        autoMigrationSpecs: Map<Class<out AutoMigrationSpec>, AutoMigrationSpec>
    ): List<Migration> = emptyList()

    private fun createOpenDelegate(): RoomOpenHelper.Delegate {
        return object : RoomOpenHelper.Delegate(2) { // version = 2 skladno z @Database
            override fun createAllTables(db: SupportSQLiteDatabase) {
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
                        `status` TEXT NOT NULL DEFAULT 'COMPLETED',
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
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_gps_points_sessionId` ON `gps_points`(`sessionId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42,'$IDENTITY_HASH')")
            }

            override fun dropAllTables(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `gps_points`")
                db.execSQL("DROP TABLE IF EXISTS `workout_sessions`")
            }

            override fun onCreate(db: SupportSQLiteDatabase) {}

            override fun onOpen(db: SupportSQLiteDatabase) {
                mDatabase = db
                db.execSQL("PRAGMA foreign_keys = ON")
                internalInitInvalidationTracker(db)
            }

            override fun onPreMigrate(db: SupportSQLiteDatabase) {}
            override fun onPostMigrate(db: SupportSQLiteDatabase) {}

            override fun onValidateSchema(db: SupportSQLiteDatabase): RoomOpenHelper.ValidationResult =
                RoomOpenHelper.ValidationResult(true, null)
        }
    }

    companion object {
        // ⚠️ Ta hash je placeholder. KSP generira pravo vrednost iz sheme.
        //    fallbackToDestructiveMigration() v AppDatabase zagotavlja, da napačen
        //    hash ne sesuje aplikacije — samo obnovi prazen DB.
        private const val IDENTITY_HASH = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
    }
}

// ── Cursor helper funkcije ─────────────────────────────────────────────────────

private fun Cursor.toWorkoutSessionEntity(): WorkoutSessionEntity {
    val ci = { name: String -> getColumnIndexOrThrow(name) }
    return WorkoutSessionEntity(
        id              = getString(ci("id")),
        userId          = getString(ci("userId")),
        startTime       = getLong(ci("startTime")),
        endTime         = getLong(ci("endTime")),
        durationSeconds = getInt(ci("durationSeconds")),
        distanceMeters  = getDouble(ci("distanceMeters")),
        maxSpeedMps     = getFloat(ci("maxSpeedMps")),
        avgSpeedMps     = getFloat(ci("avgSpeedMps")),
        createdAt       = getLong(ci("createdAt")),
        caloriesKcal    = getInt(ci("caloriesKcal")),
        elevationGainM  = getFloat(ci("elevationGainM")),
        elevationLossM  = getFloat(ci("elevationLossM")),
        activityType    = getString(ci("activityType")),
        isSmoothed      = getInt(ci("isSmoothed")) != 0,
        status          = runCatching { getString(ci("status")) }.getOrDefault("COMPLETED")
    )
}

private fun WorkoutSessionEntity.toContentValues(): ContentValues =
    ContentValues().apply {
        put("id",               id)
        put("userId",           userId)
        put("startTime",        startTime)
        put("endTime",          endTime)
        put("durationSeconds",  durationSeconds)
        put("distanceMeters",   distanceMeters)
        put("maxSpeedMps",      maxSpeedMps)
        put("avgSpeedMps",      avgSpeedMps)
        put("createdAt",        createdAt)
        put("caloriesKcal",     caloriesKcal)
        put("elevationGainM",   elevationGainM)
        put("elevationLossM",   elevationLossM)
        put("activityType",     activityType)
        put("isSmoothed",       if (isSmoothed) 1 else 0)
        put("status",           status)
    }

private fun Cursor.toGpsPointEntity(): GpsPointEntity {
    val ci = { name: String -> getColumnIndexOrThrow(name) }
    return GpsPointEntity(
        uid       = getLong(ci("uid")),
        sessionId = getString(ci("sessionId")),
        latitude  = getDouble(ci("latitude")),
        longitude = getDouble(ci("longitude")),
        altitude  = getDouble(ci("altitude")),
        speed     = getFloat(ci("speed")),
        accuracy  = getFloat(ci("accuracy")),
        timestamp = getLong(ci("timestamp")),
        isRaw     = getInt(ci("isRaw")) != 0
    )
}

private fun GpsPointEntity.toContentValues(): ContentValues =
    ContentValues().apply {
        if (uid != 0L) put("uid", uid)
        put("sessionId", sessionId)
        put("latitude",  latitude)
        put("longitude", longitude)
        put("altitude",  altitude)
        put("speed",     speed)
        put("accuracy",  accuracy)
        put("timestamp", timestamp)
        put("isRaw",     if (isRaw) 1 else 0)
    }

