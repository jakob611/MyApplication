package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.BodyGoldenRatioResult
import com.example.myapplication.domain.model.BodyRatioStatus
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Faza 30.1 — Clean Architecture: Poslovna logika za telesni Zlati Rez.
 * Faza 30.7 — Odstranjeni emoji-ji in hardkodirani nizi; status vrača BodyRatioStatus enum.
 *             Dodane realne biološke meje (30–250 cm) za preprečevanje popačenih rezultatov.
 *
 * KMP-ready: brez Android odvisnosti.
 */
class CalculateBodyGoldenRatioUseCase {

    private val phi = (1.0 + sqrt(5.0)) / 2.0  // ≈ 1.618

    companion object {
        /** Faza 30.7 — Biološke meje za telesne obsege */
        const val MIN_CIRCUMFERENCE_CM = 30.0
        const val MAX_CIRCUMFERENCE_CM = 250.0
    }

    operator fun invoke(
        shoulderCm: Double,
        waistCm: Double,
        hipCm: Double = 0.0,
        heightCm: Double = 0.0,
        isMale: Boolean = true
    ): BodyGoldenRatioResult {

        // Faza 30.7 — Realne biološke meje (ne zgolj > 0)
        require(shoulderCm in MIN_CIRCUMFERENCE_CM..MAX_CIRCUMFERENCE_CM) {
            "Obseg ramen mora biti med $MIN_CIRCUMFERENCE_CM in $MAX_CIRCUMFERENCE_CM cm (dobil: $shoulderCm)"
        }
        require(waistCm in MIN_CIRCUMFERENCE_CM..MAX_CIRCUMFERENCE_CM) {
            "Obseg pasu mora biti med $MIN_CIRCUMFERENCE_CM in $MAX_CIRCUMFERENCE_CM cm (dobil: $waistCm)"
        }
        require(hipCm == 0.0 || hipCm in MIN_CIRCUMFERENCE_CM..MAX_CIRCUMFERENCE_CM) {
            "Obseg bokov mora biti 0 (ni vnosu) ali med $MIN_CIRCUMFERENCE_CM in $MAX_CIRCUMFERENCE_CM cm (dobil: $hipCm)"
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

        // ── 5. Status — Faza 30.7: enum, brez emoji/stringov v domain sloju ─
        val status: BodyRatioStatus = when {
            deviationFromPhi <= 0.03 -> BodyRatioStatus.GOLDEN_RATIO
            deviationFromPhi <= 0.08 -> BodyRatioStatus.EXCELLENT
            deviationFromPhi <= 0.15 -> BodyRatioStatus.GOOD
            deviationFromPhi <= 0.25 -> BodyRatioStatus.AVERAGE
            else                     -> BodyRatioStatus.NEEDS_WORK
        }

        return BodyGoldenRatioResult(
            shoulderToWaistRatio = shoulderToWaistRatio,
            waistToHeightRatio   = if (waistToHeightRatio < 0) 0.0 else waistToHeightRatio,
            shoulderToHipRatio   = if (shoulderToHipRatio < 0) 0.0 else shoulderToHipRatio,
            overallScore         = overallScore,
            status               = status,
            deviationFromPhi     = deviationFromPhi
        )
    }
}
