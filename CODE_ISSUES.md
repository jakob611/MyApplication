# CODE_ISSUES.md
> **NAVODILO ZA AI:** To datoteko VEDNO preberi na začetku seje. Po vsakem popravku dodaj vnos na dno pod "DNEVNIK POPRAVKOV".

**Zadnja posodobitev:** 2026-05-03 (build fix)  
**Trenutno stanje: VSE ZNANE TEŽAVE ODPRAVLJENE ✅ (Faza 4b + build fix zaključena)**

---

## HITRI PREGLED — ARHITEKTURNA PRAVILA
| Pravilo | Pravilna pot | Prepovedano |
|---------|-------------|-------------|
| Firestore profil write | `FirestoreHelper.getCurrentUserDocRef()` | `db.collection("users").document(uid/email)` direktno |
| XP podeljevanje | `AchievementStore.awardXP()` | `addXPWithCallback()` |
| Badge zahteve | `badge.requirement` iz `BadgeDefinitions` | hardcoded števila |
| Badge progress | `AchievementStore.getBadgeProgress(badgeId, profile)` | lokalna when() logika |

## DATOTEKE KI JIH NE SMEŠ POKVARITI
- `FirestoreHelper.kt` — edini vhod za Firestore dokumente
- `AchievementStore.kt` — edini vhod za XP in badge-e  
- `BadgeDefinitions.kt` — edini vir badge definicij

## ZNANE TEŽAVE, KI OSTAJAJO

### 1. ALGORITMI IN KALKULACIJE (KALORIJE & MET)
- [x] **RunTrackerViewModel / RunLocationManager**: Izračun kalorij pri teku in hoji je nagnjen k napakam zaradi napačnih GPS skokov ("GPS jumps"), kar povzroči absurdne premike in posledično previsok izračun porabljenih kalorij. Rešeno s postopno linearno interpolacijo MET faktorja (namesto grobih stopnic) v `calculateCaloriesMet`.

### 3. SINHRONIZACIJA, ZMOGLJIVOST IN HITROST
- [x] **Težava #3 (Sinhronizacija in hitrost UI ob initial load)**: `AdvancedExerciseRepository.kt` inicializira in bere lokalni JSON na `MainThread` ob prvem branju. Če uporabnik hitro klikne na GenerateWorkout preden je background loadan, UI zamrzne.
  - **Rešitev**: Zagon `AdvancedExerciseRepository.init(context)` asinhrono v ozadju ob samem startupu aplikacije (`MainActivity`). Popravil z dodajanjem WriteBatch znotraj `BodyModuleHomeViewModel.completeWorkoutSession`.
- [x] **Race pogoji ob Firestore zapisih**: Če ob zaključku treninga hkratno prožimo posodobitve za streak, dnevno porabo kalorij (burned_cache), xp točke in odklep značk, obstaja veliko transakcij. 
  - **Rešitev**: V `AchievementStore`, `UserPreferences` sem vsilil opcijski podatek `WriteBatch`. Sedaj vse akcije ob koncu treninga (`recordWorkoutCompletion`, pridobivanje XP točk, `saveWorkoutStats`, `checkAndUpdatePlanStreak`) uporabijo en sam `db.batch()`, ki drastično zmanjša čakanje (network latency) in povsem odpravi nevarnost prepisov polj pod istim uporabnikovim dokumentom.

---

## Trenutno Odprte Težave (Backlog)
- [N/A] `ActivityLogScreen.kt` nima mehanizma za load-more paginacijo pri velikem številu map markers.

###  NUJNI POPRAVKI (pred UI/UX prenovo) — Audit 2026-04-25

#### Dead Code — treba ročno zbrisati (AI ne more brisati datotek):
| Datoteka | Razlog brisanja |
|---|---|
| `domain/nutrition/NutritionCalculations.kt` | Prazna, eksplicitno DEPRECATED, vsa logika v `utils/NutritionCalculations.kt` |
| `network/ai_utils.kt` | `requestAIPlan()` ni klicana nikjer; PlanDataStore ima lastno kopijo |
| `ui/adapters/ChallengeAdapter.kt` | RecyclerView stari View sistem; cela app je Compose; `item_challenge_card.xml` ne obstaja |

Vse 3 datoteke so označene z `// ⚠️ DEAD CODE — IZBRIŠI TO DATOTEKO ROČNO.`

#### GPS 1MB Limit (crash pri maratonih):
- `RunSession.toFirestoreMap()` vgrajen `polylinePoints` array → crash pri tekih > ~2h
- **Načrt migracije:** `GPS_POINTS_MIGRATION_PLAN.md` (sub-kolekcija `points/`)
- **Zahtevnost:** ~5-6h dela (RunTrackerScreen + ViewModel + ActivityLogScreen)

#### Community Search:
- ✅ `searchPublicProfiles()` in `getAllPublicProfiles()` — dodano `.limit(20)`

#### Navigation Stack:
- ✅ `NavigationViewModel.replaceTo()` dodana — LoadingWorkout → WorkoutSession brez push v stack

## Nedavno Zaprte Težave (Rešeno)
- [2026-04-02] `ActivityLogScreen.kt`: Odpravljeni Compose recomposition loopi pri infinite-scrollu z dodanim unikatnim parametrom `key = { it.id }`. Dodan fallback `Configuration.getInstance().load()` za preprečevanje OSMDroid inicializacijskih sesutij na nekaterih napravah in integrirana manjša telemetrija.
- [2026-04-02] `WorkoutCelebrationScreen`: Zgornji meni/sistemska orodna vrstica je včasih preprečila klike na animacije ("Continue"). Dodan `WindowInsets.systemBars()` na zunanji `Box`.
- [2026-04-02] `RunTrackerScreen`: Sistem MET in kalorij sedaj uporablja linearno interpolacijo in s tem ublaži napačne skoke GPS.
- [2026-03-29] `RunTrackerScreen.kt`: Aplikacija med tekom/hoja ni omogočala prostega premikanja po zemljevidu in je vedno na silo vračala na trenuten položaj uporabnika. Dodano je bilo, da uporabnikov dotik ustavi samodejno sledenje, hkrati pa je dodan gumb "Re-center map".
- [2026-03-28] `WorkoutSessionScreen.kt`: Uporabnik je lahko po nesreči zapustil trening brez opozorila, izgubil progress. Dodan BackHandler za potrditev.
- [2026-04-02 (Activity Log Pagination)] — V `RunTrackerViewModel` smo dodali `limit(15)` in `startAfter()` za optimizacijo pri pridobivanju iz Firestore-a, v `ActivityLogScreen.kt` pa avtomatsko load-more paginacijo, ki po potrebi prenaša po 15 kartic, kar ublaži težave na napravah ob dolgi zgodovini.

---

## DNEVNIK POPRAVKOV

### 2026-05-03 — Build Fix: KSP Configuration Cache napaka

**Problem:** Build je iskal KSP `2.2.10-1.0.32` (in prej `1.0.28`) čeprav je bil plugin že odstranjen iz `build.gradle.kts`.
**Root cause:** `org.gradle.configuration-cache=true` v `gradle.properties` → Gradle je bral stari keš, ki je vseboval zastarelo KSP referenco. Ker KSP ni na voljo za Kotlin 2.2.10 (noben `2.2.10-1.0.X` patch ni v Maven repos), je vsak build propadel.
**Rešitev:**
- ✅ `gradle.properties`: `org.gradle.configuration-cache=true` → zakomentirano (onemogoči stali keš)
- ✅ `build.gradle.kts` + `app/build.gradle.kts`: brez KSP/kapt referenc
- ✅ Room: `AppDatabase_Impl.kt` ročno napisan (nadomešča KSP code generation)
- ✅ BUILD SUCCESSFUL ✅

**Dodano v tej seji:**
- ✅ `strings.xml`: 10 novih auth napake/success stringov za MainActivity login flow
- ✅ `NutritionViewModel.clearUser()`: počisti Firestore listener, waterSyncJob, in session state ob odjavi

### 2026-05-03 — Faza 4b: Daily Habit Streak sistem + čiščenje kode

**Nova Streak logika (Daily Habit):**
- ✅ `Streak +1` → Workout dan + opravljen trening (`WORKOUT_DONE` v `dailyHistory`)
- ✅ `Streak +1` → Rest dan + opravljeno raztezanje (Stretching kartica → `STRETCHING_DONE`)
- ✅ `Streak +0 (Freeze)` → zamujeni dan + Streak Freeze razpoložljiv (auto-poraba)
- ✅ `Streak = 0` → zamujeni dan + ni freeze-a

**Odstranjeno:**
- ✅ `checkIfFutureRestDaysExistAndSwap()` — **IZBRISAN** iz `FirestoreGamificationRepository.kt`
  Aplikacija ne prestavi več dni v PlanPath-u samodejno. Streak pade ali porabi freeze.
- ✅ `daily_logs` subcollection za streak tracking → zamenjano z `dailyHistory` mapa v glavnem doc
  Razlog: hitrejše branje (1 document read namesto subcollection query), nižji Firestore stroški
- ✅ `currentPlanDayNum = logsSnap.documents.size + 1` — odstranjeno skupaj s swap funkcijo

**Novo — Firestore Schema:**
```
users/{uid}: {
  dailyHistory: {
    "2026-05-03": "WORKOUT_DONE",   // +1 streak
    "2026-05-02": "STRETCHING_DONE", // +1 streak (rest day)
    "2026-05-01": "FROZEN",          // freeze porabljen
    "2026-04-30": "MISSED"           // streak = 0
  }
}
```

**Stretching Aktivacija:**
- ✅ `BodyModuleHomeViewModel.CompleteRestDay` — implementiran (bil `// Future implementation`)
- ✅ Pokliče `ManageGamificationUseCase.restDayInitiated()` → `updateStreak("STRETCHING_DONE")` + XP +10
- ✅ Optimistično posodobi `isWorkoutDoneToday = true`, `streakDays = newStreak`
- ✅ `StreakUpdateEvent` SharedFlow emitiran za Toast + HapticFeedback v Screen-u

**UX Add-on (Toast + Haptic):**
- ✅ `BodyModuleHomeScreen`: `LaunchedEffect(vm)` zbira `streakUpdatedEvent`
- ✅ `HapticFeedback.SUCCESS` — S24 Ultra precizna vibracija ob vsaki streak posodobitvi
- ✅ Toast: `"Daily Goal Met! Streak: X days 🔥"` (Workout IN Stretching pot)

**Technical Fixes:**
- ✅ `plan_day` se bere iz Firestore profila (`plan_day` field) — `GetBodyMetricsUseCase` to dela že od Faze 13.2
- ✅ `updateStreak()` vrne `Int` (novi streak) — omogoča Toast z realnim številom
- ✅ `MyViewModelFactory` posreduje `gamificationUseCase` v `BodyModuleHomeViewModel`

### 2026-05-03 — Faza 3: Room Offline-First strategija za Activity Log

**Nova arhitektura (Offline-First + Data Splitting):**

**Problem:** Activity Log je zahteval 2-3s ob vsakem odprtju (Firestore round-trip).
**Rešitev:** Room baza lokalno hrani sesumarke tekov. Ob zagonu 0ms latenca, Firestore delta sync v ozadju.

**Novo ustvarjene datoteke:**
1. ✅ **`data/local/WorkoutEntities.kt`**
   - `WorkoutSessionEntity(@PrimaryKey id = Firestore doc.id)` — preprečuje podvajanje z @Upsert
   - `GpsPointEntity(isRaw: Boolean)` — isRaw=true surovi GPS (S24 Ultra), isRaw=false RDP iz Firestore
   - Mapper funkcije: `toRunSession()`, `toEntity()`, `toLocationPoint()`, `toGpsPointEntity()`
2. ✅ **`data/local/WorkoutDao.kt`**
   - `WorkoutSessionDao`: getSessionsFlow() → Flow, upsertAll(), getLatestCreatedAt() (za delta sync), deleteById()
   - `GpsPointDao`: getPointsPreferRaw() (isRaw DESC), insertAll(IGNORE), deleteBySessionId()
3. ✅ **`data/local/AppDatabase.kt`** — Room singleton, `glow_upp_offline.db`
4. ✅ **`data/local/OfflineFirstWorkoutRepository.kt`**
   - `sessionsFlow`: Flow<List<RunSession>> iz Room (live)
   - `syncFromFirestore()`: `whereGreaterThan("createdAt", lastTimestamp)` → delta, brez composite indeksa
   - `insertLocalSession()`: surovi GPS (isRaw=true) ob shranjevanju teka
   - `getGpsPoints()`: prioritizira surove točke pred kompresiranimi

**Spremenjene datoteke:**
5. ✅ **`viewmodels/RunTrackerViewModel.kt`** — dodan `sessions: StateFlow`, `isSyncing`, `syncFromFirestore()`, `getGpsPoints()`, `deleteFromRoom()`; init {} start Room flow
6. ✅ **`ui/screens/ActivityLogScreen.kt`** — zamenjal Firestore callback z Room flow collect + inkrementalnim GPS nalaganjem; dodan `LaunchedEffect("firestoreSync")` za delta sync; brisanje zbriše iz Room (CASCADE)
7. ✅ **`ui/home/CommunityScreen.kt`** — State Hoisting fix: skeleton samo ob prvem nalaganju (allUsers.isEmpty()); stari seznam viden med osveževanjem; Shimmer animacija (infiniteRepeatable, 0.15→0.45 alpha, 900ms)
8. ✅ **`ui/screens/MyViewModelFactory.kt`** — posreduje `OfflineFirstWorkoutRepository` v `RunTrackerViewModel`
9. ✅ **`app/build.gradle.kts`** — dodane Room odvisnosti (room-runtime, room-ktx, ksp compiler) + KSP plugin
10. ✅ **`build.gradle.kts`** (top-level) — dodan KSP plugin `2.2.10-1.0.27`

**Arhitekturna opomba — Firestore DocumentID ↔ Room PrimaryKey:**
`WorkoutSessionEntity.id = Firestore doc.id` (String). @Upsert → INSERT OR REPLACE po id.
Delta sync formula: `MAX(createdAt) FROM workout_sessions WHERE userId=X` → `whereGreaterThan("createdAt", ts)`.
Isti dokument → vedno prepiše obstoječo vrstico, brez podvajanja.

**Data Splitting Strategy:**
- Firestore: samo RDP-kompresiran GPS (`polylinePoints`, ≤500 točk) → 90% manj stroškov ✅
- Room (isRaw=true): polne surove GPS točke iz RunTrackerScreen → S24 Ultra kakovost prikaza
- Room (isRaw=false): kompresiran GPS iz Firestore sync → fallback za seje iz drugih naprav

### 2026-05-03 — Faza 2: Restavracija funkcij + Mapbox API odstranitev

### 2026-04-26 — Faza 15: Community Privacy & Calorie Sync

**Spremembe:**
1. ✅ **`PublicProfile` data class** — Dodan `shareActivities: Boolean = false` za ekspliciten flag iz Firestore dokumenta gledanega uporabnika. Prej je `PublicProfileScreen` sklepal zasebnost posredno prek `recentActivities != null`.
2. ✅ **`ProfileStore.mapToPublicProfile()`** — `shareActivities = shareActivities` nastavljeno v `return PublicProfile(...)`, tako da UI vedno bere iz dokumenta gledanega uporabnika.
3. ✅ **`PublicProfileScreen.kt`** — `hasActivities = profile.shareActivities` (ne več `recentActivities != null`). `ActivitiesContent` prikazuje FitnessCenter ikono + "No activities yet" za prazen seznam (ne zavajajočo Lock ikono).
4. ✅ **`RunTrackerScreen.kt`** — Po shranjevanju teka dodan `DailyLogRepository().updateDailyLog(todayDate)` klic, ki prišteje `calories` k `burnedCalories` v `dailyLogs/{today}`. Dodana importa `kotlinx.datetime.TimeZone` in `kotlinx.datetime.toLocalDateTime`.
5. ✅ **NutritionViewModel + Progress.kt Snapshot Listenerji** — Oba že imata `addSnapshotListener` na `dailyLogs`. Ko RunTrackerScreen zapiše kalorije, se UI samodejno posodobi brez ponovnega zagona — ni bila potrebna sprememba.

**Root cause (Task 2 — kalorije):** `RunTrackerScreen` je shranjeval tek v `runSessions` in `publicActivities` ter lokalne `.json` datoteke, nikoli pa ni posodobil `dailyLogs/{today}/burnedCalories`. `ManualExerciseLogScreen` in `WorkoutSession` sta to delala, RunTrackerScreen pa ne.

### 2026-04-26 — Global Audit & bm_prefs SharedPrefs Purge

**Ugotovitve in popravki (Global Audit pred iOS migracijo):**

1. ✅ **Streak Reset Bug (KRITIČNO)**: `MainActivity.onFinish` je ob novem planu bral streak iz `bm_prefs` (= 0) in ga zapisal v Firestore → ponastavitev streaka. Zamenjano s partial Firestore merge (samo `plan_day=1`, `weekly_target`, `weekly_done=0`).

2. ✅ **CelebrationScreen streak iz bm_prefs**: `WorkoutCelebrationScreen` je bral `streak_days` iz `bm_prefs` (vedno 0 ker bm_prefs ni več pisan). Zamenjano s parametrom `streakDays: Int` iz `vmUiState.streakDays`.

3. ✅ **weekly_target/done iz bm_prefs**: `WorkoutSessionScreen` je bral oba iz bm_prefs. Zamenjano z `vm.ui.value.weeklyTarget` in `vm.ui.value.weeklyDone`.

4. ✅ **Redundantni settingsRepo.updateWorkoutStats()**: `UpdateBodyMetricsUseCase` je pisal stari (pre-increment) `plan_day` v deprecated bm_prefs. Odstranjeno. `settingsRepo` izbrisan iz konstruktorja + factory.

5. ✅ **GetBodyMetricsUseCase bm_prefs cache**: Deprecated `settingsRepo.updateWorkoutStats()` klic z epoch konverzijo — odstranjeno.

**Streak Freeze logika (Faza 13.3) — VERIFICIRANA:**
- `dayDiff > 1` + freeze > 0: streak ohranjen, freeze -= 1, `last_workout_epoch = todayEpoch` ✅
- `dayDiff > 1` + freeze == 0: streak = 1 (reset) ✅
- `dayDiff == 0`: streak ohranjen ✅, `dayDiff == 1`: streak++ ✅
- Naslednji dan je `dayDiff = 1` → streak se pravilno podaljša ✅

**Data Model iOS-ready:**
- `UserProfile` — brez Android odvisnosti ✅
- `BodyHomeUiState`, `WorkoutProgressResult`, `DailyTotals` — brez Android odvisnosti ✅
- `UserPreferencesRepository` ima `Context` (TODO za KMP migration: `expect/actual`) — dokumentirano

### 2026-04-26 — Faza 13.2: Streak Engine & Plan Progression

**Spremembe:**
- `data/settings/UserProfileManager.kt`:
  - Nova funkcija `updateUserProgressAfterWorkout(incrementPlanDay: Boolean)` — Firestore `runTransaction`
  - Atomarno bere in zapiše `plan_day`, `streak_days`, `last_workout_epoch` v en klic
  - Logika: +1 včeraj, reset=1 prekinitev, ohrani danes; epochDays format (skladen z GetBodyMetricsUseCase)
- `domain/workout/UpdateBodyMetricsUseCase.kt`:
  - Po `workoutRepo.saveWorkoutSession()` pokliče `updateUserProgressAfterWorkout(incrementPlanDay = !isExtra)`
  - Extra workoutji → streak se posodobi, plan_day pa ne
- `viewmodels/BodyModuleHomeViewModel.kt`:
  - `CompleteWorkoutSession` success handler kliče `getBodyMetrics.invoke()` za svež Firestore fetch
  - UI takoj prikaže posodobljeni streak/planDay brez čakanja na naslednjo navigacijo
  - Fallback: optimistični +1 lokalno, če Firestore fetch ne uspe

**Root cause:** Po zaključenem treningu se `streak_days` in `plan_day` v Firestore dokument `users/{uid}` **nikoli nista posodabljala**. `settingsRepo.updateWorkoutStats()` je pisal samo SharedPrefs (deprecated), ne Firestore. Transakcija zdaj atomarno popravi obe vrednosti.

### 2026-04-26 — Faza 13.1: UI Responsiveness & Persistence Fix

**Spremembe:**
- `viewmodels/NutritionViewModel.kt`:
  - `_localWaterMl: MutableStateFlow<Int?>` — optimistični override za instant water UI
  - `updateWaterOptimistic(newValue, todayId)`: posodobi UI takoj, debounce 800ms → en Firestore zapis (ne ves klik → zapis)
  - `waterSyncJob: Job?` — predhodni zapis se cancela, ko pride novi klik
  - `_isLoading: MutableStateFlow<Boolean>` — food log operacije
  - `logFoodAsync(foodMap, todayId, onDone)`: isLoading=true med zapisom, finally isLoading=false
- `ui/screens/NutritionScreen.kt`:
  - `effectiveWaterMl = localWaterMl ?: uiState.water` — prikaže optimistično vrednost
  - WaterControlsRow → `nutritionViewModel.updateWaterOptimistic()` namesto direktnega Firestore klica
  - DonutProgressView `innerValue` → `effectiveWaterMl` (ne `uiState.water`)
  - AddFoodSheet `onAddTracked` → `nutritionViewModel.logFoodAsync()` (ne MainScope().launch)
  - `CircularProgressIndicator` overlay ko `isLoading == true`
  - MealCard `onAddFood` onemogoočen ko `isLoading == true`
- `ui/screens/ExerciseHistoryScreen.kt`:
  - **Runs tab odstranjen** — `tabs = listOf("Workouts", "Exercises")`, ActivityLogScreen je edini SSOT za teke
  - **Workout history bug fix**: `orderBy("date")` → `orderBy("timestamp")` + parsing: `(d.get("timestamp") as? Number)?.toLong()` s fallback na `getTimestamp("date")`

**Root cause workout history bug:** `UpdateBodyMetricsUseCase` shranjuje `"timestamp"` (epoch ms Long), `ExerciseHistoryScreen.WorkoutsTab` pa je iskal `"date"` (Firestore Timestamp) → query ni vračal pravilno sortiranih rezultatov.

### 2026-04-25 — Faza 12: Firestore Bridge (WorkoutSessionViewModel + Volume Progression)

**Spremembe:**
- `viewmodels/WorkoutSessionViewModel.kt` (NOVO):
  - `WorkoutGenerationState`: Idle → LoadingHistory → Ready(isProgressiveOverload) / Error
  - `prepareWorkout()`: orchestrira Firestore fetch + AlgorithmPreferences.loadParamsWithOverrides() + WorkoutGenerator
  - `fetchLastSessionForFocus(focus)`: išče zadnjo workout sejo z ujemajočim focusAreas v Firestoreu
  - gender, goal, difficulty se berejo iz Firestore profila (SSOT), SharedPrefs samo fallback
- `ui/screens/WorkoutSessionScreen.kt`:
  - `workoutVm.prepareWorkout()` nadomesti inline WorkoutGenerator klic v `LaunchedEffect`
  - `StateFlow.first { Ready || Error }` čaka na rezultat (suspending, ne blokira UI)
  - `ExerciseResult`: dodano `reps`, `sets`, `weightKg` za Volume Progression
  - `exerciseResults` map: dodano `"reps"`, `"sets"`, `"weightKg"` — shraneno v Firestore za naslednji fetch
  - `"focusAreas"` shranjen v `workoutDoc` via `CompleteWorkoutSession` intent
  -  Progressive Overload UI badge (Banner "Danes močneje!") v Preview stanju
- `viewmodels/BodyModuleHomeViewModel.kt`: `CompleteWorkoutSession` + `focusAreas: List<String>`
- `domain/workout/UpdateBodyMetricsUseCase.kt`: `focusAreas` parameter → `workoutDoc["focusAreas"]`

**Arhitekturna opomba:** Generator je zdaj popolnoma pametno vezan na Firestore. Vsak workout fetch:
1. Bere profil (gender, goal) iz Firestorea
2. Poišče zadnjo sejo z ujemajočim fokusom
3. Aplicira +5% volume progresijo za znane vaje
4. Shrani reps/sets/focusAreas za naslednji klic (self-learning loop)

### 2026-04-25 — Faza 11: Algorithm Upgrade (deterministična naključnost, gender bonus, volume progression)

**Spremembe:**
- `domain/WorkoutGenerator.kt`:
  - `buildRandom(planDay)`: deterministični seed = `epochDay * 1000 + planDay` → isti nabor vaj cel dan
  - `WorkoutGenerationParams`: dodano `gender: String = ""` in `planDay: Int = 1`
  - `calculateScore()`: gender bonus — female +15% spodnji del (glutes/legs), male +10% zgornji del (chest/shoulders)
  - `selectExercisesWeighted()`: `rng` parameter namesto globalnega `Random`
  - `applyVolumeProgression(lastSession)`: nova funkcija — +5% reps/teža za vaje iz zadnje seje (The Memory Bridge, Faza 12 ready)
  - `LastExerciseRecord` data class klase za hranjenje podatkov zadnje seje
- `data/AlgorithmPreferences.kt`:
  - `loadParamsWithOverrides()`: nova funkcija — sprejme podatke iz WorkoutViewModel / Firestore kot optional overrides (gender, difficulty, goal, focus, equipment, planDay)

**Arhitekturna opomba:** Generator je zdaj deterministično ponovljiv (isti dan + planDay = isti workout). Gender in memory bridge sta pripravljena za Fazo 12 (Firestore progresija prek WorkoutViewModel).



**Spremembe:**
- `ui/screens/WorkoutSessionScreen.kt`:
  - Dodan `val vmUiState by vm.ui.collectAsState()` za pravilno Compose StateFlow branje
  - Vsi `bm_prefs.plan_day` klici (vrstica 311, 432, 1009) zamenjani z `vmUiState.planDay` / parameter
  - `WorkoutCelebrationScreen` prejme `planDay: Int` kot parameter namesto bm_prefs
- `data/settings/UserPreferencesRepository.kt`:
  - `updateWorkoutStats()` označen DEPRECATED — ostane za offline fallback v GetBodyMetricsUseCase
  - `updateDailyCalories()` je NO-OP — kalorije gredo prek DailyLogRepository (Faza 9.1)
- `workers/StreakReminderWorker.kt`:
  - `bm_prefs.streak_days`, `bm_prefs.plan_day`, `bm_prefs.last_workout_epoch` → Firestore prek `UserProfileManager.getWorkoutStats()`
  - `bm_prefs.today_is_rest` → nova `checkTodayIsRestFromFirestore()` bere planDay dan iz `user_plans` Firestore kolekcije
- `ui/screens/ManualExerciseLogScreen.kt`:
  - `GenderCache` brez SharedPrefs sloja (`gender_cache` prefs odstranjeni) — samo in-memory cache
  - `loadFromFirestoreIfNeeded()` vedno bere direktno iz `UserProfileManager` (Firestore SSOT)

**Arhitekturna opomba:** bm_prefs ne vsebuje več biometričnih vrednosti (plan_day, streak_days, burned_calories) ki bi bile v konfliktu s Firestore. Ostanejo samo UI nastavitve (dark_mode, fresh_start) v user_prefs.

### 2026-04-25 — Faza 9.1: DailyLogRepository SSOT sanacija

**Spremembe:**
- `ui/screens/RunTrackerScreen.kt` vrstica 712–713: Email bug fix — `uiState.errorMessage ?: ""` zamenjano z `runEmail` (Firebase Auth `currentUser?.email`), ki je pravilno pridobljen že na vrstici 701
- `ui/screens/ManualExerciseLogScreen.kt` funkcija `logExerciseToFirestore()`: Po `saveExerciseLog()` dodan klic `DailyLogRepository().updateDailyLog(todayStr)` ki atomarno prišteje `caloriesRounded` k `burnedCalories` v `dailyLogs`
- `domain/workout/UpdateBodyMetricsUseCase.kt` vrstica 57: `settingsRepo.updateDailyCalories()` zakomentiran z `// [DEPRECATED — SSOT je dailyLogs]` — `bm_prefs.daily_calories` ni več pisan, edini SSOT je `dailyLogs`

**Arhitekturna opomba:** Vsi trije viri aktivnosti (WorkoutSession, RunTracker, ManualExercise) zdaj pisejo `burnedCalories` izključno prek `DailyLogRepository.updateDailyLog()` Firestore transakcije. Debug Dashboard bo zato pravilno prikazoval skupne porabljene kalorije za vse tipe aktivnosti.

**Spremembe:**
- `utils/NutritionCalculations.kt`: `calculateAdaptiveTDEE()` razširjen z `theoreticalTDEE: Int`
  - Confidence factor C: 0.0 (<3 dni), 0.5 (3–5 dni), 1.0 (6+ dni)
  - Hibridna formula: `C × adaptivni + (1−C) × teoretični`
  - Vrača `AdaptiveTDEEResult(hybridTDEE, adaptiveTDEE, confidenceFactor)` data class
- `debug/WeightPredictorStore.kt`: dodana polja `lastHybridTDEE`, `lastAdaptiveTDEE`, `lastConfidenceFactor`
- `ui/screens/Progress.kt`: `computeWeightPrediction()` posodobljen — bere `theoreticalTDEE` iz `NutritionDebugStore` in shranjuje hibridni rezultat
- `viewmodels/NutritionViewModel.kt`: `setUserMetrics()` preverja `WeightPredictorStore.lastHybridTDEE`; nova funkcija `applyHybridTDEE()`
- `viewmodels/DebugViewModel.kt`: `WeightPredictorDebugInputs` razširjen z novimi hibridnimi polji
- `domain/nutrition/NutritionCalculations.kt`: označena DEPRECATED (prazna datoteka, čaka na ročno brisanje)

**Arhitekturna opomba:** `NutritionViewModel._baseTdee` zdaj preferira hibridni TDEE pred statičnim `BMR × 1.2`, kar odpravi odvisnost od fiksnega Mifflin-St Jeor množilnika po zadostnih realnih podatkih.

### 2026-04-26 — Faza 15 Build Fix + Faza 16: Nutrition UX Recovery & E-Additives

**Faza 15 Build Fix:**
- ✅ **`RunTrackerScreen.kt` vrstica 710**: Zamenjaj `kotlinx.datetime.Clock.System.now().toLocalDateTime(...)` z `java.text.SimpleDateFormat("yyyy-MM-dd").format(Date())` — odpravlja `Unresolved reference 'toLocalDateTime'` compile error.

**Faza 16.1 — Ghost Latency Fix:**
1. ✅ **`NutritionViewModel`**: Dodan `_isNavigating: MutableStateFlow<Boolean>` + `setNavigating(value: Boolean)`. Takoj ob kliku na + v NutritionScreen se postavi na `true` → overlay se pojavi TAKOJ brez zakasnitve.
2. ✅ **`NutritionScreen.kt`**: `isNavigating` collect, loading overlay razširjen na `isLoading || isNavigating`. Ko se ModalBottomSheet odpre, `setNavigating(false)` resetira overlay. Odstranjen `Log.d("DonutRing", "Clicked: ...")` debug log. Popravljen garbled komentar `?? KRITIďż˝NO`.
3. ✅ **`AddFoodSheet.kt`**: `RecentFoodStore.getRecentFoods(context)` premaknen iz `LaunchedEffect(Unit)` v `withContext(Dispatchers.IO)` → ne blokira Main Threada pri odpiranju sheeta.

**Faza 16.2 — Custom Meal Engine:**
- Custom meal funkcionalnost je bila ŽE implementirana (Faza 13+): `MakeCustomMealsDialog`, `SavedMealChip`, `ChooseMealDialog`, `nutritionViewModel.customMealsState`, Firestore `custom_meals` kolekcija. Ni bila potrebna nova implementacija.

**Faza 16.3 — E-Additives Module:**
4. ✅ **`AppNavigation.kt`**: Dodan `object EAdditives : Screen()`.
5. ✅ **`AddFoodSheet.kt` / `RecipesSearchSection`**: Dodan `onOpenAdditives: () -> Unit = {}` parameter. Dodan E-Additives gumb (Info ikona, secondary barva) poleg Scan Barcode gumba.
6. ✅ **`NutritionScreen.kt`**: Dodan `onOpenAdditives: () -> Unit = {}` parameter, posredovan v `RecipesSearchSection`.
7. ✅ **`MainActivity.kt`**: Dodan handler za `Screen.EAdditives` → kliče `EAdditivesScreen(onNavigateBack = { navigateBack() })`. Dodan `onOpenAdditives = { navigateTo(Screen.EAdditives) }` v `NutritionScreen` klic.

### 2026-04-27 — Faza 17: Pre-iOS Audit Popravki (GPS Sync, Weight Destiny, Custom Meals, Encoding, Sync Indicator)

**GPS Cloud Sync:**
1. ✅ **`FirestoreWorkoutRepository.kt`**: `polylinePoints = emptyList()` zamenjano z dejanskim parsanjem Firestore array-a. GPS poti so zdaj dostopne na vseh napravah (ne samo lokalno).

**Weight Destiny Formula Fix:**
2. ✅ **`Progress.kt` — `computeWeightPrediction()`**: `avgDailyBalance` preračunan z `log.calories - hybridTDEE` (ne več `log.calories - burned`). `hybridTDEE` vključuje BMR + activity factor. Negativna vrednost = kalorični deficit → napoved hujšanja je zdaj pravilna.

**Custom Meals Workflow Restrukturiranje:**
3. ✅ **`NutritionDialogs.kt` — `MakeCustomMealsDialog`**: Wizard koraki prestrukturirani: **Korak 1 = Sestavine**, **Korak 2 = Ime obroka**, **Korak 3 = Destinacija**. Dodan gumb "Save Only" (samo shrani brez dodajanja) poleg "Save & Add".

**Encoding / Lokalizacija Cleanup:**
4. ✅ **`AddFoodSheet.kt`**: Garbled Quick Add gumbi popravljeni: `"Banana đźŤ"` → `"Banana "` itd. (UTF-8 encoding bug).
5. ✅ **`ActivityLogScreen.kt`**: `"Hitrost (km/h) / Čas"` → `"Speed (km/h) / Time"`.
6. ✅ **`Progress.kt` — `WeightDestinyCard`**: Vsi Sloveniani nizi prevedeni v angleščino.

**Initial Sync Indicator:**
7. ✅ **`MainActivity.kt`**: Dodan `var isSyncing by remember { mutableStateOf(false) }`. Ob zagonu (ko se nalaga profil iz Firestore) prikaže overlay `"Syncing your fitness data…"` z `CircularProgressIndicator`. Ob uspešnem nalaganju (`isSyncing = false`) se overlay skrije.

### 2026-05-02 — Faza 20: MainActivity Startup Refactor & Threading Fixes

**Spremembe:**
1. ✅ **`MainActivity.kt` — Združitev 3× `LaunchedEffect(Unit)` v en strukturiran tok:**
   - Blok na vrstici 112 (performance timer), blok na vrstici 177 (fresh_start, GlobalScope, partial auth) in blok na vrstici 277 (real auth check + workers) so tekli vzporedno → združeni v en `LaunchedEffect(Unit)` s fazami 1→3g.
   - Odstranjeno: `AppIntent.SetProfile(UserProfileManager.loadProfile(""))` z **praznim emailom** (bil je brez učinka, a povzročal Firestore read z napačnim ključem).
   - Odstranjeno: dvojni `AppIntent.StartListening(userEmail)` klic (enkrat iz prvega, enkrat iz drugega bloka).
   - `isCheckingAuth = false` se zdaj nastavi **enkrat**, na koncu enega bloka.

2. ✅ **`MainActivity.kt` — `GlobalScope.launch` → `scope.launch(Dispatchers.IO)`:**
   - `GlobalScope.launch(Dispatchers.IO)` za Firestore profil fetch (vrstica 225) zamenjano z `scope.launch(Dispatchers.IO)` — coroutine je zdaj vezana na `rememberCoroutineScope()` in se zaključi z Composable lifecycle.

3. ✅ **`NetworkObserver.kt` — 60-sekundna zakasnitev pri ponovni vzpostavitvi signala odpravljena:**
   - `onCapabilitiesChanged` je prej emitiral `false` ko `NET_CAPABILITY_VALIDATED=false` (Android captive portal validacija traja do 60s po WiFi reconnect) → prepisal `onAvailable(true)`.
   - Zdaj `onCapabilitiesChanged` emitira **samo `true`** (ko validated=true). `false` ob izgubi signala ureja `onLost`.

4. ✅ **`NutritionViewModel.kt` — `syncHealthConnectNow` na Main thread → IO:**
   - `viewModelScope.launch { ... }` zamenjano z `viewModelScope.launch(Dispatchers.IO) { ... }`.
   - Health Connect branje + Firestore transakcija ne blokirata več UI niti.

### 2026-04-28 — Faza 19: Custom Meal Flow, Global Sync v AppViewModel, GPS Subcollection

**1. Custom Meal Flow Fix (`NutritionDialogs.kt` — `MakeCustomMealsDialog`):**
- ✅ **"Save Only" prestavljen na Korak 2** (Name step): Gumb takoj shrani obrok v `custom_meals` Firestore kolekcijo in pokliče `onDismiss()`. Brez navigacije na Korak 3.
- ✅ **"Save & Add" na Koraku 2** → navigira na Korak 3 (izbira destinacije).
- ✅ **Korak 3** zdaj vsebuje samo "Save & Add" gumb (brez redundantnega "Save Only").
- **Root cause**: Prej je bil "Save Only" na Koraku 3 — kar je zahtevalo, da uporabnik prejde skozi Korak 3 za "samo shranjevanje", kar je bilo nesmiselno.

**2. Global Sync Logic → AppViewModel (`AppViewModel.kt`, `MainActivity.kt`):**
- ✅ **`AppViewModel.startInitialSync(context, email)`**: Nova suspending funkcija, ki vsebuje logiko `InitialSyncManager` (preseljenooiz `MainActivity` `LaunchedEffect`).
- ✅ **`_isProfileReady: MutableStateFlow<Boolean>`** (default `false`) + **`_syncStatusMessage`**: Overlay se vrti čez cel zaslon dokler `isProfileReady != true`.
- ✅ **`resetSyncState()`**: Pokliče se ob odjavi → naslednji login prikaže svež sync.
- ✅ **MainActivity**: Zamenjani lokalni `var isSyncing` / `var syncStatusMessage` z `appViewModel.isProfileReady.collectAsState()` / `appViewModel.syncStatusMessage.collectAsState()`. Overlay pogoj spremenjen iz `isSyncing` v `!isProfileReady`.
- **Root cause**: Sync logika je bila vgrajena neposredno v `LaunchedEffect(Unit)` v Composable → ni preživela configuration change (rotacija zaslona). ViewModel sync preživi.

**3. GPS Firestore Data Link (`FirestoreWorkoutRepository.kt`):**
- ✅ **`loadGpsPoints(sessionRef, inlinePoints)`**: Nova zasebna funkcija, ki poskusi (v vrstnem redu): `gps_points` subcollection → `points` subcollection (GPS_POINTS_MIGRATION_PLAN format) → inline `polylinePoints` (stari format, backwards compat).
- ✅ **Vsi teki/kolesarjenja/hoja** (`CYCLING`, `WALKING`, `RUNNING`) in vsi dokumenti brez inline točk (`inlinePoints.isEmpty()`) avtomatično pridobijo GPS točke iz sub-kolekcije.
- **Root cause**: Drugi telefon je videl trening metadata, ne pa GPS točk — ker jih ni bilo v glavnem dokumentu in ni bilo fallback branja sub-kolekcije.

**4. Algorithm Audit:**
- ✅ Ustvarjena `GLOW_UPP_LOGIC_AUDIT.md` z Markdown tabelami za Streak Logic, XP Calculation, PlanPath in Workout/Rest Days.

### 2026-05-02 — Faza 4: Sanitacija logike, varnost in i18n

**1. PII Logging Fixes (KRITIČNO — GDPR/varnost):**
- ✅ `WeeklyStreakWorker.kt`: `Log.d("... for $email")` → `Log.d("Daily streak check running")` — email izbrisan iz loga
- ✅ `FirestoreHelper.kt:74`: `"...falling back to UID: $uid"` → `"...falling back to UID"` — UID izbrisan iz loga
- ✅ `FirestoreHelper.kt:96`: `"...Migrating to Email: $email"` → `"...Migrating to Email document."` — email izbrisan
- ✅ `AppViewModel.kt:138`: `"InitialSync končan za uid=$initialSyncUid"` → `"InitialSync končan"` — UID izbrisan
- ✅ `Progress.kt:1047`: `"uid=$uid, weight=$wKg"` → `"Starting nutrition plan recalculation"` — uid + telesna teža izbrisana
- ✅ `fatsecret_api_service.kt`: `Log "Base URL: $baseUrlValue"` in `"Request URL: $url"` odstranjeni — URL vsebuje iskalne poizvedbe = vedenjski PII

**2. Dead Code — stenziliran v prazne stub-e (čaka ročno brisanje):**
- ✅ `network/ai_utils.kt` → vsebina zamenjana z minimalnim package stub-om
- ✅ `ui/adapters/ChallengeAdapter.kt` → vsebina zamenjana z minimalnim package stub-om
- ℹ️ `domain/nutrition/NutritionCalculations.kt` → že bila prazna, ostane
- **⚠️ AKCIJA POTREBNA**: Ročno zbriši te 3 datoteke

**3. Hardcoded Strings → strings.xml (i18n Faza 4):**
- ✅ `strings.xml`: Dodanih 12 novih string resourcov (auth napake + toast sporočila)
- ✅ `MainActivity.kt auth flow`: Vse statične napake (`"Please enter your email."`, itd.) zamenjane z `context.getString(R.string.xxx)`
- ✅ `NutritionScreen.kt`: Toast sporočila (`"Meal Saved"`, `"Not logged in"`, itd.) zamenjana z `context.getString()`
- ✅ `Progress.kt`: Toast sporočila (`"+50 XP Earned!"`, `"✅ Nutrition plan updated!"`, itd.) zamenjana z `context.getString()`
- ℹ️ Backlog: ~50+ preostalih Compose UI-label nizov v ostalih screenih (DeveloperSettingsScreen, RunTrackerScreen itd.) — niso kritični za produkcijo

**4. simulateDayPass — DEBUG-only zaklep:**
- ✅ `WeeklyStreakWorker.simulateDayPass()`: Dodan `if (!BuildConfig.DEBUG) return` guard
- V Release buildu funkcija takoj vrne, brez akcije — ne more se sprožiti iz DeveloperSettingsScreen

### 2026-05-02 — Faza 3: Performance & UI/UX Poliranje

**1. Dark Mode Flash (`MainActivity.kt`):**
- ✅ Dodan `private var initialDarkMode = false` field v `MainActivity`.
- ✅ V `onCreate()` PRED `setContent`, sinhrono prebran iz `user_prefs` SharedPreferences: `getSharedPreferences("user_prefs", MODE_PRIVATE).getBoolean("dark_mode", false)`.
- ✅ `var isDarkMode` začne z `initialDarkMode` namesto `false` → bel blisk odpravljen.
- ✅ Ob Firestore fetch in ob toggleu, dark mode se hkrati shrani v `user_prefs` → naslednji zagon brez bliska.
- **Root cause**: `isDarkMode = false` je povzročal, da se je app renderiral v svetlem načinu, šele po async Firestore klicu (100-500ms) pa je dobil pravo vrednost.

**2. XPPopup Contrast (`XPPopup.kt`):**
- ✅ `color = Color.White` zamenjano z `color = MaterialTheme.colorScheme.onPrimary`.
- ✅ V svetlem načinu: kremasta bela (`#FCFBF8`) na temno vijolični (`#38305A`) → WCAG AA ✅.
- ✅ V temnem načinu: temno vijolična (`#38305A`) na svetli pastelni modri (`#DCE4FF`) → WCAG AA ✅.
- **Root cause**: Dark mode `primary = Color(0xFFDCE4FF)` (svetlo pastelna) + hardcoded `Color.White` = kontrast ratio pod 2:1.

**3. HapticFeedback Throttle:**
- ℹ️ Že implementiran v `HapticFeedback.kt` (50ms, liniji 26-44). Nobenih sprememb ni bilo potrebnih.

**4. NutritionViewModel Memory Leak (`NutritionViewModel.kt`):**
- ✅ `observeDailyTotals()`: nested collect (`uidFlow.collect { ... .collect { } }`) zamenjano z `flatMapLatest + launchIn`.
- ✅ Dodan `@OptIn(ExperimentalCoroutinesApi::class)` na obe `flatMapLatest` uporabi.
- ✅ Nova funkcija `clearUser()`: nastavi `uidFlow.value = null`, počisti `_firestoreFoods`, cancela `waterSyncJob`.
- ✅ `syncHealthConnectNow` sedaj teče na `Dispatchers.IO` (prepreči blokiranje UI niti).
- ✅ `nutritionViewModel.clearUser()` se kliče ob logout v `MainActivity`.
- **Root cause**: `uidFlow.collect { ... }` je bil zamrznjen na prvem uid, ker se zunanji collect ne more nadaljevati dokler notranji `.collect { doc }` ne konča (Firestore listener nikoli ne konča).

### 2026-05-02 — Faza 2: Konsolidacija podatkov (Firestore polja)

**1. profilePictureUrl (`UserProfileManager.kt`):**
- ✅ `KEY_PROFILE_PICTURE` spremenjen iz `"profile_picture_url"` (snake_case) → `"profilePictureUrl"` (camelCase). `saveProfileFirestore` zdaj uporablja konstanto namesto hardcode stringa.
- **Root cause**: App je pisala pod `"profilePictureUrl"` in brala pod `"profile_picture_url"` → profilne slike se niso nikoli naložile iz Firestore.

**2. login_streak → streak_days (`FirestoreGamificationRepository.kt`):**
- ✅ Vse tri metode (`getCurrentStreak`, `updateStreak`, `runMidnightStreakCheck`) zdaj pišejo/berejo `"streak_days"` namesto `"login_streak"`. Oba polja sta bila prisotna v Firestore — zdaj en vir resnice.

**3. workoutSessions timestamp (`FirestoreWorkoutRepository.kt`):**
- ✅ `getWeeklyDoneCount` popravljeno: prej je poizvedovalo po polju `"date"` s `Firestore Timestamp`, čeprav dokumenti hranijo epoch ms v polju `"timestamp"`. Zdaj primerja `"timestamp"` (epoch ms). Odstranjen neuporabljen `import com.google.firebase.Timestamp`.

**4. GPS koordinate poenotene (`RunSession.kt`):**
- ✅ `toFirestoreMap()` zdaj shranjuje koordinate z `"lat"`/`"lng"`/`"alt"`/`"spd"`/`"acc"`/`"ts"` — skladno z `RunTrackerScreen`, `RunRouteStore` in `gps_points` subkolekcijo. `FirestoreWorkoutRepository.getRunSessions()` podpira oba formata (backwards compat).

### 2026-04-27 — Faza 18: Meal Builder UI Fix + InitialSyncManager

**Meal Builder Dialog Fix (`NutritionDialogs.kt`):**
8. ✅ `MakeCustomMealsDialog`: `AlertDialog` zdaj pogojno skrit z `if (!showFoodSearch)`. Ko je `ModalBottomSheet` odprt (iskanje sestavin), je `AlertDialog` v celoti iz composable drevesa — brez scrim konflikta, brez `onDismissRequest` uhajanja. Stanje (ingredients, name, step) ohranjen ker je deklarirano zunaj obeh composablov.

**InitialSyncManager (`MainActivity.kt`):**
9. ✅ Nov `syncStatusMessage: String` state zamenja hardcoded `"Syncing your fitness data…"` v overlayu.
10. ✅ Detekcija nove naprave: `initial_sync_done_<uid>` v `sync_prefs` SharedPreferences. Ob prvi prijavi (ključ odsoten) se nastavi `syncStatusMessage = "Downloading your fitness profile (XP, Plans & Progress)…"`.
11. ✅ Vzporedni `async` fetch-i za: `users/{uid}` (XP/level), `user_plans/{uid}` (plani), `weightLogs` (zadnjih 10). Vsi tečejo hkrati — čakamo z `.await()`. Po uspešnem prenosu: `"Profile Ready! ✓"` (1.5s) → overlay izgine.
12. ✅ Po intenzivnem prenosu se nastavi `initial_sync_done_<uid> = true` → nadaljnji zagoni gredo skozi normalni (varčni) tok.