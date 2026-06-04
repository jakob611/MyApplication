package com.example.myapplication.domain.repository

/**
 * Faza 58 — Domenski vmesnik za kardio/tek seje.
 *
 * Ločuje ViewModel/UI od konkretne Firebase implementacije.
 * Implementacija: [com.example.myapplication.data.repository.RunRepositoryImpl]
 *
 * S tem vmesnikom odpravimo K-3 kršitev: UI plast ne sme pisati v Firestore direktno.
 * RunTrackerScreen bo klical ViewModel → ViewModel bo klical ta vmesnik → implementacija
 * poskrbi za vse I/O in Firestore klice.
 */
interface RunRepository {

    /**
     * Shrani zaključeno kardio sejo v Firestore `runSessions` podkolekcijo.
     *
     * Implementacija mora zagotoviti:
     *   - Izključna uporaba [com.example.myapplication.data.store.FirestoreHelper.getCurrentUserDocRef()]
     *   - Retry logika prek FirestoreHelper.withRetry()
     *   - Thread shifting na Dispatchers.IO
     *
     * @param sessionId   Enolični ID seje — mora biti ENAK kot ID v Room checkpointu
     *                    (preprečuje K-1 Session ID Mismatch napako iz revizije)
     * @param sessionData Firestore-kompatibilen Map z vsemi polji seje
     * @return [Result.success] ob uspešnem vpisu, [Result.failure] ob napaki
     */
    suspend fun saveRunSession(sessionId: String, sessionData: Map<String, Any>): Result<Unit>

    /**
     * Atomarno doda porabljene kalorije teka k obstoječim `burnedCalories` za ta dan.
     *
     * Interno kliče DailyLogRepository.updateDailyLog() prek standardne Firestore transakcije,
     * kar preprečuje race conditions med vzporednimi pisci (Widget, NutritionScreen, RunTracker).
     *
     * @param date         ISO-8601 datum (npr. "2026-06-03")
     * @param caloriesKcal Kalorije za dodati — mora biti pozitivno celo število
     * @return [Result.success] ob uspešni posodobitvi, [Result.failure] ob napaki
     */
    suspend fun addBurnedCalories(date: String, caloriesKcal: Int): Result<Unit>

    /**
     * Shrani sejo v javno kolekcijo `publicActivities` (opcijsko, glede na nastavitev profila).
     *
     * Pokliče se samo če ima uporabnik v profilu `share_activities = true`.
     *
     * @param sessionId Enolični ID seje
     * @param publicData Map z javnimi podatki (brez osebnih informacij)
     * @return [Result.success] ali [Result.failure]
     */
    suspend fun savePublicActivity(sessionId: String, publicData: Map<String, Any>): Result<Unit>
}

