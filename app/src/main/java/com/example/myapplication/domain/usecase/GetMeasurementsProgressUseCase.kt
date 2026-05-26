package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.BodyMeasurementEntry
import com.example.myapplication.domain.model.DomainException
import com.example.myapplication.domain.repository.BodyMeasurementsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * GetMeasurementsProgressUseCase — domenski use case za analitiko napredka telesnih meritev.
 *
 * Faza 38 — Analytics & Progress Module:
 * Zbiera celotno zgodovino meritev iz [BodyMeasurementsRepository] in jo vrne kot
 * reaktivni tok, urejen kronološko (ASC po timestamp). Grafične komponente v UI
 * prejmejo podatke v pravilnem časovnem zaporedju brez dodatnega urejanja.
 *
 * Arhitekturna čistost:
 *   - 100% čist Kotlin — nobenih Android ali Firebase importov.
 *   - Vse platformske napake (Firestore, mreža) so že prevedene v [DomainException]
 *     v data sloju (FirestoreBodyMeasurementsRepository) — ta use case je od tega neodvisen.
 *   - Vrne [Result] omotač, kar Presentation sloju omogoča čisto ločevanje
 *     uspešnega stanja od napake brez try/catch v ViewModelu.
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

