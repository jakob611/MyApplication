package com.example.myapplication.data.repository

import com.example.myapplication.domain.metrics.MetricsRepository
import com.example.myapplication.data.store.FirestoreHelper
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Android implementation of MetricsRepository using Firebase Firestore.
 */
class MetricsRepositoryImpl : MetricsRepository {

    /**
     * [NonCancellable]: Teža se mora zapisati v oba dokumenta (weightLogs + dailyMetrics).
     * Delni zapis (samo eden od dveh) bi povzročil neskladnost med Progress grafom in
     * dnevnimi metrikami. NonCancellable zagotovi, da oba .await() klica zaključita.
     */
    override suspend fun saveWeight(uid: String, weightKg: Float, dateStr: String): Result<Unit> =
        withContext(Dispatchers.IO + NonCancellable) {
            try {
                val userRef = FirestoreHelper.getCurrentUserDocRef()

                userRef.collection("weightLogs").document(dateStr).set(
                    mapOf(
                        "date" to dateStr,
                        "weightKg" to weightKg.toDouble(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()

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