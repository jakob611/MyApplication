package com.example.myapplication.domain.gamification

import android.content.Context
import com.example.myapplication.data.gamification.FirestoreGamificationRepository
import com.example.myapplication.data.settings.UserPreferencesRepository

object GamificationProvider {
    @Volatile
    private var instance: ManageGamificationUseCase? = null

    fun provide(context: Context): ManageGamificationUseCase {
        return instance ?: synchronized(this) {
            instance ?: createUseCase(context).also { instance = it }
        }
    }

    private fun createUseCase(context: Context): ManageGamificationUseCase {
        val repository = FirestoreGamificationRepository()
        val prefs = UserPreferencesRepository(context.applicationContext)
        return ManageGamificationUseCase(
            repository = repository,
            workoutDoneProvider = { prefs.isWorkoutDoneToday() },
            weeklyTargetProvider = { prefs.getWeeklyTargetFlow() }
        )
    }
}

