# APP_MAP.md
> **NAVODILO ZA AI:** Ko dobiš nalogo "popravi X", najprej poglej v to datoteko da ugotoviš KATERO datoteko odpreti. Ne ugibaj.

**Zadnja posodobitev:** 2026-03-10

---

## KAKO BRATI TA DOKUMENT
- 🖥️ = UI screen (kar uporabnik vidi)
- 🧠 = logika / ViewModel
- 💾 = Firestore / lokalno shranjevanje
- 📐 = data modeli
- 🔧 = pomožne funkcije / utility
- ⚙️ = ozadni procesi (Worker, Service)

---

## NAVIGACIJA — kje se začne

| Datoteka | Kaj dela |
|----------|---------|
| `MainActivity.kt` | Vstopna točka. Auth check, Google Sign-In, navigacija med Screen objekti, DailySyncManager, streak init. **811 vrstic.** |
| `AppNavigation.kt` | `sealed class Screen` z vsemi zasloni + `AppBottomBar` (spodnja navigacija 4 tabov). Če dodajaš nov screen → dodaj objekt tukaj. |
| `AppViewModel.kt` | Drži `userProfile` in `userEmail` za celo aplikacijo. Vse screen-i ga berejo prek `viewModel()`. |
| `NavigationViewModel.kt` | Back stack in navigacija (`navigateTo`, `goBack`, `clearStack`). |
| `AppDrawer.kt` | Stranski meni: profil slika, equipment izbira, dark mode toggle, odjava, badge count. |

---

## 🖥️ SCREENS — UI datoteke

### Pred prijavo
| Datoteka | Kaj prikaže | Kaj popravljaš tu |
|----------|------------|-------------------|
| `Indexscreen.kt` | **Splash/welcome screen PRED prijavo** (logo, "Get Started" gumb) | Welcome page layout, Pro features gumb |
| `LoginScreen.kt` | Prijava z emailom ali Google Sign-In, registracija | Login/register logika in UI |

### Po prijavi — glavna navigacija
| Datoteka | Kaj prikaže | Kaj popravljaš tu |
|----------|------------|-------------------|
| `DashboardScreen.kt` | **Glavni home screen po prijavi** — kartice za module (Body, Face, Hair, Shop) | Modul kartice na home screenu |

### Trening modul
| Datoteka | Kaj prikaže | Kaj popravljaš tu |
|----------|------------|-------------------|
| `BodyModuleHomeScreen.kt` | Home screen za Body tab: streak, dnevni plan, weekly progress, plan path | Layout body home, streak prikaz |
| `BodyModule.kt` | **14-koračni kviz** za ustvarjanje novega plana (spol, starost, cilj, oprema...) — glavna funkcija: `BodyPlanQuizScreen()` | Vprašanja v kvizu, validacija vnosa, shranjevanje |
| `BodyOverviewScreen.kt` | Pregled obstoječih planov, gumb za ustvarjanje novega plana | Plan overview UI, dialog za zamenjavo plana |
| `WorkoutSessionScreen.kt` | **Aktivna vadba** — timer, seznam vaj, kalorije, animacije. Shrani session ob zaključku. | Med-vadba UI, shranjevanje rezultatov vadbe |
| `GenerateWorkoutScreen.kt` | Generiranje **dodatnega (extra) workota** za danes — izbira fokusa in opreme, lokalni algoritem | Extra workout generiranje, fokus/oprema izbira |
| `LoadingWorkoutScreen.kt` | Loading animacija med generiranjem plana (po kvizu) | Loading UI animacija |
| `ManualExerciseLogScreen.kt` | Ročno beleženje posamezne vaje (sets, reps, trajanje) + izračun kalorij | Log posamezne vaje, kalorij izračun |
| `ExerciseHistoryScreen.kt` | Zgodovina — 3 tabi: Workouts (sesije), Exercises (posamezne vaje), Runs (teki) | Prikaz preteklih vadb, sortiranje |
| `MyPlansScreen.kt` | **Seznam vseh shranjenih planov** z možnostjo brisanja | Plan CRUD UI, prikaz planov |
| `PlanPathVisualizer.kt` | Vizualni prikaz 4-tedenskega plana kot krogi (aktiven dan, rest, done) | Izgled in barve plan path krogov |
| `PlanPathDialog.kt` | **Dialog za swap dni** v planu (povleci dan A ↔ dan B) | Swap dni logika v UI |
| `KnowledgeHubScreen.kt` | Baza znanja o treningih (accordion lista nasvetov) | Vsebina knowledge hub |

### Prehrana modul
| Datoteka | Kaj prikaže | Kaj popravljaš tu |
|----------|------------|-------------------|
| `NutritionScreen.kt` | **Glavni screen za sledenje hrani** — makri, voda, porabljene kalorije, donut graf, seznam obrokov. **996 vrstic.** | Food tracking UI, donut graf integracija, sync |
| `NutritionComponents.kt` | Manjše UI komponente: `WaterControlsRow`, `MacroTextRow`, `SavedMealChip`, `MealCard`, `TrackedFoodItem` | Donut graf, food card, meal section izgled |
| `NutritionDialogs.kt` | Dialogi: `MakeCustomMealsDialog` (ustvari custom meal), `ChooseMealDialog` (izberi obrok) | Custom meal dialog, meal picker dialog |
| `NutritionModels.kt` | Data modeli: `TrackedFood`, `MealType` (Breakfast/Lunch/Dinner/Snacks), `SavedCustomMeal` | Spremembe modela hrane ali enum vrednosti |
| `AddFoodSheet.kt` | Bottom sheet za **iskanje hrane** (FatSecret API) in dodajanje v obrok | Iskanje hrane, API integracija, food card |
| `BarcodeScannerScreen.kt` | Kamera za **skeniranje barkode** — odpre AddFoodSheet z rezultatom | Barcode scan logika, kamera permissioni |
| `DonutProgressView.kt` | **Custom Canvas donut graf** za prikaz makrov (protein/carbs/fat) | Donut graf geometrija, animacije, barve |

### Napredek in statistike
| Datoteka | Kaj prikaže | Kaj popravljaš tu |
|----------|------------|-------------------|
| `Progress.kt` | **4 grafi**: teža, kalorijski vnos, voda, porabljene kalorije. Snapshot listenerji na Firestore. **937 vrstic.** | Grafi napredka, weightLog UI, range selector |
| `BodyOverviewScreen.kt` | Pregled obstoječih planov + gumb za nov plan (BMI/BF% so v `BodyOverviewViewmodel.kt`) | Plan overview layout |
| `BodyOverviewViewmodel.kt` | **ViewModel za BodyOverview**: BMI, BF%, izračuni iz profila | Body metrics izračuni (BMI, BF%) |
| `GoldenRatioScreen.kt` | Golden ratio kalkulator za idealne telesne mere | Golden ratio algoritem in UI |
| `AchievementsScreen.kt` | XP bar, current level, progress do naslednjega levela, XP history | XP prikaz, level progress bar |
| `BadgesScreen.kt` | Grid vseh badge-ev — odklenjeni (barvni) / zaklenjeni (sivi) | Badge grid layout, badge card izgled |
| `LevelPathScreen.kt` | Vizualna pot levelov (timeline) | Level path animacije in layout |

### Tek modul
| Datoteka | Kaj prikaže | Kaj popravljaš tu |
|----------|------------|-------------------|
| `RunTrackerScreen.kt` | GPS tek tracker z **živim OSMDroid zemljevidom**, timer, razdalja, hitrost, višinska razlika. Izbira tipa aktivnosti (Run/Walk/Hike/Sprint/Cycling/Skiing/Snowboard/Skating/Nordic). Shranjuje komprimirano ruto v `publicActivities` če ima `shareActivities=true`. | Tek UI, GPS logika, mapa, shranjevanje teka, tip aktivnosti |
| `ActivityLogScreen.kt` | **Nova datoteka** — vse aktivnosti na enem mestu z barvnimi karticami po tipu. Klikni kartico → razpre mini OSM mapa z barvno linijo. Dostopen z gumbom 🗺️ na BodyModuleHome. | Activity log UI, barvne kartice, mini mapa |

### Profil in socialno
| Datoteka | Kaj prikaže | Kaj popravljaš tu |
|----------|------------|-------------------|
| `MyAccountScreen.kt` | Nastavitve računa: brisanje podatkov, brisanje računa, navigacija na dev settings | Account management UI |
| `PublicProfileScreen.kt` | Javni profil drugega uporabnika: username, level, badges, follow gumb | Prikaz javnega profila, follow/unfollow |
| `ui/home/CommunityScreen.kt` | **Community tab** — iskanje uporabnikov, top 10 leaderboard, klik → javni profil. Paket `ui.home` (ne `ui.screens`!) | Iskanje in leaderboard UI |
| `LevelPathScreen.kt` | Vizualna pot levelov + **followers/following dialogi** + badge prikaz za profil | Level path, followers lista, badge detail dialog |

### Ostali screeni
| Datoteka | Kaj prikaže | Kaj popravljaš tu |
|----------|------------|-------------------|
| `HealthConnectScreen.kt` | Android Health Connect integracija — sync korakov, spanja, srčnega utripa | Health Connect sync logika |
| `FaceModule.kt` | Face analysis modul | Face modul UI in logika |
| `HairModuleScreen.kt` | Hair analysis modul | Hair modul UI in logika |
| `EAdditivesScreen.kt` | Iskanje E-aditivov — baza se bere iz `assets/e_additives_database.json`. Prikaz nevarnosti (LOW/MODERATE/HIGH). | E-aditivi baza, JSON parsing, risk level prikaz |
| `ShopScreen.kt` | Nakupovalni screen za **hair produkte** (statičen seznam) | Shop UI |
| `ProFeaturesScreen.kt` | Prikaz Pro funkcij (brez nakupa) | Pro features marketing UI |
| `ProSubscriptionScreen.kt` | Nakup Pro naročnine | Subscription flow |
| `DeveloperSettingsScreen.kt` | Skrite razvijalske nastavitve (reset, debug info) | Dev tools |
| `AboutScreen.kt` | O aplikaciji (verzija, info) | About info |
| `ContactScreen.kt` | Kontakt forma | Contact UI |
| `PrivacyPolicyScreen.kt` | Politika zasebnosti (statičen tekst) | Privacy policy tekst |
| `TermsOfServiceScreen.kt` | Pogoji uporabe (statičen tekst) | ToS tekst |

### UI pomožne datoteke
| Datoteka | Kaj dela |
|----------|---------|
| `MyViewModelFactory.kt` | Factory za kreiranje ViewModelov z parametri (npr. `BodyModuleHomeViewModel`) |
| `ui/components/BadgeUnlockAnimation.kt` | **Animacija ob odklepu badge-a** — overlay z confetti in badge prikazom. Kliče se iz MainActivity ob badge eventu. |
| `ui/components/XPPopup.kt` | **+XP popup animacija** — lebdeč napis "+X XP" ki se pojavi ob zaslužku XP. |
| `ui/adapters/ChallengeAdapter.kt` | RecyclerView adapter za `Challenge` model (stari View sistem, ne Compose). |

---

## 🧠 VIEWMODELS IN LOGIKA

| Datoteka | Kaj dela | Kliče |
|----------|---------|-------|
| `viewmodels/BodyModuleHomeViewModel.kt` | Streak, weekly progress, `completeWorkoutSession()`, `swapDaysInPlan()`, `calculateWeeklyWorkoutsFromFirestore()` | `AchievementStore`, `FirestoreHelper`, `PlanDataStore`, `AlgorithmPreferences` |
| `viewmodels/RunTrackerViewModel.kt` | Load/save run sessions iz Firestore | `RunRouteStore` |
| `ui/screens/BodyOverviewViewmodel.kt` | BMI, body fat % izračuni iz `UserProfile` podatkov | — |

---

## 💾 PERSISTENCE — shranjevanje podatkov

| Datoteka | Kaj shrani | POMEMBNO |
|----------|-----------|---------|
| `FirestoreHelper.kt` | **EDINI resolver za Firestore dokumente** — email vs UID, migracija legacy podatkov, cache | ⛔ **Nikoli ne obidi!** Vse piše skozi `getCurrentUserDocRef()` |
| `AchievementStore.kt` | `awardXP()`, `recordWorkoutCompletion()`, `checkAndUnlockBadges()`, `updateLoginStreak()`, `recordPlanCreation()` | ⛔ **Edini vhod za XP in badge-e** |
| `UserPreferences.kt` | Profil load/save (Firestore + lokalni cache), `documentToUserProfile()` mapiranje, dark mode | Vse skozi `FirestoreHelper` |
| `PlanDataStore.kt` | Plan CRUD (`addPlan`, `deletePlan`, `updatePlan`, `savePlans`), AI plan HTTP klic, Firestore + DataStore backup | Kolekcija `user_plans` (ne `users`!) |
| `NutritionPlanStore.kt` | Nutrition plan shranjevanje in posodabljanje v Firestore | — |
| `ProfileStore.kt` | Javni profili (`getPublicProfile`), iskanje uporabnikov, privacy nastavitve update | Za public profile prikaz |
| `FollowStore.kt` | `followUser()`, `unfollowUser()`, `isFollowing()` | Piše v kolekcijo `follows` (ne `users`) |
| `RunRouteStore.kt` | GPS točke teka — **samo lokalno** (SharedPreferences), ni Firestore | Lokalno only |
| `DailySyncManager.kt` | Lokalni cache za food/water/burned — `saveFoodsLocally()`, `syncOnAppOpen()` | Local-first, sync prek WorkManager |

---

## 📐 DATA MODELI

| Datoteka | Modeli | Opomba |
|----------|--------|--------|
| `data/UserProfile.kt` | `UserProfile` data class + `calculateLevel(xp)`, `xpRequiredForLevel()` | Computed property `.level` |
| `data/UserAchievements.kt` | `XPSource` enum (WORKOUT_COMPLETE, CALORIES_BURNED, DAILY_LOGIN, BADGE_UNLOCKED, PLAN_CREATED, RUN_COMPLETED...) | Dodaj sem če dodajaš nov XP vir |
| `data/BadgeDefinitions.kt` | `BadgeDefinitions.ALL_BADGES` lista, `Badge` data class, `BadgeCategory` enum | **Edini vir badge definicij in `requirement` vrednosti** |
| `data/PlanModels.kt` | `PlanResult`, `WeekPlan`, `DayPlan` | Plan data modeli |
| `data/AlgorithmData.kt` | `AlgorithmData` — debug podatki o BMR/TDEE za prikaz v planu | — |
| `data/NutritionPlan.kt` | `NutritionPlan` model | — |
| `data/AlgorithmPreferences.kt` | SharedPreferences wrapper za težavnost, recovery mode, user feedback multiplierje | Kliče se iz WorkoutPlanGenerator |
| `data/AdvancedExerciseRepository.kt` | Baza 100+ vaj z metapodatki (mišice, oprema, `caloriesPerMinPerKg`) | Vir vaj za algoritem |
| `data/RefinedExercise.kt` | `RefinedExercise` model za vajo v aktivni sesiji | — |
| `data/RunSession.kt` | `RunSession` model (razdalja, čas, hitrost, GPS točke, elevationGainM/LossM, **activityType**). `ActivityType` enum (RUN/WALK/HIKE/SPRINT/CYCLING/SKIING/SNOWBOARD/SKATING/NORDIC) z MET vrednostmi, showSpeed/showPace/showElevation. | Dodaj novo vrsto aktivnosti sem |
| `data/UserAchievements.kt` | `XPSource`, `Badge`, `PrivacySettings` (incl. **shareActivities**), **`PublicActivity`** (komprimirana javna aktivnost), `PublicProfile` (incl. **recentActivities**) | Dodaj sem če dodajaš novo privacy nastavitev |
| `data/HealthStorage.kt` | Lokalno shranjevanje Health Connect podatkov | — |

---

## 🔧 DOMAIN LOGIKA IN UTILITY

| Datoteka | Kaj dela | Ključne funkcije |
|----------|---------|-----------------|
| `domain/WorkoutPlanGenerator.kt` | **Algoritem za 4-tedenski plan** — razporedi vaje po dnevih glede na activity level, izkušnje, opremo | `generateAdvancedCustomPlan()`, `generatePlanWeeks()` |
| `utils/NutritionCalculations.kt` | **BMR, TDEE, makro izračuni** | `calculateAdvancedBMR()`, `calculateEnhancedTDEE()`, `calculateMacros()` |
| `utils/HapticFeedback.kt` | Haptic feedback wrapper | `performHapticFeedback()` |
| `utils/RouteCompressor.kt` | **RDP algoritem** za kompresijo GPS trase (~450→~35 točk, 92% manj storage). Uporablja se pri shranjevanju v `publicActivities` Firestore. | `compress(points, epsilon)` |
| `network/fatsecret_api_service.kt` | FatSecret API klic za iskanje hrane po imenu ali barkodi | Hrana API |
| `network/OpenFoodFactsAPI.kt` | Open Food Facts API — alternativni vir podatkov za hrano | Backup hrana API |
| `network/ai_utils.kt` | Pomožna funkcija `requestAIPlan()` — duplikat logike iz `PlanDataStore`. **Verjetno neuporabljeno.** | Preveri pred brisanjem |

---

## ⚙️ OZADNI PROCESI

| Datoteka | Kdaj se zažene | Kaj dela |
|----------|---------------|---------|
| `worker/DailySyncWorker.kt` | Ko app gre v ozadje (`onPause`) ali ob odprtju | Sync lokalni food/water/burned cache → Firestore |
| `workers/WeeklyStreakWorker.kt` | Vsako polnoč (OneTimeWork z reschedule) | Posodobi streak, nastavi `yesterday_was_rest` flag za naslednji dan |
| `workers/StreakReminderWorker.kt` | Ob določenem času | Push notifikacija za streak reminder |
| `service/RunTrackingService.kt` | Med aktivnim tekom (ForegroundService) | GPS tracking v ozadju, posodobi RunTrackerScreen prek binding |

---

## 🗺️ HITRI VODIČ — "Kaj popraviti za X"

| Želiš popraviti | Odpri to datoteko |
|----------------|-------------------|
| **Community tab (iskanje, leaderboard)** | `ui/home/CommunityScreen.kt` |
| **Followers / following lista** | `LevelPathScreen.kt` |
| **Badge unlock animacija** | `ui/components/BadgeUnlockAnimation.kt` |
| **+XP popup animacija** | `ui/components/XPPopup.kt` |
| **E-aditivi (JSON baza)** | `EAdditivesScreen.kt` |
| **FatSecret iskanje hrane (API)** | `network/fatsecret_api_service.kt` |
| **Donut graf za makre** | `NutritionComponents.kt` |
| **Iskanje hrane / barcode** | `AddFoodSheet.kt` ali `BarcodeScannerScreen.kt` |
| **Custom meal dialog** | `NutritionDialogs.kt` |
| **Modeli hrane (TrackedFood, MealType)** | `NutritionModels.kt` |
| **Food tracking shranjevanje / sync** | `NutritionScreen.kt` + `DailySyncManager.kt` |
| **Workout plan algoritem (4 tedni)** | `domain/WorkoutPlanGenerator.kt` |
| **BMR / TDEE / makro izračuni** | `utils/NutritionCalculations.kt` |
| **Swap dni v planu (UI)** | `PlanPathDialog.kt` |
| **Swap dni v planu (logika)** | `viewmodels/BodyModuleHomeViewModel.kt` → `swapDaysInPlan()` |
| **Plan shranjevanje / brisanje** | `persistence/PlanDataStore.kt` |
| **Plan prikaz (krogi, dnevi)** | `PlanPathVisualizer.kt` |
| **Seznam vseh planov** | `MyPlansScreen.kt` |
| **XP podeljevanje** | `persistence/AchievementStore.kt` → `awardXP()` |
| **Badge unlock logika** | `persistence/AchievementStore.kt` + `data/BadgeDefinitions.kt` |
| **Badge prikaz** | `BadgesScreen.kt` |
| **Level / XP prikaz** | `AchievementsScreen.kt` |
| **Streak logika (daily)** | `persistence/AchievementStore.kt` → `updateLoginStreak()` |
| **Streak logika (polnoč Worker)** | `workers/WeeklyStreakWorker.kt` |
| **Grafi napredka** | `Progress.kt` |
| **Teža logging** | `Progress.kt` (weightLogs Firestore listener) |
| **GPS tek (UI + mapa)** | `RunTrackerScreen.kt` |
| **Tip aktivnosti (Run/Walk/Hike...)** | `data/RunSession.kt` → `ActivityType` enum |
| **Vse aktivnosti na enem mestu (log)** | `ActivityLogScreen.kt` |
| **GPS tek (ozadje)** | `service/RunTrackingService.kt` |
| **Tek zgodovina** | `ExerciseHistoryScreen.kt` (RunsTab) |
| **Javni profil prikaz** | `PublicProfileScreen.kt` + `persistence/ProfileStore.kt` |
| **Follow / unfollow** | `persistence/FollowStore.kt` |
| **Privacy nastavitve** | `persistence/ProfileStore.kt` → `updatePrivacySettings()` |
| **Share Activities toggle** | `AppDrawer.kt` → privacy sub-toggles |
| **Javne aktivnosti (Firestore)** | `users/{uid}/publicActivities/{sessionId}` — shrani `RunTrackerScreen.kt`, bere `ProfileStore.getPublicProfile()` |
| **Kompresija GPS trase** | `utils/RouteCompressor.kt` → `RouteCompressor.compress()` |
| **Firestore dokument routing** | `persistence/FirestoreHelper.kt` ⛔ ne obidi! |
| **Profil load / save** | `data/UserPreferences.kt` |
| **Stranski meni (drawer)** | `AppDrawer.kt` |
| **Navigacija med screeni** | `AppNavigation.kt` (dodaj `Screen.Xyz` objekt) |
| **Dark mode** | `AppDrawer.kt` (toggle) + `data/UserPreferences.kt` (save) |
| **Home screen po prijavi** | `DashboardScreen.kt` |
| **Welcome / splash screen** | `Indexscreen.kt` |
| **Kviz za ustvarjanje plana** | `BodyModule.kt` → `BodyPlanQuizScreen()` |
| **Body home (streak, daily plan)** | `BodyModuleHomeScreen.kt` |
| **Health Connect sync** | `HealthConnectScreen.kt` + `data/HealthStorage.kt` |
| **BMI / body fat % izračun** | `ui/screens/BodyOverviewViewmodel.kt` |

