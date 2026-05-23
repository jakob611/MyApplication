package com.example.myapplication.domain.model

/**
 * Faza 31.3 — Identifikator posameznega vnosnega polja za telesne meritve.
 *
 * Uporablja se v [com.example.myapplication.domain.usecase.ValidationResult.Invalid]
 * za natančno označitev, katero polje je zunaj bioloških meja (30–250 cm).
 *
 * UI (BodyGoldenRatioSection) bere ta set in nastavi isError = true SAMO na
 * poljih, ki so dejansko neveljavna — ne na vseh poljih hkrati.
 */
enum class BodyField {
    /** Obseg ramen — mora biti v 30–250 cm */
    SHOULDER,
    /** Obseg pasu — mora biti v 30–250 cm */
    WAIST,
    /** Obseg bokov — mora biti 0 (ni vnosu) ali v 30–250 cm */
    HIP,
    /** Višina (iz profila) — mora biti v 30–250 cm če je podana */
    HEIGHT
}

