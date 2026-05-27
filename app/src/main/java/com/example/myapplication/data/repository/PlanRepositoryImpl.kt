package com.example.myapplication.data.repository

import com.example.myapplication.data.store.PlanDataStore
import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.domain.repository.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Faza 30.4 — Edina implementacija PlanRepository vmesnika.
 * Faza 41 — Dodana implementacija `observePlans()` za reaktivni Flow.
 *
 * DATA sloj: sme klicati PlanDataStore.
 * Domain in Presentation sloja tega ne smeta.
 *
 * Klic chain: VM → SwapPlanDaysUseCase → PlanRepository → PlanRepositoryImpl → PlanDataStore
 *             VM → BodyOverviewViewmodel → PlanRepository → PlanRepositoryImpl → PlanDataStore
 *
 * [NonCancellable]: Plan swap je atomarna operacija (dva dni morata biti zamenjena skupaj).
 * Delni zapis (en dan posodobljen, drugi ne) bi pustil plan v neveljavnem stanju.
 * NonCancellable zagotovi, da viewModelScope cancel ne prekine sredi transakcije.
 */
class PlanRepositoryImpl : PlanRepository {

    /**
     * Delegira na PlanDataStore.plansFlow() ki vzdržuje Firestore Snapshot Listener.
     * Ta metoda je edino dovoljeno mesto za branje planov iz data prikaznega sloja naprej.
     */
    override fun observePlans(): Flow<List<PlanResult>> = PlanDataStore.plansFlow()

    override suspend fun swapDays(planId: String, dayA: Int, dayB: Int): Result<Unit> =
        withContext(Dispatchers.IO + NonCancellable) {
            PlanDataStore.swapDaysAtomically(planId, dayA, dayB)
        }
}
