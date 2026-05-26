package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.BodyMetrics
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
 */
class GetBodyMetricsUseCase(
    private val statsRepo: WorkoutStatsRepository
) {
    fun invoke(email: String): Flow<BodyMetrics> = channelFlow {
        // Takoj prikaži nalaganje
        send(BodyMetrics(isLoading = true))

        try {
            // Zbieramo reaktivni tok iz Firestore poslušalnice
            statsRepo.observeWorkoutStats(email).collect { stats ->
                if (stats != null) {
                    // Uspešno mapiranje → pravi podatki
                    send(BodyMetrics(
                        streakDays             = stats.streakDays,
                        streakFreezes          = stats.streakFreezes,
                        weeklyDone             = stats.weeklyDone,
                        weeklyTarget           = stats.weeklyTarget,
                        planDay                = stats.planDay,
                        totalWorkoutsCompleted = stats.totalWorkoutsCompleted,
                        isWorkoutDoneToday     = stats.todayStatus.isDoneToday,
                        dailyKcal              = stats.dailyKcal.takeIf { it > 0 }
                                                 ?: statsRepo.getDailyCalories(),
                        todayIsRest            = stats.todayIsRest,
                        todayStatus            = stats.todayStatus,
                        isLoading              = false
                    ))
                } else {
                    // Faza 32.9 — BUG-01 Fix: Dokument ne obstaja ali ni avtenticiranega
                    // uporabnika. NE uporabimo tihega SharedPrefs fallback-a z hardkodiranimi
                    // vrednostmi (streak=0, weeklyDone=0). Namesto tega propagiramo jasno
                    // napako, ki jo UI prikaže kot ShowSnackbar.
                    send(BodyMetrics(
                        errorMessage = "Failed to sync with server — check connection",
                        isLoading    = false
                    ))
                }
            }
        } catch (e: Exception) {
            // Firestore napaka (network, auth) ali close(error) iz callbackFlow
            // propagira kot BodyMetrics z dejanskim sporočilom — brez hardkodiranih ničel
            send(BodyMetrics(
                errorMessage = e.message ?: "Unknown sync error",
                isLoading    = false
            ))
        }
    }
}