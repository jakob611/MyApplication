# 🧠 PROJECT BRAIN - Architecture & Audit Log

## 📂 `shared/src/commonMain/kotlin/com/example/myapplication/domain`

### 📝 AKTIVNE DATOTEKE (Active Files)

*   **`LocationUtils.kt`**
    *   **Odgovornost:** Pomožne matematične funkcije za izračun razdalj med GPS koordinatami (Haversine formula).
    *   **Ključne funkcije:** `haversineDist(lat1, lon1, lat2, lon2)`
*   **`WorkoutRepository.kt`**
    *   **Odgovornost:** Vmesnik (KMP abstrakcija) za repozitorij vadb.
    *   **Ključne funkcije:** `isWorkoutDoneToday()`, `getWeeklyTargetFlow()`
*   **`ExerciseRepository.kt`**
    *   **Odgovornost:** Vmesnik in repozitorijska pogodba za zbirko (katalog) vaj in potrebne opreme.
    *   **Ključne funkcije:** `getAllExercises()`, `getAllEquipment()`, `getExerciseByName(name)`
*   **`auth/AuthRepository.kt`**
    *   **Odgovornost:** Vmesnik za sledenje avtentikacijskega stanja uporabnika skozi celotno KMP domeno (v izogib neposrednemu Firebasu).
    *   **Ključne funkcije:** `observeUserId()`, `getCurrentUserId()`
*   **`auth/AuthProvider.kt`**
    *   **Odgovornost:** Preprost Singleton vzorec za DI/globalno posredovanje inicializiranega `AuthRepository` preostanku Multiplatform kode.
    *   **Ključne funkcije:** `provide(repo)`, `get()`
*   **`health/CalculateBodyMetricsUseCase.kt`**
    *   **Odgovornost:** Pure-Kotlin logika za izračun zdravstvenih metrik (BMW, BMI, TDEE in ciljnih kalorij) na podlagi uporabniških vnosov.
    *   **Ključne funkcije:** `calculate(...) -> BodyMetricsResult`

---

### ⚠️ ANALIZA ODVISNOSTI IN KMP INKOMPATIBILNOSTI
*V mapi `shared/.../domain/` sem preveril celotno vsebino – ni neposrednih Android API-jev ali staromodnih `java.time`/`java.util` referenc. Mapiranje je ohranjeno na čistem Multiplatformovem Kotlin standardu.*

---

### 🗑️ POTENTIAL DEAD CODE (Za izbris/čiščenje)

| Identifikator (Datoteka) | Razlog |
| :--- | :--- |
| `ExerciseRepositoryProvider.kt` | Datoteka je **popolnoma prazna** (0 byte kode). Lahko se varno izbriše. |
| `CalculateSpeedChartUseCase.kt` | Datoteka je **popolnoma prazna**. Prejšnji poskus razcepa logike iz Android modula? |
| `health/CalculateExerciseScoreUseCase.kt` | Datoteka je **popolnoma prazna**. |
| `profile/UserProfileRepository.kt` | Uvrščena v package `com.example.myapplication.domain.profile_deleted`! Že ob imenu paketa vidim, da gre za ostanek stare arhitekture. |
| `profile/ObserveUserProfileUseCase.kt` | Pripada paketu `com.example.myapplication.temp` (temp smeti) in krepko namiguje na zgornji mrtvi vmesnik `UserRepository`. Skupaj tvorita neuporaben relikt preteklosti. |

## 📂 `app/src/main/java/com/example/myapplication/domain`

### 📝 AKTIVNE DATOTEKE (Active Files)

**🏋️ Trening in Vadbe (Workout & Training)**
*   **`WorkoutGenerator.kt`**
    *   **Odgovornost:** Osrednji mehanizem za izbiro, filtriranje in uravnavanje težavnosti vaj glede na opremo in cilje uporabnika (bere iz lokalne ali oddaljene JSON baze).
    *   **Ključne funkcije:** `generateWorkout(...)`, `applyProgression(...)`, `calculateScore(...)`
*   **`WorkoutPlanGenerator.kt`**
    *   **Odgovornost:** Poslovna logika za gradnjo in napredno konfiguracijo večtedenskih vadbenih načrtov (vključno z AI in Custom generatorji).
    *   **Ključne funkcije:** `generatePlanWeeks(...)`, `generateAdvancedCustomPlan(...)`, `determineOptimalTrainingDays(...)`
*   **`workout/WorkoutRepository.kt`**
    *   **Odgovornost:** Vmesnik za dostop in shranjevanje zgodovine tekov ter uspešnih treningov.
    *   **Ključne funkcije:** `getWeeklyDoneCount()`, `saveWorkoutSession()`, `getRunSessions()`
*   **`workout/SwapPlanDaysUseCase.kt`**
    *   **Odgovornost:** KMP logika za zamenjavo (swap) dveh izbranih dni znotraj obstoječega planiranega tedna.
    *   **Ključne funkcije:** `invoke(currentPlan, dayA, dayB)`
*   **`run/CompressRouteUseCase.kt`**
    *   **Odgovornost:** Matematična kompresija (znižanje ločljivosti) seznama točk (GPS poti) za zmanjšanje stroškov pri shranjevanju v Firestore.
    *   **Ključne funkcije:** `invoke(points)`

**🎮 Napredek in Zdravje (Progress & Metrics)**
*   **`workout/UpdateBodyMetricsUseCase.kt`**
    *   **Odgovornost:** Povezuje shranjevanje treninga z gamifikacijo in nagradami (dodeljuje XP ob zaključku treninga in posodablja nastavitve).
    *   **Ključne funkcije:** `invoke(email, totalKcal, totalTimeMin, ...)`
*   **`workout/GetBodyMetricsUseCase.kt`**
    *   **Odgovornost:** Pridobi in agregira dnevne/tedenske statistike o vadbi in kalorijah za prikaz na začetnem zaslonu.
    *   **Ključne funkcije:** `invoke(email) -> Flow<BodyHomeUiState>`

**🍎 Prehrana (Nutrition)**
*   **`nutrition/FoodRepository.kt`**
    *   **Odgovornost:** Abstrakten vmesnik za pridobivanje entitet hrane iz črtnih kod ali iskalnikov (Single Source of Truth ob izogibanju diretnem FatSecret klicu v UI).
    *   **Ključne funkcije:** `searchFoodByName()`, `searchFoodByBarcode()`
*   **`nutrition/BodyCompositionUseCase.kt`**
    *   **Odgovornost:** Matematične funkcije za izračun telesne maščobe in BMI indeksa brez odvisnosti od Android SDK (KMP pripravljeno).
    *   **Ključne funkcije:** `calculateBMI()`, `estimateBodyFatPercentage()`

**⚙️ Sistemske Nastavitve, Profil in Orodja (Utils)**
*   **`DateFormatter.kt` & `DateTimeExtensions.kt`**
    *   **Odgovornost:** Nadomestilo za `java.time` in `SimpleDateFormat` – ovijalne metode za `kotlinx.datetime` knjižnico.
    *   **Ključne funkcije:** `formatDateTime()`, `LocalDate.now()`
*   **`barcode/BarcodeScanner.kt` & `BarcodeScannerProvider.kt`**
    *   **Odgovornost:** Vmesnik (in provider) za povezovanje analizatorja slike kamere (ML-Kit / CameraX) z UI slojem brez neposrednega zanašanja aplikacije nanj.
*   **`settings/SettingsProvider.kt` & `SettingsManager.kt`**
    *   **Odgovornost:** Singleton zagovornik uporabe KMP `com.russhwolf.settings` za branje/pisanje lokalnih preferenc.
*   **`Logger.kt`**
    *   **Odgovornost:** Preprosta KMP krovna struktura za dnevnike, ki abstrahira klice `android.util.Log`.
*   **`math/Point2D.kt`**
    *   **Odgovornost:** KMP združljiva alternativa za `PointF` s podporo izračunu razdalj.
*   **`profile/UserProfileRepository.kt`** & **`profile/ObserveUserProfileUseCase.kt`**
    *   **Odgovornost:** Opazovanje uporabniških profilov kot Flow agregator, brez prisotnosti ročnih Firestore listenerjev neposredno v `app` moduli.

---

### ⚠️ ANALIZA ODVISNOSTI IN KMP INKOMPATIBILNOSTI
*  Popoln **prehod na `kotlinx.datetime`** – `DateFormatter.kt` učinkovito zamenjuje vse lokalne pretvorbe datumov in odpravlja uporabo standardne obravnave `java.util.Calendar`. Aplikacijski repozitorij stoji po predpostavljenih MVI in KMP arhitekturnih načelih. 
*  Vidimo lahko močno vpetost specifičnih razredov za reševanje Android UI abstrakcij (`BarcodeScannerProvider` s `CameraX` tipom `ImageProxy`), kar onemogoča 100% prenosljivost teh treh vmesnikov na drugo platformo, razen če v prihodnosti platforme to implementirajo same.

---

### 🗑️ POTENTIAL DEAD CODE (Za izbris/čiščenje v app)

| Identifikator (Datoteka) | Razlog |
| :--- | :--- |
| `nutrition/NutritionCalculations.kt` | Datoteka je **popolnoma prazna** (0 bytes kode). Verjetno relikt poskusov priprave prehranskih logik, ki so prešle v WorkoutPlanGenerator ali BodyCompositionUseCase. |

## 📂 `app/src/main/java/com/example/myapplication/data`

### 📝 AKTIVNE DATOTEKE (Active Files)

**🗄️ Entitete in podatkovni modeli (Data Classes)**
*   **`UserAchievements.kt`** (`Badge`, `PrivacySettings`, `PublicActivity`, `PublicProfile`, `XPSource`)
*   **`RunSession.kt`** (`RunSession`, `LocationPoint`, `ActivityType`)
*   **`UserProfile.kt`** (`UserProfile`)
*   **`PlanModels.kt`** (`PlanResult`, `WeekPlan`, `DayPlan`)
*   **`RefinedExercise.kt`** (`RefinedExercise`, `MuscleIntensity`)
*   **`NutritionPlan.kt`** (`NutritionPlan`)
*   **`BadgeDefinitions.kt`** (Definicije in konstante za značke)

**💾 Repozitoriji in Storage arhitektura**
*   **`workout/FirestoreWorkoutRepository.kt`** - Upravljanje z vadbenimi sejami (bere teke in treninge) in preverjanje dosežkov istega tedna.
*   **`gamification/FirestoreGamificationRepository.kt`** - Transakcijsko reševanje in preprečevanje "Heisenbug-a" z dodeljevanjem XP in streaking logiko prek avtomatiziranega Rest Day *Swapa*.
*   **`nutrition/FoodRepositoryImpl.kt`** - Integracija s FatSecret API za iskanje in beleženje obroka ter vnosa vode neposredno v bazo.
*   **`metrics/MetricsRepositoryImpl.kt`** - Repozitorij za težo in metriko uporabnika.
*   **`profile/FirestoreUserProfileRepository.kt`** - Opazovalec in mapper posodobitev Firestore profilnega dokumenta nazaj na domensko entiteto `UserProfile`.
*   **`HealthStorage.kt`** - Upravljanje dnevnih statistik korakov, kalorij in minut preko Firebase.
*   **`AlgorithmPreferences.kt`** - KMP shranjevanje nastavitev za progresivno težavnost in Recovery mode (Multiplatform Settings).
*   **`settings/UserPreferencesRepository.kt`** - (Android Specific) lokalni repozitorij, prav tako bere Multiplatform preferences in zagotavlja začasne/mocked Flow podatke ter shrambo za `daily_calories` statistiko.
*   **`UserPreferences.kt`** - Stara masivna Android specific Singleton konfiguracija za sinhronizacijo uporabniških profilov s Firestore (`Firebase.firestore`).

**👁 ML/AI Integracije**
*   **`looksmaxing/AndroidMLKitFaceDetector.kt`** - Vezava zadeve ob lokalni ML prepoznavalnik.
*   **`barcode/AndroidMLKitBarcodeScanner.kt`** - Prepoznavanje pametnih črtnih kod (Android only).
*   **`settings/AndroidSettingsProvider.kt`** - Provider in vezni vmesnik za dostopanje do multiplatform nastavitev.

---

### ⚠️ ANALIZA ODVISNOSTI IN ZLOBNE FIRESTORE REFERENCE (Kritično)

Našli smo izjemno kritične odklone pri pravilih strukture! Veliko repozitorijev preskoči obvezen `FirestoreHelper` in komunicira s podedovanimi inicializacijami `Firebase.firestore` ali `FirebaseFirestore.getInstance()`. To otežuje Multiplatform migracijo in omogoča nepredvidljivost poslušalcev ob avtentikacijskih menjavah.

🚨 **Pregled 'Zlobnih' referenc, ki jih moramo očistiti:**

| Odgovorni razred | Trenutno (napačno) stanje | Pravilna resolucija |
| :--- | :--- | :--- |
| `UserPreferences.kt` | `private val db = Firebase.firestore` | Skrit za `FirestoreHelper` |
| `MetricsRepositoryImpl.kt` | `private val db = Firebase.firestore` | Namesto hard-coded instance poklicati Helper |
| `FoodRepositoryImpl.kt` | `FirebaseFirestore.getInstance().runTransaction { ... }` | Uporabiti `FirestoreHelper.withRetry` oz ustrezno transakcijsko abstrakcijo |
| `FirestoreGamificationRepository.kt` | `private val db = FirebaseFirestore.getInstance()` | Za transakcije prenoviti iz `db.runTransaction` na podprt helper mehanizem |
| `HealthStorage.kt` | `private val db get() = Firebase.firestore` | Spremeniti vse klice preko Helperja |

🚨 **PODVAJANJE PODATKOV in LOKALNE SMETI (SharedPreferences Konflikt)**

V mapi `data` se masovno podvajata dva sistema: `UserPreferences` in `UserPreferencesRepository`.
Oba kličeta in pišeta v `"user_prefs"`. Prvi uporablja izvirni `Context.getSharedPreferences()`, drugi pa knjižnico `com.russhwolf.settings.SharedPreferencesSettings()`. Potrebujemo enoten prenos v KMP `SettingsManager` in dokončen izbris starega Android mehanizma, saj zdaj oba sistema "tekmujeta" med sabo.

---

### 🗑️ POTENTIAL DEAD CODE (Za izbris/čiščenje v app)

| Identifikator (Datoteka) | Razlog |
| :--- | :--- |
| `auth/FirebaseAuthRepositoryImpl.kt` | Celotna vsebina datoteke je od 1. do 28. vrstice enostavno **zakomentirana** (`/* ... */`) in napisano je "Leftover file from KMP migration. Unused.". NUJNO izbrisati. |

## 📂 `app/src/main/java/com/example/myapplication/ui`

### 📝 AKTIVNE DATOTEKE (Active Components)

**🖥️ Jedrni Zasloni (Core Screens / Dashboards)**
*   **`DashboardScreen.kt` & `Indexscreen.kt`** - Glavna vhodna in nadzorna plošča.
*   **`BodyModuleHomeScreen.kt` / `HairModuleScreen.kt`** - Moduli za specifične sekcije aplikacije.
*   **`ActivityLogScreen.kt`** - Pregled zgodovine (aktivnosti, treningi).
*   **`RunTrackerScreen.kt`** - Zaslon za sledenje teku in shranjevanje rut.
*   **`WorkoutSessionScreen.kt` / `LoadingWorkoutScreen.kt` / `GenerateWorkoutScreen.kt`** - Potek same vadbe in pripravljalni ekrani.
*   **`MyPlansScreen.kt`** - Pogled uporabnikovih planov.

**🍎 Prehrana & Zdravje**
*   **`NutritionScreen.kt`** - Glavni prehranski modul.
*   **`AddFoodSheet.kt` / `NutritionDialogs.kt` / `NutritionComponents.kt`** - Pomožni UI in dialogi (vključuje FatSecret UI).
*   **`BarcodeScannerScreen.kt`** - UI za črtno kodo hrane, povezan z instanco `AndroidMLKitBarcodeScanner`.
*   **`HealthConnectScreen.kt`** - Integracija za sistemsko sinhronizacijo z Google Health Connect.

**🎮 Igrifikacija (Gamification)**
*   **`AchievementsScreen.kt`** - Zbirni prikaz dosežkov in XP zgodovine.
*   **`DonutProgressView.kt`** - Komponenta za napredek kalorij.

**⚙️ Nastavitve in Profili (Settings/Profile)**
*   **`MyAccountScreen.kt` / `PublicProfileScreen.kt`** - Prikaz in urejanje osebnih/javnih podatkov.
*   **`ShopScreen.kt`** - Trgovina z zamenjavami (navidezna valuta).
*   **`DeveloperSettingsScreen.kt`** - Napreden pregled lokalnih KMP in Firestore stanj.
*   **`AboutScreen.kt` / `ContactScreen.kt` / `PrivacyPolicyScreen.kt`** - Statične informativne strani.

**🏗️ Arhitektura in Infrastruktura (UI Architecture)**
*   **`MyViewModelFactory.kt`** - Tisti ultimativni DI router znotraj aplikacije, ki skrbi za vstavljanje KMP in Android-specific repozitorijev v ViewModele preden so ti dodani Compose Graphu.
*   **`ui/theme/`** - Tema aplikacije (`theme.kt`, `MyApplicationTheme.kt`).
*   **`ui/components/`** - (npr. `LoadingRetryView`, `XPPopup`, `OnboardingHint`).

---

### ⚠️ ANALIZA ODVISNOSTI IN KRITICNE NAPAKE V UI (Arhitekturni Prekrški)

Na UI sloju smo našli nekaj ogromnih arhitekturnih "grehov", kjer so Compose funkcije mimo vseh ViewModelov prevzemale vlogo Data in Domain sloja! Prečistili smo tudi prekrške z `java.time` (sedaj zavedeno pod KMP inkompatibilnost).

🚨 **ZASLONI ZA REFAKTORIRANJE LOGIKE (Nujno čiščenje):**

| Zaslon | Opis Prekrška (Problem) | Zahtevan Ukrep (Refaktor) |
| :--- | :--- | :--- |
| `NutritionScreen.kt` | Vsebuje direktne klice `FirebaseFirestore.getInstance()` ali ročno branje `.collection("users")` neposredno v vmesniku baze! | Vso Firestore kodo preseliti v `NutritionViewModel` ter nato opazovati le UI state iz screena. |
| `Progress.kt` | Podobno kot Nutrition, tudi ta sam inicializira bazo `val db = Firebase.firestore` in direktno kliče Firestore za dnevne metrike. | Premakniti kodo v relavantne `MetricsRepositoryImpl` in jo upravljati preko ViewModela (`ProgressViewModel`). |
| `HealthConnectScreen.kt` | Prekršek `java.time` knjižnice – edini zaslon z ohranjeno dediščino standardne Jave (`java.time.Instant`), vendar je to dovoljeno in pričakovano **zaradi Health Connect SDK-ja**, ki eksplicitno zahteva te razrede. Tega modula ni mogoče deliti na KMP. | Ni možen KMP popravek. Izolacija na Android SourceSet preide skozi. |
| Splošni KMP Prekrški | V UI so bile odpravljene vrstice na mrtve `java.time.LocalDate` (`DeveloperSettingsScreen.kt`, `PlanDataStore.kt`). Očiščeni `MainActivity.kt` importi. | Vsi splošni zasloni (ki niso HealthConnect) morajo uporabljati in že uporabljajo `kotlinx.datetime`. |

---

### 🗑️ POTENTIAL DEAD CODE (Mrtvi Zasloni in UI Smeti)

| Identifikator (Datoteka) | Razlog |
| :--- | :--- |
| `LevelPathScreen.kt` | **Izbrisano** (2026-04-19): Mrtva komponenta, nikoli priklicana. |
| `BadgesScreen.kt` | **Izbrisano** (2026-04-19): Mrtva komponenta, nikoli priklicana. |
| `GoldenRatioScreen.kt` | **Izbrisano** (2026-04-19): Izolirana in neuporabljena komponenta. |
| `EAdditivesScreen.kt` | **Izbrisano** (2026-04-19): Izpadla funkcionalnost, mrtva koda. |
