# APP_MAP.md — Ground Truth
> **NAVODILO ZA AI:** Ko dobiš nalogo "popravi X", najprej poglej v to datoteko da ugotoviš KATERO datoteko odpreti. Ne ugibaj.

**Zadnja posodobitev:** 2026-05-03 (Faza 5 Clean Architecture + Faza 7 Audit)

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

## ARHITEKTURNI PREGLED (Clean Architecture Faza 5)

```
MainActivity (100 vrstic) — samo onCreate + setContent
    └── ui/MainAppContent.kt — vse Composable routing (30+ screeni)
            ├── appViewModel (AppViewModel.kt)
            ├── navViewModel (NavigationViewModel.kt)
            └── vsi screen Composables (Screen.XYZ → XYZScreen())

domain/model/     — čisti domenski modeli, brez Android
domain/usecase/   — posamezne logične operacije, ios-ready
data/repository/  — Firestore implementacije interfacev
data/settings/    — UserProfileManager (legacy, migrira v data/repository)
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

### Trening modul
| Datoteka | Kaj prikaže | Kaj popravljaš tu |
|----------|------------|-------------------|
| `BodyModuleHomeScreen.kt` | Home za Body tab: streak, dnevni plan, weekly progress | **"Start Stretching" gumb** (rest day only), Extra Workout, Streak UI |
| `BodyModule.kt` | 14-koračni kviz za ustvarjanje plana | Vprašanja v kvizu |
| `BodyOverviewScreen.kt` | Pregled obstoječih planov | Plan overview UI |
| `WorkoutSessionScreen.kt` | Aktivna vadba — timer, vaje, kalorije | Med-vadba UI |
| `GenerateWorkoutScreen.kt` | Extra workout — izbira fokusa in opreme | Extra workout generiranje |
| `LoadingWorkoutScreen.kt` | Loading animacija | Loading UI |
| `ManualExerciseLogScreen.kt` | Ročno beleženje vaje | Log vaje, kalorij izračun |
| `ExerciseHistoryScreen.kt` | Zgodovina vadb (Workouts, Exercises) | Prikaz vadb |
| `MyPlansScreen.kt` | Seznam vseh planov | Plan CRUD UI |
| `PlanPathVisualizer.kt` | Vizualni prikaz 4-tedne plana | Plan path vizual |
| `PlanPathDialog.kt` | **Swap dni v planu** — drag & drop + Firestore persist | Swap dni |
| `KnowledgeHubScreen.kt` | Baza znanja treninga | Knowledge hub |

### Prehrana modul
| Datoteka | Kaj prikaže |
|----------|------------|
| `NutritionScreen.kt` | Food tracking, makri, voda, donut graf |
| `AddFoodSheet.kt` | Bottom sheet za iskanje hrane |
| `BarcodeScannerScreen.kt` | Kamera za skeniranje barkode |
| `DonutProgressView.kt` | Custom Canvas donut graf |
| `NutritionComponents.kt` | Manjše UI komponente |
| `NutritionDialogs.kt` | Custom Meal dialog |

### 🔑 Prehranska logika — SSOT (Faza 9)
| Komponenta | Datoteka | Opis |
|-----------|---------|------|
| **Kalorični izračun (SSOT)** | `domain/usecase/CalculateDailyCalorieTargetUseCase.kt` | **EDINI vir resnice za dnevni kalorični cilj.** Mifflin-St Jeor + TDEE faktor + konzervativna ciljna prilagoditev. Kliče se iz BodyModule.kt (kviz) in NutritionViewModel. |
| Dinamični TDEE (ViewModel) | `viewmodels/NutritionViewModel.kt` → `setUserMetrics(bmr, goal, activityLevel)` | Delegira na UseCase. Upošteva WeightPredictorStore hibridni TDEE če na voljo. |
| Adaptivni hibridni TDEE | `utils/NutritionCalculations.kt` → `calculateAdaptiveTDEE()` | Iz realnih telesnih meritev (EMA teže × kalorije). Posodablja `WeightPredictorStore`. |
| Makro izračun | `utils/NutritionCalculations.kt` → `calculateOptimalMacros()` | Protein/ogljikohidrati/maščobe iz ciljnih kalorij. |

### Napredek in statistike
| Datoteka | Kaj prikaže |
|----------|------------|
| `Progress.kt` | 4 grafi: teža, kalorije, voda, burned |
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

### Tek modul
| Datoteka | Kaj prikaže |
|----------|------------|
| `RunTrackerScreen.kt` | GPS tek s live OSMDroid zemljevidom, vse activity types |
| `ActivityLogScreen.kt` | Celozaslonski zemljevid vseh aktivnosti |

### Profil in socialno
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

| Datoteka | Kaj dela | Kliče |
|----------|---------|-------|
| `viewmodels/BodyModuleHomeViewModel.kt` | Streak UI, `CompleteWorkoutSession`, `CompleteRestDay` (Stretching), `SwapDays` | `UpdateBodyMetricsUseCase`, `GetBodyMetricsUseCase`, `ManageGamificationUseCase`, `SwapPlanDaysUseCase` |
| `viewmodels/RunTrackerViewModel.kt` | GPS seje, Room offline | `FirestoreWorkoutRepository`, `OfflineFirstWorkoutRepository` |
| `ui/screens/BodyOverviewViewmodel.kt` | BMI, BF% izračuni | — |
| `viewmodels/NutritionViewModel.kt` | Food tracking, water, burnedCalories sync | `ManageGamificationUseCase`, `DailySyncManager` |

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

## 🔑 STREAK LOGIC — ODGOVORNOST PO DATOTEKAH (SSOT) — Faza 8 Unified

| Scenarij | Odgovorna datoteka | Funkcija |
|----------|-------------------|---------|
| Redni workout zaključen | `data/gamification/FirestoreGamificationRepository.kt` | `processWorkoutCompletion(incrementPlanDay=true)` |
| Extra workout (workout dan) | `data/gamification/FirestoreGamificationRepository.kt` | `processWorkoutCompletion(incrementPlanDay=false)` |
| **Extra workout REST dan** | **BLOKIRAN** — samo XP | `ManageGamificationUseCase.recordWorkoutCompletion(isRestDay=true)` guard |
| Rest day stretching (EDINI VELJAVNI NAČIN) | `ManageGamificationUseCase.kt` | `restDayInitiated()` → `repository.updateStreak("STRETCHING_DONE")` |
| Polnočni check (Worker) | `workers/WeeklyStreakWorker.kt` | `executeMidnightStreakCheck()` |
| Streak Freeze poraba | `data/gamification/FirestoreGamificationRepository.kt` | znotraj `processWorkoutCompletion()` (dayDiff guard) |
| Označi rest day PENDING | `domain/usecase/UpdateStreakUseCase.kt` | `markRestDayPending()` |
| ~~UserProfileManager.updateUserProgressAfterWorkout()~~ | ~~DEPRECATED~~ | ~~No-op stub — Faza 8~~ |

---

## 🔑 PLAN PATH LOGIC — ODGOVORNOST PO DATOTEKAH

| Scenarij | Odgovorna datoteka | Funkcija |
|----------|-------------------|---------|
| Prikaži plan path dialog | `ui/screens/PlanPathDialog.kt` | `PlanPathDialog()` composable |
| Swap dni (UI) | `ui/screens/PlanPathDialog.kt` | Drag & drop + confirm |
| Swap dni (Firestore persist) | `domain/workout/SwapPlanDaysUseCase.kt` + `persistence/PlanDataStore.kt` | `invoke()` + `updatePlan()` |
| Plan CRUD | `persistence/PlanDataStore.kt` | `addPlan`, `deletePlan`, `updatePlan` |
| Plan load v BodyHome | `domain/workout/GetBodyMetricsUseCase.kt` | `invoke(email)` → Flow<BodyHomeUiState> |

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
  last_workout_epoch:   Long (epochDays),
  streak_freezes:       Int,
  plan_day:             Int,
  xp:                   Int,
  profilePictureUrl:    String,
  dailyHistory: {
    "2026-05-03": "WORKOUT_DONE" | "STRETCHING_DONE" | "FROZEN" | "MISSED" | "PENDING_STRETCHING"
  }
}

user_plans/{uid}/plans/{planId}: { ... }
dailyLogs/{date}: { burnedCalories, calories, water }
workoutSessions/{uid}/{sessionId}: { timestamp, type, totalKcal, focusAreas, exercises, ... }
users/{uid}/publicActivities/{sessionId}: { polylinePoints (RDP compressed), activityType, ... }
follows/{uid_follower}_{uid_following}: { ... }
```

---

## ⚙️ OZADNI PROCESI

| Datoteka | Kdaj | Kaj dela |
|----------|------|---------|
| `workers/WeeklyStreakWorker.kt` | Vsako polnoč | `executeMidnightStreakCheck()` → dailyHistory |
| `workers/StreakReminderWorker.kt` | Ob določenem času | Push notifikacija za streak |
| `worker/DailySyncWorker.kt` | App v ozadje / ob odprtju | Food/water cache → Firestore |
| `service/RunTrackingService.kt` | Med aktivnim tekom | GPS v ozadju |

---

## 🗺️ HITRI VODIČ — "Kaj popraviti za X"

| Želiš popraviti | Odpri to datoteko |
|----------------|-------------------|
| **Streak (workout dan)** | `data/settings/UserProfileManager.kt` → `updateUserProgressAfterWorkout()` |
| **Streak (rest dan stretching)** | `domain/gamification/ManageGamificationUseCase.kt` → `restDayInitiated()` |
| **Streak domain (UseCase)** | `domain/usecase/UpdateStreakUseCase.kt` |
| **"Start Stretching" gumb (UI)** | `ui/screens/BodyModuleHomeScreen.kt` (rest day card sekcija) |
| **Extra Workout streak lock** | `domain/workout/UpdateBodyMetricsUseCase.kt` (guard `isExtra && isRestDay`) |
| **Streak Freeze logika** | `data/settings/UserProfileManager.kt` → `updateUserProgressAfterWorkout()` |
| **Plan swap (UI)** | `ui/screens/PlanPathDialog.kt` |
| **Plan swap (Firestore persist)** | `persistence/PlanDataStore.kt` → `updatePlan()` |
| **Face Analysis (kamera/galerija)** | `ui/screens/GoldenRatioScreen.kt` → `AutoAnalysisSection()` |
| **Face Analysis (algoritem)** | `domain/looksmaxing/CalculateGoldenRatioUseCase.kt` |
| **Camera foto ne prikaže** | `GoldenRatioScreen.kt` → `displayUri` / `cameraFileUri` / Coil `diskCachePolicy` |
| **Community tab** | `ui/home/CommunityScreen.kt` |
| **Firestore dokument routing** | `persistence/FirestoreHelper.kt` ⛔ ne obidi |
| **XP podeljevanje** | `domain/gamification/AchievementStore.kt` → `awardXP()` |
| **Badge unlock** | `ManageGamificationUseCase.kt` + `data/BadgeDefinitions.kt` |
| **Navigation med screeni** | `AppNavigation.kt` + `ui/MainAppContent.kt` routing blok |
| **Auth (login/logout)** | `ui/MainAppContent.kt` + `data/auth/AuthRepository.kt` |
| **Sync overlay** | `ui/MainAppContent.kt` (`isProfileReady`, `syncStatusMessage`) |
| **Dark mode** | `AppDrawer.kt` + `data/UserPreferences.kt` |
| **GPS tek** | `RunTrackerScreen.kt` + `service/RunTrackingService.kt` |
| **Tek tip (Run/Walk/Cycling...)** | `data/RunSession.kt` → `ActivityType` enum |
| **Food tracking** | `NutritionScreen.kt` + `DailySyncManager.kt` |
| **Kalorični cilj (TDEE)** | `domain/usecase/CalculateDailyCalorieTargetUseCase.kt` ⚡ SSOT |
| **Donut graf** | `NutritionComponents.kt` |
| **BMI/BF% izračun** | `ui/screens/BodyOverviewViewmodel.kt` |
| **Body home (streak, daily plan)** | `BodyModuleHomeScreen.kt` |
| **Widget streak/plan day** | `widget/StreakWidgetProvider.kt` + `widget/PlanDayWidgetProvider.kt` |

