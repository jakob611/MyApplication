package com.example.myapplication.domain.repository

/**
 * Faza 30.4 — Domenski vmesnik za operacije nad načrtom treninga.
 *
 * ViewModel in Use Case ne smeta vedeti za obstoj Firestore ali PlanDataStore.
 * Implementacija je v data sloju (PlanRepositoryImpl).
 */
interface PlanRepository {
    /**
     * Atomarna zamenjava celotnih objektov dni v trenažnem načrtu.
     *
     * Garantira:
     *   - Celoten vsebinski swap (vse vaje, rutine, statusi)
     *   - Pravilni indeksi dni (dayNumber) ostanejo na mestu
     *   - Firestore transakcija — brez race conditiona
     *
     * @return Result.success(Unit) ob uspešnem zapisu, Result.failure z opisno napako sicer.
     */
    suspend fun swapDays(planId: String, dayA: Int, dayB: Int): Result<Unit>
}
