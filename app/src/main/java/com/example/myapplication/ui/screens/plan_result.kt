package com.example.myapplication.ui.screens

data class PlanResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val trainingPlan: String,
    val trainingDays: Int, // <-- PRAVILNO!
    val sessionLength: Int,
    val tips: List<String>,
    val createdAt: Long,
    val trainingLocation: String,
    val experience: String? = null,
    val goal: String? = null,
    val equipment: List<String> = emptyList(), // Added equipment
    val focusAreas: List<String> = emptyList(), // Added focus areas
    val weeks: List<WeekPlan> = emptyList(),
    val algorithmData: AlgorithmData? = null
)

data class WeekPlan(
    val weekNumber: Int,
    val days: List<DayPlan>
)
data class DayPlan(
    val dayNumber: Int,
    val exercises: List<String>
)