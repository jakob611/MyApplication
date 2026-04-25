# CODE_ISSUES.md
> **NAVODILO ZA AI:** To datoteko VEDNO preberi na začetku seje. Po vsakem popravku dodaj vnos na dno pod "DNEVNIK POPRAVKOV".

**Zadnja posodobitev:** 2026-04-25  
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

## DNEVNIK POPRAVKOV

### 2026-04-25 — Faza 7.1: Hibridni TDEE z Confidence Faktorjem

**Spremembe:**
- `utils/NutritionCalculations.kt`: `calculateAdaptiveTDEE()` razširjen z `theoreticalTDEE: Int`
  - Confidence factor C: 0.0 (<3 dni), 0.5 (3–5 dni), 1.0 (6+ dni)
  - Hibridna formula: `C × adaptivni + (1−C) × teoretični`
  - Vrača `AdaptiveTDEEResult(hybridTDEE, adaptiveTDEE, confidenceFactor)` data class
- `debug/WeightPredictorStore.kt`: dodana polja `lastHybridTDEE`, `lastAdaptiveTDEE`, `lastConfidenceFactor`
- `ui/screens/Progress.kt`: `computeWeightPrediction()` posodobljen — bere `theoreticalTDEE` iz `NutritionDebugStore` in shranjuje hibridni rezultat
- `viewmodels/NutritionViewModel.kt`: `setUserMetrics()` preverja `WeightPredictorStore.lastHybridTDEE`; nova funkcija `applyHybridTDEE()`
- `viewmodels/DebugViewModel.kt`: `WeightPredictorDebugInputs` razširjen z novimi hibridnimi polji
- `domain/nutrition/NutritionCalculations.kt`: označena DEPRECATED (prazna datoteka, čaka na ročno brisanje)

**Arhitekturna opomba:** `NutritionViewModel._baseTdee` zdaj preferira hibridni TDEE pred statičnim `BMR × 1.2`, kar odpravi odvisnost od fiksnega Mifflin-St Jeor množilnika po zadostnih realnih podatkih.
