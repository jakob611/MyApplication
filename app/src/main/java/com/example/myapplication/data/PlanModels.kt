package com.example.myapplication.data

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

// Preprost KMP generator unikatnih ID-jev kot zamenjava za java.util.UUID
private fun generateSimpleId(): String {
    val charPool = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    return (1..32)
        .map { Random.nextInt(0, charPool.length) }
        .map(charPool::get)
        .joinToString("")
}

data class PlanResult(
    val id: String = generateSimpleId(),
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
    val startDate: String = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
)
data class WeekPlan(
    val weekNumber: Int,
    val days: List<DayPlan>
)
data class DayPlan(
    val dayNumber: Int,        // 1-based zaporedna številka (1 = prvi dan plana)
    val exercises: List<String> = emptyList(),
    val isRestDay: Boolean = false,   // true = počitek, false = trening
    val focusLabel: String = "",       // npr. "Legs", "Push", "Pull", "Rest" — prikazano na nodu
    val isSwapped: Boolean = false,
    val isFrozen: Boolean = false
)
