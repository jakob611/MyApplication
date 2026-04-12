package com.example.myapplication.data.metrics
import com.example.myapplication.domain.metrics.MetricsRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
class MetricsRepositoryImpl : MetricsRepository {
    private val db = Firebase.firestore
    override suspend fun saveWeight(uid: String, weightKg: Float, dateStr: String): Result<Unit> {
        return try {
            val userRef = db.collection("users").document(uid)
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
            val snap = db.collection("users").document(uid)
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
