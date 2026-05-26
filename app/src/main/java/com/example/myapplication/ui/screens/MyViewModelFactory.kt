package com.example.myapplication.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
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
import com.example.myapplication.data.repository.FoodRepositoryImpl
import com.example.myapplication.data.gamification.GamificationFactory
import com.example.myapplication.data.settings.UserPreferencesRepository
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.repository.OfflineFirstWorkoutRepository
import com.example.myapplication.data.profile.FirestoreUserProfileRepository
import com.example.myapplication.data.repository.UserWorkoutStatsRepository
import com.example.myapplication.data.auth.FirebaseAuthStateRepository
import com.example.myapplication.data.repository.PlanRepositoryImpl
import com.example.myapplication.data.repository.FirestoreBodyMeasurementsRepository
import com.example.myapplication.domain.usecase.CalculateBodyGoldenRatioUseCase
import com.example.myapplication.domain.usecase.SaveBodyMeasurementsUseCase

class MyViewModelFactory(private val context: Context? = null) : ViewModelProvider.Factory {

    /**
     * Faza 34 — HIGH-04: CreationExtras-aware override za SavedStateHandle podporo.
     * Klicano za BodyModuleHomeViewModel, ki zahteva SavedStateHandle za Process Death recovery.
     * Ostali ViewModel-i se delegirajo na `create(modelClass)` (stari API).
     */
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        if (modelClass.isAssignableFrom(BodyModuleHomeViewModel::class.java)) {
            requireNotNull(context) { "Context required for BodyModuleHomeViewModel" }
            val savedStateHandle = extras.createSavedStateHandle()
            val gamificationUseCase = GamificationFactory.provide(context)
            val settingsRepo = UserPreferencesRepository(context)
            val statsRepo = UserWorkoutStatsRepository(settingsRepo)
            // Faza 40 — FIX Anomaly 7: bodyMeasurementsRepo izračunan enkrat in posredovan
            // eksplicitno v BodyModuleHomeViewModel ORAZ SaveBodyMeasurementsUseCase.
            // Pred tem sta bila CalculateBodyGoldenRatioUseCase in SaveBodyMeasurementsUseCase
            // skrito instantiirana znotraj ViewModel razreda — nevidna odvisnost.
            val bodyMeasurementsRepo = FirestoreBodyMeasurementsRepository()
            return BodyModuleHomeViewModel(
                GetBodyMetricsUseCase(statsRepo),
                UpdateBodyMetricsUseCase(gamificationUseCase),
                SwapPlanDaysUseCase(PlanRepositoryImpl()),
                gamificationUseCase,                           // non-nullable (Faza 34 — LOW-03)
                FirestoreUserProfileRepository(),
                FirebaseAuthStateRepository(),
                bodyMeasurementsRepo,                          // Faza 30.6: história meritev
                CalculateBodyGoldenRatioUseCase(),             // Faza 40: eksplicitna DI
                SaveBodyMeasurementsUseCase(bodyMeasurementsRepo), // Faza 40: eksplicitna DI
                savedStateHandle                               // Faza 34 — HIGH-04: Process Death
            ) as T
        }
        // Delegiraj ostale ViewModel-e na stari create(modelClass) override
        return create(modelClass)
    }

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
            // Faza 29.8: FoodRepositoryImpl kot NutritionRepository vmesnik (DI)
            return NutritionViewModel(gamificationUseCase, FoodRepositoryImpl) as T
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
            val gamificationUseCase = GamificationFactory.provide(context)
            val settingsRepo = UserPreferencesRepository(context)
            val statsRepo = UserWorkoutStatsRepository(settingsRepo)
            val bodyMeasurementsRepo = FirestoreBodyMeasurementsRepository()
            @Suppress("UNCHECKED_CAST")
            return BodyModuleHomeViewModel(
                GetBodyMetricsUseCase(statsRepo),
                UpdateBodyMetricsUseCase(gamificationUseCase),
                SwapPlanDaysUseCase(PlanRepositoryImpl()),
                gamificationUseCase,
                FirestoreUserProfileRepository(),
                FirebaseAuthStateRepository(),
                bodyMeasurementsRepo,
                CalculateBodyGoldenRatioUseCase(),
                SaveBodyMeasurementsUseCase(bodyMeasurementsRepo)
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}