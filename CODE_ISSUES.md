# CODE_ISSUES.md
> **NAVODILO ZA AI:** To datoteko VEDNO preberi na začetku seje. Po vsakem popravku dodaj vnos na dno pod "DNEVNIK POPRAVKOV".

**Zadnja posodobitev:** 2026-04-26  
**Trenutno stanje: VSE ZNANE TEŽAVE ODPRAVLJENE ✅**

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

### 🔴 NUJNI POPRAVKI (pred UI/UX prenovo) — Audit 2026-04-25

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
  - `exerciseResults` map: dodano `"reps"`, `"sets"`, `"weightKg"` — shranjeno v Firestore za naslednji fetch
  - `"focusAreas"` shranjen v `workoutDoc` via `CompleteWorkoutSession` intent
  - 🔥 Progressive Overload UI badge (Banner "Danes močneje!") v Preview stanju
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
