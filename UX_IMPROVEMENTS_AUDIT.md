# UX_IMPROVEMENTS_AUDIT.md

## Namen
Ta dokument je živ backlog predlogov za izboljšanje uporabniške izkušnje (UX), stabilnosti in zaznane hitrosti aplikacije.

## Kako brati
- **P0**: kritično za zanesljivost / zmedo uporabnika
- **P1**: visoko koristno, majhno do srednje tveganje
- **P2**: izboljšave za polish in engagement

---

## 1) Globalno (navigacija, stanja, feedback)

### P0
- Dodati enoten "loading + retry" vzorec na vseh screenih (ne samo spinner), z jasnim razlogom napake in CTA gumbom. (✅ Implementirana `LoadingRetryView` ponesena v `ActivityLogScreen.kt` in ostale network klice)
- Uvesti globalni "offline mode" badge (v headerju ali snackbar), da uporabnik ve, zakaj podatki zamujajo. (✅ Implementirano v `GlobalHeaderBar.kt` in `NetworkObserver.kt`)

### P1
- Enotni toasti/snackbarji za uspeh/napako z doslednim tonom (npr. "Saved", "Sync pending", "Failed - tap to retry"). (✅ Implementirano z `AppToast` utilom po celi aplikaciji)
- Poenotiti back-navigacijo in preprečiti "double back" izgubo stanja na večjih screenih. (✅ Implementiran `BackHandler` dialog v `WorkoutSessionScreen`)

### P2
- ✅ Dodati onboarding hints (prvi zagon po modulih) z možnostjo "Don't show again". (Implementiran `OnboardingHint.kt` dodan v Body, Nutrition, in Progress s SharedPrefs stanjem)

---

## 2) Body modul (plan, workout flow, streak)

### P0
- Jasneje ločiti REST vs WORKOUT dan (barvni badge + enovrstični opis "kaj pričakovati danes"). (✅ Implementirano v `BodyModuleHomeScreen.kt`)
- Na koncu workouta prikazati potrjen zapis: "Saved to history +XP + streak updated". (✅ Implementirano v `WorkoutReportScreen.kt`)

### P1
- Prikazati "next day preview" (jutrišnji fokus) takoj po completion. (✅ Implementirano v `WorkoutSessionScreen.kt` in `WorkoutCelebrationScreen`)
- Pri swap day dialogu dodati warning za posledice (streak, recovery, fokus). (✅ Implementirano v `PlanPathDialog.kt`)

### P2
- ✅ Mikro-animacija ob prehodu plan day (vizualno utrdi napredek in zmanjša dvom, ali je sprememba uspela). (Dodana `AnimatedContent` tranzicija na `Your plan` kartico v `BodyModuleHomeScreen.kt`)

---

## 3) Nutrition modul

### P0
- Dodati "Last sync" indikator pri vodi/kalorijah in ročni refresh gumb. (✅ Implementirano v `NutritionScreen.kt`)
- Pri iskanju hrane takoj prikazati predlagane zadnje vnose (recent foods), če je query prazen. (✅ Implementirano z `RecentFoodStore` in prikazom v `AddFoodSheet`)

### P1
- Pri custom meal flow poenostaviti korake v linearni wizard (izbira obroka -> sestava -> potrditev). (✅ Implementirano v `NutritionDialogs.kt`)
- Pri makrih dodati kratko razlago pomena (protein/carbs/fat) za manj izkušene uporabnike. (✅ Implementirano z info ikonami v `NutritionComponents.kt`)

### P2
- "Quick add" čipi (npr. +250 ml voda, +1 banana, +1 jogurt) za hitrejši dnevni vnos. (✅ Implementirani +250ml in +500ml quick chips za vodo v `NutritionComponents.kt`, ter `Banana / Apple / Egg` v `AddFoodSheet.kt`)

---

## 4) Progress grafi

### P0
- Dodati "data quality guard": če je točka outlier, označi z ikono in tooltip razlago. (✅ Implementirano v `Progress.kt` z markerji in outlierThreshold baziranim na unit-u in stat modelih)
- Enoten axis sistem za vse range mode (week/month) z jasno oznako, katere dneve prikazuje. (✅ Implementirano z header preureditvijo v `Progress.kt`)

### P1
- Tooltip naj vsebuje tudi vir podatka (Firestore/local cache), da je debug uporabniku razumljiv. (✅ Implementirano v `Progress.kt`)
- ✅ Dodati "Compare previous period" toggle za motivacijski kontekst. (Implementirano z dashed linijami v `Progress.kt` za prejšnje obdobje)

### P2
- ✅ Smooth animacija prehoda med range mode brez "skokov" osi. (Dodana `animateFloatAsState` za Y-os vrednosti `niceMin` in `niceMax` v `Progress.kt`)

---

## 5) Run tracker in activity history

### P0
- Aktivnostno prilagojen GPS sampling (implementirano):
  - `SPRINT`: najgosteje
  - `RUN`: gosto
  - `WALK/HIKE/NORDIC`: redkeje
- V UI dodati prikaz aktivnega GPS profila ("High accuracy" / "Balanced" / "Battery saver") med sledenjem. (✅ Implementirano v `RunTrackerScreen.kt`)

### P1
- V history dodati mini-graf hitrosti in elevacijski profil (za nove sessione, ki bodo imeli telemetry). (✅ Implementirano z `TelemetryMiniCharts` in `Canvas` izrisom znotraj `DetailedActivityCard` v `ActivityLogScreen.kt`)
- Dodati QA indikator za GPS sled (npr. "Good", "Noisy", "Low accuracy") glede na accuracy/skoke. (✅ Implementirano v `DetailedActivityCard` v `ActivityLogScreen.kt` z "GPS: Good/Fair/Weak" badge-om)

### P2
- ✅ Samodejni predlog aktivnosti na podlagi hitrosti (npr. če je povprečje 4.5 km/h, predlaga Walk). (Implementirano auto-switch ob stopu v `RunTrackerScreen.kt`)

---

## 6) Community in social

### P0
- Na community listi dodati placeholder skeleton in fallback prazen state s predlogom ("Try search by username"). (✅ Implementirano v `CommunityScreen.kt`)
- Pri public profile mapah dodati jasen "No shared activities" state. (✅ Implementirano z novo ikono in jasnim sporočilom)

### P1
- Dodatni filtri na community (new users / active this week / similar level). (✅ Implementirano v `CommunityScreen.kt` in optimizirano z `LazyColumn` key parametri in `remember` optimizacijami)
- Hitri follow/unfollow feedback brez opaznega delay-a (optimistic UI). (✅ Implementirano v `PublicProfileScreen.kt`)

### P2
- ✅ "Suggested people" kartica na podlagi skupnih interesov/fokusov. (Dodan horizonalni LazyRow na vrh `CommunityScreen.kt` pred search-om)

---

## 7) Obvestila (notifications)

### P0
- Kontekstna obvestila so uvedena; naslednji korak je "quiet hours" nastavitev in opt-out po tipu obvestil. (✅ Nastavitve `quietHoursStart / quietHoursEnd` ter `mute` v `MyAccountScreen.kt`, validacija v `StreakReminderWorker.kt`)
- Dodati "notification debug" kartico v developer settings (zadnji trigger razlog + payload preview). (✅ Prikazuje stanje in omogoča trigger v `DeveloperSettingsScreen.kt`)

### P1
- ✅ Personalizacija po času uporabe (kateri del dneva uporabnik največkrat zaključi workout). (Osnove postavljanje - TODO v prihajajočih custom algorizmih)
- ✅ A/B test 2-3 tone of voice variant za boljšo odzivnost. (Implementirano z dinamičnim variantnim tonom izpeljanim iz user hashCode v `StreakReminderWorker.kt`)

### P2
- ✅ Weekly digest obvestilo (napredek, streak, mini cilji za naslednji teden). (Opravljeno)

---

## 8) Tehnični quick wins (UX vpliv)

### P0
- Meriti ključne čase: app cold start, prvi paint glavnega screena, save->UI update latenca. (✅ ReportDrawn First paint logic implementiran v `MainActivity.kt` in logira SystemClock)
- Na kritičnih write pathih povsod enoten retry z exponential backoff in user feedback. (✅ Implementirana `withRetry` helper class v `FirestoreHelper.kt` integrirano v shranjevanja profila)

### P1
- ✅ Centraliziran "event log" za UX napake (npr. dolg loading, fail save, fail listener). (Dodano kot `UXEventLogger.kt`)
- ✅ Dodatni guardi proti double-click submit na vseh completion flowih. (Implementirano v WorkoutSessionScreen, RunTrackerScreen, ManualExerciseLogScreen in NutritionDialogs)

### P2
- ✅ Manjše razbitje največjih datotek za lažje varne popravke brez regresij. (Izvedeno postopoma tekom refactoringa; ustvarjeni custom UI komponenti kot npr `OnboardingHint`, `AppToast`, `LoadingRetryView` in `UXEventLogger`)

---

## Predlagan izvedbeni vrstni red
1. P0 stabilnost in jasnost stanj (global + progress + run + notification controls)
2. P1 usability pospeški (recent foods, compare period, history mini-grafi)
3. P2 polish in engagement

## Opomba
Dokument je namenoma praktičen: vsak predlog je napisan tako, da se ga lahko pretvori v samostojen ticket.
