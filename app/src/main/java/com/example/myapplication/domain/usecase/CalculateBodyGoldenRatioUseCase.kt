package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.BodyGoldenRatioResult
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Faza 30.1 — Clean Architecture: Poslovna logika za telesni Zlati Rez.
 *
 * Preseljeno iz UI/ViewModel → Domain sloj. KMP-ready: brez Android odvisnosti.
 *
 * MATEMATIČNI MODEL:
 *   φ (phi) ≈ 1.618 — Zlatorezo razmerje
 *   Idealno razmerje ramen/pas (Adonis Index):
 *     - Moški: ≈ 1.618 φ (športna postava)
 *     - Ženska: ≈ 1.45 (peščena ura)
 *   Razmerje pas/višina (WHtR): zdravstveno tveganje < 0.50
 *
 * @param shoulderCm   Obseg ramen v cm (merjeno od vrha ene roke prek hrbta do vrha druge)
 * @param waistCm      Obseg pasu v cm (najožji del)
 * @param hipCm        Obseg bokov v cm (0.0 = ni podan)
 * @param heightCm     Višina v cm (0.0 = ni podan; pas/višina razmerje se preskoči)
 * @param isMale       true = moški ideali (φ ≈ 1.618), false = ženski ideali (≈ 1.45)
 */
class CalculateBodyGoldenRatioUseCase {

    private val phi = (1.0 + sqrt(5.0)) / 2.0  // ≈ 1.618

    operator fun invoke(
        shoulderCm: Double,
        waistCm: Double,
        hipCm: Double = 0.0,
        heightCm: Double = 0.0,
        isMale: Boolean = true
    ): BodyGoldenRatioResult {

        require(shoulderCm > 0 && waistCm > 0) {
            "shoulderCm in waistCm morata biti večja od 0"
        }

        // ── 1. Razmerje ramen/pas (Adonis Index) ────────────────────────────
        val shoulderToWaistRatio = shoulderCm / waistCm
        val idealSWR = if (isMale) phi else 1.45
        val deviationFromPhi = abs(shoulderToWaistRatio - idealSWR) / idealSWR

        // ── 2. Razmerje pas/višina (WHtR) ────────────────────────────────────
        val waistToHeightRatio = if (heightCm > 0) waistCm / heightCm else -1.0

        // ── 3. Razmerje ramen/boki ───────────────────────────────────────────
        val shoulderToHipRatio  = if (hipCm > 0) shoulderCm / hipCm else -1.0

        // ── 4. Skupni rezultat (0..1) ────────────────────────────────────────
        // Osnova: odmik od idealnega SWR (tehtanje 60%)
        val swrScore = (1.0 - deviationFromPhi.coerceIn(0.0, 1.0)) * 0.60

        // Bonus: WHtR < 0.50 (zdravstveno optimalno) — tehtanje 25%
        val whtrScore = when {
            waistToHeightRatio < 0    -> 0.25 * 0.5  // ni podatka → nevtralno
            waistToHeightRatio <= 0.50 -> 0.25
            waistToHeightRatio <= 0.60 -> 0.25 * (1.0 - (waistToHeightRatio - 0.50) * 5.0)
            else                       -> 0.0
        }

        // Bonus: boki/ramena balance — tehtanje 15%
        val hipScore = when {
            shoulderToHipRatio < 0   -> 0.15 * 0.5
            isMale && shoulderToHipRatio in 1.05..1.20  -> 0.15
            !isMale && shoulderToHipRatio in 0.90..1.10 -> 0.15
            else -> 0.15 * 0.5
        }

        val overallScore = (swrScore + whtrScore + hipScore).coerceIn(0.0, 1.0)

        // ── 5. Status ────────────────────────────────────────────────────────
        val status = when {
            deviationFromPhi <= 0.03  -> "💛 Golden Ratio"
            deviationFromPhi <= 0.08  -> "✅ Excellent"
            deviationFromPhi <= 0.15  -> "👌 Good"
            deviationFromPhi <= 0.25  -> "📈 Average"
            else                      -> "⚠️ Needs Work"
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

