package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.BodyField
import com.example.myapplication.domain.model.BodyGoldenRatioResult
import com.example.myapplication.domain.model.BodyRatioStatus
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Faza 31.3 — Rezultat validacije in izračuna telesnega Zlatega Reza.
 *
 * Nadomešča metanje [IllegalArgumentException] za kontrolo toka.
 * Klic chain: UseCase.invoke() → ValidationResult → ViewModel mapira v GoldenRatioUiState.
 */
sealed interface ValidationResult {
    /**
     * Vse vrednosti so znotraj bioloških meja — izračun uspel.
     * @param data  Rezultat izračuna, pripravljen za prikaz v UI.
     */
    data class Success(val data: BodyGoldenRatioResult) : ValidationResult

    /**
     * Ena ali več vrednosti je zunaj bioloških meja (30–250 cm).
     * @param invalidFields  Set polj, ki so neveljavna — UI obarva SAMO ta polja rdeče.
     */
    data class Invalid(val invalidFields: Set<BodyField>) : ValidationResult
}

/**
 * Faza 30.1 — Clean Architecture: Poslovna logika za telesni Zlati Rez.
 * Faza 30.7 — Odstranjeni emoji-ji in hardkodirani nizi; status vrača BodyRatioStatus enum.
 *             Dodane realne biološke meje (30–250 cm).
 * Faza 31.3 — Zamenjava require() z ValidationResult.Invalid(invalidFields):
 *             • Brez izjem za kontrolo toka
 *             • Per-field natančnost (UI vidi točno katera polja so napačna)
 *
 * KMP-ready: brez Android odvisnosti.
 */
class CalculateBodyGoldenRatioUseCase {

    private val phi = (1.0 + sqrt(5.0)) / 2.0  // ≈ 1.618

    companion object {
        /** Biološke meje za telesne obsege */
        const val MIN_CIRCUMFERENCE_CM = 30.0
        const val MAX_CIRCUMFERENCE_CM = 250.0
    }

    operator fun invoke(
        shoulderCm: Double,
        waistCm: Double,
        hipCm: Double = 0.0,
        heightCm: Double = 0.0,
        isMale: Boolean = true
    ): ValidationResult {

        // Faza 31.4 — Simetrična logika za vsa polja:
        //   0.0 = polje ni izpolnjeno → preskoči validacijo (enako kot HIP in HEIGHT)
        //   Faza 31.3: zgradi set neveljavnih polj
        val invalidFields = mutableSetOf<BodyField>()

        if (shoulderCm != 0.0 && shoulderCm !in MIN_CIRCUMFERENCE_CM..MAX_CIRCUMFERENCE_CM) {
            invalidFields.add(BodyField.SHOULDER)
        }
        if (waistCm != 0.0 && waistCm !in MIN_CIRCUMFERENCE_CM..MAX_CIRCUMFERENCE_CM) {
            invalidFields.add(BodyField.WAIST)
        }
        // hipCm = 0.0 pomeni ni vnosu → preskoči validacijo
        if (hipCm != 0.0 && hipCm !in MIN_CIRCUMFERENCE_CM..MAX_CIRCUMFERENCE_CM) {
            invalidFields.add(BodyField.HIP)
        }
        // heightCm = 0.0 pomeni ni vnosu → preskoči validacijo
        if (heightCm != 0.0 && heightCm !in MIN_CIRCUMFERENCE_CM..MAX_CIRCUMFERENCE_CM) {
            invalidFields.add(BodyField.HEIGHT)
        }

        // Katerokoli neveljavno polje → vrni Invalid z natančnim setom
        if (invalidFields.isNotEmpty()) {
            return ValidationResult.Invalid(invalidFields)
        }

        // ── 1. Razmerje ramen/pas (Adonis Index) ────────────────────────────
        val shoulderToWaistRatio = shoulderCm / waistCm
        val idealSWR = if (isMale) phi else 1.45
        val deviationFromPhi = abs(shoulderToWaistRatio - idealSWR) / idealSWR

        // ── 2. Razmerje pas/višina (WHtR) ────────────────────────────────────
        val waistToHeightRatio = if (heightCm > 0) waistCm / heightCm else -1.0

        // ── 3. Razmerje ramen/boki ───────────────────────────────────────────
        val shoulderToHipRatio = if (hipCm > 0) shoulderCm / hipCm else -1.0

        // ── 4. Skupni rezultat (0..1) ────────────────────────────────────────
        val swrScore = (1.0 - deviationFromPhi.coerceIn(0.0, 1.0)) * 0.60
        val whtrScore = when {
            waistToHeightRatio < 0     -> 0.25 * 0.5
            waistToHeightRatio <= 0.50 -> 0.25
            waistToHeightRatio <= 0.60 -> 0.25 * (1.0 - (waistToHeightRatio - 0.50) * 5.0)
            else                       -> 0.0
        }
        val hipScore = when {
            shoulderToHipRatio < 0                              -> 0.15 * 0.5
            isMale  && shoulderToHipRatio in 1.05..1.20         -> 0.15
            !isMale && shoulderToHipRatio in 0.90..1.10         -> 0.15
            else                                                -> 0.15 * 0.5
        }
        val overallScore = (swrScore + whtrScore + hipScore).coerceIn(0.0, 1.0)

        // ── 5. Status — enum, brez emoji/stringov v domain sloju ─────────────
        val status: BodyRatioStatus = when {
            deviationFromPhi <= 0.03 -> BodyRatioStatus.GOLDEN_RATIO
            deviationFromPhi <= 0.08 -> BodyRatioStatus.EXCELLENT
            deviationFromPhi <= 0.15 -> BodyRatioStatus.GOOD
            deviationFromPhi <= 0.25 -> BodyRatioStatus.AVERAGE
            else                     -> BodyRatioStatus.NEEDS_WORK
        }

        return ValidationResult.Success(
            BodyGoldenRatioResult(
                shoulderToWaistRatio = shoulderToWaistRatio,
                waistToHeightRatio   = if (waistToHeightRatio < 0) 0.0 else waistToHeightRatio,
                shoulderToHipRatio   = if (shoulderToHipRatio < 0) 0.0 else shoulderToHipRatio,
                overallScore         = overallScore,
                status               = status,
                deviationFromPhi     = deviationFromPhi
            )
        )
    }
}


