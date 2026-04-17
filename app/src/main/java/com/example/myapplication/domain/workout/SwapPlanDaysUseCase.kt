package com.example.myapplication.domain.workout

import com.example.myapplication.data.PlanResult

class SwapPlanDaysUseCase {
    fun invoke(currentPlan: PlanResult, dayA: Int, dayB: Int): Result<PlanResult> {
        return try {
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

