package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.BodyMetrics
import com.example.myapplication.domain.model.DomainException
import com.example.myapplication.domain.repository.WorkoutStatsRepository
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
 * Repository odda platformsko-nevtralni [DomainException], ki ga ta use case
 * samo propagira navzgor do ViewModel-a. Presentation sloj nikoli ne vidi Firebase tipov.
 */
class GetBodyMetricsUseCase(
    private val statsRepo: WorkoutStatsRepository
) {
    fun invoke(email: String): Flow<BodyMetrics> = channelFlow {
        // Takoj prikaži nalaganje
        send(BodyMetrics(isLoading = true))

        try {
            // Faza 34 — MED-04 Fix: getDailyCalories() se kešira ENKRAT pred collect zanko.
            val cachedDailyKcal = statsRepo.getDailyCalories()

            // Zbieramo reaktivni tok iz Firestore poslušalnice
            statsRepo.observeWorkoutStats(email).collect { stats ->
                if (stats != null) {
                    send(BodyMetrics(
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
                    ))
                } else {
                    // Faza 32.9 — BUG-01 Fix: dokument ne obstaja ali ni auth uporabnika.
                    send(BodyMetrics(
                        errorMessage = "Failed to sync with server — check connection",
                        isLoading    = false
                    ))
                }
            }
        } catch (e: DomainException) {
            // Faza 37 — Clean Architecture: DomainException je že platformsko-nevtralna —
            // use case jo samo propagira navzgor brez transformacije.
            // Data sloj (UserWorkoutStatsRepository) je že prevedel Firebase izjeme.
            throw e
        } catch (e: Exception) {
            // Splošne napake (IO, parsing) — zapakiraj v BodyMetrics za blag prikaz v UI.
            send(BodyMetrics(
                errorMessage = e.message ?: "Unknown sync error",
                isLoading    = false
            ))
        }
    }
}