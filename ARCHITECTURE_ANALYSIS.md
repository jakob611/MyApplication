# Arhitekturna Analiza in Načrt Refaktoriranja (KMP & Clean Architecture)

**Datum analize:** 2026-04-11

## 1. Trenutno stanje projekta (Težave in podvajanja)

Aplikacija trenutno vsebuje veliko **"God object"** vzorcev, kjer se na enem mestu prepletajo UI logika, branje iz baze, lokalno shranjevanje (SharedPreferences) in domenska logika (izračuni).

### 1.1 UI datoteke, ki delajo preveč (Primer: `ManualExerciseLogScreen.kt`)
Samo iz ene same datoteke (`ManualExerciseLogScreen.kt`) je očitno, da trenutna arhitektura krši načelo enotne odgovornosti (Single Responsibility Principle):
- **Branje in filtriranje JSON podatkov:** V UI datoteki imamo `ExerciseRepository`, ki parsira JSON, hkrati pa definira algoritem za izračun osebnega "score-a" vaje.
- **Lokalni Cache:** `GenderCache` upravlja z `SharedPreferences` direktno v isti datoteki.
- **Neposredno pisanje v bazo:** Funkcija `logExerciseToFirestore` opravlja asinhrono poizvedbo za "težo" uporabnika iz zadnjega loga, ročno izračuna porabo kalorij in neposredno zapisuje v `FirestoreHelper`.

### 1.2 Odkrita podvajanja in prepletanja
- **Domenska logika je raztresena:** Algoritmi za `personalizedScore()`, BMI, TDEE, "WorkoutPlanGenerator" itd. so deloma v `utils`, deloma znotraj samih zaslonov ali ViewModelov. To otežuje prenos v Kotlin Multiplatform (KMP), saj je logika tesno zvezana z Android `Context`-om.
- **Direktni klici v Firebase iz celotne aplikacije:** Klici na bazo niso omejeni na repozitorije. Posledično imamo recimo beleženje XP-ja (`AchievementStore.awardXP()`) vsiljeno v različne prostore kode.
- **SharedPreferences vs Firestore:** Sinhronizacija in pravila "kaj se shrani kam" sta nejasna in vsaka domena to rešuje po svoje.

---

## 2. KMP in Clean Architecture Načrt (Opcija A)

Prehod na KMP zahteva jasno ločitev odvisnosti o Android platformi (Android SDK, Context, Compose) in čistih logičnih komponent, ki se lahko kompilirajo za iOS in Android.

Sistem bomo razdelili na **Feature module**. Vsak modul bo sledil 3-nivojski Clean arhitekturi:

### Nivo 1: UI Sloj (Presentation) - Samostojen za Android in iOS
- **Zasloni (Compose):** Ne "vejo" ničesar o `Firestore`, `Context` ali `SharedPreferences`. Dobivajo le stanje (`StateFlow`) iz ViewModel-a.
- **ViewModels:** Prejemajo akcije uporabnika in kličejo ustrezne Use-Case-e. Ne vsebujejo težkih kalkulacij (BMR, score itn.).

### Nivo 2: Domenski Sloj (Domain) - Skupen za vse (KMP: `commonMain`)
To je **srce aplikacije**. Brez Android odvisnosti (brez `Context`-a).
- **Entitete (Models):** Čisti podatkovni razredi (npr. `Exercise`, `User`, `WorkoutSession`).
- **Use Cases (Interactors):** En razred = ena poslovna logika. Primeri: 
  - `CalculateCaloriesBurnedUseCase`
  - `GenerateWorkoutPlanUseCase`
  - `LogExerciseUseCase`
  - `CheckAndAwardBadgesUseCase`
- **Repository Interfejsi:** Npr. `interface ExerciseRepository { suspend fun logExercise(...) }`. Implementacije so v sloju s podatki.

### Nivo 3: Podatkovni Sloj (Data) - Platform specifičen, a enoten vmesnik
- **Repository implementacije:** Npr. `ExerciseRepositoryImpl`, ki se odloči, ali bere inicialno iz lokalne baze ali Firestore.
- **Podatkovni viri (Data Sources):** 
  - `RemoteDataSource`: Komunikacija s Firestore.
  - `LocalDataSource`: SharedPreferences / DataStore ali lokalna Room/SQLDelight baza.

---

## 3. Predlagana struktura modulov (Feature-based)

Da se izognemo "velikim datotekam", bomo aplikacijo organizirali po področjih (tudi znotraj paketov):

1. **`feature_auth` & `feature_profile`**
   - Podatki o uporabniku, login, dark mode nastavitev.
2. **`feature_workout`**
   - Načrti za trening (`WorkoutPlanGenerator`), zgodovina vaj, seznam vaj (JSON branje), ročno beleženje vaj.
3. **`feature_nutrition`**
   - Vnos hrane, Food APIs, kalkulacija BMR in TDEE.
4. **`feature_run`**
   - Route tracking, OSMDroid mape, kompresija poti.
5. **`feature_gamification`**
   - Vse vezano na `AchievementStore`, Streaks, XP, Leveling in Badges. Omenjeni vsi Use Cas-i združijo obstoječo logiko v čisto strukturo.

## 4. Koraki za miren prehod (Refactoring Roadmap extension)

Da nam med refaktorizacijo ne preneha delovati obstoječa aplikacija, bomo izvajali refactoring postopoma ("Strangler Fig" pattern):

1. **Extract Domain Logic:** Najprej vzamemo algoritme za izračune (kot je npr. `personalizedScore` iz ManualExerciseLogScreen) in jih prestavimo v klasične Kotlin razrede, ki sploh ne poznajo Androida.
2. **Repository Pattern:** Združimo branje/pisanje za specifične domene v repozitorije (npr. prestavimo branje JSONa v `LocalExerciseDataSource`, pisanje v bazo pa v `RemoteWorkoutDataSource` pod `WorkoutRepository`).
3. **Povezava Use-Cases z ViewModelom:** UI zgolj kliče ViewModel, ta kliče UseCase, na koncu odstranimo staro kodo.
4. Na koncu se odstranijo `Firestore` SDK klici iz vseh Compose UI zaslonov.
