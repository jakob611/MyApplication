package com.example.myapplication.domain.model

/**
 * Faza 30.1 — Domain model za rezultate telesnih proporcov.
 * Faza 30.7 — status: String → BodyRatioStatus enum (brez emojiev v domain sloju).
 * KMP-ready: brez Android/Firebase odvisnosti.
 */
data class BodyGoldenRatioResult(
    /** Razmerje obseg ramen / obseg pasu */
    val shoulderToWaistRatio: Double,
    /** Razmerje obseg pasu / višina */
    val waistToHeightRatio: Double,
    /** Razmerje obseg ramen / obseg bokov */
    val shoulderToHipRatio: Double,
    /** Skupni rezultat 0..1 (1.0 = popolni zlatorezni proporci) */
    val overallScore: Double,
    /** Faza 30.7: tipsko-varni enum — tekst/emojiji so v presentation sloju */
    val status: BodyRatioStatus,
    /** Absolutni odmik od φ deljen z φ (nižje = bliže idealu) */
    val deviationFromPhi: Double
)