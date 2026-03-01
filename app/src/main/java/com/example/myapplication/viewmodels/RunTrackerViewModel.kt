package com.example.myapplication.viewmodels

import androidx.lifecycle.ViewModel
import com.example.myapplication.data.RunSession
import com.google.firebase.firestore.FirebaseFirestore

/**
 * ViewModel za RunTrackerScreen — samo branje zgodovine tekov.
 * Dejansko sledenje teka izvaja RunTrackingService (foreground service).
 */
class RunTrackerViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Naloži pretekle teke iz Firestore.
     */
    fun loadRunSessions(onResult: (List<RunSession>) -> Unit) {
        val userId = com.example.myapplication.persistence.FirestoreHelper.getCurrentUserDocId() ?: return

        firestore.collection("users")
            .document(userId)
            .collection("runSessions")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
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
                            caloriesKcal = (data["caloriesKcal"] as? Number)?.toInt() ?: 0
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
