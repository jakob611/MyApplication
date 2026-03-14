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
**Tveganje:** 🔴 visoko (podatki bi se shranjevali na napačen dokument)

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

## 2026-03-12 — Shop Module & Streak Freeze
**Datoteke:** `ui/screens/ShopScreen.kt`, `viewmodels/ShopViewModel.kt`, `persistence/AchievementStore.kt`, `data/UserProfile.kt`
**Kaj:** Dodan nov zaslon Trgovina (Shop) kjer uporabnik za XP kupuje "Streak Freezes" in "Kupone". Implementirana logika za porabo XP in "zamrznitev" streaka.
**Zakaj:** Gamifikacija in povečanje retencije uporabnikov.
**Tveganje:** 🟢 nizko

## 2026-03-12 — Face Module ML Integration & Content
**Datoteke:** `ui/screens/GoldenRatioScreen.kt`, `ui/screens/FaceModule.kt`
**Kaj:** Dodana ML Kit Face Detection integracija za avtomatsko analizo obraza (Beauty Score). Dodani placeholder dialogi za Skincare in vaje.
**Zakaj:** Uporabniki so želeli delujoč Face Module namesto "Coming Soon".
**Tveganje:** 🟡 srednje (odvisnost od ML Kit knjižnice in kamere)

## 2026-03-12 — Streak Animation Fixes
**Datoteke:** `ui/screens/BodyModuleHomeScreen.kt`
**Kaj:** StreakCounter animacija predelana z uporabo `snapshotFlow` za zanesljivo proženje haptike in preprečevanje recomposition zank.
**Zakaj:** Prejšnja implementacija je včasih povzročala crash ali vizualne glitche.
**Tveganje:** 🟢 nizko

## 2026-03-12 — Fix Stale Streak in Rest Day
**Datoteke:** `viewmodels/BodyModuleHomeViewModel.kt`
**Kaj:** `completeRestDayActivity` zdaj po posodobitvi streaka s `AchievementStore` ponovno prebere svež profil iz `UserPreferences` in uporabi to vrednost za UI in lokalni cache. Prav tako popravljen ID resolution za User Plans (uporaba `PlanDataStore.getResolvedUserId()`).
**Zakaj:** Prej je UI prikazoval star streak (pred posodobitvijo), ker je bral iz "bm_prefs" cache-a preden je bil ta posodobljen. Plan lookup bi lahko spodletel za migrirane uporabnike.
**Tveganje:** 🟢 nizko

## 2026-03-12 — UX Improvements for Shop Access
**Datoteke:** `ui/screens/BodyModuleHomeScreen.kt`, `MainActivity.kt`
**Kaj:** Dodan gumb za Trgovino (🛒) v glavno vrstico z akcijami v `BodyModuleHomeScreen`. Implementirana navigacija.
**Zakaj:** Prej je bila trgovina dostopna le iz Dashboarda, kar je zmanjševalo vidnost gamifikacije.
**Tveganje:** 🟢 nizko
