package com.example.myapplication.data

/**
 * Faza 40 — Backwards-compat typealias.
 * UserProfile je premaknjen v `domain/model/UserProfile.kt`.
 * Ta typealias zagotavlja, da obstoječi importi v data sloju ne zahtevajo takojšnje posodobitve.
 * NOVA KODA mora uvažati iz `com.example.myapplication.domain.model.UserProfile`.
 */
@Deprecated("Premaknjeno v domain.model.UserProfile — posodobi import")
typealias UserProfile = com.example.myapplication.domain.model.UserProfile
