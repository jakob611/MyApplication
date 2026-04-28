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

---

## 7. FIRESTORE DATA MODEL

> **Legenda tipov:** `String`, `Int`, `Long`, `Double`, `Float`, `Boolean`, `Array<T>`, `Map`, `Timestamp` (Firestore server timestamp), `EpochMs` (Long, epoch milliseconds), `EpochDays` (Long — DNI od 1970-01-01, NE millisekunde!)

> **Doc ID format:** `users/{docId}` kjer je `docId` = email (primarna pot) ali UID (fallback). Reši ga `FirestoreHelper.getCurrentUserDocRef()`.

---

### 7.1 Kolekcija: `users/{docId}`

Glavni profil — eden dokument na uporabnika.

#### 7.1.1 Identifikacija in osnova

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `username` | String | `""` | Prikazno ime | `UserProfileManager.saveProfileFirestore()` |
| `first_name` | String | `""` | Ime | `UserProfileManager.saveProfileFirestore()` |
| `last_name` | String | `""` | Priimek | `UserProfileManager.saveProfileFirestore()` |
| `address` | String | `""` | Naslov | `UserProfileManager.saveProfileFirestore()` |
| `profilePictureUrl` | String? | `null` | URL slike profila v Firebase Storage | `UserProfileManager.saveProfileFirestore()` |
| `darkMode` | Boolean | `false` | Temni način | `UserProfileManager.setDarkMode()` |

> ⚠️ **DATA MISMATCH:** `saveProfileFirestore()` piše s ključem `"profilePictureUrl"` (camelCase), toda `documentToUserProfile()` bere s `KEY_PROFILE_PICTURE = "profile_picture_url"` (snake_case). **Slika se nikoli ne naloži nazaj iz Firestore.**

#### 7.1.2 Gamifikacija

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `xp` | Int | `0` | Skupni XP — upravljano IZKLJUČNO prek transakcije | `FirestoreGamificationRepository.awardXP()` |
| `level` | Int | `1` | Izračunan nivel (= `UserProfile.calculateLevel(xp)`) — atomarno z `xp` | `FirestoreGamificationRepository.awardXP()` |
| `badges` | Array\<String\> | `[]` | Lista odklepljenih badge ID-jev (npr. `"first_workout"`) | `UserProfileManager.saveProfileFirestore()` |
| `streak_days` | Int | `0` | Streak v dneh — **kanonični ključ** | `UserProfileManager.updateUserProgressAfterWorkout()`, `saveWorkoutStats()` |
| `login_streak` | Int | `0` | ⚠️ **REDUNDANTNO** — isti podatek kot `streak_days`, piše `FirestoreGamificationRepository.updateStreak()` | `FirestoreGamificationRepository` |
| `streak_freezes` | Int | `0` | Število preostalih Streak Freeze uporab | `UserProfileManager.updateUserProgressAfterWorkout()`, `FirestoreGamificationRepository.consumeStreakFreeze()` |
| `last_workout_epoch` | EpochDays | `0` | Zadnji trening v **epochDays** (NE ms!) — za streak izračun | `UserProfileManager.updateUserProgressAfterWorkout()` |
| `last_login_date` | String | `null` | Datum zadnje prijave (`"yyyy-MM-dd"`) | `UserProfileManager.saveProfileFirestore()` |
| `last_streak_update_date` | String | `null` | Datum zadnje streak posodobitve (`"yyyy-MM-dd"`) | `FirestoreGamificationRepository.updateStreak()` |
| `total_workouts_completed` | Int | `0` | Skupaj zaključenih treningov | `UserProfileManager.saveWorkoutStats()` |
| `total_calories` | Double | `0.0` | Skupaj porabljenih kalorij skozi ves čas | `UserProfileManager.saveProfileFirestore()` |
| `early_bird_workouts` | Int | `0` | Treningi pred 7:00 | `UserProfileManager.saveProfileFirestore()` |
| `night_owl_workouts` | Int | `0` | Treningi po 21:00 | `UserProfileManager.saveProfileFirestore()` |
| `total_plans_created` | Int | `0` | Skupaj ustvarjenih planov | `UserProfileManager.saveProfileFirestore()` |

#### 7.1.3 Napredek in plan

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `plan_day` | Int | `1` | Aktualni dan v 4-tedenskem planu (1–28+) | `UserProfileManager.updateUserProgressAfterWorkout()`, `saveWorkoutStats()` |
| `weekly_done` | Int | `0` | Število treningov ta teden | `UserProfileManager.saveWorkoutStats()` |
| `weekly_target` | Int | `0` | Cilj treningov na teden (iz kviza: 2–6) | `UserProfileManager.saveWorkoutStats()` |

#### 7.1.4 Telesne metrike (iz kviza)

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `height` | Double | `null` | Višina v cm | `UserProfileManager.saveProfileFirestore()` |
| `age` | Int | `null` | Starost | `UserProfileManager.saveProfileFirestore()` |
| `gender` | String | `null` | `"Male"` ali `"Female"` | `UserProfileManager.saveProfileFirestore()` |
| `activityLevel` | String | `null` | `"2x"`, `"3x"`, `"4x"`, `"5x"`, `"6x"` | `UserProfileManager.saveProfileFirestore()` |
| `experience` | String | `null` | `"Beginner"`, `"Intermediate"`, `"Advanced"` | `UserProfileManager.saveProfileFirestore()` |
| `bodyFat` | String | `null` | Odstotek maščobe (npr. `"15-20%"`) | `UserProfileManager.saveProfileFirestore()` |
| `limitations` | Array\<String\> | `[]` | Telesne omejitve | `UserProfileManager.saveProfileFirestore()` |
| `nutritionStyle` | String | `null` | `"Standard"`, `"Vegetarian"`, `"Vegan"`, `"Keto/LCHF"`, `"Intermittent fasting"` | `UserProfileManager.saveProfileFirestore()` |
| `sleepHours` | String | `null` | `"Less than 6"`, `"6-7"`, `"7-8"`, `"8-9"`, `"9+"` | `UserProfileManager.saveProfileFirestore()` |
| `goalWeightKg` | Double | `null` | Ciljna teža v kg za Weight Destiny prediktor | `UserProfileManager.saveProfileFirestore()` |
| `workoutGoal` | String | `""` | Cilj treninga (`"Lose weight"`, `"Build muscle"`, ...) | `UserProfileManager.saveProfileFirestore()` |
| `focusAreas` | Array\<String\> | `[]` | Fokusna področja | `UserProfileManager.saveProfileFirestore()` |
| `equipment` | Array\<String\> | `[]` | Razpoložljiva oprema | `UserProfileManager.saveProfileFirestore()` |

#### 7.1.5 Socialno in zasebnost

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `followers` | Int | `0` | Število sledilcev — upravljano IZKLJUČNO prek transakcije | `FollowStore.followUser()` / `unfollowUser()` |
| `following` | Int | `0` | Število sledenih — upravljano IZKLJUČNO prek transakcije | `FollowStore.followUser()` / `unfollowUser()` |
| `is_public_profile` | Boolean | `false` | Javni profil omogočen | `ProfileStore.updatePrivacySettings()`, `UserProfileManager.saveProfileFirestore()` |
| `show_level` | Boolean | `false` | Pokaži level na javnem profilu | ProfileStore + UserProfileManager |
| `show_badges` | Boolean | `false` | Pokaži badge-e na javnem profilu | ProfileStore + UserProfileManager |
| `show_streak` | Boolean | `false` | Pokaži streak na javnem profilu | ProfileStore + UserProfileManager |
| `show_plan_path` | Boolean | `false` | Pokaži plan path na javnem profilu | ProfileStore + UserProfileManager |
| `show_challenges` | Boolean | `false` | Pokaži izzive na javnem profilu | ProfileStore + UserProfileManager |
| `show_followers` | Boolean | `false` | Pokaži followers/following na javnem profilu | ProfileStore + UserProfileManager |
| `share_activities` | Boolean | `false` | Deli GPS aktivnosti s skupnostjo | ProfileStore + UserProfileManager |

#### 7.1.6 Nastavitve in preferenčne

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `weight_unit` | String | `"kg"` | Enota teže | `UserProfileManager.saveProfileFirestore()` |
| `speed_unit` | String | `"km/h"` | Enota hitrosti | `UserProfileManager.saveProfileFirestore()` |
| `start_of_week` | String | `"Monday"` | Začetek tedna | `UserProfileManager.saveProfileFirestore()` |
| `quiet_hours_start` | String | `"22:00"` | Začetek tihega časa za obvestila | `UserProfileManager.saveProfileFirestore()` |
| `quiet_hours_end` | String | `"07:00"` | Konec tihega časa | `UserProfileManager.saveProfileFirestore()` |
| `mute_streak_reminders` | Boolean | `false` | Utišaj streak opomnik | `UserProfileManager.saveProfileFirestore()` |
| `detailed_calories` | Boolean | `false` | Segmentiran prikaz kalorij (fat/protein/carbs) | `UserProfileManager.saveProfileFirestore()` |

---

### 7.2 Sub-kolekcija: `users/{docId}/dailyLogs/{date}`

`{date}` = `"yyyy-MM-dd"` (npr. `"2026-04-28"`). En dokument na dan.

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `date` | String | `{date}` | Datum v formatu `"yyyy-MM-dd"` | `DailyLogRepository.updateDailyLog()`, `FoodRepositoryImpl.logFood()` |
| `burnedCalories` | Double | `0.0` | Skupaj porabljenih kalorij (vsote vseh aktivnosti) | `DailyLogRepository.updateDailyLog()` ← RunTrackerScreen, ManualExerciseLogScreen, ManageGamificationUseCase |
| `waterMl` | Int | `0` | Zaužita voda v ml | `FoodRepositoryImpl.logWater()` prek `NutritionViewModel.updateWaterOptimistic()` |
| `consumedCalories` | Double | `0.0` | Skupaj zaužite kalorije iz hrane | `FoodRepositoryImpl.logFood()` |
| `items` | Array\<Map\> | `[]` | Lista vnesene hrane (glej 7.2.1) | `FoodRepositoryImpl.logFood()` (arrayUnion) |
| `updatedAt` | Timestamp | server | Čas zadnje posodobitve | `DailyLogRepository`, `FoodRepositoryImpl` |

#### 7.2.1 Struktura posameznega food item v `items[]`

| Polje | Tip | Opis |
|-------|-----|------|
| `name` | String | Ime živila |
| `caloriesKcal` | Double | Kalorije |
| `protein` | Double | Beljakovine v g |
| `carbs` | Double | Ogljikovi hidrati v g |
| `fat` | Double | Maščobe v g |
| `mealType` | String | `"Breakfast"`, `"Lunch"`, `"Dinner"`, `"Snacks"` |
| `quantity` | Double | Količina (v g ali ml) |
| `unit` | String | Enota (`"g"`, `"ml"`) |
| `timestamp` | EpochMs | Čas vnosa |

---

### 7.3 Sub-kolekcija: `users/{docId}/runSessions/{sessionId}`

Celotna tek/aktivnost sesija. `{sessionId}` = Firestore auto-generated ID.

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `id` | String | `""` | Interni ID (= doc ID) | `RunSession.toFirestoreMap()` |
| `userId` | String | `""` | User doc ID | `RunSession.toFirestoreMap()` |
| `startTime` | EpochMs | `0` | Čas začetka aktivnosti (ms) | `RunSession.toFirestoreMap()` |
| `endTime` | EpochMs | `0` | Čas konca aktivnosti (ms) | `RunSession.toFirestoreMap()` |
| `durationSeconds` | Int | `0` | Trajanje v sekundah | `RunSession.toFirestoreMap()` |
| `distanceMeters` | Double | `0.0` | Razdalja v metrih | `RunSession.toFirestoreMap()` |
| `avgSpeedMps` | Float | `0.0` | Povprečna hitrost m/s | `RunSession.toFirestoreMap()` |
| `maxSpeedMps` | Float | `0.0` | Maksimalna hitrost m/s | `RunSession.toFirestoreMap()` |
| `caloriesKcal` | Int | `0` | Porabljene kalorije | `RunSession.toFirestoreMap()` |
| `elevationGainM` | Float | `0.0` | Skupni vzpon v metrih | `RunSession.toFirestoreMap()` |
| `elevationLossM` | Float | `0.0` | Skupni spust v metrih | `RunSession.toFirestoreMap()` |
| `activityType` | String | `"RUN"` | Enum name: `"RUN"`, `"WALK"`, `"HIKE"`, `"SPRINT"`, `"CYCLING"`, `"SKIING"`, `"SNOWBOARD"`, `"SKATING"`, `"NORDIC"` | `RunSession.toFirestoreMap()` |
| `isSmoothed` | Boolean | `false` | Ali so GPS točke glajene | `RunSession.toFirestoreMap()` |
| `createdAt` | EpochMs | now | Čas ustvarjanja dokumenta | `RunSession.toFirestoreMap()` |
| `polylinePoints` | Array\<Map\> | `[]` | ⚠️ **STARI FORMAT** — inline GPS točke (potential 1MB crash pri tekih >2h) | `RunSession.toFirestoreMap()` |

#### 7.3.1 GPS točke — `polylinePoints[]` (STARI inline format)

| Polje | Tip | Opis |
|-------|-----|------|
| `latitude` | Double | Geografska širina |
| `longitude` | Double | Geografska dolžina |
| `altitude` | Double | Nadmorska višina (m) |
| `speed` | Float | Hitrost (m/s) |
| `accuracy` | Float | GPS natančnost (m) |
| `timestamp` | EpochMs | Čas točke |

#### 7.3.2 GPS točke — `gps_points/{chunk}` ali `points/{chunk}` (NOVI sub-format)

| Polje | Tip | Opis |
|-------|-----|------|
| `chunkIndex` | Int | Vrstni red chunka (za orderBy) |
| `pts` | Array\<Map\> | Komprimirane točke |
| `pts[].lat` | Double | Geografska širina (**kratica!**) |
| `pts[].lng` | Double | Geografska dolžina (**kratica!**) |
| `pts[].alt` | Double | Nadmorska višina |
| `pts[].spd` | Float | Hitrost |
| `pts[].ts` | EpochMs | Čas točke |

> ⚠️ **KLJUČNA RAZLIKA:** Inline format uporablja `latitude`/`longitude`, sub-kolekcija pa `lat`/`lng`. `FirestoreWorkoutRepository` podpira oba formata z fallback logiko.

---

### 7.4 Sub-kolekcija: `users/{docId}/publicActivities/{sessionId}`

Komprimirana javna aktivnost za prikaz na javnem profilu (samo če `share_activities = true`). GPS točke so stisnjene z **RDP algoritmom** (~450 → ~35 točk).

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `activityType` | String | `"RUN"` | Tip aktivnosti (enako kot runSessions) | `RunTrackerScreen` |
| `distanceMeters` | Double | `0.0` | Razdalja v metrih | `RunTrackerScreen` |
| `durationSeconds` | Int | `0` | Trajanje v sekundah | `RunTrackerScreen` |
| `caloriesKcal` | Int | `0` | Porabljene kalorije | `RunTrackerScreen` |
| `elevationGainM` | Float | `0.0` | Vzpon v metrih | `RunTrackerScreen` |
| `elevationLossM` | Float | `0.0` | Spust v metrih | `RunTrackerScreen` |
| `avgSpeedMps` | Float | `0.0` | Povprečna hitrost m/s | `RunTrackerScreen` |
| `maxSpeedMps` | Float | `0.0` | Maksimalna hitrost m/s | `RunTrackerScreen` |
| `startTime` | EpochMs | `0` | Čas začetka | `RunTrackerScreen` |
| `routePoints` | Array\<Map\> | `[]` | Komprimirane GPS točke (RDP, ~35 točk) | `RunTrackerScreen` + `RouteCompressor.compress()` |
| `routePoints[].lat` | Double | — | Geografska širina (**kratica** `lat`, ne `latitude`) | `RunTrackerScreen` |
| `routePoints[].lng` | Double | — | Geografska dolžina (**kratica** `lng`, ne `longitude`) | `RunTrackerScreen` |

---

### 7.5 Sub-kolekcija: `users/{docId}/workoutSessions/{sessionId}`

Zaključena vadba sesija.

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `timestamp` | EpochMs | now | Čas zaključka vadbe (**NOVI format**) | `UpdateBodyMetricsUseCase` |
| `date` | Timestamp | — | Firestore Timestamp (**STARI format** — samo v starih dokumentih) | stari `WorkoutSessionScreen` |
| `type` | String | `"regular"` | `"regular"` ali `"extra"` | `UpdateBodyMetricsUseCase` |
| `totalKcal` | Int | `0` | Porabljene kalorije pri vadbi | `UpdateBodyMetricsUseCase` |
| `totalTimeMin` | Double | `0.0` | Trajanje vadbe v minutah | `UpdateBodyMetricsUseCase` |
| `exercisesCount` | Int | `0` | Število vaj v vadbi | `UpdateBodyMetricsUseCase` |
| `planDay` | Int | `1` | Plan dan te vadbe | `UpdateBodyMetricsUseCase` |
| `focusAreas` | Array\<String\> | `[]` | Fokusna področja (za progressive overload) | `UpdateBodyMetricsUseCase` |
| `exercises` | Array\<Map\> | `[]` | ExerciseResult-i (seznam telesnih vaj z reps/sets/weightKg) | `UpdateBodyMetricsUseCase` |

#### 7.5.1 Struktura posameznega exercise v `exercises[]`

| Polje | Tip | Opis |
|-------|-----|------|
| `name` | String | Ime vaje |
| `reps` | Int | Število ponovitev |
| `sets` | Int | Število serij |
| `weightKg` | Float | Teža v kg (0 = bodyweight) |

> ⚠️ **TIMESTAMP NESKLADJE:** `getWeeklyDoneCount()` query-ja po polju `"date"` (Firestore Timestamp), toda `UpdateBodyMetricsUseCase` piše samo `"timestamp"` (Long). Stari dokumenti imajo `date`, novi pa `timestamp`. Poizvedba ne najde novih dokumentov.

---

### 7.6 Sub-kolekcija: `users/{docId}/xp_history/{autoId}`

XP log vsake podelitve. Uporablja se za AchievementsScreen prikaz.

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `amount` | Int | — | Količina podeljenih XP | `FirestoreGamificationRepository.awardXP()` |
| `reason` | String | — | Vzrok (`"workout_complete"`, `"daily_login"`, ...) | `FirestoreGamificationRepository.awardXP()` |
| `date` | String | today | Datum (`"yyyy-MM-dd"`) | `FirestoreGamificationRepository.awardXP()` |
| `timestamp` | EpochMs | now | Čas podelitve (epoch ms) | `FirestoreGamificationRepository.awardXP()` |
| `xpAfter` | Int | — | XP po podelitvi | `FirestoreGamificationRepository.awardXP()` |
| `levelAfter` | Int | — | Level po podelitvi | `FirestoreGamificationRepository.awardXP()` |

---

### 7.7 Sub-kolekcija: `users/{docId}/weightLogs/{autoId}`

Teža skozi čas.

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `date` | Timestamp | — | Datum meritve (Firestore Timestamp, za orderBy) | `SaveWeightUseCase` / `Progress.kt` |
| `weightKg` | Double | — | Teža v kg | `SaveWeightUseCase` / `Progress.kt` |

---

### 7.8 Sub-kolekcija: `users/{docId}/customMeals/{mealId}`

Custom obroki (ustvarjeni z MakeCustomMealsDialog).

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `name` | String | — | Ime custom obroka | `FoodRepositoryImpl.logCustomMeal()` |
| `items` | Array\<Any\> | `[]` | Seznam sestavin (food item mape) | `FoodRepositoryImpl.logCustomMeal()` |
| `createdAt` | Timestamp | server | Čas ustvarjanja | `FoodRepositoryImpl.logCustomMeal()` |

> ⚠️ **OPOMBA:** Pot je `users/{docId}/customMeals` (v kodi `"customMeals"`), toda v `deleteUserData()` se izbriše `"customMeals"`. V `UserProfileManager.deleteUserData()` se pojavlja tudi `"meal_feedback"` — ta kolekcija ni dokumentirana drugje.

---

### 7.9 Sub-kolekcija: `users/{docId}/daily_logs/{date}` ⚠️

**POZOR:** To je RAZLIČNA kolekcija od `dailyLogs`!  
`daily_logs` (snake_case) piše `FirestoreGamificationRepository.updateStreak()` — stara streaklogika.  
`dailyLogs` (camelCase) piše `FoodRepositoryImpl` in `DailyLogRepository` — nova hrana/voda logika.

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `date` | String | `{date}` | Datum (`"yyyy-MM-dd"`) | `FirestoreGamificationRepository` |
| `status` | String | — | `"WORKOUT_DONE"`, `"REST_DONE"`, `"FROZEN"`, `"REST_SWAPPED"` | `FirestoreGamificationRepository` |
| `timestamp` | EpochMs | now | Čas vpisa | `FirestoreGamificationRepository` |

---

### 7.10 Kolekcija: `user_plans/{docId}`

En dokument na uporabnika. Vsi plani shranjeni kot polje `plans: Array`.

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `plans` | Array\<Map\> | `[]` | Lista vseh shranjenih planov | `PlanDataStore.savePlans()` |

#### 7.10.1 Struktura PlanResult v `plans[]`

| Polje | Tip | Opis |
|-------|-----|------|
| `id` | String | UUID plana |
| `name` | String | Ime plana |
| `calories` | Int | Dnevni kalorični cilj |
| `protein` | Int | Dnevni protein cilj (g) |
| `carbs` | Int | Dnevni ogljikohidratni cilj (g) |
| `fat` | Int | Dnevni maščobni cilj (g) |
| `trainingPlan` | String | Opis plana (tekst) |
| `trainingDays` | Int | Število treningov na teden |
| `sessionLength` | Int | Dolžina sesije v minutah |
| `tips` | Array\<String\> | Nasveti za plan |
| `createdAt` | EpochMs | Čas ustvarjanja plana |
| `trainingLocation` | String | `"Home"`, `"Gym"`, `"Outdoor"` |
| `experience` | String? | `"Beginner"`, `"Intermediate"`, `"Advanced"` |
| `goal` | String? | Cilj treninga |
| `startDate` | String | Datum začetka plana (`"yyyy-MM-dd"`) |
| `focusAreas` | Array\<String\> | Fokusne mišične skupine |
| `equipment` | Array\<String\> | Oprema |
| `weeks` | Array\<Map\> | 4 tedni (WeekPlan) |
| `weeks[].weekNumber` | Int | Številka tedna (1–4) |
| `weeks[].days` | Array\<Map\> | Dnevi v tednu |
| `weeks[].days[].dayNumber` | Int | Številka dne (1–28+) |
| `weeks[].days[].exercises` | Array\<String\> | Imena vaj |
| `weeks[].days[].isRestDay` | Boolean | Ali je to počitniški dan |
| `weeks[].days[].focusLabel` | String | Label fokusa (npr. `"Upper Body"`) |
| `weeks[].days[].isSwapped` | Boolean | Ali je dan bil auto-swapan (opcijsko) |
| `algorithmData` | Map? | Debug BMI/BMR/TDEE podatki (opcijsko) |
| `algorithmData.bmi` | Double | BMI vrednost |
| `algorithmData.bmr` | Double | Bazalni metabolizem |
| `algorithmData.tdee` | Double | Skupna dnevna poraba energije |

---

### 7.11 Kolekcija: `follows/{followerId}_{followingId}`

Doc ID je **deterministični** format `"{followerId}_{followingId}"` — prepreči dvojno sledenje.

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `followerId` | String | — | Doc ID sledilca (resolvedId) | `FollowStore.followUser()` |
| `followingId` | String | — | Doc ID sledene osebe | `FollowStore.followUser()` |
| `followedAt` | Timestamp | server | Čas sledenja | `FollowStore.followUser()` |

> ℹ️ Stari dokumenti (pred implementacijo determinističnega ID) imajo naključni auto-generated ID, toda iste `followerId`/`followingId` vrednosti v poljih. `unfollowUser()` podpira oba formata z fallback query.

---

### 7.12 Kolekcija: `notifications/{userId}/items/{autoId}`

Obvestila (zaenkrat samo "new_follower").

| Polje | Tip | Default | Opis | Piše |
|-------|-----|---------|------|------|
| `type` | String | — | Tip obvestila (npr. `"new_follower"`) | `FollowStore.followUser()` |
| `fromUserId` | String | — | Kdo je sprožil obvestilo | `FollowStore.followUser()` |
| `message` | String | — | Besedilo obvestila | `FollowStore.followUser()` |
| `timestamp` | Timestamp | server | Čas obvestila | `FollowStore.followUser()` |
| `read` | Boolean | `false` | Ali je obvestilo prebrano | `FollowStore.followUser()` |

---

## 8. AUTH & SYNC FLOW

> **Faza 3 — samo dejstva. Nobenih popravkov.**  
> Vsi vrstični sklici se nanašajo na `MainActivity.kt` (skupaj 985 vrstic).

---

### 8.1 Sequence Map — Celoten zagon od "Login klika" do "podatki naloženi"

#### 8.1.1 Hladen zagon (app nikoli ni bil odprt)

```
T+0ms  — MainActivity.onCreate()
         ├── coldStartEpochMs = elapsedRealtime()
         ├── GlobalScope.launch(IO): AdvancedExerciseRepository.init()  [JSON vaje, asinhrono]
         └── setContent { ... }

T+~5ms — Compose composition začne

T+~10ms — LaunchedEffect(Unit) #1  [vrstica 177]
          ├── AppIntent.SetProfile(loadProfile(""))  ← PRAZEN email! Naloži prazen profil iz local
          ├── Firebase.auth.currentUser?.email  → null za nov zagon
          ├── fresh_start_on_login flag check + bm_prefs clear (če nastavljen)
          ├── Faza5 legacy cache clear (one-time migration)
          └── isCheckingAuth = false  ← UI preide iz "auth spinner" v pravo vsebino

T+~10ms — DisposableEffect(Unit)  [vrstica 259]
          └── auth.addAuthStateListener { fbUser →
                  if (verified) → scope.launch { appViewModel.startInitialSync() }
                  else          → appViewModel.resetSyncState()
              }
          ⚡ TAKOJ sproži authListener enkrat z obstoječim auth stanjem!

T+~10ms — LaunchedEffect(Unit) #2  [vrstica 277]
          ├── Firebase.auth.currentUser == null → navViewModel.navigateTo(Screen.Index)
          └── isCheckingAuth = false  (že false iz #1 — podvojeno)

T+~10ms — LaunchedEffect(isLoggedIn)  [vrstica 337]
          └── if (isLoggedIn) → StreakReminderWorker.scheduleForToday()
              (isLoggedIn == false → nič)

         ── UI prikaže Screen.Index (splash) → Screen.Login → Login uspešen ─────────────────
```

---

#### 8.1.2 Email Login (user klikne "Login" v LoginScreen)

```
[User tip]
Firebase.auth.signInWithEmailAndPassword(email, password)
  └── .addOnCompleteListener { task →
        if (success && email verified):
          isLoggedIn = true
          userEmail = email
          AppIntent.SetProfile(loadProfile(email))  ← LOCAL profil
          scope.launch { isDarkMode = isDarkMode(email) }  ← Firestore klic!
          navViewModel.navigateTo(Screen.Dashboard)
      }

[AUTH STATE CHANGE sproži DisposableEffect authListener]
  └── scope.launch {
        appViewModel.startInitialSync(context, email)  ← ASYNC sync začne
      }

[LaunchedEffect(isLoggedIn) se sproži ker isLoggedIn → true]
  └── StreakReminderWorker.scheduleForToday(context)

         ── UI prikaže Screen.Dashboard + sync overlay (isProfileReady=false) ──
```

---

#### 8.1.3 Google Sign-In (user klikne "Sign in with Google")

```
[User tap] → googleSignInLauncher.launch(signInIntent)
[Google Activity vrne result] → googleSignInLauncher callback
  └── firebaseAuthWithGoogle(account, onSuccess, onError)
        └── Firebase.auth.signInWithCredential(credential)
              └── onSuccess:
                    isLoggedIn = true
                    userEmail = user.email
                    AppIntent.SetProfile(loadProfile(email))
                    scope.launch { isDarkMode = isDarkMode(email) }
                    navViewModel.navigateTo(Screen.Dashboard)

[AUTH STATE sproži DisposableEffect authListener]  ← isto kot Email Login
  └── scope.launch { appViewModel.startInitialSync(context, email) }
```

---

#### 8.1.4 AppViewModel.startInitialSync() — Podroben tok

```
startInitialSync(context, userEmail):

GUARD 1: if (isSyncStarted) return  ← session-lifetime boolean (ne StateFlow)
GUARD 2: if (_isSyncing.value) return  ← coroutine-lifetime StateFlow

isSyncStarted = true
_isSyncing.value = true

viewModelScope.launch {
  try {
    ├── getCurrentUserDocId()  ← sinhrono, iz Firebase.auth
    ├── syncPrefs.getBoolean("initial_sync_done_{uid}", false)
    │
    ├── IF needsInitialSync (nova naprava):
    │   ├── _syncStatusMessage = "Downloading your fitness profile..."
    │   └── withContext(IO):
    │       ╠══ async { UserProfileManager.loadProfileFromFirestore(email) }
    │       ╠══ async { db.collection("user_plans").document(uid).get() }
    │       ╠══ async { userRef.collection("weightLogs").limit(10).get() }
    │       │     ↑ VSI TRIJE TEČEJO VZPOREDNO
    │       ├── profileDeferred.await()  ← čaka na profil
    │       ├── plansDeferred.await()    ← čaka na plane (segreje cache)
    │       ├── weightDeferred.await()   ← čaka na teže (segreje cache)
    │       │
    │       ├── if (remote != null):
    │       │   ├── UserProfileManager.saveProfile(remote)  ← local save
    │       │   ├── bm_prefs.weekly_target = actParsed  ← SharedPrefs zapis!
    │       │   └── withContext(Main) { _userProfile.value = remote }
    │       │
    │       ├── syncPrefs.putBoolean("initial_sync_done_{uid}", true)
    │       ├── _syncStatusMessage = "Profile Ready! ✓"
    │       └── delay(1500)  ← 1.5s prikaz sporočila
    │
    └── IF !needsInitialSync (znana naprava):
        └── withContext(IO):
            ├── UserProfileManager.loadProfileFromFirestore(email)
            ├── saveProfile(remote)
            ├── bm_prefs.weekly_target = actParsed
            └── _userProfile.value = remote

  } catch (e: Exception) {
    Log.e(...)  ← NAPAKA TIHO ZALOGIRA, NE SPOROČI UI-ju
  } finally {
    _isSyncing.value = false
    _isProfileReady.value = true  ← VEDNO se postavi na true (tudi pri napaki!)
  }
}
```

---

#### 8.1.5 Vzporedni procesi ob zaloginosti — LaunchedEffect(Unit) #2 [vrstica 277]

Ko `Firebase.auth.currentUser != null`, se **HKRATI** (neodvisno od startInitialSync) izvajajo:

```
scope.launch(IO) {
  useCase.recordLoginOnly()  ← XP za dnevno prijavo + streak posodobitev
}

scope.launch(IO) {
  WeeklyStreakWorker.ensureScheduled(context, profile.startOfWeek)
}

scope.launch(IO) {
  RunRouteCleanupWorker.ensureScheduled(context)
}

scope.launch(IO) {
  delay(1500)
  useCase.checkAndSyncBadgesOnStartup()  ← batch badge preverjanje po 1.5s
}

scope.launch {
  PlanDataStore.migrateLocalPlansToFirestore(context)  ← enkratna migracija
  StreakWidgetProvider.refreshAll(context)
  PlanDayWidgetProvider.refreshAll(context)
  DailySyncManager.syncOnAppOpen(context, uid)  ← sproži DailySyncWorker
}

appViewModel.handleIntent(AppIntent.StartListening(userEmail))  ← Firestore listener
```

---

### 8.2 Concurrency (Vzporednost)

#### 8.2.1 Vzporedno (simultano)

| Operacija A | Operacija B | Razmerje |
|-------------|-------------|----------|
| `startInitialSync` (async profil+plani+teže) | `recordLoginOnly` (XP/streak) | ⚡ Vzporedno — oba tečeta neodvisno |
| `startInitialSync` (async fetch) | `PlanDataStore.migrateLocalPlansToFirestore` | ⚡ Vzporedno — nista sinhronizirana |
| `startInitialSync` | `checkAndSyncBadgesOnStartup` (po 1500ms) | ⚡ Vzporedno — badge sync ne čaka na sync |
| `profileDeferred.await()` | `plansDeferred.await()` | ⚡ Vzporedno (oba `async`) |
| `plansDeferred.await()` | `weightDeferred.await()` | ⚡ Vzporedno |
| `AppIntent.StartListening` listener | celoten sync | ⚡ Vzporedno — listener se registrira med syncanjem |

#### 8.2.2 Zaporedno (mora čakati)

| Operacija | Čaka na |
|-----------|---------|
| `navViewModel.navigateTo(Screen.Dashboard)` | `isLoggedIn = true` (isti signin callback) |
| `bodyOverviewViewModel.refreshPlans()` | `isProfileReady == true` (LaunchedEffect(isProfileReady)) |
| Sync overlay izgine (`isProfileReady = true`) | `finally` blok v `startInitialSync` |
| `isDarkMode` naložen | ViewModel compose klic (`scope.launch { isDarkMode = ... }`) — po Dashboard navigaciji |

---

### 8.3 The Loop Analysis — Ali isSyncing postane `false` v vseh primerih?

#### Test 1: Normalen flow (internet OK)
```
startInitialSync() → finally { _isSyncing=false, _isProfileReady=true }  ✅ OK
```

#### Test 2: Firestore vrne napako (internet OK, napaka na Firestore)
```
startInitialSync() → try { ... loadProfileFromFirestore() throws Exception }
                  → catch(e) { Log.e(...) }   ← napaka TIHO zalogirana
                  → finally { _isSyncing=false, _isProfileReady=true }  ✅ OK
                  Rezultat: overlay izgine, profil ostane prazen/lokalen
```

#### Test 3: Brez interneta, znana naprava (Firestore 100MB cache topel)
```
startInitialSync() → loadProfileFromFirestore() vrne iz Firestore OFFLINE CACHE
                     (ker PersistentCacheSettings = 100MB v MyApplication.onCreate)
                  → finally { _isSyncing=false, _isProfileReady=true }  ✅ OK
                  Rezultat: profil naložen iz cache, overlay izgine hitro
```

#### Test 4: Brez interneta, NOVA naprava (nič v Firestore cache)
```
startInitialSync() → loadProfileFromFirestore() → Firestore SDK throws
                     FirebaseFirestoreException(UNAVAILABLE) po timeoutu
                  → catch(e) { Log.e(...) }
                  → finally { _isSyncing=false, _isProfileReady=true }  ✅ OK, a počasi
                  
  ⚠️ PROBLEM: Overlay lahko prikazan do ~60 sekund!
  Uporabnik vidi spinner 1 minuto brez popravila da ni interneta.
  syncStatusMessage ostane "Syncing your fitness data…" ves čas.
  NetworkObserver (isOnline StateFlow) obstaja v MainActivity, a ni
  integriran v startInitialSync — ne more prekiniti čakanja.
```

#### Test 5: Dvojni klic startInitialSync (authListener + drugi trigger)
```
1. authListener sproži scope.launch { startInitialSync() } → isSyncStarted=true, začne
2. authListener se morda sproži znova (auth refresh) → startInitialSync() → GUARD: return takoj  ✅ OK
   Kljub temu scope.launch { } ustvari novo corutino ki se takoj vrne — minimalen overhead.
```

#### Test 6: Rotacija zaslona (configuration change)
```
ViewModel preživi configuration change → isSyncStarted=true ostane
AppViewModel.startInitialSync() NE bo poklican znova → overlay stanje ohranjeno  ✅ OK
(DisposableEffect se resetira → authListener se doda znova → sproži startInitialSync()
 → GUARD isSyncStarted=true → return takoj)
```

#### Test 7: Odjava in ponovna prijava v isti seji
```
Odjava: appViewModel.resetSyncState() → isSyncStarted=false, _isProfileReady=false
Prijava: authListener → startInitialSync() → isSyncStarted=false → NOVA sinhronizacija  ✅ OK
```

**Zaključek:** `_isProfileReady` postane `true` v **vseh** primerih (finally blok je dosežen). Edina anomalija je **Test 4** — brez interneta na novi napravi overlay čaka do ~60 sekund.

---

### 8.4 Observer Patterns — Kdo posluša `userProfile` StateFlow

#### 8.4.1 Direktni opazovalci

| Opazovalec | Lokacija | Kaj naredi ob spremembi |
|------------|----------|------------------------|
| `val userProfile by appViewModel.userProfile.collectAsState()` | `MainActivity.kt` vrstica 123 | Recompose — posreduje `userProfile` v screene |

#### 8.4.2 Screeni ki sprejemajo `userProfile` kot parameter

| Screen | Parameter | Trigira sync? |
|--------|-----------|---------------|
| `DashboardScreen` | — (ne sprejme userProfile) | ❌ Ne |
| `NutritionScreen` | `userProfile = userProfile` | ❌ Ne — samo prikaže |
| `ProgressScreen` | `userProfile = userProfile` | ❌ Ne — samo prikaže |
| `AchievementsScreen` | `userProfile = userProfile` | ❌ Ne — samo prikaže |
| `MyAccountScreen` | `userProfile = userProfile` | ❌ Ne — samo prikaže |
| `DeveloperSettingsScreen` | `userProfile = userProfile` | ❌ Ne |

#### 8.4.3 Callback-i ki posodobijo `userProfile` (potencialne zanke)

| Callback | Lokacija | Kaj naredi |
|----------|----------|------------|
| `onXPAdded` (WorkoutSession) | `AppIntent.SetProfile(loadProfile(userEmail))` | Naloži LOCAL profil (ne Firestore) — brez omrežnega klica — ❌ ni zanke |
| `onBadgeUnlocked` (WorkoutSession) | `AppIntent.SetProfile(loadProfile(userEmail))` | Isto — LOCAL — ❌ ni zanke |
| `onXPAdded` (NutritionScreen) | `AppIntent.SetProfile(loadProfile(userEmail))` | LOCAL — ❌ ni zanke |
| `onProfileUpdate` (Drawer) | `AppIntent.SetProfile(updatedProfile)` + `saveProfileFirestore()` | Piše v Firestore → Firestore listener sproži `_userProfile.update` → UI recomposes → ❌ NI zanke (samo prikaz, brez ponovnega pisanja) |
| `AppIntent.StartListening` | `ObserveUserProfileUseCase` → Firestore snapshot | Ob vsaki Firestore spremembi → `_userProfile.value = nova vrednost` → UI recompose → ❌ NI zanke |

**Zaključek:** Nobena sprememba `userProfile` ne sproži ponovnega klica `startInitialSync`. **Sink Loop (pisanje iz observerja) ne obstaja.**

---

### 8.5 Problematični vzorci — "Kaotično stanje"

#### 8.5.1 DVA `LaunchedEffect(Unit)` bloka v istem composable-u

```kotlin
// Vrstica 177:
LaunchedEffect(Unit) {
    // ... fresh_start check, local load, FIRST isCheckingAuth=false ...
}

// Vrstica 277:
LaunchedEffect(Unit) {
    // ... Firebase.auth check, navigacija, workers scheduling, SECOND isCheckingAuth=false ...
}
```

**Dejstvo:** Oba se izvajta neodvisno. Compose jih obravnava kot dva ločena effect bloka na isti composable poziciji (oba imata isti key `Unit` a **različno vrstno pozicijo** v composition tree). Oba korutinata **tečeta vzporedno**.

**Posledice:**
| Operacija | Blok 1 (vrstica 177) | Blok 2 (vrstica 277) |
|-----------|---------------------|---------------------|
| `AppIntent.SetProfile(loadProfile(email))` | ✅ Kliče | ✅ Kliče (podvojeno) |
| `UserProfileManager.isDarkMode(email)` | ✅ Kliče | ✅ Kliče (podvojeno Firestore get) |
| `checkAndSyncBadgesOnStartup()` | ✅ Takoj | ✅ Po 1500ms delay |
| `PlanDataStore.migrateLocalPlansToFirestore` | ✅ Kliče | ✅ Kliče (podvojeno) |
| `AppIntent.StartListening` listener | ✅ Vrstica 241 | ✅ Vrstica 329 (guard v AppViewModel prepreči dvojni listener) |
| `isCheckingAuth = false` | ✅ Vrstica 243 | ✅ Vrstica 333 (redundantno) |

#### 8.5.2 Podvojeni Firestore klic za profil pri avtologinu

Ko je user že prijavljen (hot start), se profil naloži **TRIKRAT**:
1. `loadProfile(email)` (LOCAL, takoj) → vrstica 178 in 210
2. `GlobalScope.launch { loadProfileFromFirestore(email) }` → vrstica 225
3. `startInitialSync` (via authListener) → kliče `loadProfileFromFirestore(email)` znova

**Skupaj: 2 lokalna + 2 Firestore fetch-a** pri vsakem hladnem zagonu z obstoječo prijavo.

#### 8.5.3 Google Sign-In

```kotlin
kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
    val remote = UserProfileManager.loadProfileFromFirestore(userEmail)
    // ... saveProfile, bm_prefs, _userProfile.value = remote
}
```

**Dejstvo:** `GlobalScope` ni vezan na lifecycle — korutina preživi navigacijo, rotacijo, onPause. Ni mehanizma za cancel. Hkrati `startInitialSync` (ki teče VZPOREDNO) dela isto stvar. Race condition med `GlobalScope` in `startInitialSync`: eden od njiju zadnji prepiše `_userProfile.value`.

#### 8.5.4 isCheckingAuth vs isProfileReady — prekrivanje

```
Stanje na zalogiranem hladnem zagonu:

T+0ms:   isCheckingAuth=true  → prikaže isCheckingAuth spinner (Modifier.fillMaxSize)
T+~15ms: isCheckingAuth=false → spinner izgine
T+~15ms: isLoggedIn=true, isProfileReady=false → prikaže sync overlay (0.80 alpha Box)
...
T+~1800ms: isProfileReady=true (po sync + 1500ms delay) → overlay izgine

PROBLEM: Med T=0 in T=15ms sta HKRATI aktiva isCheckingAuth spinner IN sync overlay (ker isLoggedIn
postane true znotraj istega LaunchedEffect ki postavi isCheckingAuth=false).
V praksi to ni vidno (~15ms), a je arhitekturno nepravilno.
```

---

### 8.6 Offline Behavior — Povzetek

| Scenarij | Rezultat |
|---------|---|
| Internet OK, znana naprava | ✅ Normalno |
| Internet OK, nova naprava | ✅ Normalno |
| Offline, znana naprava (Firestore 100MB cache topel) | ✅ Brez interneta |
| Offline, nova naprava (nič v cache) | ⚠️ Dolgo čakanje, nato prazen prif |
| Firestore napaka (auth OK, Firestore down) | ⚠️ Isto kot zgoraj |

**Ključna zaščita:** `finally { _isProfileReady.value = true }` garantira, da overlay **vedno** izgine — aplikacija **nikoli ne obvisi** za vedno.

**Manjkajoča zaščita:** `NetworkObserver` (ki obstaja v MainActivity kot `isOnline`) **ni integriran** v `startInitialSync`. Ob offline stanju bi lahko takoj prikazali sporočilo "No internet" in nastavili `_isProfileReady = true` brez ~60s čakanja.

---

## 9. NUTRITION & DIET LOGIC

> **Faza 5 od 10** — Dokumentacija prehranskega modula. Brez popravkov.

---

### 9.1 Food Search Flow

Iskanje hrane deluje izključno prek **zunanjega REST API-ja** — FatSecret Platform.  
Ni lokalne SQLite baze, ni lokalnega JSON fajla za hrano.

**Arhitektura klicne verige:**

```
UI (AddFoodSheet.kt / NutritionDialogs.kt)
  │
  ├─ FoodRepositoryImpl.searchFoodByName(query, maxResults=20)
  │     → FatSecretApi.searchFoods(q, page=1, pageSize=20)
  │           → HTTP GET  {FATSECRET_BASE_URL}/foods/search?q=...&page=1&pageSize=20
  │                 ← JSON: { foods: { food: [ {food_id, food_name, brand_name, food_description} ] } }
  │           → List<FoodSummary>
  │
  ├─ FoodRepositoryImpl.getFoodAutocomplete(query, maxResults=5)
  │     → FatSecretApi.getFoodAutocomplete(q, maxResults=5)
  │           → HTTP GET  {base}/foods/autocomplete?q=...&maxResults=5
  │                 ← JSON: { suggestions: { suggestion: [ "...", "..." ] } }
  │           → List<AutocompleteSuggestion>
  │
  ├─ FoodRepositoryImpl.getFoodDetail(id)
  │     → FatSecretApi.getFoodDetail(foodId)
  │           → HTTP GET  {base}/foods/{id}
  │                 ← JSON: { food: { servings: { serving: [ ... ] } } }
  │           → FoodDetail  (kalorije, makri, vlakna, natrij, holesterol ...)
  │
  └─ FoodRepositoryImpl.searchRecipes(query) / getRecipeDetail(id)
        → FatSecretApi.searchRecipes() / getRecipeDetail()
              → HTTP GET  {base}/recipes/search  /  {base}/recipes/{id}
              → List<RecipeSummary>  /  RecipeDetail
```

**Backend proxy:** Aplikacija ne kliče FatSecret API neposredno — gre prek **lastnega Cloud Run proxy-ja** (`fatsecret-551351477998.europe-west1.run.app`), ki doda OAuth2 kredenciale. URL se prebere iz `BuildConfig.FATSECRET_BASE_URL`; če je prazen, se uporabi hardcoded fallback.

**Autentikacija proxy-ja:** OkHttp interceptor doda `Authorization: Bearer {BACKEND_API_KEY}` na vsak zahtevek.

**Priporočila (Recommended foods):**  
`FatSecretApi.recommendedFoods(limit=8)` izvede 12 seed iskanj (`"chicken"`, `"rice"`, `"oats"`, …) in vrne prvih 8 unikatnih zadetkov (LinkedHashMap deduplikacija po `food_id`).

**Barcode iskanje:**  
`FatSecretApi.getFoodByBarcode(barcode)` → HTTP GET `{base}/foods/barcode?barcode=...`  
Vrne `BarcodeResult(foodId, foodName)`.  Fallback za sliko: **OpenFoodFacts** REST API (`world.openfoodfacts.org/cgi/search.pl`).

**Porcija:** Ob klicu `getFoodDetail()` se prioritizira porcija s `metric_serving_amount=100` in `metric_serving_unit="g"`. Če 100g porcija ne obstaja, se vzame prva porcija v nizu.

**Lokalni cache:** Ni — vsaka poizvedba je svež HTTP klic. Firestore offline persistenca (`:setPersistenceEnabled(true)`) velja samo za `dailyLogs`, ne za food search rezultate.

---

### 9.2 Macro Math (Seštevanje dnevnih makronutrientov)

#### Seštevanje — NutritionScreen.kt (vrstice 158–167)

Makroji se seštevajo **lokalno v Compose state** iz `trackedFoods: List<TrackedFood>`:

```
consumedKcal      = trackedFoods.sumOf { it.caloriesKcal.roundToInt() }
consumedProtein   = trackedFoods.sumOf { it.proteinG     ?: 0.0 }
consumedCarbs     = trackedFoods.sumOf { it.carbsG       ?: 0.0 }
consumedFat       = trackedFoods.sumOf { it.fatG         ?: 0.0 }
consumedFiber     = trackedFoods.sumOf { it.fiberG       ?: 0.0 }
consumedSugar     = trackedFoods.sumOf { it.sugarG       ?: 0.0 }
consumedSodium    = trackedFoods.sumOf { it.sodiumMg     ?: 0.0 }
consumedPotassium = trackedFoods.sumOf { it.potassiumMg  ?: 0.0 }
consumedCholest.  = trackedFoods.sumOf { it.cholesterolMg ?: 0.0 }
consumedSatFat    = trackedFoods.sumOf { it.saturatedFatG ?: 0.0 }
```

**⚠️ Rounding Error brez zaščite:**  
`caloriesKcal.roundToInt()` se zaokroži na celo število **pred** seštevanjem. Pri 20+ živilih z decimalnimi vrednostmi se nabere napaka do ±10 kcal. Ostali makroji (`proteinG`, `carbsG`, `fatG`) se seštevajo kot `Double` brez zaokroževanja.

**Dvojna vrednost kalorij:**  
Hkrati obstajata dve vrednosti zaužitih kalorij:
1. `consumedKcal` — lokalni seštevek iz `trackedFoods` (za progresne bare v UI)
2. `uiState.consumed` — vrednost iz Firestore `dailyLogs/{danes}.consumedCalories` (za dinamični TDEE)

Obe sta prikazani, a se ne ujemata vedno, ker `consumedCalories` v Firestore posodablja samo `logFood()` (transakcija: `currentConsumed + caloriesKcal`), medtem ko lokalni seštevek upošteva vse `trackedFoods` iz `items` array-a.

#### Cilji — NutritionScreen.kt (vrstice 197–202)

Prednostni vrstni red pri določanju ciljnih vrednosti:

| Makro | 1. prednost | 2. prednost | 3. prednost | Fallback |
|-------|-------------|-------------|-------------|----------|
| Kalorije | `nutritionPlan.calories` | `parseMacroBreakdown(plan.algorithmData.macroBreakdown)` | `plan.calories` | 2000 |
| Beljakovine | `nutritionPlan.protein` | `parsed.proteinG` | `plan.protein` | 100 |
| OH | `nutritionPlan.carbs` | `parsed.carbsG` | `plan.carbs` | 200 |
| Maščobe | `nutritionPlan.fat` | `parsed.fatG` | `plan.fat` | 60 |

**Zaokroževanje kalorij navzdol:** `targetCalories = (rawTargetCalories / 100) * 100`  
→ npr. 2183 → 2100 (Integer division, brez Math.floor).

---

### 9.3 Daily Goals Logic (BMR/TDEE Formula)

Celoten izračunski pipeline je v `utils/NutritionCalculations.kt`, sprožen prek `NutritionPlanStore.recalculateNutritionPlan()`.

#### Korak 1: BMR — `calculateAdvancedBMR(weight, height, age, isMale, bodyFat?)`

**Če bodyFat% je znan (> 0):** Katch-McArdle formula:
```
leanBodyMass = weight × (1 − bodyFat / 100)
BMR = 370 + (21.6 × leanBodyMass)
```

**Če bodyFat% ni znan:** Mifflin-St Jeor z age korekcijami:
```
Moški:  baseBMR = 10×weight + 6.25×height − 5×age + 5
Ženska: baseBMR = 10×weight + 6.25×height − 5×age − 161

Age multiplier:
  < 18  → × 1.12
  18–25 → × 1.05
  26–35 → × 1.00
  36–45 → × 0.97
  46–55 → × 0.94
  56–65 → × 0.91
  65+   → × 0.87
```

#### Korak 2: TDEE — `calculateEnhancedTDEE(bmr, frequency?, experience?, age, limitations, sleep?)`

```
TDEE = BMR
     × baseMultiplier     (treningFrequency: 2x=1.375, 3x=1.55, 4x=1.725, 5x=1.9, 6x=2.0, else=1.2)
     × experienceMultiplier  (Beginner=1.08, Intermediate=1.0, Advanced=0.96)
     × ageMultiplier         (<25=1.02, 25–35=1.00, 36–50=0.98, >50=0.95)
     × sleepMultiplier       (<6h=0.90, 6–7h=0.97, 7–8h=1.00, 8–9h=1.02, 9+h=1.01)
     × limitationMultiplier  (Asthma=0.92, HBP/Diabetes=0.94, Knee/Shoulder/Back=0.96, else=1.00)
```

#### Korak 3: Kalorijski cilj — `calculateSmartCalories(tdee, goal?, experience?, bmi, age, isMale, bodyFat?, limitations)`

| Cilj | Formula |
|------|---------|
| `"Build muscle"` | `tdee + baseSurplus × ageFactor × bodyFatFactor`  (baseSurplus: Beg=450, Int=350, Adv=250) |
| `"Lose fat"` | `max(tdee − baseDeficit × genderFactor × ageFactor, minKcal)`  (min: M=1500, F=1200) |
| `"Recomposition"` | tdee ± 150–200 glede na BMI/bodyFat |
| `"Improve endurance"` | `tdee + 200–300` |
| `"General health"` | `tdee ± 0–250` glede na BMI |

#### Korak 4: Makroji — `calculateOptimalMacros(calories, weight, goal?, experience?, age, isMale, bodyFat?, nutrition?, limitations)`

```
protein = baseProteinPerKg × weight × ageProteinFactor × genderProteinFactor × nutritionProteinFactor
           → toInt()  [g]

fat = fatPerKg × weight
       → toInt()  [g]

carbs = max(80, (calories − protein×4 − fat×9) / 4)
         → Keto/LCHF: max vrednost = 50g
         → IF: minimum = 100g
         → else: minimum = 80g
```

`return Triple(protein, carbs, fat)`

#### Korak 5: Adaptivni TDEE — `calculateAdaptiveTDEE()` (Faza 7)

```
adaptiveTDEE = avgConsumedKcal − (ΔmasaKg × 7700 / aktivniDni)

hybridTDEE = C × adaptiveTDEE + (1−C) × theoreticalTDEE
  kjer C:
    0.0 → < 3 dni podatkov
    0.5 → 3–5 dni podatkov
    1.0 → 6+ dni podatkov
```

Rezultat se shrani v `WeightPredictorStore.lastHybridTDEE` (in-memory singleton) in ga `NutritionViewModel.setUserMetrics()` prebere pri naslednjem klicu.

#### Dinamični kalorični cilj — `NutritionViewModel`

```
dynamicTargetCalories = max(1200, baseTdee + burnedCalories + goalAdjustment)

  baseTdee = hybridTDEE          (če > 800)
           ELSE bmr × 1.2        (sedentarni TDEE)

  goalAdjustment:
    "Lose"/"Cut"  → −500
    "Build"/"Gain" → +300
    else          → 0
```

Ko uporabnik zaključi vajo/tek → `DailyLogRepository.updateDailyLog()` prišteje kalorije → Firestore snapshot posodobi `uiState.burned` → `dynamicTargetCalories` se samodejno poviša.

---

### 9.4 Water Tracker

#### Implementacija — Optimistični zapis z debounce-om (Faza 13.1)

```
UI klik "+250ml"
  │
  ├─ nutritionViewModel.updateWaterOptimistic(newValue, todayId)
  │
  ├─ _localWaterMl.value = newValue    ← TAKOJŠEN UI odziv (brez čakanja Firestore)
  │
  ├─ waterSyncJob?.cancel()           ← prekliče prejšnji job če je v 800ms
  │
  └─ delay(800ms)
       │
       ├─ FoodRepositoryImpl.logWater(newValue, todayId)
       │     → Firestore transaction:
       │           SET dailyLogs/{danes} MERGE {
       │             "date": todayId,
       │             "waterMl": newValue,         ← ABSOLUTNA vrednost (ne increment)
       │             "updatedAt": serverTimestamp()
       │           }
       │
       ├─ SUCCESS → _localWaterMl.value = null   ← počisti lokalni override
       └─ FAIL    → _localWaterMl.value = null   ← rollback na server vrednost
```

**⚠️ Absolutni zapis (ne increment):**  
`logWater()` zapiše `"waterMl": newValue` kot absolutno vrednost, ne kot `FieldValue.increment()`.  
Če dva klici prideta znotraj 800ms in prvi ne bo preklican (ker ViewModel nima singleton scope), obstaja Race Condition — zadnji zapis zmaga.

**UI double-tap varovalka:** `lastWaterClickState` (remember mutableStateOf(0L)) v NutritionScreen prepreči dvojni klik z min. intervalom.

**Dnevni cilj vode — `calculateDailyWaterMl()`:**

```
base = weight × 35ml
× 1.1   (moški)
+ activityBonus  (Sedentary=0, Lightly=250, Moderately=500, Very=750 ml)
+ 500ml  (treningov dan)
→ zaokroži na 100ml
→ coerceIn(1500..5000 ml)
```

Cilj se **ne shrani v Firestore** — izračuna se sproti iz profila ob zagonu screena.

---

### 9.5 Custom Meals & Recipes

#### Struktura Custom Meal v Firestore

```
users/{uid}/customMeals/{mealId}
  name:      String          // npr. "Zajtrk z jajci"
  items:     List<Map>       // sestavine — glejte spodaj
  createdAt: Timestamp       // serverTimestamp()
```

Vsak element v `items`:
```
{
  id:   String  // FatSecret food_id
  name: String  // ime živila
  amt:  String  // količina (shranjena kot String, ne Number)
  unit: String  // enota (npr. "g", "cup")
}
```

**⚠️ `amt` je String, ne Number** — shranjeno pred konverzijo; ob branju `parseRawItemsToTrackedFoods()` poskuša `(m["amount"] as? Number)?.toDouble() ?: (m["amount"] as? String)?.toDoubleOrNull() ?: 1.0`.

#### Shranjevanje

`FoodRepositoryImpl.logCustomMeal(name, itemsList)` → Firestore transakcija:
```
// auto-ID dokument
val newRef = docRef.collection("customMeals").document()
transaction.set(newRef, mapOf("name" to name, "items" to items, "createdAt" to serverTimestamp()))
// vrne newRef.id
```

#### Branje (Live)

`FoodRepositoryImpl.observeCustomMeals(uid)` → `addSnapshotListener` na `customMeals/`  
→ `callbackFlow<QuerySnapshot>` → `NutritionViewModel.customMealsState: StateFlow<QuerySnapshot?>`  
→ `NutritionScreen` pretvori v `List<SavedCustomMeal>` ob vsakem onSnapshot.

#### Logiranje Custom Meal kot obrok

Ko uporabnik izbere custom meal, NutritionScreen kliče `NutritionViewModel.getCustomMealItems(uid, mealId)`:
```
db.collection("users").document(currentUid)
    .collection("customMeals").document(mealId)
    .get().await()
// vrne: doc.get("items") as? List<Map<String, Any>>
```

Nato za vsako sestavino zbere `FoodDetail` iz FatSecret API in jo doda v `trackedFoods`.

#### Recepti (FatSecret Recipes)

Recepti so ločeni od Custom Meals. Iščejo se prek `FoodRepositoryImpl.searchRecipes()` / `getRecipeDetail()`.  
**Ne shranjujejo** se v Firestore — so samo read-only prikaz iz FatSecret baze.

#### Vgrajevanje makrojev ob vnosu (Embed vs Reference)

Ko se custom meal loggira kot obrok:

- **Makroji se ob vnosu uvozijo iz FatSecret API-ja** in shranijo kot `TrackedFood` v `dailyLogs/{danes}.items[]`
- **Referenca na `customMeals/{mealId}` se NE shrani** v dnevni log
- **Posledica:** Kasnejša sprememba custom meal recepta NE vpliva na pretekle dnevne loge — stari vnosi ohranijo originalne makroje

---

### 9.6 Ugotovljene Anomalije

| # | Opis | Lokacija |
|---|------|----------|
| 1 | `consumedCalories` v Firestore in lokalni `consumedKcal` (sumOf trackedFoods) sta neodvisni vrednosti — prikazani hkrati v UI | `NutritionViewModel.logFood()` vs `NutritionScreen.consumedKcal` |
| 2 | `logWater()` zapiše absolutno vrednost `waterMl`, ne incremental — Race Condition ob hitrem klikanju | `FoodRepositoryImpl.logWater()` |
| 3 | `getCustomMealItems()` kliče `db.collection("users").document(currentUid)` **direktno** (bypassa `FirestoreHelper.getCurrentUserDocRef()`) | `NutritionViewModel.getCustomMealItems()` vrstica 293 |
| 4 | `WeightPredictorStore.lastHybridTDEE` je in-memory singleton — po restartu aplikacije se izgubi in NutritionViewModel pade nazaj na `bmr × 1.2` | `NutritionViewModel.setUserMetrics()` vrstica 165 |
| 5 | Makro cilji imajo 3-stopenjski fallback (nutritionPlan → parsed → plan), toda `nutritionPlan.calories` se zaokroži navzdol na 100, `dynamicTargetCalories` pa ne — UI prikazuje dve različni ciljni vrednosti | `NutritionScreen` vrstice 197–202 vs `dynamicTargetCalories` StateFlow |

---

## Poglavje 11 — GAMIFICATION & PROGRESSION ENGINE

> Faza 7 od 10 — Avtor: AI Audit | Datum: 2026-04-28

---

### 11.1 XP Logic — Kako se dodeljuje XP

**Vstopna točka:** `UpdateBodyMetricsUseCase.invoke()` → `ManageGamificationUseCase.recordWorkoutCompletion()` → `FirestoreGamificationRepository.awardXP()`

**Formuli za XP ob zaključku treninga:**

```
// ManageGamificationUseCase.recordWorkoutCompletion()
baseXP = 50
calorieXP = (caloriesBurned / 8).toInt()
isCritical = Random.nextFloat() < 0.1f      // 10% šansa
finalBaseXP = if (isCritical) baseXP * 2 else baseXP   // 100 ali 50
skupaj = finalBaseXP + calorieXP
```

**Alternativna formula** (v `completeWorkoutSession()` — klicana, ko UI pokliče neposredno):
```
workoutXp = 100 + (calKcal / 10)
```

> ⚠️ **Anomalija**: Dve različni formuli za XP za dokončan trening obstajata vzporedno (`recordWorkoutCompletion` vs `completeWorkoutSession`). `UpdateBodyMetricsUseCase` kliče samo `recordWorkoutCompletion`, `completeWorkoutSession` ni klicana iz nobenega zaključka treninga v produkcijskem toku, hkrati pa ni označena kot deprecated.

**XP za ostale akcije** (iz `ManageGamificationUseCase`):
| Akcija | XP |
|--------|-----|
| `DAILY_LOGIN` | 10 |
| `REST_DAY` | 10 |
| `PLAN_CREATED` | 100 |
| `WORKOUT_COMPLETE` (base) | 50 (ali 100 ob critical hit) |
| `CALORIES_BURNED` | `caloriesBurned / 8` |

**Pisanje v Firestore:** `FirestoreGamificationRepository.awardXP()` uporablja `db.runTransaction` — atomarno prebere trenutni XP, izračuna `newXp = currentXp + amount`, hkrati izračuna `newLevel = UserProfile.calculateLevel(newXp)` in zapiše oba atributa skupaj. Vsak XP event se hkrati zapiše v `users/{uid}/xp_history/{autoId}` z razlogom, datumom in stanjem po transakciji.

**Daily cap:** **Ne obstaja.** Ni omejitve, kolikokrat na dan se XP podeli. Vsak klic `awardXP()` bo atomarno prištel XP, neovirano.

> ⚠️ **Anomalija**: Brez daily capa brez preverjanja, ali je trening danes že bil narejen, ni ovire za "farming" XP z zaganjanjem in takojšnjim zaključevanjem treningov večkrat na dan.

---

### 11.2 Level System — Formula za levele

**Datoteka:** `data/UserProfile.kt` vrstice 77–98

```
// Inicializacija
level = 1
requiredXp = 100
totalXp = 0

// Iteracija
while (totalXp + requiredXp <= xp):
    totalXp += requiredXp
    level++
    requiredXp = floor(requiredXp * 1.2)  // 20% rast na level
```

**Primeri XP pragov:**
| Level | XP za dosego |
|-------|-------------|
| 1 | 0 |
| 2 | 100 |
| 3 | 220 (100+120) |
| 4 | 364 (100+120+144) |
| 5 | 537 (100+120+144+173) |
| 10 | ~2 591 |
| 25 | ~44 900 |
| 50 | ~2 100 000 (ocena) |

**Tip:** Eksponentna / geometrijska progresija (+20% XP potrebnih na level). Vsak naslednji level zahteva 20% več kot prejšnji.

**Level se izračuna atomarno v transakciji** `awardXP()` — ni možno imeti "stale" levela.

---

### 11.3 Streak Engine — Kako deluje streak

**Dve vzporedni implementaciji:**

#### A) `UserProfileManager.updateUserProgressAfterWorkout()` (PRIMARNA)
- Kliče se iz `UpdateBodyMetricsUseCase` ob vsakem zaključku treninga.
- Temelji na **epochDays** (celo število dni od Unix epohe) — `last_workout_epoch` v Firestoreu.
- Logika:
```
dayDiff = todayEpochDays - lastWorkoutEpochDays
newStreak = when:
    oldLastEpoch == 0 → 1            // prvi trening kdajkoli
    dayDiff == 0     → oldStreak     // danes že treniral → ohrani
    dayDiff == 1     → oldStreak + 1  // včeraj treniral → podaljšaj
    dayDiff > 1 AND freezes > 0 → oldStreak (freeze porabljen, streak ohranjen, NE poveča se!)
    dayDiff > 1 AND freezes == 0 → 1 (reset)
```

#### B) `FirestoreGamificationRepository.updateStreak()` (SEKUNDARNA)
- Temelji na **datum string** (`login_streak` polje), ne epochDays.
- Piše v `users/{uid}/daily_logs/{todayStr}` s statusom `WORKOUT_DONE` ali `REST_DONE`.
- Ima **idempotency guard**: če `daily_logs/{todayStr}` že obstaja, transakcija vrne takoj brez pisanja.
- Streak se poveča samo ob `isWorkoutSuccess=true`. Rest Day (kliče se z `isWorkoutSuccess=true`!) poveča streak enako kot workout.

> ⚠️ **Anomalija**: Dve vzporedni streak implementaciji (`login_streak` in `streak_days`) pišeta v različni Firestore polji (`login_streak` vs `streak_days`). Ni jasno, kateri je prikazan v UI. `updateUserProgressAfterWorkout` piše `streak_days` in `last_workout_epoch`; `updateStreak` piše `login_streak` in `last_streak_update_date`.

> ⚠️ **Anomalija**: `WeeklyStreakWorker` (midnight check) kliče `FirestoreGamificationRepository.updateStreak()` oziroma `runMidnightStreakCheck()`, ki prebere `daily_logs/{yesterdayStr}`. Če pa je primarni streak v `streak_days` + `last_workout_epoch` (epochDays baza), Worker ne popravlja pravilnega polja.

**Rest Day in streak:** V `ManageGamificationUseCase.restDayInitiated()` kliče `repository.updateStreak(isWorkoutSuccess = true)` — Rest Day šteje enako kot workout za `login_streak`. Za `streak_days` (primarystreak) Rest Day ne ustvari zapisa prek `updateUserProgressAfterWorkout` (ta se ne kliče).

**WeeklyStreakWorker — urnik:** `OneTimeWorkRequest` z zamikom do naslednjega dne 00:01 lokalnega časa. Ob koncu vsakega uspeha se razporedi naslednji (`scheduleNext(context)`). Zahteva `NetworkType.CONNECTED`.

> ⚠️ **Anomalija**: Worker ne bo zagnal in preveril streaka, če telefon ob polnoči nima internetne povezave. Streak ne bo padel, dokler Worker naslednjič ne uspe zagnati. To pomeni, da je možno streak ohraniti dalj časa z izogibanjem interneta.

**Manipulacija s sistemskim časom:** `getTodayStr()` in `getYesterdayStr()` v `FirestoreGamificationRepository` kličeta `Clock.System.now()` z `TimeZone.currentSystemDefault()` — **sistemski čas naprave**. Ker so vrednosti vnesene v Firestore kot string/epochMs in niso preverjene prek `serverTimestamp()`, je manipulacija z uro naprave možna za podaljšanje streaka.

> ⚠️ **Anomalija**: `updateUserProgressAfterWorkout()` prav tako kliče `Clock.System.now()` za `todayEpochDays`. Celotna streak logika temelji na lokalnem telefonskem času, ne na Firestore `serverTimestamp()`. Nastavitev datuma naprave naprej za 1 dan ohrani streak, nastavljanje nazaj pa ga ne prekine.

---

### 11.4 Badge Unlock System

**Definicije:** `BadgeDefinitions.ALL_BADGES` — 22 badge-ev v 6 kategorijah:
| Kategorija | Badge-i |
|-----------|---------|
| `WORKOUT` | first_workout, committed_10/50/100/250/500 |
| `ACHIEVEMENT` | calorie_crusher_1k/5k/10k, level_5/10/25/50, first_plan, plan_master |
| `SOCIAL` | first_follower, social_butterfly (10), influencer (50), celebrity (100) |
| `SPECIAL` | early_bird (5× pred 7:00), night_owl (5× po 21:00) |
| `STREAK` | week_warrior (7), month_master (30), year_champion (365) |

**Progress izračun:** `ManageGamificationUseCase.getBadgeProgress(badgeId, profile)` vrne vrednost iz `UserProfile` (npr. `profile.totalWorkoutsCompleted`, `profile.followers`, `profile.level`).

**Unlock preverjanje (kje):**  
`AchievementsScreen.kt` in `LevelPathScreen.kt` — oba računata badge status v composableu ob vsakem renderu:
```
isUnlocked = userProfile.badges.contains(badge.id) || userProgress >= badge.requirement
```

**Shranjevanje odklepov:** `userProfile.badges: List<String>` — Firestore polje `badges` je seznam ID-jev odklenjenih badge-ev. Ni evidence o tem, da se badge ID atomarno doda v Firestore ob odklepu; `WorkoutSessionScreen` ob zaključku treninga pokliče `result.unlockedBadges.firstOrNull()` za animacijo, ampak `WorkoutCompletionResult.unlockedBadges` vrne vedno **prazen seznam** (`emptyList()` — vrstica 86 v `ManageGamificationUseCase`).

> ⚠️ **Anomalija**: `WorkoutCompletionResult.unlockedBadges` je hardcoded `emptyList()`. Badge animacija v `WorkoutSessionScreen.kt` (vrstica 554) bo vedno prazna — badge popup se nikoli ne sproži ob zaključku treninga.

**Trigger model:** Badge odklepanje ni trigger-based (ni callbacka ob `awardXP` ali `updateStreak`). Badge status je izračunan on-demand v UI ob vsakem renderu na podlagi `UserProfile` vrijednosti. Ni periodičnega backend scana.

> ⚠️ **Anomalija**: Ker badge odklepanje temelji na client-side izračunu brez pisanja, badge `id` ne bo dodan v Firestore `badges` seznam avtomatično ob doseženi meji. Brez dodatne logike za pisanje badge ID-ja, bo isUnlocked vedno `true` v UI (ker `userProgress >= req`), ampak `userProfile.badges.contains(badge.id)` bo vračal `false` do ročnega vpisa.

---

### 11.5 Streak Freeze — Nakup in Poraba

**Nakup:** `ShopViewModel.buyStreakFreeze()`
- Cena: **300 XP**
- Maks zalogo: **3 Freezes** hkrati
- Mehanizem: Firestore transakcija atomarno preveri `streak_freezes < 3` in `xp >= 300`, nato atomarno zmanjša XP in poveča `streak_freezes`. Pisanje v `xp_history` z `"source": "SHOP_SPEND"`.
- Level se preračuna znotraj iste transakcije.

**Poraba:** Dve poti:
1. `UserProfileManager.updateUserProgressAfterWorkout()` — porabi freeze ko `dayDiff > 1` in `streak_freezes > 0`. Streak ostane na stari vrednosti (ne poveča se). Piše `streak_freezes -= 1` le ako je bil `freezeUsed = true`.
2. `FirestoreGamificationRepository.consumeStreakFreeze()` — kliče se iz `runMidnightStreakCheck()` Worker-ja. Enaka logika: atomarno zmanjša `streak_freezes` za 1.

> ⚠️ **Anomalija**: Dve ločeni poti za porabo zamrznitev pišeta v isto polje (`streak_freezes`). Ko `updateUserProgressAfterWorkout` porabi freeze (workoutpath) IN Worker hkrati pokliče `consumeStreakFreeze` (midnight path), je za isto zamujeno noč možna dvojna poraba.

**Pridobivanje Freeze (brez nakupa):** Ni evidence o tem, da se Freeze kdajkoli podeli brez nakupa v shopu. Ni nagradnega sistema (dnevna prijava, streak milestone) ki bi podarjal Freeze.

---

### 11.6 Rest Day Swap Mehanizem

**Lokacija:** `FirestoreGamificationRepository.checkIfFutureRestDaysExistAndSwap()`

**Potek:** Ko Worker zazna, da je bil včerajšnji dan zamuden (ni zapisa v `daily_logs`):
1. Poišče first upcoming rest day v aktivnem planu (`user_plans/{uid}.plans[0].weeks[].days[]`).
2. Zamenjana dan — zamujeni dan postane `isRestDay=true, isSwapped=true`.
3. Prihodnji rest dan postane workout z originalnim `focusLabel` — `isSwapped=true`.
4. Posodobljeni plan se zapiše nazaj v Firestore.

**Omejitev:** Zamenjava se naredi samo enkrat na zamujeni dan (FIFO — prvi razpoložljiv rest dan naprej).

> ⚠️ **Anomalija**: `currentPlanDayNum` se izračuna kot `logsSnap.documents.size + 1` — skupno število vseh dokumentov v `users/{uid}/daily_logs`, ne samo aktivnih treningov. Če je kolekcija `daily_logs` zakrpana z `REST_DONE` ali `FROZEN` dokumenti za pretekle dni, bo `currentPlanDayNum` napačen (precenjen).

---

### 11.7 Ugotovljene Anomalije

| # | Opis | Lokacija | Resnost |
|---|------|----------|---------|
| 1 | Brez daily XP capa — XP farming možen z večkratnim zagonom/zaključkom treninga | `FirestoreGamificationRepository.awardXP()` | 🔴 Visoka |
| 2 | Dve vzporedni streak implementaciji — `streak_days` (epochDays) in `login_streak` (dateStr) pišeta različni Firestore polji | `UserProfileManager` + `FirestoreGamificationRepository` | 🔴 Visoka |
| 3 | Streak temelji na lokalnem telefonskem času, ne `serverTimestamp()` — manipulacija z uro naprave ohrani streak | `getTodayStr()`, `updateUserProgressAfterWorkout()` | 🔴 Visoka |
| 4 | `WorkoutCompletionResult.unlockedBadges` je vedno `emptyList()` — badge unlock animacija nikoli ne sproži | `ManageGamificationUseCase.recordWorkoutCompletion()` vrstica 86 | 🔴 Visoka |
| 5 | Worker ne zagona brez interneta — streak ne pade ob izključeni povezavi ob polnoči | `WeeklyStreakWorker` (`NetworkType.CONNECTED` constraint) | 🟡 Srednje |
| 6 | Dvojna poraba Freeze možna: `updateUserProgressAfterWorkout` + `consumeStreakFreeze()` oba tečeta za isti zamujeni dan | `UserProfileManager` + `FirestoreGamificationRepository.runMidnightStreakCheck()` | 🟡 Srednje |
| 7 | `completeWorkoutSession()` (druga XP formula) ni klicana iz WorkoutSession toka — brez opozorila o deprecated | `ManageGamificationUseCase` vrstica 44 | 🟡 Srednje |
| 8 | Badge ID se nikoli atomarno ne doda v Firestore `badges` seznam ob dosegu meje — samo UI bere progress on-demand | `AchievementsScreen`, `LevelPathScreen` | 🟡 Srednje |
| 9 | `currentPlanDayNum` v Swap algoritmu temelji na skupnem številu `daily_logs` dokumentov — vključno z REST/FROZEN zapisi | `checkIfFutureRestDaysExistAndSwap()` vrstice 202–203 | 🟡 Srednje |
| 10 | `restDayInitiated()` kliče `updateStreak(isWorkoutSuccess=true)` — rest day poveča `login_streak` enako kot workout | `ManageGamificationUseCase.restDayInitiated()` vrstica 128 | 🟢 Nizko |
