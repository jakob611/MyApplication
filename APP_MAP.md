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

---

## NAVIGACIJA — kje se začne

| Datoteka | Kaj dela |
|----------|---------|
| `MainActivity.kt` | Vstopna točka. **100 vrstic.** Samo `onCreate`, `setContent { MainAppContent() }`, `firebaseAuthWithGoogle()`. |
| `ui/MainAppContent.kt` | **Koren vse UI logike** (ekstrakcija iz MainActivity Faza 5). Auth stanje, screen routing, Drawer, BottomBar, sync overlay, badge animacija. |
| `AppNavigation.kt` | `sealed class Screen` z vsemi zasloni + `AppBottomBar`. Če dodajaš nov screen → dodaj objekt tukaj. |
| `AppViewModel.kt` | `userProfile`, `syncStatusMessage`, `isProfileReady` StateFlow za celo aplikacijo. |
| `NavigationViewModel.kt` | Back stack (`navigateTo`, `navigateBack`, `replaceTo`, `clearStack`, `popTo`). |
| `AppDrawer.kt` | Stranski meni: profil, dark mode, odjava. |

---

## 🏗️ CLEAN ARCHITECTURE — DOMAIN SLOJ

### domain/model/ — pure Kotlin, 0 Android odvisnosti
| Datoteka | Model | Opis |
|----------|-------|------|
| `domain/model/Streak.kt` | `Streak(days, freezes, todayStatus)` | Domenski model za streak. Computed: `isActive`, `isTodayCompleted`, `isTodayFrozen`. KMP-ready. |
| `domain/model/UserPlan.kt` | `UserPlan(days, createdAt, targetPerWeek)` | Domenski wrapper za plan. KMP-ready. |

### domain/usecase/ — business logika, iOS-ready
| Datoteka | Kaj dela | Klici |
|----------|---------|-------|
| `domain/usecase/UpdateStreakUseCase.kt` | **EDINI domenski vhod za streak posodobitve.** `workout()`, `restDayStretching()`, `runMidnightCheck()`, `markRestDayPending()`, `getCurrentStreak()` | `GamificationRepository` |
| `domain/usecase/CalculateDailyCalorieTargetUseCase.kt` | **⚡ SSOT za kalorični izračun (Faza 9).** Mifflin-St Jeor BMR + TDEE faktor aktivnosti + konzervativna ciljna prilagoditev (−500/0/+300). `invoke(Input)` = polni izračun iz bio-metrik; `fromBmr(bmr, goal, activityLevel)` = ko je BMR že znan. | `utils/NutritionCalculations.kt` |
| `domain/workout/GetBodyMetricsUseCase.kt` | Bere profil in plan iz Firestore → `BodyHomeUiState` Flow | `WorkoutRepository`, `UserPreferencesRepository` |
| `domain/workout/UpdateBodyMetricsUseCase.kt` | Zapiše workout sejo + sproži XP logiko. **Guard: `isExtra && isRestDay` → samo XP, brez streak.** | `WorkoutRepository`, `ManageGamificationUseCase`, `UserProfileManager.updateUserProgressAfterWorkout()` |
| `domain/workout/SwapPlanDaysUseCase.kt` | Zamenjaj dan A ↔ dan B v planu. **Guard: `lockedDay` parameter — danes opravljen dan ne sme biti swap-an (Faza 9).** | pure Kotlin |
| `domain/gamification/ManageGamificationUseCase.kt` | XP, badge, `restDayInitiated()`, Workout-Nutrition Bridge. **NE kliče `repository.updateStreak()` za redne workouty** (Faza 7 Audit). | `GamificationRepository`, `DailyLogRepository` |

### ⚠️ ARHITEKTURNA OPOMBA — Unified Streak Engine (Faza 8)
> **Stanje (2026-05-03):** POENOTEN — Dual Streak Engine ELIMINIRAN ✅
> - **Redni workout**: `FirestoreGamificationRepository.processWorkoutCompletion(incrementPlanDay=true)` — epoch+freeze+dailyHistory ✅
> - **Extra workout (workout dan)**: `FirestoreGamificationRepository.processWorkoutCompletion(incrementPlanDay=false)` — streak računa, plan_day ne ✅
> - **Extra workout REST dan**: BLOKIRAN v `ManageGamificationUseCase.recordWorkoutCompletion(isRestDay=true)` — samo XP ✅
> - **Rest day stretching**: `ManageGamificationUseCase.restDayInitiated()` → `repository.updateStreak("STRETCHING_DONE")` ✅
> - **UserProfileManager.updateUserProgressAfterWorkout()**: DEPRECATED no-op stub ✅
>
> SSOT: `FirestoreGamificationRepository` piše dailyHistory za VSE poti.
> `UserProfileManager` bere samo za prikaz v profilu.

---

## 🖥️ SCREENS — UI datoteke

### Pred prijavo
| Datoteka | Kaj prikaže |
|----------|------------|
| `Indexscreen.kt` | Splash/welcome screen |
| `LoginScreen.kt` | Prijava z emailom ali Google Sign-In |

### Po prijavi — glavna navigacija
| Datoteka | Kaj prikaže |
|----------|------------|
| `DashboardScreen.kt` | Glavni home screen po prijavi |

### Trening modul 🆕 (premaknjeno v `ui/workout/`)
| Datoteka | Paket | Kaj prikaže |
|----------|-------|-------------|
| `BodyModuleHomeScreen.kt` | `ui/screens/` | Home za Body tab: streak, dnevni plan, weekly progress |
| `BodyModule.kt` | `ui/screens/` | 14-koračni kviz za ustvarjanje plana |
| `BodyOverviewScreen.kt` | `ui/screens/` | Pregled obstoječih planov |
| `WorkoutSessionScreen.kt` | `ui/workout/` 🆕 | Aktivna vadba — timer, vaje, kalorije |
| `GenerateWorkoutScreen.kt` | `ui/workout/` 🆕 | Extra workout — izbira fokusa in opreme |
| `LoadingWorkoutScreen.kt` | `ui/workout/` 🆕 | Loading animacija |
| `ManualExerciseLogScreen.kt` | `ui/workout/` 🆕 | Ročno beleženje vaje |
| `ExerciseHistoryScreen.kt` | `ui/workout/` 🆕 | Zgodovina vadb (Workouts, Exercises) |
| `MyPlansScreen.kt` | `ui/screens/` | Seznam vseh planov |
| `PlanPathVisualizer.kt` | `ui/run/` 🆕 | Vizualni prikaz 4-tedne plana |
| `PlanPathDialog.kt` | `ui/run/` 🆕 | **Swap dni v planu** — drag & drop + Firestore persist |
| `KnowledgeHubScreen.kt` | `ui/screens/` | Baza znanja treninga |

### Tek modul 🆕 (premaknjeno v `ui/run/`)
| Datoteka | Paket | Kaj prikaže |
|----------|-------|-------------|
| `RunTrackerScreen.kt` | `ui/run/` 🆕 | GPS tek s live OSMDroid zemljevidom, vse activity types |
| `RunTrackerViewModel.kt` | `ui/run/` 🆕 | GPS seje, Room offline |
| `ActivityLogScreen.kt` | `ui/screens/` | Celozaslonski zemljevid vseh aktivnosti |
| Datoteka | Kaj prikaže |
|----------|------------|
| `NutritionScreen.kt` | Food tracking, makri, voda, donut graf |
| `AddFoodSheet.kt` | Bottom sheet za iskanje hrane |
| `BarcodeScannerScreen.kt` | Kamera za skeniranje barkode |
| `DonutProgressView.kt` | Custom Canvas donut graf |
| `NutritionComponents.kt` | Manjše UI komponente |
| `NutritionDialogs.kt` | Custom Meal dialog |

### 🔑 Prehranska logika — SSOT (Faza 9 + Faza 10 + Faza 17 Refactoring)
| Komponenta | Datoteka | Opis |
|-----------|---------|------|
| **Kalorični izračun (SSOT)** | `domain/usecase/CalculateDailyCalorieTargetUseCase.kt` | **EDINI vir resnice za dnevni kalorični cilj.** Hibridni BMR motor: **Katch-McArdle** (če BF% poznan) ali **Mifflin-St Jeor fallback** + konzervativni FAO/WHO TDEE faktorji + ciljna prilagoditev. Kliče se iz BodyModule.kt (kviz) in NutritionViewModel. |
| Hibridni BMR motor (Faza 10) | `CalculateDailyCalorieTargetUseCase.calculateBmr()` | Katch-McArdle: `lbm = weight×(1−BF%/100); BMR=370+21.6×lbm`. Pogoj: BF% != null && BF% ∈ (0,60). Sicer Mifflin-St Jeor z starostno korekcijo. |
| Konzervativni TDEE faktorji (Faza 10) | `CalculateDailyCalorieTargetUseCase.activityFactor()` | SEDENTARY=1.20 \| LIGHT(2x)=1.375 \| **MODERATE(3x)=1.45** \| **ACTIVE(4x)=1.65** \| **EXTREME(5x)=1.85** \| MAX(6x)=2.00. Znižano vs. standard za preprečevanje overshoot. |
| Dinamični TDEE (ViewModel) | `viewmodels/NutritionViewModel.kt` → `setUserMetrics(bmr, goal, activityLevel, bodyFatPercentage)` | Delegira na UseCase. Upošteva WeightPredictorStore hibridni TDEE če na voljo. |
| **🆕 Vse prehranske kalkulacije (SSOT)** | `domain/nutrition/NutritionCalculations.kt` | `calculateAdvancedBMR()`, `calculateEnhancedTDEE()`, `calculateSmartCalories()`, `calculateOptimalMacros()`, `calculateAdaptiveTDEE()`, `calculateDailyWaterMl()`, `calculateRestDayCalories()`, `calculateEMA()` — **EDINA lokacija, vse ostalo je dead code!** |

> ⚠️ **Faza 10 opomba**: Vključena Katch-McArdle formula za uporabnike z znanim BF% ter znižani, konzervativni TDEE faktorji (Moderate = 1.45, Active = 1.65). Vse spremembe so v `CalculateDailyCalorieTargetUseCase.kt` — brez android odvisnosti (KMP-ready).

### Profil in socialno
| Datoteka | Kaj prikaže |
|----------|------------|
| `MyAccountScreen.kt` | Nastavitve računa |
| `PublicProfileScreen.kt` | Javni profil |
| `ui/home/CommunityScreen.kt` | Community tab — iskalnik, leaderboard |
| `LevelPathScreen.kt` | Level path + followers/following dialogi |

### Napredek in statistike
| Datoteka | Kaj prikaže |
|----------|------------|
| `ui/progress/Progress.kt` 🆕 | 4 grafi: teža, kalorije, voda, burned |
| `GoldenRatioScreen.kt` | **Face Analysis** — ML Kit detekcija + golden ratio |
| `AchievementsScreen.kt` | XP bar, level, XP history |
| `BadgesScreen.kt` | Grid badge-ev |
| `LevelPathScreen.kt` | Level path + followers/following dialogi |

### Face modul — ODGOVORNE DATOTEKE ZA FACE ANALYSIS
| Datoteka | Kaj dela | Opomba |
|----------|---------|--------|
| `FaceModule.kt` | Face modul home — Skincare, Face Exercises, Golden Ratio | `onGoldenRatio` → `Screen.GoldenRatio` |
| `GoldenRatioScreen.kt` | **Face Analysis zaslon** — ML Kit + Golden Ratio algoritem | **Camera FIX**: `displayUri`(AsyncImage) ↔ `cameraFileUri`(launch) ločeno. Coil `diskCachePolicy=DISABLED`. `rememberSaveable` za config change. |
| `domain/looksmaxing/CalculateGoldenRatioUseCase.kt` | Golden Ratio izračun | iOS-ready |
| `data/looksmaxing/AndroidMLKitFaceDetector.kt` | Android ML Kit face detekcija | Android (data layer) |

### Prehrana modul
| Datoteka | Kaj prikaže |
|----------|------------|
| `MyAccountScreen.kt` | Nastavitve računa |
| `PublicProfileScreen.kt` | Javni profil |
| `ui/home/CommunityScreen.kt` | Community tab — iskalnik, leaderboard |
| `LevelPathScreen.kt` | Level path + followers/following dialogi |

### Ostali screeni
| Datoteka | Kaj prikaže |
|----------|------------|
| `HealthConnectScreen.kt` | Android Health Connect integracija |
| `HairModuleScreen.kt` | Hair analysis modul |
| `EAdditivesScreen.kt` | E-aditivi baza (assets/e_additives_database.json) |
| `ShopScreen.kt` | Hair produkti |
| `ProFeaturesScreen.kt` | Pro funkcij prikaz |
| `DeveloperSettingsScreen.kt` | Dev tools |

---

## 🧠 VIEWMODELS IN LOGIKA

| Datoteka | Paket | Kaj dela | Kliče |
|----------|-------|---------|-------|
| `viewmodels/BodyModuleHomeViewModel.kt` | `viewmodels/` | Streak UI, `CompleteWorkoutSession`, `CompleteRestDay` (Stretching), `SwapDays` | `UpdateBodyMetricsUseCase`, `GetBodyMetricsUseCase`, `ManageGamificationUseCase`, `SwapPlanDaysUseCase` |
| `ui/run/RunTrackerViewModel.kt` | `ui/run/` 🆕 | GPS seje, Room offline | `FirestoreWorkoutRepository`, `OfflineFirstWorkoutRepository` |
| `ui/screens/BodyOverviewViewmodel.kt` | `ui/screens/` | BMI, BF% izračuni | — |
| `ui/nutrition/NutritionViewModel.kt` | `ui/nutrition/` 🆕 | Food tracking, water, burnedCalories sync | `ManageGamificationUseCase`, `DailySyncManager` |
| `ui/progress/ProgressViewModel.kt` | `ui/progress/` 🆕 | Grafi (teža, kalorije, voda, burned) | `ManageGamificationUseCase` |
| `ui/shared/GamificationSharedViewModel.kt` | `ui/shared/` 🆕 | Skupni gamification state (badges, XP) | `ManageGamificationUseCase` |
| `ui/workout/WorkoutSessionViewModel.kt` | `ui/workout/` 🆕 | Workout seje, timer, set management | `AdvancedExerciseRepository` |

---

## 💾 PERSISTENCE — shranjevanje podatkov

### ⛔ PRAVILA: Nikoli ne obidi teh!
| Pravilo | Pravilno | Prepovedano |
|---------|---------|-------------|
| Firestore dokument write | `FirestoreHelper.getCurrentUserDocRef()` | `db.collection("users").document(uid)` direktno |
| XP podeljevanje | `AchievementStore.awardXP()` | `addXPWithCallback()` |
| Badge zahteve | `badge.requirement` iz `BadgeDefinitions` | hardcoded števila |
| Badge progress | `AchievementStore.getBadgeProgress(badgeId, profile)` | lokalna when() logika |

### Shranjevanje po področjih
| Datoteka | Kaj shrani | Opomba |
|----------|-----------|--------|
| `persistence/FirestoreHelper.kt` | Email↔UID resolver, cache | ⛔ EDINI VHOD |
| `domain/gamification/AchievementStore.kt` | XP, badge-e, workout completion | ⛔ EDINI XP/BADGE VHOD |
| `persistence/PlanDataStore.kt` | Plan CRUD | kolekcija `user_plans` (NE `users`!) |
| `data/gamification/FirestoreGamificationRepository.kt` | Streak `dailyHistory`, XP, badges | `updateStreak()` = rest day stretching pot |
| `data/settings/UserProfileManager.kt` | Profil Firestore R/W, **`updateUserProgressAfterWorkout()`** (epoch streak za workouty) | ⚠️ Vsebuje streak Engine Faza 13.3, migracija v toku |
| `data/auth/AuthRepository.kt` | Sign-out, clearCache, FCM token clear | Edini vhod za odjavo |
| `data/local/AppDatabase.kt` | Room singleton `glow_upp_offline.db` | WorkoutSessionEntity, GpsPointEntity |
| `data/local/OfflineFirstWorkoutRepository.kt` | Offline-first teki: Room + Firestore delta sync | `sessionsFlow` Flow |
| `persistence/ProfileStore.kt` | Javni profil, iskanje, privacy | Za PublicProfileScreen |
| `persistence/FollowStore.kt` | follow/unfollow | kolekcija `follows` |
| `DailySyncManager.kt` | Food/water local cache ↔ Firestore | WorkManager sync |

---

## 🔑 STREAK LOGIC — ODGOVORNOST PO DATOTEKAH (SSOT) — Faza 12 Unified

| Scenarij | Odgovorna datoteka | Funkcija | dailyHistory status |
|----------|-------------------|---------|---------------------|
| Redni workout zaključen | `data/gamification/FirestoreGamificationRepository.kt` | `processActivityCompletion(isRestDay=false, incrementPlanDay=true)` | `"WORKOUT_DONE"` |
| Extra workout (workout dan) | `data/gamification/FirestoreGamificationRepository.kt` | `processActivityCompletion(isRestDay=false, incrementPlanDay=false)` | `"WORKOUT_DONE"` |
| **Extra workout REST dan** | **`data/gamification/FirestoreGamificationRepository.kt`** | `processActivityCompletion(isRestDay=true, incrementPlanDay=false)` → streak se OHRANI, NE poveča | `"REST_WORKOUT_DONE"` |
| Rest day stretching (EDINI VELJAVNI NAČIN) | `domain/gamification/ManageGamificationUseCase.kt` | `restDayInitiated()` → `repository.updateStreak("STRETCHING_DONE")` | `"STRETCHING_DONE"` |
| Polnočni check (Worker) | `workers/WeeklyStreakWorker.kt` | `executeMidnightStreakCheck()` → `repository.runMidnightStreakCheck()` | `"FROZEN"` ali `"MISSED"` |
| Streak Freeze poraba | `data/gamification/FirestoreGamificationRepository.kt` | znotraj `processActivityCompletion()` (dayDiff guard) | `"FROZEN"` (midnight check) |
| Označi rest day PENDING | `domain/usecase/UpdateStreakUseCase.kt` | `markRestDayPending()` | `"PENDING_STRETCHING"` |
| ~~UserProfileManager.updateUserProgressAfterWorkout()~~ | ~~DEPRECATED~~ | ~~No-op stub~~ | ~~—~~ |

> 🔑 **SSOT pravilo**: `dailyHistory` pišeta SAMO `FirestoreGamificationRepository` (workout/rest workflow) in `ManageGamificationUseCase.restDayInitiated()` (stretching). Nobena druga koda ne sme pisati v `dailyHistory`!
>
> ✅ **Strogi statusni check** (Faza 12c): Midnight check preveri VREDNOST, ne zgolj obstoj ključa. Samo `WORKOUT_DONE`, `REST_WORKOUT_DONE`, `STRETCHING_DONE`, `FROZEN` osvobodijo od kazni. `PENDING_STRETCHING` in `null` → kazen (Freeze ali reset).

---

## 🔑 PLAN PATH LOGIC — ODGOVORNOST PO DATOTEKAH

| Scenarij | Odgovorna datoteka | Funkcija |
|----------|-------------------|---------|
| Prikaži plan path dialog | `ui/run/PlanPathDialog.kt` 🆕 | `PlanPathDialog()` composable |
| Swap dni (UI guard) | `ui/run/PlanPathDialog.kt` 🆕 | `isTodayDone && (fromDay == currentDay || toDay == currentDay)` → Toast opozorilo, swap blokiran |
| Swap dni (domenski guard) | `domain/usecase/SwapPlanDaysUseCase.kt` | `lockedDay` parameter — danes opravljen dan ne sme biti swapan ✅ **POPRAVLJENO Faza 17 Audit** |
| Swap dni (Firestore persist) | `data/store/PlanDataStore.kt` 🆕 | `updatePlan()` |
| Plan CRUD | `data/store/PlanDataStore.kt` 🆕 | `addPlan`, `deletePlan`, `updatePlan` |
| Plan load v BodyHome | `domain/usecase/GetBodyMetricsUseCase.kt` | `invoke(email)` → Flow<BodyHomeUiState> |

> ✅ **Audit fix**: `BodyModuleHomeViewModel.SwapDays` handler zdaj pravilno posreduje `lockedDay = planDay` (če `isWorkoutDoneToday == true`) v domenski UseCase. Pred tem domenski guard ni bil nikoli sprožen.

---

## 🔑 FACE ANALYSIS — ODGOVORNOST PO DATOTEKAH

| Scenarij | Odgovorna datoteka | Funkcija |
|----------|-------------------|---------|
| Face modul home | `ui/screens/FaceModule.kt` | `FaceModuleScreen(onGoldenRatio = ...)` |
| Navigate → Golden Ratio | `ui/MainAppContent.kt` | `Screen.GoldenRatio → GoldenRatioScreen()` |
| Camera foto zajem | `ui/screens/GoldenRatioScreen.kt` | `AutoAnalysisSection()` — `cameraFileUri` za launch, `displayUri` za prikaz |
| Galerija foto zajem | `ui/screens/GoldenRatioScreen.kt` | `galleryLauncher` → `displayUri = uri` |
| ML Kit detekcija | `data/looksmaxing/AndroidMLKitFaceDetector.kt` | `detectFace(uri)` |
| Golden ratio izračun | `domain/looksmaxing/CalculateGoldenRatioUseCase.kt` | `distance()`, score formula |

---

## 🔑 FIRESTORE SCHEMA (SSOT za polja)

```
users/{uid}: {
  streak_days:          Int,
  last_activity_epoch:  Long (epochDays) — pokriva workout IN stretching (NE last_workout_epoch!),
  streak_freezes:       Int,
  plan_day:             Int,
  xp:                   Int,
  level:                Int,
  profilePictureUrl:    String,
  dailyHistory: {
    "2026-05-17": "WORKOUT_DONE" | "REST_WORKOUT_DONE" | "STRETCHING_DONE" | "FROZEN" | "MISSED" | "PENDING_STRETCHING"
  }
}

user_plans/{uid}/plans/{planId}: { weeks: [...] }
dailyLogs/{date}: { burnedCalories, calories, water, userId }
workoutSessions/{uid}/{sessionId}: { timestamp, type, totalKcal, focusAreas, exercises, planDay, ... }
users/{uid}/xp_history/{docId}: { amount, reason, date, timestamp, xpAfter, levelAfter }
users/{uid}/publicActivities/{sessionId}: { polylinePoints (RDP compressed, max 500 pts), activityType, ... }
follows/{uid_follower}_{uid_following}: { ... }
```

---

## ⚙️ OZADNI PROCESI

| Datoteka | Paket | Kdaj | Kaj dela |
|----------|-------|------|---------|
| `workers/WeeklyStreakWorker.kt` | `workers/` | Vsako polnoč (00:01) | `executeMidnightStreakCheck()` → delegira na `FirestoreGamificationRepository.runMidnightStreakCheck()`. Samodejno se reschedula za naslednjo polnoč. |
| `workers/StreakReminderWorker.kt` | `workers/` | Ob določenem času | Push notifikacija za streak |
| `core/worker/DailySyncWorker.kt` | `core/worker/` | App v ozadje / ob odprtju | Food/water cache → Firestore |
| `service/RunTrackingService.kt` | `service/` | Med aktivnim tekom | GPS v ozadju, Android 14+ `FOREGROUND_SERVICE_TYPE_LOCATION` |

> ⚠️ **OPOMBA**: Mapa `worker/` (brez 's') je zdaj **prazna** — `DailySyncWorker` je bil premaknjen v `core/worker/`. Ne ustvarjaj novih workerjev v `worker/`!

---

## 🗺️ HITRI VODIČ — "Kaj popraviti za X"

> ⚠️ **Figma Design System (Faza 17):** Vse UI datoteke z gumbi/karticami/vhodnimi polji mora uporabiti `UppColors` in `UppComponents`. Barvna specifikacija: Orange #FF6411 · Blue #648DE5 · LightGray #E0E2DB · Background #181818 · White #FCFCFC. Skupne komponente: `UppPrimaryButton`, `UppCard`, `UppTextField`, `UppGoogleButton`. Gradient (`OrangeToBlackGradient`) SAMO za naslovne module.

| Želiš popraviti | Odpri to datoteko |
|----------------|-------------------|
| **Streak (workout dan)** | `data/gamification/FirestoreGamificationRepository.kt` → `processActivityCompletion()` ⚡ SSOT |
| **Streak (rest dan stretching)** | `domain/gamification/ManageGamificationUseCase.kt` → `restDayInitiated()` |
| **Streak domain (UseCase)** | `domain/usecase/UpdateStreakUseCase.kt` |
| **"Start Stretching" gumb (UI)** | `ui/screens/BodyModuleHomeScreen.kt` → `BodyHomeIntent.CompleteRestDay` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. |
| **Extra Workout streak lock** | `domain/usecase/UpdateBodyMetricsUseCase.kt` → `isRestDay = isRestDay && isExtra` guard |
| **Streak Freeze logika** | `data/gamification/FirestoreGamificationRepository.kt` → `processActivityCompletion()` dayDiff guard |
| **Polnočni streak check** | `workers/WeeklyStreakWorker.kt` → `executeMidnightStreakCheck()` |
| **Plan swap (UI + guard)** | `ui/run/PlanPathDialog.kt` → `isTodayDone` guard |
| **Plan swap (domenski guard)** | `domain/usecase/SwapPlanDaysUseCase.kt` → `lockedDay` parameter |
| **Plan swap (Firestore persist)** | `data/store/PlanDataStore.kt` → `updatePlan()` |
| **Face Analysis (kamera/galerija)** | `ui/screens/GoldenRatioScreen.kt` → `AutoAnalysisSection()` |
| **Face Analysis (algoritem)** | `domain/looksmaxing/CalculateGoldenRatioUseCase.kt` |
| **Camera foto ne prikaže** | `GoldenRatioScreen.kt` → `displayUri` / `cameraFileUri` / Coil `diskCachePolicy` |
| **Community tab** | `ui/home/CommunityScreen.kt` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. |
| **Firestore dokument routing** | `data/store/FirestoreHelper.kt` ⛔ ne obidi |
| **XP podeljevanje** | `domain/gamification/ManageGamificationUseCase.kt` → `awardXP()` |
| **Badge unlock** | `domain/gamification/ManageGamificationUseCase.kt` + `data/BadgeDefinitions.kt` |
| **Navigation med screeni** | `AppNavigation.kt` + `ui/MainAppContent.kt` routing blok |
| **Auth (login/logout)** | `ui/MainAppContent.kt` + `data/auth/AuthRepository.kt` |
| **Sync overlay** | `ui/MainAppContent.kt` (`isProfileReady`, `syncStatusMessage`) |
| **Dark mode** | `AppDrawer.kt` + `data/UserPreferences.kt` |
| **GPS tek** | `ui/run/RunTrackerScreen.kt` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. + `service/RunTrackingService.kt` |
| **GPS anti-drift / route compression** | `domain/run/RouteCompressor.kt` (RDP) |
| **Tek tip (Run/Walk/Cycling...)** | `data/workout/RunSession.kt` → `ActivityType` enum |
| **Workout generiranje** | `domain/workout/WorkoutGenerator.kt` + `WorkoutPlanGenerator.kt` |
| **Vaje baza (exercises.json init)** | `data/repository/AdvancedExerciseRepository.kt` → `init()` pred `getAllExercises()` |
| **Workout UI (aktivna vadba)** | `ui/workout/WorkoutSessionScreen.kt` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. |
| **Extra workout generiranje** | `ui/workout/GenerateWorkoutScreen.kt` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. |
| **Ročno beleženje vaje** | `ui/workout/ManualExerciseLogScreen.kt` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. |
| **Workout zgodovina** | `ui/workout/ExerciseHistoryScreen.kt` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. |
| **Food tracking** | `ui/screens/NutritionScreen.kt` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. + `data/store/DailySyncManager.kt` |
| **Prehranske kalkulacije (TDEE/BMR/voda)** | `domain/nutrition/NutritionCalculations.kt` ⚡ SSOT |
| **Kalorični cilj (UseCase)** | `domain/usecase/CalculateDailyCalorieTargetUseCase.kt` ⚡ |
| **Donut graf** | `ui/screens/NutritionComponents.kt` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. |
| **BMI/BF% izračun** | `ui/screens/BodyOverviewViewmodel.kt` |
| **Body home (streak, daily plan)** | `ui/screens/BodyModuleHomeScreen.kt` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. |
| **BodyHome ViewModel** | `viewmodels/BodyModuleHomeViewModel.kt` |
| **Grafi (teža, kalorije, voda)** | `ui/progress/Progress.kt` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. + `ui/progress/ProgressViewModel.kt` |
| **Widget streak/plan day** | `widget/StreakWidgetProvider.kt` + `widget/PlanDayWidgetProvider.kt` |
| **Daily food/water sync (Worker)** | `core/worker/DailySyncWorker.kt` |
| **Dashboard moduli** | `ui/screens/DashboardScreen.kt` ⚠️ Vizualni prenos iz Figme: oranžni gumbi, temna siva obroba za kartice, KMP-ready barve. |
| **Splash / onboarding** | `ui/screens/Indexscreen.kt` ✅ Figma aligned |
| **Login / Sign-up / Forgot Password** | `ui/screens/LoginScreen.kt` ✅ Figma aligned |
| **Skupne UI komponente (SSOT)** | `ui/components/UppComponents.kt` — `UppPrimaryButton`, `UppCard`, `UppTextField`, `UppGoogleButton`, `GradientHeaderText` |
| **Barve sistema (SSOT)** | `ui/theme/UppColors.kt` — vse barve, `ui/theme/theme.kt` — Material3 tema |


