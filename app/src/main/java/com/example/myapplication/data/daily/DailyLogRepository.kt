package com.example.myapplication.data.daily

import android.util.Log
import com.example.myapplication.persistence.FirestoreHelper
import kotlinx.coroutines.tasks.await

class DailyLogRepository {
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

        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(ref)

                // Parsing obstoječih podatkov, ali inicializacija novega dne
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

                // Matematična pretvorba v varni "lock" transakciji brez FieldValue izjem
                action(data)

                // Posodobitev sledi (Timestamp) in zapis prepisanega objkta nazaj
                data["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()

                transaction.set(ref, data)
            }.await()
            // Log.d("DailyLogRepo", "Uspešno izvršena transakcija za dailyLog: $date")
        } catch (e: Exception) {
            Log.e("DailyLogRepo", "Transakcija za dailyLog spodletela: $date", e)
        }
    }
}

