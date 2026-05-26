package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.BodyMeasurementEntry
import com.example.myapplication.domain.model.DomainException
import com.example.myapplication.domain.repository.BodyMeasurementsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * GetMeasurementsProgressUseCase — domenski use case za analitiko napredka telesnih meritev.
 *
 * Faza 38 — Analytics & Progress Module:
 * Zbiera celotno zgodovino meritev iz [BodyMeasurementsRepository] in jo vrne kot
 * reaktivni tok, urejen kronološko (ASC po timestamp). Grafične komponente v UI
 * prejmejo podatke v pravilnem časovnem zaporedju brez dodatnega urejanja.
 *
 * Faza 38b — Performance audit:
 * `.flowOn(Dispatchers.Default)` zagotavlja, da se CPU-intenzivno sortiranje
 * izvaja IZKLJUČNO na ozadnjem nitnem bazenu, ne glede na to, s katerega
 * dispatcherja ViewModel zbira tok. Preprečuje UI jank pri velikih podatkovnih setih
 * (npr. 365+ dnevnih meritev).
 *
 * Arhitekturna čistost:
 *   - 100% čist Kotlin — nobenih Android ali UI importov.
 *   - [Dispatchers.Default] je del `kotlinx.coroutines` — ni Android-specifičen.
 *   - Vse platformske napake so že prevedene v [DomainException] v data sloju.
 *   - Vrne [Result] omotač za čisto ločevanje uspešnega stanja od napake v ViewModelu.
 */
class GetMeasurementsProgressUseCase(
    private val bodyMeasurementsRepository: BodyMeasurementsRepository
) {
    /**
     * Vrne reaktivni [Flow] z zgodovino meritev, urejeno kronološko (ASC po timestamp).
     *
     * Vsaka emisija je [Result.success] z urejenim seznamom, ob napaki toka pa
     * [Result.failure] z [DomainException.NetworkFailure] — ViewModel nikoli ne vidi
     * surovih izjem.
     */
    operator fun invoke(): Flow<Result<List<BodyMeasurementEntry>>> =
        bodyMeasurementsRepository
            .observeMeasurementsHistory()
            .map { entries ->
                // Kronološko urejanje (ASC timestamp) — grafični komponenti ni treba
                // skrbeti za vrstni red podatkovnih točk.
                val sorted = entries.sortedBy { it.timestamp }
                Result.success(sorted)
            }
            // Faza 38b — Performance: .flowOn() premakne VSE operacije nad njim (.map + izvor)
            // na Dispatchers.Default (CPU thread pool). ViewModel zbira tok na Main, sortiranje
            // pa se vedno izvede v ozadju — zero UI jank za 1000+ vnosov.
            .flowOn(Dispatchers.Default)
            .catch { throwable ->
                // Prevajanje napak v domenski tip — Presentation sloj ne vidi surovih izjem.
                val domainException = when (throwable) {
                    is DomainException -> throwable
                    else -> DomainException.NetworkFailure(
                        throwable.message ?: "Failed to load measurements history"
                    )
                }
                emit(Result.failure(domainException))
            }
}

