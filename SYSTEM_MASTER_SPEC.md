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
| `timestamp` | EpochMs | Čas točke |

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

## Poglavje 12 — UI/UX & COMPOSE ARCHITECTURE

> Faza 8 od 10 — Avtor: AI Audit | Datum: 2026-04-28

---

### 12.1 Navigation Flow — NavigationViewModel & Back Stack

**Datoteka:** `NavigationViewModel.kt` (121 vrstic)

**Arhitektura:** Lasten navigacijski stack implementiran kot `MutableStateFlow<List<Screen>>` v `ViewModel` (preživi rotacijo zaslona). **Ne uporablja Jetpack Navigation Compose** — navigacija je ročno upravljana.

#### Stack operacije:

| Metoda | Opis | Kdaj se kliče |
|--------|------|---------------|
| `navigateTo(screen)` | Push `currentScreen` na stack, nastavi nov current | Velik večina navigacij |
| `replaceTo(screen)` | Zamenja current brez pusha na stack | `LoadingWorkout → WorkoutSession` (prepreči "nazaj skozi loading") |
| `popTo(screen)` | Pop do ciljnega zaslona ali `navigateTo()` | Redka specifična navigacija |
| `clearStack()` | Prazni celoten stack | Ni klicana iz produkcijskega toka |
| `navigateBack()` | Pop last ali fallback na Dashboard/Index | `BackHandler` v `MainActivity` |

#### Odjava in Back Stack — Varnostna analiza:

```
onLogout klic (AppDrawer.kt → AlertDialog confirmButton):
  └── onLogout()  [callback v MainActivity]
      ├── Firebase.auth.signOut()
      ├── isLoggedIn = false
      ├── userEmail = ""
      ├── appViewModel.resetSyncState()
      └── navViewModel.navigateTo(Screen.Index)
          └── Push currentScreen (Dashboard) na stack!
```

> ⚠️ **Anomalija**: Ob odjavi se kliče `navigateTo(Screen.Index)` — to **pusha Dashboard na stack**. Stack ni počiščen. Če uporabnik klikne Back na `Screen.Index`, bo `navigateBack()` poklican s `stack.isNotEmpty()` → vrne se na `Screen.Dashboard` (zadnji element stacka), ki je zaščitena stran brez autentikacije. Šele naslednji Back klik bo preveril `isLoggedIn` in nagnal na `Screen.Index`.

**Korektivna varovalna plast:** `navigateBack()` vsebuje:
```kotlin
current is Screen.Dashboard -> {
    if (!isLoggedIn) _currentScreen.value = Screen.Index
    else onFinish()
}
```
To pomeni, da ko prideš nazaj na Dashboard brez prijave, bo naslednji Back klik odkril `!isLoggedIn` in šel na Index. Glasni dashboard vsebaj pa ni prikazan ker `isLoggedIn=false` → Compose ne prikaže vsebine (screeni preverjajo `isLoggedIn` iz MainActivity state). Stack ni počiščen, a vsebina je pravilno skrita.

**Deep Links:** `Screen` je `sealed class` brez deep link URI podpore. **Ni podpore za Android deep links** (ni `Intent` filteriranja, ni Jetpack Navigation `deepLink {}` blokov). Zunanje aplikacije ne morejo odpreti specifičnih zaslonov.

**Screen sealed class:** 37 zaslonov definiranih. `Screen.PublicProfile` ima parameter `userId: String` — edini parametriziran zaslon v navigacijskem sistemu.

---

### 12.2 State Management — StateFlow in Re-Composition

#### AppViewModel — Globalni profil StateFlow

```kotlin
val userProfile: StateFlow<UserProfile>
val isProfileReady: StateFlow<Boolean>
val isSyncing: StateFlow<Boolean>
val syncStatusMessage: StateFlow<String>
```

**Trigger za re-composition:** `val userProfile by appViewModel.userProfile.collectAsState()` v `MainActivity`. Ob vsaki spremembi `UserProfile` data classa se **celotna MainActivity Compose hierarhija** rekomponira, ker je `userProfile` parameter višje v drevesu, ki se propagira navzdol.

**UserProfile ima 40+ polj** — vsaka sprememba kateregakoli polja (npr. samo `xp`) sproži `collectAsState` trigger in potencialno rekomponira vse screene, ki sprejemajo `userProfile` kot parameter.

**Firestore snapshot listener** (`ObserveUserProfileUseCase`) ne filtrira "kaj se je spremenilo" — vsak Firestore onSnapshot (tudi ko se polje ni spremenilo) nastavi `_userProfile.value = nova vrednost`, ki sproži rekomposicijo.

> ⚠️ **Anomalija (Flicker)**: Ker Firestore listener in `startInitialSync` oba pišeta v `_userProfile` vzporedno in asinhronično, je možno kratkotrajen "prazen profil" → "pravi profil" prehod, ki povzroči vizualni flicker v screenih ki takoj prikazujejo `username` ali `level`. Screeni nimajo skeleton/loading stanja med posodobitvama.

#### NutritionViewModel — StateFlow za vodo (re-composition analiza)

```kotlin
private val _localWaterMl = MutableStateFlow<Int?>(null)  // override pri optimistič. posod.
private val _uiState = MutableStateFlow(NutritionUiState())
```

**Water update flow ob "+250ml" kliku:**
1. `_localWaterMl.value = newValue` → takojšnja re-composition NutritionScreen (samo water bar animacija)
2. Po 800ms debounce → Firestore write → onSnapshot → `_uiState.value` update → **DRUGA re-composition**

**Skupaj: 2 re-composiciji za en klik na "+250ml".**

Ker je `_uiState` in `_localWaterMl` ločena StateFlow-a, obe premi sprožilki za re-composition. Compose smart recomposition pri `collectAsState()` bo rekomponiral samo composable-e ki dejansko berejo te vrednosti — ne celoten screen.

**S24 Ultra specifika:** Na zmogljivem 120Hz zaslonu sta 2 zaporedni re-composiciji brez opaznih artefaktov. Edini kritičen scenarij je, če je `_uiState` posodobitev (Firestore onSnapshot) zamujene in pride hkrati z novo user akcijo — tedaj sta 2 onsnapshot v kratkem intervalu → 2 re-composiciji z `_uiState`.

> ⚠️ **Anomalija (nepotrebna re-composition)**: NutritionViewModel `combine()` operator za `dynamicTargetCalories` združuje 3 StateFlow-e: `baseTdee`, `uiState`, `goalAdjustment`. Vsak click ki posodobi `uiState` (ne samo kalorije) sproži ponoven izračun `dynamicTargetCalories` — vključno z vnosom vode. **Vnos vode ne vpliva na ciljne kalorije, a kljub temu sproži izračun TDEE.**

---

### 12.3 Onboarding Logic — OnboardingHint

**Datoteka:** `ui/components/OnboardingHint.kt` (65 vrstic)

**Implementacija:** Čista Compose kartica s 3 elementi: `Icon(Info)`, naslov, besedilo, in "Don't show again" gumb.

**Shranjevanje stanja:** Edina raba `OnboardingHint` je v `BodyModuleHomeScreen.kt`:
```kotlin
val prefs = context.getSharedPreferences("app_flags", Context.MODE_PRIVATE)
var showOnboarding by remember { mutableStateOf(!prefs.getBoolean("hide_body_hint", false)) }

OnboardingHint(
    title = "Welcome to Body Module",
    message = "...",
    onDismiss = {
        showOnboarding = false
        prefs.edit().putBoolean("hide_body_hint", true).apply()
    }
)
```

**Storage backend:** `SharedPreferences("app_flags")` — **lokalno na napravi**, ne v Firestore ali DataStore.

**Posledica:** Hint je skrit samo na napravi, kjer je bil skrit. Na novi napravi ali po čiščenju podatkov aplikacije se hint prikaže znova.

**Obseg:** Samo **ena instanca** `OnboardingHint` obstaja v celotni aplikaciji — v `BodyModuleHomeScreen`. Ni splošnega onboarding sistema za celotno aplikacijo.

**DataStore:** Kljub temu, da ima aplikacija `DataStore Preferences` knjižnico, onboarding hint jo **ne uporablja** — ostaja pri `SharedPreferences`.

---

### 12.4 Haptic & Visual Feedback

#### HapticFeedback.kt

**Arhitektura:** `object` singleton z `enum FeedbackType` (9 tipov):

| Tip | Vzorec (API 29+) | Primer rabe |
|-----|-----------------|-------------|
| `LIGHT_CLICK` | `EFFECT_TICK` | Bottom bar navigacija, manjši gumbi |
| `CLICK` | `EFFECT_CLICK` | Back gumb, splošni gumbi |
| `HEAVY_CLICK` | `EFFECT_HEAVY_CLICK` | Swipe plan, WorkoutSession start |
| `SUCCESS` | Waveform: 50-50-100-50-50ms | Rest day complete, workout done |
| `ERROR` | Waveform: 100-100-100ms (255 amp) | Napaka |
| `SELECTION` | `EFFECT_TICK` | |
| `LONG_PRESS` | `EFFECT_HEAVY_CLICK` | |
| `DOUBLE_TAP` | `EFFECT_DOUBLE_CLICK` | |
| `DRAWER_OPEN` | Waveform crescendo: 80-40-60ms | |

**OS verzijska granulacija:**
- API 29+: `VibrationEffect.createPredefined()`
- API 26–28: `VibrationEffect.createOneShot()` / `createWaveform()`
- API <26: `vibrator.vibrate(durationMs)` (deprecated path)

**Throttle mehanizem:** `lastVibrationTime` s 50ms minimalnim intervalom — prepreči preveliko vibriranje ob hitrem klikanju.

> ⚠️ **Anomalija**: `lastVibrationTime` je **instanca spremenljivka** na `object` singletonu — pomeni, da se throttle deli med vsemi klici `performHapticFeedback` iz kateregakoli mesta v aplikaciji. Hitri klik, ki bi moral dobiti HEAVY_CLICK feedback, lahko dobi tiho (throttled), ker je nek drug LIGHT_CLICK pred 50ms sprožil vibracijo.

**Trigiranje:** Haptic feedback se sproži **neposredno iz Composable onClick lambde** — ni posrednika prek ViewModel. Je sinhrono in se dogaja v UI threadu znotraj onClick callbacka.

```kotlin
// Primer iz BodyModuleHomeScreen:
modifier = Modifier.clickable {
    HapticFeedback.performHapticFeedback(context, HapticFeedback.FeedbackType.CLICK)
    onBack()
}
```

#### XPPopup.kt

**Arhitektura:** Stateless Composable ki sprejme `xpAmount: Int`, `isVisible: Boolean`, `onDismiss: () -> Unit`.

**Animation flow:**
```
isVisible=true → LaunchedEffect(isVisible):
  delay(2500ms) → shouldFadeOut=true
  delay(300ms)  → onDismiss()

animateFloatAsState(if (shouldFadeOut) 0f else 1f)  // fade out animacija
```

**Raba:** `XPPopup` je klicana samo v `Progress.kt` (AchievementsProgress screen). **Ni klicana** v `WorkoutSessionScreen`, `NutritionScreen`, ali drugje kjer se XP dejansko podeli.

> ⚠️ **Anomalija**: `XPPopup` je definirana kot lasten popup, ampak nima "kritičen hit" razlikovanje (ne prikaže drugačnega sporočila za 2× XP critical hit iz `ManageGamificationUseCase`). Vedno prikaže samo `✨ +$xpAmount XP`.

> ⚠️ **Anomalija**: Barva besedila v `XPPopup` je hardcoded `Color.White`:
```kotlin
color = Color.White
```
V svetlih temah je ozadje popupa `MaterialTheme.colorScheme.primary` = `Color(0xFF38305A)` (temno vijolična) — bela tekst na temnem ozadju je OK. V temnih temah je `primary` = `Color(0xFFDCE4FF)` (svetlo pastelno modra) — **bela tekst na svetlem ozadju je slabo berljiv**.

#### BadgeUnlockAnimation

**Lokacija:** `BadgeUnlockAnimation.kt` (korenski java direktorij, NE v `com.example.myapplication` paketu)

**Hardcoded barve (ne sledijo temi):**
```kotlin
Color(0xFFFFD700)  // zlata — badge prikaz
Color(0xFF2563EB)  // modra — konfeti
Color(0xFF13EF92)  // zelena — konfeti
Color(0xFFF04C4C)  // rdeča — konfeti
Color(0xFFFEE440)  // rumena — konfeti
```
Te barve so fiksirane neodvisno od aktivne teme.

**Trigiranje:** Kliče `HapticFeedback.performHapticFeedback(context, SUCCESS)` sinhrono ob prikazanu animacije — direktno iz Composable.

---

### 12.5 Dark Mode Integrity

**Implementacija:** `ui/theme/theme.kt` — dva `ColorScheme` objekta (`DarkColors`, `LightColors`).

**Pogoj za aktivacijo:**
```kotlin
@Composable
fun MyApplicationTheme(darkTheme: Boolean = false, ...) {
    val colors = if (darkTheme) DarkColors else LightColors
    ...
    MaterialTheme(colorScheme = colors, ...)
}
```

`darkTheme` parameter prihaja iz `isDarkMode` State v `MainActivity`, ki ga bere iz `UserProfileManager.isDarkMode(email)` (Firestore polje `darkMode`).

#### Barvna shema — primerjava

| Token | Light | Dark | Opomba |
|-------|-------|------|--------|
| `primary` | `#38305A` (temno vijolična) | `#DCE4FF` (svetlo pastelna) | Inverzija |
| `onPrimary` | `#FCFBF8` (smetanasto bela) | `#38305A` | Inverzija |
| `secondary` | `#DCE4FF` (pastelno vijolična) | `#FCF5C7` (rumena) | Različen hue! |
| `background` | `#FCFBF8` | `#14121F` (temno modra) | Inverzija |
| `surface` | `#FFFFFF` | `#26223B` | Ok |
| `surfaceVariant` | `#DCE4FF` | `#363251` | Ok |

**Konzistentna raba:** Večina kode v `AppDrawer.kt` pravilno bere iz `MaterialTheme.colorScheme.*`:
```kotlin
val TextPrimary = MaterialTheme.colorScheme.onSurface
val TextSecondary = MaterialTheme.colorScheme.onSurfaceVariant
val SheetBg = MaterialTheme.colorScheme.surface
```

#### Hardcoded barve izven teme

Kljub dobremu dizajnu tema sistem imajo naslednji elementi hardcoded barve:

| Datoteka | Hardcoded barva | Vrednost | Problem v dark mode? |
|----------|----------------|----------|---------------------|
| `XPPopup.kt` vrstica 66 | `Color.White` (besedilo) | `#FFFFFF` | 🔴 Slabo berljivo na `primary=#DCE4FF` v dark modu |
| `BodyModuleHomeScreen.kt` vrstica 235, 243, 248, 293, 579 | `Color(0xFF4CAF50)` | zelena | 🟡 Barva je dovolj kontrastna na obeh temah |
| `BadgeUnlockAnimation.kt` vrstice 89–159 | `Color(0xFFFFD700)`, `#2563EB`, `#13EF92`, `#F04C4C`, `#FEE440` | konfeti | 🟡 Animacijska barva — ne vpliva na berljivost |
| `ActivityLogScreen.kt` vrstica 64 | `Color(0xFF4CAF50)` za `WALK` ActivityType | zelena | 🟡 Dovolj kontrastna |
| `ExerciseHistoryScreen.kt` vrstica 259, 378 | `Color(0xFF4CAF50)` | zelena | 🟡 |
| `ManualExerciseLogScreen.kt` vrstica 665, 697 | `Color(0xFF4CAF50)` | zelena | 🟡 |
| `PlanPathDialog.kt` vrstica 297 | `Color(0xFF4CAF50)` | zelena | 🟡 |
| `HealthConnectScreen.kt` vrstice 375, 436, 660 | `Color(0xFF4CAF50)` | zelena | 🟡 |
| `PublicProfileScreen.kt` vrstica 451 | `Color(0xFF4CAF50)` | zelena | 🟡 |
| `theme.kt` vrstica 22 | `val DrawerBlue = Color(0xFF2563EB)` | modra | **Ni klicana nikjer** — dead constant |

> ⚠️ **Anomalija**: `DrawerBlue = Color(0xFF2563EB)` je definirana v `theme.kt`, ampak ni referenčna z nobenim mestom v projektu (ni rezultatov grep). Je dead constant.

> ⚠️ **Anomalija**: `XPPopup` prikazuje belo besedilo (`Color.White`) na `MaterialTheme.colorScheme.primary` ozadju. V dark modu je `primary = Color(0xFFDCE4FF)` (svetlo pastelno modra) — **kontrast razmerje bele na `#DCE4FF` je ~1.5:1, kar ne zadosti WCAG AA minimuma 4.5:1**.

#### Dark mode aktivacija — Timing Problem

```
Zagon aplikacije (up):
T+0ms: MyApplicationTheme(darkTheme = isDarkMode)  ← isDarkMode=false (default)
T+10ms: LaunchedEffect #1 → Firebase.auth check → LocalScope { isDarkMode = isDarkMode(email) }
         [Firestore klic]
T+500ms: isDarkMode=true vrne iz Firestorea → MyApplicationTheme(darkTheme=true) → RECOMPOSITION
```

**Posledica:** Pri vsakem zagonu z aktiviranim dark modom bo kratkotrajen **light mode flash** (`~500ms`) preden Firestore vrne vrednost. To je vizualni flicker ob zagonu.

---

---

## Poglavje 13 — PERFORMANCE & DATA ARCHITECTURE

> **Faza 9 od 10** — Audit izveden: 2026-04-28  
> **Cilj:** Dokumentirati, kako aplikacija preživi brez interneta, kako se čistijo pomnilniška sredstva in kje so bottlenecki zmogljivosti.

---

### 13.1 Offline Persistence (Firestore Cache)

#### Konfiguracija

Firestore offline persistenca je konfigurirana v `MyApplication.kt` (vrstice 34–48) ob startu aplikacije:

```kotlin
// MyApplication.kt, vrstice 37–44
val persistentCache = PersistentCacheSettings.newBuilder()
    .setSizeBytes(100L * 1024 * 1024) // 100 MB
    .build()
Firebase.firestore.firestoreSettings =
    FirebaseFirestoreSettings.Builder()
        .setLocalCacheSettings(persistentCache)
        .build()
```

- Uporabljena je novejša `PersistentCacheSettings` API (ne `isPersistenceEnabled` — ta je deprecated).
- Velikost cache-a: **100 MB** na disku naprave.
- `FirestoreHelper.kt` sam **ne konfigurira** nobenih cache nastavitev — edina konfiguracija je v `MyApplication`.

#### OSMdroid (karte)

`MyApplication.kt` (vrstice 20–28) ločeno konfigurira osmdroid tile cache za RunTracker karte:

```kotlin
osmConfig.tileFileSystemCacheMaxBytes  = 50L * 1024 * 1024   // 50 MB disk
osmConfig.tileFileSystemCacheTrimBytes = 40L * 1024 * 1024   // trim pri 40 MB
osmConfig.cacheMapTileCount            = 100.toShort()        // 100 tiles v RAM
osmConfig.cacheMapTileOvershoot        = 16.toShort()         // pred-naloži 16 ekstra
```

#### Vedenje med odsotnostjo omrežja (mid-workout)

Ko uporabnik sredi gozda izgubi LTE signal:

| Operacija | Vedenje offline | Razlog |
|---|---|---|
| `awardXP()`, `FieldValue.increment()` | Write se shrani lokalno → samodejno posla ob reconect | Firestore SDK buffer |
| `updateStreak()`, `.set()` polj | Write shranjen lokalno → samodejno posla ob reconnect | Firestore SDK buffer |
| `logFood()`, `logWater()` | Write shranjen lokalno → samodejno posla | Firestore SDK buffer |
| `addSnapshotListener` (real-time) | Vrne zadnje cached stanje iz diska (100 MB cache) | SDK offline mode |
| FatSecret API klic (iskanje hrane) | **Takoj vrne napako** — OkHttp ne pozna offline cachea | Ni implementiranega HTTP cachea |
| Health Connect sync | **Takoj vrne napako** — Health Connect je lokalna Android baza, klic uspe; Firestore write se shrani v cache | Odvisno od dela |

#### Conflict Resolution

Aplikacija **nima eksplicitnega koda za conflict resolution**. Zanašanje je na Firebase SDK-jevo strategijo:

- **`FieldValue.increment()`** (XP, burned calories) — atomarno na serverju → pravilno setvari pri off+on reconnect.
- **`set()` in `update()`** (streak, streak_freeze, profil) — **last-write-wins**: local write prišel zadnji → server vrednost bo prepisana z offline vrednostjo. Možen scenarij: dve napravni off-line spremenita streak različno → druga, ki se je sinhronizirala pozneje, bo prepisala prvo.

---

### 13.2 Memory Leaks — Snapshot Listener Lifecycle

#### FirestoreUserProfileRepository — Race Condition

`FirestoreUserProfileRepository.kt` (vrstice 14–40) odpre Firestore listener znotraj `callbackFlow`:

```kotlin
// FirestoreUserProfileRepository.kt, vrstice 14–39
override fun observeUserProfile(email: String): Flow<UserProfile> = callbackFlow {
    var listener: ListenerRegistration? = null  // ← inicializiran kot null
    launch {                                     // ← asinhroni launch
        try {
            val userRef = FirestoreHelper.getCurrentUserDocRef()   // ← network klic (~200–500ms)
            listener = userRef.addSnapshotListener { snap, error -> ... }
        } catch (e: Exception) { close(e) }
    }
    awaitClose { listener?.remove() }           // ← zaklene se zdaj, listener še null
}
```

**Race condition:** Če se flow prekliče (npr. ViewModel uničen) preden `getCurrentUserDocRef()` vrne rezultat (~200–500ms), velja:
1. `awaitClose` se izvede z `listener == null` → `listener?.remove()` ne naredi ničesar.
2. `launch` coroutine je bil cancelled → `addSnapshotListener` se **ne registrira** (korutina je preklicana pred klicem).

Rezultat: V tem bestem primeru se listener **nikoli ne registrira**, ni leaka. V najslabšem primeru (overload na thread poolerju, kjer `launch` uspe registrirati listener tik pred cancellom) bi listener ostal aktiven.

#### FoodRepositoryImpl — Pravilna Implementacija

`FoodRepositoryImpl.observeCustomMeals()` (vrstice 42–55) in `observeDailyLog()` (vrstice 57–71):

```kotlin
// Pravilno — listener dodeljen SINHRONO pred awaitClose
val listener = docRef.addSnapshotListener { ... }
awaitClose { listener.remove() }   // ← listener je vedno non-null ✅
```

Oba listnerjeva sta pravilno registrirana in odstranjena ob preklicu flow-a. ✅

#### NutritionViewModel — Dvojni Collect Vzorec

`NutritionViewModel.observeDailyTotals()` (vrstice 200–233):

```kotlin
viewModelScope.launch {
    uidFlow.collect { uid ->                    // ← zunanji collect
        if (uid != null) {
            observeDailyLog(uid, todayId).collect { doc -> ... }  // ← notranji collect
        }
    }
}
```

**Problem:** Ob vsaki spremembi `uid` (npr. ob logout/login ciklu brez restart ViewModel-a) se **odpre nov notranji collect** na `observeDailyLog`, medtem ko prejšnji inner collect ni eksplicitno prekinjen. Stari `callbackFlow` se zapre šele, ko se zapre korutina starša (`viewModelScope.launch`), kar pomeni **hkratno aktivnih dva Firestore listenerja** na istem dokumentu.

> ⚠️ **Anomalija**: `NutritionViewModel.observeDailyTotals()` puede imeti aktivna 2 `addSnapshotListener` na `dailyLogs/{danes}` ob ponovnem login brez rekreacije ViewModel-a.

---

### 13.3 Image Loading

#### Deklaracija brez uporabe

`app/build.gradle.kts` (vrstica 120) deklarira:

```kotlin
implementation("io.coil-kt:coil-compose:2.5.0")
```

**Grep rezultat:** Nobena Kotlin datoteka v projektu ne kliče `AsyncImage()`, `rememberAsyncImagePainter()`, ali katerekoli druge Coil API.

#### Profil slike

`UserProfile` data class (`data/UserProfile.kt`) **nima polja za URL slike profila** (`photoUrl`, `avatarUrl` ali podobnega). Google Sign-In sicer vrne `FirebaseUser.photoUrl`, toda to polje ni nikjer prebrano ali prikazano.

**Sklep:** Funkcionalnost profilnih slik **ne obstaja v produkcijskem kodu**. Coil knjižnica je naložena in poveča APK za ~1.2 MB brez učinka.

---

### 13.4 Firestore Poizvedbe in Indeksi

#### Aktivne Poizvedbe

| Lokacija | Poizvedba | Tip indeksa | Status |
|---|---|---|---|
| `WorkoutSessionViewModel.fetchLastSessionForFocus()` vrstica 219 | `workoutSessions.orderBy("timestamp", DESC).limit(10)` | Eno-poljna | Auto-index ✅ |
| `AppViewModel.startInitialSync()` vrstica 119 | `weightLogs.orderBy("date", DESC).limit(10)` | Eno-poljna | Auto-index ✅ |
| `FirestoreHelper.fetchLatestWeightKg()` vrstica 24 | `weightLogs.orderBy("date", DESC).limit(1)` | Eno-poljna | Auto-index ✅ |
| `FoodRepositoryImpl.observeCustomMeals()` vrstica 43 | `customMeals` full snapshot listener brez filtra | Ni poizvedbe | N/A |
| `FoodRepositoryImpl.observeDailyLog()` vrstica 58 | `dailyLogs/{dateId}` direct document read | Ni poizvedbe | N/A |

**Ugotovitev:** Aplikacija ne izvaja nobene sestavljene poizvedbe (`whereEqualTo` + `orderBy` ali multi-where), ki bi zahtevala ročno kreirani kompozitni indeks. **Ni nevarnosti za `FirebaseFirestoreException: FAILED_PRECONDITION: The query requires an index`** pri obstoječih poizvedbah.

#### Iskanje hrane

Iskanje hrane (FatSecret API) je REST klic prek `OkHttp` na zunanji strežnik — **ne Firestore**. `FoodRepositoryImpl.searchFoodByName()` (vrstica 22) delegira na `FatSecretApi.searchFoods()`. Ni lokalnega cachea za rezultate iskanja — vsak znak v search boxu sproži nov network klic, razen če je implementiran debounce v UI sloju.

#### Leaderboard

**Leaderboard funkcionalnost ne obstaja** v kodi. Ni nobenih poizvedb tipa `users.orderBy("xp", DESC).limit(10)`. Pojem se pojavlja le v dokumentaciji, ne v implementaciji.

---

### 13.5 Težki Izračuni — Niti in Dispatchers

#### Pregled vseh korutinskih kontekstov

| Operacija | Datoteka | Dispatcher | Ocena |
|---|---|---|---|
| `prepareWorkout()` — workout generiranje | `WorkoutSessionViewModel.kt` vrstica 115 | `Dispatchers.IO` | ✅ Varno |
| `AdvancedExerciseRepository.init()` — JSON parsanje | `AdvancedExerciseRepository.kt` vrstica 30 | `Dispatchers.Default` | ✅ Varno |
| `startInitialSync()` — 3× vzporeden Firestore fetch | `AppViewModel.kt` vrstica 101 | `Dispatchers.IO` | ✅ Varno |
| `CompressRouteUseCase.invoke()` — decimacija GPS točk | `CompressRouteUseCase.kt` vrstica 3 | **Noben (synchronous)** | ✅ O(100) — trivialno |
| `syncHealthConnectNow()` — HC branje + Firestore transakcija | `NutritionViewModel.kt` vrstica 273 | **`Dispatchers.Main` (default)** | ❌ Blokira UI nit |
| `observeDailyTotals()` — Firestore listener setup | `NutritionViewModel.kt` vrstica 200 | `viewModelScope` (Main) | ✅ Setup je non-blocking |
| `fetchLastSessionForFocus()` — Firestore `.get().await()` | `WorkoutSessionViewModel.kt` vrstica 216 | Znotraj `Dispatchers.IO` launch-a | ✅ Varno |

#### Kritična Anomalija: syncHealthConnectNow()

```kotlin
// NutritionViewModel.kt, vrstice 273–278
fun syncHealthConnectNow(context: Context) {
    viewModelScope.launch {                       // ← Dispatchers.Main (implicitno)
        val syncUseCase = SyncHealthConnectUseCase()
        syncUseCase(context)                      // ← Health Connect read + Firestore transaction
    }
}
```

`viewModelScope.launch { }` brez eksplicitnega dispatcherja privzeto teče na **`Dispatchers.Main`** (UI nit). `SyncHealthConnectUseCase.invoke()` izvaja:
1. `HealthConnectManager.readCalories()` — Android Health Connect API klici
2. `DailyLogRepository.updateDailyLog()` — Firestore transakcija (`.await()`)

`kotlinx.coroutines.tasks.await()` je sicer non-blocking (suspends, ne blocks), toda če `readCalories()` interno ne suspendira (odvisno od implementacije), bi blokiral Main nit.

> ⚠️ **Anomalija**: `NutritionViewModel.syncHealthConnectNow()` sproži Health Connect branje + Firestore transakcijo na `Dispatchers.Main` brez eksplicitnega `withContext(Dispatchers.IO)`.

#### CompressRouteUseCase — Brez Dispatcherja

```kotlin
// CompressRouteUseCase.kt — celotna implementacija
class CompressRouteUseCase {
    operator fun invoke(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val limit = 100
        if (points.size <= limit) return points
        val step = points.size / limit.toDouble()
        // O(100) loop — fiksno
        ...
    }
}
```

Algoritem je **vedno O(100)** (ne O(n)) — ne glede na dolžino GPS rute. Če je vhodnih 50.000 točk, zanka vseeno naredi natanko 100 iteracij. Ni nevarnosti blokiranja Main niti pri realističnih vhodih.

---

### 13.6 Povzetek Anomalij

| # | Tip | Lokacija | Opis | Resnost |
|---|---|---|---|---|
| 1 | Threading | `NutritionViewModel.kt` vrstica 273 | `syncHealthConnectNow()` brez `Dispatchers.IO` | 🔴 |
| 2 | Memory | `NutritionViewModel.kt` vrstica 200 | Dvojni inner collect — potencialno 2 Firestore listenerja ob uid spremembi | 🔴 |
| 3 | Race Condition | `FirestoreUserProfileRepository.kt` vrstica 16 | `listener` dodeljen znotraj async `launch` — null ob zgodnjem cancel | 🟡 |
| 4 | Dead Dep. | `app/build.gradle.kts` vrstica 120 | Coil `2.5.0` deklariran, nič ne kliče API-ja | 🟢 |
| 5 | Offline | `FoodRepositoryImpl.searchFoodByName()` | Iskanje hrane brez HTTP cache — vedno network, brez offline fallback | 🟡 |
| 6 | Offline | `set()` streak/profil polja | Last-write-wins brez conflict resolution — multi-device off-line scenariji | 🟡 |
| 7 | Feature Gap | `UserProfile.kt` | Ni `photoUrl` polja — profilne slike niso implementirane kljub Coil odvisnosti | 🟢 |

---

