# 🔬 DEEP AUDIT: `plan_day` Lifecycle
**Datum:** 2026-05-29 | **Tip:** Risk Analysis | **Metodologija:** Statična analiza izvršljive Kotlin kode (komentarji ignorirani)

---

## 1. VIR PODATKOV (Source of Truth)

### 1.1 Firestore lokacija
`plan_day` je `Long` polje na root nivoju dokumenta `users/{resolvedDocId}` (email ali UID).

**Konkretno polje:**
```
users/{email}
  └─ plan_day: Long   ← edini SSOT za persistence
```

### 1.2 Prebiranje ob zagonu aplikacije

Pretok beranja ob zagonu (ko `BodyModuleHomeScreen` dobi `LoadMetrics` intent):

```
BodyModuleHomeScreen
└─ LaunchedEffect(currentPlan)
     └─ vm.handleIntent(LoadMetrics(currentPlan))
          └─ BodyModuleHomeViewModel.handleIntent(LoadMetrics)
               └─ getBodyMetrics.invoke(email)    ← GetBodyMetricsUseCase
                    └─ statsRepo.observeWorkoutStats(email)  ← UserWorkoutStatsRepository
                         └─ FirestoreHelper.getCurrentUserDocRef()
                              └─ docRef.addSnapshotListener(Dispatchers.Default.asExecutor()) { snapshot, _ ->
                                   snapshot.getLong("plan_day")?.toInt() ?: 1   ← PRIVZETO 1
                                 }
```

**Natančni repozitorij:** `UserWorkoutStatsRepository` (data sloj)
**Natančni UseCase:** `GetBodyMetricsUseCase` (domain sloj)

**Kritični defaulti v read path:**

| Razred | Vrstica (privzeta vrednost) | Posledica |
|---|---|---|
| `UserWorkoutStatsRepository.observeWorkoutStats()` | `snapshot.getLong("plan_day")?.toInt() ?: 1` | Nič v Firestoreu → `planDay = 1` |
| `UserWorkoutStatsRepository.getWorkoutStats()` | `doc.getLong("plan_day")?.toInt() ?: 1` | Nič v Firestoreu → `planDay = 1` |
| `WorkoutStats` data class | `val planDay: Int = 1` | Konstruktor default |
| `BodyMetrics` data class | `val planDay: Int = 1` | Konstruktor default |

---

## 2. STANJE V VIEWMODELU (UI State)

### 2.1 Kako BodyModuleHomeViewModel hrani `plan_day`

`plan_day` **ni** shranjen kot samostojna `StateFlow`. Je vgnezdena lastnost v:

```kotlin
_ui: MutableStateFlow<BodyUiState>
  └─ .metrics: BodyMetrics?        ← NULL dokler Firestore ni vrnil prve emisije
       └─ .planDay: Int = 1        ← default, NIKOLI prikazan UI (skeleton guard)
```

**BodyUiState inicializacija:**
```kotlin
private val _ui = MutableStateFlow(BodyUiState())
// BodyUiState() → metrics = null, isLoading = false
```

`metrics` je `null` ob kreaciji ViewModel-a in ostane `null` dokler `getBodyMetrics.invoke(email).collect { }` ne emitira prvega uspešnega rezultata.

### 2.2 Ali defaulta na `1`?

**Ne direktno v UI.** Compose zaslon ima eksplicitni skeleton guard:

```kotlin
// BodyModuleHomeScreen.kt
if (ui.metrics == null) {
    CircularProgressIndicator(...)   // skeleton spinner
} else {
    // prikaži metrics?.planDay ← NIKOLI dosežemo sem s privzetim 1
}
```

Ko je `metrics == null`, UI prikaže spinner — ne privzetih vrednosti. Ko `metrics` postane ne-null, `planDay` prikazuje **Firestore vrednost**, ne lokalni default.

⚠️ IZJEMA: `WorkoutSessionScreen` (gl. Sekcijo 4, BP-2) bere `planDay` z `?: 1` fallbackom brez skeleton guarda.

### 2.3 Kdaj se posodobi iz baze

`plan_day` v VM stanju se posodobi v dveh primerih:

#### A — Reaktivni Firestore event (snapshot listener)
Vsakič, ko Firestore dokument se spremeni, `addSnapshotListener` sproži novo emisijo v `observeWorkoutStats()`. `GetBodyMetricsUseCase.channelFlow` to prejme in prek `collect {}` posodobi `_ui.update { copy(metrics = ...) }`.

**Zakasnitev:** milisekunde (Firestore SDK realtime push).

#### B — Optimistična posodobitev po zaključku treninga
Neposredno v `handleIntent(CompleteWorkoutSession)`:
```kotlin
_ui.update { current ->
    current.copy(
        metrics = current.metrics?.copy(
            planDay = newPlanDay,  ← takoj, brez čakanja na Firestore round-trip
            ...
        )
    )
}
```
**Zakasnitev:** 0 (optimistična — pred potrditvijo Firestorea).

---

## 3. MUTACIJA (Ko uporabnik pritisne "Finish Workout")

### 3.1 Vstopna točka v UI

**Datoteka:** `WorkoutSessionScreen.kt` (vrstica ~545)

```kotlin
// WorkoutState.Report → klik "Done"
vm.handleIntent(
    BodyHomeIntent.CompleteWorkoutSession(
        email          = FirebaseAuth.getInstance().currentUser?.email ?: "",
        isExtraWorkout = isExtra,
        totalKcal      = results.sumOf { it.caloriesKcal },
        totalTimeMin   = results.sumOf { it.activeMinutes + it.restMinutes },
        exerciseResults = results.map { r -> mapOf("name" to r.name, ...) },
        focusAreas     = currentFocusAreas.toList(),
        onCompletion   = { result: WorkoutCompletionResult? -> ... }
    )
)
```

**Kritično:** `email` se prebere neposredno iz `FirebaseAuth.getInstance().currentUser?.email` — mimo `FirestoreHelper` in `AuthStateRepository`. Prazni `email` ne ustavi toka.

### 3.2 Celoten klic graf (plan_day fokus)

```
WorkoutSessionScreen (UI)
  │
  │  handleIntent(CompleteWorkoutSession(email, isExtra=false, totalKcal, ...))
  ▼
BodyModuleHomeViewModel.handleIntent()
  │
  │  [1] Multi-tap guard: if (activeAsyncOperations.value > 0) return@launch
  │  [2] activeAsyncOperations.update { it + 1 }
  │  [3] Snapshot PRED suspend klicem:
  │       val currentStateSnapshot = _ui.value
  │       val oldPlanDay = currentStateSnapshot.metrics?.planDay ?: 1   ← SNAPPIRA LOKALNI UI STATE
  │
  │  updateBodyMetrics.invoke(
  │      email, totalKcal, totalTimeMin, exercisesCount,
  │      planDay = oldPlanDay,          ← prenese snapshot vrednost (ne svežo iz Firestorea!)
  │      isExtra = false,
  │      exerciseResults, focusAreas,
  │      isRestDay = todayIsRest && false   ← ker isExtra=false
  │  )
  ▼
UpdateBodyMetricsUseCase.invoke()
  │
  │  [4] Gradi workoutDoc:
  │       val workoutDoc = mutableMapOf(
  │           "planDay" to planDay,     ← planDay iz parametra (= oldPlanDay iz VM snapshot-a)
  │           "type"    to "regular",
  │           ...
  │       )
  │
  │  gamificationUseCase.recordWorkoutCompletion(
  │      caloriesBurned   = totalKcal.toDouble(),
  │      isRestDay        = false,          ← ker isRestDay && isExtra = false && false
  │      incrementPlanDay = true,           ← ker !isExtra = true
  │      currentPlanDay   = planDay,        ← oldPlanDay (UI snapshot)
  │      workoutSessionDoc = workoutDoc
  │  )
  ▼
ManageGamificationUseCase.recordWorkoutCompletion()
  │
  │  [5] newStatus = UserDayStatus.WORKOUT_DONE
  │  [6] shouldIncrement = true  (incrementPlanDay && !isRestDay)
  │
  │  repository.moveToNextDay(
  │      newStatus        = WORKOUT_DONE,
  │      xpToBeAwarded    = 50 + (caloriesBurned/8).toInt(),
  │      incrementPlanDay = true,
  │      workoutSessionDoc = workoutDoc
  │  )
  │
  │  [7] LOKALNI IZRAČUN (neodvisen od Firestore rezultata!):
  │       val newPlanDay = if (shouldIncrement && currentPlanDay > 0)
  │                            currentPlanDay + 1   ← oldPlanDay + 1
  │                        else currentPlanDay
  │
  │  return WorkoutCompletionResult(newPlanDay = newPlanDay, newStreakDays = newStreak)
  ▼
FirestoreGamificationRepository.moveToNextDay()   ← EDINO MESTO Z DEJANSKIM FIRESTORE WRITE-OM
  │
  │  db.runTransaction { transaction ->
  │
  │    ── READ FAZA ──────────────────────────────────────────────
  │    val snapshot   = transaction.get(userRef)
  │    val oldPlanDay = (snapshot.getLong("plan_day") ?: 1L).toInt()   ← BERE IZ FIRESTOREA
  │
  │    ── DE-DUP PREVERJANJE ─────────────────────────────────────
  │    if (existingStatus == WORKOUT_DONE || existingStatus == newStatus) {
  │        return@runTransaction snapshot.getLong("streak_days")?.toInt() ?: 0
  │        // ⚠️ NE incrementira plan_day — vrne samo streak
  │    }
  │
  │    ── IZRAČUN NOVEGA plan_day ─────────────────────────────────
  │    val newPlanDay = if (incrementPlanDay) oldPlanDay + 1 else oldPlanDay
  │
  │    ── WRITE FAZA ──────────────────────────────────────────────
  │    transaction.update(userRef, mapOf(
  │        "plan_day"    to newPlanDay,   ← DEJANSKO PISANJE V FIRESTORE
  │        "streak_days" to newStreak,
  │        "dailyHistory.$todayStr" to "WORKOUT_DONE",
  │        "xp"          to newXp,
  │        "level"       to newLevel,
  │        ...
  │    ))
  │    transaction.set(sessionRef, workoutDoc)   ← workout session v isto transakcijo
  │
  │    return newStreak
  │  }.await()
  ▼
← vrne: newStreak (Int) — NE newPlanDay!

ManageGamificationUseCase ← WorkoutCompletionResult(newPlanDay = oldPlanDay+1, newStreakDays = newStreak)
UpdateBodyMetricsUseCase ← Result.success(WorkoutCompletionResult)
BodyModuleHomeViewModel
  │
  │  [8] OPTIMISTIČNA POSODOBITEV UI:
  │       val newPlanDay = completionResult?.newPlanDay?.takeIf { it > 0 }
  │                        ?: (oldPlanDay + if (!isExtra) 1 else 0)
  │       _ui.update { current ->
  │           current.copy(metrics = current.metrics?.copy(
  │               planDay = newPlanDay,   ← posodobi UI state TAKOJ
  │               streakDays = newStreak,
  │               isWorkoutDoneToday = true,
  │               todayStatus = WORKOUT_DONE
  │           ))
  │       }
  │
  │  [9] Reaktivni Firestore event (sekunde zatem):
  │       Snapshot listener → observeWorkoutStats() → GetBodyMetricsUseCase
  │       → collect → _ui.update { metrics = svežo stanje }
  │       (Prepiše optimistično vrednost z Firestore vrednostjo)
  ▼
WorkoutSessionScreen → onCompletion(result) → onXPAdded() → onFinished()
  └─ MainAppContent: navViewModel.popTo(Screen.BodyModuleHome)
```

---

## 4. PRELOMNE TOČKE (Breakpoints / Risk Analysis)

---

### 🔴 BP-1 — WorkoutSessionScreen: NAPAČNA factory za BodyModuleHomeViewModel

**Lokacija:** `WorkoutSessionScreen.kt` vrstica 177  
**Resnost:** KRITIČNO (potencialni crash ob nenavadni navigaciji)

```kotlin
val vm: BodyModuleHomeViewModel = viewModel(
    factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
        context.applicationContext as Application
    )
)
```

`BodyModuleHomeViewModel` ima 9 konstruktorskih parametrov (GetBodyMetricsUseCase, UpdateBodyMetricsUseCase, ManageGamificationUseCase…). `AndroidViewModelFactory` ne zna kreirati takih ViewModelov — zna samo `AndroidViewModel(app)` ali no-arg `ViewModel`.

**Zakaj to ZDAJ ne crashira:**  
`MainAppContent` renderira vse zaslone v enem `when { }` bloku brez NavHost. Vsi klici `viewModel()` delijo isti `LocalViewModelStoreOwner` (Activity). Ker `BodyModuleHomeScreen` vedno pride PRED `WorkoutSessionScreen` v navigacijski poti (Dashboard → BodyModuleHome → LoadingWorkout → WorkoutSession), je ViewModel že ustvarjen z `MyViewModelFactory` ko `WorkoutSessionScreen` pride na vrsto. `viewModel()` vrne obstoječo instanco — factory se ne sproži.

**Kdaj BO crashiralo:**  
- Ob prehodu na Navigation Compose (NavHost) — vsak `NavBackStackEntry` ima lasten `ViewModelStoreOwner`
- Ob direct link ali deep link direktno na `Screen.WorkoutSession`
- Ob katerikoli spremembi navigacijske infrastrukture

---

### 🔴 BP-2 — WorkoutSessionScreen: `planDay = 1` ko metrics ni naložen

**Lokacija:** `WorkoutSessionScreen.kt` vrstica 413  
**Resnost:** KRITIČNO (tiha napaka podatkov — plan_day se restarira na `2` ne na `N+1`)

```kotlin
workoutVm.prepareWorkout(
    planDay = (vmUiState.metrics?.planDay ?: 1).coerceAtLeast(1),
    ...
)
```

Vrstica 529:
```kotlin
planDay = vmUiState.metrics?.planDay ?: 1,
```

Oba klica fallbackata na `1` če `metrics == null`. Ni skeleton guarda kot v `BodyModuleHomeScreen`.

**Pot do napake:**
1. Firestore je počasen ali ni mreže
2. `LoadMetrics` je bil sprožen, a ni še emitiral (metrics == null)
3. Uporabnik klikne "Start Workout" in trening se začne
4. `planDay = 1` je posredovan v `CompleteWorkoutSession`
5. `FirestoreGamificationRepository.moveToNextDay()` prebere PRAVO vrednost iz Firestore (npr. 15) in zapiše `15+1=16`
6. `ManageGamificationUseCase` izračuna `newPlanDay = 1+1 = 2` (iz stale UI stanja)
7. VM posodobi UI z `planDay=2`, čeprav je Firestore zapisal `16`
8. Ko pride Firestore reaktivni event, UI se popravi na `16`

**Opomba:** Firestore transakcija BERE svežo vrednost (`oldPlanDay = snapshot.getLong("plan_day")`) — torej Firestore bo imel pravilno vrednost `16`. Napaka je samo v OPTIMISTIČNI UI posodobitvi, ki bo napačna do naslednjega Firestore eventa.

---

### 🔴 BP-3 — De-dup sproži lokalni planDay izračun brez Firestore confirmation

**Lokacija:** `ManageGamificationUseCase.recordWorkoutCompletion()` vrstice 76–84  
**Resnost:** VISOKO (UI prikazuje napačen planDay po dvojnem kliku)

```kotlin
// ManageGamificationUseCase.kt
val newStreak = repository.moveToNextDay(...)   // lahko vrne 0 (de-dup) ali -1 (Firestore napaka)
val newPlanDay = if (shouldIncrement && currentPlanDay > 0) currentPlanDay + 1 else currentPlanDay
// newPlanDay se izračuna BREZ preverjanja ali je Firestore dejansko incrementiral!
return WorkoutCompletionResult(..., newPlanDay = newPlanDay)
```

**Primer napake:**
1. Uporabnik zaključi trening → `plan_day` v Firestoreu naraste na `16`
2. `dailyHistory[today] = "WORKOUT_DONE"` v Firestoreu
3. Firestore reaktivni event → UI se posodobi na `planDay=16`
4. Ista seja se nekako sproži znova (edge case: onCompletion klic, navigacijska napaka)
5. `moveToNextDay()` ujame de-dup: `existingStatus == WORKOUT_DONE` → vrne obstoječ streak **brez pisanja**
6. `ManageGamificationUseCase` izračuna `newPlanDay = 16+1 = 17` ker de-dup ni signaliziran
7. VM posodobi UI z `planDay=17` — **Firestore ima 16**

---

### 🔴 BP-4 — Firestore transakcija spodleti → optimistična UI napreduje, DB ne

**Lokacija:** `FirestoreGamificationRepository.moveToNextDay()` vrstice 296–302  
**Resnost:** VISOKO (UI state razkorači od Firestore stanja)

Ko Firestore transakcija spodleti, repozitorij vrne `-1`:
```kotlin
} catch (e: Exception) {
    Log.e("GamificationRepo", "❌ moveToNextDay spodletel: ${e.message}", e)
    return -1   // signal napake, ne 0
}
```

V `UpdateBodyMetricsUseCase.invoke()`:
```kotlin
val res = gamificationUseCase.recordWorkoutCompletion(...)
Result.success(res)   // ← Result.success ker ni izjeme — napaka je tiho zaužita v -1 return!
```

`UpdateBodyMetricsUseCase` vrne `Result.success(WorkoutCompletionResult(newPlanDay=N+1, newStreakDays=-1))` ker `FirestoreGamificationRepository` ne meče izjeme ob napaki — vrne `-1`.

V VM:
```kotlin
if (result.isSuccess) {                           // ← true, kljub Firestore napaki!
    val completionResult = result.getOrNull()
    val newPlanDay = completionResult?.newPlanDay?.takeIf { it > 0 }   // ← N+1 > 0 → da!
    _ui.update { copy(metrics = metrics.copy(planDay = newPlanDay)) }   // ← UI naraste!
}
```

**Rezultat:** Firestore ima `plan_day = N`, UI prikazuje `N+1`. Snackbar NI prikazan (gre samo na "ShowSnackbar" pot). Napaka je opazna šele ob naslednji navigaciji na BodyModuleHome ko reaktivni event povrne pravo vrednost iz Firestorea.

---

### 🟡 BP-5 — `plan_day` reset ob ustvaritvi novega plana BREZ transakcije

**Lokacija:** `MainAppContent.kt` vrstica 562–565  
**Resnost:** SREDNJE (neatomarni reset)

```kotlin
FirestoreHelper.getCurrentUserDocRef()
    .set(
        mapOf("plan_day" to 1, "weekly_target" to weeklyTargetToSave, "weekly_done" to 0),
        com.google.firebase.firestore.SetOptions.merge()
    ).await()
```

Ta klic je **izven transakcije** — navaden `.set()`. Ni preverjanja ali je trening danes že bil opravljen. Teoretično: če uporabnik zaključi trening v milisekundi pred ustvarjanjem novega plana (izreden edge case), `plan_day` se ponastavi na `1` kljub opravljenemu treningu.

Napaka ni kritična za normalno uporabo, ampak je arhitekturno nedosleden (ostali `plan_day` zapisi gredo skozi transakcijo).

---

### 🟡 BP-6 — `UserPreferencesRepository.getPlanDay()` — shadow lokalnega SharedPrefs cache-a

**Lokacija:** `UserPreferencesRepository.kt` vrstica 59–61  
**Resnost:** SREDNJE (zastarela vrednost v edge case-u)

```kotlin
fun getPlanDay(): Int {
    return bmSettings.getInt("plan_day", 1)   // bere iz "bm_prefs" SharedPrefs
}
```

`WorkoutStatsRepository.getPlanDay()` delegira na to metodo:
```kotlin
override suspend fun getPlanDay(): Int = prefs.getPlanDay()
```

Ta lokalna vrednost se posodablja le prek `UserPreferencesRepository.updateWorkoutStats(completedDay, timestamp)`, ki je označena kot DEPRECATED (Faza 9.2) in bi morala biti klicana samo kot fallback. Lokalni cache ni sinhroniziran z Firestore — če ni mreže in UI pri načinu gradnje zaupa lokalnim fallbackom, bi lahko prebral staro vrednost.

V normalnem toku `GetBodyMetricsUseCase` NE kliče `getPlanDay()` — klicano je samo v `WorkoutStatsRepository.getDailyCalories()` (za `dailyKcal`). `getPlanDay()` sam po sebi ne pride do UI prek `GetBodyMetricsUseCase`.

---

### 🟡 BP-7 — `UserProfileManager.saveWorkoutStats()` — neposreden `plan_day` write brez transakcije

**Lokacija:** `UserProfileManager.kt` vrstice 158–181  
**Resnost:** SREDNJE (obstoj legacy write poti z `planDay: Int = 1` defaultom)

```kotlin
suspend fun saveWorkoutStats(
    email: String,
    streak: Int,
    totalWorkouts: Int,
    weeklyDone: Int,
    lastWorkoutEpoch: Long,
    planDay: Int = 1,   // ← PRIVZETO 1!
    weeklyTarget: Int = 0,
    batch: WriteBatch? = null
) {
    val data = mapOf(
        "plan_day" to planDay,   // ← piše plan_day v Firestore BREZ transakcije
        ...
    )
    FirestoreHelper.getCurrentUserDocRef().set(data, SetOptions.merge()).await()
}
```

Funkcija pišepre `plan_day` z `SetOptions.merge()` — ne transakcijsko. Če je klicana kjer koli z `planDay` defaultom (`= 1`), **prepiše pravi plan_day nazaj na 1**.

Preveriti je treba vsak klic te funkcije v kodi.

---

### 🟡 BP-8 — WorkoutSessionScreen bere `planDay` iz potencialno zastarelega VM stanja

**Lokacija:** `WorkoutSessionScreen.kt` vrstica 413  
**Resnost:** SREDNJE (race condition med Firestore snapshot-om in workout generiranjem)

```kotlin
planDay = (vmUiState.metrics?.planDay ?: 1).coerceAtLeast(1),
```

`vmUiState` je stanje, ki ga je nazadnje emitiral `BodyModuleHomeViewModel`. Ker `WorkoutSessionScreen` ne kliče `LoadMetrics` (ta je v `BodyModuleHomeScreen`), bere vrednost iz zadnjega stanja ko je bil `BodyModuleHomeScreen` prikazan.

**Scenarij:**
1. Danes ponoči: `WeeklyStreakWorker` ne uspe (ni omrežja)
2. Jutri zjutraj: uporabnik odpre app → Firestore vrne `planDay = 17`
3. Takoj klikne "Start Workout" — `WorkoutSessionScreen` dobi `planDay = 17` ✅
4. Firestore snapshot listener emitira novo vrednost med treningom: `planDay = 17` ← ni spremembe, ok
5. Vse deluje pravilno

Dejansko tveganje je majhno v normalnem toku, ker `LoadMetrics` se sproži ob vsaki navigaciji na `BodyModuleHomeScreen`.

---

### 🟡 BP-9 — `newPlanDay` izračun v VM ne upošteva `incrementPlanDay=false` Firestore rezultata

**Lokacija:** `BodyModuleHomeViewModel.kt` vrstice 756–758  
**Resnost:** NIZKO (samo za extra workout pot)

```kotlin
val newPlanDay = completionResult?.newPlanDay?.takeIf { it > 0 }
    ?: (oldPlanDay + if (!isExtra) 1 else 0)
```

Fallback `(oldPlanDay + if (!isExtra) 1 else 0)` se izvede ko:
- `completionResult == null` (Firestore napaka zaprta v Result.failure)
- `completionResult.newPlanDay <= 0` (de-dup vrnil 0)

Za extra workout: fallback pravilno vrne `oldPlanDay + 0 = oldPlanDay`. ✅

Za regularni workout z napako: fallback vrne `oldPlanDay + 1` → isto tveganje kot BP-4. ⚠️

---

### 🟡 BP-10 — `userWeightKg` v WorkoutSessionScreen defaulta na `70.0 kg` (hardkodirano)

**Lokacija:** `WorkoutSessionScreen.kt` vrstica 192  
**Resnost:** NIZKO (posredni vpliv na XP, ne na plan_day direktno)

```kotlin
var userWeightKg by remember { mutableStateOf(70.0) }
```

Kalkulacija kalorij temelji na `userWeightKg`. Kalorije se posredujejo v `CompleteWorkoutSession.totalKcal`. V `ManageGamificationUseCase`: `calorieXP = (caloriesBurned / 8).toInt()`. Napačen `userWeightKg` → napačen XP, ne direktno napačen `plan_day`. Vpliv na `plan_day` je posreden (ni ga).

---

## 5. POVZETEK TVEGANJ — Prioritetna matrika

| ID | Opis | Vpliv na plan_day | Resnost | Pogostost |
|---|---|---|---|---|
| **BP-1** | `AndroidViewModelFactory` v `WorkoutSessionScreen` | Crash (ni plan_day dostopa) | 🔴 KRITIČNO | Redko (samo ob spremembi navigacije) |
| **BP-2** | `metrics == null` → `planDay=1` v WorkoutSessionScreen | UI prikaže napačen planDay optimistično | 🔴 KRITIČNO | Možno ob počasnem omrežju |
| **BP-3** | De-dup v moveToNextDay → lokalni +1 brez Firestore potrditvE | UI naraste, DB ne (do naslednjega Firestore eventa) | 🔴 KRITIČNO | Edge case |
| **BP-4** | Firestore transakcija spodleti → tiha `Result.success` → UI napreduje | UI razkorači od DB do naslednjega Firestore eventa | 🔴 VISOKO | Ob omrežni napaki |
| **BP-5** | `plan_day = 1` reset ob novem planu — brez transakcije | Pravilni reset, ampak neatomaren | 🟡 SREDNJE | Ob ustvarjanju plana |
| **BP-6** | `bm_prefs` shadow cache z zastarelim `plan_day` | Stara vrednost v lokalnem fallbacku | 🟡 SREDNJE | Redko (samo offline fallback) |
| **BP-7** | `UserProfileManager.saveWorkoutStats(planDay=1)` default | Prepiše plan_day na 1 ob napačnem klicu | 🟡 SREDNJE | Odvisno od klicnih mest |
| **BP-8** | VM stanje potencialno zastarelo med treningom | Minimalni vpliv (Firestore transakcija bere svežo vrednost) | 🟡 NIZKO | Vedno (sprejemljivo) |
| **BP-9** | Fallback formula v VM ne locira de-dup | Isti vpliv kot BP-3 za fallback pot | 🟡 NIZKO | Edge case |

---

## 6. KLJUČNA ARHITEKTURNA OPAŽANJA

### ✅ Kar deluje pravilno

1. **Firestore transakcija je SSOT za dejanski write:** `FirestoreGamificationRepository.moveToNextDay()` bere `oldPlanDay` **neposredno iz Firestorea** znotraj transakcije — ne iz parametra `incrementPlanDay`. Parametri so le navodila za logiko, ne vrednosti.

2. **Skeleton guard v BodyModuleHomeScreen:** `if (ui.metrics == null) CircularProgressIndicator(...)` pravilno preprečuje prikaz `planDay=1` default vrednosti.

3. **De-dup zaščita:** De-dup znotraj transakcije preprečuje dvojno inkrementacijo `plan_day` pri ponovnem klicu na isti dan.

4. **NonCancellable v PlanRepositoryImpl:** `withContext(Dispatchers.IO + NonCancellable)` zagotavlja, da Firestore transakcija ne more biti prekinjena sredi pisanja.

### ❌ Kar je arhitekturno tvegano

1. **Dvoplastna `plan_day` logika:** Firestore transakcija bere `plan_day` iz DB, `ManageGamificationUseCase` izračuna `newPlanDay` lokalno iz parametra. Oba computing-a **nista sinhronizirana**. Transakcija vrne le `newStreak` (ne `newPlanDay`), kar pomeni klic-veriga ne more vedeti kaj je Firestore dejansko zapisal.

2. **`moveToNextDay()` vrne `newStreak`, ne `newPlanDay`:** VM mora **ugibati** `newPlanDay` lokalno namesto da ga pridobi iz Firestore odgovora. To je korenski vzrok BP-3 in BP-4.

3. **Legacy `saveWorkoutStats()` v `UserProfileManager`** ostaja aktivna Firestore pisalna pot za `plan_day` brez transakcije in z nevarnim defaultom `planDay=1`.

---

*Konec revizijskega dokumenta. Nobena koda ni bila spremenjena.*

