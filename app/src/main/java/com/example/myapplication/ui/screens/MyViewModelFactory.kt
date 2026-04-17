package com.example.myapplication.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import com.example.myapplication.viewmodels.BodyModuleHomeViewModel
import com.example.myapplication.viewmodels.RunTrackerViewModel
import com.example.myapplication.domain.workout.GetBodyMetricsUseCase
import com.example.myapplication.domain.workout.UpdateBodyMetricsUseCase
import com.example.myapplication.domain.workout.SwapPlanDaysUseCase
import com.example.myapplication.data.workout.FirestoreWorkoutRepository
import com.example.myapplication.domain.gamification.ManageGamificationUseCase
import com.example.myapplication.data.gamification.FirestoreGamificationRepository
import com.example.myapplication.data.settings.UserPreferencesRepository

class MyViewModelFactory(private val context: Context? = null) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BodyOverviewViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BodyOverviewViewmodel() as T
        }
        if (modelClass.isAssignableFrom(RunTrackerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RunTrackerViewModel(FirestoreWorkoutRepository()) as T
        }
        if (modelClass.isAssignableFrom(BodyModuleHomeViewModel::class.java)) {
            requireNotNull(context) { "Context required for BodyModuleHomeViewModel" }
            val workoutRepo = FirestoreWorkoutRepository()
            val gamificationRepo = FirestoreGamificationRepository()
            val gamificationUseCase = ManageGamificationUseCase(gamificationRepo)
            val settingsRepo = UserPreferencesRepository(context)

            @Suppress("UNCHECKED_CAST")
            return BodyModuleHomeViewModel(
                GetBodyMetricsUseCase(workoutRepo, settingsRepo),
                UpdateBodyMetricsUseCase(workoutRepo, gamificationUseCase, settingsRepo),
                SwapPlanDaysUseCase()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}