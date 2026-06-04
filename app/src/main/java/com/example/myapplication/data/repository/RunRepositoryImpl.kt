package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.daily.DailyLogRepository
import com.example.myapplication.data.store.FirestoreHelper
import com.example.myapplication.domain.repository.RunRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Faza 58 — Produkcijska implementacija [RunRepository].
 *
 * ## Arhitekturna pravila:
 *   - IZKLJUČNA uporaba [FirestoreHelper.getCurrentUserDocRef()] za vse uporabniške reference.
 *     Direkten `db.collection("users").document(uid)` je prepovedano (K-2 fix).
 *   - Vse I/O operacije se izvajajo na [Dispatchers.IO].
 *   - Retry logika prek [FirestoreHelper.withRetry] (3x eksponentni backoff).
 *
 * ## Dependency Injection:
 *   Razred prejme [DailyLogRepository] prek konstruktorja za testabilnost.
 *   V produkciji se instancira prek [companion object] singleton dostopnika.
 */
class RunRepositoryImpl(
    private val dailyLogRepository: DailyLogRepository = DailyLogRepository()
) : RunRepository {

    companion object {
        private const val TAG = "RunRepositoryImpl"

        /** Thread-safe singleton za produkcijsko uporabo. */
        @Volatile
        private var INSTANCE: RunRepositoryImpl? = null

        fun getInstance(): RunRepositoryImpl =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: RunRepositoryImpl().also { INSTANCE = it }
            }
    }

    /**
     * Shrani zaključeno kardio sejo v Firestore `runSessions` podkolekcijo.
     *
     * Garantira:
     *   - Pridobitev user ref prek [FirestoreHelper.getCurrentUserDocRef()] (nikoli direktno)
     *   - Retry do 3x z eksponentnim backoff-om
     *   - Thread shift na IO
     */
    override suspend fun saveRunSession(
        sessionId: String,
        sessionData: Map<String, Any>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userRef = FirestoreHelper.getCurrentUserDocRef()
            Log.d(TAG, "WRITE runSessions doc=${userRef.id} session=$sessionId")

            FirestoreHelper.withRetry {
                userRef.collection("runSessions")
                    .document(sessionId)
                    .set(sessionData)
                    .await()
            }

            Log.i(TAG, "WRITE_OK runSessions doc=${userRef.id} session=$sessionId")
            Unit
        }.onFailure { e ->
            Log.e(TAG, "WRITE_FAIL runSessions session=$sessionId", e)
        }
    }

    /**
     * Atomarno doda kalorije v `dailyLogs/{date}/burnedCalories`.
     *
     * Delegira na [DailyLogRepository.updateDailyLog] ki izvaja Firestore transakcijo —
     * preprečuje race conditions med vzporednimi pisci (Widget, NutritionScreen, RunTracker).
     */
    override suspend fun addBurnedCalories(
        date: String,
        caloriesKcal: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(caloriesKcal >= 0) { "caloriesKcal mora biti pozitivno, dobljeno: $caloriesKcal" }
            val kcal = caloriesKcal.toDouble()

            dailyLogRepository.updateDailyLog(date) { data ->
                val existing = (data["burnedCalories"] as? Number)?.toDouble() ?: 0.0
                data["burnedCalories"] = existing + kcal
            }

            Log.d(TAG, "burnedCalories +${caloriesKcal} kcal posodobljeno za $date")
            Unit
        }.onFailure { e ->
            Log.e(TAG, "addBurnedCalories spodletelo za $date", e)
        }
    }

    /**
     * Shrani javno aktivnost v `publicActivities` podkolekcijo.
     * Kliče se pogojno (samo če ima uporabnik `share_activities = true`).
     */
    override suspend fun savePublicActivity(
        sessionId: String,
        publicData: Map<String, Any>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val userRef = FirestoreHelper.getCurrentUserDocRef()
            Log.d(TAG, "WRITE publicActivities doc=${userRef.id} session=$sessionId")

            FirestoreHelper.withRetry {
                userRef.collection("publicActivities")
                    .document(sessionId)
                    .set(publicData)
                    .await()
            }

            Log.i(TAG, "WRITE_OK publicActivities doc=${userRef.id} session=$sessionId")
            Unit
        }.onFailure { e ->
            Log.e(TAG, "WRITE_FAIL publicActivities session=$sessionId", e)
        }
    }
}




