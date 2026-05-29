# 🏗️ SYSTEM ARCHITECTURE AUDIT — GlowUpp
**Datum revizije:** 2026-05-29  
**Revizor:** GitHub Copilot (Principal Engineer)  
**Obseg:** BodyModule (plan_day logika) + Gamification/Streak sistem  
**Metodologija:** Analiza IZKLJUČNO na podlagi izvršljive Kotlin kode — komentarji ignorirani.

---

## 1. MODULNA KATEGORIZACIJA DATOTEK

### 📦 A — Nutrition

| Datoteka | Sloj | Vloga |
|---|---|---|
| `data/store/NutritionPlanStore.kt` | Data | Firestore CRUD za prehranjevalne plane |
| `data/daily/DailyLogRepository.kt` | Data | Dnevni vnosi hrane / vode |
| `data/nutrition/` | Data | Implementacije nutricion repozitorija |
| `domain/nutrition/` | Domain | Nutrition use case-i |
| `domain/usecase/CalculateDailyCalorieTargetUseCase.kt` | Domain | Izračun dnevnega kaloričnega cilja |
| `domain/usecase/GetNutritionTargetsUseCase.kt` | Domain | Pridobitev tarč makrohranil |
| `ui/screens/NutritionScreen.kt` | UI | Zaslon za vnos obrokov |
| `ui/screens/NutritionComponents.kt` | UI | Kompozitne komponente za prehrano |
| `ui/screens/NutritionDialogs.kt` | UI | Dialogi za dodajanje hrane |
| `ui/screens/NutritionModels.kt` | UI | UI modeli za prehrano |

---

### 📦 B — BodyModule (Plan / Day Logic)

| Datoteka | Sloj | Vloga |
|---|---|---|
| `data/store/PlanDataStore.kt` | Data | Firestore + lokalnega DataStore CRUD za plane (SSOT write točka za `user_plans`) |
| `data/repository/PlanRepositoryImpl.kt` | Data | Implementacija `PlanRepository` — delegira na `PlanDataStore` |
| `data/repository/UserWorkoutStatsRepository.kt` | Data | Firestore snapshot listener za `WorkoutStats` (bere `plan_day`, `streak_days` itd.) |
| `domain/repository/PlanRepository.kt` | Domain | Interface za plan operacije (`observePlans`, `swapDays`) |
| `domain/repository/WorkoutStatsRepository.kt` | Domain | Interface za branje workout statistik (skupaj z `WorkoutStats` data class) |
| `domain/usecase/GetBodyMetricsUseCase.kt` | Domain | Reaktivni flow `Flow<Result<BodyMetrics>>` prek `WorkoutStatsRepository.observeWorkoutStats()` |
| `domain/usecase/UpdateBodyMetricsUseCase.kt` | Domain | Orkestrator zaključka treninga; gradi `workoutDoc` in delegira na `ManageGamificationUseCase.recordWorkoutCompletion()` |
| `domain/usecase/SwapPlanDaysUseCase.kt` | Domain | Domenška validacija + klic `PlanRepository.swapDays()` za atomarno Firestore transakcijo |
| `domain/model/BodyMetrics.kt` | Domain | Čisti domenski model (streak, plan_day, weeklyDone…) |
| `domain/model/PlanModels.kt` | Domain | `PlanResult`, `WeekPlan`, `DayPlan` modeli |
| `domain/model/UserDayStatus.kt` | Domain | Tipsko-varni enum za stanje dneva (WORKOUT_DONE, REST_DAY_DONE…) |
| `viewmodels/BodyModuleHomeViewModel.kt` | Presentation | Glavni ViewModel za BodyModule; obdeluje vse intente; emitira `BodyUiState` |
| `ui/screens/BodyModuleHomeScreen.kt` | UI | Compose zaslon; zbira stanje iz VM; pošilja intente |
| `ui/screens/BodyModule.kt` | UI | Navigacijska lupina BodyModula |
| `ui/screens/BodyOverviewScreen.kt` | UI | Pregled vadbenega plana |
| `ui/screens/BodyOverviewViewmodel.kt` | UI | ViewModel za pregled plana (bere `PlanRepository.observePlans()`) |

---

### 📦 C — Gamification (Streak / XP)

| Datoteka | Sloj | Vloga |
|---|---|---|
| `data/gamification/FirestoreGamificationRepository.kt` | Data | Edina konkretna implementacija `GamificationRepository`; vse Firestore transakcije za streak, XP, plan_day |
| `data/gamification/GamificationFactory.kt` | Data | Singleton factory za `ManageGamificationUseCase` (composition root) |
| `domain/gamification/GamificationRepository.kt` | Domain | Interface (contract) za gamification operacije |
| `domain/gamification/ManageGamificationUseCase.kt` | Domain | Orchestrator: `recordWorkoutCompletion()`, `restDayInitiated()`, `awardXP()`, `executeMidnightStreakCheck()` |
| `workers/WeeklyStreakWorker.kt` | Worker | Midnight check — sproži `ManageGamificationUseCase.executeMidnightStreakCheck()` vsak dan ob 00:01 |
| `workers/StreakReminderWorker.kt` | Worker | Ob 20:00 preveri stanje vadbe in pošlje notifikacijo; bere iz `UserProfileManager.getWorkoutStats()` |

---

### 📦 D — Core UI / Infrastructure

| Datoteka | Sloj | Vloga |
|---|---|---|
| `data/store/FirestoreHelper.kt` | Core | SSOT za `getCurrentUserDocRef()` — email-first z UID fallback; cache guard |
| `data/store/DailySyncManager.kt` | Core | Orkestrator `DailySyncWorker`-ja ob odprtju aplikacije |
| `ui/screens/MyViewModelFactory.kt` | Core | Ročna DI factory za vse ViewModele |
| `AppNavigation.kt` | Core | Navigacijski graf |
| `MainActivity.kt` | Core | Entry point; inicializacija workerjev |
| `domain/model/DomainException.kt` | Core | `AuthenticationExpired`, `NetworkFailure` za čisto error propagacijo |

---

## 2. PRETOK PODATKOV — BodyModule (`plan_day` + `streak_days`)

### 2.1 READ PATH — Firestore → UI

```
Firestore (users/{docId})
    ↓ addSnapshotListener [UserWorkoutStatsRepository.observeWorkoutStats()]
    ↓   Bere polja: "streak_days", "plan_day", "streak_freezes", "weekly_target",
    ↓               "dailyHistory", "total_workouts_completed", "last_workout_epoch"
    ↓   Izračuna weeklyDone dinamično iz dailyHistory (pon–danes)
    ↓   Emitira: WorkoutStats (data class)
    ↓
GetBodyMetricsUseCase.invoke(email): Flow<Result<BodyMetrics>>
    ↓   Transformira WorkoutStats → BodyMetrics
    ↓   Mapira: stats.planDay → BodyMetrics.planDay
    ↓           stats.streakDays → BodyMetrics.streakDays
    ↓           stats.todayStatus → BodyMetrics.todayStatus
    ↓   Null snapshot → Result.failure(NetworkFailure)
    ↓   PERMISSION_DENIED → Result.failure(AuthenticationExpired) [prek DomainException]
    ↓
BodyModuleHomeViewModel.handleIntent(LoadMetrics)
    ↓   collect { result → ... }
    ↓   result.isSuccess → _ui.update { copy(metrics = updatedMetrics) }
    ↓   Dodatni izračun: todayIsRestFromPlan iz currentPlanState.value?.weeks
    ↓                    (plan je avtoritativen vir za isRestDay — ne Firestore)
    ↓   result.isFailure → _ui.update { errorMessage / isAuthExpired }
    ↓
BodyModuleHomeScreen (Composable)
    val ui by vm.ui.collectAsStateWithLifecycle()
    val metrics = ui.metrics   ← null-safe alias
    Prikaže: metrics?.streakDays, metrics?.planDay, metrics?.todayIsRest...
```

**Opomba:** `todayIsRest` Firestore **nikoli ne shrani** — vedno je `false` v `UserWorkoutStatsRepository`. ViewModel ga izračuna lokalno iz `currentPlanState.value` (plan model), ki ga je dobil iz `PlanDataStore` prek `PlanRepositoryImpl`.

---

### 2.2 WRITE PATH — UI → Firestore (plan_day + streak_days)

#### Pot A: Redni trening zaključen (WORKOUT_DONE)

```
UI: BodyModuleHomeScreen
    → vm.handleIntent(CompleteWorkoutSession(...))
    
BodyModuleHomeViewModel.handleIntent(CompleteWorkoutSession)
    ↓ updateBodyMetrics.invoke(email, totalKcal, ..., planDay=oldPlanDay, isExtra=false)
    
UpdateBodyMetricsUseCase.invoke(...)
    ↓ Gradi workoutDoc: Map<String, Any> (timestamp, type, kcal, exercises, focusAreas…)
    ↓ gamificationUseCase.recordWorkoutCompletion(
          caloriesBurned = totalKcal.toDouble(),
          isRestDay = false,
          incrementPlanDay = true,   ← ker isExtra=false
          currentPlanDay = planDay,
          workoutSessionDoc = workoutDoc
      )
      
ManageGamificationUseCase.recordWorkoutCompletion(...)
    ↓ newStatus = UserDayStatus.WORKOUT_DONE
    ↓ shouldIncrement = true (incrementPlanDay && !isRestDay)
    ↓ repository.moveToNextDay(
          newStatus = WORKOUT_DONE,
          xpToBeAwarded = 50 + (caloriesBurned/8).toInt() [+100% če isCritical=10% verjetnost],
          incrementPlanDay = true,
          workoutSessionDoc = workoutDoc
      )
      
FirestoreGamificationRepository.moveToNextDay(...)
    ↓ db.runTransaction { ... }
        READ: snapshot.getLong("plan_day")     → oldPlanDay
              snapshot.getLong("streak_days")  → oldStreak
              snapshot.getLong("last_activity_epoch") → oldLastEpoch
        
        STREAK IZRAČUN:
          dayDiff = todayEpoch - oldLastEpoch
          newStreak = when {
              dayDiff == 1L → oldStreak + 1   ← normalen primer
              dayDiff == 0L → oldStreak        ← de-dup isti dan
              oldFreezes > 0 → oldStreak + porabljen freeze
              else → 1                          ← reset
          }
        
        WRITE: transaction.update(userRef, {
            "streak_days"             → newStreak,
            "plan_day"                → oldPlanDay + 1,   ← ker incrementPlanDay=true
            "last_activity_epoch"     → todayEpoch,
            "last_streak_update_date" → todayStr,
            "dailyHistory.$todayStr"  → "WORKOUT_DONE",
            "xp"                      → newXp,
            "level"                   → newLevel
        })
        
        + transaction.set(dailyLogRef, { burnedCalories })    ← Nutrition bridge
        + transaction.set(sessionRef, workoutDoc)             ← atomarni workout session zapis
    ↓ Vrne: newStreak (Int)
    
ManageGamificationUseCase → vrne WorkoutCompletionResult(newStreakDays, newPlanDay, xpAwarded…)
UpdateBodyMetricsUseCase → Result.success(WorkoutCompletionResult)
BodyModuleHomeViewModel → _ui.update { metrics.copy(planDay=newPlanDay, streakDays=newStreak) }
                        → _showCompletionAnimation = true
```

#### Pot B: Rest dan zaključen (REST_DAY_DONE)

```
UI: BodyModuleHomeScreen
    → vm.handleIntent(CompleteRestDay)
    
BodyModuleHomeViewModel.handleIntent(CompleteRestDay)
    ↓ gamificationUseCase.restDayInitiated()
    
ManageGamificationUseCase.restDayInitiated()
    ↓ repository.getTodayStatus() → preveri de-dup
    ↓ Če WORKOUT_DONE ali REST_WORKOUT_DONE → vrne repository.getCurrentStreak() [brez pisanja]
    ↓ Drugače:
      repository.moveToNextDay(
          newStatus = REST_DAY_DONE,
          xpToBeAwarded = 10,
          xpReason = "REST_DAY",
          incrementPlanDay = false   ← plan_day se NE poveča
      )
      
FirestoreGamificationRepository.moveToNextDay(...)
    ↓ db.runTransaction { ... }
        WRITE: {
            "streak_days"             → oldStreak + 1,   ← streak +1
            "plan_day"                → oldPlanDay,       ← NESPREMENJENO
            "dailyHistory.$todayStr"  → "STRETCHING_DONE",
            "xp"                      → newXp,
            ...
        }
    ↓ Vrne: newStreak
    
BodyModuleHomeViewModel → _ui.update { metrics.copy(streakDays=newStreak, isWorkoutDoneToday=true) }
```

#### Pot C: Atomarna zamenjava dni plana (SwapDays)

```
UI: BodyModuleHomeScreen
    → vm.handleIntent(SwapDays(currentPlan, dayA, dayB, onResult))
    
BodyModuleHomeViewModel.handleIntent(SwapDays)
    ↓ swapPlanDays.invoke(intent.currentPlan, dayA, dayB, lockedDay)
    
SwapPlanDaysUseCase.invoke(...)
    ↓ Triple domenska validacija:
      - dayA == dayB → fail
      - plan.id.isBlank() → fail
      - lockedDay == dayA || lockedDay == dayB → fail (danes opravljen dan)
      - weekA != weekB → fail (cross-week swap)
      - allDays[iA].isFrozen → fail
      - allDays[iB].isFrozen → fail
    ↓ Gradi posodobljeni lokalni model (optimistični UI)
    ↓ planRepository.swapDays(planId, dayA, dayB)
    
PlanRepositoryImpl.swapDays(...)
    ↓ withContext(Dispatchers.IO + NonCancellable) { ... }
      PlanDataStore.swapDaysAtomically(planId, dayA, dayB)
      
PlanDataStore.swapDaysAtomically(...)
    ↓ firestore.runTransaction { ... }
        READ: firestore.collection("user_plans").document(userId)
        VALIDACIJA: isFrozen check na Firestore strani (drugi obrambni sloj)
        WRITE: transaction.update(docRef, "plans", plansData)
               (samo "plans" polje — ne prepiše celotnega dokumenta)
    ↓ Vrne: Result<Unit>
    
SwapPlanDaysUseCase → Result.success(updatedPlan) — lokalni model z zamenjano vsebino
BodyModuleHomeViewModel → currentPlanState.value = updatedPlan
                        → intent.onResult(updatedPlan)
```

#### Pot D: Polnočni streak check (WeeklyStreakWorker)

```
WeeklyStreakWorker.doWork() [vsak dan ob 00:01]
    ↓ FirestoreGamificationRepository() ← direktna instantiacija (ne factory!)
    ↓ ManageGamificationUseCase(repository)
    ↓ useCase.executeMidnightStreakCheck()
    
ManageGamificationUseCase.executeMidnightStreakCheck()
    ↓ repository.runMidnightStreakCheck()
    
FirestoreGamificationRepository.runMidnightStreakCheck()
    ↓ db.runTransaction { ... }
        READ: dailyHistory[yesterdayStr]
        CHECK: ali je včeraj v safeStatuses (WORKOUT_DONE, REST_WORKOUT_DONE, REST_DAY_DONE, FROZEN)?
        
        ČE NI (MISSED ali PENDING):
          Ima freeze? → transaction.update({ streak_freezes-1, dailyHistory.yesterday → "FROZEN" })
          Nima freeze? → transaction.update({ streak_days → 0, dailyHistory.yesterday → "MISSED" })
```

---

## 3. TRIGGER TOČKE — Kdaj se piše v Firestore

### 3.1 Trigger točke za `plan_day`

| # | Trigger funkcija | Klic chain | Pogoj za write | Vrednost po zapisu |
|---|---|---|---|---|
| **T-PD-1** | `BodyModuleHomeViewModel.handleIntent(CompleteWorkoutSession)` | VM → `UpdateBodyMetricsUseCase.invoke()` → `ManageGamificationUseCase.recordWorkoutCompletion()` → `FirestoreGamificationRepository.moveToNextDay()` | `incrementPlanDay=true` AND `isExtra=false` AND de-dup guard ne ujame | `plan_day = oldPlanDay + 1` |
| **T-PD-2** | `BodyModuleHomeViewModel.handleIntent(CompleteRestDay)` | VM → `ManageGamificationUseCase.restDayInitiated()` → `FirestoreGamificationRepository.moveToNextDay()` | Vedno `incrementPlanDay=false` | `plan_day` NESPREMENJEN |
| **T-PD-3** | `BodyModuleHomeViewModel.handleIntent(CompleteWorkoutSession)` z `isExtraWorkout=true` | Enaka pot kot T-PD-1 ampak `incrementPlanDay=false` ker `shouldIncrement = !isExtra` | `isExtra=true` | `plan_day` NESPREMENJEN |

**Skupni zaključek za `plan_day`:** Edino mesto v celotnem sistemu, kjer se `plan_day` dejansko poveča, je `FirestoreGamificationRepository.moveToNextDay()` znotraj Firestore transakcije — in samo kadar klic pride iz regularne vadbe (`isExtra=false`, `isRestDay=false`).

---

### 3.2 Trigger točke za `streak_days`

| # | Trigger funkcija | Klic chain | Pogoj za write | Vrednost po zapisu |
|---|---|---|---|---|
| **T-SD-1** | `BodyModuleHomeViewModel.handleIntent(CompleteWorkoutSession)` | VM → `UpdateBodyMetricsUseCase` → `ManageGamificationUseCase.recordWorkoutCompletion()` → `FirestoreGamificationRepository.moveToNextDay(WORKOUT_DONE)` | `newStatus.contributesToStreak == true` AND `dayDiff == 1` | `streak_days = oldStreak + 1` |
| **T-SD-2** | `BodyModuleHomeViewModel.handleIntent(CompleteRestDay)` | VM → `ManageGamificationUseCase.restDayInitiated()` → `FirestoreGamificationRepository.moveToNextDay(REST_DAY_DONE)` | `contributesToStreak == true` AND `dayDiff == 1` | `streak_days = oldStreak + 1` |
| **T-SD-3** | `WeeklyStreakWorker.doWork()` [polnočni check] | Worker → `ManageGamificationUseCase.executeMidnightStreakCheck()` → `FirestoreGamificationRepository.runMidnightStreakCheck()` | Včeraj ni v `safeStatuses` AND `streak_freezes == 0` | `streak_days = 0` (RESET) |
| **T-SD-4** | `WeeklyStreakWorker.doWork()` [polnočni check — Freeze porabljen] | Enaka pot kot T-SD-3 | Včeraj ni v `safeStatuses` AND `streak_freezes > 0` | `streak_days` NESPREMENJEN, `streak_freezes -= 1` |
| **T-SD-5** | `BodyModuleHomeViewModel.handleIntent(CompleteWorkoutSession)` z `isExtraWorkout=true` | VM → `UpdateBodyMetricsUseCase` → `recordWorkoutCompletion(isRestDay=isRestDay&&isExtra)` → `moveToNextDay(REST_WORKOUT_DONE)` | `newStatus = REST_WORKOUT_DONE` → `contributesToStreak == false` | `streak_days` NESPREMENJEN |

---

## 4. KRITIČNE ARHITEKTURNE ANOMALIJE (IDENTIFICIRANE MED REVIZIJO)

### 🔴 ANOMALIJA-1: `StreakReminderWorker` bere `streak_days` prek `UserProfileManager.getWorkoutStats()` — MIMO `FirestoreHelper`

**Lokacija:** `StreakReminderWorker.kt` vrstica 149  
```kotlin
val workerStats = UserProfileManager.getWorkoutStats(email)
```
`UserProfileManager` je legacy razred, ki morda ne gre skozi `FirestoreHelper.getCurrentUserDocRef()`. Bere `streak_days` in `plan_day` neposredno, mimo uradne vstopne točke. Enaka je tudi pot v `checkTodayIsRestFromFirestore()` (vrstica 190-192), ki kliče `FirestoreHelper.getCurrentUserDocId()` (sinhroni, ne `getCurrentUserDocRef()` suspend verzija) in nato direktno `db.collection("user_plans").document(uid)`.

---

### 🔴 ANOMALIJA-2: `WeeklyStreakWorker` instantiira `FirestoreGamificationRepository()` direktno — NE prek `GamificationFactory`

**Lokacija:** `WeeklyStreakWorker.kt` vrstici 24–25  
```kotlin
val repository = FirestoreGamificationRepository()
val useCase = ManageGamificationUseCase(repository)
```
Vsak `doWork()` klic ustvari **novo instanco** repozitorija in use case-a, namesto da bi uporabil `GamificationFactory.provide(context)` singleton. To ni skladno z arhitekturnimi pravili.

---

### 🟡 ANOMALIJA-3: `StreakReminderWorker.loadReminderContext()` bere `dailyLogs` direktno prek `FirestoreHelper.getCurrentUserDocId()` (sinhronizem)

**Lokacija:** `StreakReminderWorker.kt` vrstice 223–233  
```kotlin
val uid = FirestoreHelper.getCurrentUserDocId()
val doc = db.collection("users").document(uid).collection("dailyLogs")...
```
Kliče sinhroni `getCurrentUserDocId()` (email/UID iz lokalne Auth — brez cache guard validacije) in **ne** suspend `getCurrentUserDocRef()`. Tveganje: ob email-based migration dokumentov bi dobil napačen UID in bral iz napačnega dokumenta.

---

### 🟡 ANOMALIJA-4: `todayIsRest` v `UserWorkoutStatsRepository` je vedno `false` — prazna vrednost

**Lokacija:** `UserWorkoutStatsRepository.kt` vrstici 78 in 157  
```kotlin
todayIsRest = false,
```
`WorkoutStats.todayIsRest` se nikoli ne nastavi na `true` s strani repozitorija. Pravo vrednost izračuna ViewModel iz lokalnega `currentPlanState`, kar pomeni, da vsak ponovni zagon ViewModel-a (process death, navigacija) zahteva, da je plan posredovan prek `LoadMetrics(plan=...)` intenta — sicer bo `todayIsRest = false` dokler plan ni na voljo.

---

### 🟡 ANOMALIJA-5: `PlanDataStore` vsebuje direkten `firestore.collection(PLANS_COLLECTION).document(userId)` — MIMO `FirestoreHelper`

**Lokacija:** `PlanDataStore.kt` — večkrat skozi celotno datoteko (vrstice 59, 80, 110, 204, 225, 272, 409…)  
```kotlin
firestore.collection(PLANS_COLLECTION).document(userId)
```
`PLANS_COLLECTION = "user_plans"` je **ločena kolekcija** (ne `users/{docId}`), zato morda namerno ne gre skozi `getCurrentUserDocRef()`. Toda `userId` pride iz `getResolvedUserId()` ki interno kliče `FirestoreHelper.getCurrentUserDocRef().id` — kar je pravilno. Tveganje je minimalno, ampak arhitekturno nedosledno: `PlanDataStore` hrani lasten `private val firestore` in `auth` namesto da bi šel izključno skozi `FirestoreHelper`.

---

## 5. POVZETEK GRAFOV KLICEV

### 5.1 Zaključek treninga (plan_day +1, streak +1)
```
BodyModuleHomeScreen
  └─ handleIntent(CompleteWorkoutSession)
       └─ UpdateBodyMetricsUseCase.invoke()
            └─ ManageGamificationUseCase.recordWorkoutCompletion()
                 └─ FirestoreGamificationRepository.moveToNextDay()
                      └─ db.runTransaction { UPDATE plan_day +1, streak_days +1, XP, workoutSession }
```

### 5.2 Branje stanja (reaktivni flow)
```
Firestore snapshot listener
  └─ UserWorkoutStatsRepository.observeWorkoutStats()
       └─ GetBodyMetricsUseCase.invoke()
            └─ BodyModuleHomeViewModel (collect → _ui.update)
                 └─ BodyModuleHomeScreen (collectAsStateWithLifecycle)
```

### 5.3 Zamenjava dni plana
```
BodyModuleHomeScreen
  └─ handleIntent(SwapDays)
       └─ SwapPlanDaysUseCase.invoke()
            └─ PlanRepositoryImpl.swapDays()
                 └─ PlanDataStore.swapDaysAtomically()
                      └─ db.runTransaction { UPDATE user_plans.plans }
```

### 5.4 Polnočni streak reset
```
WorkManager (ob 00:01)
  └─ WeeklyStreakWorker.doWork()
       └─ ManageGamificationUseCase.executeMidnightStreakCheck()
            └─ FirestoreGamificationRepository.runMidnightStreakCheck()
                 └─ db.runTransaction { UPDATE streak_days=0 ali streak_freezes-1 }
```

---

*Konec revizijskega dokumenta. Nobena koda ni bila spremenjena med pripravo tega dokumenta.*

