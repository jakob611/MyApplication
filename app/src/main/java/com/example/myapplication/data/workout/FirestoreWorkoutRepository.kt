package com.example.myapplication.data.workout

import com.example.myapplication.data.RunSession
import com.example.myapplication.domain.workout.WorkoutRepository
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
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

    override suspend fun getRunSessions(userId: String, startAfterDoc: Any?, limit: Int): Pair<List<RunSession>, Any?> {
        return try {
            var query = FirestoreHelper.getCurrentUserDocRef()
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
                    RunSession(
                        id = data["id"] as? String ?: "",
                        userId = data["userId"] as? String ?: "",
                        startTime = (data["startTime"] as? Number)?.toLong() ?: 0L,
                        endTime = (data["endTime"] as? Number)?.toLong() ?: 0L,
                        durationSeconds = (data["durationSeconds"] as? Number)?.toInt() ?: 0,
                        distanceMeters = (data["distanceMeters"] as? Number)?.toDouble() ?: 0.0,
                        maxSpeedMps = (data["maxSpeedMps"] as? Number)?.toFloat() ?: 0.0f,
                        avgSpeedMps = (data["avgSpeedMps"] as? Number)?.toFloat() ?: 0.0f,
                        polylinePoints = emptyList(), // Not returning full polyline array to save space
                        createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                        caloriesKcal = (data["caloriesKcal"] as? Number)?.toInt() ?: 0,
                        elevationGainM = (data["elevationGainM"] as? Number)?.toFloat() ?: 0f,
                        elevationLossM = (data["elevationLossM"] as? Number)?.toFloat() ?: 0f,
                        activityType = com.example.myapplication.data.ActivityType.fromString(data["activityType"] as? String),
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

