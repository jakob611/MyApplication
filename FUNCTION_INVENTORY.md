# Function Inventory & Audit Log
To je dnevnik vseh ključnih domenskih, utility in repozitorijskih funkcij ob pregledu celotne aplikacije. Tukaj bomo zapisovali funkcije, in iskali podvajanja ("duplication checks").

**Namen:** Takojšnja identifikacija podvojenih metod, prepletanja odgovornosti (UI/Domain/Data) in iskanje "orphan" (neuporabljene kode).

---

## 💾 1. Data Layer (Persistence / Repositories)

### 1.1 `AchievementStore.kt`
*Skrbi za točke, level in streaks. Hitro postane ogromen.*
- `awardXPInternal()` / `awardXP()` -> Shranjevanje XP-ja.
- `getBadgeProgress(badgeId: String, profile: UserProfile)` -> **Domain logika v data sloju!** Izraža poslovno odločitev "kako daleč do badge-a", morala bi biti v UseCase.
- `checkAndUnlockBadges()` / `checkAndSyncBadgesOnStartup()` / `unlockBadge()` -> Povezano z UI in podatki.
- `recordWorkoutCompletion()` / `recordPlanCreation()` / `recordLoginOnly()` -> **Firestore writes**. Zanimivo je, da funkcije ob vsaki spremembi kličejo *novo shranjevanje*, brez batchanja.
- `checkAndUpdatePlanStreak()` -> **Streaks** domenska logika se nahaja tukaj. Opomba: Ali ne upravlja streaka tudi `WeeklyStreakWorker.kt`? **Potencialno podvajanje/težava pri sinhronizaciji!**

### 1.2 `DailySyncManager.kt`
*Lokalno predpomnjenje in sinhronizacija za prehrano / vodo / kalorije.*
- `saveFoodsLocally`, `saveWaterLocally`, `saveBurnedLocally`, `saveCaloriesLocally` -> SharedPreferences lokalno shranjevanje.
- `syncOnAppOpen()`, `syncTodayNow()` -> Sinhronizacija s Firestore. 
- *Opomba:* Ta razred uporablja `context.getSharedPreferences("DailyData_$date")`, kar ustvari *novo datoteko vsak dan*. To ni optimizirano in hitro napolni storage z majhnimi datotekami.

### 1.3 `FirestoreHelper.kt`
- `getCurrentUserDocId()` / `getCurrentUserDocRef()` -> Glavna resolverja za pravilnega Firebase uporabnika (Email vs UID). To je dobro zasnovano, vendar ga preveč failov direktno kliče.
- `migrateSubcollection()` -> Migracija starih uid->email struktur.

(Več Firestore klicev bom popisal postopoma)

---

## 🧠 2. Domain & Utility Layer (Algorithms)

### 2.1 `domain/WorkoutPlanGenerator.kt`
*Algoritem za razporejanje vaj po dneh v tednu in po zahtevnosti.*
- `generateAdvancedCustomPlan()` -> Generira 4-tedenski plan; vključuje "God Object" obnašanje (združuje JSON branje, kalkulacijo setov/reps, equipment match in AI integracijo na enem mestu).
- To je eden ključnih kandidatov za prenos v posamične "Use-Case" razrede, da se izognemo ogromnim nepreglednim funkcijam in da testiramo logiko.

### 2.2 `utils/NutritionCalculations.kt`
*Temeljna domenska logika glede metabolizma in prehrane.*
- `calculateAdvancedBMR()`, `calculateEnhancedTDEE()`
- `calculateMacros()` -> KMP kandidat. Ta datoteka je že ločena od Android Contexta, kar je dobro. A ker se uporablja nepovezano iz `BodyOverviewViewmodel` in `NutritionScreen`, jo velja vgraditi v `feature_nutrition` repo ali UseCase.

### 2.3 Algoritmi iz UI datotek (Podvajanje in neustrezno mesto!)
- `ManualExerciseLogScreen.kt` -> `ExerciseRepository.getFilteredForUser()` in `personalizedScore()`. Istovrstna logika tisti v `domain/WorkoutPlanGenerator.kt`. **Podvojena logika za branje `exercises.json` in filtriranje glede na equipment!** Te funkcionalnosti je potrebno združiti v `ExerciseUseCase`.
- `Progress.kt` -> Računanje BMI in povprečne teže. BMI se ročno računa tudi v `BodyOverviewViewmodel.kt`. **Podvojena implementacija osnovnih enačb (`kg / (m*m)`).**

---

## 🌎 3. Network & External APIs

### 3.1 `network/fatsecret_api_service.kt` in `network/OpenFoodFactsAPI.kt`
- Funkcije za preverjanje hrane se direktno kličejo iz `AddFoodSheet.kt` in `BarcodeScannerScreen.kt`. Ne obstaja zanesljiv "FoodRepository". Če FatSecret API odpove, aplikacija ročno pade nazaj (fallback) v samem Compose UI bloku, kar močno onesnaži UI kodo.
- `network/ai_utils.kt` -> Vsebuje `requestAIPlan()`, kar pa se morda podvaja s plan generacijo v `PlanDataStore.kt`.

---

## 🏃 4. Run/GPS Modul

### 4.1 `service/RunTrackingService.kt` vs `RunTrackerViewModel.kt`
- Servis, ki teče v ozadju, samostojno beleži točke, ViewModel pa bere zgodovino in upravlja UI. `RunRouteStore.kt` zgolj shranjuje v `SharedPreferences`.
- `RouteCompressor.kt` (RDP algoritem) komprimira GPS točke šele ob "shrani".

---

## 🏗 Kratke Ugotovitve iz Pregleda:
1. **SharedPreferences Kaos:** Nastavitve se shranjujejo na vsaj štirih različnih mestih: `UserPreferences.kt`, `DailySyncManager.kt`, `HealthStorage.kt`, in posameznih Activity-jih.
2. **Nezdruženi Use Casing:** Algoritmi kot je BMI in filtriranje vaj po `Equipment` obstajajo na najmanj dveh ali treh mestih (enkrat za plan, enkrat za posamično vajo, enkrat v UI `BodyModule`).
3. **Firestore writes iz UI-ja:** `ManualExerciseLogScreen`, `BodyModuleHomeViewModel` in `NutritionScreen` izvajajo `Firestore.collection().add()` neposredno. Temu manjkajo transakcije ali `batch`.

## ⚙️ 5. Background Workers & Services

### 5.1 `workers/WeeklyStreakWorker.kt`
- Izvajanje vsako polnoč. Prebere "yesterday_was_rest" zastavico in povečuje streak v primeru opravljenega/neopravljenega včerajšnjega dne.
- **Konflikt s `BodyModuleHomeViewModel` in `AchievementStore`!** `BodyModuleHomeViewModel` tudi prikliče `checkAndUpdatePlanStreak` neposredno v `CompleteWorkoutSession()`, v `markRestDay()` in celo v update rutini. Dvojno zapisovanje povzroča napačna stanja in resetiranja streaka.
- Poleg tega bere iz `SharedPreferences("bm_prefs")`, ki pa morda niso sinhronizirane s tem, kar misli UI/algoritem v Firebase.

### 5.2 `workers/StreakReminderWorker.kt`
- Bere `Goal` iz plana in motivira uporabnika ob 20:00 (v primeru, da workout ni narejen). Še ena komponenta, ki re-implementira branje istih vrst podatkov, kot so UI viewmodeli.

### 5.3 `workers/DailySyncWorker.kt`
- Enako kot `DailySyncManager` žari po unikatnem `SharedPreferences(DailyData_$date)` za shranjevanje. Dlje ko bo aplikacija živela, več datotek bo kreiranih v ozadju. To je izjemno potratno za Android naprave.

## 🐛 6. Logične napake in ugotovitve iz globoke analize
1. **The Streak "Heisenbug":** Zakaj sta on dveh napravah na istem računu 0 in 2 streaka? Zato ker `BodyModuleHomeViewModel` piše lokalno (SharedPrefs) pred izvedbo Firestore Batcha (`// Let's rely on AchievementStore saving to local prefs inside checkAndUpdatePlanStreak.`), hkrati pa `WeeklyStreakWorker` bere in prepisuje glede na *svoje* lokalne zastavice in na drugi napravi pač ni "včerajšnjih workoutov".
2. **Neposlušnost State "Virov Resnice" (SSOT):** Aplikacija nima "enkratnega vira resnice" (Single Source of Truth). Ko v bazo vpisuje UI (ViewModel), na Firebase piše tudi FirestoreHelper, hkrati v ozadju teče Worker na podlagi svojega `Context`-a, vse tri veje krmarijo po svoje. To bo rešil `Repository Pattern`.
3. **Obremenitev naprave:** RDP kompresija koordinatnih stanj v aplikaciji teče, ko uporanik želi obiskati `RunHistory` oz. ko se shrani *Public Activity*. `service/RunTrackingService.kt` logiko za sinhronizacijo dela, kar po nepotrebnem v UI nit vrivamo (npr. UI zaslon kompresira RDP namesto servisa ali Repozitorija, kar pripelje so lagganja).

*Končni zaključek: Aplikacija nujno potrebuje izločitev teh 5-7 kritičnih UseCase datotek in unifikacijo podatkovnih klicov v Centralne repozitorije (Firestore Remote / Local Room), kot je navedeno v ARCHITECTURE_ANALYSIS.md.*



