package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import com.example.myapplication.data.ActivityType
import com.example.myapplication.data.RunSession
import com.google.firebase.firestore.FirebaseFirestore

/**
 * ViewModel za RunTrackerScreen — samo branje zgodovine tekov.
 * Dejansko sledenje teka izvaja RunTrackingService (foreground service).
 */
class RunTrackerViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private var lastVisibleDoc: com.google.firebase.firestore.DocumentSnapshot? = null
    var isLastPage = false
        private set

    /**
     * Naloži pretekle teke iz Firestore z load-more paginacijo.
     */
    fun loadRunSessions(isLoadMore: Boolean = false, onResult: (List<RunSession>) -> Unit) {
        val userId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId()
        if (userId == null) {
            onResult(emptyList())
            return
        }

        if (isLoadMore && isLastPage) {
            onResult(emptyList())
            return
        }

        var query = firestore.collection("users")
            .document(userId)
            .collection("runSessions")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(15)

        if (isLoadMore && lastVisibleDoc != null) {
            query = query.startAfter(lastVisibleDoc!!)
        } else if (!isLoadMore) {
            lastVisibleDoc = null
            isLastPage = false
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.documents.isNotEmpty()) {
                    lastVisibleDoc = snapshot.documents.last()
                    if (snapshot.documents.size < 15) {
                        isLastPage = true
                    }
                } else {
                    isLastPage = true
                }

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
                            polylinePoints = emptyList(),
                            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                            caloriesKcal = (data["caloriesKcal"] as? Number)?.toInt() ?: 0,
                            elevationGainM = (data["elevationGainM"] as? Number)?.toFloat() ?: 0f,
                            elevationLossM = (data["elevationLossM"] as? Number)?.toFloat() ?: 0f,
                            activityType = ActivityType.fromString(data["activityType"] as? String),
                            isSmoothed = data["isSmoothed"] as? Boolean ?: false
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
                onResult(sessions)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                onResult(emptyList())
            }
    }
}
