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
- ✅ ~~**Podvajanje kalorij (Wipe Bug)**~~ — **ODPRAVLJENO** (commit: `fix(dailyLogs)`). Referenčna vrednost je zdaj v Firestore polju `hcBurnedCalories`, ne v SharedPreferences. Wipe/Reset naprave ne more povzročiti podvojitve.
- ✅ ~~**Ignoriranje izbrisanih podatkov (Negative Delta)**~~ — **ODPRAVLJENO**. Nova logika podpira negativno delto: `(currentBurned + delta).coerceAtLeast(0.0)`.
- ✅ ~~**Race Condition**~~ — **ODPRAVLJENO**. Celotna logika teče znotraj `DailyLogRepository.updateDailyLog()` → Firestore `runTransaction { }`. Brez `SetOptions.merge()` ali `FieldValue.increment()` izven transakcije.

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
- **Level:** `UserProfile.calculateLevel(newXp)` — izračunano ZNOTRAJ transakcije in atomirano zapisano skupaj z XP.
- **Streaki:** Ce je vadba uspesna (`isWorkoutSuccess`), se streak inkrementira za 1, razen ce je ze bil zabelezen danes. Ce pride do rest daya in se ob polnoci opravi provizija, algoritem preveri `user_plans` in se skuša zamenjati (Swap) s kasnejšim rest dnem. V skrajnem primeru porabi "Streak Freeze".

**Izhod (Output):**
- Ažurira se glavni dokument `users/{uid}` polji `xp` IN `level` — ATOMARNO v isti transakciji.
- Ustvari se log v podzbirki `users/{uid}/xp_history/` (v isti Firestore Transakciji!).
- Ustvari se log v podzbirki `users/{uid}/daily_logs/{date}`.

**Kritična analiza (Težave):**
- ✅ **XP transakcija varno posodablja xp + level atomarno** — ODPRAVLJENO (Faza 3). `awardXP()` znotraj `db.runTransaction { }` sedaj bere `currentXp`, izračuna `newXp + newLevel` in piše oba polji z `SetOptions.merge()`. Ni možen level desync.
- ⚠️ **Lokalni RNG v UseCase-u:** Množitelj "Critical" je generiran lokalno. Če klic propade, se RNG generira znova — sprejmljiv kompromis za gamification element.
- ⚠️ **Complex Swap Logic (`checkIfFutureRestDaysExistAndSwap`):** Logika za samodejno prestavljanje Rest Daya zahteva prebiranje JSON-like struktur znotraj arraya planov. Manipulacija po večih Map listah nazaj v Firestore update je krhka.

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
- ✅ ~~**Odsotnost Transakcij / Atomarnosti**~~ — **ODPRAVLJENO** (commit: `fix(dailyLogs)`). `handleDelta()` zdaj kliče `DailyLogRepository().updateDailyLog()` znotraj `CoroutineScope(Dispatchers.IO)`. Lokalni SharedPrefs ostane za optimistični UI odziv.
- ✅ ~~**Network Race na DailyLogs dokument**~~ — **ODPRAVLJENO**. `DailyLogRepository` zagotavlja, da se `waterMl` in `burnedCalories` ne pišeta hkrati izven transakcije.
- ⚠️ **Fail Silently**: Ob neuspehu Firestore transakcije ostane lokalni cache neusklajen. Widget prikazuje lokalno vrednost; ob naslednjem `REFRESH` gesti se prikliče prava vrednost iz baze. Sprejemljiv kompromis za widget UX.

---

## 5. Workout Burned Calories / QuickMealWidget / DailySyncManager

**Kritična analiza (Težave):**
- ✅ ~~**QuickMealWidgetProvider Silent Write**~~ — **ODPRAVLJENO (Faza 2)**. `handleAddMeal()` → coroutine + `DailyLogRepository`. Atomarno branje + append znotraj transakcije. Eliminiran callback pekel.
- ✅ ~~**DailySyncManager.syncTodayNow Silent Write**~~ — **ODPRAVLJENO (Faza 2)**. `DailyLogRepository().updateDailyLog()` v `CoroutineScope`.
- ✅ ~~**DailySyncWorker Silent Write**~~ — **ODPRAVLJENO (Faza 2)**. `DailyLogRepository().updateDailyLog()` direkten klic iz suspend `doWork()`.
- ✅ ~~**HealthStorage.doStoreDailyData Silent Write**~~ — **ODPRAVLJENO (Faza 2)**. Migrirano na `DailyLogRepository`.

---

## ✅ POTRJENO: V celotnem projektu NI niti ene `.set(..., SetOptions.merge())` na `dailyLogs` izven transakcije.

Preostali `SetOptions.merge()` klici pišejo v **druge kolekcije** ali so že znotraj `runTransaction { }`:
- `DailySyncManager.kt` → `daily_health` (ProgressScreen prikaz — ločena kolekcija)
- `UserProfileManager.kt` → korenski `users/` dokument (profil, darkMode)
- `MetricsRepositoryImpl.kt` → `weightLogs` in `dailyMetrics`
- `FoodRepositoryImpl.logWater/logFood` → `dailyLogs` **znotraj `db.runTransaction { }`** ✅

---

## 6. Sledenje (Follow System)
**Ime in lokacija:** `FollowStore.kt`

**Vhodni podatki (Inputs):**
- Klik na gumb "Follow" ali "Unfollow" na profilu drugega uporabnika.

**Matematična formula/Logika:**
- Oseba A doda Osebo B v svoj `following` bazen, Oseba B dobi Osebo A v `followers` bazen.
- Števec `followers` in `following` se za posamezna uporabnika poveča/zmanjša za 1.

**Izhod (Output):**
- Follow dokument z determinističnim ID `{followerId}_{followingId}` v kolekciji `follows`.
- Atomarno posodabljanje `followers` in `following` števcev znotraj `db.runTransaction { }`.

**Kritična analiza (Težave):**
- ✅ **Manko transakcij ob inkrementaciji — ODPRAVLJENO (Faza 3)**. `followUser()` in `unfollowUser()` sedaj tečeta v celoti znotraj `db.runTransaction { }`. Deterministični doc ID `{followerId}_{followingId}` prepreči dvojni follow celo pri sočasnih klikih (race condition safe). Transakcija atomarno: (1) preveri obstoj follow doc, (2) zapiše follow doc, (3) posodobi oba števca.
- ✅ **Backward compatibility**: `unfollowUser` vsebuje fallback za stare zapise z naključnimi ID-ji. `isFollowing` preveri oba formata (deterministični + query).

---

## 8. Shop — Nakupi z XP (Double-Spend zaščita)
**Ime in lokacija:** `ShopViewModel.kt`

**Vhodni podatki (Inputs):**
- Klik na "Buy Streak Freeze" (300 XP) ali "Buy Coupon" (500 XP).

**Kritična analiza (Težave):**
- ✅ **Double-spend hrošč — ODPRAVLJENO (Faza 3)**. `buyStreakFreeze()` in `buyCoupon()` sedaj tečeta v celoti znotraj `db.runTransaction { }`. Transakcija atomarno: (1) prebere trenutni XP in zaloge, (2) preveri pogoje (zadostni XP, max freezes), (3) posodobi XP + level + streakFreezes + logira XP history — vse ali nič. Hiter dvojni klik ne more porabiti XP dvakrat.

---

## ✅ POTRJENO (Faza 3): V celotnem projektu ni niti enega nevarnega 'Silent Write' na kritičnih poljih.

| Polje | Zaščita |
|-------|---------|
| `xp` | `FirestoreGamificationRepository.awardXP()` → `runTransaction` |
| `level` | atomarno z `xp` v isti transakciji |
| `followers` | `FollowStore.followUser/unfollowUser()` → `runTransaction` z determinističnim doc ID |
| `following` | `FollowStore.followUser/unfollowUser()` → `runTransaction` z determinističnim doc ID |
| `streak_freezes` (nakup) | `ShopViewModel.buyStreakFreeze()` → `runTransaction` |
| `streak_freezes` (poraba) | `FirestoreGamificationRepository.consumeStreakFreeze()` → `runTransaction` |
| `burnedCalories` / `waterMl` | `DailyLogRepository.updateDailyLog()` → `runTransaction` (Faza 1+2) |
| Profil nastavitve | `UserProfileManager.saveProfileFirestore()` — **brez** xp/followers/following |

---

## 7. Background Notifikacije
**Ime in lokacija:** `StreakReminderWorker.kt`

**Vhodni podatki (Inputs):**
- **Samo `SharedPreferences` poizvedbe:** Prebira ločene cache lokacije ("water_cache", "calories_cache", "burned_cache", "bm_prefs").

**Logični proces:**
- Odločitvena drevesa za prikaz točnih besedil ob točni uri ("Imaš manj kot X kalorij in manj kot Y vode, naredi še to...").

**Kritična analiza (Težave):**
- 🛑 **Odstopanje od resnice (Out-of-Sync State):** Ta algoritem ignorira Firestore. Če uporabnik vnese kalorije na namizju / widgetu, in se `calories_cache` za notification worker ni osvežil (zaradi različnih providerjev), bo uporabnik dobil neaktualen in zmeden opomin. Pomembni Workerji morajo za branje vedno prisluhniti Single Source of Truth (Firestore).
