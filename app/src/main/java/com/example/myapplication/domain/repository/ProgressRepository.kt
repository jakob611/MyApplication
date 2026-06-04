package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.DailyLogSummary
import com.example.myapplication.domain.model.WeightLog
import kotlinx.coroutines.flow.Flow

/**
 * Domain vmesnik za Progress Analytics modul.
 * Implementacija: [com.example.myapplication.data.repository.ProgressRepositoryImpl].
 *
 * P-2 Fix: UI/ViewModel ne sme direktno klicati Firestore — izključno prek tega vmesnika.
 * P-3 Fix: saveWeightLog() in domain, ne ViewModel suspend fun.
 * P-4 Fix: recalculateUserNutritionPlan() enkapsulira NutritionPlanStore.
 * P-8 Fix: Implementacija strogo uporablja FirestoreHelper.getCurrentUserDocRef().
 */
interface ProgressRepository {

    /** Reaktivni stream telesnih tež — Firestore SnapshotListener. */
    fun observeWeightLogs(): Flow<List<WeightLog>>

    /**
     * Reaktivni stream dnevnih logov.
     * Vsebuje consumedCalories, waterMl in burnedCalories (iz dailyLogs.burnedCalories).
     */
    fun observeDailyLogs(): Flow<List<DailyLogSummary>>

    /**
     * Shrani telesno težo za določen dan v Firestore.
     * P-3 Fix: Odstranjeno iz ProgressViewModel in WeightEntryDialog.
     */
    suspend fun saveWeightLog(dateStr: String, weightKg: Double): Result<Unit>

    /**
     * Sproži ponovni izračun prehranskega plana z novo težo.
     * P-4 Fix: NutritionPlanStore enkapsuliran v Repository — ne v @Composable.
     * @return true = uspešno, false = manjkajoči plan podatki.
     */
    suspend fun recalculateUserNutritionPlan(weightKg: Double): Result<Boolean>
}

