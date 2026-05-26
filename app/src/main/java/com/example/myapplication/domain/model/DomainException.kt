package com.example.myapplication.domain.model

/**
 * Domensko-nevtralne izjeme — Clean Architecture meja med domenskim in podatkovnim slojem.
 *
 * Faza 36 — Eliminacija Firebase SDK odvisnosti iz presentation sloja.
 *
 * PROBLEM (pred Fazo 36):
 *   GetBodyMetricsUseCase (domain) je re-throwal FirebaseFirestoreException.
 *   BodyModuleHomeViewModel (presentation) je lovil FirebaseFirestoreException.
 *   → Presentation sloj je bil neposredno sklopljen s Firebase SDK (kršitev Clean Architecture).
 *
 * REŠITEV:
 *   GetBodyMetricsUseCase (domain) prevede platform-specifično izjemo v DomainException.
 *   BodyModuleHomeViewModel (presentation) lovi samo DomainException — brez Firebase uvozov.
 *   Firebase SDK ostane IZKLJUČNO v data sloju.
 *
 * Klic chain (novi):
 *   data: UserWorkoutStatsRepository.callbackFlow → close(FirebaseFirestoreException)
 *   domain: GetBodyMetricsUseCase catch(FirebaseFirestoreException) → throw DomainException
 *   presentation: BodyModuleHomeViewModel catch(DomainException) → AuthExpired / errorMessage
 *
 * KMP-ready: brez Android ali Firebase odvisnosti — bo deloval na iOS in Desktop.
 */
sealed class DomainException : RuntimeException() {

    /**
     * Auth token je potekel ali Firestore pravila zavrnijo dostop (PERMISSION_DENIED).
     *
     * Vzroki:
     *   - Firebase ID token je potekel (~1h inaktivnost) in se ni avtomatično osvežil
     *   - Firestore Security Rules zavrnejo operacijo (napačno konfigurirana pravila)
     *
     * UI reakcija: prikaži opozorilo "Seja je potekla" + navigiraj nazaj na login.
     */
    data object AuthenticationExpired : DomainException()

    /**
     * Omrežna napaka ali splošna Firestore napaka (brez auth vzroka).
     *
     * Vzroki:
     *   - Ni internetne povezave
     *   - Firestore SDK napaka (UNAVAILABLE, DEADLINE_EXCEEDED…)
     *   - Presežen kvoto zahtevkov
     *
     * UI reakcija: prikaži Snackbar z opisno napako.
     *
     * @param message Opisno sporočilo za Snackbar (iz original exception).
     */
    data class NetworkFailure(override val message: String) : DomainException()
}

