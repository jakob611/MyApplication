package com.example.myapplication.data.workout

import android.util.Log
import com.example.myapplication.data.LocationPoint
import com.example.myapplication.data.RunSession
import com.example.myapplication.domain.workout.WorkoutRepository
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirestoreWorkoutRepository : WorkoutRepository {

    override suspend fun getWeeklyDoneCount(email: String, startEpochSeconds: Long): Int {
        val startTimestamp = Timestamp(startEpochSeconds, 0)
        return try {
            val ref = FirestoreHelper.getCurrentUserDocRef()
            val querySnapshot = ref.collection("workoutSessions")
                .whereGreaterThanOrEqualTo("date", startTimestamp)
                .get()
                .await()
            querySnapshot.documents.count { doc ->
                (doc.getString("type") ?: "regular") == "regular"
            }
        } catch (e: Exception) {
            -1
        }
    }

    override suspend fun saveWorkoutSession(email: String, workoutDoc: Map<String, Any>): Boolean {
        return try {
            val ref = FirestoreHelper.getCurrentUserDocRef()
            ref.collection("workoutSessions").add(workoutDoc).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Naloži GPS točke za sesijo — poskusi v vrstnem redu:
     *  1. Sub-kolekcija `gps_points` (nova naprava / nova pot)
     *  2. Sub-kolekcija `points` (po migracijskem načrtu GPS_POINTS_MIGRATION_PLAN.md)
     *  3. Inline `polylinePoints` array v dokumentu (stari format, backwards compat)
     *
     * Pot: users/{uid}/runSessions/{docId}/gps_points  ali  .../points
     */
    private suspend fun loadGpsPoints(
        sessionRef: DocumentReference,
        inlinePoints: List<LocationPoint>
    ): List<LocationPoint> {
        // 1. Poskusi gps_points subcollection
        try {
            val gpsSnap = sessionRef.collection("gps_points")
                .orderBy("chunkIndex")
                .get().await()
            if (!gpsSnap.isEmpty) {
                val pts = gpsSnap.documents.flatMap { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val rawPts = doc.get("pts") as? List<Map<String, Any>> ?: emptyList()
                    rawPts.mapNotNull { pt ->
                        try {
                            LocationPoint(
                                latitude  = (pt["lat"] as? Number)?.toDouble() ?: return@mapNotNull null,
                                longitude = (pt["lng"] as? Number)?.toDouble() ?: return@mapNotNull null,
                                altitude  = (pt["alt"] as? Number)?.toDouble() ?: 0.0,
                                speed     = (pt["spd"] as? Number)?.toFloat()  ?: 0f,
                                accuracy  = 0f,
                                timestamp = (pt["ts"]  as? Number)?.toLong()   ?: 0L
                            )
                        } catch (e: Exception) {
                            Log.e("WorkoutRepo", "❌ gps_points točka parse napaka @ ${sessionRef.path}: ${e.message}", e)
                            null
                        }
                    }
                }
                if (pts.isNotEmpty()) {
                    Log.d("WorkoutRepo", "GPS: ${pts.size} točk iz gps_points @ ${sessionRef.path}")
                    return pts
                } else {
                    Log.w("WorkoutRepo", "⚠️ gps_points subcollection obstaja ampak je prazna @ ${sessionRef.path}")
                }
            } else {
                Log.d("WorkoutRepo", "GPS: gps_points subcollection prazna @ ${sessionRef.path}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepo", "❌ gps_points fetch napaka @ ${sessionRef.path}: ${e.message}", e)
        }

        // 2. Poskusi `points` subcollection (GPS_POINTS_MIGRATION_PLAN format)
        try {
            val pointsSnap = sessionRef.collection("points")
                .orderBy("chunkIndex")
                .get().await()
            if (!pointsSnap.isEmpty) {
                val pts = pointsSnap.documents.flatMap { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val rawPts = doc.get("pts") as? List<Map<String, Any>> ?: emptyList()
                    rawPts.mapNotNull { pt ->
                        try {
                            LocationPoint(
                                latitude  = (pt["lat"] as? Number)?.toDouble() ?: return@mapNotNull null,
                                longitude = (pt["lng"] as? Number)?.toDouble() ?: return@mapNotNull null,
                                altitude  = (pt["alt"] as? Number)?.toDouble() ?: 0.0,
                                speed     = 0f, accuracy = 0f,
                                timestamp = (pt["ts"] as? Number)?.toLong() ?: 0L
                            )
                        } catch (e: Exception) {
                            Log.e("WorkoutRepo", "❌ points točka parse napaka @ ${sessionRef.path}: ${e.message}", e)
                            null
                        }
                    }
                }
                if (pts.isNotEmpty()) {
                    Log.d("WorkoutRepo", "GPS: ${pts.size} točk iz points sub-kolekcije @ ${sessionRef.path}")
                    return pts
                } else {
                    Log.w("WorkoutRepo", "⚠️ points subcollection obstaja ampak je prazna @ ${sessionRef.path}")
                }
            } else {
                Log.d("WorkoutRepo", "GPS: points subcollection prazna @ ${sessionRef.path}")
            }
        } catch (e: Exception) {
            Log.e("WorkoutRepo", "❌ points subcollection fetch napaka @ ${sessionRef.path}: ${e.message}", e)
        }

        // 3. Fallback: inline polylinePoints (stari format)
        Log.d("WorkoutRepo", "GPS fallback: inline polylinePoints (${inlinePoints.size} točk) @ ${sessionRef.path}")
        return inlinePoints
    }

    override suspend fun getRunSessions(userId: String, startAfterDoc: Any?, limit: Int): Pair<List<RunSession>, Any?> {
        return try {
            val userRef = FirestoreHelper.getCurrentUserDocRef()
            var query = userRef
                .collection("runSessions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())

            if (startAfterDoc is DocumentSnapshot) {
                query = query.startAfter(startAfterDoc)
            }

            val snapshot = query.get().await()
            val sessions = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null
                    // Preberi inline polylinePoints (fallback za stare dokumente)
                    val inlinePoints = try {
                        @Suppress("UNCHECKED_CAST")
                        val rawPts = data["polylinePoints"] as? List<Map<String, Any>>
                        rawPts?.mapNotNull { pt ->
                            try {
                                LocationPoint(
                                    latitude  = (pt["latitude"]  as? Number ?: pt["lat"]      as? Number)?.toDouble() ?: return@mapNotNull null,
                                    longitude = (pt["longitude"] as? Number ?: pt["lng"]      as? Number)?.toDouble() ?: return@mapNotNull null,
                                    altitude  = (pt["altitude"]  as? Number ?: pt["alt"]      as? Number)?.toDouble() ?: 0.0,
                                    speed     = (pt["speed"]     as? Number ?: pt["spd"]      as? Number)?.toFloat()  ?: 0f,
                                    accuracy  = (pt["accuracy"]  as? Number ?: pt["acc"]      as? Number)?.toFloat()  ?: 0f,
                                    timestamp = (pt["timestamp"] as? Number ?: pt["ts"]       as? Number)?.toLong()   ?: 0L
                                )
                            } catch (_: Exception) { null }
                        } ?: emptyList()
                    } catch (_: Exception) { emptyList() }

                    // ── GPS Firestore Data Link ───────────────────────────────────────────
                    // Za cycling in walk: dodatni fetch sub-kolekcije gps_points / points
                    // ker GPS točke morda niso vgrajene v glavni dokument (drugi telefon!)
                    val activityTypeStr = data["activityType"] as? String ?: ""
                    val needsSubFetch = activityTypeStr.equals("CYCLING", ignoreCase = true) ||
                                       activityTypeStr.equals("WALKING", ignoreCase = true) ||
                                       activityTypeStr.equals("RUNNING", ignoreCase = true) ||
                                       inlinePoints.isEmpty() // vedno poskusi sub-kolekcijo če inline ni podatkov
                    val finalPoints = if (needsSubFetch) {
                        loadGpsPoints(doc.reference, inlinePoints)
                    } else inlinePoints
                    // ──────────────────────────────────────────────────────────────────────

                    RunSession(
                        id = data["id"] as? String ?: "",
                        userId = data["userId"] as? String ?: "",
                        startTime = (data["startTime"] as? Number)?.toLong() ?: 0L,
                        endTime = (data["endTime"] as? Number)?.toLong() ?: 0L,
                        durationSeconds = (data["durationSeconds"] as? Number)?.toInt() ?: 0,
                        distanceMeters = (data["distanceMeters"] as? Number)?.toDouble() ?: 0.0,
                        maxSpeedMps = (data["maxSpeedMps"] as? Number)?.toFloat() ?: 0.0f,
                        avgSpeedMps = (data["avgSpeedMps"] as? Number)?.toFloat() ?: 0.0f,
                        polylinePoints = finalPoints,
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                        caloriesKcal = (data["caloriesKcal"] as? Number)?.toInt() ?: 0,
                        elevationGainM = (data["elevationGainM"] as? Number)?.toFloat() ?: 0f,
                        elevationLossM = (data["elevationLossM"] as? Number)?.toFloat() ?: 0f,
                        activityType = com.example.myapplication.data.ActivityType.fromString(activityTypeStr),
                        isSmoothed = data["isSmoothed"] as? Boolean ?: false
                    )
                } catch (e: Exception) {
                    null
                }
            }
            Pair(sessions, snapshot.documents.lastOrNull())
        } catch (e: Exception) {
            Pair(emptyList(), null)
        }
    }
}

