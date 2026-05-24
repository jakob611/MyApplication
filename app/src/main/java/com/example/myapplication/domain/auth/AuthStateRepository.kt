package com.example.myapplication.domain.auth

import kotlinx.coroutines.flow.Flow

/**
 * Faza 30.2 — Domenski vmesnik za stanje avtentikacije.
 *
 * ViewModel NE ve za Firebase — prejme le reaktivni tok e-poštnega naslova.
 * Implementacija je v data sloju (FirebaseAuthStateRepository).
 */
interface AuthStateRepository {
    /** Reaktivni tok e-poštnega naslova prijavljenega uporabnika. null = odjavljen. */
    fun observeCurrentUserEmail(): Flow<String?>
}
