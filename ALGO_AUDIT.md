# Algoritemska Revizija (ALGO_AUDIT)

Dokumentacija trenutnega stanja algoritmov v projektu. Vsebuje natančen popis vhodnih podatkov, matematičnih formul, izhodov in kritično analizo (težave, 'race conditions', hrošči).

---

## 1. Health Connect Sync
**Ime in lokacija:** `SyncHealthConnectUseCase.kt` (app/src/main/java/com/example/myapplication/domain/workout/SyncHealthConnectUseCase.kt)

**Vходni podatki (Inputs):**
- **Health Connect API:** Prebere seštevek "Active" (ali ocenjenih) kalorij za tekoči dan.
- **SharedPreferences:** Prebere `lastSyncedHcKcal` (zadnja znana vrednost prebranih kalorij iz HC).
- **Firestore:** Prebere trenutni `serverBurned` (število pokurjenih kalorij v bazi za tekoči dan) znotraj `users/{uid}/dailyLogs/{todayId}`.

**Matematična formula/Logika:**
- `delta = healthConnectCalories - lastSyncedHcKcal`
- Če je `delta > 0`, se pošlje zahteva v bazo, ki `burnedCalories` inkrementira za to relacijo: `FieldValue.increment(delta)`.
- Fallback: Če ne gre, a na serverju še ni kalorij (`serverBurned == 0`), se preprosto prebriše: `burnedCalories = healthConnectCalories`.

**Izhod (Output):**
- Posodobi se ali ustvari Firestore dokument `users/{uid}/dailyLogs/{todayId}` (polje `burnedCalories`) preko `SetOptions.merge()`.
- Posodobi se `SharedPreferences` s trenutnimi HC kalorijami.

**Kritična analiza (Težave):**
- 🚨 **Podvajanje kalorij (Wipe Bug):** Če uporabnik izbriše podatke aplikacije ali gre na novo napravo, se `SharedPreferences` izbriše in postane 0. Ob prvem syncu bo `delta` enaka VSEM kalorijam iz HC, ki se bodo prišteli na *obstoječe* kalorije v bazi preko `increment()`. Uporabnik bo imel ogromno podvojenih kalorij.
- 🚨 **Ignoriranje izbrisanih podatkov (Negative Delta):** Če uporabnik v Health Connect-u ročno pobriše napačen trening (kalorije v HC padejo), bo `delta` negativna. Skripta negativno delto ignorira in nikoli ne odšteje kalorij iz baze.
- **Race Condition:** Funkcija ne uporablja Firestore Transakcij. Bere se `ref.get().await()`, nato se čez nekaj milisekund piše s `ref.set(..., merge())`. 

---

## 2. TDEE / BMR Kalkulator
**Ime in lokacija:** `CalculateBodyMetricsUseCase.kt` (shared/src/commonMain/kotlin/com/example/myapplication/domain/health/CalculateBodyMetricsUseCase.kt)

**Vhodni podatki (Inputs):**
- Podatki uporabnika (Firestore/User Input): `gender`, `age`, `heightCm`, `weightKg`, `goal`, `activityLevel`, `bodyFatPct`.

**Matematična formula/Logika:**
- **BMI:** `weightKg / (heightM * heightM)`
- **BMR (Mifflin-St Jeor):** `(10 * weight) + (6.25 * height) - (5 * age) + [5 za moške / -161 za ženske]`.
- **BMR (Katch-McArdle):** Če je podan Body Fat (5-50%): `LeanMass = weight * (1 - bodyFat / 100)`. `BMR = 370 + (21.6 * LeanMass)`.
- **TDEE:** `BMR * modifier`. Modifikator je trdo kodiran glede na `activityLevel` (1.2, 1.375, 1.55, 1.725, 1.9).
- **Target Calories:** TDEE - 500 za hujšanje, TDEE + 300 za "bulking".

**Izhod (Output):**
- Vrne `BodyMetricsResult` objekt (z BMI, Target Calories, TDEE, BMR), ki ga nato aplikacija najverjetnejše prikaže ali pošlje nazaj v profil ali dnevni log.

**Kritična analiza (Težave):**
- 🛑 **Statičen Activity Level:** Omejujemo se na fiksni vnos `activityLevel` ("sedentary", "active"). Ta podatek ne variira v realnem času. Če uporabnik en dan preteče 25 km, drugi dan pa preleži, mu bo algoritem TDEE računal isto kvoto tarčnih kalorij za oba dneva. 
- 🛑 **Izoliranost:** Kalkulator ne upošteva prejšnje komponente (`SyncHealthConnectUseCase`). Osebni cilji se ne osvežujejo dinamično z dnevno aktivnostjo.

---

## 3. Sistem XP-jev in Streakov (Gamification)
**Ime in lokacija:** `ManageGamificationUseCase.kt` & `FirestoreGamificationRepository.kt`

**Vhodni podatki (Inputs):**
- Oddelane vadbe (`caloriesBurned`), Dnevne prijave, Ustvarjeni plani itd.

**Matematična formula/Logika:**
- **Kaloričen dodatek:** `calorieXP = caloriesBurned / 8`.
- **Baza XP in Kriticizem:** `baseXP = 50`. Verjetnost, da je trening "Critical" je 10% (naključno generirano s `kotlin.random.Random.nextFloat() < 0.1f`). Če je critical, se `baseXP` podvoji.
- **Skupni XP:** `finalBaseXP + calorieXP`.
- **Streaki:** Ce je vadba uspesna (`isWorkoutSuccess`), se streak inkrementira za 1, razen ce je ze bil zabelezen danes. Ce pride do rest daya in se ob polnoci opravi provizija, algoritem preveri `user_plans` in se skuša zamenjati (Swap) s kasnejšim rest dnem. V skrajnem primeru porabi "Streak Freeze".

**Izhod (Output):**
- Ažurira se glavni dokument `users/{uid}` polje `xp` IN `login_streak`.
- Ustvari se se log v podzbirki `users/{uid}/xp_history/` (v isti Firestore Transakciji!).
- Ustvari se log v podzbirki `users/{uid}/daily_logs/{date}`.

**Kritična analiza (Težave):**
- ⚠️ **Lokalni RNG v UseCase-u:** Množitelj "Critical" je generiran lokalno. Če klic propade na poti do baze oz. uporabnik prekine povezavo ter poskusi ponovno, se RNG generira znova in potencialno goljufa/spreminja sistem. 
- ⚠️ **Complex Swap Logic (`checkIfFutureRestDaysExistAndSwap`):** Logika za samodejno prestavljanje Rest Daya zahteva prebiranje JSON-like struktur znotraj arraya planov. Manipulacija po večih Map listah nazaj v Firestore update je zelo krhka. Črkuje izjemno globoke modifikacije drevesa (`docRef.update("plans", plansList)`).  

---

## 4. Water Tracker
**Ime in lokacija:** `WaterWidgetProvider.kt` in `BIDIRECTIONAL_SYNC.md` (arhitekturna zasnova).

**Vhodni podatki (Inputs):**
- Gumbi na domačem zaslonu (+50 ml / -50 ml) ali neposredni vnosi.

**Matematična formula/Logika:**
- Bere prejšnjo vrednost iz cache-a (ali Firestore, če se Widget prvič naloži).
- `newVal = (currentVal + delta).coerceAtLeast(0)`.

**Izhod (Output):**
- 1. Takojšnji zapis v lokalni `SharedPreferences`.
- 2. Asinhronski "fire and forget" zapis v `users/{uid}/dailyLogs/{todayId}` (`waterMl` polje).

**Kritična analiza (Težave):**
- 🚨 **Odsotnost Transakcij / Atomarnosti:** Predstavljaj si, da uporabnik v 2 sekundah pritisne "Plus" gumb 10-krat. Sprožilo se bo 10 `set(..., merge())` klicev v Firestore z inkrementalnimi vrednostmi pridobljenimi iz `SharedPreferences`. Če je lokalni cache sfalil ali se uničil, widget overwrite-a bazno stanje s povsem neusklajeno vrednostjo.
- 🚨 **Network Race na DailyLogs dokument:** Če se widget voda popravlja hkrati, ko se v ozadju prenašajo kalorije (`SyncHealthConnectUseCase.kt`), in dokument se še ustvarja, lahko pride do kolizij.
- 🚨 **Fail Silently:** Če Firestore `set` ne uspe (`addOnFailureListener`), je lokalni `SharedPreferences` vseeno uspešno napredoval. Uporabnik misli, da ima spito vodo, na bazi pa je ni.

---

## 5. Workout Burned Calories
(Deloma pokrito pod "Health Connect Sync" in "XP sisteme", sicer v preteklih VM kot je `RunTrackerViewModel.kt`)

**Logika:**
Teče izrecna delitev med *prijavljenimi* / *znanimi* workouti uvoženimi ali zajetimi preko UI (kjer se ob zaključku pošlje podatek) in tistimi, ki se background synchronizirajo.
**Kritična Napaka:**
Pomanjkanje jasne razmejitve; kje točno Health Connect prepiše (ali dopolni) tiste treninge, ki jih aplikacija SAMA snema. Imamo odprta vrata za dvojno beleženje.

---

## 6. Sledenje (Follow System)
**Ime in lokacija:** `FollowStore.kt`

**Vhodni podatki (Inputs):**
- Klik na gumb "Follow" ali "Unfollow" na profilu drugega uporabnika.

**Matematična formula/Logika:**
- Oseba A doda Osebo B v svoj `following` bazen, Oseba B dobi Osebo A v `followers` bazen.
- Števec `followers` in `following` se za posamezna uporabnika poveča/zmanjša za 1.

**Izhod (Output):**
- `.update("followers", FieldValue.increment(1))`
- `.update("following", FieldValue.increment(1))`

**Kritična analiza (Težave):**
- 🚨 **Manko transakcij ob inkrementaciji:** Če enemu (vplivnemu) uporabniku v 1 sekundi "Follow" gumb klikne 10 ljudi, preprosti `.update` (ponekod, posebej brez transakcij) lahko ne ujame vrstnega reda inkrementov pod visoko obremenitvijo. Na ravni uporabnika še niso klicane `db.runTransaction { }`.

---

## 7. Background Notifikacije
**Ime in lokacija:** `StreakReminderWorker.kt`

**Vhodni podatki (Inputs):**
- **Samo `SharedPreferences` poizvedbe:** Prebira ločene cache lokacije ("water_cache", "calories_cache", "burned_cache", "bm_prefs").

**Logični proces:**
- Odločitvena drevesa za prikaz točnih besedil ob točni uri ("Imaš manj kot X kalorij in manj kot Y vode, naredi še to...").

**Kritična analiza (Težave):**
- 🛑 **Odstopanje od resnice (Out-of-Sync State):** Ta algoritem ignorira Firestore. Če uporabnik vnese kalorije na namizju / widgetu, in se `calories_cache` za notification worker ni osvežil (zaradi različnih providerjev), bo uporabnik dobil neaktualen in zmeden opomin. Pomembni Workerji morajo za branje vedno prisluhniti Single Source of Truth (Firestore).
