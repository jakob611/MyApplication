package com.example.myapplication.data.metrics

import com.example.myapplication.domain.metrics.MetricsRepository
import com.google.firebase.firestore.FieldValue
import com.example.myapplication.persistence.FirestoreHelper
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Android implementation of MetricsRepository using Firebase Firestore.
 */
class MetricsRepositoryImpl : MetricsRepository {

    override suspend fun saveWeight(uid: String, weightKg: Float, dateStr: String): Result<Unit> {
        return try {
            val userRef = FirestoreHelper.getCurrentUserDocRef()

            // Save to weightLogs
            userRef.collection("weightLogs").document(dateStr).set(
                mapOf(
                    "date" to dateStr,
                    "weightKg" to weightKg.toDouble(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            // Save to dailyMetrics
            userRef.collection("dailyMetrics").document(dateStr).set(
                mapOf(
                    "date" to dateStr,
                    "weight" to weightKg,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getWeight(uid: String, dateStr: String): Result<Float> {
        return try {
            val snap = FirestoreHelper.getCurrentUserDocRef()
                .collection("dailyMetrics").document(dateStr)
                .get()
                .await()
            val serverVal = (snap.get("weight") as? Number)?.toFloat()
            if (serverVal != null) {
                Result.success(serverVal)
            } else {
                Result.failure(Exception("Weight not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
