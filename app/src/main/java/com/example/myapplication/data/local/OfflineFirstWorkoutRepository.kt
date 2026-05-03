package com.example.myapplication.data.local

import android.util.Log
import com.example.myapplication.data.LocationPoint
import com.example.myapplication.data.RunSession
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

/**
 * OfflineFirstWorkoutRepository — Faza 3: Offline-First strategija za Activity Log.
 *
 * Arhitektura:
 *  1. sessionsFlow → Room (0ms latenca) → UI prikaže takoj ob zagonu
 *  2. syncFromFirestore() → delta sync: samo createdAt > lastLocalTimestamp → UPSERT v Room
 *  3. Data Splitting:
 *     - Room: polne surove GPS točke (isRaw=true) → odlična kakovost za S24 Ultra
 *     - Firestore: RDP-kompresirana pot (isRaw=false) → 90% manj stroškov
 *
 * Preprečevanje podvajanja: Room @Upsert se zanaša na Firestore doc.id kot @PrimaryKey.
 * Firestore doc.id == WorkoutSessionEntity.id → isti dokument vedno prepiše obstoječo vrstico.
 */
class OfflineFirstWorkoutRepository(private val db: AppDatabase) {

    companion object {
        private const val TAG = "OfflineFirstRepo"
        private const val SYNC_LIMIT = 50L
    }

    // ─── Live Flow iz Room ────────────────────────────────────────────────
    val sessionsFlow: Flow<List<RunSession>> = db.workoutSessionDao()
        .getSessionsFlow()
        .map { entities -> entities.map { it.toRunSession() } }

    // ─── Firestore delta sync ─────────────────────────────────────────────
    /**
     * Prenese samo NOVE seje iz Firestora (po lastnem createdAt iz Rooma).
     * Firestore query: whereGreaterThan("createdAt", lastTimestamp) + orderBy("createdAt")
     * Ne zahteva kompozitnega indeksa — filter in orderBy sta na istem polju.
     */
    suspend fun syncFromFirestore() {
        val userId = FirestoreHelper.getCurrentUserDocId() ?: return
        val lastTimestamp = db.workoutSessionDao().getLatestCreatedAt(userId) ?: 0L

        Log.d(TAG, "Delta sync: lastTimestamp=$lastTimestamp")

        try {
            val userRef = FirestoreHelper.getCurrentUserDocRef()
            val baseCollection = userRef.collection("runSessions")

            // Delta: samo novejši teki (prihrani ~90% Firestore reads ob rednih odprtjih)
            val filteredQuery: Query = if (lastTimestamp > 0L) {
                baseCollection.whereGreaterThan("createdAt", lastTimestamp)
            } else {
                baseCollection
            }

            val snapshot = filteredQuery
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(SYNC_LIMIT)
                .get()
                .await()

            if (snapshot.isEmpty) {
                Log.d(TAG, "Sync: ni novih sej v Firestoreu")
                return
            }

            val entities = mutableListOf<WorkoutSessionEntity>()
            val gpsToInsert = mutableListOf<Pair<String, List<GpsPointEntity>>>()

            snapshot.documents.forEach { doc ->
                try {
                    val d = doc.data ?: return@forEach
                    val sessionId = d["id"] as? String ?: doc.id

                    entities.add(
                        WorkoutSessionEntity(
                            id = sessionId,
                            userId = d["userId"] as? String ?: userId,
                            startTime = (d["startTime"] as? Number)?.toLong() ?: 0L,
                            endTime = (d["endTime"] as? Number)?.toLong() ?: 0L,
                            durationSeconds = (d["durationSeconds"] as? Number)?.toInt() ?: 0,
                            distanceMeters = (d["distanceMeters"] as? Number)?.toDouble() ?: 0.0,
                            maxSpeedMps = (d["maxSpeedMps"] as? Number)?.toFloat() ?: 0f,
                            avgSpeedMps = (d["avgSpeedMps"] as? Number)?.toFloat() ?: 0f,
                            createdAt = (d["createdAt"] as? Number)?.toLong() ?: 0L,
                            caloriesKcal = (d["caloriesKcal"] as? Number)?.toInt() ?: 0,
                            elevationGainM = (d["elevationGainM"] as? Number)?.toFloat() ?: 0f,
                            elevationLossM = (d["elevationLossM"] as? Number)?.toFloat() ?: 0f,
                            activityType = d["activityType"] as? String ?: "RUN",
                            isSmoothed = d["isSmoothed"] as? Boolean ?: false
                        )
                    )

                    // Shrani kompresiran GPS iz Firestore (isRaw=false)
                    val existingCount = db.gpsPointDao().getPointCount(sessionId)
                    if (existingCount == 0) {
                        @Suppress("UNCHECKED_CAST")
                        val rawPts = d["polylinePoints"] as? List<Map<String, Any>>
                        if (!rawPts.isNullOrEmpty()) {
                            val gpsEntities = rawPts.mapNotNull { pt ->
                                try {
                                    GpsPointEntity(
                                        sessionId = sessionId,
                                        latitude = (pt["lat"] as? Number)?.toDouble() ?: return@mapNotNull null,
                                        longitude = (pt["lng"] as? Number)?.toDouble() ?: return@mapNotNull null,
                                        altitude = (pt["alt"] as? Number)?.toDouble() ?: 0.0,
                                        speed = (pt["spd"] as? Number)?.toFloat() ?: 0f,
                                        accuracy = (pt["acc"] as? Number)?.toFloat() ?: 0f,
                                        timestamp = (pt["ts"] as? Number)?.toLong() ?: 0L,
                                        isRaw = false // Firestore kompresiran GPS (RDP)
                                    )
                                } catch (e: Exception) { null }
                            }
                            if (gpsEntities.isNotEmpty()) {
                                gpsToInsert.add(Pair(sessionId, gpsEntities))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse napaka za doc ${doc.id}: ${e.message}")
                }
            }

            if (entities.isNotEmpty()) {
                db.workoutSessionDao().upsertAll(entities)
                gpsToInsert.forEach { (_, pts) -> db.gpsPointDao().insertAll(pts) }
                Log.d(TAG, "Sync OK: ${entities.size} sej upsert-anih, GPS za ${gpsToInsert.size} sej")
            }

        } catch (e: Exception) {
            Log.e(TAG, "syncFromFirestore napaka: ${e.message}", e)
        }
    }

    // ─── Lokalno shranjevanje (ob zaključku teka v RunTrackerScreen) ───────
    /**
     * Shrani sejo in polne surove GPS točke v Room takoj po teku.
     * isRaw=true → se ohranijo ne glede na Firestore sync.
     * Kliče se POLEG Firestore save v RunTrackerScreen — ne zamenja ga.
     */
    suspend fun insertLocalSession(session: RunSession, rawPoints: List<LocationPoint>) {
        db.workoutSessionDao().upsert(session.toEntity())
        if (rawPoints.isNotEmpty()) {
            // Zbrišemo morebitne stare (kompresiran GPS) in nadomestimo s surovimi
            db.gpsPointDao().deleteBySessionId(session.id)
            db.gpsPointDao().insertAll(
                rawPoints.map { it.toGpsPointEntity(session.id, isRaw = true) }
            )
            Log.d(TAG, "Local save: ${rawPoints.size} surovih GPS točk za ${session.id}")
        }
    }

    // ─── GPS točke za prikaz ──────────────────────────────────────────────
    /**
     * Vrne GPS točke za sejo.
     * Prioritizira isRaw=true (surove lokalne točke) pred kompresiranimi (Firestore).
     */
    suspend fun getGpsPoints(sessionId: String): List<LocationPoint> {
        return db.gpsPointDao()
            .getPointsPreferRaw(sessionId)
            .map { it.toLocationPoint() }
    }

    // ─── Brisanje ─────────────────────────────────────────────────────────
    /** Room CASCADE bo samodejno zbrisal tudi gps_points za to sejo */
    suspend fun deleteSession(sessionId: String) {
        db.workoutSessionDao().deleteById(sessionId)
    }
}




