package com.example.myapplication.domain.repository

import com.example.myapplication.domain.model.BodyMeasurementEntry
import kotlinx.coroutines.flow.Flow

/**
 * Faza 30.6 — Domenski vmesnik za telesne meritve.
 *
 * Odgovornosti:
 *   - Batch write: posodobi profil (trenutne vrednosti) + doda zapis v zgodovino
 *   - Reaktivni stream zgodovine za izris grafov
 *
 * ViewModel in UseCase ne smeta vedeti za Firestore.
 * Implementacija: FirestoreBodyMeasurementsRepository (data sloj).
 */
interface BodyMeasurementsRepository {

    /**
     * Dvojni zapis (batch):
     *   a) Posodobi polja v profilu (shoulderCm, waistCm, hipCm) — za takojšen Golden Ratio izračun
     *   b) Doda nov/posodobi dokument v subcollection measurements_history — za grafe napredka
     *
     * Faza 30.9 — [dateId] je vnaprej pripravljen niz (npr. "2026-05-23"), ki ga generira
     * UseCase oz. ViewModel. Repozitorij je tako popolnoma determinističen in testabilen
     * brez odvisnosti od sistemske ure naprave.
     *
     * @param dateId  ID Firestore dokumenta v obliki "YYYY-MM-DD" — isti dan → upsert
     * @return Result.success(Unit) ob uspešnem zapisu, Result.failure sicer
     */
    suspend fun saveMeasurements(
        dateId: String,
        shoulderCm: Double,
        waistCm: Double,
        hipCm: Double,
        heightCm: Double
    ): Result<Unit>

    /**
     * Reaktivni tok celotne zgodovine meritev, urejene po timestampu ASC.
     * Prazen seznam ko ni zgodovine ali ko uporabnik ni prijavljen.
     */
    fun observeMeasurementsHistory(): Flow<List<BodyMeasurementEntry>>
}

