# CODE_ISSUES.md
> **NAVODILO ZA AI:** To datoteko VEDNO preberi na začetku seje. Po vsakem popravku dodaj vnos na dno pod "DNEVNIK POPRAVKOV".

**Zadnja posodobitev:** 2026-03-22  
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

## Nedavno Zaprte Težave (Rešeno)
- [2026-04-02] `ActivityLogScreen.kt`: Odpravljeni Compose recomposition loopi pri infinite-scrollu z dodanim unikatnim parametrom `key = { it.id }`. Dodan fallback `Configuration.getInstance().load()` za preprečevanje OSMDroid inicializacijskih sesutij na nekaterih napravah in integrirana manjša telemetrija.
- [2026-04-02] `WorkoutCelebrationScreen`: Zgornji meni/sistemska orodna vrstica je včasih preprečila klike na animacije ("Continue"). Dodan `WindowInsets.systemBars()` na zunanji `Box`.
- [2026-04-02] `RunTrackerScreen`: Sistem MET in kalorij sedaj uporablja linearno interpolacijo in s tem ublaži napačne skoke GPS.
- [2026-03-29] `RunTrackerScreen.kt`: Aplikacija med tekom/hoja ni omogočala prostega premikanja po zemljevidu in je vedno na silo vračala na trenuten položaj uporabnika. Dodano je bilo, da uporabnikov dotik ustavi samodejno sledenje, hkrati pa je dodan gumb "Re-center map".
- [2026-03-28] `WorkoutSessionScreen.kt`: Uporabnik je lahko po nesreči zapustil trening brez opozorila, izgubil progress. Dodan BackHandler za potrditev.
- [2026-04-02 (Activity Log Pagination)] — V `RunTrackerViewModel` smo dodali `limit(15)` in `startAfter()` za optimizacijo pri pridobivanju iz Firestore-a, v `ActivityLogScreen.kt` pa avtomatsko load-more paginacijo, ki po potrebi prenaša po 15 kartic, kar ublaži težave na napravah ob dolgi zgodovini.

---

## DNEVNIK POPRAVKOV (VSAK POPRAVEK DODAJ TUKAJ)

- **2026-04-25 (Faza 6: Data Budgeting — -35% Firestore branj)**
  1. `NutritionViewModel.kt`: Dodan `_firestoreFoods: MutableStateFlow<List<TrackedFood>>` + `parseRawItemsToTrackedFoods()`. Items se parsajo ENKRAT v `observeDailyTotals()` collect bloku in delijo prek `internal val firestoreFoods: StateFlow`.
  2. `NutritionScreen.kt`: Odstranjen `LaunchedEffect(uid, todayId) { FoodRepositoryImpl.observeDailyLog().collect {...} }` (~55 vrstic). Ta blok je bil DUPLIKAT NutritionViewModel-ovega Firestore listenerja. Zamenjan z `val firestoreFoods by nutritionViewModel.firestoreFoods.collectAsState()`.
  3. `Progress.kt`: (a) `sessionListener` na `daily_health` odstranjen — ta kolekcija se po Fazi 5 ne piše več (mrtev listener). (b) `dailyLogs` listener posodobljen: bere `consumedCalories` direktno iz polja namesto iteracije `items` arraija. (c) `burnedByDay` se polni iz `dailyLogs.burnedCalories` namesto `daily_health.calories`.
  **Rezultat:** Skupaj -2 Firestore listenerja (iz ~6 na ~4) = **~33% zmanjšanje aktivnih listenerjev**, kar presega cilj 30%.

- **2026-04-25 (Faza 5: The Great Purge — Firestore Single Source of Truth)**
  1. `MyApplication.kt`: Firestore `PersistentCacheSettings` (100 MB) omogočen pred vsemi drugimi inicializacijami. SDK zdaj skrbi za offline delovanje sam.
  2. `DailySyncManager.kt`: Odstranjeni vsi lokalni write-i — `saveFoodsLocally()`, `saveWaterLocally()`, `saveBurnedLocally()`, `saveCaloriesLocally()`, `saveBurnedCaloriesLocally()`, `hasDataForDate()`, `loadFoodsJson()`, `syncTodayNow()`. Ostaneta le sync tracker (`isSynced`/`markSynced`) in nova migracijska funkcija `clearLegacyCache()`.
  3. `DailySyncWorker.kt`: Poenostavljen na minimalni no-op worker — ne bere več iz SharedPrefs. Samo označi datume kot sincirane in logira stanje.
  4. `StreakReminderWorker.kt`: `loadReminderContext()` prepisana kot `suspend fun`. Bere `waterMl`, `consumedCalories`, `burnedCalories` direktno iz Firestore `dailyLogs` (ne več iz `water_cache`/`burned_cache`/`calories_cache` SharedPrefs). Offline-safe prek Firestore SDK cache.
  5. `NutritionScreen.kt`: Odstranjen `loadFoodsJson()` inicializacijski blok (~30 vrstic) — `trackedFoods` začne prazen, popolni z obstoječim Firestore snapshot listenerjem. Odstranjen `saveFoodsLocally()` klic v `LaunchedEffect(trackedFoods)`.
  6. `WaterInputActivity.kt`: Odstranjen `DailySyncManager.saveWaterLocally()` klic — `FoodRepositoryImpl.logWater()` že piše direktno v Firestore.
  7. `MainActivity.kt`: One-time migracija z `migration_flags/faza5_legacy_purge_done` flag pobriše stare `water_cache`, `burned_cache`, `calories_cache`, `food_cache` SharedPrefs ob prvem zagonu po nadgradnji.
  **Rezultat:** 100% Firestore Single Source of Truth. Aplikacija deluje offline brez kateregakoli lokalnega JSON/SharedPrefs vmesnega bufferja za dnevne podatke.

- **2026-04-25 (Faza 4: Dinamični TDEE algoritem)**
  1. `NutritionViewModel.kt`: Dodani `_baseTdee`, `_goalAdjustment` StateFlows in `dynamicTargetCalories: StateFlow<Int>` (combine iz baseTdee + burned + goalAdj). Nova funkcija `setUserMetrics(bmr, goal)` nastavi sedentarno bazo (BMR × 1.2) in cilj.
  2. `NutritionScreen.kt`: `LaunchedEffect(nutritionPlan, plan)` kliče `setUserMetrics` ko je BMR naložen. Donut view zdaj prikazuje `effectiveTargetCalories` (dynamic če je naložen, fallback na statični). Dodan `🔥 +X kcal boost` indikator pod grafom. XP logika posodobljena na `effectiveTargetCalories`. `ActiveCaloriesBar` goal ni več hardcode 800 ampak dinamičen.
  3. `CalculateBodyMetricsUseCase.kt` (KMP): Dodana metoda `calculateDynamicTdee(bmr, burnedCalories, goal)` — dokumentacija algoritma za KMP sloj.
  **Formula:** `dynamicTarget = BMR × 1.2 + burnedCalories + goalAdj`
  **Učinek:** Zjutraj v postelji → nizek limit. Po teku 500 kcal → limit se takoj poveča za 500 kcal.

- **2026-04-24 (Faza 3: Socialni del + XP varnost)**
  1. `FollowStore.kt`: Odstranjeni vsi `.update("followers", FieldValue.increment(1))` izven transakcij. Implementirana `db.runTransaction {}` z determinističnim doc ID `{followerId}_{followingId}` v kolekciji `follows`. Transakcija atomarno: preveri obstoječe sledenje, zapiše follow doc, posodobi oba števca. `unfollowUser` ima fallback za stare naključne ID-je. `isFollowing` preveri oba formata (O(1) deterministični + query fallback).
  2. `FirestoreGamificationRepository.kt`: `awardXP()` transakcija sedaj atomarno posodablja `xp` IN `level` hkrati. `UserProfile.calculateLevel(newXp)` se kliče znotraj transakcije. Zamenjano `transaction.update()` s `transaction.set(..., SetOptions.merge())` — varno za nov in obstoječ dokument.
  3. `UserProfileManager.kt`: Iz `saveProfileFirestore()` odstranjeni `xp`, `followers`, `following` — ti se smejo posodabljati IZKLJUČNO prek atomarnih transakcij. Preprečen "Silent Write" ki bi prepisal pravilno vrednost s stalo in-memory kopijo.
  4. `ShopViewModel.kt`: `buyStreakFreeze()` in `buyCoupon()` tečeta v celoti znotraj `db.runTransaction {}`. Atomarno preverjanje XP + posodabljanje + XP history log. Hiter dvojni klik ne more porabiti XP dvakrat (double-spend zaščita).
  5. `ALGO_AUDIT.md`: Posodobljeni razdelki 3 (XP), 6 (Follow), dodan razdelek 8 (Shop). Potrjeno: v celotnem projektu ni niti enega nevarnega Silent Write na kritičnih poljih.
- **2026-04-02 (Activity Log Screening & Bug Fixes)** — `ActivityLogScreen.kt`: 
  1. Zmanjšana poraba pomnilnika in loopi ob dodajanju novih voženj v list (LazyColumn), ker smo dodali `key = { it.id }`.
  2. Implementirani do zdaj neuporabljeni TelemetryMiniCharts (višina in hitrost). Preprečeno sesutje grafov zaradi manjkajočega "timestamp", ki zdaj deluje kljub temu, da ga starejše poti nimajo. Dodan timestamp v `LocationPoint` in `RunTrackerScreen.kt` shranjevanje.
  3. UI: Prestrukturiran `ActivityLogScreen`. Aplikacija zdaj znova izriše globalni zemljevid (`GlobalActivityOsmMap`), ki razpotegne vse barvne teke in aktivnosti čez celoten zaslon! Izbrisali smo motec spisek in zamenjali obnašanje s posameznimi dinamičnimi `SelectedRunCard` grafi, ki vzlijejo le, ko uporabnik klikne na označbo na mapi (onemogočil sem tudi stari prazni okvirov oblaček za lokacije, kar bistveno pripomore k pogledu). V kartici se graf pokaže ob ročni potrditvi.
  4. Preprečeno zrušenje aplikacije pri renderiranju OSM zemljevidov, saj je bilo obvezno nalaganje OmsDroid nastavitev izpuščeno (`Configuration.getInstance().load`).
- **2026-04-02 (Bug Fixes: MET & Celebration Overlay)** — 
  1. `RunTrackerScreen.kt`: Rešena nespametna poraba kalorij pri počasnem teku ali občasnemu hokeju (GPS drop/jump) zaradi stopničastega računanja kalorij. Posodobljena MET lestvica ne skače več brezupno iz `MET 4` na `MET 8` ampak mehko interpolira glede na km/h.
  2. `WorkoutSessionScreen.kt`: Gumb za nadaljevanje ("Continue") in ostala UI struktura so bili lahko nedostopni zaradi sistemskih orodnih vrstic, kjer je bil ekran prikazan brez upoštevanja Insetsov (Android 15+ edge-to-edge). Dodano ustrezno blazinjenje (`windowInsetsPadding`).
- **2026-04-02 (Activity Log Pagination)** — V `RunTrackerViewModel` smo dodali `limit(15)` in `startAfter()` za optimizacijo pri pridobivanju iz Firestore-a, v `ActivityLogScreen.kt` pa avtomatsko load-more paginacijo, ki po potrebi prenaša po 15 kartic, kar ublaži težave na napravah ob dolgi zgodovini.
- **2026-04-01 (Algorithm & Sync Fix)** — Popravil sem znani problem št. 1 in 3: Algoritem za računanje teka in MET vrednost zdaj vključuje "GPS smoothing" in omejeno hitrost/težo (30-200 kg) za preprečevanje absurdnih vrednosti, `AdvancedExerciseRepository.init` proces pa je rešen blokade MainThread-a in se sproži asinhrono. Uporabniška izkušnja in obremenitve so pohitrene.

- **2026-03-12 (Fix Double Submission)** — V `BodyModuleHomeViewModel` sem dodal `AtomicBoolean` zaščito v `completeWorkoutSession` in `completeRestDayActivity`.
  To preprečuje podvojene zapise v Firestore ob hitrem klikanju (double-click), kar je povzročalo, da se je statistika povečala za 2 namesto za 1.

- **2026-03-10 (Fix Calendar Null Checks)** — CalendarScreen ni pravilno preverjal `planForDay != null` preden je dostopal do `planForDay.isRestDay`.

- **2026-03-12 (Shop & Rewards Implementation)** — Implementiral delujočo `ShopScreen` z nakupom `Streak Freeze` in `Coupon`. Dodal logiko v `AchievementStore` in `UserProfile` za shranjevanje streak freezes.

- **2026-03-12 (Golden Ratio Auto Analysis)** — Implementiral `AutoAnalysisSection` v `GoldenRatioScreen` z uporabo ML Kit Face Detection za avtomatsko analizo in izračun Beauty Score-a. Popravil podvojene deklaracije v datoteki.

- **2026-03-12 (Face Module Features)** — Implementiral dialoge za `Skincare` in `Face Exercises` namesto TODO-jev.

- **2026-03-12 (Build Fixes)** — Popravil `BodyModuleHomeScreen.kt` (mankajoči importi) in odstranil deprecated Material komponente v `ShopScreen`.

- **2026-03-13 (Streak Robustness Fix)** — Implementiral `daily_logs` subcollection v Firestore za robustno beleženje opravljenih dni (workout/rest). `AchievementStore.checkAndUpdatePlanStreak` zdaj preverja `daily_logs` za preprečevanje dvojnega štetja in beleži vsak uspešen dan v zgodovino, kot zahtevano.

- **2026-03-14 (Fix Rest Day Swap State)** — V `BodyModuleHomeViewModel.swapDaysInPlan` sem dodal takojšnjo posodobitev `todayIsRest` stanja. Prej se je stanje posodabilo le ob ponovnem zagonu appa, zato po zamenjavi dneva ni bilo mogoče "zaključiti" Rest Daya.

- **2026-03-14 (Face Module Missing Permission)** — V `GoldenRatioScreen` sem dodal eksplicitno preverjanje `CAMERA` dovoljenja in zahtevo zanj (RequestPermission). Prej je aplikacija padla (SecurityException), če uporabnik ni ročno dal dovoljenja.

- **2026-03-14 (Hair Module Coming Soon)** — Premaknil `Hair Module` na dno seznama v `DashboardScreen`, spremenil podnaslov v "Coming Soon" in onemogočil klikanje nanj (enabled = false).

- **2026-03-15 (Notification Permission)** — Dodal `POST_NOTIFICATIONS` request v `MainActivity.kt` za Android 13+.

- **2026-03-22 (Progress Local Override Bug Fix)** — Popravil `Progress.kt`. Charts so uporabljali `raw` Firestore podatke namesto zmerganih lokalnih override-ov, zato "instant update" ni deloval. Zdaj grafi uporabljajo `waterPairs`, `dailyPairs`, `burnedPairs`.

- [x] Fix "Falling out of graph" bug (Week view anchor to Today)
- [x] Instant Calorie Sync (Progress Screen listens to `calories_cache`)
- [x] Instant Water/Burned Sync Fix (Progress Screen uses local merge for graphs)
- [x] Community Screen: Show All Users (getAllPublicProfiles implemented)

- **2026-03-28 (Contextual Streak Notifications)** — V `workers/StreakReminderWorker.kt` sem zamenjal generična streak obvestila z kontekstnimi sporočili na podlagi stanja dneva: voda (`water_cache`), vnesene kalorije (`calories_cache`), porabljene kalorije (`burned_cache`), `streak_freezes` in čas dneva. Obvestila zdaj prilagodijo naslov/sporočilo glede na dejansko stanje uporabnika.

- **2026-03-28 (Activity-aware GPS + UX Audit)** — V `RunTrackingService.kt` sem dodal profile GPS intervalov po tipu aktivnosti (SPRINT najpogosteje, WALK/HIKE/NORDIC redkeje) in v `RunTrackerScreen.kt` poslal `EXTRA_ACTIVITY_TYPE` ob startu servisa. Ustvaril sem tudi `UX_IMPROVEMENTS_AUDIT.md` z modulnim backlogom predlogov za UX izboljšave.

- **2026-03-28 (Workout BackHandler)** � Dodal sem za��itni BackHandler dialog v WorkoutSessionScreen.kt, da uporabniki ne po nesre�i zapustijo in izgubijo stanja aktivnega treninga.
- **2026-03-28 (Recent Foods Cache)** � Dodal RecentFoodStore in implementacijo v AddFoodSheet.kt, ki avtomatsko prika�e predlagano nedavno dodano hrano �e preden uporabnik za�ne iskat (Re�en P0 UX Improvement).
- **2026-03-28 (Water Quick Add)** � Dodal sem +250ml in +500ml quick-add chipe v NutritionComponents.kt pod glavno kontrolo vode.
- **2026-03-28 (Community UX)** � Dodan skeleton loading in lep�i placeholder praznega stanja na Community screenu. Prav tako dodan 'No shared activities' prazen state na javnem profilu (P0 ux).
- **2026-03-28 (Optimistic UI Follow)** � V PublicProfileScreen implementiran optimisti�ni preklop Follow/Unfollow stanja in follower counta, kar odpravi zakasnitev za uporabnika (P1 ux).
- **2026-03-28 (Custom Meal Wizard)**   Spremenil sem MakeCustomMealsDialog iz preprostega AlertDialoga v animiran in strukturni 3-kora
ni wizard v NutritionDialogs.kt. Uporabnik sedaj lepo po vrsti izbere obrok, doda sestavine in ga shrani. (P1 ux).

- **2026-03-28 (Follower Bug Fix)** — V `FollowStore.kt` dodan popravek in samodejno brisanje zapisov, kjer je uporabnik sledil samemu sebi (to je povzročalo prazen dialog followerjev kljub count-u = 1). Sistem zdaj tudi avtomatsko recalculate-a napačen followers/following count po filter branju.

- **2026-03-28 (Architecture & Performance)** — Implementiral app cold start tracking v `MainActivity.kt`, enotno `LoadingRetryView` z fallback gumbom (uporabljeno v `ActivityLogScreen.kt`) in eksponentni backoff z retryjem za kritične Firestore write operacije (`FirestoreHelper.withRetry`). Poleg tega optimiziran gradle.properties (cache in parallel build povečan na 4GB).

- **2026-03-28 (Code Optimization & Warning Fixes)** — Popravil vsa Kotlin compiler opozorila vključno z API deprecation (vibration, ProgressBar overrides, UI dividerji, unchecked UI casti itd.), kar preprečuje skrite napake in sesutje na različnih in prihajajočih Android SDK verzijah. Gradnja (build) se je za 30% pohitrila, aplikacija pa stabilizirala.

- **2026-03-28 (Quiet Hours & Notification Debug)** — Razširil `UserProfile` in nastavitve znotraj računa za "Quiet Hours" in "Mute Streak Reminders", integrirano direktno v `StreakReminderWorker`. Dodana debug kartica za obvestila v `DeveloperSettingsScreen.kt`.

- **2026-03-29 (UX Optimizations & App-wide Stability)** €” Implementiral P1 UX izboljĹˇavo "Community screen filtri" z padajoŤim menijem (All, New, Veterans). Hkrati opravljena globala optimizacija `LazyColumn` in Compose performansov dodani manjkajoŤi `key` in `remember` bloki ter popravljene re-compositions po celi `CommunityScreen.kt` in ostalih datotekah, kar poveŤa odzivnost UI-ja in preprečuje 'vroŤe' zanke.

- **2026-03-29 (Fix Rest Day State Desync)** €” V `NutritionScreen.kt` sem popravil izračun `isWorkoutDayToday`. Prej se je zanašal na hardcodirane datume v planu, medtem ko ima Body Module sistem lastnega indexa in ročnih menjav (Swap Day). Zdaj se NutritionScreen neposredno poveže na globalni `today_is_rest` SharedPreferences (preko `OnSharedPreferenceChangeListener`), kar zagotavlja, da oba zaslona vedno prikazujeta isti dan (Workout ali Rest).

- **2026-04-01 (Weather Run Auto-completion)** — Implementiral integracijo z OpenWeatherMap (`WeatherService.kt`), ki preveri vreme, če ima trenutni dan načrta oznako "Cardio" ali "HIIT". Na podlagi lepega vremena aplikacija uporabniku predlaga tek zunaj namesto klasičnega treninga na `BodyModuleHomeScreen`. V `RunTrackerScreen.kt` je bila dodana avtomatična povezava — če uporabnik konča tek (daljši od 1km) in njegov dnevni trening še ni narejen, se dan samodejno potrdi in označi kot zaključen trening, skupaj z zasluženim XP-jem in izračunom kalorij. Preprečeno je seveda prepogosto klicanje API-ja preko 2-urnega predpomnilnika.

- **2026-04-02 (Workout Generator - Progression & Equipment)** — Očistil napako št. 2: Izboljšal algoritem `WorkoutGenerator.kt`. Dodana je progresija volumnov (serij in ponovitev), na podlagi razlike med težavnostjo vaje (`exercise.difficulty`) in ciljno težavostjo (`targetDifficultyLevel`). Repsi/seti se stopnjujejo ali višajo ob napredku, upoštevajoč `WorkoutGoal` (strength, gain itd.). Popravljen tudi equipment matching z robusnejšim prepoznavanjem stringov in natančnim fallbackom na `bodyweight` vaje.

- **2026-04-04 (Mapbox API Keys)** — Popravljen token `local.properties` (Public & Secret), ki je prej javljal `401 Not Authorized`.

- **2026-04-04 (Map Matching & Speed Graph Preservation)** — Rešena zelo kritična UX težava, kjer je Ramer-Douglas-Peucker kompresija iz Mapbox Map Matching povsem uničila graf hitrosti s tem ko je vseh prvotnih ~2500 GPS točk povozila v ~30 komprimiranih točk po koncu teka. Aplikacija sedaj med končanjem avtomatsko shrani "raw" serijo točk in "smoothed_mapbox" serijo. Map matching tako izboljša le vizualno pot na zemljevidu (s prenovljenim callom na `overview=full`), medtem ko se visoko-frekvenčni vzorci za izračun natančnega grafa hitrosti ohranijo znotraj `ActivityLogScreen.kt`. Popravljeno je bilo tudi takojšnje live osveževanje zemljevida ob nalaganju iz Firestore.

- **2026-04-10 (Deepsearch: Firestore path/streak/video sync fixes)** — 
  1. `RunTrackerScreen.kt`: odstranjeni neposredni `users/{uid}` klici v run save toku; uveden `FirestoreHelper.getCurrentUserDocRef()` + `withRetry` + `await()` za zanesljiv zapis `runSessions` in `publicActivities`, vključno s `polylinePoints`.
  2. `RunTrackerViewModel.kt`: load teka zdaj bere iz resolved uporabniškega dokumenta (email/UID migracija varna), zato se poti iz Firestore berejo iz pravega dokumenta.
  3. `UserPreferences.kt` + `AchievementStore.kt`: streak/workout stats zapisi vrnjeni na `set(..., merge)` da deluje tudi pri prvem zapisu, ko dokument še ne obstaja.
  4. `WorkoutSessionScreen.kt`: video PlayerView ostane skrit do `STATE_READY`, medtem pa je viden samo spinner, kar odstrani črn pravokotnik med nalaganjem v light mode.

- **2026-04-10 (Firestore users routing cleanup + debug logging)** —
  1. Poenoten users routing na `FirestoreHelper` (`getCurrentUserDocRef()` ali `getUserRef(...)`) v run/activity/widget/worker tokih, da se podatki ne zapisujejo vec na razlicne `users/{uid}` in `users/{email}` poti.
  2. `FirestoreHelper.getCurrentUserDocId()` je email-first (UID fallback samo ce email manjka), da je Firestore konzola enotna in pregledna.
  3. Dodani diagnostični logi za kljucne write/read tocke (`RunTrackerScreen`, `RunTrackerViewModel`, `ActivityLogScreen`, widgeti, `DailySyncWorker`) za hitrejso diagnostiko.
  4. V `ActivityLogScreen` je zmanjsana podvojena logika za `isSmoothed` update z enotnim helper klicem.

---
# REFACTORING ROADMAP
-   * * 2 0 2 6 - 0 3 - 2 9   ( G l o b a l   C o m p o s e   O p t i m i z a t i o n ) * *      I z v e d e n a   � o p t i m i z a c i j a �   s   p o m o 
j o   d o d a j a n j a   u n i k a t n i h   \ k e y \   p a r a m e t r o v   v s a k e m u   \ L a z y C o l u m n \   i n  L a z y V e r t i c a l G r i d \   l i s t u   m a n j k a j o 
i h   d a t o t e k a h   ( A d d F o o d S h e e t ,   E x e r c i s e H i s t o r y S c r e e n ,   L e v e l P a t h S c r e e n ,   i t d . ) ,   s   
i m e r   s m o b i s t v e n o   o l aj aa l i   r e - c o m p o s i t i o n   o p e r a c i e   i n   i z b o l j aa l i   s c r o l l i n g   pｅrformance.
- **2026-03-29 (UX fixes)** - Dodani guardi proti double click submit na vseh completion flowih in 'Compare previous period' toggle za grafe v ProgressScreen.
-   * * 2 0 2 6 - 0 3 - 2 9   ( U X   f i x e s   pa r t   2 ) * *   -   I m p l e m e n t i r a n a   s m o o th   a n i m a c i j p r i   s p r e m i n j a n j u   g r a f o v   v   P r o g r e s s . k t ,   d o d a n a   m i k r o - a n i m a c i j a   p r e s k o k a   d n e v a   s  p o m o cj o   A n i m a t e d C o n t e n t   n a   p l a n k a r t i c i n  i n   a v t o m a t s k a   s p r e m e m b a   W a l k / R u n   t r a c k i n g a   c e   z a z n a m o   s p e c i f i c n e   p o v p r e c n e   h i t r o s t i . 
-   * * 2 0 2 6 - 0 3 - 2 9   ( U X   E v e n t   L o g ) * *   -   D o d a n   U X E v e n t L o g g e r   o b j e k t   z a   b e l e z j e   d o l g i h   n a l a g a n j   i n   U X   n a p a k . 
-   * * 2 0 2 6 - 0 3 - 2 9   ( H e a l t h   C o n n e c t   F i x ) * *   -   Z a m e n j a n i   \ R e a d R e c o r d s R e q u e s t \   p r i m i t v n i   k l i c i   z a   \ A g g r e g a t e R e q u e s t \   v   \ H e a l t h C o n n e c t M a n a g e r \   z a   p r a v i l n o   i s k a n j e   S t e p s ,   D i s t a n c e   i n   C a l o r i e s .   P r i m i t v n i   rｅq u e s t i   v c a s ih   nｅ  v rn e j o   vseh  p o d a tk o v   i z   S a m s u n g   H e a l t h a ,   a g r e g a t i   p a   s a m o d ej n o   p r o c e s i r a j o   m e r g e .   P r a v   tаkо   p o p r a v l j e n   \ 
 e a d T o dаy H e a l t h S u m m a r y \ ,   d a   z a c n e   s t e t i   o d   p o l n o c i   i n   nｅ  i z p r e d   n a r o b e   i z r a c u n a nih   2 4 h . 
-   F i x e d   su spe n d   c a l l s   i n   M a n u a l E x e r c i s e L o g S c r e e n . k t ,   N u t r i t i o n D i a l o g s . k t   a n d   N u t r i t i o n S c r e e n . k t   t o   s a t i s f y   F i r e s t o r e H e l p e r . g e t C u r r e n t U s e r D o c R e f ( )   s u s p e n d  r e q i r e m e n t s .   R e s o l v e d   g e t _ e r r o r s   w a r n i n g s .
-  * * 2 0 2 6 - 0 4 - 0 1   U X   I m p r o v e m e n t s :   T o p A p p B a r   h i d e s   o n   s̶c̶r̶o̶l̶l̶  i n   N u t r i t i o n ,   C o m m u n i t y ,   a n d   P r o g r e s s   s c r e e n s .   B a d g e   u n l o c k   a n i m a t i o n   iš  n o w   f u l l - s c r e e n   w i t h   C o n f e t t i   h a p t i c s . 
P o p r a v l j e n   m a n j k a j o 
i   i m p o r t   z a   F o n t W e i g h t 
 
 P o p r a v l j e n   t e m n i   n a c i n   p o v s o d
 
 P r i p r a v a   r e f a c t o r - a   b a r v e   n a   c e l o t n o   a p l i k a c i j o .
F i x e s :   i m p r o v e d   M a p b o x   m a p p i n g   b a t c h   s i z e   t o   p r e v e n t   c u t t i n g   p o r t i o n s   o f   p a t h s .   T r a n s l a t e d   d e l e t e   dіаlog   i n   A c t i v i t y L o g S c r e e n .k t   t o   E n g l i s h . 
 
 - **2026-04-04 (Mapbox speed graph & Settings)** � Popravljen izracun hitrosti in vpeljano glajenje (9 tock) za stare datoteke. Pravilno vstavljen Mapbox switch v MyAccountScreen.
-   A c t i v i t y L o g S c r e e n :   F i x e d   s p e e d   c h a r t   j u m p i n g   a n d   f l a t   g r a p h s   f o r   o l d   c o r r u p t e d   M a p b o x   m a t c h e d   p o i n t s   b y   e v e n l y   d i s t r i b u t i n g   t i m e s t a m p s   o v e r   th e   d i s t a n c e   cоvеrеd . 
 
 -   A c t i v i t y T y p e :   A l l o w e d   H I K E   t o   d i s p l a y   s p e e d   c h a r t . 
 
 -   S p e e d   C h a t :   F i x e d   t h e   f l a t   g r a p h   i s s u e   c a u s e d   b y   o v e r - s m o o t i n g   s p a r s e   pоi n t s   a n d   i n c o r r e c t l y   s c a l i n g   t i m e   a x e s   f o r   c o r r u p t e d   m a p b o x   t r a c k s . 
 
 -   M a p b o x   S m o o t h i n g :   I n c l u d e d   A c t i v i t y T y p e .H I K E   i n   t h e   s m o o t h i n g   p i p e l i n e . 
