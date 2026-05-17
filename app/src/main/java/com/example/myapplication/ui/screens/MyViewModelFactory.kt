package com.example.myapplication.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.Context
import com.example.myapplication.viewmodels.BodyModuleHomeViewModel
import com.example.myapplication.ui.run.RunTrackerViewModel
import com.example.myapplication.ui.nutrition.NutritionViewModel
import com.example.myapplication.ui.progress.ProgressViewModel
import com.example.myapplication.ui.shared.GamificationSharedViewModel
import com.example.myapplication.domain.usecase.GetBodyMetricsUseCase
import com.example.myapplication.domain.usecase.UpdateBodyMetricsUseCase
import com.example.myapplication.domain.usecase.SwapPlanDaysUseCase
import com.example.myapplication.data.repository.FirestoreWorkoutRepository
import com.example.myapplication.data.gamification.GamificationFactory
import com.example.myapplication.data.settings.UserPreferencesRepository
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.repository.OfflineFirstWorkoutRepository
import com.example.myapplication.data.repository.UserWorkoutStatsRepository

class MyViewModelFactory(private val context: Context? = null) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BodyOverviewViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BodyOverviewViewmodel() as T
        }
        if (modelClass.isAssignableFrom(RunTrackerViewModel::class.java)) {
            requireNotNull(context) { "Context required for RunTrackerViewModel" }
            val gamificationUseCase = GamificationFactory.provide(context)
            val db = AppDatabase.getInstance(context)
            val offlineRepo = OfflineFirstWorkoutRepository(db)
            @Suppress("UNCHECKED_CAST")
            return RunTrackerViewModel(FirestoreWorkoutRepository(), gamificationUseCase, offlineRepo) as T
        }
        if (modelClass.isAssignableFrom(NutritionViewModel::class.java)) {
            requireNotNull(context) { "Context required for NutritionViewModel" }
            val gamificationUseCase = GamificationFactory.provide(context)
            @Suppress("UNCHECKED_CAST")
            return NutritionViewModel(gamificationUseCase) as T
        }
        if (modelClass.isAssignableFrom(ProgressViewModel::class.java)) {
            requireNotNull(context) { "Context required for ProgressViewModel" }
            val gamificationUseCase = GamificationFactory.provide(context)
            @Suppress("UNCHECKED_CAST")
            return ProgressViewModel(gamificationUseCase) as T
        }
        if (modelClass.isAssignableFrom(GamificationSharedViewModel::class.java)) {
            requireNotNull(context) { "Context required for GamificationSharedViewModel" }
            val gamificationUseCase = GamificationFactory.provide(context)
            @Suppress("UNCHECKED_CAST")
            return GamificationSharedViewModel(gamificationUseCase) as T
        }
        if (modelClass.isAssignableFrom(BodyModuleHomeViewModel::class.java)) {
            requireNotNull(context) { "Context required for BodyModuleHomeViewModel" }
            val workoutRepo = FirestoreWorkoutRepository()
            val gamificationUseCase = GamificationFactory.provide(context)
            val settingsRepo = UserPreferencesRepository(context)
            val statsRepo = UserWorkoutStatsRepository(settingsRepo)

            @Suppress("UNCHECKED_CAST")
            return BodyModuleHomeViewModel(
                GetBodyMetricsUseCase(statsRepo),
                UpdateBodyMetricsUseCase(workoutRepo, gamificationUseCase),
                SwapPlanDaysUseCase(),
                gamificationUseCase  // Faza 4b: za CompleteRestDay stretching logiko
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}