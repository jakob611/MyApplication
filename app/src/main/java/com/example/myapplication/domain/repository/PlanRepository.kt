package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.PlanResult
import kotlinx.coroutines.flow.Flow

/**
 * Faza 30.4 — Domenski vmesnik za operacije nad načrtom treninga.
 * Faza 41 — Dodana `observePlans()` za reaktivno branje planov (Anomaly 2 fix).
 *
 * ViewModel in Use Case ne smeta vedeti za obstoj Firestore ali PlanDataStore.
 * Implementacija je v data sloju (PlanRepositoryImpl).
 */
interface PlanRepository {

    /**
     * Reaktivni tok planov za trenutno prijavljenega uporabnika.
     * Emitira ob vsaki spremembi v Firestore (callbackFlow → Snapshot Listener).
     * Emitira prazen seznam, če uporabnik ni prijavljen.
     */
    fun observePlans(): Flow<List<PlanResult>>

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
