# FAZA 4A — Revizija Streak/PlanPath sistema
> Generiran: 2026-05-03 | Samo analiza — brez sprememb kode

---

## 1. 🗺️ PlanPath Audit — Avtomatski Rest Day / Anti-drop logika

### Kje živi logika
**Datoteka:** `app/.../data/gamification/FirestoreGamificationRepository.kt`  
**Metodi:** `runMidnightStreakCheck()` → `checkIfFutureRestDaysExistAndSwap()`

### Kako deluje trenutna "preprečevanje padca streaka" logika

```
POLNOČ (WeeklyStreakWorker.kt) sproži executeMidnightStreakCheck()
    ↓
FirestoreGamificationRepository.runMidnightStreakCheck()
    ↓
  Ali obstaja daily_logs/{včeraj} dokument?
    ├── DA  → Vadba zabeležena. Streak ni ogrožen. Konec.
    └── NE  → Zamujeno! Sproži obrambno logiko:
            ↓
         checkIfFutureRestDaysExistAndSwap(včerajStr)
            ├── Poišče PRIHODNJI REST dan v isti skupini dni (user_plans/{uid})
            ├── Zamenja: zamujeni dan → isRestDay=true, isSwapped=true
            │          prihodnji REST → isRestDay=false (postane workout dan)
            ├── Zapiše daily_logs/{včeraj} = { status: "REST_SWAPPED" }
            └── STREAK OHRANJEN! ✅
            ↓
         Swap ni mogoč (ni REST dana v skupini)?
            ├── streakFreezes > 0 → porabi freeze
            │   Zapiše daily_logs/{včeraj} = { status: "FROZEN" }
            └── Ni freezeov → streak_days = 0 💥
```

### Kritičen bug v checkIfFutureRestDaysExistAndSwap()
```kotlin
// PROBLEM (vrstica ~202):
val currentPlanDayNum = logsSnap.documents.size + 1
// ❌ Predpostavi, da = count(daily_logs) → NAPAČNO pri zamujenih dnevih/freezeih!
// Pravilno bi moralo biti: plan_day polje iz users/{uid}
```

---

## 2. 🧘 Stretching Logic — Status najdbe

### Kje je UI komponenta
**Datoteka:** `app/.../ui/screens/BodyModuleHomeScreen.kt`, vrstica **188–208**

```kotlin
// ✅ UI OBSTAJA in se prikaže samo kadar je: ui.todayIsRest && !ui.isWorkoutDoneToday
if (ui.todayIsRest && !ui.isWorkoutDoneToday) {
    Card(...clickable { vm.handleIntent(BodyHomeIntent.CompleteRestDay) }) {
        Text("🧘")
        Text("Rest Day: Mobility & Stretching")
        Text("Take 5 mins to stretch +10 XP")
    }
}
```

### Status handler v ViewModelu
**Datoteka:** `app/.../viewmodels/BodyModuleHomeViewModel.kt`, vrstica ~84

```kotlin
// ⛔ STUB — NI IMPLEMENTIRANO!
is BodyHomeIntent.CompleteRestDay -> {
    // Future implementation
}
```

### Kaj BI moralo narediti (use case ZE OBSTAJA)
**Datoteka:** `domain/gamification/ManageGamificationUseCase.kt`, vrstica 126–130

```kotlin
// ✅ USE CASE JE PRIPRAVLJEN:
suspend fun restDayInitiated() {
    repository.updateStreak(isWorkoutSuccess = true) // streak se NE poveča (+0)
    repository.awardXP(10, "REST_DAY")
}
```

### Zaključek Stretching analize
| Komponenta | Status |
|---|---|
| UI kartica (Rest Day: Mobility & Stretching) | ✅ Obstaja |
| Klik → `BodyHomeIntent.CompleteRestDay` | ✅ Obstaja |
| ViewModel handler | ⛔ **STUB** — prazna |
| UseCase `restDayInitiated()` | ✅ Pripravljen, samo ne klican |
| Streak posodobitev po stretching | ❌ Nikoli se ne zgodi |
| UI feedback (isWorkoutDoneToday=true) | ❌ Nikoli se ne nastavi |

**Zaključek:** Stretching kartico je potrebno SAMO PRIKLJUČITI — ne graditi na novo.  
ViewController handler `CompleteRestDay` je edini manjkajoči člen.

---

## 3. 📊 Streak Logic Analysis — Celotna slika

### Kako aplikacija ve, da je danes "Workout" dan?

**Metoda A — GetBodyMetricsUseCase (primarni vir za UI)**
```
user_plans/{uid}.plans[0].weeks[n].days → najde dan z dayNumber == plan_day
todayIsRest = day.isRestDay
→ shrani v BodyHomeUiState.todayIsRest
```

**Metoda B — StreakReminderWorker (notifikacije)**
```
users/{uid}.plan_day → checkTodayIsRestFromFirestore(planDay)
→ bere user_plans/{uid} direktno
```

**Metoda C — UserProfileManager.updateUserProgressAfterWorkout() (pri zamenjanju)**
```
Atomarna transakcija: bere users/{uid}.plan_day in last_workout_epoch
dayDiff = todayEpoch - last_workout_epoch
```

### Kje živi streak v Firestoreu danes

```
users/{uid}:
  ├── streak_days: Int           ← glavni streak counter
  ├── last_streak_update_date: String  ← za idempotency v updateStreak()
  ├── streak_freezes: Int        ← disponibilne zamrznitve
  ├── last_workout_epoch: Long   ← epoch DAYS (ne ms!) — za dayDiff izračun
  ├── plan_day: Int              ← trenutni dan v planu (1-based)
  └── /daily_logs/{datum}:       ← sub-kolekcija
        ├── status: "WORKOUT_DONE" | "REST_DONE" | "FROZEN" | "REST_SWAPPED"
        └── timestamp: Long

user_plans/{uid}:
  └── plans[0].weeks[n].days[d]:
        ├── isRestDay: Boolean
        ├── focusLabel: String
        ├── isSwapped: Boolean
        └── isFrozen: Boolean
```

### Konflikt med viri (dokumentiran problem)
| Vir | Piše streak | Bere streak |
|---|---|---|
| `UserProfileManager.updateUserProgressAfterWorkout()` | ✅ (transakcija) | ✅ |
| `FirestoreGamificationRepository.updateStreak()` | ✅ (transakcija) | ✅ |
| `WeeklyStreakWorker` → `runMidnightStreakCheck()` | ✅ | ✅ |
| `ManageGamificationUseCase.restDayInitiated()` | ✅ (prek repo) | – |

**⚠️ Podvajanje:** `BodyModuleHomeViewModel.CompleteWorkoutSession` kliče tako  
`updateBodyMetrics.invoke()` (→ UserProfileManager) **KOT** (posredno) gamification logiko.  
**Streak se dejansko posodablja v UserProfileManager transakciji**, ne v GamificationRepository pri workouta.  
GamificationRepository se kliče le pri **Rest Day** (in nikoli, ker je stub). To pomeni,  
da sta `users/{uid}.streak_days` (GamificationRepo) in `users/{uid}.streak_days`  
(UserProfileManager) IST polje — ni duplikacije polj — a dve poti do njega.

---

## 4. 🏗️ Firestore Schema Predlog — Minimalni Streak Dokument

### Cilj
Vse streak-relevantne podatke preseliti v en skupen strukturiran dokument,  
ki ga bere/piše **en sam repozitorij (SSOT)**.

### Predlagana struktura

```
users/{uid}:
  lastActivityTimestamp: Long          # epoch milliseconds (ne days!) — enotna enota
  currentStreak: Int                   # preimenuj streak_days → currentStreak
  streakFreezeCount: Int               # preimenuj streak_freezes → streakFreezeCount
  planDay: Int                         # ostane plan_day → planDay (camelCase)

  # NOVO — dailyHistory kot MAP (alternativa sub-kolekciji)
  dailyHistory: {
    "2026-05-01": "WORKOUT_DONE",
    "2026-05-02": "REST_DONE",
    "2026-05-03": "STRETCHING_DONE",   # ← NOVO za stretching
    ...
  }
```

### Primerjava: MAP vs Sub-kolekcija

| Kriterij | Map (predlog) | Sub-kolekcija (zdaj) |
|---|---|---|
| Branje enega dne | `users/{uid}.dailyHistory["2026-05-03"]` | `users/{uid}/daily_logs/2026-05-03` |
| Branje celotne zgodovine | 1 read | N reads |
| Pisanje | `update({"dailyHistory.2026-05-03": "DONE"})` | `set(/daily_logs/datum, {...})` |
| Firestore cena | Boljše (1 dokument) | Dražje (N dokumentov) |
| Omejitev | 1 MB dokument limit | Neomejeno |
| Priporočilo | ✅ Za prvih 3 leta / ~1000 dni | Za tekmovalce z 1000+ dnevi |

> **Priporočilo:** Ohrani sub-kolekcijo `daily_logs` za obstoječe zapise,  
> a dodaj denormalizirani `dailyHistory` map za hitro branje (streak screen, widget).

### Predlagani novi Firestore dokument za streak (finalna oblika)

```json
// users/{uid} — samo streak polja
{
  "currentStreak": 42,
  "lastActivityTimestamp": 1746230400000,
  "streakFreezeCount": 2,
  "planDay": 15,
  "dailyHistory": {
    "2026-05-01": "WORKOUT_DONE",
    "2026-05-02": "REST_DONE",
    "2026-05-03": "STRETCHING_DONE"
  }
}
```

---

## 5. ✅ Predlog implementacije: Stretching kot pogoj za streak na Rest dneve

### Logika v naslednjem koraku (Faza 4b)

```
Uporabnik klikne "Rest Day: Mobility & Stretching"
    ↓
BodyHomeIntent.CompleteRestDay
    ↓
BodyModuleHomeViewModel handler:
    1. ManageGamificationUseCase.restDayInitiated()
       → repository.updateStreak(isWorkoutSuccess = true)  ← ohrani streak
       → repository.awardXP(10, "REST_DAY")
    2. Zapiši dailyHistory["danes"] = "STRETCHING_DONE" ali "REST_DONE"
    3. _ui.value = _ui.value.copy(isWorkoutDoneToday = true)
    4. Prikaži mini animacijo / toast "+10 XP • Streak ohranjen 🔥"
```

### Kaj se NE bi smelo spremeniti
- Midnight worker logika ostane nespremenjena (fallback za tiste, ki ne kliknejo)
- `daily_logs` sub-kolekcija ostane kot sekundarni log
- Stretching NE poveča `streak_days` (ostane isti) — samo zapiše "opravlen rest dan"

### Datoteke, ki jih je treba spremeniti (Faza 4b)

| Datoteka | Sprememba |
|---|---|
| `viewmodels/BodyModuleHomeViewModel.kt` | Implementirati `CompleteRestDay` handler (klic `restDayInitiated()`) |
| `viewmodels/BodyModuleHomeViewModel.kt` | Po `restDayInitiated()` → `_ui.update { isWorkoutDoneToday = true }` |
| `data/gamification/FirestoreGamificationRepository.kt` | `updateStreak(isWorkoutSuccess=true)` že piše daily_log z "WORKOUT_DONE" — dodati ločeno stanje za REST_DONE / STRETCHING_DONE |

---

## 6. 🐛 Odkrite napake (za Faza 4b backlog)

| # | Datoteka | Problem | Resnost |
|---|---|---|---|
| 1 | `FirestoreGamificationRepository.kt:202` | `currentPlanDayNum = logsSnap.size + 1` — napačno štetje | 🔴 HIGH |
| 2 | `BodyModuleHomeViewModel.kt:84` | `CompleteRestDay` je STUB | 🔴 HIGH |
| 3 | `updateStreak()` | Piše "WORKOUT_DONE" tudi za REST dan | 🟡 MEDIUM |
| 4 | `UserProfileManager` vs `GamificationRepo` | Streak se posodablja po dveh poteh | 🟡 MEDIUM |
| 5 | `ManageGamificationUseCase.restDayInitiated()` | `isWorkoutSuccess=true` → streak se poveča (+1) na REST dan! Ni prav. | 🔴 HIGH |

> **Posebej bug #5:** `repository.updateStreak(isWorkoutSuccess = true)` v `restDayInitiated()`  
> bo povečal streak (ker `newStreak = if (isWorkoutSuccess) currentStreak + 1`).  
> Na rest dnevu bi moral ohraniti streak, ne povečati. Pravilni klic: `isWorkoutSuccess = false`.

---

## Povzetek za Fazo 4b

**Minimalen set sprememb za funkcionalen Stretching + Streak sistem:**

1. ✏️ `BodyModuleHomeViewModel.kt` — Implementiraj `CompleteRestDay` (klic `restDayInitiated` + UI update)
2. 🐛 `ManageGamificationUseCase.kt` — Popravi `restDayInitiated()`: `isWorkoutSuccess = false`
3. 🐛 `FirestoreGamificationRepository.kt` — Popravi `currentPlanDayNum` izračun (beri `plan_day` iz Firestore)
4. 🗺️ `FirestoreGamificationRepository.kt` — Dodaj `"REST_DONE"` status ločeno od `"WORKOUT_DONE"`

**Skupaj: ~4 majhne spremembe, 0 novih datotek.**

