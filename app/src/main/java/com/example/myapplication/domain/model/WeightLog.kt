package com.example.myapplication.domain.model

import kotlinx.datetime.LocalDate

/** Dnevni vpis telesne teže — SSOT domain model za Progress Analytics. */
data class WeightLog(
    val date: LocalDate,
    val weightKg: Double
)

