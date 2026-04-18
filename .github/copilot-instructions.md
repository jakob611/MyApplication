🚨 KRITIČNI PROTOKOL ZA COPILOT (Vrini tole)
Vedno piši v slovenščini.

⛔ STOP — PREDEN KARKOLI NAREDIŠ
ANTI-WIPE: Nikoli ne premikaj več kot 1 datoteke hkrati. Pred shranjevanjem preveri, da vsebina ni prazna.

BREZ TERMINALSKIH MV/CP/RM: Za premikanje uporabi IZKLJUČNO vgrajen edit ali prosi uporabnika.

STRIKTNO MCP ORODJE: Za build in Git uporabi IZKLJUČNO moj-android-tools. Pozabi na ./gradlew v terminalu.

🛠️ MCP DELOVNI TOK (Obvezen vrstni red)
Po vsakem popravku moraš izvesti:

get_errors na vseh spremenjenih datotekah.

start_android_build (preko MCP).

check_build_results (čez 30s) — ne ustavi se, dokler ne vidiš "BUILD SUCCESSFUL".

git_commit_and_push — šele ko je build ZELEN (hkrati doda spremembe in pusha na remote).

check_git_status — za preverjanje stanja v terminalu.

📋 PREVERI DOKUMENTACIJO (Brez izjem)
Pred začetkom preberi: CODE_ISSUES.md, REFACTORING_ROADMAP.md in APP_MAP.md. Če jih nisi prebral, se USTAVI.

🏗️ ARHITEKTURNA PRAVILA
Firestore: Uporabljaj FirestoreHelper.getCurrentUserDocRef().

XP/Achievements: Uporabljaj AchievementStore.awardXP() in getBadgeProgress().

Badge: Uporabljaj badge.requirement, ne hardcode vrednosti.

🔍 ISKANJE IN VERIFIKACIJA
Vedno grep -rn "vzorec" --include="*.kt" app/src/main/java/.

Po popravku z grep preveri, če klicana funkcija DEJANSKO obstaja v projektu.

Posodobi CODE_ISSUES.md in FEATURE_LOG.md po vsakem uspešnem pushu.