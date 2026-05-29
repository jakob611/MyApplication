# APP_MAP.md — Ground Truth
> **NAVODILO ZA AI:** Ko dobiš nalogo "popravi X", najprej poglej v to datoteko da ugotoviš KATERO datoteko odpreti. Ne ugibaj.

**Zadnja posodobitev:** 2026-05-17 (Faza 17 Clean Architecture Refactoring — novi paketi)

---

## KAKO BRATI TA DOKUMENT
- 🖥️ = UI screen (kar uporabnik vidi)
- 🧠 = logika / ViewModel
- 💾 = Firestore / lokalno shranjevanje
- 📐 = data modeli
- 🔧 = pomožne funkcije / utility
- ⚙️ = ozadni procesi (Worker, Service)
- 🏗️ = domain/usecase (Clean Architecture, iOS-ready)

---

## ARHITEKTURNI PREGLED (Clean Architecture — Faza 17 Refactoring)

```
MainActivity (100 vrstic) — samo onCreate + setContent
    └── ui/MainAppContent.kt — vse Composable routing (30+ screeni)
            ├── appViewModel (AppViewModel.kt)
            ├── navViewModel (NavigationViewModel.kt)
            └── vsi screen Composables (Screen.XYZ → XYZScreen())

PAKETNA STRUKTURA (po refaktoringu):
├── domain/
│   ├── model/          — čisti domenski modeli, brez Android
│   ├── usecase/        — posamezne logične operacije, ios-ready
│   ├── nutrition/      — NutritionCalculations.kt (SSOT za vse kalkulacije: BMR, TDEE, makri, voda, rest day)
│   ├── workout/        — WorkoutGenerator.kt, WorkoutPlanGenerator.kt
│   ├── run/            — RouteCompressor.kt (RDP), MapboxMapMatcher.kt
│   ├── gamification/   — AchievementStore.kt, ManageGamificationUseCase.kt
│   └── looksmaxing/    — CalculateGoldenRatioUseCase.kt
├── data/
│   ├── store/          — FirestoreHelper.kt, NutritionPlanStore.kt, PlanDataStore.kt, ProfileStore.kt, RunRouteStore.kt, DailySyncManager.kt, FollowStore.kt
│   ├── local/          — AppDatabase.kt, OfflineFirstWorkoutRepository.kt, Room DAOs/Entities
│   ├── gamification/   — FirestoreGamificationRepository.kt
│   ├── settings/       — UserProfileManager.kt
│   └── auth/           — AuthRepository.kt
├── ui/
│   ├── screens/        — NutritionScreen.kt, BodyModule*.kt, FaceModule.kt, Dashboard*, Login, Index...
│   ├── workout/        — WorkoutSessionScreen.kt, GenerateWorkoutScreen.kt, ManualExerciseLogScreen.kt, ExerciseHistoryScreen.kt, LoadingWorkoutScreen.kt
│   ├── run/            — RunTrackerScreen.kt, RunTrackerViewModel.kt, PlanPathDialog.kt, PlanPathVisualizer.kt
│   ├── progress/       — Progress.kt
│   ├── home/           — CommunityScreen.kt
│   ├── nutrition/      — (AddFoodSheet, NutritionDialogs, Barcode)
│   └── components/     — skupne UI komponente
├── service/            — RunTrackingService.kt (Foreground GPS, Android 14+)
├── viewmodels/         — NutritionViewModel.kt, BodyModuleHomeViewModel.kt, RunTrackerViewModel.kt
├── persistence/        — (legacy, postopoma migrira v data/store)
├── workers/ + worker/  — WeeklyStreakWorker.kt, DailySyncWorker.kt, StreakReminderWorker.kt
└── utils/              — HapticFeedback.kt, AppToast.kt, RouteCompressor (PREMAKNI → domain/run)

⚠️  MRTVA KODA (Safe Delete že opravljen ali v teku):
- domain/run/CompressRouteUseCase.kt → IZBRISANO (nadomeščen z RouteCompressor, RDP algoritem)
- domain/nutrition/NutritionCalculations.kt (stub) → IZBRISANO (canonical je domain/nutrition/NutritionCalculations.kt)
```

