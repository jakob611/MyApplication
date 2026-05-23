package com.example.myapplication.domain.model

/**
 * Faza 30.1 — Domain model za rezultate telesnih proporcov.
 * KMP-ready: brez Android/Firebase odvisnosti.
 *
 * Zlati rez (φ ≈ 1.618) za telo:
 *   - Idealno razmerje ramen/pas ≈ 1.618 (moški) / 1.45 (ženske)
 *   - Razmerje pas/višina < 0.50 = zdravstveno optimalno
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
    /** Besedilni status: "💛 Golden Ratio", "✅ Good", "👌 Average", "⚠️ Needs Work" */
    val status: String,
    /** Absolutni odmik od φ deljenokotnica φ (nižje = bliže idealu) */
    val deviationFromPhi: Double
)

