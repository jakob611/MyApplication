package com.example.myapplication.data.repository

import com.example.myapplication.data.store.PlanDataStore
import com.example.myapplication.domain.repository.PlanRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Faza 30.4 — Edina implementacija PlanRepository vmesnika.
 *
 * DATA sloj: sme klicati PlanDataStore.
 * Domain in Presentation sloja tega ne smeta.
 *
 * Klic chain: VM → SwapPlanDaysUseCase → PlanRepository → PlanRepositoryImpl → PlanDataStore
 *
 * [NonCancellable]: Plan swap je atomarna operacija (dva dni morata biti zamenjena skupaj).
 * Delni zapis (en dan posodobljen, drugi ne) bi pustil plan v neveljavnem stanju.
 * NonCancellable zagotovi, da viewModelScope cancel ne prekine sredi transakcije.
 */
class PlanRepositoryImpl : PlanRepository {

    override suspend fun swapDays(planId: String, dayA: Int, dayB: Int): Result<Unit> =
        withContext(Dispatchers.IO + NonCancellable) {
            PlanDataStore.swapDaysAtomically(planId, dayA, dayB)
        }
}
