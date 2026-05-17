package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.PlanResult

class SwapPlanDaysUseCase {
    /**
     * Zamenjaj dan A ↔ dan B v planu.
     *
     * @param lockedDay Opcijsko — danes opravljen dan (WORKOUT_DONE / STRETCHING_DONE).
     *                  Če je lockedDay == dayA ali dayB, swap ni dovoljen (Faza 9: Day Locking).
     *                  Preverjanje v UI sloju (PlanPathDialog) je primarna zapora;
     *                  ta parameter je varnostna plast na domenskem sloju.
     */
    fun invoke(currentPlan: PlanResult, dayA: Int, dayB: Int, lockedDay: Int? = null): Result<PlanResult> {
        return try {
            // 🔒 Faza 9: varnostna domeska zapora za zaklenjen dan
            if (lockedDay != null && (lockedDay == dayA || lockedDay == dayB)) {
                return Result.failure(Exception("Day $lockedDay is locked (already completed today). Cannot swap."))
            }

            val weekA = (dayA - 1) / 7
            val weekB = (dayB - 1) / 7

            if (weekA != weekB) {
                return Result.failure(Exception("Cross-week swap not allowed: $dayA <-> $dayB"))
            }

            val allDays = currentPlan.weeks.flatMap { it.days }.toMutableList()
            val iA = allDays.indexOfFirst { it.dayNumber == dayA }
            val iB = allDays.indexOfFirst { it.dayNumber == dayB }

            if (iA >= 0 && iB >= 0) {
                val rA = allDays[iA].isRestDay
                val fA = allDays[iA].focusLabel
                val rB = allDays[iB].isRestDay
                val fB = allDays[iB].focusLabel
                allDays[iA] = allDays[iA].copy(isRestDay = rB, focusLabel = fB)
                allDays[iB] = allDays[iB].copy(isRestDay = rA, focusLabel = fA)
            }

            val updatedWeeks = currentPlan.weeks.map { week ->
                week.copy(days = allDays.filter { (it.dayNumber - 1) / 7 == week.weekNumber - 1 })
            }
            val updatedPlan = currentPlan.copy(weeks = updatedWeeks)
            Result.success(updatedPlan)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}