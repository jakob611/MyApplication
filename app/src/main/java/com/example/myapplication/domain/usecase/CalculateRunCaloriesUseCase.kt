package com.example.myapplication.domain.usecase

import com.example.myapplication.domain.model.ActivityType
import com.example.myapplication.domain.model.ProfileConstants

/**
 * Faza 58 — SSOT za izračun porabljenih kalorij med kardio aktivnostjo.
 *
 * Ekstrakcija iz UI layer-ja (RunTrackerScreen) v skladu z UDF arhitekturnim pravilom.
 * UI ne sme vsebovati poslovne logike — ta UseCase je edina avtoriteta za energetski izračun.
 *
 * ## Algoritem (MET formula):
 *   kcal = MET × teža_kg × čas_v_urah + elevBonus
 *
 * ## Popravki (Faza 58 Audit):
 *   - FIX V-1: HIKE MET je fiksen (6.0). elevationGainM je ODSTRANJEN iz MET baze —
 *     vzponski strošek se upošteva izključno prek elevBonus (enkratno štetje).
 *   - FIX V-2: CYCLING MET interpolira gladko med pragovi. Odpravlja 50% stopničaste
 *     skoke ki so nastajali pri prehodu med fiksnimi MET razredi (<15, <20, <25 km/h).
 *   - Dinamični MET za SKIING, SNOWBOARD, SKATING, NORDIC (prej: statičen 5.0).
 *   - ProfileConstants.DEFAULT_WEIGHT_KG kot edini in standardiziran fallback.
 *
 * KMP-ready: brez Android/Firebase odvisnosti.
 */
class CalculateRunCaloriesUseCase {

    /**
     * Vhodni parametri za izračun kalorij.
     *
     * @param activityType    Tip aktivnosti
     * @param durationSeconds Trajanje v sekundah (surovi čas brez pavz)
     * @param distanceKm      Prevožena razdalja v kilometrih
     * @param elevationGainM  Skupni vzpon v metrih (0 za ravninske aktivnosti)
     * @param userWeightKg    Telesna teža uporabnika v kg; 0 oz. neveljavna vrednost
     *                        sproži fallback na [ProfileConstants.DEFAULT_WEIGHT_KG]
     */
    data class Input(
        val activityType: ActivityType,
        val durationSeconds: Long,
        val distanceKm: Double,
        val elevationGainM: Float,
        val userWeightKg: Double
    )

    /**
     * Izvede izračun porabljenih kalorij.
     *
     * @return Izračunane kalorije v kcal (celo število, nikoli negativno).
     */
    operator fun invoke(input: Input): Int {
        // ── 1. Varna teža ──────────────────────────────────────────────────────────
        // ProfileConstants.DEFAULT_WEIGHT_KG kot edini standardiziran fallback (Faza 58).
        val safeWeight = when {
            input.userWeightKg <= 0.0 || input.userWeightKg.isNaN() -> ProfileConstants.DEFAULT_WEIGHT_KG
            else -> input.userWeightKg
        }.coerceIn(30.0, 200.0)

        // ── 2. Povprečna hitrost (brez deljenja z nič) ────────────────────────────
        val avgSpeedKmh: Float = if (input.durationSeconds > 0 && input.distanceKm > 0.0) {
            (input.distanceKm / (input.durationSeconds / 3600.0)).toFloat()
        } else {
            0f
        }

        // Omejimo hitrost na realistično GPS območje glede na tip aktivnosti
        val safeSpeed: Float = when (input.activityType) {
            ActivityType.SKIING, ActivityType.SNOWBOARD -> avgSpeedKmh.coerceIn(0f, 100f)
            ActivityType.CYCLING                        -> avgSpeedKmh.coerceIn(0f, 65f)
            ActivityType.SKATING                        -> avgSpeedKmh.coerceIn(0f, 50f)
            else                                        -> avgSpeedKmh.coerceIn(0f, 35f)
        }

        // ── 3. MET vrednost ───────────────────────────────────────────────────────
        val met: Double = calculateMet(input.activityType, safeSpeed)

        // ── 4. Osnovna kalorična vrednost ─────────────────────────────────────────
        val hours = input.durationSeconds / 3600.0
        val base = met * safeWeight * hours

        // ── 5. Vzponski bonus ─────────────────────────────────────────────────────
        // FIX V-1: elevationGainM vstopa SAMO sem, ne v MET formulo (preprečuje dvojno štetje).
        // ~0.8 kcal/m vzpona za standardno težo 70 kg, skaliran z dejansko težo.
        // Velja samo za aktivnosti z vertikalno komponento (showElevation = true).
        // Omejitev na 2000 m preprečuje GPS artefakte.
        val elevBonus: Double = if (input.activityType.showElevation) {
            val safeElevation = input.elevationGainM.coerceIn(0f, 2000f)
            safeElevation * 0.8 * (safeWeight / 70.0)
        } else {
            0.0
        }

        return (base + elevBonus).toInt().coerceAtLeast(0)
    }

    // ── Interna MET logika ──────────────────────────────────────────────────────────
    // Vse vrednosti temeljijo na standardnih ACSM/Compendium of Physical Activities tabelah.
    // Linearna interpolacija preprečuje stopničaste skoke pri spremembi hitrosti.

    private fun calculateMet(activity: ActivityType, speedKmh: Float): Double = when (activity) {

        // Tek: MET 4–16+ glede na hitrost (Compendium kode 12000–12110)
        ActivityType.RUN -> when {
            speedKmh < 4f  -> speedKmh.toDouble()                          // 0 → 4 (hoja)
            speedKmh < 8f  -> 4.0 + (speedKmh - 4f) * 0.5                 // 4 → 6
            speedKmh < 10f -> 6.0 + (speedKmh - 8f) * 1.0                 // 6 → 8
            speedKmh < 12f -> 8.0 + (speedKmh - 10f) * 1.0                // 8 → 10
            speedKmh < 14f -> 10.0 + (speedKmh - 12f) * 0.75              // 10 → 11.5
            else           -> 11.5 + (speedKmh - 14f) * 0.4               // 11.5+
        }

        // Hoja: MET 2.0–5.5 (Compendium kode 17000–17200)
        ActivityType.WALK -> when {
            speedKmh < 3f -> 2.0 + (speedKmh / 3f) * 0.5                  // 2.0 → 2.5
            speedKmh < 5f -> 2.5 + (speedKmh - 3f) * 0.5                  // 2.5 → 3.5
            speedKmh < 7f -> 3.5 + (speedKmh - 5f) * 0.5                  // 3.5 → 4.5
            else          -> 4.5 + (speedKmh - 7f) * 0.3                   // 4.5+
        }

        // Sprint: MET 8–18+ (visoka intenzivnost, Compendium 12170–12190)
        ActivityType.SPRINT -> when {
            speedKmh < 10f -> 8.0
            speedKmh < 16f -> 8.0  + (speedKmh - 10f) * 0.83              // 8 → 13
            speedKmh < 20f -> 13.0 + (speedKmh - 16f) * 0.75              // 13 → 16
            else           -> 16.0 + (speedKmh - 20f) * 0.5               // 16+
        }

        // FIX V-1: HIKE MET = 6.0 (bazna vrednost za zmerni pohod s povprečnim terenom).
        // elevationGainM je popolnoma ODSTRANJEN iz te formule.
        // Vzponski energetski strošek se obračuna ENKRAT in izključno v elevBonus zgoraj.
        ActivityType.HIKE -> 6.0

        // FIX V-2: CYCLING — gladka linearna interpolacija (Compendium kode 01000–01090)
        // Pred popravkom: stopničasti pragovi (<15→4, <20→6, <25→8) so povzročali
        // 50% skok MET pri prehodu praga (npr. 14.9→4.0 vs. 15.1→6.0 MET).
        ActivityType.CYCLING -> when {
            speedKmh < 10f -> 2.5 + (speedKmh / 10f) * 1.5                // 2.5 → 4.0
            speedKmh < 20f -> 4.0 + (speedKmh - 10f) * 0.4                // 4.0 → 8.0
            speedKmh < 30f -> 8.0 + (speedKmh - 20f) * 0.35               // 8.0 → 11.5
            else           -> 11.5 + (speedKmh - 30f) * 0.25              // 11.5+
        }

        // Smučanje: dinamični MET (prej statičen 5.0) — Compendium kode 03000–03030
        ActivityType.SKIING -> when {
            speedKmh < 10f -> 4.0 + (speedKmh / 10f) * 1.5               // 4.0 → 5.5 (lahka proga)
            speedKmh < 20f -> 5.5 + (speedKmh - 10f) * 0.25              // 5.5 → 8.0 (zmerna proga)
            speedKmh < 40f -> 8.0 + (speedKmh - 20f) * 0.2               // 8.0 → 12.0 (hitro)
            else           -> 12.0                                          // 12.0 cap (racing/strmina)
        }

        // Snowboard: tehnika > hitrost, zmerni MET — Compendium koda 03015
        ActivityType.SNOWBOARD -> when {
            speedKmh < 15f -> 4.5 + (speedKmh / 15f) * 1.0               // 4.5 → 5.5
            speedKmh < 30f -> 5.5 + (speedKmh - 15f) * 0.13              // 5.5 → 7.5
            else           -> 7.5                                           // stabilna zgornja meja
        }

        // Rolanje/drsanje: aeroben šport z visokim MET — Compendium koda 15620
        ActivityType.SKATING -> when {
            speedKmh < 10f -> 5.0 + (speedKmh / 10f) * 2.5               // 5.0 → 7.5
            speedKmh < 20f -> 7.5 + (speedKmh - 10f) * 0.45              // 7.5 → 12.0
            else           -> 12.0 + (speedKmh - 20f) * 0.2              // 12.0+ (sprint)
        }

        // Nordijska hoja: palice dvignejo MET nad navadni hoji — Compendium koda 17151
        ActivityType.NORDIC -> when {
            speedKmh < 5f  -> 4.0 + (speedKmh / 5f) * 1.0                // 4.0 → 5.0
            speedKmh < 10f -> 5.0 + (speedKmh - 5f) * 0.5                // 5.0 → 7.5
            else           -> 7.5 + (speedKmh - 10f) * 0.3               // 7.5+
        }
    }
}

