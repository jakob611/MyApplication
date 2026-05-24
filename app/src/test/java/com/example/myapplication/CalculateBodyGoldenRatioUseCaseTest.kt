package com.example.myapplication

import com.example.myapplication.domain.model.BodyField
import com.example.myapplication.domain.model.BodyRatioStatus
import com.example.myapplication.domain.usecase.CalculateBodyGoldenRatioUseCase
import com.example.myapplication.domain.usecase.ValidationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Faza 31.6 — Unit testi za [CalculateBodyGoldenRatioUseCase].
 *
 * Pokriva tri scenarije:
 *   1) Prazni vnosi (0.0) → UseCase vrne Success brez validacijskih napak
 *   2) Napačne biološke mere (< 30 cm) → vrne Invalid z natančnim setom polj
 *   3) Pravilne mere → izračun vrne pričakovano razmerje in status
 */
class CalculateBodyGoldenRatioUseCaseTest {

    private lateinit var useCase: CalculateBodyGoldenRatioUseCase

    @Before
    fun setup() {
        useCase = CalculateBodyGoldenRatioUseCase()
    }

    /**
     * Test 1: Prazni vnosi (0.0) preskočijo validacijo in vrnejo Success.
     *
     * Razlog: ViewModel kliče UseCase samo ko shoulderCm > 0 && waistCm > 0,
     * toda UseCase sam mora biti robusten: 0.0 = "ni vnosa" → ne sme vrniti Invalid.
     */
    @Test
    fun test_prazni_vnosi_vrnejo_success_brez_napak_in_brez_podatkov() {
        // Ko oba obvezna vnosa sta 0.0, biološka validacija se preskoči
        val result = useCase.invoke(shoulderCm = 0.0, waistCm = 0.0)

        // UseCase mora vrniti Success (ne Invalid) — 0.0 = "polje prazno", ne neveljavna mera
        assertTrue(
            "Prazni vnosi (0.0) morajo vrniti ValidationResult.Success, ne Invalid",
            result is ValidationResult.Success
        )
        // Zagotovimo, da ni padla nobena izjema in da invalidFields niso vrnjeni
        assertTrue(result !is ValidationResult.Invalid)
    }

    /**
     * Test 2: Napačne biološke mere (zunaj 30–250 cm) morajo vrniti Invalid
     * z natančnim setom polj, ki so neveljavna.
     */
    @Test
    fun test_napacne_bioloske_mere_vrnejo_invalid_set() {
        // 5.0 cm je pod minimumom (30 cm) → oba SHOULDER in WAIST sta neveljavna
        val result = useCase.invoke(shoulderCm = 5.0, waistCm = 5.0)

        assertTrue(
            "Mere pod biološkim minimumom (30 cm) morajo vrniti ValidationResult.Invalid",
            result is ValidationResult.Invalid
        )

        val invalid = result as ValidationResult.Invalid
        assertTrue(
            "SHOULDER mora biti v setu neveljavnih polj",
            BodyField.SHOULDER in invalid.invalidFields
        )
        assertTrue(
            "WAIST mora biti v setu neveljavnih polj",
            BodyField.WAIST in invalid.invalidFields
        )
        assertEquals("Točno 2 neveljavni polji", 2, invalid.invalidFields.size)
    }

    /**
     * Test 3: Pravilne mere → UseCase izračuna pričakovano razmerje in status.
     *
     * Ramena 120 cm / pas 74 cm ≈ 1.621 ≈ φ (1.618) → odmik ~0.2% → GOLDEN_RATIO status.
     */
    @Test
    fun test_pravilne_mere_izracunajo_pravilen_zlati_rez() {
        // φ ≈ 1.618: ramena/pas ≈ 1.621 → odmik < 1% → GOLDEN_RATIO
        val result = useCase.invoke(
            shoulderCm = 120.0,
            waistCm    = 74.0,
            hipCm      = 100.0,
            heightCm   = 180.0,
            isMale     = true
        )

        assertTrue(
            "Pravilne mere morajo vrniti ValidationResult.Success",
            result is ValidationResult.Success
        )

        val data = (result as ValidationResult.Success).data

        // Razmerje ramen/pas mora biti blizu φ (1.618)
        assertEquals(
            "shoulderToWaistRatio mora biti ≈ 1.621",
            1.621,
            data.shoulderToWaistRatio,
            0.01  // toleranca ±0.01
        )

        // Odmik od φ < 3% → status GOLDEN_RATIO
        assertEquals(
            "Status mora biti GOLDEN_RATIO (odmik od φ < 3%)",
            BodyRatioStatus.GOLDEN_RATIO,
            data.status
        )

        // Razmerje pas/višina mora biti izračunano (ne 0.0)
        assertTrue(
            "waistToHeightRatio mora biti > 0 ko je višina podana",
            data.waistToHeightRatio > 0.0
        )

        // Skupni rezultat mora biti v veljavnem območju 0..1
        assertTrue(
            "overallScore mora biti v območju [0.0, 1.0]",
            data.overallScore in 0.0..1.0
        )
    }
}

