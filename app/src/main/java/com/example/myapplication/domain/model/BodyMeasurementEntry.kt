package com.example.myapplication.domain.model

/**
 * Faza 30.6 — Podatkovni model za en zgodovinski vnos telesnih meritev.
 *
 * Shranjeno v: users/{userId}/measurements_history/{timestamp}
 *
 * @param shoulderCm Obseg ramen v centimetrih
 * @param waistCm    Obseg pasu v centimetrih
 * @param hipCm      Obseg bokov v centimetrih (0.0 = ni vnosa)
 * @param heightCm   Višina v centimetrih (iz profila ali ročnega vnosa)
 * @param timestamp  Unix epoch ms — ID dokumenta in časovni žig hkrati
 */
data class BodyMeasurementEntry(
    val shoulderCm: Double = 0.0,
    val waistCm: Double    = 0.0,
    val hipCm: Double      = 0.0,
    val heightCm: Double   = 0.0,
    val timestamp: Long    = 0L
) {
    /** Izračunano razmerje ramen/pas — za graf napredka */
    val shoulderToWaistRatio: Double
        get() = if (waistCm > 0.0) shoulderCm / waistCm else 0.0
}

