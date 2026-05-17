package com.example.myapplication.data.local.doo

import androidx.room.*
import com.example.myapplication.data.local.entity.GpsPointEntity
import com.example.myapplication.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.flow.Flow

// ─── WorkoutSessionDao ─────────────────────────────────────────────────────

@Dao
interface WorkoutSessionDao {

    /** Live Flow za UI — Room samodejno obvesti ko se podatki spremenijo */
    @Query("SELECT * FROM workout_sessions ORDER BY createdAt DESC")
    fun getSessionsFlow(): Flow<List<WorkoutSessionEntity>>

    /** Upsert: INSERT OR REPLACE — preprečuje podvajanje na osnovi id (Firestore doc.id) */
    @Upsert
    suspend fun upsertAll(sessions: List<WorkoutSessionEntity>)

    @Upsert
    suspend fun upsert(session: WorkoutSessionEntity)

    /** Delta sync: MAX createdAt → Firestore vrne samo novejše od tega */
    @Query("SELECT MAX(createdAt) FROM workout_sessions WHERE userId = :userId")
    suspend fun getLatestCreatedAt(userId: String): Long?

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM workout_sessions WHERE userId = :userId")
    suspend fun getSessionCount(userId: String): Int

    // Faza 15: Obnova po OOM — vrne zadnjo aktivno (nedokončano) sejo
    @Query("SELECT * FROM workout_sessions WHERE status = 'IN_PROGRESS' ORDER BY startTime DESC LIMIT 1")
    suspend fun getInProgressSession(): WorkoutSessionEntity?

    // Faza 15: Posodobi status seje (IN_PROGRESS → COMPLETED)
    @Query("UPDATE workout_sessions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)
}

// ─── GpsPointDao ──────────────────────────────────────────────────────────

@Dao
interface GpsPointDao {

    /** Vrne GPS točke za dano sejo, razvrščene po času */
    @Query("SELECT * FROM gps_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getPointsForSession(sessionId: String): List<GpsPointEntity>

    /** Prioriteta: raw (isRaw=true) točke pred kompresiranimi — S24 Ultra kakovost */
    @Query("SELECT * FROM gps_points WHERE sessionId = :sessionId ORDER BY isRaw DESC, timestamp ASC")
    suspend fun getPointsPreferRaw(sessionId: String): List<GpsPointEntity>

    /** IGNORE: GPS točke so nespremenljive po vpisu ��� ne prepisuj obstoječih */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(points: List<GpsPointEntity>)

    @Query("DELETE FROM gps_points WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("SELECT COUNT(*) FROM gps_points WHERE sessionId = :sessionId")
    suspend fun getPointCount(sessionId: String): Int
}

