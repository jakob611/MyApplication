package com.example.myapplication.domain.model

/**
 * Faza 56 — Centralizirana konfiguracijska konstanta za privzete vrednosti profila.
 *
 * Nadomešča inline magic numbers v poslovni logiki.
 * KMP-ready: brez Android/Firebase odvisnosti.
 */
object ProfileConstants {

    /**
     * Privzeta telesna teža (kg) — uporablja se kot varni fallback ko:
     *  - algorithmData.caloriesPerKg še ni naložen (plan se nalaga)
     *  - algorithmData.caloriesPerKg ali plan.calories sta 0 ali null
     *
     * Vrednost 75.0 kg temelji na WHO mediani telesne teže odraslih.
     * Vpliva IZKLJUČNO na izračun ciljnega vnosa vode; zamenja se z dejansko
     * vrednostjo takoj ko plan emitira veljavne algorithmData.
     */
    const val DEFAULT_WEIGHT_KG = 75.0
}

