package com.example.myapplication.data.daily

import android.util.Log
import com.example.myapplication.persistence.FirestoreHelper
import kotlinx.coroutines.tasks.await
import java.util.Collections

/**
 * Zapisi posamezne Firestore transakcije za Debug Dashboard.
 */
data class TransactionRecord(
    val operation: String,
    val durationMs: Long,
    val status: String,   // "✅ Success" ali "❌ Fail"
    val timestamp: Long = System.currentTimeMillis()
)

class DailyLogRepository {

    companion object {
        /** Zadnjih 5 transakcij — thread-safe synchronized list */
        val lastTransactions: MutableList<TransactionRecord> =
            Collections.synchronizedList(mutableListOf())

        private fun recordTransaction(op: String, durationMs: Long, success: Boolean) {
            val record = TransactionRecord(
                operation = op,
                durationMs = durationMs,
                status = if (success) "✅ Success" else "❌ Fail"
            )
            synchronized(lastTransactions) {
                lastTransactions.add(0, record)
                if (lastTransactions.size > 5) lastTransactions.removeAt(lastTransactions.size - 1)
            }
        }
    }
    private val db get() = FirestoreHelper.getDb()

    /**
     * Atomsko prebere in popravi dailyLogs dokument znotraj Firestore Transakcije.
     * Rešuje predhodne `SetOptions.merge()` in `FieldValue.increment()` napake
     * in preprečuje 'Race Conditions' med večimi UseCase-i in Widgeti, ki istočasno
     * poizkušajo pisati v isti dokument (npr. waterMl in burnedCalories).
     */
    suspend fun updateDailyLog(date: String, action: (MutableMap<String, Any>) -> Unit) {
        val uid = FirestoreHelper.getCurrentUserDocId() ?: return
        val ref = db.collection("users").document(uid).collection("dailyLogs").document(date)

        val startMs = System.currentTimeMillis()
        val opLabel = "updateDailyLog($date)"
        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(ref)
                val data = if (snapshot.exists()) {
                    snapshot.data?.toMutableMap() ?: mutableMapOf()
                } else {
                    mutableMapOf(
                        "date" to date,
                        "burnedCalories" to 0.0,
                        "waterMl" to 0,
                        "consumedCalories" to 0.0,
                        "items" to emptyList<Any>()
                    )
                }
                action(data)
                data["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                transaction.set(ref, data)
            }.await()
            recordTransaction(opLabel, System.currentTimeMillis() - startMs, true)
        } catch (e: Exception) {
            Log.e("DailyLogRepo", "Transakcija za dailyLog spodletela: $date", e)
            recordTransaction(opLabel, System.currentTimeMillis() - startMs, false)
        }
    }
}

