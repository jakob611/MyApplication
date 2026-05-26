package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.BodyMetrics
import com.example.myapplication.domain.model.DomainException
import com.example.myapplication.domain.repository.WorkoutStatsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * GetBodyMetricsUseCase — reaktivni domenski use case za branje fitness metrik.
 *
 * Faza 21: todayStatus je zdaj UserDayStatus (tipsko-varni enum).
 * Faza 32.9 — BUG-05 Fix: Prenovljeno iz one-shot flow{} v channelFlow, ki zbira
 * iz reaktivne Firestore poslušalnice [WorkoutStatsRepository.observeWorkoutStats].
 * Ob vsaki spremembi Firestore dokumenta UI samodejno prejme svežo vrednost —
 * brez ponovnega klica LoadMetrics.
 *
 * BUG-01 Fix: Null snapshot in Firestore napaka sta EKSPLICITNO propagirana prek
 * [BodyMetrics.errorMessage] — nobenih tihih padcev na hardkodirane privzete vrednosti.
 *
 * Faza 37 — Clean Architecture (popolna izčistitev):
 * Firebase SDK je bil premaknen IZKLJUČNO v data sloj (UserWorkoutStatsRepository).
 * Ta use case je zdaj 100% čist Kotlin — nobenih platform-odvisnih importov.
 *
 * Faza 38 — Poenotena Result API pogodba:
 * Vrne [Flow<Result<BodyMetrics>>] namesto surovega toka z meta-polji (isLoading itd.).
 * Predvidljive domenske napake ([DomainException.AuthenticationExpired],
 * [DomainException.NetworkFailure]) se emitirajo kot [Result.failure], nikoli mete.
 * Presentation sloj prejme čiste, tipsko-varne emisije brez try/catch na stream nivoju.
 */
class GetBodyMetricsUseCase(
    private val statsRepo: WorkoutStatsRepository
) {
    fun invoke(email: String): Flow<Result<BodyMetrics>> = channelFlow {
        // Takoj prikaži nalaganje — uspešna emisija z isLoading=true
        send(Result.success(BodyMetrics(isLoading = true)))

        try {
            // Faza 34 — MED-04 Fix: getDailyCalories() se kešira ENKRAT pred collect zanko.
            val cachedDailyKcal = statsRepo.getDailyCalories()

            // Zbieramo reaktivni tok iz Firestore poslušalnice
            statsRepo.observeWorkoutStats(email).collect { stats ->
                if (stats != null) {
                    send(Result.success(BodyMetrics(
                        streakDays             = stats.streakDays,
                        streakFreezes          = stats.streakFreezes,
                        weeklyDone             = stats.weeklyDone,
                        weeklyTarget           = stats.weeklyTarget,
                        planDay                = stats.planDay,
                        totalWorkoutsCompleted = stats.totalWorkoutsCompleted,
                        isWorkoutDoneToday     = stats.todayStatus.isDoneToday,
                        dailyKcal              = stats.dailyKcal.takeIf { it > 0 }
                                                 ?: cachedDailyKcal,
                        todayIsRest            = stats.todayIsRest,
                        todayStatus            = stats.todayStatus,
                        isLoading              = false
                    )))
                } else {
                    // Faza 32.9 — BUG-01 Fix: dokument ne obstaja ali ni auth uporabnika.
                    send(Result.success(BodyMetrics(
                        errorMessage = "Failed to sync with server — check connection",
                        isLoading    = false
                    )))
                }
            }
        } catch (e: CancellationException) {
            // CancellationException nikoli ne ovijemo v Result — korutinska infrastruktura
            // jo potrebuje za pravilno preklicanje (cooperative cancellation).
            throw e
        } catch (e: Exception) {
            // Faza 38 — Result API: namesto meta-polj ali surovega throwanja emitiramo
            // Result.failure z eksplicitnim domenskim vzrokom. ViewModel prejme čist Result.
            val domainFailure: DomainException = when (e) {
                is DomainException -> e   // že prevedeno v data sloju — propagiraj as-is
                else -> DomainException.NetworkFailure(e.message ?: "Unknown sync error")
            }
            send(Result.failure(domainFailure))
        }
    }
}