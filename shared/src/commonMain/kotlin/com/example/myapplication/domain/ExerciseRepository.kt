package com.example.myapplication.domain

import com.example.myapplication.data.RefinedExercise

interface ExerciseRepository {
    fun getAllExercises(): List<RefinedExercise>
    fun getAllEquipment(): Set<String>
    fun getExerciseByName(name: String): RefinedExercise?
}

