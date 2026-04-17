package com.example.myapplication.domain

import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun isWorkoutDoneToday(): Boolean
    fun getWeeklyTargetFlow(): Flow<Int>
}
