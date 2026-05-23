package com.example.myapplication.domain.model

/**
 * Faza 30.7 — Tipsko-varni enum za status telesnih proporcov.
 *
 * Zamenjuje hardkodirane stringe z emojiji v domain sloju ("💛 Golden Ratio" itd.).
 * Tekst in emojiji za prikaz so IZKLJUČNO v presentation sloju (UI mapper).
 *
 * KMP-ready: brez Android odvisnosti.
 */
enum class BodyRatioStatus {
    /** Odmik od φ ≤ 3 % — skoraj popolno zlatorezo razmerje */
    GOLDEN_RATIO,

    /** Odmik od φ ≤ 8 % — odlični proporci */
    EXCELLENT,

    /** Odmik od φ ≤ 15 % — dobri proporci */
    GOOD,

    /** Odmik od φ ≤ 25 % — povprečni proporci */
    AVERAGE,

    /** Odmik od φ > 25 % — proporci zahtevajo delo */
    NEEDS_WORK
}

