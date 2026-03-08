package com.example.myapplication.ui.screens

data class PlanResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val trainingPlan: String,
    val trainingDays: Int,
    val sessionLength: Int,
    val tips: List<String>,
    val createdAt: Long,
    val trainingLocation: String,
    val experience: String? = null,
    val goal: String? = null,
    val equipment: List<String> = emptyList(),
    val focusAreas: List<String> = emptyList(),
    val weeks: List<WeekPlan> = emptyList(),
    val algorithmData: AlgorithmData? = null,
    // Dnevni calendar sistem: ISO datum začetka plana (npr. "2026-03-07")
    val startDate: String = java.time.LocalDate.now().toString()
)

data class WeekPlan(
    val weekNumber: Int,
    val days: List<DayPlan>
)

data class DayPlan(
    val dayNumber: Int,        // 1-based zaporedna številka (1 = prvi dan plana)
    val exercises: List<String> = emptyList(),
    val isRestDay: Boolean = false,   // true = počitek, false = trening
    val focusLabel: String = ""       // npr. "Legs", "Push", "Pull", "Rest" — prikazano na nodu
)