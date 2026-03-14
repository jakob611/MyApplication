Vedno piši v slovenščini.

## ⛔ STOP — PREDEN KARKOLI NAREDIŠ

Izvedi VSE 3 korake. Ne preskočiš nobenega:

```
[ ] 1. Preberi CODE_ISSUES.md       → da veš kaj je bilo popravljeno
[ ] 2. Preberi REFACTORING_ROADMAP.md → da veš kje smo
[ ] 3. Preberi APP_MAP.md           → da veš katere datoteke so relevantne
```

Če katerega nisi prebral → USTAVI SE in ga preberi. Ni izjeme.

---

## ⛔ STOP — PO VSAKEM POPRAVKU

```
[ ] 1. grep "fun imeKlicane" → ali klicana funkcija DEJANSKO OBSTAJA?
[ ] 2. get_errors na vsaki datoteki ki si jo spremenil
[ ] 3. ./gradlew assembleDebug 2>&1 | tail -50 → preberi OUTPUT DO KONCA
[ ] 4. Posodobi CODE_ISSUES.md in FEATURE_LOG.md
```

Ne reci "done" dokler nisi naredil vseh 4 korakov.

---

## ⛔ PRAVILO ZA TERMINAL

Terminal VEDNO vrača output. Če dobiš prazen output ali "null":
- To JE rezultat — ga preberi
- NIKOLI ne napiši "terminal ne vrača outputa"
- Zaženi: `./gradlew assembleDebug 2>&1 | tail -50` za jasen output

---

## ARHITEKTURNA PRAVILA (ne smeš kršiti)

| Kaj | Pravilno | Prepovedano |
|-----|----------|-------------|
| Firestore profil write | `FirestoreHelper.getCurrentUserDocRef()` | `db.collection("users").document(uid)` direktno |
| XP | `AchievementStore.awardXP()` | `addXPWithCallback()` |
| Badge zahteve | `badge.requirement` | hardcoded števila |
| Badge progress | `AchievementStore.getBadgeProgress()` | lokalna when() logika |

---

## DELOVNI POSTOPEK (v tem vrstnem redu, brez preskakovanja)

1. Preberi CODE_ISSUES.md + REFACTORING_ROADMAP.md + APP_MAP.md
2. Poišči datoteko: `grep -rn "vzorec" --include="*.kt" app/src/main/java/`
3. Preberi datoteko preden jo spreminjaš
4. Naredi popravek
5. Preveri da vsaka klicana `fun` obstaja z grep
6. `get_errors` na vsaki spremenjeni datoteki
7. `./gradlew assembleDebug 2>&1 | tail -50` — preberi output
8. Posodobi CODE_ISSUES.md in FEATURE_LOG.md

---

## ISKANJE TEŽAV

VEDNO grep čez celotno kodo — nikoli ročno po eni datoteki:
```
grep -rn "vzorec" --include="*.kt" app/src/main/java/
```
