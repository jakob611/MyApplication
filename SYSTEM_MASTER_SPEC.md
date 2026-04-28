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
| `burnedCalories` | Double | `0.0` | Skupaj porabljene kalorije (vsote vseh aktivnosti) | `DailyLogRepository.updateDailyLog()` ← RunTrackerScreen, ManualExerciseLogScreen, ManageGamificationUseCase |
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
| `id` | String | `""` | Interni ID (= doc ID) | `RunSession.toFirestoreMap()` prek `RunTrackerScreen` |
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

### 7.13 DATA REDUNDANCY — Pregled podvojenih podatkov

| # | Podatek | Polje 1 | Polje 2 | Resnost | Opis |
|---|---------|---------|---------|---------|------|
| 1 | Streak | `streak_days` (read by UserProfileManager) | `login_streak` (read+write by FirestoreGamificationRepository) | 🔴 Visoka | Dva pisca, en bralec — vrednosti se lahko razlikujeta. `UserProfileManager` je SSOT za streak v večini kode. |
| 2 | Rating dnevnih aktivnosti | `dailyLogs/{date}` (food/water/burned) | `daily_logs/{date}` (streak status) | 🟡 Srednja | Dve različni sub-kolekciji z podobno semantiko. Ne pride do direktnega konflikta, a povzroča zmedo. |
| 3 | Profil slika URL | ključ `"profilePictureUrl"` (zapis) | `"profile_picture_url"` (branje) | 🔴 Kritična | Write-read mismatch — `profilePictureUrl` nikoli ni prebrana nazaj, ker documentToUserProfile() bere `profile_picture_url`. |
| 4 | GPS točke | `polylinePoints` (inline, `latitude`/`longitude`) | `gps_points/{chunk}` ali `points/{chunk}` (sub-col, `lat`/`lng`) | 🟡 Srednja | Dva formata za iste podatke. FirestoreWorkoutRepository podpira oba. |
| 5 | Workout timestamp | `"timestamp"` (Long, novi format) | `"date"` (Firestore Timestamp, stari format) | 🔴 Visoka | `getWeeklyDoneCount()` querya po `"date"`, toda novi dokumenti nimajo tega polja → poizvedba vrne 0. |
| 6 | Teža v profilu vs weightLogs | `goalWeightKg` v `users/{docId}` | `weightKg` v `users/{docId}/weightLogs` | 🟢 Nizka | `goalWeightKg` je CILJNA teža. `weightLogs` je zgodovina DEJANSKE teže. Ni konflikt. |

---

### 7.14 FIRESTORE INDEXES

Potrebni (kompozitni) indeksi za delujoče poizvedbe:

| Kolekcija | Polja | Tip | Kdo potrebuje |
|-----------|-------|-----|---------------|
| `users` | `is_public_profile ASC`, `followers DESC` | Kompozitni | `ProfileStore.getTopUsers()` |
| `users/{uid}/runSessions` | `createdAt DESC` | Single-field descending | `FirestoreWorkoutRepository.getRunSessions()` + paginacija |
| `users/{uid}/workoutSessions` | `date ASC` (Timestamp) | Single-field | `FirestoreWorkoutRepository.getWeeklyDoneCount()` ⚠️ problema s timestamp vs date |
| `users/{uid}/gps_points` | `chunkIndex ASC` | Single-field | `FirestoreWorkoutRepository.loadGpsPoints()` |
| `users/{uid}/points` | `chunkIndex ASC` | Single-field | `FirestoreWorkoutRepository.loadGpsPoints()` |
| `users/{uid}/publicActivities` | `startTime DESC` | Single-field descending | `ProfileStore.mapToPublicProfile()` |
| `follows` | `followingId ==` | Single-field equality | `FollowStore.getFollowers()` |
| `follows` | `followerId ==` | Single-field equality | `FollowStore.getFollowing()` |
| `follows` | `followerId ==`, `followingId ==` | Kompozitni | `FollowStore.isFollowing()` fallback query |

