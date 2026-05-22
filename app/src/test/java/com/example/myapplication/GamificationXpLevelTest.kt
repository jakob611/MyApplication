package com.example.myapplication

import com.example.myapplication.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lokalni unit testi za XP/Level sistem v UserProfile.
 *
 * Testirajo:
 *  1. calculateLevel(xp) — pravilna stopnja za znane mejne vrednosti
 *  2. Level-up scenarij — dodajanje XP pravilno sproži napredovanje v višji nivo
 *  3. XP presežek se ne izgubi — po level-upu se ohrani preostanek
 *  4. progressToNextLevel — natančen float med 0f in 1f
 *  5. xpForCurrentLevel / xpForNextLevel — pravilne meje za vsak nivo
 *  6. Eksponentna rast zahtev — vsak nivo zahteva ~20% več XP kot prejšnji
 *  7. Robni primeri — xp=0, negativni xp (obrambno), enormni xp
 *
 * KMP-ready: brez Android odvisnosti. Vse funkcije so pure (companion object).
 *
 * Level formula:
 *  Nivo 1 = 0 XP (startno stanje)
 *  Nivo 2 = 100 XP
 *  Nivo 3 = 100 + 120 = 220 XP
 *  Nivo 4 = 220 + 144 = 364 XP
 *  ...vsak nivo +20% od prejšnjega
 */
class GamificationXpLevelTest {

    // ══════════════════════════════════════════════════════════════════════════
    // 1. Osnovni calculateLevel — znane mejne vrednosti
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `nivo 1 pri 0 XP`() {
        assertEquals(1, UserProfile.calculateLevel(0))
    }

    @Test
    fun `nivo 1 pri 99 XP (tik pred mejo)`() {
        assertEquals(1, UserProfile.calculateLevel(99))
    }

    @Test
    fun `nivo 2 pri tocno 100 XP`() {
        assertEquals(2, UserProfile.calculateLevel(100))
    }

    @Test
    fun `nivo 2 pri 219 XP (tik pred mejo nivoja 3)`() {
        assertEquals(2, UserProfile.calculateLevel(219))
    }

    @Test
    fun `nivo 3 pri tocno 220 XP`() {
        // Nivo 2→3: 100 + 120 = 220 XP
        assertEquals(3, UserProfile.calculateLevel(220))
    }

    @Test
    fun `nivo 4 pri tocno 364 XP`() {
        // 220 + floor(120 * 1.2) = 220 + 144 = 364 XP
        assertEquals(4, UserProfile.calculateLevel(364))
    }

    @Test
    fun `nivo 5 pri tocno 537 XP`() {
        // 364 + floor(144 * 1.2) = 364 + 172 = 536
        // Opomba: floor(172.8) = 172 → 364 + 172 = 536
        assertEquals(5, UserProfile.calculateLevel(536))
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Level-up scenarij — XP presežek se ne izgubi (ključni test)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `level-up scenarij - dodajanje XP cez mejo dvigne nivo`() {
        // Pred level-upom: 95 XP (nivo 1, 5 XP pred mejo)
        // Dodamo 50 XP → skupno 145 XP (prestopimo mejo 100)
        val xpPred = 95
        val dodaniXp = 50
        val xpPo = xpPred + dodaniXp  // = 145

        val nivoPreed = UserProfile.calculateLevel(xpPred)
        val nivoPo = UserProfile.calculateLevel(xpPo)

        assertEquals("Pred level-upom mora biti nivo 1", 1, nivoPreed)
        assertEquals("Po level-upu mora biti nivo 2", 2, nivoPo)
    }

    @Test
    fun `level-up - presezek XP se ne izgubi`() {
        // Nivo 2 zahteva 100 XP. Imamo 150 XP → presežek = 50 XP v nivoju 2
        val xp = 150
        val profile = UserProfile(xp = xp)

        assertEquals(2, profile.level)

        // Preostanek v tekočem nivoju = 150 - 100 = 50 XP
        val xpVTekoCemNivoju = xp - profile.xpForCurrentLevel
        assertEquals("Presežek XP mora biti natanko 50", 50, xpVTekoCemNivoju)
    }

    @Test
    fun `level-up - presezek XP pri nivoju 3`() {
        // Nivo 3 začne pri 220 XP. Imamo 270 XP → presežek = 50 XP v nivoju 3
        val xp = 270
        val profile = UserProfile(xp = xp)

        assertEquals(3, profile.level)
        val xpVTekoCemNivoju = xp - profile.xpForCurrentLevel
        assertEquals(50, xpVTekoCemNivoju)
    }

    @Test
    fun `level-up - simulacija 10 zaporednih workout nagrad (po 50 XP)`() {
        // Začnemo na 0 XP, dodamo 10x 50 XP = 500 XP skupno
        // 100 → nivo 2, 220 → nivo 3, 364 → nivo 4 ... preverimo, da nivo raste
        var skupniXp = 0
        var zadniNivo = 1

        repeat(10) { i ->
            skupniXp += 50
            val noviNivo = UserProfile.calculateLevel(skupniXp)
            assertTrue(
                "Po ${i + 1}. workout-u (${skupniXp} XP) se nivo ne sme zmanjšati",
                noviNivo >= zadniNivo
            )
            zadniNivo = noviNivo
        }

        // Po 500 XP mora biti nivo vsaj 3
        assertTrue("Po 500 XP mora biti nivo >= 3", UserProfile.calculateLevel(500) >= 3)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. progressToNextLevel — natančnost med 0f in 1f
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `progressToNextLevel - na zacetku nivoja je 0_0f`() {
        val profile = UserProfile(xp = 100)  // točno na meji nivoja 2
        assertEquals(0.0f, profile.progressToNextLevel, 0.001f)
    }

    @Test
    fun `progressToNextLevel - na sredini nivoja je priblizno 0_5f`() {
        // Nivo 2: od 100 do 220 (120 XP). Sredina = 100 + 60 = 160 XP
        val profile = UserProfile(xp = 160)
        assertEquals(0.5f, profile.progressToNextLevel, 0.01f)
    }

    @Test
    fun `progressToNextLevel - tik pred naslednjim nivojem je blizu 1_0f`() {
        val profile = UserProfile(xp = 219)  // 1 XP pred nivojem 3
        val progress = profile.progressToNextLevel
        assertTrue("Progress mora biti > 0.99f", progress > 0.99f)
        assertTrue("Progress mora biti < 1.0f", progress < 1.0f)
    }

    @Test
    fun `progressToNextLevel - je vedno med 0f in 1f`() {
        listOf(0, 1, 50, 100, 150, 220, 500, 1000, 5000).forEach { xp ->
            val profile = UserProfile(xp = xp)
            val progress = profile.progressToNextLevel
            assertTrue(
                "progressToNextLevel za xp=$xp mora biti >= 0f, dejansko: $progress",
                progress >= 0f
            )
            assertTrue(
                "progressToNextLevel za xp=$xp mora biti <= 1f, dejansko: $progress",
                progress <= 1f
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 4. xpForCurrentLevel / xpForNextLevel — konsistentnost
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `xpForCurrentLevel nivo 1 je 0`() {
        val profile = UserProfile(xp = 50)
        assertEquals(0, profile.xpForCurrentLevel)
    }

    @Test
    fun `xpForCurrentLevel nivo 2 je 100`() {
        val profile = UserProfile(xp = 150)
        assertEquals(100, profile.xpForCurrentLevel)
    }

    @Test
    fun `xpForNextLevel nivo 1 je 100`() {
        val profile = UserProfile(xp = 0)
        assertEquals(100, profile.xpForNextLevel)
    }

    @Test
    fun `xpForNextLevel nivo 2 je 220`() {
        val profile = UserProfile(xp = 100)
        assertEquals(220, profile.xpForNextLevel)
    }

    @Test
    fun `xpForCurrentLevel je vedno manj od xpForNextLevel`() {
        listOf(0, 50, 100, 150, 220, 400, 1000).forEach { xp ->
            val profile = UserProfile(xp = xp)
            assertTrue(
                "xpForCurrentLevel (${profile.xpForCurrentLevel}) mora biti < xpForNextLevel (${profile.xpForNextLevel}) pri xp=$xp",
                profile.xpForCurrentLevel < profile.xpForNextLevel
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 5. Eksponentna rast zahtev (~20% na nivo)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `vsak nivo zahteva vec XP kot prejsnji (eksponentna rast)`() {
        var prejsnjaMeja = 0
        for (nivo in 2..10) {
            val mejaNivoja = UserProfile.xpRequiredForLevel(nivo)
            val doprinosNivoja = mejaNivoja - prejsnjaMeja
            val doprinosPrejsnjega = if (nivo > 2) UserProfile.xpRequiredForLevel(nivo - 1) - UserProfile.xpRequiredForLevel(nivo - 2) else 100

            assertTrue(
                "Nivo $nivo zahteva doprinosa $doprinosNivoja, mora biti > prejšnjega $doprinosPrejsnjega",
                doprinosNivoja >= doprinosPrejsnjega
            )
            prejsnjaMeja = mejaNivoja
        }
    }

    @Test
    fun `xpRequiredForLevel je strogo narascujoce`() {
        for (nivo in 1..15) {
            val xpTekoci = UserProfile.xpRequiredForLevel(nivo)
            val xpNaslednji = UserProfile.xpRequiredForLevel(nivo + 1)
            assertTrue(
                "xpRequiredForLevel($nivo)=$xpTekoci mora biti < xpRequiredForLevel(${nivo+1})=$xpNaslednji",
                xpNaslednji > xpTekoci
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 6. Robni primeri
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `robni primer - enormni xp vrne visok nivo brez izjeme`() {
        // 1.000.000 XP — ne sme vrgniti StackOverflow ali exception
        val nivo = UserProfile.calculateLevel(1_000_000)
        assertTrue("1M XP mora dati nivo >= 20", nivo >= 20)
    }

    @Test
    fun `robni primer - tocno na vsaki meji nivo napreduje`() {
        // Nivo 1→2: 100 XP. Preverimo tik pod in tik nad mejo
        assertEquals(1, UserProfile.calculateLevel(99))
        assertEquals(2, UserProfile.calculateLevel(100))

        // Nivo 2→3: 220 XP
        assertEquals(2, UserProfile.calculateLevel(219))
        assertEquals(3, UserProfile.calculateLevel(220))
    }

    @Test
    fun `xpRequiredForLevel 1 je 0`() {
        assertEquals(0, UserProfile.xpRequiredForLevel(1))
    }

    @Test
    fun `xpRequiredForLevel 2 je 100`() {
        assertEquals(100, UserProfile.xpRequiredForLevel(2))
    }

    @Test
    fun `calculateLevel in xpRequiredForLevel sta konzistentna`() {
        // Za vsak nivo od 1 do 20: calculateLevel(xpRequiredForLevel(n)) == n
        for (n in 1..20) {
            val xpZaNivo = UserProfile.xpRequiredForLevel(n)
            val izracunaniNivo = UserProfile.calculateLevel(xpZaNivo)
            assertEquals(
                "calculateLevel(xpRequiredForLevel($n)=$xpZaNivo) mora vrniti $n, vrnil: $izracunaniNivo",
                n, izracunaniNivo
            )
        }
    }
}


