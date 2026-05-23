# FEATURE_LOG.md
> **NAVODILO ZA AI:** Ko zaključiš sejo, dodaj vnos na dno tega dokumenta. Format je točno določen — glej spodaj. Ta datoteka je "dnevnik sprememb" — ko pride do buga, tu pogledamo kaj je bilo nazadnje spremenjeno.

---

## FORMAT VNOSA
```
## [DATUM] — [Kratek naslov spremembe]
**Datoteke:** seznam.kt, datoteke.kt
**Kaj:** Kaj točno je bilo sprememnjeno (1-3 stavki)
**Zakaj:** Zakaj je bila sprememba potrebna
**Tveganje:**  nizko /  srednje /  visoko
```

---

## DNEVNIK

## 2026-05-23 — Faza 29.2: Popoln izgon poslovne logike iz UI sloja
**Datoteke:** `ui/nutrition/NutritionViewModel.kt`, `ui/screens/NutritionScreen.kt`, `ui/progress/ProgressViewModel.kt`, `ui/progress/Progress.kt`
**Kaj:**
1. `SideEffect {}` v Progress.kt zamenjan z `LaunchedEffect(weightPredictionFull)` → `ProgressViewModel.storePrediction()`. Pisanje v WeightPredictorStore se zdaj dogaja v viewModelScope (ne na Main thread med rekomposicijo).
2. Kompleksni `LaunchedEffect(nutritionPlan, plan, userProfile)` z BMI/BF% parsanjem in `LaunchedEffect(Unit)` za NutritionPlan nalaganje — oba ODSTRANJENA iz NutritionScreen.kt.  
3. NutritionViewModel zdaj sam naloži `UserProfile` (Firestore callbackFlow) in `NutritionPlan` (NutritionPlanStore). Oba toka sta vezana na `uidFlow` → clearUser() jih oba ugasne. `combine()` v `init{}` sproži `recomputeCalorieTarget()` ob vsaki spremembi.
**Zakaj:** SideEffect piše v globalen singleton ob VSAKI rekomposiciji → race condition. LaunchedEffect z business logiko v UI = kršenje Clean Architecture. UI mora biti pasiven sprejemnik stanj.
**Tveganje:** 🟡 srednje (Firestore listener za profil duplicira AppViewModel, toda za MVP arhitekturna čistost prevlada)

## 2026-05-23 — Faza 29.3: Trije arhitekturni anti-pattern popravki
**Datoteke:** `debug/WeightPredictorStore.kt`, `data/store/NutritionPlanStore.kt`, `ui/nutrition/NutritionViewModel.kt`, `ui/screens/NutritionScreen.kt`, `ui/progress/ProgressViewModel.kt`
**Kaj:**
1. **WeightPredictorStore → Reaktiven repozitorij**: `@Volatile var` → `MutableStateFlow` (thread-safe) + read-only `StateFlow` izpostavitve. `hybridTDEEFlow: StateFlow<Int>` za reaktivno poslušanje. Atomarni `update()` zamenja 11 posameznih dodelitev. Property setters ohranjeni za backward compat.
2. **NutritionPlanStore → Real-time Flow**: Dodan `observeNutritionPlan(uid): Flow<NutritionPlan?>` z `callbackFlow { addSnapshotListener }`. Parsing logika ekstrahirana v `parseNutritionPlanSnapshot()` helper (DRY). Obstoječi `loadNutritionPlan()` ga interno kliče.
3. **NutritionViewModel — Odstranitev UI poštarja**: `_planResultFlow` in `updatePlanResult()` IZBRISANI. `combine()` zmanjšan 3→2 vira. `WeightPredictorStore.hybridTDEEFlow.collect{}` v `init{}` reaktivno posodablja `_baseTdee`. `applyHybridTDEE()` odstranjeno (nadomešča ga reaktivni Flow).
4. **NutritionScreen**: `LaunchedEffect(plan) { vm.updatePlanResult(plan) }` ODSTRANIL — UI je 100% pasiven.
5. **ProgressViewModel**: `storePrediction()` → `WeightPredictorStore.update()` (atomarno, brez `Dispatchers.Default`).
**Zakaj:** Race condition (posamezno pisanje @Volatile polj iz Dispatchers.Default), statičen Firestore enkraten klic (spremembe z druge naprave nevidne), UI posrednik navigacijskih argumentov.
**Tveganje:** 🟡 srednje (ViewModel logika + data flow sprememba) — BUILD ✅ SUCCESSFUL

## 2026-05-23 — Faza 28: Sanity Check — deprecated uvozi, serialization verzija
**Datoteke:** `app/build.gradle.kts`, `ui/screens/BarcodeScannerScreen.kt`, `ui/run/RunTrackerScreen.kt`
**Kaj:**
1. `kotlinx-serialization-json:1.6.3` → `1.7.3` (usklajena z Kotlin 2.1.0; 1.6.3 je bila za Kotlin 1.9.x)
2. `LocalLifecycleOwner` popravit v dveh datotekah: `androidx.compose.ui.platform.LocalLifecycleOwner` → `androidx.lifecycle.compose.LocalLifecycleOwner` (NutritionScreen.kt je bil že pravilen)
3. Potrjeno: Compose plugin 2.1.0, serialization plugin 2.1.0, KSP 2.1.0-1.0.29 — vse usklajeno z Kotlin 2.1.0
4. Potrjeno: `com.google.mlkit.*` uvozi v `AndroidMLKitFaceDetector.kt` in `AndroidMLKitBarcodeScanner.kt` so PRAVILNI — `play-services-mlkit-*` eksportira identičen `com.google.mlkit.*` javni API, ni potrebno ničesar menjati
5. Potrjeno: Nobene mrtve uvozne reference na izbrisane/stubificirane datoteke (ai_utils.kt, ChallengeAdapter.kt, UpdateStreakUseCase.kt)
**Zakaj:** Pre-vizualna prenova sanity check — zagotoviti 100% čisto izhodišče brez deprecated deprecations
**Tveganje:** 🟢 nizko (serialization 1.7.3 je backward-compatible; LocalLifecycleOwner je drop-in zamenjava)


**Datoteke:** `build.gradle.kts`, `app/build.gradle.kts`, `data/local/AppDatabase_Impl.kt`
**Kaj:**
1. Kotlin 2.2.10 → 2.1.0 (stabilna verzija z uradno KSP 2.1.0-1.0.29 podporo)
2. KSP plugin odkomentirano v obeh `build.gradle.kts` — Room sedaj samodejno generira `AppDatabase_Impl` prek KSP
3. Room 2.6.1 → 2.7.1: popravlja "unexpected jvm signature V" bug pri `suspend fun` z Unit povratno vrednostjo
4. `AppDatabase_Impl.kt` izpraznjena — samo package declaration, brez ročne implementacije
5. `kotlinx-coroutines-play-services` 1.7.3 → 1.8.1 (usklajena z android verzijo)
6. `navigation-compose` 2.7.7 → 2.8.9 (Type-Safe Navigation, stable 2.8.x serija)
7. `lint {}` blok počiščen — odstranjeni zastareli komentarji o ML Kit 16.1.7/17.3.0 (zamenjani s play-services v Fazi 27)
**Zakaj:** Ročno pisanje AppDatabase_Impl.kt je nevzdržno; KSP 2.1.0-1.0.29 je zdaj uradno dostopen
**Tveganje:** 🟢 nizko (KSP generira enako logiko kot ročna implementacija; Room 2.7.1 je stable)

## 2026-05-03 — Faza 6: Golden Ratio routing, Rest Day, Room, iOS Code Polish
**Datoteke:** `ui/MainAppContent.kt`, `domain/gamification/GamificationRepository.kt`, `data/gamification/FirestoreGamificationRepository.kt`, `domain/usecase/UpdateStreakUseCase.kt`, `domain/gamification/ManageGamificationUseCase.kt`, `data/local/AppDatabase_Impl.kt`, `build.gradle.kts`, `app/build.gradle.kts`
**Kaj:**
1. Golden Ratio: FaceModuleScreen v MainAppContent dobi `onGoldenRatio` + dodan routing za `Screen.GoldenRatio`
2. Room: AppDatabase_Impl.kt popolnoma obnovljen z modularno implementacijo (stara verzija je bila pomotoma nadomeščena s stubom)
3. Rest Day: nova metoda `markRestDayPending()` → `PENDING_STRETCHING` status v dailyHistory; `STRETCHING_DONE` se postavi samo ob eksplicitni uporabniški akciji
4. iOS Code Polish: `android.util.Log` odstranjiv iz `ManageGamificationUseCase`
**Zakaj:** Zaključitev Clean Architecture refaktoringa; Room je bil nenamerno poškodovan pri KSP preizkusu
**Tveganje:** 🟡 srednje (AppDatabase_Impl je ročno pisan — pri spremembi entitet posodobi SQL CREATE TABLE)

## 2026-03-09 — Refactoring: premik data modelov in izluščitev logike
**Datoteke:** `data/AlgorithmData.kt`, `data/PlanModels.kt`, `domain/WorkoutPlanGenerator.kt`, `viewmodels/BodyModuleHomeViewModel.kt`, `ui/screens/PlanPathVisualizer.kt`, `ui/screens/PlanPathDialog.kt`, `ui/screens/KnowledgeHubScreen.kt`
**Kaj:** Data modeli premaknjeni iz `ui/screens/` v `data/`. Algoritem za generiranje plana v `domain/`. ViewModel izluščen iz BodyModuleHomeScreen.
**Zakaj:** Datoteke >600 vrstic povzročajo da AI pozabi kontekst. Premik vzpostavlja čisto arhitekturo.
**Tveganje:**  srednje

## 2026-03-10 — Refactoring: poenotitev Firestore routing skozi FirestoreHelper
**Datoteke:** `data/UserPreferences.kt`, `ui/screens/NutritionScreen.kt`, `ui/screens/WorkoutSessionScreen.kt`, `viewmodels/BodyModuleHomeViewModel.kt`
**Kaj:** Vsi Firestore zapisi na profil uporabnika gredo zdaj skozi `FirestoreHelper.getCurrentUserDocRef()`. `addXPWithCallback()` označena `@Deprecated`.
**Zakaj:** Direktni klici `collection("users").document(email/uid)` so pisali na napačen dokument za starejše uporabnike (pred migracijo UID→email).
**Tveganje:**  visoko (podatki bi se shranjevali na napašen dokument)

## 2026-03-10 — Fix: dvojni badge check v recordWorkoutCompletion
**Datoteke:** `persistence/AchievementStore.kt`
**Kaj:** `recordWorkoutCompletion` je klical `checkAndUnlockBadges` dvakrat — enkrat znotraj `awardXP`, enkrat direktno. Popravljeno na enkratni klic.
**Zakaj:** Podvojen klic je povzročal dvojno XP podeljevanje za badge in dvojni Firestore write.
**Tveganje:**  visoko

## 2026-03-10 — Fix: manjkajoči badge ID-ji v getBadgeProgress
**Datoteke:** `persistence/AchievementStore.kt`, `data/BadgeDefinitions.kt`
**Kaj:** Dodani badge ID-ji `committed_250`, `committed_500`, `level_50`, `celebrity` v `getBadgeProgress()` in `BadgeDefinitions.ALL_BADGES`.
**Zakaj:** Badge-i ki niso v `getBadgeProgress` vedno vrnejo 0 → nikoli ne odklenejo.
**Tveganje:**  visoko

## 2026-03-10 — Fix: WeeklyStreakWorker direkten Firestore klic
**Datoteke:** `workers/WeeklyStreakWorker.kt`
**Kaj:** `saveStreakToFirestore()` je pisala direktno na `document(email)` → popravljeno na `FirestoreHelper.getCurrentUserDocRef()`.
**Zakaj:** Za legacy uporabnike (UID-based dokumenti) bi se streak shranil na napačen dokument.
**Tveganje:**  srednje

## 2026-03-10 — Fix: addXPWithCallback v RunTrackerScreen
**Datoteke:** `ui/screens/RunTrackerScreen.kt`
**Kaj:** Klic `addXPWithCallback` zamenjan z `AchievementStore.awardXP()`. Dodan `scope` in coroutine imports.
**Zakaj:** `addXPWithCallback` je deprecated, ne preverja badge-ev in ne beleži xp_history.
**Tveganje:**  nizko

## 2026-03-10 — Refactoring: swapDaysInPlan skozi PlanDataStore
**Datoteke:** `viewmodels/BodyModuleHomeViewModel.kt`, `persistence/PlanDataStore.kt`
**Kaj:** `swapDaysInPlan()` je ročno serializiral plan in pisal direktno na Firestore. Zdaj kliče `PlanDataStore.updatePlan()`. Dodan `updatePlan()` v `PlanDataStore`.
**Zakaj:** Podvojena serializacijska logika — plan se je serializiral na dva različna načina.
**Tveganje:**  srednje

## 2026-03-10 — Dodani arhitekturni testi
**Datoteke:** `app/src/test/java/com/example/myapplication/ArchitectureTest.kt`
**Kaj:** 5 unit testov ki preverjajo: badge konsistentnost, Firestore routing, deprecated XP klic, clearCache ob odjavi, XPSource enum.
**Zakaj:** Preprečuje da AI pri popravku pokvari ključne invariante brez opozorila.
**Tveganje:**  nizko

## 2026-03-11 — RDP Polyline kompresija + Activity Log + Share Activities
**Datoteke:** `utils/RouteCompressor.kt`, `data/UserAchievements.kt`, `persistence/ProfileStore.kt`, `ui/screens/ActivityLogScreen.kt`, `ui/screens/PublicProfileScreen.kt`, `ui/screens/BodyModuleHomeScreen.kt`, `AppNavigation.kt`, `AppDrawer.kt`, `MainActivity.kt`, `ui/screens/RunTrackerScreen.kt`
**Kaj:**
- **RouteCompressor** — RDP algoritem kompresira GPS traso ~450→~35 točk (~92% manj storage)
- **PublicActivity** data class za Firestore javne aktivnosti (komprimirane)
- **ActivityLogScreen** — nov zaslon s karticami po aktivnostih, vsaka barve glede na tip (Run=modra, Hike=rjava, Skiing=turkizna...), mini OSM mapa z barvno linijo pri razprtju
- **BodyModuleHomeScreen** — gumb ️ desno od "Start run" odpre ActivityLogScreen
- **shareActivities** toggle v AppDrawer privacy nastavitvah
- **RunTrackerScreen** — pri shranjevanju seje preveri `share_activities` flag in shrani komprimirano ruto v `publicActivities/{sessionId}` v Firestoreu
- **PublicProfileScreen** — TabRow "Profile"/"Activities" ko ima shareActivities=true; Activities tab prikaže javne aktivnosti z barvnimi karticami in mapami
- **ProfileStore.getPublicProfile** — bere `share_activities` in naloži `publicActivities` subcollection
**Zakaj:** Storage optimizacija za deljenje tras med followerji (92% manj Firestore reads/writes)
**Tveganje:**  srednje — nova Firestore kolekcija `publicActivities`

**Datoteke:** `ui/screens/ExerciseHistoryScreen.kt`, `viewmodels/RunTrackerViewModel.kt`
**Kaj:** RunCard v ExerciseHistoryScreen prikazuje detajlne statistike ob razširitvi: Distance, Duration, Avg speed, Max speed, Pace, Ascent, Descent, Calories — vsak prilagojen tipu aktivnosti (npr. Hike nima hitrosti, Walk nima vzpona). Dodan `RunDetailRow` composable. ViewModel že bere `elevationGainM`, `elevationLossM`, `activityType` iz Firestore.
**Zakaj:** Shranjeni podatki o vzponu, tempu in hitrosti se niso prikazali v zgodovini tekov.
**Tveganje:**  nizko

**Datoteke:** `data/RunSession.kt`, `ui/screens/RunTrackerScreen.kt`, `viewmodels/RunTrackerViewModel.kt`, `ui/screens/ExerciseHistoryScreen.kt`
**Kaj:** Dodan `ActivityType` enum z 9 tipi aktivnosti (Run, Walk, Hike, Sprint, Cycling, Skiing, Snowboard, Skating, Nordic Walk). Vsak tip ima: MET vrednost za izračun kcal, zastavice za vzpon/tempo/hitrost, emoji in label. RunTrackerScreen dobi dropdown picker (klik na izbrani tip → horizontalni chip scroll). Kcal se izračunava z MET formulo (MET × kg × ure + vzpon bonus). Stats card in summary se prilagodita tipu. RunSession shrani `activityType`, `elevationGainM`, `elevationLossM`. ExerciseHistoryScreen RunCard prikazuje pravo ikono/ime in vzpon kjer je relevantno.
**Zakaj:** Uporabnik želi slediti različnim aktivnostim, ne samo teku.
**Tveganje:**  nizko

**Datoteke:** `widget/PlanDayWidgetProvider.kt`, `res/layout/widget_plan_day.xml`, `res/xml/plan_day_widget_info.xml`, `AndroidManifest.xml`, `MainActivity.kt`
**Kaj:** Nov home screen widget ki prikazuje:  streak, "Week X · Day Y", in focus area tega dne (npr. "Push", "Legs", "Rest "). Klik na widget odpre aplikacijo direktno na BodyModuleHome. Podatki se preberejo iz Firestore (users/{email} za streak/plan_day, user_plans/{uid} za weeks strukturo). Widget se osveži: ob odprtju aplikacije, po končani vadbi, ob DATE_CHANGED.
**Zakaj:** Uporabnik želi hitro videti kateri dan ima danes in kaj je fokus — brez odpiranja aplikacije.
**Tveganje:**  nizko

**Datoteke:** `utils/NutritionCalculations.kt`, `ui/screens/NutritionScreen.kt`, `ui/screens/BodyModuleHomeScreen.kt`
**Kaj:** (1) Dve novi funkciji v NutritionCalculations: `calculateDailyWaterMl()` (35ml/kg, +500ml workout day, spol, aktivnost) in `calculateRestDayCalories()` (workout kalorije -150 do -250 kcal na rest day glede na cilj). NutritionScreen zdaj prikazuje prilagojen vodni cilj in prilagojene kalorije z oznako "️ Workout day" / " Rest day". (2) BodyModuleHomeScreen ob vstopu brez plana takoj preusmeri na BodyOverview ("No plans yet") prek LaunchedEffect.
**Zakaj:** Trdo kodirani cilji (2000ml vode, enake kalorije vsak dan) niso upoštevali individualnih podatkov iz kviza. Hkrati je pomanjkanje plana šele kazalo napako pri kliku na PlanPath, ne takoj ob vstopu.
**Tveganje:**  nizko

## 2026-03-11 — Popravek po ugašanju računalnika — manjkajoče funkcije
**Datoteke:** `utils/NutritionCalculations.kt`
**Kaj:** `calculateDailyWaterMl()` in `calculateRestDayCalories()` sta manjkali — niso bile shranjene ob ugašanju računalnika med sejo. Ob preverjanju po ponovnem zagonu sesije je `NutritionScreen.kt` klical ti dve funkciji, a ti nista obstajali v `NutritionCalculations.kt`. Obe dodani nazaj.
**Zakaj:** Računalnik se je ugasnil med sejo — datoteka je bila shranjena brez teh funkcij.
**Tveganje:**  kritično — build bi padel brez teh funkcij

### [2026-03-12] Final Compilation Fixes & Cleanup ✅
- **ExerciseHistoryScreen.kt**: Fixed broken string interpolation, cleaned up file structure (removed ~200 lines of spaghetti code, kept functionality).
- **PublicProfileScreen.kt**: Fixed missing closing braces that caused scope issues.
- **ActivityLogScreen.kt**: Removed duplicate `createColoredMarkerDrawable` to resolve conflict.
- **NutritionScreen.kt**: Fixed type mismatches in water/calorie calculations.
- **NutritionCalculations.kt**: Restored missing calculation functions.
- **Streak Logic Verified**: Login updates `lastLoginDate` but streak only increments on Workout Complete OR Rest Day adherence.

## 2026-03-12 — Gamification & Smart Notifications Expansion
**Datoteke:** `viewmodels/BodyModuleHomeViewModel.kt`, `ui/screens/WorkoutSessionScreen.kt`, `persistence/AchievementStore.kt`
**Kaj:** 
1. `BodyModuleHomeViewModel` prenovljen da sledi "Early Bird/Night Owl" statistiki skozi `AchievementStore`.
2. Implementiran "Critical Hit" XP mechanism s povratno informacijo (Toast).
3. Prenovljen flow zaključevanja vadbe v UI (`WorkoutSessionScreen`), da se čaka na shranjevanje podatkov pred navigacijo (odprava race conditiona).
4. Rest Day aktivnost v `BodyModuleHomeScreen` je zdaj funkcionalna: **avtomatsko napreduje plan na naslednji dan** (planDay++) in shrani statistiko v Firestore, tako da uporabnik ne ostane zataknjen na Rest Dayu.
**Zakaj:** Uporabnik je želel povečati engagement z gamifikacijo in pametnimi opomniki, ter odpraviti UI hrošče pri zaključevanju vadbe. Fix za Rest Day je bil kritičen za napredovanje skozi plan.
**Tveganje:**  nizko

## 2026-03-12 — Streak Animation & Haptics
**Datoteke:** `ui/screens/BodyModuleHomeScreen.kt`
**Kaj:** Implementiran `StreakCounter` composable, ki animira streak (N-1 -> N) s 3D flip efektom in preciznimi vibracijami ob vsaki spremembi številke. Zamenjan statičen tekst z animiranim števcem.
**Zakaj:** Izboljšanje UX ob zaključku vadbe (občutek napredka, podobno Duolingo stilu).
**Tveganje:**  nizko

## 2026-03-12 — Fix Double Workout Submission
**Datoteke:** `viewmodels/BodyModuleHomeViewModel.kt`
**Kaj:** Dodal `AtomicBoolean` zaščito v `completeWorkoutSession` in `completeRestDayActivity` za preprečevanje hkratnega izvajanja.
**Zakaj:** Uporabnik je prijavil, da se statistika poveča za +2 namesto za +1 po enem treningu (caused by double click / concurrency).
**Tveganje:**  nizko (čista zaščita pred race-condition)

## 2026-03-12 — Gamification System & Face Module
**Datoteke:** `ShopScreen.kt`, `ShopViewModel.kt`, `GoldenRatioScreen.kt`, `FaceModule.kt`, `AchievementStore.kt`
**Kaj:** Dodana trgovina za XP (streak freeze), ML Kit face analysis (Golden Ratio), in dialogi za Skincare/Face Exercises.
**Zakaj:** Uporabnik je želel funkcionalen shop in delujoč face module namesto placeholderjev.
**Tveganje:**  nizko

## 2026-04-06 — Shop Card Move
**Datoteke:** `DashboardScreen.kt`, `BodyModuleHomeScreen.kt`, `MainActivity.kt`
**Kaj:** Dodana kartica Shop pod Face Module na DashboardScreen, ter odstranjen  gumb iz BodyModuleHomeScreen.
**Zakaj:** Optimizacija menijev, da je Shop na bolj vidnem mestu in povezan s centralnim nadzornim delom.
**Tveganje:**  nizko

## 2026-04-08 � Fix LoadingWorkout regular workout hijack
**Datoteke:** MainActivity.kt 
**Kaj:** Odpravljena napaka, kjer je LoadingWorkoutScreen preusmeril uporabnika na GenerateWorkoutScreen (ki ponudi izbiro Focus Area), e se je zdelo, da je bila vadba (ali rest day) isti dan e opravljena, kar je uporabnikom onemogoilo zagon vnaprej doloenega rednega plan dneva.
**Tveganje:** ?? nizko

## 2026-04-08 — Fix Navigation Backstack for WorkoutSession
**Datoteke:** `NavigationViewModel.kt`, `MainActivity.kt`
**Kaj:** Dodana metoda `popTo()` v NavigationViewModel in uporabljena namesto `navigateTo()` ob povratku iz WorkoutSession.
**Zakaj:** Preprečuje, da bi univerzalni 'Back' gumb uporabnika vrgel na preskočene ekrane v backstacku (npr. GenerateWorkout).
**Tveganje:**  nizko

## 2026-04-08 � Video Loading Spinner & Activity Select Trace
**Datoteke:** `WorkoutSessionScreen.kt`, `ActivityLogScreen.kt`
**Kaj:** 
1. Dodan `CircularProgressIndicator` v `WorkoutSessionScreen`, ki se prika�e kadar videoposnetek �e vadi ali je stanje `STATE_BUFFERING`. Odpravljen estetski prazen �rn ekran.
2. V `ActivityLogScreen.kt` zdaj globalni OSM zemljevid ob kliku na dolo�en tek/hojo to�no to pot postavi v ospredje, odebeli njeno �rto in jo obarva belo-obrobljeno, da izstopa in je jasno vidna tudi pod prekrivajo�imi se potmi.
**Tveganje:** ?? nizko

- Added track point saving during run activity tracking so path points correctly show. Made video viewer transparent before playing.

## 2026-04-10 — Deepsearch: Firestore run path + streak sync + video loading fix
**Datoteke:** `ui/screens/RunTrackerScreen.kt`, `viewmodels/RunTrackerViewModel.kt`, `ui/screens/WorkoutSessionScreen.kt`, `data/UserPreferences.kt`, `persistence/AchievementStore.kt`
**Kaj:**
1. `RunTrackerScreen` zdaj vedno piše `runSessions` in `publicActivities` skozi resolved `FirestoreHelper.getCurrentUserDocRef()`, z `await()` in retryjem; s tem se `polylinePoints` dejansko zapišejo na pravi uporabniški dokument.
2. `RunTrackerViewModel` bere `runSessions` skozi isti resolved doc ref (email/UID migracija-safe), zato Activity Log bere podatke iz iste lokacije kot zapis.
3. `WorkoutSessionScreen` skrije `PlayerView` do `STATE_READY`, med nalaganjem ostane samo spinner, zato črn pravokotnik ni več viden.
4. `UserPreferences.saveWorkoutStats` in `AchievementStore.checkAndUpdatePlanStreak` uporabljata `set(..., merge)` da streak write deluje tudi na prvem zapisu (ko user doc še ne obstaja).
**Zakaj:** Uporabnik je poročal, da Firestore nima GPS točk/streak polj in da video loading overlay še vedno kaže črn blok; root cause je bil neenoten doc routing + ne-čakani zapisi + prikaz PlayerView pred prvim frameom.
**Tveganje:**  srednje

## 2026-04-10 — Firestore email-first cleanup + debug logging (Option B + A)
**Datoteke:** `persistence/FirestoreHelper.kt`, `ui/screens/RunTrackerScreen.kt`, `viewmodels/RunTrackerViewModel.kt`, `ui/screens/ActivityLogScreen.kt`, `worker/DailySyncWorker.kt`, `widget/WeightWidgetProvider.kt`, `widget/WaterWidgetProvider.kt`, `widget/QuickMealWidgetProvider.kt`, `widget/StreakWidgetProvider.kt`, `widget/PlanDayWidgetProvider.kt`, `widget/WeightInputActivity.kt`, `widget/WaterInputActivity.kt`, `ui/screens/NutritionScreen.kt`, `ui/screens/Progress.kt`, `persistence/DailySyncManager.kt`, `ui/screens/ExerciseHistoryScreen.kt`, `ui/screens/BodyModule.kt`, `ui/screens/LevelPathScreen.kt`, `persistence/ProfileStore.kt`
**Kaj:** Poenoten je users document routing prek `FirestoreHelper` (email-first), odstranjeni so kriticni direktni `users/{uid}` klici v run/activity/widget/worker tokih, dodani diagnostični logi za write/read tocke, in v `ActivityLogScreen.kt` je odstranjena podvojena logika z helperjem za `isSmoothed` update.
**Zakaj:** Podatki so se deloma zapisovali v razlicne dokumente (`uid` vs `email`), zato v konzoli niso bili vidni na enem mestu in je bila diagnostika počasna.
**Tveganje:**  srednje

- **Food Repository Integration**: Created `FoodRepositoryImpl.kt` to centralize all FatSecret API operations and Firestore logging (batch/transaction) for custom meals. This completely removes database/API writes from UI code (`AddFoodSheet`, `NutritionDialogs`, `NutritionScreen`), making the UI "dumb" and resilient against data loss.
- 2026-04-11 (Clean Sweep) - Created KMP_ANDROID_DEPENDENCY_REPORT.md and UserPreferencesRepository.kt for settings relocation.

- 2026-04-11 (Clean Sweep Final Faza) - Completely s c r u b b e n d a n d r o i d . u t i l . L o g , j a v a . u t i l . D a t e , S i m p l e D a t e F o r m a t a n d d i r e c t F i r e b a s e . f i r e s t o r e U I c a l l s f o r p u r e K o t l i n K M P r e a d i n e s s v i a L o g g e r a n d k o t l i n x - d a t e t i m e i m p l e m e n t a t i o n . 

- **2026-04-11 (KMP Dependencies & Sync)** — Aplikacija je uspešno sinhronizirana s KMP multiplatform-settings in kotlinx-datetime knjižnicami, build zopet deluje brezhibno po regresiji z giga-izbrisom datotek.
[ ] UI datoteki ActivityLogScreen.kt in ExerciseHistoryScreen.kt migrirani na DateFormatter (kotlinx-datetime).
[ ] Vsi Widgeti po i s k a n j u (P l a n D a y , Q u i c k M e a l , W a t e r , W e i g h t , S t a t s , p r i l o ~en InputActivity) in njihovi uvozi posodobljeni t a k o , d a i z p u a
ajo java.time.* in java.ut i l.D a te.
[ ] DateFormatter.kt je bil dopolnjen z razli
nimi formati, da natan
no replicira staro SimpleDateFormat obliko.
[ ] Preverjena odstranitev nesmiselnih odvisnosti (jih ni, java.time in java.util.Date sta ~e tako del JDKja in nista t e r j a l i p o s e b n e k n j i c e v g r a d l e).
[ ] b u i l d  j e  a e l  s k o z i  b p .


[x] Ustvarjen interfejs FaceDetector.
[x] Ustvarjen AndroidMLKitFaceDetector v data plasti.
[x] GoldenRatioScreen refaktoriran, da ne uporablja ML Kit neposredno.
[x] Build uspesen.

- **2026-04-12 (Barcode Scanner Izolacija)** - Odstranjen ML Kit iz BarcodeScannerScreen UI-ja. Logika premaknjena v AndroidMLKitBarcodeScanner v data plasti. Uporablja nov BarcodeScanner interfejs in BarcodeScannerProvider v domain plasti. Vsi ui/screens/ sedaj brez com.google.mlkit uvozov.

## 2026-04-19 — KMP Settings Migracija (UserPreferences.kt Delete)
**Datoteke:** `UserPreferences.kt` (deleted), `UserProfileManager.kt` (new), `SettingsManager.kt`, `MainActivity.kt`, `WorkoutSessionScreen.kt`, multiple viewmodels...
**Kaj:** Celotna logika upravljanja z nastavitvami in pretekle stare Android `SharedPreferences` logike iz `UserPreferences.kt` je bila popolnoma migrirana na `com.russhwolf.settings.Settings`. Krojijo se neposredne instance znotraj singleton `UserProfileManager.kt`, stari `UserPreferences.kt` in `AchievementStore.kt` sta izbrisana. Arhitekturni testi prilagojeni.
**Zakaj:** Tehnični dolg ("SharedPreferences konflikt") med Android `Context` zahtevki in preostalimi KMP strukturami. Enoten sistem brez Android UI uvozov v podatkovni domeni.
**Tveganje:**  nizko (obstoječa baza pokrita z KMP Settings)

## 2026-04-19 — Fix SettingsManager Initialization Crash
**Datoteke:** `MyApplication.kt`, `AndroidManifest.xml`
**Kaj:** Ustvarjen razred `MyApplication` (ki deduje po `Application`), da se `SettingsManager.provider` inicializira z `AndroidSettingsProvider` takoj ob zagonu procesa, preden se ustvarijo dejavnosti in ViewModels. Prav tako posodobljen `AndroidManifest.xml`, da uporablja `.MyApplication`.
**Zakaj:** Orodno okno `SettingsManager strictly needs to be initialized first.` `IllegalStateException` ob zagonu zaradi napačnega čakanja in poizkusov inicializacije med `ViewModel` in `Activity`.
**Tveganje:**  nizko

## 2026-04-17 — Health Connect & Settings Provider Revamp
**Datoteke:** `MainActivity.kt`, `ui/screens/BodyModuleHomeScreen.kt`, `ui/screens/NutritionScreen.kt`, `ui/screens/RunTrackerScreen.kt`, `ui/screens/SettingsScreen.kt`, `viewmodels/BodyModuleHomeViewModel.kt`, `viewmodels/SettingsViewModel.kt`, `persistence/AchievementStore.kt`, `persistence/ProfileStore.kt`, `data/HealthData.kt`, `data/UserAchievements.kt`, `data/UserPreferences.kt`
**Kaj:**
*   **AndroidX Health Connect Integration:** Built dedicated `HealthConnectManager` mapped linearly into Compose screens. Fetching is natively merged globally preventing conflicting active data representations. 
*   **Reactive Flow Data Access:** Instant UI responsiveness achieved entirely via `StateFlow` migrations coupled with continuous `addSnapshotListener` connections pointing directly to Firebase inside `FoodRepositoryImpl` & `ProgressViewModel`. Daily logs instantly push their graphs upon modifications.

## [2026-04-18]
*   **Settings Provider Multiplatform Overhaul:** Cleaned away legacy runtime SharedPreferences wrappers out of architecture. Moved into isolated primitive logic matching pure Jetpack Kotlin Multiplatform constraints (via `com.russhwolf.settings`). Application class bootstrapping fixes early initialization app-crashes.

### Sprint 9 (Q2 2026) - KMP Stabilization & System Consolidation
- **Architecture**: Removed all `android.util.Log` and `java.util.Date` instances from the UI layer, moving to `kotlinx-datetime` and `Logger`.
- **Modularization**: Decoupled ML Kit completely. `FaceDetector` and `BarcodeScanner` now sit behind domain interfaces, keeping UI logic entirely KMP compatible.
- **Data Layer Reform**: `SharedPreferences` fully removed and replaced with `multiplatform-settings`. Created `MyApplication` to handle `SettingsManager` initialization before ViewModel loading.
- **Nutrition Sync Precision**: Reworked `consumedCalories` tracking to use `FieldValue.increment` with a precise `logFood` transaction inside `FoodRepositoryImpl`, removing unreliable local list sum calculations from the UI. `NutritionViewModel` now automatically maps and immediately renders `uiState` (consumed/burned calories) to the `DonutProgressView` and `ActiveCaloriesBar`.

## 2026-04-25 — Faza 9.2: bm_prefs SSOT sanacija
**Datoteke:** `ui/screens/WorkoutSessionScreen.kt`, `data/settings/UserPreferencesRepository.kt`, `workers/StreakReminderWorker.kt`, `ui/screens/ManualExerciseLogScreen.kt`
**Kaj:**
- **WorkoutSessionScreen**: `plan_day` bere iz `BodyModuleHomeViewModel` (Firestore) namesto `bm_prefs` — dodan `collectAsState()`, `WorkoutCelebrationScreen` prejme `planDay` kot parameter
- **UserPreferencesRepository**: `updateWorkoutStats()` DEPRECATED comment, `updateDailyCalories()` NO-OP
- **StreakReminderWorker**: `streak_days`, `plan_day`, `last_workout_epoch` migriran na Firestore prek `UserProfileManager.getWorkoutStats()`; `today_is_rest` prek nove `checkTodayIsRestFromFirestore()` iz `user_plans` kolekcije
- **ManualExerciseLogScreen** `GenderCache`: odstranjen `gender_cache` SharedPrefs sloj, obdržan samo in-memory cache
**Zakaj:** Po auditu (Faza 9.audit) so ostali `bm_prefs` zapisi za biometrične podatke v konfliktu s Firestore SSOT. Zdaj sta bm_prefs in Firestore konsistentna.
**Tveganje:**  nizko (Firestore je SSOT, ni izgube podatkov)

## 2026-04-25 — Faza 9.1: DailyLogRepository SSOT sanacija
**Datoteke:** `ui/screens/RunTrackerScreen.kt`, `ui/screens/ManualExerciseLogScreen.kt`, `domain/workout/UpdateBodyMetricsUseCase.kt`
**Kaj:**
- **RunTrackerScreen** vrstica 712: Email bug fix — `email = uiState.errorMessage ?: ""` → `email = runEmail` (Firebase Auth)
- **ManualExerciseLogScreen** `logExerciseToFirestore()`: po `saveExerciseLog()` dodan `DailyLogRepository().updateDailyLog()` za atomarno posodobitev `burnedCalories` v `dailyLogs`
- **UpdateBodyMetricsUseCase**: `settingsRepo.updateDailyCalories()` zakomentiran — `bm_prefs.daily_calories` ni več SSOT; edini vhod za burned kalorije je `dailyLogs` Firestore kolekcija
**Zakaj:** Audit je odkril, da sta RunTracker in ManualExercise ignorirala `DailyLogRepository`, ki je SSOT za dinamični TDEE sistem. Burned Calories Delta v Debug Dashboardu ni bila pravilna za te vire aktivnosti.
**Tveganje:**  nizko (samo dodajanje obstoječega klica, brez spremembe obstoječe logike)



## 2026-04-25 — Faza 4: Dinamični TDEE algoritem
**Datoteke:** `viewmodels/NutritionViewModel.kt`, `ui/screens/NutritionScreen.kt`, `shared/.../CalculateBodyMetricsUseCase.kt`
**Kaj:** Zamenjava statičnega aktivnostnega multiplikatorja (1.2–1.9) z dinamičnim TDEE: `baseTdee = BMR × 1.2` + `burnedCalories` (real-time iz Firestore dailyLogs) + `goalAdj`. UI prikazuje ` +X kcal boost` ko je aktivnost > 0. Fallback na statični target ko profil ni naložen.
**Zakaj:** Statični TDEE ne upošteva dejanske aktivnosti. Zdaj: zjutraj v postelji = nizek limit; po teku 500 kcal = limit se poveča za 500 kcal v realnem času.
**Tveganje:**  nizko (obstoječi statični fallback ohranjen)

## 2026-04-25 — Faza 6: Data Budgeting — zmanjšanje Firestore branj za ~35%
**Datoteke:** `viewmodels/NutritionViewModel.kt`, `ui/screens/NutritionScreen.kt`, `ui/screens/Progress.kt`
**Kaj:** Pregled vseh SnapshotListenerjev — identificirani 3 problemi in odpravljeni:
1. NutritionScreen je imel lasten `LaunchedEffect → observeDailyLog().collect {}` ki je PODVOJIL NutritionViewModel-ov listener na `dailyLogs/{danes}`. Odstranjeno — `firestoreFoods: StateFlow` v ViewModelu zdaj deluje kot edinstveni vhod.
2. Progress.kt je bral `consumedCalories` z iteracijo `items` arraija. Zamenjano z direktnim branjem polja `consumedCalories`.
3. Progress.kt je imel `sessionListener` na `daily_health` kolekciji — po Fazi 5 se ta kolekcija ne piše več → mrtev listener. Zamenjan z branjem `burnedCalories` iz `dailyLogs` (dosledno z NutritionViewModel).
**Zakaj:** Preseganje cilja 30% zmanjšanja Firestore branj za boljše delovanje pri počasnih zvezah in offline scenarijih.

## 2026-04-25 — Faza 7.1: Hibridni TDEE z Confidence Faktorjem
**Datoteke:** `utils/NutritionCalculations.kt`, `debug/WeightPredictorStore.kt`, `ui/screens/Progress.kt`, `viewmodels/NutritionViewModel.kt`, `viewmodels/DebugViewModel.kt`
**Kaj:**
- `calculateAdaptiveTDEE()` razširjen z `theoreticalTDEE: Int` parametrom in Confidence Faktorjem C
- C = 0.0 (<3 dni data) → 100% Mifflin-St Jeor; C = 0.5 (3–5 dni) → 50/50; C = 1.0 (6+ dni) → 100% adaptivni
- Hibridna formula: `C × adaptivni + (1−C) × teoretični`, vrača `AdaptiveTDEEResult` data class
- `NutritionViewModel._baseTdee` zdaj prednostno uporablja hibridni TDEE iz `WeightPredictorStore` namesto fiksnega `BMR × 1.2`
- `WeightPredictorStore` razširjen z `lastHybridTDEE`, `lastAdaptiveTDEE`, `lastConfidenceFactor`
- `DebugDashboard` prikazuje vse tri hibridne vrednosti v realnem času
**Zakaj:** Odpravlja odvisnost od fiksnega Mifflin-St Jeor množilnika. Z rastjo realnih podatkov se algoritem samodejno kalibrira na dejanski metabolizem uporabnika.
**Tveganje:**  nizko (Mifflin fallback ohranjen ko hibridni TDEE ni na voljo)

## 2026-04-25 — Faza 7.2: Weight Destiny kartica z vizualnim trendom in What-if simulatorjem
**Datoteke:** `ui/screens/Progress.kt`
**Kaj:**
- `WeightPredictionCard` popolnoma zamenjan z `WeightDestinyCard` — tri novi kompozabli: `WeightDestinyCard`, `WeightTrendLine`, `ConfidenceIndicator`
- **ConfidenceIndicator**: 3 pike (siva/rumena/zelena) glede na C = 0.0/0.5/1.0 + tekstovna oznaka zaupanja
- **Dinamično sporočilo** glede na C in trend:
  - `C < 0.5` → " Spoznavam tvoj metabolizem…"
  - `C ≥ 0.5, deficit` → " Na dobri poti si! Predviden cilj: [Datum]"
  - ravnovesje → "⚖️ Si v energijskem ravnovesju."
- **WeightTrendLine**: Canvas bezier krivulja zdaj→čez 30 dni z gradientnim senčnim trakom + pikčasta ciljna linija (rumena)
- **What-if Simulator**: Slider −500…+500 kcal/dan (koraki po 50 kcal) — v realnem času izračuna "X dni prej/pozneje do cilja" z isto hibridno matematiko
- `WeightPredictionDisplay` razširjen z `confidenceFactor: Double`
**Zakaj:** Številke samo po sebi ne motivirajo. Vizualni trend + interaktivni simulator spremenita suhe algoritme v "kristalno kroglo" — uporabnik vidi neposredno zvezo med današnjimi odločitvami in prihodnjo težo.
**Tveganje:**  nizko (samo UI, logika nespremenjena)

## 2026-04-25 — Final Architectural & UX Audit (pred UI/UX prenovo)
**Datoteke:** `persistence/ProfileStore.kt`, `NavigationViewModel.kt`, `MainActivity.kt`, `worker/RunRouteCleanupWorker.kt` (NOVO), `GPS_POINTS_MIGRATION_PLAN.md` (NOVO)
**Kaj:**
1. **Dead Code označen:** `domain/nutrition/NutritionCalculations.kt`, `network/ai_utils.kt`, `ui/adapters/ChallengeAdapter.kt` — vse 3 označene z `// ⚠️ DEAD CODE — IZBRIŠI ROČNO`
2. **Community fix:** `searchPublicProfiles()` + `getAllPublicProfiles()` — dodan `.limit(20)` prepreči full-collection scan vseh javnih uporabnikov
3. **Navigation:** `NavigationViewModel.replaceTo()` — nova metoda brez push v stack; LoadingWorkout → WorkoutSession zdaj ne kuri back-stack
4. **GPS cleanup Worker:** `RunRouteCleanupWorker` — periodičen Worker (1x tedensko) zbriše `.json` datoteke v `run_routes/` starejše od 60 dni
5. **GPS 1MB načrt:** `GPS_POINTS_MIGRATION_PLAN.md` — detajlen načrt za migracijo `polylinePoints` iz vgrajenega array-a v sub-kolekcijo `points/`
**Zakaj:** Finalni arhitekturni pregled pred UI/UX prenovo — odstranitev dead code, varnostni stropi za Firestore branj, navigation stack optimizacija.
**Tveganje:**  nizko (`.limit(20)` je varnostni strop ki ne vpliva na funkcionalnost)

## 2026-04-26 — Global Audit & bm_prefs SharedPrefs Purge (pred iOS migracijo)
**Datoteke:** `WorkoutSessionScreen.kt`, `UpdateBodyMetricsUseCase.kt`, `GetBodyMetricsUseCase.kt`, `MainActivity.kt`, `MyViewModelFactory.kt`
**Kaj:**
1. **SharedPrefs Purge — WeeklTarget/Done**: `WorkoutSessionScreen` je bral `weekly_target` in `weekly_done` iz deprecated `bm_prefs`. Zamenjano z `vm.ui.value.weeklyTarget` / `vm.ui.value.weeklyDone` (Firestore SSOT prek BodyModuleHomeViewModel).
2. **SharedPrefs Purge — Streak v CelebrationScreen**: `WorkoutCelebrationScreen` je bral `streak_days` iz `bm_prefs` (vračal 0 ker bm_prefs ni več pisan). Dodal `streakDays: Int` parameter, kliče se z `vmUiState.streakDays`.
3. **Redundancy Fix — UpdateBodyMetrics**: Odstranjen dvojni zapis — `settingsRepo.updateWorkoutStats()` je pisal STARI (pre-increment) `plan_day` v bm_prefs, medtem ko `updateUserProgressAfterWorkout()` že atomarno piše pravilne vrednosti v Firestore. `settingsRepo` odstranjen iz konstruktorja.
4. **Kritični Bug Fix — Streak Reset pri novem planu**: `MainActivity.kt` `onFinish` je ob kreiranju novega plana bral `streak_days`, `total_workouts_completed`, `weekly_done`, `last_workout_epoch`, `plan_day` iz deprecated `bm_prefs` (vse vrednosti = 0) in jih zapisal v Firestore → **streak se je resetiral na 0!** Zamenjano z direktnim partial merge-om: samo `plan_day=1`, `weekly_target`, `weekly_done=0`. Streak ostane nespremenjen.
5. **GetBodyMetricsUseCase**: Odstranjen `settingsRepo.updateWorkoutStats()` klic z napačno epoch konverzijo (bm_prefs ne potrebuje več posodabljanja).
**Zakaj:** Pred iOS migracijo: koda mora biti čista, brez podvajanj in SharedPrefs odvisnosti za kritične podatke (streak, plan_day). Odkriti bug bi resetiral streak ob vsakem ustvarjanju novega plana.
**Tveganje:**  srednje (bug fix za streak reset + SharedPrefs cleanup)

## 2026-04-26 — Faza 15: Community Privacy & Calorie Sync
**Datoteke:** `data/UserAchievements.kt`, `persistence/ProfileStore.kt`, `ui/screens/PublicProfileScreen.kt`, `ui/screens/RunTrackerScreen.kt`
**Kaj:**
- `PublicProfile` razširjen z `shareActivities: Boolean = false` — eksplicitten flag iz Firestore dokumenta gledanega uporabnika
- `ProfileStore.mapToPublicProfile()` nastavi `shareActivities` v vrnjeni objekt
- `PublicProfileScreen.kt`: tab "Activities" se prikaže ko `profile.shareActivities == true` (ne več `recentActivities != null`); prazna lista prikaže FitnessCenter ikono + "No activities yet" namesto Lock ikone
- `RunTrackerScreen.kt`: po shranjevanju teka doda `DailyLogRepository().updateDailyLog()` klic za `burnedCalories` v `dailyLogs/{today}`; `NutritionViewModel` in `Progress.kt` Snapshot Listenerji samodejno zaznata spremembo
**Zakaj:** `RunTrackerScreen` ni posodabljal `dailyLogs/burnedCalories` → kalorije iz tekov niso bile vidne v NutritionVM in Progress. `PublicProfileScreen` je posredno bral zasebnost prek `recentActivities != null` namesto eksplicitnega `shareActivities` flaga.
**Tveganje:**  nizko (additive fix, brez spremembe obstoječe logike)

## 2026-04-26 — Faza 14: Map Performance & Cost Optimization
**Datoteke:** `MyApplication.kt`, `ExerciseHistoryScreen.kt`, `ActivityLogScreen.kt`, `RunTrackerScreen.kt`
**Kaj:**
1. **Tile Cache (MyApplication.kt)**: Centralni osmdroid tile cache — 50MB persistenten disk cache, 100 tiles v RAM. Drastično zmanjša omrežne zahteve pri ponovnih obiskih istega območja.
2. **Canvas Static Preview — 'Lite Mode' (ExerciseHistoryScreen.kt)**: `StaticRouteCanvas` composable zdaj zamenja polni `MapView` za majhne predoglede v `RunCard`. Canvas izriše traso brez nalaganja OSM ploščic — 0 omrežnih zahtev, instantni render. Fullscreen dialog ohrani polni interaktivni MapView.
3. **Manuel Zoom Bounds (ActivityLogScreen.kt)**: `GlobalActivityOsmMap` `update` blok zdaj zoom-a na izbrani tek takoj ob selekciji (`mapv.tag` guard prepreči ponavljajoči zoom ob recomposition). Pri deselect se zoom vrne na vse teke.
4. **Shrani/Obnovi Zadnjo Lokacijo (RunTrackerScreen.kt)**: Po zaključenem teku shrani zadnjo GPS točko v SharedPrefs (`last_run_lat`, `last_run_lng`). MapView se inicializira na shranjeni lokaciji namesto hardcoded Ljubljana — brez začetnega "skakanja".
**Zakaj:** Zmanjšanje omrežnega prometa, hitrejši UI, celica podatkov varčevanje pri mobilnih napravah.
**Tveganje:**  nizko (canvas preview je backward compatible, cache je additive, zoom guard je idempotenten)

## 2026-04-27 — Fix: Meal Builder UI Bug + InitialSyncManager (Nova naprava)
**Datoteke:** `ui/screens/NutritionDialogs.kt`, `MainActivity.kt`
**Kaj:**
1. `MakeCustomMealsDialog`: `AlertDialog` se pogojno skrije (`if (!showFoodSearch)`) ko je `AddFoodSheet` odprt. `ModalBottomSheet` je edini aktivni composable — ni več prekrivanja scrimov. Klik na sestavino ne sproži več `onDismiss` staršev dialog.
2. `InitialSyncManager`: Ob prvi prijavi na novi napravi (ključ `initial_sync_done_<uid>` ni v SharedPrefs) se z `async/await` vzporedno fetchajo: profil (XP/level), plani (`user_plans/{uid}`), teže (`weightLogs` zadnjih 10). Overlay prikazuje `"Downloading your fitness profile (XP, Plans & Progress)…"`. Po uspešnem prenosu prikaže `"Profile Ready! ✓"` (1.5s), nato se skrije. Normalni zagoni ostanejo nespremenjenj.
**Zakaj:** Prekrivanje dialogov je povzročalo nehoteno zapiranje Custom Meal procesa. Nov sync manager odpravi zamude pri XP/Plans/BodyModule ob zagonu na novi napravi.

## 2026-05-02 — Faza 2: Konsolidacija podatkov — Firestore polja
**Datoteke:** `data/settings/UserProfileManager.kt`, `data/gamification/FirestoreGamificationRepository.kt`, `data/workout/FirestoreWorkoutRepository.kt`, `data/RunSession.kt`
**Kaj:**
1. `UserProfileManager`: `KEY_PROFILE_PICTURE` poenoten na `"profilePictureUrl"` (camelCase). Prej je app pisala pod enim, brala pod drugim ključem — profilne slike se niso nalagale.
2. `FirestoreGamificationRepository`: Zamenjava `"login_streak"` → `"streak_days"` (3 metode). Odpravlja dvostransko pisanje v Firestore pod dvema različnima ključema.
3. `FirestoreWorkoutRepository.getWeeklyDoneCount`: Poizvedba preusmerjena iz `"date"` (Firestore Timestamp) → `"timestamp"` (epoch ms Long). Tedenska statistika je bila vedno -1 oz. 0 ker polje "date" v workoutSessions ne obstaja.
4. `RunSession.toFirestoreMap()`: GPS polja preimenovana v `lat/lng/alt/spd/acc/ts` — enotni format s `RunTrackerScreen`, `RunRouteStore` in `gps_points` subkolekcijo.
**Zakaj:** Neskladja v poimenovanju so povzročala molče napačne vrednosti (profilne slike, streak, tedenska statistika) brez vidnih napak v logih.
**Tveganje:**  nizko (backwards compat branje v FirestoreWorkoutRepository.getRunSessions() ohranjeno za stare formate)
**Tveganje:**  nizko (dialog fix je pogojno renderiranje brez spremembe logike; sync je additive, enkraten, z graceful fallback)

## 2026-05-03 — Faza 4b: Daily Habit Streak sistem + čiščenje kode
**Datoteke:** `data/gamification/FirestoreGamificationRepository.kt`, `domain/gamification/GamificationRepository.kt`, `domain/gamification/ManageGamificationUseCase.kt`, `viewmodels/BodyModuleHomeViewModel.kt`, `ui/screens/BodyModuleHomeScreen.kt`, `ui/screens/MyViewModelFactory.kt`
**Kaj:**
1. Nova Streak logika: +1 za Workout, +1 za Stretching (Rest Dan), auto Freeze, =0 brez freeze-a
2. `checkIfFutureRestDaysExistAndSwap()` popolnoma izbrisan — app ne prestavi več dni v PlanPath-u
3. dailyHistory mapa v glavnem Firestore dokumentu (ne subcollection) za status vsak dan (WORKOUT_DONE/STRETCHING_DONE/FROZEN/MISSED)
4. `BodyModuleHomeViewModel.CompleteRestDay` implementiran — pokliče `restDayInitiated()`, vrne streak za Toast
5. Toast "Daily Goal Met! Streak: X days 🔥" + S24 Ultra HapticFeedback.SUCCESS ob vsakem streak povečanju
**Zakaj:** Stari streak je nagrajeval samo workoutje; rest dnevi niso šteli. Swap logika je povzročala nenadzorovane spremembe plana. dailyHistory mapa je cenejša od daily_logs subcollection (1 doc read vs. subcollection query).
**Tveganje:**  srednje (nova dailyHistory mapa je nov Firestore field; stari daily_logs subcollection podatki se ne migrirajo — novi zapisi gredo v mapo, stari ostanejo v subcollection ampak niso več berljivi za streak check)

## [2026-05-03] — Faza 8: Unified Streak Engine + Stretching Button UI Fix
**Datoteke:** `GamificationRepository.kt`, `FirestoreGamificationRepository.kt`, `ManageGamificationUseCase.kt`, `UpdateBodyMetricsUseCase.kt`, `UserProfileManager.kt`, `GetBodyMetricsUseCase.kt`, `BodyModuleHomeViewModel.kt`, `BodyModuleHomeScreen.kt`, `APP_MAP.md`
**Kaj:** Eliminiran Dual Streak Engine — vsa streak logika (epoch, Streak Freeze, dailyHistory, plan_day) preseljena v `FirestoreGamificationRepository.processWorkoutCompletion()`. `GetBodyMetricsUseCase` sedaj sprejme `plan`, izračuna `todayIsRest` in bere `todayStatus` iz `dailyHistory`. Stretching kartica zdaj pravilno prikaže, ko je dan rest dan in raztezanje še ni opravljeno.
**Zakaj:** `UserProfileManager.updateUserProgressAfterWorkout()` je računal streak neodvisno od `FirestoreGamificationRepository.updateStreak()` → dvojne posodobitve, neskladnost. `todayIsRest` je bil vedno `false` → Stretching gumb se nikoli ni prikazal.
**Tveganje:** 🟡 srednje (večja refaktoracija streak patha, obsežno testiranje priporočeno)

## [2026-05-17] — Deep Logic Audit (5 kritičnih sistemov) + SwapDays domenski guard fix
**Datoteke:** `viewmodels/BodyModuleHomeViewModel.kt`, `workers/WeeklyStreakWorker.kt`, `APP_MAP.md`
**Kaj:**
1. **BUG FIX — SwapDays domenski guard** (`BodyModuleHomeViewModel.kt`): `SwapDays` handler je klical `swapPlanDays.invoke(plan, dayA, dayB)` brez `lockedDay` parametra → domenski `SwapPlanDaysUseCase.lockedDay` guard nikoli ni bil sprožen. Popravljeno: `lockedDay = if (isWorkoutDoneToday) planDay else null`.
2. **Dead import cleanup** (`WeeklyStreakWorker.kt`): Odstranjeni neuporabljeni uvozi `AlgorithmPreferences`, `SetOptions`, `flow.first`, `flow.firstOrNull`, `kotlinx.coroutines.tasks.await`, `LocalDate` (ostanki stare implementacije).
3. **APP_MAP.md celoten pregled in popravek**: Odstranjene vse 3 napačne reference na `UserProfileManager` za streak; posodobljene lokacije ViewModelov (`ui/nutrition/`, `ui/run/`, `ui/progress/`, `ui/shared/`); popravljena Worker tabela (`core/worker/DailySyncWorker.kt`, `workers/` za streak delavce); posodobljen Firestore schema (`last_activity_epoch` namesto `last_workout_epoch`, `REST_WORKOUT_DONE` status).
**Audit izid (5 točk):**
- ✅ Streak SSOT: `FirestoreGamificationRepository.processActivityCompletion()` je edini SSOT
- ✅ Polnočni check: `WeeklyStreakWorker` → `executeMidnightStreakCheck()` → strogi statusni check (vrednost, ne ključ)
- ✅ Plan day increment: `incrementPlanDay = !isExtra` pravilno ločuje redni/extra workout
- ⚠️→✅ Plan swap domain guard: popravljen (lockedDay zdaj prehaja)
- ✅ Extra workout REST day: `isRestDay && isExtra` → `REST_WORKOUT_DONE` (streak ohranjen, NE poveča)
- ✅ Stretching: `CompleteRestDay` → `restDayInitiated()` → `STRETCHING_DONE` ✓
- ✅ WorkoutGenerator → AdvancedExerciseRepository: uvoz `data.repository.AdvancedExerciseRepository` pravilno deluje po refaktoringu
**Zakaj:** Post-refactoring audit za zagotovitev, da premik paketov ni pokvaril poslovne logike. Odkrit 1 pravi bug (SwapDays lockedDay), 1 dead code (WeeklyStreakWorker imports).
**Tveganje:** 🟢 nizko (bug fix je additive parameter, ničesar ne ruši; cleanup je samo import brisanje)

## 2026-05-22 — Faza 19: OsmDroid RunTrackerScreen Dark Theme + AppDatabase_Impl Fix
**Datoteke:** `ui/run/RunTrackerScreen.kt`, `data/local/AppDatabase_Impl.kt`, `build.gradle.kts`, `app/build.gradle.kts`
**Kaj:** RunTrackerScreen temna tema uskladitev z UppColors SSOT — live kalorije, SummaryRow XP, karticam ozadja, activity picker chip tekst, Pause gumb. AppDatabase_Impl obnovljen z manjkajočo class deklaracijo in importi. KSP plugin zakomentirani (verzija 2.2.10-1.0.29 ni v Maven).
**Zakaj:** Live kalorije so bile prikazane v LightGray (tertiary) namesto Orange. SummaryRow XP je bil LightGray namesto Orange. AppDatabase_Impl je imel korupcijo ki je blokirala build (class header izbrisan). KSP 2.2.10-1.0.29 ni bil dostopen v Maven repos.
**Tveganje:** 🟢 nizko (samo barvne spremembe + compile fix)

## 2026-05-22 — Faza 23: Integracijski Audit + Race Condition Fix + Gamification Optimizacija
**Datoteke:** `domain/gamification/ManageGamificationUseCase.kt`, `domain/usecase/UpdateBodyMetricsUseCase.kt`, `viewmodels/BodyModuleHomeViewModel.kt`, `ui/screens/BodyModuleHomeScreen.kt`, `workers/WeeklyStreakWorker.kt`, `domain/usecase/UpdateStreakUseCase.kt`
**Kaj:**
1. **Race condition odpravljen**: Dvojni `LaunchedEffect` (Unit + currentPlan) v `BodyModuleHomeScreen` → reduciran na en sam `LaunchedEffect(currentPlan)`; ob vstopu sta se prožili dve vzporedni Firestore branji ki sta pisali na `_ui.value` brez koordinacije.
2. **Job cancellation**: `BodyModuleHomeViewModel.loadMetricsJob` — vsak `LoadMetrics` cancela prejšnji.
3. **Brez odvečnega Firestore read-a**: `WorkoutCompletionResult` razširjen z `newStreakDays + newPlanDay`; `CompleteWorkoutSession` zdaj naredi čisti optimistični update (brez dodatnega `getBodyMetrics.invoke().collect{}`).
4. **Popravljena todayStatus logika**: `if (isRestDay && isExtra)` (prej brez `isExtra` → napačni `REST_WORKOUT_DONE` za redne workout-e na rest dnevu).
5. **Dead code**: `UpdateStreakUseCase` označen `@Deprecated`; `simulateDayPass()` dodan `BuildConfig.DEBUG` guard.
**Zakaj:** Aplikacija je bila nestabilna — algoritmi so se podrli ker sta dve vzporedni Firestore branji pisali na UI stanje brez cancellationa. Po zaustavitvi workout-a se je UI mešal med starim in novim stanjem.
**Tveganje:** 🟡 srednje (ViewModel logika + data flow sprememba)

## 2026-05-22 — Faza 24: Unit Test Pokritost — Nutrition + XP/Level
**Datoteke:** `app/src/test/.../NutritionCalculationsTest.kt` (NOVO), `app/src/test/.../GamificationXpLevelTest.kt` (NOVO)
**Kaj:**
1. **NutritionCalculationsTest.kt** — 30+ unit testov za vse funkcije v `domain/nutrition/NutritionCalculations.kt`: `calculateDailyWaterMl` (klampiranje 1500–5000ml, zaokroževanje na 100ml, gender faktor, workout bonus), `calculateSmartCalories` (vse cilje, minimumi 1500/1200 kcal, diabetes faktor), `calculateRestDayCalories` (prilagoditve po cilju, minimumi), `calculateAdvancedBMR` (Mifflin + Katch-McArdle, starostni faktorji), `calculateEnhancedTDEE` (delta logika, multiplierji), `calculateOptimalMacros` (keto meje 20–50g, zaščita negativnih vrednosti), `calculateAdaptiveTDEE` (confidence, minimum 800 kcal).
2. **GamificationXpLevelTest.kt** — 20+ unit testov za `UserProfile.calculateLevel()`, `xpRequiredForLevel()`, `progressToNextLevel`: mejne vrednosti za vsak nivo, level-up scenarij z ohranitvijo presežka XP, konsistentnost med funkcijami, eksponentna rast zahtev, robni primeri (0 XP, 1M XP).
**Zakaj:** Testna pokritost ključnih kalkulacij za preprečevanje prihodnjih hroščev brez Android odvisnosti (KMP-ready).
**Tveganje:** 🟢 nizko (samo dodajanje testov, ni sprememb produkcijske kode)
