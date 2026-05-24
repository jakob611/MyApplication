package com.example.myapplication.data.repository

import com.example.myapplication.data.store.PlanDataStore
import com.example.myapplication.domain.repository.PlanRepository

/**
 * Faza 30.4 — Edina implementacija PlanRepository vmesnika.
 *
 * DATA sloj: sme klicati PlanDataStore.
 * Domain in Presentation sloja tega ne smeta.
 *
 * Klic chain: VM → SwapPlanDaysUseCase → PlanRepository → PlanRepositoryImpl → PlanDataStore
 */
class PlanRepositoryImpl : PlanRepository {

    override suspend fun swapDays(planId: String, dayA: Int, dayB: Int): Result<Unit> {
        return PlanDataStore.swapDaysAtomically(planId, dayA, dayB)
    }
}
