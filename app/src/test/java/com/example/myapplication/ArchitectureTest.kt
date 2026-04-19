package com.example.myapplication

import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * ARHITEKTURNI TESTI — zagotavljajo da AI popravki ne pokvari ključnih invariant.
 *
 * Zaženemo z: ./gradlew test
 * Ko test pade → točno vidiš KAJ je pokvarjeno, brez ročnega iskanja.
 *
 * TESTI:
 *  1. BadgeConsistencyTest  — vsak badge ID v BadgeDefinitions mora imeti case v getBadgeProgress
 *  2. FirestoreRoutingTest  — nobena kt datoteka ne sme pisati direktno na document(email) ali document(uid) za profil
 *  3. DeprecatedXPTest      — addXPWithCallback se ne sme klicati nikjer (razen v sami definiciji)
 */
class ArchitectureTest {

    private val srcDir = File("src/main/java/com/example/myapplication")

    // ─── Pomožne funkcije ──────────────────────────────────────────────────────

    /** Vrne vse .kt datoteke rekurzivno */
    private fun allKtFiles(): List<File> =
        srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Vrne vse vrstice vseh .kt datotek z imenom datoteke */
    private fun allLines(): List<Pair<String, String>> =
        allKtFiles().flatMap { f -> f.readLines().map { f.name to it } }

    /** Beri vsebino posamezne datoteke */
    private fun readFile(relativePath: String): String {
        val f = File("src/main/java/com/example/myapplication/$relativePath")
        return if (f.exists()) f.readText() else ""
    }

    // ─── TEST 1: Badge konsistentnost ──────────────────────────────────────────

    /**
     * Vsak badge ID definiran v BadgeDefinitions.ALL_BADGES mora imeti
     * ustrezen `case` v AchievementStore.getBadgeProgress().
     *
     * Zakaj: če badge ID manjka v getBadgeProgress(), vrne 0 → badge se nikoli ne odklene.
     */
    @Test
    fun `vsak badge ID v BadgeDefinitions mora imeti case v getBadgeProgress`() {
        val badgeDefsContent = readFile("data/BadgeDefinitions.kt")
        val achievementContent = readFile("persistence/AchievementStore.kt")

        assertTrue(
            "BadgeDefinitions.kt ne obstaja ali je prazna",
            badgeDefsContent.isNotBlank()
        )
        assertTrue(
            "AchievementStore.kt ne obstaja ali je prazna",
            achievementContent.isNotBlank()
        )

        // Izvleci vse badge ID-je iz BadgeDefinitions (id = "xxx" vzorec)
        val badgeIdRegex = Regex("""id\s*=\s*"([^"]+)"""")
        val definedIds = badgeIdRegex.findAll(badgeDefsContent)
            .map { it.groupValues[1] }
            .toList()

        assertTrue("BadgeDefinitions ne vsebuje nobenih badge ID-jev", definedIds.isNotEmpty())

        // Izvleci vse ID-je ki so pokriti v getBadgeProgress when-bloku
        val coveredIds = mutableSetOf<String>()
        val quotedIdRegex = Regex(""""([^"]+)"""")
        var inGetBadgeProgress = false
        var braceDepth = 0

        for (line in readFile("persistence/AchievementStore.kt").lines()) {
            if (line.contains("fun getBadgeProgress")) {
                inGetBadgeProgress = true
                braceDepth = 0
            }
            if (inGetBadgeProgress) {
                braceDepth += line.count { it == '{' } - line.count { it == '}' }
                quotedIdRegex.findAll(line).forEach { coveredIds.add(it.groupValues[1]) }
                if (braceDepth <= 0 && line.contains("}")) inGetBadgeProgress = false
            }
        }

        // Preveri da vsak definiran ID ima case
        val missing = definedIds.filter { it !in coveredIds }
        assertTrue(
            "Badge ID-ji NISO pokriti v getBadgeProgress() → ne bodo nikoli odklenjeni:\n" +
                    missing.joinToString("\n") { "  - \"$it\"" },
            missing.isEmpty()
        )
    }

    // ─── TEST 2: Firestore routing ─────────────────────────────────────────────

    /**
     * Nobena datoteka ne sme pisati .set() ali .update() direktno na
     * collection("users").document(<SPREMENLJIVKA>) — razen:
     *   - FirestoreHelper.kt (tam je to legalno — to JE resolver)
     *   - UserPreferences.kt vrstica z deleteUserData (tam je to namerno za brisanje)
     *   - ProfileStore.kt searchPublicProfiles (bere javne profile — ok)
     *
     * Bere samo — get(), addSnapshotListener() je ok (query, ne write).
     * Piše — set(), update(), add() na document(<var>) je napaka.
     */
    @Test
    fun testFirestoreDirectUsage() {
        val exceptions = listOf(
            "FirestoreHelper.kt",           // tu je definiran
            "ProfileStore.kt",      // searchPublicProfiles — bere javne profile
            "FollowStore.kt"        // follows kolekcija — ne users/profil
        )

        // Vzorec: .document(<karkoli>).set( ali .update( ali .add(
        // Iščemo WRITE operacije na document z spremenljivko (ne s fiksnim nizom)
        val writeOnVarDocRegex = Regex(
            """\.document\(\s*(?!"\w)([a-zA-Z_][a-zA-Z0-9_]*)\s*\)\s*\.(set|update|add)\s*\("""
        )

        val violations = mutableListOf<String>()

        allKtFiles()
            .filter { it.name !in exceptions }
            .forEach { file ->
                file.readLines().forEachIndexed { idx, line ->
                    if (writeOnVarDocRegex.containsMatchIn(line)) {
                        // Izključi komentarje
                        val stripped = line.trimStart()
                        if (!stripped.startsWith("//") && !stripped.startsWith("*")) {
                            violations.add("${file.name}:${idx + 1}: $line")
                        }
                    }
                }
            }

        assertTrue(
            "Direktni Firestore WRITE na document(<spremenljivka>) najden — " +
                    "uporabi FirestoreHelper.getCurrentUserDocRef() namesto tega:\n" +
                    violations.joinToString("\n"),
            violations.isEmpty()
        )
    }

    // (Test testUserPreferencesDeprecation odstranjen, ker je UserPreferences.kt pobrisan)
}
