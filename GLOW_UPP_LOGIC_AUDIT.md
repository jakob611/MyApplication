# Glow Upp Logic Audit
> Generirano: 2026-04-28 | Verzija kode: Faza 18+

---

## 1. Streak Logic

| Polje | Vrednost |
|-------|---------|
| **Ime datoteke** | `data/settings/UserProfileManager.kt` |
| **Ime funkcije** | `updateUserProgressAfterWorkout(incrementPlanDay: Boolean)` |
| **Firestore polja** | `users/{uid}` → `streak_days`, `last_workout_epoch`, `streak_freezes` |
| **Format epoch** | `epochDays` (Int → Long via `date.toEpochDays().toLong()`) — **NE** ms! |

### Izračun (Atomarna Firestore transakcija)

```
dayDiff = todayEpoch - last_workout_epoch

PRIMER:
  oldLastEpoch == 0            → newStreak = 1  (prvi trening kadarkoli)
  dayDiff == 0                 → newStreak = oldStreak  (danes že treniral — ohrani)
  dayDiff == 1                 → newStreak = oldStreak + 1  (včeraj → podaljšaj)
  dayDiff > 1 AND freezes > 0  → newStreak = oldStreak (streak freeze porabljen, freezes -= 1)
  dayDiff > 1 AND freezes == 0 → newStreak = 1 (reset)
```

### Kdaj se sproži
- Po vsakem zaključenem treningu prek `UpdateBodyMetricsUseCase` (vrstica ~46)
- Extra treningi: `incrementPlanDay = false` → streak se posodobi, `plan_day` pa ne
- Login-only streak: ločen mehanizem prek `ManageGamificationUseCase.recordLoginOnly()` → piše `login_streak` (ločeno od `streak_days`)

### Sekundarni vir (Workers)
- `workers/WeeklyStreakWorker.kt` → `checkTodayIsRestFromFirestore(planDay)` preverja ob polnoči
- `workers/StreakReminderWorker.kt` → bere `streak_days` + `plan_day` iz Firestore za napoved notifikacij

---

## 2. XP Calculation

| Polje | Vrednost |
|-------|---------|
| **Primarna datoteka** | `domain/gamification/ManageGamificationUseCase.kt` |
| **Primarna funkcija** | `recordWorkoutCompletion(caloriesBurned, hour)` |
| **Sekundarna datoteka** | `ui/screens/RunTrackerScreen.kt` |
| **Sekundarna funkcija** | `calculateXP(activityType, distanceMeters, timeSeconds)` |
| **Shranjevanje** | `persistence/AchievementStore.awardXP()` → Firestore `users/{uid}.xp` + `xp_history` subcollection |

### XP Tabela

| Akcija | Formula | Min/Max | Vir |
|--------|---------|---------|-----|
| Workout Complete | `50 + (kcal / 10)` base; 10% šansa za kritični hit (×2 base) | min 50 | `ManageGamificationUseCase.recordWorkoutCompletion()` |
| Kalorije pri treningu | `caloriesBurned / 8` | 0+ | ista funkcija, isti fajl |
| Tek / Kolesarjenje / Hoja | `((distM/100) + (timeSec/60)) × multiplier` | min 10 | `RunTrackerScreen.calculateXP()` |
| ↳ RUN / SPRINT multiplier | ×1.5 | — | `RunTrackerScreen.kt` vrstica ~126 |
| ↳ HIKE / NORDIC multiplier | ×1.3 | — | ista datoteka |
| ↳ WALK multiplier | ×1.0 | — | ista datoteka |
| ↳ CYCLING multiplier | ×0.6 | — | ista datoteka |
| Dnevni login | +10 XP | flat | `ManageGamificationUseCase.recordLoginOnly()` |
| Ustvarjanje plana | +100 XP | flat | `ManageGamificationUseCase.recordPlanCreation()` |
| Vnos teže | +50 XP | flat | `ProgressViewModel.awardWeightLogXP()` |
| Rest day | +10 XP | flat | `ManageGamificationUseCase.restDayInitiated()` |

### Level Formula
- `UserProfile.calculateLevel(xp)` — izračunano znotraj Firestore transakcije skupaj z XP prek `AchievementStore.awardXP()`
- Rezultat: atomarno posodobita se oba polji `xp` IN `level` v `users/{uid}`

---

## 3. PlanPath (Trenutna Točka na Poti)

| Polje | Vrednost |
|-------|---------|
| **Primarna datoteka** | `ui/screens/NutritionScreen.kt` + `widget/PlanDayWidgetProvider.kt` |
| **Primarna funkcija** | Lambda `isWorkoutDayToday` v NutritionScreen (vrstica ~335) |
| **Widget funkcija** | `PlanDayWidgetProvider.updateAppWidget()` + `planDayToLabel()` |
| **Firestore polja** | `users/{uid}.plan_day`, `user_plans/{uid}.plans[].startDate`, `plans[].weeks[].days[].isRestDay` |

### Izračun Trenutnega Dne v Planu

```kotlin
// 1. startDate = ISO datum začetka plana (npr. "2026-03-07") iz PlanResult.startDate
val start = LocalDate.parse(plan.startDate)
val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
val daysSinceStart = (today.toEpochDays() - start.toEpochDays())  // absolutni diff

// 2. Najdi DayPlan za ta dan v planu (0-indeksirano po flatMap vseh dni)
val allDays = plan.weeks.flatMap { it.days }
val todayDayInPlan = allDays.getOrNull(daysSinceStart)

// 3. Preveri isRestDay
todayDayInPlan?.isRestDay  // true = počitek, false = trening
```

### Widget Logika (popovedi)
```kotlin
// PlanDayWidgetProvider: pretvori plan_day v "Week X Day Y"
fun planDayToLabel(planDay: Int, trainingDaysPerWeek: Int): String {
    val day = planDay.coerceAtLeast(1)
    val weekNum = ((day - 1) / trainingDaysPerWeek) + 1
    val dayInWeek = ((day - 1) % trainingDaysPerWeek) + 1
    return "Week $weekNum · Day $dayInWeek"
}
```

### Opomba: Dvojno Štetje
- `users/{uid}.plan_day` (Int) = Firestore SSOT, atomarno poveča `UpdateBodyMetricsUseCase`
- Absolutni `daysSinceStart` = alternativna kalkulacija za widget/NutritionScreen (ne zahteva Firestore)
- **Potencialni konflikt**: `plan_day` in `daysSinceStart` se ne ujemata, če je bil dan "preskočen" ali "swapan"

---

## 4. Workout/Rest Days

| Polje | Vrednost |
|-------|---------|
| **Primarna datoteka** | `data/PlanModels.kt` + `persistence/PlanDataStore.kt` |
| **Model** | `DayPlan.isRestDay: Boolean` |
| **UI datoteka** | `ui/screens/NutritionScreen.kt` |
| **UI funkcija** | `isWorkoutDayToday` (remember lambda, vrstica ~333) |
| **Worker datoteka** | `workers/StreakReminderWorker.kt` |
| **Worker funkcija** | `checkTodayIsRestFromFirestore(planDay)` |

### Kako Aplikacija Ve Kateri Dan Je

#### Metoda 1 — NutritionScreen (absolutni datum)
```kotlin
val daysSinceStart = (todayDate.toEpochDays() - startDate.toEpochDays())
val allDays = plan.weeks.flatMap { it.days }
val todayDayInPlan = allDays.getOrNull(daysSinceStart)
val isRestDay = todayDayInPlan?.isRestDay?.not() ?: true  // default: workout day
```
**Vir**: `plan.weeks[].days[].isRestDay` v lokalnem `PlanResult` objektu

#### Metoda 2 — StreakReminderWorker (Firestore)
```kotlin
// Bere plan_day iz Firestore users/{uid}
val planDay = workerStats?.get("plan_day") as? Int ?: 1
val todayIsRest = checkTodayIsRestFromFirestore(planDay)
// checkTodayIsRestFromFirestore: bere user_plans/{uid}.plans[0].weeks
// in najde DayPlan za planDay-ti dan
```

#### Metoda 3 — GetBodyMetricsUseCase
```kotlin
// Bere last_workout_epoch iz Firestore → izračuna isDoneToday
val isDoneToday = lastDate == now
// todayIsRest: pridobljeno iz user_plans Firestore kolekcije
```

### DayPlan Struktura
```kotlin
data class DayPlan(
    val dayNumber: Int,       // 1-based zaporedna številka
    val exercises: List<String> = emptyList(),
    val isRestDay: Boolean = false,  // true = počitek
    val focusLabel: String = "",     // "Legs", "Push", "Pull", "Rest"
    val isSwapped: Boolean = false,
)
```

---

## Povzetek — Arhitekturni SSOT

| Algoritem | SSOT Firestore Polje | Klic Pri Pisanju | Klic Pri Branju |
|-----------|---------------------|-----------------|----------------|
| Streak | `users/{uid}.streak_days` | `UserProfileManager.updateUserProgressAfterWorkout()` | `GetBodyMetricsUseCase` |
| Plan Day | `users/{uid}.plan_day` | `UpdateBodyMetricsUseCase` → `UserProfileManager` | `GetBodyMetricsUseCase` |
| XP | `users/{uid}.xp` | `AchievementStore.awardXP()` | `ObserveUserProfileUseCase` |
| Level | `users/{uid}.level` | `AchievementStore.awardXP()` (atomarno z XP) | `ObserveUserProfileUseCase` |
| Workout Day | `user_plans/{uid}.plans[].weeks[].days[].isRestDay` | `PlanDataStore.migrateLocalPlansToFirestore()` | `PlanDataStore` / `StreakReminderWorker` |

---

*Zadnja posodobitev: 2026-04-28 — Faza 18+ (InitialSyncManager v AppViewModel, GPS subcollection, Custom Meal Flow)*

