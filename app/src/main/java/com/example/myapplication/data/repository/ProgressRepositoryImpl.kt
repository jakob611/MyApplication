package com.example.myapplication.data.repository

import android.util.Log
import com.example.myapplication.data.store.FirestoreHelper
import com.example.myapplication.data.store.NutritionPlanStore
import com.example.myapplication.domain.model.DailyLogSummary
import com.example.myapplication.domain.model.WeightLog
import com.example.myapplication.domain.repository.ProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/**
 * Implementacija [ProgressRepository].
 *
 * Arhitekturna pravila:
 *  - IZKLJUČNO FirestoreHelper.getCurrentUserDocRef() — nikoli getUserRef(uid) z zunanjim UID-om.
 *  - Vse Firestore operacije na Dispatchers.IO.
 *  - Singleton prek companion object (@Volatile thread-safe).
 */
class ProgressRepositoryImpl private constructor() : ProgressRepository {

    companion object {
        private const val TAG = "ProgressRepository"

        @Volatile private var INSTANCE: ProgressRepositoryImpl? = null

        fun getInstance(): ProgressRepositoryImpl =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProgressRepositoryImpl().also { INSTANCE = it }
            }
    }

    // ── Reactive Streams ──────────────────────────────────────────────────────

    override fun observeWeightLogs(): Flow<List<WeightLog>> = callbackFlow {
        val ref = try {
            FirestoreHelper.getCurrentUserDocRef()
        } catch (e: Exception) {
            Log.w(TAG, "observeWeightLogs: getCurrentUserDocRef failed — ${e.message}")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = ref.collection("weightLogs")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w(TAG, "weightLogs snapshot error: ${error.message}")
                    return@addSnapshotListener
                }
                val logs = snap?.documents?.mapNotNull { d ->
                    val dateStr = d.getString("date") ?: d.id
                    val w = (d.get("weightKg") as? Number)?.toDouble() ?: return@mapNotNull null
                    val date = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                        ?: return@mapNotNull null
                    WeightLog(date, w)
                }?.sortedBy { it.date } ?: emptyList()
                trySend(logs)
            }

        awaitClose { listener.remove() }
    }.catch { e ->
        Log.e(TAG, "observeWeightLogs flow error: ${e.message}")
        emit(emptyList())
    }

    override fun observeDailyLogs(): Flow<List<DailyLogSummary>> = callbackFlow {
        val ref = try {
            FirestoreHelper.getCurrentUserDocRef()
        } catch (e: Exception) {
            Log.w(TAG, "observeDailyLogs: getCurrentUserDocRef failed — ${e.message}")
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = ref.collection("dailyLogs")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.w(TAG, "dailyLogs snapshot error: ${error.message}")
                    return@addSnapshotListener
                }
                val logs = snap?.documents?.mapNotNull { d ->
                    val dateStr = d.getString("date") ?: d.id
                    val date = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                        ?: return@mapNotNull null
                    val calories = (d.get("consumedCalories") as? Number)?.toDouble() ?: 0.0
                    val water = (d.get("waterMl") as? Number)?.toInt() ?: 0
                    val burned = (d.get("burnedCalories") as? Number)?.toDouble() ?: 0.0
                    DailyLogSummary(date, calories, water, burned)
                }?.sortedBy { it.date } ?: emptyList()
                trySend(logs)
            }

        awaitClose { listener.remove() }
    }.catch { e ->
        Log.e(TAG, "observeDailyLogs flow error: ${e.message}")
        emit(emptyList())
    }

    // ── Write Operations ──────────────────────────────────────────────────────

    override suspend fun saveWeightLog(dateStr: String, weightKg: Double): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                FirestoreHelper.getCurrentUserDocRef()
                    .collection("weightLogs")
                    .document(dateStr)
                    .set(mapOf("date" to dateStr, "weightKg" to weightKg))
                    .await()
                Unit
            }.onFailure { Log.e(TAG, "saveWeightLog failed: ${it.message}") }
        }

    override suspend fun recalculateUserNutritionPlan(weightKg: Double): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = FirestoreHelper.getCurrentUserDocId()
                    ?: throw IllegalStateException("User not authenticated — cannot recalculate nutrition plan")
                // P-4 Fix: NutritionPlanStore enkapsuliran tukaj, ne v @Composable
                NutritionPlanStore.recalculateNutritionPlan(uid, weightKg)
            }.onFailure { Log.e(TAG, "recalculateNutritionPlan failed: ${it.message}") }
        }
}

