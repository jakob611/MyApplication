package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.repository.BodyMeasurementsRepository

/**
 * Faza 30.6 — Use Case za shranjevanje telesnih meritev.
 *
 * Domenska validacija:
 *   - shoulderCm in waistCm morata biti > 0 (brez njiju Golden Ratio ni smiseln)
 *   - hipCm je opcijsko (zadostuje 0.0)
 *
 * Po uspešni validaciji kliče BodyMeasurementsRepository.saveMeasurements()
 * ki izvede atomarni batch write (profil + subcollection history).
 *
 * Klic chain: VM.saveBodyMeasurements() → UseCase → Repository → Firestore
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

        return bodyMeasurementsRepository.saveMeasurements(
            shoulderCm = shoulderCm,
            waistCm    = waistCm,
            hipCm      = hipCm,
            heightCm   = heightCm
        )
    }
}

