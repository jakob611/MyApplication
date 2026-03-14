# CODE_ISSUES.md
> **NAVODILO ZA AI:** To datoteko VEDNO preberi na začetku seje. Po vsakem popravku dodaj vnos na dno pod "DNEVNIK POPRAVKOV".

**Zadnja posodobitev:** 2026-03-12  
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

**Še ni prijavljenih težav.**

---

## DNEVNIK POPRAVKOV (VSAK POPRAVEK DODAJ TUKAJ)
- **2026-03-12 (Fix Double Submission)** — V `BodyModuleHomeViewModel` sem dodal `AtomicBoolean` zaščito v `completeWorkoutSession` in `completeRestDayActivity`.
  To preprečuje podvojene zapise v Firestore ob hitrem klikanju (double-click), kar je povzročalo, da se je statistika povečala za 2 namesto za 1.

- **2026-03-10 (Fix Calendar Null Checks)** — CalendarScreen ni pravilno preverjal `planForDay != null` preden je dostopal do `planForDay.isRestDay`.

- **2026-03-12 (Shop & Rewards Implementation)** — Implementiral delujočo `ShopScreen` z nakupom `Streak Freeze` in `Coupon`. Dodal logiko v `AchievementStore` in `UserProfile` za shranjevanje streak freezes.

- **2026-03-12 (Golden Ratio Auto Analysis)** — Implementiral `AutoAnalysisSection` v `GoldenRatioScreen` z uporabo ML Kit Face Detection za avtomatsko analizo in izračun Beauty Score-a. Popravil podvojene deklaracije v datoteki.

- **2026-03-12 (Face Module Features)** — Implementiral dialoge za `Skincare` in `Face Exercises` namesto TODO-jev.

- **2026-03-12 (Build Fixes)** — Popravil `BodyModuleHomeScreen.kt` (mankajoči importi) in odstranil deprecated Material komponente v `ShopScreen`.

- **2026-03-13 (Streak Robustness Fix)** — Implementiral `daily_logs` subcollection v Firestore za robustno beleženje opravljenih dni (workout/rest). `AchievementStore.checkAndUpdatePlanStreak` zdaj preverja `daily_logs` za preprečevanje dvojnega štetja in beleži vsak uspešen dan v zgodovino, kot zahtevano.
