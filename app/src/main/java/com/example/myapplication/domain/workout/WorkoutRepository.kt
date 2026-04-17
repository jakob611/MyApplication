package com.example.myapplication.domain.workout

import com.example.myapplication.data.RunSession

interface WorkoutRepository {
    suspend fun getWeeklyDoneCount(email: String, startEpochSeconds: Long): Int
    suspend fun saveWorkoutSession(email: String, workoutDoc: Map<String, Any>): Boolean
    // RunTracker paginacija
    suspend fun getRunSessions(userId: String, startAfterDoc: Any?, limit: Int): Pair<List<RunSession>, Any?>
}

