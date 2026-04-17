# REFACTORING ROADMAP
**Datum nastanka:** 2026-03-09  
**Namen:** Razdeliti prevelike datoteke na majhne, odpraviti podvojeno logiko in vzpostaviti jasno arhitekturo.

## ZAKAJ JE TO POTREBNO

AI agent ima omejeno "delovno pomnilnico" (context window). Ko datoteka presega ~600 vrstic, agent:
1. Prebere začetek datoteke
2. Začne pisati spremembo
3. Med pisanjem odgovora "pozabi" kontekst iz začetka datoteke
4. Napiše kodo ki je sintaktično pravilna, a logično napačna ali nepovezana

**Rešitev:** Datoteke ki so dovolj majhne da jih agent vidi VSE NAENKRAT.

---

## PRAVILA DELA

- **Nikoli ne začni Koraka N+1 preden ni Korak N označen ✅ DONE**
- **Nikoli ne spreminjaš logike med refaktoriranjem** — samo preselimo, ne spremenimo
- **Vsak korak mora imeti uspešen build** (`./gradlew assembleDebug`) preden nadaljujemo
- **En korak = ena seja pogovora z AI**

---

## HITRI STATUS

| # | Korak | Datoteke | Tveganje | Status |
|---|-------|----------|----------|--------|
| 1 | Premik data modelov v `data/` paket | `AlgorithmData.kt`, `plan_result.kt` | 🟢 Nizko | ✅ DONE 2026-03-09 |
| 2 | Izluščitev domenke logike | `BodyModule.kt` → `domain/WorkoutPlanGenerator.kt` | 🟡 Srednje | ✅ DONE 2026-03-09 |
| 3a | Izluščitev ViewModela | `BodyModuleHomeScreen.kt` → `viewmodels/BodyModuleHomeViewModel.kt` | 🟡 Srednje | ✅ DONE 2026-03-09 |
| 3b | Izluščitev PlanPathVisualizer | `BodyModuleHomeScreen.kt` → `ui/screens/PlanPathVisualizer.kt` | 🟡 Srednje | ✅ DONE 2026-03-09 |
| 3c | Izluščitev PlanPathDialog | `BodyModuleHomeScreen.kt` → `ui/screens/PlanPathDialog.kt` | 🟡 Srednje | ✅ DONE 2026-03-10 |
| 3d | Izluščitev KnowledgeHub | `BodyModuleHomeScreen.kt` → `ui/screens/KnowledgeHubScreen.kt` | 🟢 Nizko | ✅ DONE 2026-03-10 |
| 4 | Poenotitev podvajanih ključev | `UserPreferences.kt` | 🔴 Visoko | ✅ DONE 2026-03-10 |
| 5a | AppViewModel za userProfile | `MainActivity.kt` → `AppViewModel.kt` | 🔴 Visoko | ✅ DONE 2026-03-10 |
| 5b | NavigationViewModel | `MainActivity.kt` → `NavigationViewModel.kt` | 🔴 Visoko | ✅ DONE 2026-03-10 |

---

## KORAK 1 — Premik data modelov

**Tveganje:** 🟢 Nizko — samo premik + update importov, nič logike se ne spremeni  
**Predpogoji:** Nobenih  
**Pričakovani čas:** ~30 min  

### Problem
`PlanResult`, `WeekPlan`, `DayPlan`, `AlgorithmData` so v paketu `ui.screens`.  
Datoteke iz `persistence/` in `data/` morajo importati iz UI paketa — to je **obratna odvisnost** (dependency inversion violation).  
Vsaka sprememba data modela zahteva odpiranje UI datotek.

### Korak 1a — `AlgorithmData` v `data/` paket

**Originalna datoteka:** `ui/screens/AlgorithmData.kt` (13 vrstic)  
**Nova datoteka:** `data/AlgorithmData.kt`  
**Edina sprememba:** `package com.example.myapplication.ui.screens` → `package com.example.myapplication.data`

Datoteke kjer je treba posodobiti import (`...ui.screens.AlgorithmData` → `...data.AlgorithmData`):

| Datoteka | Vrstica | Stari import |
|----------|---------|--------------|
| `persistence/PlanDataStore.kt` | 9 | `import com.example.myapplication.ui.screens.AlgorithmData` |
| `persistence/NutritionPlanStore.kt` | 5 | `import com.example.myapplication.ui.screens.AlgorithmData` |
| `data/NutritionPlan.kt` | 3 | `import com.example.myapplication.ui.screens.AlgorithmData` |
| `ui/screens/BodyModule.kt` | nov import | doda: `import com.example.myapplication.data.AlgorithmData` |
| `ui/screens/BodyModuleHomeScreen.kt` | nov import | doda: `import com.example.myapplication.data.AlgorithmData` |

Po posodobitvi importov: **izbriši** `ui/screens/AlgorithmData.kt`.

### Korak 1b — `PlanResult`, `WeekPlan`, `DayPlan` v `data/` paket

**Originalna datoteka:** `ui/screens/plan_result.kt` (36 vrstic)  
**Nova datoteka:** `data/PlanModels.kt`  
**Edina sprememba:** package deklaracija

Datoteke kjer je treba posodobiti import:

| Datoteka | Stari import (ui.screens.*) | Nov import (data.*) |
|----------|----------------------------|---------------------|
| `persistence/PlanDataStore.kt` | vrstice 10-12 | `data.PlanResult`, `data.WeekPlan`, `data.DayPlan` |
| `network/ai_utils.kt` | vrstice 4-6 | enako |
| `ui/screens/BodyModule.kt` | isti paket (ni importa) | doda 3 importe |
| `ui/screens/BodyModuleHomeScreen.kt` | isti paket | doda 3 importe |
| `ui/screens/LevelPathScreen.kt` | isti paket | doda 3 importe |
| `ui/screens/WorkoutSessionScreen.kt` | isti paket | doda 3 importe |
| `ui/screens/AchievementsScreen.kt` | isti paket | doda 3 importe |
| `domain/WorkoutGenerator.kt` | isti paket | doda 3 importe |
| `MainActivity.kt` | `ui.screens.PlanResult` | `data.PlanResult` |

Po posodobitvi importov: **izbriši** `ui/screens/plan_result.kt`.

### Done kriterij Korak 1
- [ ] `data/AlgorithmData.kt` obstaja z `package com.example.myapplication.data`
- [ ] `data/PlanModels.kt` obstaja z `package com.example.myapplication.data`
- [ ] `ui/screens/AlgorithmData.kt` NE OBSTAJA VEČ
- [ ] `ui/screens/plan_result.kt` NE OBSTAJA VEČ
- [ ] `./gradlew assembleDebug` uspešen brez napak

---

## KORAK 2 — Izluščitev domenke logike iz `BodyModule.kt`

**Tveganje:** 🟡 Srednje  
**Predpogoji:** Korak 1 ✅  
**Cilj:** `BodyModule.kt` 1589 → ~620 vrstic. Nova `domain/WorkoutPlanGenerator.kt` ~700 vrstic.

### Problem
`BodyModule.kt` (1589 vrstic) meša:
- Quiz UI (Composable) — spada sem
- Čisto domensko logiko (pure functions, nič UI) — **NE spada sem**

Ko je napaka v `generatePlanWeeks()`, moraš prebrati 1589 vrstic da jo najdeš.

### Kaj premakni iz `BodyModule.kt` v `domain/WorkoutPlanGenerator.kt`

Vse naslednje funkcije so **čiste** (brez Composable, brez Context, brez Android API):

| Funkcija | Začetna vrstica | Končna vrstica | Vrstic |
|----------|----------------|----------------|--------|
| `generatePlanWeeks(...)` | ~971 | ~1028 | ~58 |
| `generateAdvancedCustomPlan(...)` | ~1030 | ~1100 | ~71 |
| `determineOptimalTrainingDays(...)` | ~1103 | ~1120 | ~18 |
| `generateIntelligentTrainingPlan(...)` | ~1122 | ~1243 | ~122 |
| `calculateOptimalSessionLength(...)` | ~1244 | ~1297 | ~54 |
| `generatePersonalizedTips(...)` | ~1299 | ~1580 | ~282 |

**Nova datoteka** `domain/WorkoutPlanGenerator.kt`:
```
package com.example.myapplication.domain

import com.example.myapplication.data.PlanResult
import com.example.myapplication.data.WeekPlan
import com.example.myapplication.data.DayPlan
import com.example.myapplication.data.AlgorithmData
import com.example.myapplication.utils.calculateAdvancedBMR
import com.example.myapplication.utils.calculateEnhancedTDEE
import com.example.myapplication.utils.calculateSmartCalories
import com.example.myapplication.utils.calculateOptimalMacros
```

**Posodobi `BodyModule.kt`:**
- Doda import: `import com.example.myapplication.domain.*`
- Odstrani premeščene funkcije (~vrstice 971–1589)

### Done kriterij Korak 2
- [ ] `domain/WorkoutPlanGenerator.kt` obstaja
- [ ] `BodyModule.kt` je krajši za ~600 vrstic (zgolj UI koda ostane)
- [ ] `./gradlew assembleDebug` uspešen
- [ ] Funkcionalni test: ustvari plan 5x/teden, preveri 5 workout + 2 rest dni

---

## KORAK 3a — Izluščitev BodyModuleHomeViewModel

**Tveganje:** 🟡 Srednje  
**Predpogoji:** Koraka 1 in 2 ✅  
**Cilj:** `BodyModuleHomeScreen.kt` 1962 → ~1500 vrstic. Nova `viewmodels/BodyModuleHomeViewModel.kt` ~480 vrstic.

### Kaj premakni

Razred `BodyModuleHomeViewModel` (vrstice ~80–480 v BodyModuleHomeScreen.kt):
- `BodyHomeUiState` data class
- `Challenge` data class  
- `BodyModuleHomeViewModel : AndroidViewModel` z vsemi metodami:
  - `refreshStats()`
  - `refreshFromPrefs()`
  - `completeWorkoutSession(...)`
  - `tryGetNextDayIsRest(...)`
  - `updateStreak()`
  - `swapDaysInPlan(...)`
  - `acceptChallenge(...)`
  - `calculateWeeklyWorkoutsFromFirestore(...)`

**Nova datoteka** `viewmodels/BodyModuleHomeViewModel.kt`:
```
package com.example.myapplication.viewmodels
```

**Posodobi `BodyModuleHomeScreen.kt`:**
- Doda import: `import com.example.myapplication.viewmodels.BodyModuleHomeViewModel`
- Doda import: `import com.example.myapplication.viewmodels.BodyHomeUiState`
- Odstrani premeščene razrede

### Done kriterij Korak 3a
- [ ] `viewmodels/BodyModuleHomeViewModel.kt` obstaja
- [ ] `BodyModuleHomeScreen.kt` je krajši za ~480 vrstic
- [ ] `./gradlew assembleDebug` uspešen

---

## KORAK 3b — Izluščitev PlanPathVisualizer

**Tveganje:** 🟡 Srednje  
**Predpogoji:** Korak 3a ✅  
**Cilj:** `BodyModuleHomeScreen.kt` → ~1100 vrstic. Nova `ui/screens/PlanPathVisualizer.kt` ~400 vrstic.

### Kaj premakni

`PlanPathVisualizer` Composable (vrstice ~1530–1880 v BodyModuleHomeScreen.kt):
- Funkcija `PlanPathVisualizer(...)` z vsemi parametri
- Drag & drop logika (lokalna, znotraj composable)
- `EpicCounter` Composable

Obe funkciji sta **neodvisni od ViewModela** — prejmeta podatke samo prek parametrov.

**Nova datoteka** `ui/screens/PlanPathVisualizer.kt`:
```
package com.example.myapplication.ui.screens
```
(isti paket → import ni potreben v BodyModuleHomeScreen.kt)

### Done kriterij Korak 3b
- [ ] `ui/screens/PlanPathVisualizer.kt` obstaja z `PlanPathVisualizer` in `EpicCounter`
- [ ] `BodyModuleHomeScreen.kt` je krajši za ~400 vrstic
- [ ] `./gradlew assembleDebug` uspešen
- [ ] Plan path vizualizator se prikaže pravilno

---

## KORAK 3c — Izluščitev PlanPathDialog

**Tveganje:** 🟡 Srednje  
**Predpogoji:** Korak 3b ✅  
**Cilj:** Nova `ui/screens/PlanPathDialog.kt` ~350 vrstic.

### Kaj premakni

Iz `BodyModuleHomeScreen.kt`:
- `PlanPathDialog(...)` Composable (~vrstice 1106–1390)
- `DayInfoDialog(...)` Composable (~vrstice 1392–1540)
- `DayInfoRow(...)` Composable (~vrstice 1542–1570)

**Nova datoteka** `ui/screens/PlanPathDialog.kt`:
```
package com.example.myapplication.ui.screens
```

### Done kriterij Korak 3c
- [ ] `ui/screens/PlanPathDialog.kt` obstaja
- [ ] `BodyModuleHomeScreen.kt` je krajši za ~350 vrstic
- [ ] `./gradlew assembleDebug` uspešen
- [ ] Plan path dialog se odpre, day info dialog deluje

---

## KORAK 3d — Izluščitev KnowledgeHub

**Tveganje:** 🟢 Nizko  
**Predpogoji:** Korak 3c ✅  
**Cilj:** Nova `ui/screens/KnowledgeHubScreen.kt` ~280 vrstic. `BodyModuleHomeScreen.kt` → ~300 vrstic.

### Kaj premakni

Iz `BodyModuleHomeScreen.kt`:
- `KnowledgeHubFullScreen(...)` Composable
- `KnowledgeCard(...)` Composable
- `KnowledgeItem` data class
- `KNOWLEDGE_ITEMS` statični seznam (vse vrednosti)

**Nova datoteka** `ui/screens/KnowledgeHubScreen.kt`:
```
package com.example.myapplication.ui.screens
```

### Done kriterij Korak 3d
- [ ] `ui/screens/KnowledgeHubScreen.kt` obstaja
- [ ] `BodyModuleHomeScreen.kt` je ~300 vrstic
- [ ] `./gradlew assembleDebug` uspešen
- [ ] Knowledge hub se odpre in prikaže seznam

---

## KORAK 4 — Poenotitev podvajanih ključev

**Tveganje:** 🔴 Visoko — napaka = napačni podatki ali izguba podatkov  
**Predpogoji:** Koraki 1–3d ✅  
**Cilj:** Vsak podatek se hrani in bere z enim samim ključem.

### Problem — race conditioni

`saveProfileFirestore()` in `saveWorkoutStats()` oba pišeta v Firestore `users/{email}` z **različnimi ključi za iste podatke**. Ko ena funkcija zapiše `login_streak=5`, druga prepiše z `streak_days=5` — ali obratno. Ob branju se vzame `maxOf(a, b)` kar deluje, ampak je krhko.

### Podvojeni ključi

| Podatek | Kanonični ključ | Odvečni ključ | Kje se piše odvečni |
|---------|----------------|---------------|---------------------|
| Streak | `streak_days` | `login_streak` | `saveProfileFirestore` vrstica ~237, `saveWorkoutStats` vrstica ~415 |
| Total workouts | `total_workouts_completed` | `total_workouts` | `saveProfileFirestore` vrstica ~235 |
| Weekly target | Firestore `weekly_target` | SharedPrefs `weekly_target` (6 mest) | MainActivity vrstice 196, 294, 666; BodyModuleHomeScreen vrstice 163 |

### Kaj točno narediti

**`UserPreferences.saveProfileFirestore()` (~vrstica 237):**
- Odstrani vrstico: `KEY_LOGIN_STREAK to profile.currentLoginStreak` (alias)
- Odstrani vrstico: `KEY_TOTAL_WORKOUTS to profile.totalWorkoutsCompleted` (alias, ohrani samo `"total_workouts_completed"`)
- Ohrani: `"streak_days" to profile.currentLoginStreak`
- Ohrani: `"total_workouts_completed" to profile.totalWorkoutsCompleted`

**`UserPreferences.saveWorkoutStats()` (~vrstica 415):**
- Odstrani vrstico: `"login_streak" to streak`
- Ohrani: `"streak_days" to streak`
- Odstrani vrstico: `"total_workouts" to totalWorkouts`
- Ohrani: `"total_workouts_completed" to totalWorkouts`

**`UserPreferences.loadProfileFromFirestore()` (~vrstica 340):**
- Zamenjaj: `maxOf(total_workouts, total_workouts_completed)` → samo `total_workouts_completed`
- Zamenjaj: `maxOf(login_streak, streak_days)` → samo `streak_days`

**`MainActivity.kt` real-time listener (~vrstica 265):**
- Zamenjaj: `snap.get("login_streak")` → `snap.get("streak_days")`
- Zamenjaj: `snap.get("total_workouts")` → `snap.get("total_workouts_completed")`

**`weekly_target` poenotitev:**
Nastavi se samo ob zaključku kviza (`onFinish` v MainActivity ~vrstica 718).  
Odstranim redundantna nastavljanja v:
- `MainActivity.kt` vrstica 196 (ob auto-loginu) — zamenjaj s samo branjem, ne pisanjem
- `MainActivity.kt` vrstica 294 (v real-time listenerju) — odstrani pisanje v SharedPrefs

### Done kriterij Korak 4
- [x] `saveWorkoutStats` piše samo `streak_days` (ne `login_streak`)
- [x] `saveWorkoutStats` piše samo `total_workouts_completed` (ne `total_workouts`)
- [x] `loadProfileFromFirestore` bere samo kanonične ključe — izluščeno v `documentToUserProfile()`
- [x] Neuporabljeni konstanti `KEY_LOGIN_STREAK` in `KEY_TOTAL_WORKOUTS` odstranjeni
- [x] `./gradlew assembleDebug` uspešen

---

## KORAK 5a — AppViewModel za userProfile

**Tveganje:** 🔴 Visoko — napaka = prazni profil ali crash  
**Predpogoji:** Koraki 1–4 ✅  
**Cilj:** `MainActivity.kt` 912 → ~700 vrstic. Nova `AppViewModel.kt` ~120 vrstic.

### Problem
Real-time Firestore listener v `MainActivity` (~vrstice 230–295) ročno gradi `UserProfile` z 40+ polji.  
To je **duplikat** `UserPreferences.loadProfileFromFirestore()`.  
Posledica: ko dodamo novo polje v `UserProfile`, ga moramo dodati na **dveh** mestih:
1. V `loadProfileFromFirestore()`
2. V Firestore listenerju v `MainActivity`

Prav to je bil vzrok za napake kjer je lastnost obstajala v profilu ampak se ni prikazovala.

### Rešitev

```kotlin
// AppViewModel.kt
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile

    fun startListening(email: String) {
        viewModelScope.launch {
            val userRef = FirestoreHelper.getCurrentUserDocRef()
            // Listener kliče loadProfileFromFirestore() — NE gradi ročno
            userRef.addSnapshotListener { _, _ ->
                viewModelScope.launch {
                    val fresh = UserPreferences.loadProfileFromFirestore(email)
                    if (fresh != null) _userProfile.value = fresh
                }
            }
        }
    }
}
```

**Posodobi `MainActivity.kt`:**
- Zamenjaj celoten ročni listener (~65 vrstic) z: `appViewModel.startListening(userEmail)`
- `var userProfile` → `val userProfile by appViewModel.userProfile.collectAsState()`

### Done kriterij Korak 5a
- [x] `AppViewModel.kt` obstaja (54 vrstic)
- [x] Ročni Firestore listener v `MainActivity` je ODSTRANJEN (~75 vrstic)
- [x] `AppViewModel.startListening()` kliče `documentToUserProfile()` direktno — brez dvojnega branja
- [x] `./gradlew assembleDebug` uspešen

---

## KORAK 5b — NavigationViewModel

**Tveganje:** 🟡 Srednje  
**Predpogoji:** Korak 5a ✅  
**Cilj:** `MainActivity.kt` ~700 → ~400 vrstic. Nova `NavigationViewModel.kt` ~80 vrstic.

### Problem
Navigacijski stack (`navigationStack`, `currentScreen`, `previousScreen`, `navigateTo()`, `navigateBack()`) je Compose `remember` state v `MainActivity`.  
Problem: pri screen rotation se stanje izgubi (Compose `remember` ne preživi configuration change).

### Rešitev

```kotlin
// NavigationViewModel.kt
class NavigationViewModel : ViewModel() {
    private val _stack = MutableStateFlow<List<Screen>>(listOf(Screen.Index))
    val currentScreen: StateFlow<Screen> = ...
    
    fun navigateTo(screen: Screen) { ... }
    fun navigateBack(isLoggedIn: Boolean) { ... }
}
```

### Done kriterij Korak 5b
- [x] `NavigationViewModel.kt` obstaja (97 vrstic)
- [x] Lokalni `navigateTo()` in `navigateBack()` delegirata na `navViewModel`
- [x] `currentScreen` in `previousScreen` sta `collectAsState()` iz `navViewModel`
- [x] Vsi `currentScreen = X` zamenjani z `navViewModel.navigateTo(X)`
- [x] `./gradlew assembleDebug` uspešen

---

## ZNANE NAPAKE (neodvisno od refaktoriranja)

Te napake so bile odkrite med analizo. Jih popravimo **kadar koli** — ne čakamo na refaktoriranje.

| # | Napaka | Datoteka | Vrstica | Status |
|---|--------|----------|---------|--------|
| 1 | `determineOptimalTrainingDays` skrajšuje frekvenco (5x Beginner → 4) | `BodyModule.kt` | ~1103 | ✅ POPRAVLJENO 2026-03-09 |
| 2 | Real-time Firestore listener ročno gradi `UserProfile` (duplikat `loadProfileFromFirestore`) | `MainActivity.kt` | ~230 | ✅ POPRAVLJENO 2026-03-10 (Korak 5a) |
| 3 | `AppViewModel.startListening()` dvojno bral Firestore (snapshot + eksplicitni fetch) | `AppViewModel.kt` | ~44 | ✅ POPRAVLJENO 2026-03-10 |
| 4 | `KEY_TOTAL_WORKOUTS` alias pisan v Firestore poleg kanoničnega | `UserPreferences.kt` | ~237 | ✅ POPRAVLJENO 2026-03-10 |
| 5 | `KEY_LOGIN_STREAK` in `KEY_TOTAL_WORKOUTS` neuporabljeni konstanti | `UserPreferences.kt` | ~34 | ✅ POPRAVLJENO 2026-03-10 |
| 6 | `loadProfileFromFirestore` vsebovala 70+ vrstic mapiranja (duplikat) | `UserPreferences.kt` | ~272 | ✅ POPRAVLJENO — izluščeno v `documentToUserProfile()` |
| 7 | `KEY_LAST_LOGIN` konstanta podvojena (v `UserPreferences` in `AchievementStore`) | `AchievementStore.kt` | ~379 | ✅ POPRAVLJENO 2026-03-10 |
| 8 | Dvojni `checkAndUnlockBadges` klic po `recordWorkoutCompletion` | `WorkoutSessionScreen.kt` | ~429 | ✅ POPRAVLJENO 2026-03-10 — `recordWorkoutCompletion` sedaj vrne `List<Badge>` |
| 3 | `today_is_rest` flag se nastavi asinhrono — UI se naloži preden je vrednost znana | `BodyModuleHomeScreen.kt` | ~199 | ⬜ TODO |
| 4 | `weekly_target` se nastavlja na 6+ mestih, brez garantiranega vrstnega reda | `MainActivity.kt` | 196, 294, 666 | ⬜ TODO (Korak 4) |

---

## STANJE DATOTEK

| Datoteka | Vrstic danes | Cilj po refakt. | Korak |
|----------|-------------|-----------------|-------|
| `ui/screens/BodyModuleHomeScreen.kt` | 1962 | ~300 | 3a–3d |
| `ui/screens/BodyModule.kt` | 1589 | ~620 | 2 |
| `MainActivity.kt` | 912 | ~400 | 5a–5b |
| `data/UserPreferences.kt` | 552 | ~480 | 4 |
| `persistence/PlanDataStore.kt` | 511 | ~460 | 1 (import update) |
| `ui/screens/AlgorithmData.kt` | 13 | briši | 1a |
| `ui/screens/plan_result.kt` | 36 | briši | 1b |
| `data/AlgorithmData.kt` | — | ~15 | 1a (nova) |
| `data/PlanModels.kt` | — | ~40 | 1b (nova) |
| `domain/WorkoutPlanGenerator.kt` | — | ~700 | 2 (nova) |
| `viewmodels/BodyModuleHomeViewModel.kt` | — | ~480 | 3a (nova) |
| `ui/screens/PlanPathVisualizer.kt` | — | ~400 | 3b (nova) |
| `ui/screens/PlanPathDialog.kt` | — | ~350 | 3c (nova) |
| `ui/screens/KnowledgeHubScreen.kt` | — | ~280 | 3d (nova) |
| `AppViewModel.kt` | — | ~120 | 5a (nova) |
| `NavigationViewModel.kt` | — | ~80 | 5b (nova) |

---

## KAKO NAROČITI IMPLEMENTACIJO

Ko hočeš da AI implementira korak, reci točno:  
**"Implementiraj Korak 1"** ali **"Implementiraj Korak 3b"**

AI bo prebral ta dokument (ne 1900-vrstične datoteke!) in točno vedel kaj narediti.




