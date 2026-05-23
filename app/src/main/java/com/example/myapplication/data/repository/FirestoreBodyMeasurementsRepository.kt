package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.store.FirestoreHelper
import com.example.myapplication.domain.model.BodyMeasurementEntry
import com.example.myapplication.domain.repository.BodyMeasurementsRepository
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Faza 30.6 — Edina Firestore implementacija BodyMeasurementsRepository.
 *
 * Subcollection schema:
 *   users/{userId}/measurements_history/{YYYY-MM-DD}
 *     shoulderCm: Double
 *     waistCm:    Double
 *     hipCm:      Double
 *     heightCm:   Double
 *     timestamp:  Long   ← epoch ms ob zadnjem shranjevanju tega dne
 *
 * Faza 30.8 — Document ID = datum (YYYY-MM-DD):
 *   • Isti dan → overwrite (upsert) namesto podvajanja
 *   • Preprečuje akumulacijo dupliciranih vnosov ob večkratnih shranitvah
 *   • Batch write zagotavlja atomarnost (profil + history sta vedno sinhronizirana)
 */
class FirestoreBodyMeasurementsRepository : BodyMeasurementsRepository {

    companion object {
        private const val TAG = "BodyMeasurementsRepo"
        private const val HISTORY_COLLECTION = "measurements_history"
    }

    /**
     * Batch write (atomarno):
     *   a) Posodobi shoulderCm / waistCm / hipCm v root user dokumentu — za GoldenRatio izračun
     *   b) Doda nov dokument v measurements_history — za grafe napredka
     */
    override suspend fun saveMeasurements(
        shoulderCm: Double,
        waistCm: Double,
        hipCm: Double,
        heightCm: Double
    ): Result<Unit> {
        return try {
            val db = FirestoreHelper.getDb()
            val userRef = FirestoreHelper.getCurrentUserDocRef()
            val timestamp = System.currentTimeMillis()

            // Faza 30.8: Document ID = "YYYY-MM-DD" → isti dan vedno overwritea obstoječ vnos
            // Rešitev: preprečuje podvajanje podatkov ob večkratnih shranitvah istega dne.
            val dateId = LocalDate.now().toString()  // npr. "2026-05-23"

            val batch = db.batch()

            // a) Posodobi trenutne vrednosti v profilu (merge — ne prepiše ostalih polj)
            val profileUpdate = mapOf(
                "shoulderCm" to shoulderCm,
                "waistCm"    to waistCm,
                "hipCm"      to hipCm
            )
            batch.update(userRef, profileUpdate)

            // b) Novi/posodobljeni dokument v subcollection — ID = datum (upsert semantika)
            val historyRef = userRef
                .collection(HISTORY_COLLECTION)
                .document(dateId)

            val historyDoc = mapOf(
                "shoulderCm" to shoulderCm,
                "waistCm"    to waistCm,
                "hipCm"      to hipCm,
                "heightCm"   to heightCm,
                "timestamp"  to timestamp
            )
            batch.set(historyRef, historyDoc)

            batch.commit().await()

            Log.d(TAG, "✅ Batch write uspel: meritve + history (dateId=$dateId, ts=$timestamp)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Batch write spodletel: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Reaktivni tok celotne zgodovine, urejene ASC po timestamp.
     * callbackFlow — samodejno ugasne ob prekinitvi naročnine.
     */
    override fun observeMeasurementsHistory(): Flow<List<BodyMeasurementEntry>> = callbackFlow {
        val listener = try {
            val userRef = FirestoreHelper.getCurrentUserDocRef()
            userRef.collection(HISTORY_COLLECTION)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        Log.e(TAG, "Listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    val entries = snap?.documents?.mapNotNull { doc ->
                        try {
                            BodyMeasurementEntry(
                                shoulderCm = (doc.getDouble("shoulderCm") ?: 0.0),
                                waistCm    = (doc.getDouble("waistCm")    ?: 0.0),
                                hipCm      = (doc.getDouble("hipCm")      ?: 0.0),
                                heightCm   = (doc.getDouble("heightCm")   ?: 0.0),
                                timestamp  = (doc.getLong("timestamp")    ?: 0L)
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing history doc ${doc.id}: ${e.message}")
                            null
                        }
                    } ?: emptyList()
                    trySend(entries)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting history listener: ${e.message}")
            trySend(emptyList())
            null
        }

        awaitClose { listener?.remove() }
    }
}





