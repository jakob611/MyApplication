package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.PlanResult
import com.example.myapplication.domain.repository.PlanRepository

/**
 * Faza 30.4 — Clean Architecture: VM → UseCase → Repository → DataStore.
 *
 * UseCase odgovornosti:
 *   1. Domenska validacija (triple lock: blank id, lockedDay, isFrozen)
 *   2. Gradnja posodobljenega lokalnega modela (za optimistični UI)
 *   3. Klic planRepository.swapDays() za persistenco (Firestore)
 *
 * ViewModel ne ve za obstoj PlanDataStore — kliče SAMO ta use case.
 */
class SwapPlanDaysUseCase(
    private val planRepository: PlanRepository
) {

    /**
     * Validira, gradi lokalni posodobljeni model in persistira v Firestore.
     *
     * @return Result.success(updatedPlan) ko je Firestore zapis uspel
     *         Result.failure(SwapValidationException) za domenske napake
     *         Result.failure(Exception) za omrežne/Firestore napake
     */
    suspend fun invoke(
        currentPlan: PlanResult,
        dayA: Int,
        dayB: Int,
        lockedDay: Int? = null
    ): Result<PlanResult> {
        // ── Domenska validacija (triple lock) ──────────────────────────────────

        //  Zapora 1: prazen plan ID — pot do načrta ni naložena
        if (currentPlan.id.isBlank()) {
            return Result.failure(
                SwapValidationException("Plan path is empty — cannot swap without a valid plan ID.")
            )
        }

        //  Zapora 2: danes opravljen dan (WORKOUT_DONE / STRETCHING_DONE)
        if (lockedDay != null && (lockedDay == dayA || lockedDay == dayB)) {
            return Result.failure(
                SwapValidationException("Day $lockedDay is locked (completed today). Cannot swap.")
            )
        }

        // Cross-week kontrola
        val weekA = (dayA - 1) / 7
        val weekB = (dayB - 1) / 7
        if (weekA != weekB) {
            return Result.failure(
                SwapValidationException(
                    "Cross-week swap not allowed: day $dayA (week ${weekA + 1}) ↔ day $dayB (week ${weekB + 1})"
                )
            )
        }

        val allDays = currentPlan.weeks.flatMap { it.days }.toMutableList()
        val iA = allDays.indexOfFirst { it.dayNumber == dayA }
        val iB = allDays.indexOfFirst { it.dayNumber == dayB }

        if (iA < 0 || iB < 0) {
            return Result.failure(
                SwapValidationException("Day not found in plan: dayA=$dayA (idx=$iA), dayB=$dayB (idx=$iB)")
            )
        }

        //  Zapora 3: isFrozen — UI preverjanje ni dovolj, domain mora blokirati
        if (allDays[iA].isFrozen) {
            return Result.failure(
                SwapValidationException("Day $dayA is frozen (completed). Cannot swap locked days.")
            )
        }
        if (allDays[iB].isFrozen) {
            return Result.failure(
                SwapValidationException("Day $dayB is frozen (completed). Cannot swap locked days.")
            )
        }

        // ── Gradnja lokalnega modela (optimistični UI) ─────────────────────────
        // Polna zamenjava vsebine dni: ohrani dayNumber, zamenjaj VSE ostale podatke
        val contentA = allDays[iA]
        val contentB = allDays[iB]
        allDays[iA] = contentB.copy(dayNumber = dayA)
        allDays[iB] = contentA.copy(dayNumber = dayB)

        val updatedWeeks = currentPlan.weeks.map { week ->
            week.copy(days = allDays.filter { (it.dayNumber - 1) / 7 == week.weekNumber - 1 })
        }
        val updatedPlan = currentPlan.copy(weeks = updatedWeeks)

        // ── Persistenca: VM → UseCase → Repository → DataStore ────────────────
        return try {
            planRepository.swapDays(currentPlan.id, dayA, dayB)
                .map { updatedPlan }  // Firestore uspel → vrni posodobljeni lokalni model
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Specifična izjema za domenske validacijske napake pri zamenjavi dni.
 * UI jo lahko prikaže brez stack tracea.
 */
class SwapValidationException(message: String) : Exception(message)