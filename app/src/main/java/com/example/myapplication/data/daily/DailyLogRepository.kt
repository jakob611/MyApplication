package com.example.myapplication.data.daily

import android.util.Log
import com.example.myapplication.data.store.FirestoreHelper
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
     *
     * Faza 14 — Zgodovinski Snapshoti: Ob inicializaciji NOVEGA dne (dokument še ne obstaja)
     * zamrzne kalorične in makro cilje, ki so bili aktivni ob tistem datumu.
     * S tem zavarujemo statistiko — stari dnevi ostanejo z originalnimi cilji, ne s trenutnimi.
     *
     * @param initTargetCalories  Kalorični cilj za zamrznitev (samo ob kreaciji novega dokumenta)
     * @param initTargetProtein   Proteinski cilj (g) za zamrznitev
     * @param initTargetCarbs     Ogljikohidratni cilj (g) za zamrznitev
     * @param initTargetFat       Maščobni cilj (g) za zamrznitev
     */
    suspend fun updateDailyLog(
        date: String,
        initTargetCalories: Int? = null,
        initTargetProtein: Int? = null,
        initTargetCarbs: Int? = null,
        initTargetFat: Int? = null,
        action: (MutableMap<String, Any>) -> Unit
    ) {
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
                    // Nov dan — inicializiraj z osnovno strukturo
                    val init = mutableMapOf<String, Any>(
                        "date" to date,
                        "burnedCalories" to 0.0,
                        "waterMl" to 0,
                        "consumedCalories" to 0.0,
                        "items" to emptyList<Any>()
                    )
                    // Faza 14: Zamrzni kalorične cilje tega dne (Zgodovinski Snapshoti).
                    // Te vrednosti se NE prepisujejo pri kasnejših klicih updateDailyLog,
                    // ker jih vključujemo samo v init (ne v action lambda).
                    if (initTargetCalories != null) init["targetCalories"] = initTargetCalories
                    if (initTargetProtein  != null) init["targetProtein"]  = initTargetProtein
                    if (initTargetCarbs    != null) init["targetCarbs"]    = initTargetCarbs
                    if (initTargetFat      != null) init["targetFat"]      = initTargetFat
                    init
                }
                action(data)
                data["updatedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                // SetOptions.merge() — nikoli ne prepiše polj, ki jih action ni modificiral
                transaction.set(ref, data, com.google.firebase.firestore.SetOptions.merge())
            }.await()
            recordTransaction(opLabel, System.currentTimeMillis() - startMs, true)
        } catch (e: Exception) {
            Log.e("DailyLogRepo", "Transakcija za dailyLog spodletela: $date", e)
            recordTransaction(opLabel, System.currentTimeMillis() - startMs, false)
        }
    }
}

