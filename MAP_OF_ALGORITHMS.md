# Zemljevid Algoritmov: Deep Discovery Protocol (MAP_OF_ALGORITHMS)

Ta zemljevid predstavlja neizpodbitna dejstva, pridobljena z globinskim iskanjem po kodi (Grep). Prikazuje natančen pretok podatkov, mesta zapisovanja in branja ('Silent Writes') ter kritične točke nevarnosti ("Shared Truth" konflikte).

---

## 1. Data Flow Matrix (Firestore polja in algoritmi)

| Algoritem / Razred | `dailyLogs/burnedCalories` | `dailyLogs/waterMl` | `dailyLogs/consumedCalories` | `users/xp` | `users/login_streak` |
| :--- | :---: | :---: | :---: | :---: | :---: |
| ❗ **`UpdateBodyMetricsUseCase.kt`** | **W** (Silent Write) | | | | |
| **`SyncHealthConnectUseCase.kt`** | **R / W** (Silent Write) | | | | |
| ❗ **`DailySyncWorker.kt`** | **W** (Silent Write) | | | | |
| ❗ **`DailySyncManager.kt`** | **W** (Silent Write) | | | | |
| ❗ **`StreakReminderWorker.kt`** | **R** (SharedPrefs) | **R** (SharedPrefs) | **R** (SharedPrefs) | | **R** |
| ❗ **`FoodRepositoryImpl.kt`** | | | **R / W** (Transaction!) | | |
| ❗ **`NutritionViewModel.kt`** | **R** | **R** | **R** | | |
| **`WaterWidgetProvider.kt`** | | **W** (Silent Write) | | | |
| **`FirestoreGamificationRepository.kt`** | | | | **R / W** (Transaction!) | **R / W** (Trans.)|
| ❗ **`ShopViewModel.kt`** | | | | **R** | |
| ❗ **`QuickMealWidgetProvider.kt`** | | | **W** (Silent Write) | | |

---

## 2. Dependency Map: "Kdo opazuje in kdo vlada"

### `burnedCalories`
- 📡 **Opazuje:** `NutritionViewModel.kt` (bere neposredno iz Firestore - `doc.get("burnedCalories")`). `StreakReminderWorker.kt` (bere iz `SharedPrefs`).
- ✍️ **Spreminja (UseCases):** `SyncHealthConnectUseCase.kt` (računa delto iz HC), ❗`UpdateBodyMetricsUseCase.kt` (TotalKcal vaje).
- 🛠️ **Pišejo (Workers):** `DailySyncWorker.kt` in `DailySyncManager.kt` (počneta merge).

### `waterMl`
- 📡 **Opazuje:** `NutritionViewModel.kt` (bere iz dnevnega dokumenta), `StreakReminderWorker.kt` (uporablja lokalno referenco preden pošlje opomnik).
- ✍️ **Spreminja:** Sistem brez UseCase-a, direktno Widget.
- 🛠️ **Pišejo:** `WaterWidgetProvider.kt` (takojšen update v SharedPrefs + Silent Write v Firestore).

### `consumedCalories`
- 📡 **Opazuje:** `NutritionScreen.kt`, `DonutProgressView.kt` (za prikaz progressa obrokov).
- ✍️ **Spreminja:** ❗`FoodRepositoryImpl.kt` (dodajanje hrane uporabi TRANSAKCIJO na tem polju!).
- 🛠️ **Pišejo:** ❗`QuickMealWidgetProvider.kt` (dodajanje widget obroka brez transakcije - Silent Write).

### `xp` in `streak`
- 📡 **Opazuje:** `RunTrackerViewModel.kt`, `ProgressViewModel.kt`, `ShopViewModel.kt` (bere `xp_history`).
- ✍️ **Spreminja:** `FirestoreGamificationRepository.kt` in `ManageGamificationUseCase.kt`.
- 🛡️ **Status transakcij:** DOBER. V Gamification Repo se `xp` updejta znotraj `db.runTransaction { ... }`.

---

## 3. "Silent Write" Audit: Identifikacija bomb v kodi 💣
Z iskanjem smo identificirali vse "tihe zapise", kjer koda uporablja `update()`, `set(..., SetOptions.merge())` ali `FieldValue.increment()` IZVEN `db.runTransaction()`. To povzroča prepisovanje ob istočasnih klicih!

1. 💣~~→✅~~ **`UpdateBodyMetricsUseCase.kt`:** ~~`.set(mapOf("burnedCalories" to FieldValue.increment(totalKcal)), SetOptions.merge())`~~ → **ODPRAVLJENO**: Zdaj uporablja `DailyLogRepository.updateDailyLog { data["burnedCalories"] = currentBurned + totalKcal }`.

2. 💣~~→✅~~ **`SyncHealthConnectUseCase.kt`:** ~~Uporablja `FieldValue.increment(delta)` in `SetOptions.merge()`. SharedPreferences kot referenca.~~ → **ODPRAVLJENO**: Eliminiran SharedPrefs. `hcBurnedCalories` referenca v Firestore. Transakcija via `DailyLogRepository`. Podpora negativni delti.

3. 💣~~→✅~~ **`WaterWidgetProvider.kt`:** ~~`.set(data, SetOptions.merge())`~~ → **ODPRAVLJENO**: `DailyLogRepository().updateDailyLog { data["waterMl"] = newVal }` znotraj `CoroutineScope(Dispatchers.IO)`.

4. 💣~~→✅~~ **`QuickMealWidgetProvider.kt`:** ~~`.set(..., SetOptions.merge())`~~ → **ODPRAVLJENO**: `handleAddMeal()` prepisana na `CoroutineScope(Dispatchers.IO)` + `DailyLogRepository.updateDailyLog`. Items se dodajajo atomarno znotraj transakcije (branje obstoječih → append → pisanje). Eliminiran callback pekel.

5. 💣~~→✅~~ **`DailySyncManager.kt`:** ~~`.set(payload, SetOptions.merge())`~~ → **ODPRAVLJENO**: `syncTodayNow()` zdaj kliče `DailyLogRepository().updateDailyLog()` v `CoroutineScope(Dispatchers.IO)`.

6. 💣~~→✅~~ **`DailySyncWorker.kt`:** ~~`Firebase.firestore...set(payload, SetOptions.merge()).await()`~~ → **ODPRAVLJENO**: `doWork()` zdaj kliče `DailyLogRepository().updateDailyLog()` (suspend, direkten klic brez CoroutineScope).

7. 💣~~→✅~~ **`HealthStorage.kt` (`doStoreDailyData`):** ~~`docRef.set(updates, SetOptions.merge())`~~ → **ODPRAVLJENO**: Migrirano na `DailyLogRepository().updateDailyLog()`.

---

## ✅ FINALNO STANJE: dailyLogs Silent Write Audit

V celotnem projektu **NI NITI ENE** `.set(..., SetOptions.merge())` direktno na `dailyLogs` dokumentu izven transakcije.

Preostali `SetOptions.merge()` klici v kodi pišejo v **različne kolekcije** (ne `dailyLogs`):
- `DailySyncManager.kt:205` → `daily_health` kolekcija (steps prikaz za ProgressScreen)
- `UserProfileManager.kt` → korenski `users/` dokument (profil, darkMode)
- `MetricsRepositoryImpl.kt` → `weightLogs` in `dailyMetrics` kolekciji
- `FoodRepositoryImpl.logWater/logFood` → `dailyLogs` **znotraj `db.runTransaction { }`** ✅

---

## 4. Akcijski načrt (Prioritetni popravki)

Na podlagi teh dejstev, moramo nujno:
1. **Zdruziti zapise v `dailyLogs` (Burned, Water, Macros)** znotraj enega SAMEGA modula za dnevnike (ali pa vse "Silent write" spremeniti v `runTransaction {}`).
2. Ubiti `FieldValue.increment` iz `UpdateBodyMetricsUseCase` ter `SyncHealthConnectUseCase` in ravnati z matematično logiko znotraj Firestore transakcije (da dobimo lock nad dokumentom dokler zapis ni končan).
3. Prepisati `WaterWidgetProvider` ter `QuickMealWidgetProvider`, da za zapisovanje podata zahtevo bazi, in ne zaupata izkjučno lokalnemu `SharedPrefs`.

---

## 5. Audit: SharedPreferences (Lokalne Laži)
Seznam vseh odkritih lokalnih predpomnilnikov, ki ohranjajo stanje neodvisno od baze:
*   `"hc_sync_prefs"`: Bere/piše `SyncHealthConnectUseCase` (zadržuje kritično 'delto' Health Connecta).
*   `"water_widget_prefs"`: Bere/piše `WaterWidgetProvider` in UI.
*   `"weight_widget_prefs"`: Bere/piše `WeightWidgetProvider`.
*   `"streak_widget_prefs"`: Bere/piše `StreakWidgetProvider`.
*   `"plan_day_widget"`: Bere/piše `PlanDayWidgetProvider`.
*   `"DailyData_$date"`: Unikatno kreirane datoteke za vsak dan posebej v `DailySyncManager` in `HealthStorage`. (Memory Leak nevarnost!)
*   `"bm_prefs"`, `"water_cache"`, `"calories_cache"`, `"burned_cache"`: Tiste pobere `StreakReminderWorker.kt` (čeprav bi moral brati iz baze!).

## 6. Algoritemska veriga (Chaining)
Kako se dnevne metrike verižijo naprej po aplikaciji:
*   **`burnedCalories`** →  Vstopi skozi `UpdateBodyMetricsUseCase` ali `SyncHealthConnectUseCase` →  Preide v `ManageGamificationUseCase` in `recordWorkoutCompletion()` →  Se deli z 8 (`calorieXP = caloriesBurned / 8`) →  Zapiše se v `users/{uid}/xp`.
*   **`consumedCalories`** → Vstopi skozi `FoodRepositoryImpl` (ali `QuickMealWidgetProvider`) → Preide v `CalculateBodyMetricsUseCase` kalkulacije in `DonutProgressView` za določanje barve in limitov (rdeče/zeleno UI stanje). 
*   **Vse 3 (water, consumed, burned)** → Preidejo v `StreakReminderWorker.kt`, ki izvede logiko pogojev za izbiro "Push Notification" sporočila ob določeni uri.

## 7. Worker Inventory
Vsi background akterji in njihove pravice:
1.  **`DailySyncWorker.kt`**: Periodično občasno klican. Tiho posodablja (`SetOptions.merge()`) iz cache-a v `dailyLogs`. Ne preverja, če je novejše stanje že na serverju.
2.  **`WeeklyStreakWorker.kt`**: Poklican ob polnoči. Izvede `ManageGamificationUseCase.executeMidnightStreakCheck()`. Ta edini rešuje vse v varni *Firestore Transakciji*.
3.  **`StreakReminderWorker.kt`**: Dnevno sprožen (verjetno ob večernih urah). Pridobiva ne-verificirane podatke iz izoliranih `SharedPrefs` (`water_cache`, `consumed_cache`) za pošiljanje Push Notification-ov.

## 8. Tretjeokrilni "Silent Write" Akterji (Ostale najdbe izklicanih baznih vdorov)
Zadnjo GREP rešeto je pokazalo še druge razrede, kjer se tiho spreminja baza ali inkrementira polja:
*   ❗ **`FollowStore.kt`**: Uporablja `.update("followers", FieldValue.increment(1))` in `FieldValue.increment(-1)` brez transakcije. (Pri zelo popularnem uporabniku se bodo sledilci izgubili ob hkratnem kliku več oseb).
*   ❗ **`UserProfileManager.kt`**: Skupki `.set(..., SetOptions.merge())` klicev (npr. preklapljanje Dark Mode, urejanje profilnih preferenc).
*   ❗ **`HealthStorage.kt`**: `.set(updates, SetOptions.merge())` za prepis dnevnih ciljev, ki spet tekmuje s prejšnjimi `dailyLogs` akterji.
*   ❗ **`MetricsRepositoryImpl.kt`**: Uporablja `.set(..., SetOptions.merge())` na baznih metrikah.
