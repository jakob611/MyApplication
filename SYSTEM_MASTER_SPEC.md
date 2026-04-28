# SYSTEM_MASTER_SPEC.md
> **Faza 1 — Strukturni inventar (samo dejstva, brez predlogov)**  
> **Datum nastanka:** 2026-04-28  
> **Namen:** Popoln pregled arhitekture kot referenca za vse prihodnje faze audita.

---

## 1. TEHNOLOŠKI SKLAD

### Gradle / Build
| Parameter | Vrednost |
|-----------|----------|
| compileSdk / targetSdk | **36** |
| minSdk | **26** (Android 8.0 Oreo) |
| Java compatibility | **17** |
| Kotlin JVM target | **17** |
| Kotlin Compose Compiler Plugin | **2.2.10** |
| Release minify | `isMinifyEnabled = true` + R8 |
| ABI packaging | Izključeni `x86` / `x86_64`; `useLegacyPackaging = true` |

### Ključne knjižnice (verzije)

| Kategorija | Knjižnica | Verzija |
|-----------|-----------|---------|
| **UI** | Compose BOM | `2024.06.00` |
| **UI** | Material3 (Compose) | via BOM |
| **UI** | Material Icons Extended | via BOM |
| **UI** | Navigation Compose | `2.7.7` |
| **Firebase** | Firebase BOM | `33.1.0` |
| **Firebase** | firebase-auth-ktx | via BOM |
| **Firebase** | firebase-firestore-ktx | via BOM |
| **Firebase** | firebase-storage-ktx | via BOM |
| **Auth** | play-services-auth (Google Sign-In) | `21.4.0` |
| **GPS/Lokacija** | play-services-location | `21.3.0` |
| **Mapa** | OSMDroid | `6.1.20` |
| **Mapa** | Mapbox (MapMatcher) | konfiguriran v `local.properties` |
| **Health** | Health Connect (`connect-client`) | `1.1.0-alpha08` (alpha!) |
| **Kamera** | CameraX (camera2 + lifecycle + view) | `1.3.1` |
| **Barcode** | ML Kit Barcode Scanning | `17.3.0` |
| **Face** | ML Kit Face Detection | `16.1.6` |
| **Video** | Media3 ExoPlayer / UI / Common | `1.2.1` |
| **Slike** | Coil Compose | `2.5.0` |
| **HTTP** | OkHttp | `4.12.0` |
| **JSON** | Gson | `2.10.1` |
| **Serializacija** | kotlinx-serialization-json | `1.6.3` |
| **Čas** | kotlinx-datetime | `0.6.1` |
| **KMP nastavitve** | multiplatform-settings (Russhwolf) | `1.1.1` |
| **Lokalno shranjevanje** | DataStore Preferences | `1.0.0` |
| **Ozadni procesi** | WorkManager | `2.9.0` |
| **Coroutines** | kotlinx-coroutines-android | `1.8.1` |
| **Coroutines** | kotlinx-coroutines-play-services | `1.7.3` |
| **Lifecycle** | lifecycle-runtime-ktx | `2.7.0` |
| **Lifecycle** | lifecycle-runtime-compose | `2.7.0` |
| **Activity** | activity-compose | `1.9.0` |
| **Core** | core-ktx | `1.13.1` |
| **AppCompat** | appcompat | `1.7.0` |

### Zunanji API-ji (BuildConfig polja)
| Ključ | Namen |
|-------|-------|
| `FATSECRET_BASE_URL` | Iskanje hrane po imenu |
| `FITNESS_API_BASE_URL` | AI plan generation (HTTP backend) |
| `BACKEND_API_KEY` | Autentikacija do backend API |
| `MAPBOX_PUBLIC_KEY` / `MAPBOX_SECRET_KEY` | Mapbox map matching |
| `OPEN_WEATHER_API_KEY` | Vremenski podatki (WeatherService.kt) |

---

## 2. PACKAGE STRUKTURA

Vse datoteke so v: `com.example.myapplication`

```
com.example.myapplication/
│
├── [ROOT]                        ← Vstopne točke in globalni singletons
│   ├── MainActivity.kt           ← Vstopna točka; auth check, Google Sign-In, Compose root
│   ├── MyApplication.kt          ← Application razred; OSMdroid + Firestore init, KMP init
│   ├── AppViewModel.kt           ← Globalni profil StateFlow + InitialSyncManager
│   ├── NavigationViewModel.kt    ← Navigacijski stack (preživi config change)
│   ├── AppNavigation.kt          ← `sealed class Screen` + AppBottomBar (4 tabi)
│   ├── AppDrawer.kt              ← Stranski meni: profil, oprema, dark mode, odjava
│   └── TestDate.kt               ← Razvojno orodje za datum simulacijo
│
├── data/                         ← Čisti data modeli + repository implementacije
│   ├── UserProfile.kt            ← `UserProfile` data class + `calculateLevel(xp)`
│   ├── UserAchievements.kt       ← `XPSource`, `PrivacySettings`, `PublicActivity`, `PublicProfile`
│   ├── BadgeDefinitions.kt       ← `ALL_BADGES` lista, `Badge`, `BadgeCategory` — SSOT za badge-e
│   ├── PlanModels.kt             ← `PlanResult`, `WeekPlan`, `DayPlan`
│   ├── AlgorithmData.kt          ← Debug podatki o BMR/TDEE
│   ├── AlgorithmPreferences.kt   ← SharedPrefs wrapper za algoritemske parametre
│   ├── AdvancedExerciseRepository.kt ← JSON baza 100+ vaj z metapodatki
│   ├── RefinedExercise.kt        ← Model vaje v aktivni sesiji
│   ├── RunSession.kt             ← `RunSession` + `ActivityType` enum s MET vrednostmi
│   ├── HealthStorage.kt          ← Lokalno shranjevanje Health Connect podatkov
│   ├── NutritionPlan.kt          ← `NutritionPlan` model
│   ├── UserPreferences.kt        ← Lokalni SharedPrefs/Firestore load-save profila
│   ├── auth/
│   │   └── FirebaseAuthRepositoryImpl.kt
│   ├── barcode/
│   │   └── AndroidMLKitBarcodeScanner.kt
│   ├── daily/
│   │   └── DailyLogRepository.kt ← Atomarni Firestore zapis `dailyLogs/{date}/burnedCalories`
│   ├── gamification/
│   │   └── FirestoreGamificationRepository.kt
│   ├── looksmaxing/             ← (Face/Hair module modeli)
│   ├── metrics/
│   │   └── MetricsRepositoryImpl.kt
│   ├── nutrition/
│   │   └── FoodRepositoryImpl.kt
│   ├── profile/
│   │   └── FirestoreUserProfileRepository.kt ← Firestore implementacija UserProfileRepository
│   ├── settings/
│   │   ├── AndroidSettingsProvider.kt ← KMP Settings Android implementacija
│   │   ├── UserPreferencesRepository.kt ← KMP Settings Data (Flow manager) — zamenja bm_prefs
│   │   └── UserProfileManager.kt   ← `loadProfileFromFirestore()`, `updateUserProgressAfterWorkout()`
│   └── workout/
│       └── FirestoreWorkoutRepository.kt ← GPS točke load + sub-kolekcija fallback
│
├── domain/                       ← Čista poslovna logika (brez Android API)
│   ├── WorkoutPlanGenerator.kt   ← 4-tedenski plan algoritem
│   ├── WorkoutGenerator.kt       ← Dnevni workout generator s progresijo (Faza 11-12)
│   ├── DateFormatter.kt
│   ├── DateTimeExtensions.kt
│   ├── Logger.kt
│   ├── gamification/
│   │   ├── ManageGamificationUseCase.kt ← `awardXP()`, `updateStreak()`, badge unlock
│   │   ├── GamificationRepository.kt   ← Interfejs
│   │   └── GamificationProvider.kt
│   ├── looksmaxing/
│   │   ├── CalculateGoldenRatioUseCase.kt
│   │   ├── FaceDetector.kt
│   │   └── FaceDetectorProvider.kt
│   ├── math/                     ← (matematični pomočniki)
│   ├── metrics/
│   │   ├── MetricsRepository.kt  ← Interfejs
│   │   ├── SaveWeightUseCase.kt
│   │   └── SyncWeightUseCase.kt
│   ├── nutrition/
│   │   ├── BodyCompositionUseCase.kt
│   │   ├── FoodRepository.kt     ← Interfejs
│   │   └── NutritionCalculations.kt ← ⚠️ DEPRECATED (prazna datoteka)
│   ├── profile/
│   │   ├── ObserveUserProfileUseCase.kt ← Firestore snapshot listener → StateFlow
│   │   └── UserProfileRepository.kt    ← Interfejs
│   ├── run/
│   │   └── CompressRouteUseCase.kt ← RDP kompresija GPS trase
│   ├── settings/
│   │   ├── SettingsManager.kt
│   │   └── SettingsProvider.kt
│   └── workout/
│       ├── GetBodyMetricsUseCase.kt
│       ├── UpdateBodyMetricsUseCase.kt ← Shrani streak/planDay transakcijsko
│       ├── SwapPlanDaysUseCase.kt
│       ├── SyncHealthConnectUseCase.kt
│       └── WorkoutRepository.kt  ← Interfejs
│
├── persistence/                  ← Konkretni Firestore/lokalni dostop do podatkov
│   ├── FirestoreHelper.kt        ← ⛔ SSOT za Firestore ref; email vs UID reševanje + cache
│   ├── DailySyncManager.kt       ← Local-first cache za food/water; sync prek WorkManager
│   ├── FollowStore.kt            ← `followUser()`, `unfollowUser()`, `isFollowing()`
│   ├── NutritionPlanStore.kt     ← Nutrition plan Firestore CRUD
│   ├── PlanDataStore.kt          ← Plan CRUD + AI plan HTTP klic (`user_plans` kolekcija)
│   ├── ProfileStore.kt           ← Javni profili, iskanje, privacy nastavitve
│   ├── RecentFoodStore.kt        ← Lokalni cache zadnje hrane
│   └── RunRouteStore.kt          ← GPS točke teka — samo lokalno (SharedPreferences)
│
├── ui/
│   ├── adapters/
│   │   └── ChallengeAdapter.kt   ← ⚠️ DEAD CODE — RecyclerView stari View sistem
│   ├── components/
│   │   ├── LoadingRetryView.kt
│   │   ├── OnboardingHint.kt
│   │   └── XPPopup.kt            ← +XP lebdeč popup
│   ├── home/
│   │   └── CommunityScreen.kt    ← Community tab (iskanje, leaderboard)
│   ├── screens/                  ← Vsi Compose screeni (45 datotek)
│   │   ├── [Pred-login]          ← Indexscreen.kt, LoginScreen.kt
│   │   ├── [Body/Vadba]          ← BodyModule.kt, BodyModuleHomeScreen.kt, WorkoutSessionScreen.kt,
│   │   │                            GenerateWorkoutScreen.kt, LoadingWorkoutScreen.kt,
│   │   │                            ManualExerciseLogScreen.kt, ExerciseHistoryScreen.kt,
│   │   │                            MyPlansScreen.kt, PlanPathVisualizer.kt, PlanPathDialog.kt,
│   │   │                            KnowledgeHubScreen.kt, BodyOverviewScreen.kt
│   │   ├── [Prehrana]            ← NutritionScreen.kt, NutritionComponents.kt, NutritionDialogs.kt,
│   │   │                            NutritionModels.kt, AddFoodSheet.kt, BarcodeScannerScreen.kt,
│   │   │                            DonutProgressView.kt
│   │   ├── [Napredek]            ← Progress.kt, AchievementsScreen.kt, BadgesScreen.kt,
│   │   │                            LevelPathScreen.kt, BodyOverviewViewmodel.kt, GoldenRatioScreen.kt
│   │   ├── [Tek]                 ← RunTrackerScreen.kt, ActivityLogScreen.kt
│   │   ├── [Profil/Social]       ← MyAccountScreen.kt, PublicProfileScreen.kt
│   │   ├── [Obraz/Lasje]         ← FaceModule.kt, HairModuleScreen.kt
│   │   ├── [Pro/Shop]            ← ProFeaturesScreen.kt, ProSubscriptionScreen.kt, ShopScreen.kt
│   │   ├── [Utiliti]             ← HealthConnectScreen.kt, EAdditivesScreen.kt, DeveloperSettingsScreen.kt,
│   │   │                            DebugDashboardScreen.kt
│   │   ├── [Pravne]              ← PrivacyPolicyScreen.kt, TermsOfServiceScreen.kt, ContactScreen.kt, AboutScreen.kt
│   │   └── MyViewModelFactory.kt ← Factory za ViewModele s parametri
│   └── theme/                    ← Compose teme in barve
│
├── viewmodels/                   ← ViewModeli za specifične screene
│   ├── BodyModuleHomeViewModel.kt ← Streak, weekly progress, `completeWorkoutSession()`, `swapDaysInPlan()`
│   ├── DebugViewModel.kt
│   ├── GamificationSharedViewModel.kt
│   ├── NutritionViewModel.kt     ← Food tracking, optimistični water updates, custom meals
│   ├── ProgressViewModel.kt
│   ├── RunTrackerViewModel.kt    ← Load/save run sessions, pagination
│   ├── ShopViewModel.kt
│   └── WorkoutSessionViewModel.kt ← `prepareWorkout()`, progressive overload, Firestore fetch
│
├── network/
│   ├── fatsecret_api_service.kt  ← FatSecret API (hrana po imenu/barkodi)
│   ├── OpenFoodFactsAPI.kt       ← Open Food Facts alternativni vir
│   └── ai_utils.kt               ← ⚠️ DEAD CODE — `requestAIPlan()` ni klicana nikjer
│
├── utils/
│   ├── AppToast.kt
│   ├── HapticFeedback.kt
│   ├── NetworkObserver.kt
│   ├── NutritionCalculations.kt  ← ✅ AKTIVEN SSOT: BMR, TDEE, makro izračuni, AdaptiveTDEE
│   ├── UXEventLogger.kt
│   └── WeatherService.kt
│
├── debug/
│   ├── NutritionDebugStore.kt
│   └── WeightPredictorStore.kt   ← `lastHybridTDEE`, `lastAdaptiveTDEE`, `lastConfidenceFactor`
│
├── health/
│   └── HealthConnectManager.kt
│
├── map/
│   └── MapboxMapMatcher.kt
│
├── service/
│   └── RunTrackingService.kt     ← ForegroundService za GPS tracking v ozadju
│
├── worker/
│   ├── DailySyncWorker.kt        ← Food/water/burned → Firestore sync
│   └── RunRouteCleanupWorker.kt
│
├── workers/
│   ├── WeeklyStreakWorker.kt     ← Polnočni streak update (OneTimeWork + reschedule)
│   └── StreakReminderWorker.kt   ← Push notifikacija za streak reminder
│
└── widget/
    ├── PlanDayWidgetProvider.kt
    ├── QuickMealWidgetProvider.kt
    ├── StatsWidget.kt
    ├── StreakWidgetProvider.kt
    ├── WaterInputActivity.kt
    ├── WaterWidgetProvider.kt
    ├── WeightInputActivity.kt
    └── WeightWidgetProvider.kt
```

---

## 3. ENTRY POINTS

### 3.1 Zagon aplikacije (sekvenča)

```
1. MyApplication.onCreate()
   ├── OSMDroid tile cache: 50 MB disk, 100 tiles RAM
   ├── Firestore offline persistence: 100 MB
   └── SettingsManager.provider = AndroidSettingsProvider(this)  ← KMP init

2. MainActivity (ComponentActivity)
   ├── AppViewModel (viewModel())      ← globalni profil + sync stanje
   ├── NavigationViewModel (viewModel()) ← navigacijski stack
   ├── GamificationSharedViewModel     ← XP/badge eventi
   ├── Google Sign-In intent launcher
   └── BackHandler → navViewModel.navigateBack()

3. Auth check v MainActivity:
   ├── Firebase.auth.currentUser != null?
   │   ├── YES → AppViewModel.startInitialSync(context, email)
   │   │           └── Firestore fetch: profil + plani + teže (vzporedno)
   │   │               → navViewModel.navigateTo(Screen.Dashboard)
   │   └── NO  → navViewModel.navigateTo(Screen.Index)

4. Screen.Index (splash) → Screen.Login → Login uspešen
   └── AppViewModel.startInitialSync() → Screen.Dashboard
```

### 3.2 Managerji v ozadju (Singletons)

| Manager | Tip | Kdaj se zažene | Namen |
|---------|-----|----------------|-------|
| `FirestoreHelper` | `object` (singleton) | ob prvem klicu | Centralni resolver Firestore doc ref (email vs UID) |
| `AchievementStore` | `object` (singleton) | ob vadbi/loginu | XP podeljevanje, badge unlock, streak update |
| `DailySyncManager` | `object` | ob app pause/open | Lokalni food/water cache sync → Firestore |
| `AppViewModel` | ViewModel (activity-scoped) | ob zagonu MainActivity | Globalni profil + InitialSync overlay |
| `NavigationViewModel` | ViewModel (activity-scoped) | ob zagonu MainActivity | Navigacijski stack (preživi rotacijo) |
| `RunTrackingService` | ForegroundService | ob začetku teka | GPS tracking v ozadju (ne zgubi lokacije ob zaklenjeni napravi) |
| `DailySyncWorker` | WorkManager (OneTime) | ob `onPause` / `onResume` | Burst sync food+water+burned → Firestore |
| `WeeklyStreakWorker` | WorkManager (OneTime + reschedule) | vsako polnoč | Streak posodobitev, `yesterday_was_rest` flag |
| `StreakReminderWorker` | WorkManager | ob določenem času | Push notifikacija za streak |
| `SettingsManager` | Singleton (KMP) | `MyApplication.onCreate()` | KMP cross-platform nastavitve |

### 3.3 Widget Entry Points (HomeScreen widgeti)
- `PlanDayWidgetProvider` — prikaže aktualni plan dan
- `StreakWidgetProvider` — prikaže streak
- `WaterWidgetProvider` + `WaterInputActivity` — direkten vnos vode
- `WeightWidgetProvider` + `WeightInputActivity` — direkten vnos teže
- `QuickMealWidgetProvider`, `StatsWidget`

---

## 4. DEPENDENCY MAP

### 4.1 Ključne datoteke (kliče jih največ modulov hkrati)

| Datoteka | Kdo jo kliče | Zakaj je kritična |
|----------|-------------|-------------------|
| **`FirestoreHelper.kt`** | ProfileStore, PlanDataStore, FollowStore, NutritionPlanStore, DailySyncManager, AppViewModel, BodyModuleHomeViewModel, RunTrackerViewModel, WorkoutSessionViewModel, AchievementStore, DailyLogRepository, FirestoreWorkoutRepository, UserProfileManager, WeeklyStreakWorker, StreakReminderWorker, … | SSOT za Firestore document reference (reši email vs UID) |
| **`AchievementStore.kt`** | BodyModuleHomeViewModel, ManualExerciseLogScreen, WorkoutSessionScreen, RunTrackerScreen, WeeklyStreakWorker | Edini vhod za XP + badge unlock + streak |
| **`BadgeDefinitions.kt`** | AchievementStore, BadgesScreen, ProfileStore.mapToPublicProfile, PublicProfileScreen | Edina definicija badge `requirement` vrednosti |
| **`UserProfile.kt`** | AppViewModel, BodyModuleHomeViewModel, NutritionViewModel, WorkoutSessionViewModel, Progress.kt, AchievementsScreen, BadgesScreen, ProfileStore, PublicProfileScreen, BodyOverviewViewmodel, ManualExerciseLogScreen, … | Centralni data class za celoten profil |
| **`UserProfileManager.kt`** | AppViewModel.startInitialSync, BodyModuleHomeViewModel (GetBodyMetricsUseCase), WorkoutSessionScreen, StreakReminderWorker, ManualExerciseLogScreen | `loadProfileFromFirestore()`, `updateUserProgressAfterWorkout()` |
| **`DailyLogRepository.kt`** | RunTrackerScreen (po teku), ManualExerciseLogScreen (po vaji), UpdateBodyMetricsUseCase (po vadbi) | SSOT za `burnedCalories` in `dailyLogs`; vsi 3 viri aktivnosti pišejo sem |
| **`AppNavigation.kt`** (Screen sealed class) | MainActivity (routing), NavigationViewModel, AppDrawer, vsak screen s navigacijo | Vsak screen, ki navigira, mora imeti tu definiran `Screen.Xyz` objekt |
| **`ManageGamificationUseCase.kt`** | (prek AchievementStore ali direktno) | XP izračun, badge check, streak logika |
| **`NutritionViewModel.kt`** | NutritionScreen, NutritionComponents, NutritionDialogs, AddFoodSheet, Progress.kt | Centralni state za food tracking (water, meals, macros, TDEE) |
| **`AppViewModel.kt`** | MainActivity (overlay), vsak screen prek `ViewModelProvider` | Globalni `userProfile` StateFlow + sync overlay state |

### 4.2 Firestore kolekcije (shema)

| Kolekcija / Pot | Namen | Kdo piše |
|----------------|-------|----------|
| `users/{docId}` | Glavni profil (xp, streak_days, plan_day, badges…) | FirestoreHelper, UserProfileManager, AchievementStore |
| `users/{docId}/weightLogs` | Teža zgodovina | SaveWeightUseCase, Progress.kt |
| `users/{docId}/dailyLogs/{date}` | Dnevni vnos (calories, water, burnedCalories) | DailyLogRepository, DailySyncManager |
| `users/{docId}/publicActivities/{sessionId}` | Komprimirane GPS trase (shareActivities=true) | RunTrackerScreen (RouteCompressor) |
| `users/{docId}/gps_points` ali `points/` | GPS sub-kolekcija (GPS_POINTS migration) | (v delu — FirestoreWorkoutRepository) |
| `user_plans/{docId}` | 4-tedenski plan | PlanDataStore |
| `follows/{followId}` | Follow relacije | FollowStore |
| `custom_meals/{userId}/...` | Custom obroki | NutritionViewModel |
| `runSessions/{uid}/sessions` | Tek sesije | RunTrackerScreen, RunTrackerViewModel |
| `workoutSessions/{uid}/sessions` | Vadba sesije + focusAreas | UpdateBodyMetricsUseCase |

### 4.3 Kritični arhitekturni toki

```
VADBA ZAKLJUČENA:
WorkoutSessionScreen
  → BodyModuleHomeViewModel.completeWorkoutSession()
    → UpdateBodyMetricsUseCase (Firestore batch)
      ├── FirestoreWorkoutRepository.saveWorkoutSession()
      ├── UserProfileManager.updateUserProgressAfterWorkout()  [streak + planDay transakcija]
      ├── DailyLogRepository.updateDailyLog()  [burnedCalories]
      └── AchievementStore.recordWorkoutCompletion()  [XP + badge check]

TEK ZAKLJUČEN:
RunTrackerScreen
  → RunRouteStore (lokalno GPS točke — SharedPrefs)
  → Firestore runSessions
  → Firestore publicActivities (RouteCompressor.compress() → ~35 točk)
  → DailyLogRepository.updateDailyLog()  [burnedCalories — dodano Faza 15]

PROFIL SYNC (app open):
AppViewModel.startInitialSync()
  ├── Nova naprava: vzporeden async fetch (profil + plani + teže)
  └── Znana naprava: samo profil (Firestore cache topel)
  → _isProfileReady = true → overlay izgine
```

---

## 5. ZNANE ANOMALIJE (brez popravkov — samo dejstva)

| # | Datoteka | Tip | Opis |
|---|----------|-----|------|
| 1 | `network/ai_utils.kt` | ⚠️ Dead code | `requestAIPlan()` ni klicana nikjer; označena za ročno brisanje |
| 2 | `domain/nutrition/NutritionCalculations.kt` | ⚠️ Dead code | Prazna, DEPRECATED; aktivna logika je v `utils/NutritionCalculations.kt` |
| 3 | `ui/adapters/ChallengeAdapter.kt` | ⚠️ Dead code | RecyclerView stari View sistem; app je 100% Compose |
| 4 | `data/RunSession.kt` — `polylinePoints` | 🔴 Potencialni crash | Inline Firestore array → može preseči 1MB pri tekih >2h; migracija v `GPS_POINTS_MIGRATION_PLAN.md` |
| 5 | `health/HealthConnectManager.kt` | ℹ️ Alpha API | Health Connect `1.1.0-alpha08` — nestabilno za production |
| 6 | `ActivityLogScreen.kt` | ℹ️ Backlog | Ni paginacije za `markers` (map overlays) pri velikem številu |
| 7 | `data/settings/UserPreferencesRepository.kt` | ℹ️ TODO | `Context` odvisnost — blokira KMP iOS migracijo (potreben `expect/actual`) |
| 8 | `RunRouteStore.kt` | ℹ️ Arhitekturna razhajnost | GPS točke samo lokalno (SharedPreferences); ostale podatke piše Firestore |

---

## 6. POVZETEK — ARHITEKTURNI SLOJI

```
┌─────────────────────────────────────────────────────────────┐
│  UI SLOJ                                                     │
│  Compose Screens (ui/screens/, ui/home/) + ViewModels       │
│  AppViewModel (profil) + NavigationViewModel (navigacija)   │
└───────────────────┬─────────────────────────────────────────┘
                    │ kliče
┌───────────────────▼─────────────────────────────────────────┐
│  DOMAIN SLOJ                                                 │
│  Use Cases (domain/workout/, gamification/, metrics/, …)    │
│  Čista poslovna logika — brez Android API (razen Settings)  │
└───────────────────┬─────────────────────────────────────────┘
                    │ kliče
┌───────────────────▼─────────────────────────────────────────┐
│  DATA / PERSISTENCE SLOJ                                     │
│  Repository implementacije (data/**/*Impl.kt)               │
│  Stores (persistence/*.kt) — direktni Firestore klici       │
│  AchievementStore — SSOT XP/badge/streak                    │
│  FirestoreHelper — SSOT document resolution                 │
└─────────────────────────────────────────────────────────────┘
```

**Opomba:** Arhitektura ni striktna clean-architecture — nekatere UI datoteke (RunTrackerScreen, ManualExerciseLogScreen) kličejo repository/store sloj direktno brez use case posrednika. To je znan kompromis dokumentiran v CODE_ISSUES.md.

