package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.repository.BodyMeasurementsRepository
import java.time.LocalDate

/**
 * Faza 30.6 — Use Case za shranjevanje telesnih meritev.
 *
 * Domenska validacija:
 *   - shoulderCm in waistCm morata biti > 0 (brez njiju Golden Ratio ni smiseln)
 *   - hipCm je opcijsko (zadostuje 0.0)
 *
 * Faza 30.9 — dateId generira ta UseCase (domenski sloj), ne repozitorij (podatkovni sloj).
 *   LocalDate je čisti Java API brez Android odvisnosti → sprejemljivo v domenski logiki.
 *   Repozitorij ostaja popolnoma determinističen in testabilen.
 *
 * Klic chain: VM.saveBodyMeasurements() → UseCase (validacija + dateId) → Repository → Firestore
 */
class SaveBodyMeasurementsUseCase(
    private val bodyMeasurementsRepository: BodyMeasurementsRepository
) {
    suspend operator fun invoke(
        shoulderCm: Double,
        waistCm: Double,
        hipCm: Double = 0.0,
        heightCm: Double = 0.0
    ): Result<Unit> {
        // Domenska validacija — ne shranjujemo praznih vnosov
        if (shoulderCm <= 0.0) {
            return Result.failure(IllegalArgumentException("Obseg ramen mora biti večji od 0."))
        }
        if (waistCm <= 0.0) {
            return Result.failure(IllegalArgumentException("Obseg pasu mora biti večji od 0."))
        }

        // Faza 30.9 — dateId se generira v domeni, ne v podatkovnem sloju
        // Format "YYYY-MM-DD" zagotavlja upsert semantiko (en vnos na dan v Firestore)
        val dateId = LocalDate.now().toString()

        return bodyMeasurementsRepository.saveMeasurements(
            dateId     = dateId,
            shoulderCm = shoulderCm,
            waistCm    = waistCm,
            hipCm      = hipCm,
            heightCm   = heightCm
        )
    }
}


