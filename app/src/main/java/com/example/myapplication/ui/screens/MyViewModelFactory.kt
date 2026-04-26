package com.example.myapplication.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import com.example.myapplication.viewmodels.BodyModuleHomeViewModel
import com.example.myapplication.viewmodels.RunTrackerViewModel
import com.example.myapplication.viewmodels.NutritionViewModel
import com.example.myapplication.viewmodels.ProgressViewModel
import com.example.myapplication.viewmodels.GamificationSharedViewModel
import com.example.myapplication.domain.workout.GetBodyMetricsUseCase
import com.example.myapplication.domain.workout.UpdateBodyMetricsUseCase
import com.example.myapplication.domain.workout.SwapPlanDaysUseCase
import com.example.myapplication.data.workout.FirestoreWorkoutRepository
import com.example.myapplication.domain.gamification.GamificationProvider
import com.example.myapplication.data.settings.UserPreferencesRepository

class MyViewModelFactory(private val context: Context? = null) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BodyOverviewViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BodyOverviewViewmodel() as T
        }
        if (modelClass.isAssignableFrom(RunTrackerViewModel::class.java)) {
            requireNotNull(context) { "Context required for RunTrackerViewModel" }
            val gamificationUseCase = GamificationProvider.provide(context)
            @Suppress("UNCHECKED_CAST")
            return RunTrackerViewModel(FirestoreWorkoutRepository(), gamificationUseCase) as T
        }
        if (modelClass.isAssignableFrom(NutritionViewModel::class.java)) {
            requireNotNull(context) { "Context required for NutritionViewModel" }
            val gamificationUseCase = GamificationProvider.provide(context)
            @Suppress("UNCHECKED_CAST")
            return NutritionViewModel(gamificationUseCase) as T
        }
        if (modelClass.isAssignableFrom(ProgressViewModel::class.java)) {
            requireNotNull(context) { "Context required for ProgressViewModel" }
            val gamificationUseCase = GamificationProvider.provide(context)
            @Suppress("UNCHECKED_CAST")
            return ProgressViewModel(gamificationUseCase) as T
        }
        if (modelClass.isAssignableFrom(GamificationSharedViewModel::class.java)) {
            requireNotNull(context) { "Context required for GamificationSharedViewModel" }
            val gamificationUseCase = GamificationProvider.provide(context)
            @Suppress("UNCHECKED_CAST")
            return GamificationSharedViewModel(gamificationUseCase) as T
        }
        if (modelClass.isAssignableFrom(BodyModuleHomeViewModel::class.java)) {
            requireNotNull(context) { "Context required for BodyModuleHomeViewModel" }
            val workoutRepo = FirestoreWorkoutRepository()
            val gamificationUseCase = GamificationProvider.provide(context)
            val settingsRepo = UserPreferencesRepository(context)

            @Suppress("UNCHECKED_CAST")
            return BodyModuleHomeViewModel(
                GetBodyMetricsUseCase(workoutRepo, settingsRepo),
                UpdateBodyMetricsUseCase(workoutRepo, gamificationUseCase),
                SwapPlanDaysUseCase()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}