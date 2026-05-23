package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.PlanResult

/**
 * Faza 30.3 — Domain sloj za zamenjavo dni v večtedenskem načrtu.
 *
 * Strogo ZAPORA (triple lock):
 *   1. Prazen plan ID → takoj Result.failure (brez Firestore klica)
 *   2. lockedDay (today's workout done) → ne sme biti eden od zamenjanih dni
 *   3. DayPlan.isFrozen → zamrznjen dan se ne sme premikati ne glede na UI
 *
 * UI preverjanje ni dovolj — ta Use Case je zadnja linija obrambe.
 */
class SwapPlanDaysUseCase {

    fun invoke(
        currentPlan: PlanResult,
        dayA: Int,
        dayB: Int,
        lockedDay: Int? = null
    ): Result<PlanResult> {
        return try {
            // 🔒 Faza 30.3 — Zapora 1: prazen plan ID pomeni, da pot do plana ni naložena
            if (currentPlan.id.isBlank()) {
                return Result.failure(
                    SwapValidationException("Plan path is empty — cannot swap without a valid plan ID.")
                )
            }

            // 🔒 Faza 9 — Zapora 2: danes opravljen dan (WORKOUT_DONE / STRETCHING_DONE)
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
                    SwapValidationException("Cross-week swap not allowed: day $dayA (week ${weekA + 1}) ↔ day $dayB (week ${weekB + 1})")
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

            // 🔒 Faza 30.3 — Zapora 3: isFrozen direktno na domenskem modelu
            // UI preverjanje ni dovolj — Use Case je zadnja linija obrambe.
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

            // Zamenjaj isRestDay + focusLabel (ne gifujemo dayNumber — ta ostane na mestu)
            val rA = allDays[iA].isRestDay; val fA = allDays[iA].focusLabel
            val rB = allDays[iB].isRestDay; val fB = allDays[iB].focusLabel
            allDays[iA] = allDays[iA].copy(isRestDay = rB, focusLabel = fB)
            allDays[iB] = allDays[iB].copy(isRestDay = rA, focusLabel = fA)

            val updatedWeeks = currentPlan.weeks.map { week ->
                week.copy(days = allDays.filter { (it.dayNumber - 1) / 7 == week.weekNumber - 1 })
            }
            Result.success(currentPlan.copy(weeks = updatedWeeks))
        } catch (e: SwapValidationException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Specifična izjema za validacijske napake pri zamenjavi dni.
 * Ločimo jo od splošnih RuntimeException — UI jo lahko prikaže uporabniško-prijazno.
 */
class SwapValidationException(message: String) : Exception(message)
