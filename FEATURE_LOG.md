# FEATURE_LOG.md
> **NAVODILO ZA AI:** Ko zaključiš sejo, dodaj vnos na dno tega dokumenta. Format je točno določen — glej spodaj. Ta datoteka je "dnevnik sprememb" — ko pride do buga, tu pogledamo kaj je bilo nazadnje spremenjeno.

---

## FORMAT VNOSA
```
## [DATUM] — [Kratek naslov spremembe]
**Datoteke:** seznam.kt, datoteke.kt
**Kaj:** Kaj točno je bilo sprememnjeno (1-3 stavki)
**Zakaj:** Zakaj je bila sprememba potrebna
**Tveganje:** 🟢 nizko / 🟡 srednje / 🔴 visoko
```

---

## DNEVNIK

## 2026-03-09 — Refactoring: premik data modelov in izluščitev logike
**Datoteke:** `data/AlgorithmData.kt`, `data/PlanModels.kt`, `domain/WorkoutPlanGenerator.kt`, `viewmodels/BodyModuleHomeViewModel.kt`, `ui/screens/PlanPathVisualizer.kt`, `ui/screens/PlanPathDialog.kt`, `ui/screens/KnowledgeHubScreen.kt`
**Kaj:** Data modeli premaknjeni iz `ui/screens/` v `data/`. Algoritem za generiranje plana v `domain/`. ViewModel izluščen iz BodyModuleHomeScreen.
**Zakaj:** Datoteke >600 vrstic povzročajo da AI pozabi kontekst. Premik vzpostavlja čisto arhitekturo.
**Tveganje:** 🟡 srednje

## 2026-03-10 — Refactoring: poenotitev Firestore routing skozi FirestoreHelper
**Datoteke:** `data/UserPreferences.kt`, `ui/screens/NutritionScreen.kt`, `ui/screens/WorkoutSessionScreen.kt`, `viewmodels/BodyModuleHomeViewModel.kt`
**Kaj:** Vsi Firestore zapisi na profil uporabnika gredo zdaj skozi `FirestoreHelper.getCurrentUserDocRef()`. `addXPWithCallback()` označena `@Deprecated`.
**Zakaj:** Direktni klici `collection("users").document(email/uid)` so pisali na napačen dokument za starejše uporabnike (pred migracijo UID→email).
**Tveganje:** 🔴 visoko (podatki bi se shranjevali na napašen dokument)

## 2026-03-10 — Fix: dvojni badge check v recordWorkoutCompletion
**Datoteke:** `persistence/AchievementStore.kt`
**Kaj:** `recordWorkoutCompletion` je klical `checkAndUnlockBadges` dvakrat — enkrat znotraj `awardXP`, enkrat direktno. Popravljeno na enkratni klic.
**Zakaj:** Podvojen klic je povzročal dvojno XP podeljevanje za badge in dvojni Firestore write.
**Tveganje:** 🔴 visoko

## 2026-03-10 — Fix: manjkajoči badge ID-ji v getBadgeProgress
**Datoteke:** `persistence/AchievementStore.kt`, `data/BadgeDefinitions.kt`
**Kaj:** Dodani badge ID-ji `committed_250`, `committed_500`, `level_50`, `celebrity` v `getBadgeProgress()` in `BadgeDefinitions.ALL_BADGES`.
**Zakaj:** Badge-i ki niso v `getBadgeProgress` vedno vrnejo 0 → nikoli ne odklenejo.
**Tveganje:** 🔴 visoko

## 2026-03-10 — Fix: WeeklyStreakWorker direkten Firestore klic
**Datoteke:** `workers/WeeklyStreakWorker.kt`
**Kaj:** `saveStreakToFirestore()` je pisala direktno na `document(email)` → popravljeno na `FirestoreHelper.getCurrentUserDocRef()`.
**Zakaj:** Za legacy uporabnike (UID-based dokumenti) bi se streak shranil na napačen dokument.
**Tveganje:** 🟡 srednje

## 2026-03-10 — Fix: addXPWithCallback v RunTrackerScreen
**Datoteke:** `ui/screens/RunTrackerScreen.kt`
**Kaj:** Klic `addXPWithCallback` zamenjan z `AchievementStore.awardXP()`. Dodan `scope` in coroutine imports.
**Zakaj:** `addXPWithCallback` je deprecated, ne preverja badge-ev in ne beleži xp_history.
**Tveganje:** 🟢 nizko

## 2026-03-10 — Refactoring: swapDaysInPlan skozi PlanDataStore
**Datoteke:** `viewmodels/BodyModuleHomeViewModel.kt`, `persistence/PlanDataStore.kt`
**Kaj:** `swapDaysInPlan()` je ročno serializiral plan in pisal direktno na Firestore. Zdaj kliče `PlanDataStore.updatePlan()`. Dodan `updatePlan()` v `PlanDataStore`.
**Zakaj:** Podvojena serializacijska logika — plan se je serializiral na dva različna načina.
**Tveganje:** 🟡 srednje

## 2026-03-10 — Dodani arhitekturni testi
**Datoteke:** `app/src/test/java/com/example/myapplication/ArchitectureTest.kt`
**Kaj:** 5 unit testov ki preverjajo: badge konsistentnost, Firestore routing, deprecated XP klic, clearCache ob odjavi, XPSource enum.
**Zakaj:** Preprečuje da AI pri popravku pokvari ključne invariante brez opozorila.
**Tveganje:** 🟢 nizko

## 2026-03-11 — RDP Polyline kompresija + Activity Log + Share Activities
**Datoteke:** `utils/RouteCompressor.kt`, `data/UserAchievements.kt`, `persistence/ProfileStore.kt`, `ui/screens/ActivityLogScreen.kt`, `ui/screens/PublicProfileScreen.kt`, `ui/screens/BodyModuleHomeScreen.kt`, `AppNavigation.kt`, `AppDrawer.kt`, `MainActivity.kt`, `ui/screens/RunTrackerScreen.kt`
**Kaj:**
- **RouteCompressor** — RDP algoritem kompresira GPS traso ~450→~35 točk (~92% manj storage)
- **PublicActivity** data class za Firestore javne aktivnosti (komprimirane)
- **ActivityLogScreen** — nov zaslon s karticami po aktivnostih, vsaka barve glede na tip (Run=modra, Hike=rjava, Skiing=turkizna...), mini OSM mapa z barvno linijo pri razprtju
- **BodyModuleHomeScreen** — gumb 🗺️ desno od "Start run" odpre ActivityLogScreen
- **shareActivities** toggle v AppDrawer privacy nastavitvah
- **RunTrackerScreen** — pri shranjevanju seje preveri `share_activities` flag in shrani komprimirano ruto v `publicActivities/{sessionId}` v Firestoreu
- **PublicProfileScreen** — TabRow "Profile"/"Activities" ko ima shareActivities=true; Activities tab prikaže javne aktivnosti z barvnimi karticami in mapami
- **ProfileStore.getPublicProfile** — bere `share_activities` in naloži `publicActivities` subcollection
**Zakaj:** Storage optimizacija za deljenje tras med followerji (92% manj Firestore reads/writes)
**Tveganje:** 🟡 srednje — nova Firestore kolekcija `publicActivities`

**Datoteke:** `ui/screens/ExerciseHistoryScreen.kt`, `viewmodels/RunTrackerViewModel.kt`
**Kaj:** RunCard v ExerciseHistoryScreen prikazuje detajlne statistike ob razširitvi: Distance, Duration, Avg speed, Max speed, Pace, Ascent, Descent, Calories — vsak prilagojen tipu aktivnosti (npr. Hike nima hitrosti, Walk nima vzpona). Dodan `RunDetailRow` composable. ViewModel že bere `elevationGainM`, `elevationLossM`, `activityType` iz Firestore.
**Zakaj:** Shranjeni podatki o vzponu, tempu in hitrosti se niso prikazali v zgodovini tekov.
**Tveganje:** 🟢 nizko

**Datoteke:** `data/RunSession.kt`, `ui/screens/RunTrackerScreen.kt`, `viewmodels/RunTrackerViewModel.kt`, `ui/screens/ExerciseHistoryScreen.kt`
**Kaj:** Dodan `ActivityType` enum z 9 tipi aktivnosti (Run, Walk, Hike, Sprint, Cycling, Skiing, Snowboard, Skating, Nordic Walk). Vsak tip ima: MET vrednost za izračun kcal, zastavice za vzpon/tempo/hitrost, emoji in label. RunTrackerScreen dobi dropdown picker (klik na izbrani tip → horizontalni chip scroll). Kcal se izračunava z MET formulo (MET × kg × ure + vzpon bonus). Stats card in summary se prilagodita tipu. RunSession shrani `activityType`, `elevationGainM`, `elevationLossM`. ExerciseHistoryScreen RunCard prikazuje pravo ikono/ime in vzpon kjer je relevantno.
**Zakaj:** Uporabnik želi slediti različnim aktivnostim, ne samo teku.
**Tveganje:** 🟢 nizko

**Datoteke:** `widget/PlanDayWidgetProvider.kt`, `res/layout/widget_plan_day.xml`, `res/xml/plan_day_widget_info.xml`, `AndroidManifest.xml`, `MainActivity.kt`
**Kaj:** Nov home screen widget ki prikazuje: 🔥 streak, "Week X · Day Y", in focus area tega dne (npr. "Push", "Legs", "Rest 😴"). Klik na widget odpre aplikacijo direktno na BodyModuleHome. Podatki se preberejo iz Firestore (users/{email} za streak/plan_day, user_plans/{uid} za weeks strukturo). Widget se osveži: ob odprtju aplikacije, po končani vadbi, ob DATE_CHANGED.
**Zakaj:** Uporabnik želi hitro videti kateri dan ima danes in kaj je fokus — brez odpiranja aplikacije.
**Tveganje:** 🟢 nizko

**Datoteke:** `utils/NutritionCalculations.kt`, `ui/screens/NutritionScreen.kt`, `ui/screens/BodyModuleHomeScreen.kt`
**Kaj:** (1) Dve novi funkciji v NutritionCalculations: `calculateDailyWaterMl()` (35ml/kg, +500ml workout day, spol, aktivnost) in `calculateRestDayCalories()` (workout kalorije -150 do -250 kcal na rest day glede na cilj). NutritionScreen zdaj prikazuje prilagojen vodni cilj in prilagojene kalorije z oznako "🏋️ Workout day" / "😴 Rest day". (2) BodyModuleHomeScreen ob vstopu brez plana takoj preusmeri na BodyOverview ("No plans yet") prek LaunchedEffect.
**Zakaj:** Trdo kodirani cilji (2000ml vode, enake kalorije vsak dan) niso upoštevali individualnih podatkov iz kviza. Hkrati je pomanjkanje plana šele kazalo napako pri kliku na PlanPath, ne takoj ob vstopu.
**Tveganje:** 🟢 nizko

## 2026-03-11 — Popravek po ugašanju računalnika — manjkajoče funkcije
**Datoteke:** `utils/NutritionCalculations.kt`
**Kaj:** `calculateDailyWaterMl()` in `calculateRestDayCalories()` sta manjkali — niso bile shranjene ob ugašanju računalnika med sejo. Ob preverjanju po ponovnem zagonu sesije je `NutritionScreen.kt` klical ti dve funkciji, a ti nista obstajali v `NutritionCalculations.kt`. Obe dodani nazaj.
**Zakaj:** Računalnik se je ugasnil med sejo — datoteka je bila shranjena brez teh funkcij.
**Tveganje:** 🔴 kritično — build bi padel brez teh funkcij

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
**Tveganje:** 🟢 nizko

## 2026-03-12 — Streak Animation & Haptics
**Datoteke:** `ui/screens/BodyModuleHomeScreen.kt`
**Kaj:** Implementiran `StreakCounter` composable, ki animira streak (N-1 -> N) s 3D flip efektom in preciznimi vibracijami ob vsaki spremembi številke. Zamenjan statičen tekst z animiranim števcem.
**Zakaj:** Izboljšanje UX ob zaključku vadbe (občutek napredka, podobno Duolingo stilu).
**Tveganje:** 🟢 nizko

## 2026-03-12 — Fix Double Workout Submission
**Datoteke:** `viewmodels/BodyModuleHomeViewModel.kt`
**Kaj:** Dodal `AtomicBoolean` zaščito v `completeWorkoutSession` in `completeRestDayActivity` za preprečevanje hkratnega izvajanja.
**Zakaj:** Uporabnik je prijavil, da se statistika poveča za +2 namesto za +1 po enem treningu (caused by double click / concurrency).
**Tveganje:** 🟢 nizko (čista zaščita pred race-condition)

## 2026-03-12 — Gamification System & Face Module
**Datoteke:** `ShopScreen.kt`, `ShopViewModel.kt`, `GoldenRatioScreen.kt`, `FaceModule.kt`, `AchievementStore.kt`
**Kaj:** Dodana trgovina za XP (streak freeze), ML Kit face analysis (Golden Ratio), in dialogi za Skincare/Face Exercises.
**Zakaj:** Uporabnik je želel funkcionalen shop in delujoč face module namesto placeholderjev.
**Tveganje:** 🟢 nizko

## 2026-04-06 — Shop Card Move
**Datoteke:** `DashboardScreen.kt`, `BodyModuleHomeScreen.kt`, `MainActivity.kt`
**Kaj:** Dodana kartica Shop pod Face Module na DashboardScreen, ter odstranjen 🛒 gumb iz BodyModuleHomeScreen.
**Zakaj:** Optimizacija menijev, da je Shop na bolj vidnem mestu in povezan s centralnim nadzornim delom.
**Tveganje:** 🟢 nizko

## 2026-04-08 � Fix LoadingWorkout regular workout hijack
**Datoteke:** MainActivity.kt 
**Kaj:** Odpravljena napaka, kjer je LoadingWorkoutScreen preusmeril uporabnika na GenerateWorkoutScreen (ki ponudi izbiro Focus Area), e se je zdelo, da je bila vadba (ali rest day) isti dan e opravljena, kar je uporabnikom onemogoilo zagon vnaprej doloenega rednega plan dneva.
**Tveganje:** ?? nizko

## 2026-04-08 — Fix Navigation Backstack for WorkoutSession
**Datoteke:** `NavigationViewModel.kt`, `MainActivity.kt`
**Kaj:** Dodana metoda `popTo()` v NavigationViewModel in uporabljena namesto `navigateTo()` ob povratku iz WorkoutSession.
**Zakaj:** Preprečuje, da bi univerzalni 'Back' gumb uporabnika vrgel na preskočene ekrane v backstacku (npr. GenerateWorkout).
**Tveganje:** 🟢 nizko

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
**Tveganje:** 🟡 srednje

## 2026-04-10 — Firestore email-first cleanup + debug logging (Option B + A)
**Datoteke:** `persistence/FirestoreHelper.kt`, `ui/screens/RunTrackerScreen.kt`, `viewmodels/RunTrackerViewModel.kt`, `ui/screens/ActivityLogScreen.kt`, `worker/DailySyncWorker.kt`, `widget/WeightWidgetProvider.kt`, `widget/WaterWidgetProvider.kt`, `widget/QuickMealWidgetProvider.kt`, `widget/StreakWidgetProvider.kt`, `widget/PlanDayWidgetProvider.kt`, `widget/WeightInputActivity.kt`, `widget/WaterInputActivity.kt`, `ui/screens/NutritionScreen.kt`, `ui/screens/Progress.kt`, `persistence/DailySyncManager.kt`, `ui/screens/ExerciseHistoryScreen.kt`, `ui/screens/BodyModule.kt`, `ui/screens/LevelPathScreen.kt`, `persistence/ProfileStore.kt`
**Kaj:** Poenoten je users document routing prek `FirestoreHelper` (email-first), odstranjeni so kriticni direktni `users/{uid}` klici v run/activity/widget/worker tokih, dodani diagnostični logi za write/read tocke, in v `ActivityLogScreen` je odstranjena podvojena logika z helperjem za `isSmoothed` update.
**Zakaj:** Podatki so se deloma zapisovali v razlicne dokumente (`uid` vs `email`), zato v konzoli niso bili vidni na enem mestu in je bila diagnostika počasna.
**Tveganje:** 🟡 srednje

- **Food Repository Integration**: Created `FoodRepositoryImpl.kt` to centralize all FatSecret API operations and Firestore logging (batch/transaction) for custom meals. This completely removes database/API writes from UI code (`AddFoodSheet`, `NutritionDialogs`, `NutritionScreen`), making the UI "dumb" and resilient against data loss.

-   2 0 2 6 - 0 4 - 1 1   ( C l e a n   S w e e p )   -   C r e a t e d   K M P _ A N D R O I D _ D E P E N D E N C Y _ R E P O R T . m d   a n d   U s e r P r e f e r e n c e s R e p o s i t o r y . k t   f o r   s e t t i n g s   r e l o c a t i o n .  
 -   2 0 2 6 - 0 4 - 1 1   ( C l e a n   S w e e p   F i n a l   F a z a )   -   C o m p l e t e l y   s c r u b b e d   a n d r o i d . u t i l . L o g ,   j a v a . u t i l . D a t e ,   S i m p l e D a t e F o r m a t   a n d   d i r e c t   F i r e b a s e . f i r e s t o r e   U I   c a l l s   f o r   p u r e   K o t l i n   K M P   r e a d i n e s s   v i a   L o g g e r   a n d   k o t l i n x - d a t e t i m e   i m p l e m e n t a t i o n .  
    
 -   * * 2 0 2 6 - 0 4 - 1 1   ( B u i l d   F i x ) * *      O b n o v l j e n a   i z b r i s a n a   k o d a   i z   G i t   z g o d o v i n e   t e r   p o p r a v l j e n   \ D a t e F o r m a t t e r . k t \   z   u p o r a b o   \ j a v a . t i m e \ ,   d a   j e   p r o j e k t   z n o v a   z g r a d l j i v .  
    
 -   * * 2 0 2 6 - 0 4 - 1 1   ( B u i l d   F i x ) * *      O b n o v l j e n a   p o p v a r j e n a   i z b r i s a n a   k o d a   i z   G i t   z g o d o v i n e   t e r   p o p r a v l j e n   \ D a t e F o r m a t t e r . k t \   z   u p o r a b o   \ j a v a . t i m e \ .   A p l i k a c i j a   z o p e t   z i d a   u s p e an o .  
 - **2026-04-11 (KMP Dependencies & Sync)** — Aplikacija je uspešno sinhronizirana s KMP multiplatform-settings in kotlinx-datetime knjižnicama, build zopet deluje brezhibno po regresiji z giga-izbrisom datotek.
