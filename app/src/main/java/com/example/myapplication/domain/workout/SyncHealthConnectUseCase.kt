package com.example.myapplication.domain.workout

import android.content.Context
import android.util.Log
import com.example.myapplication.data.daily.DailyLogRepository
import com.example.myapplication.health.HealthConnectManager
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toLocalDateTime

/**
 * SyncHealthConnectUseCase — VARNO SINHRONIZIRA kalorije iz Health Connect v Firestore.
 *
 * ARHITEKTURNE ODLOČITVE:
 * ─────────────────────────────────────────────────────────────────
 * 1. BREZ SharedPreferences: Referenčna vrednost za delto je shranjena v
 *    Firestore polju [hcBurnedCalories], ne lokalno. To preprečuje "Wipe Bug"
 *    (brisanje podatkov aplikacije ali menjava naprave ne povzroči podvojitve).
 *
 * 2. ATOMARNA TRANSAKCIJA: Celotna logika (branje, izračun, pisanje) se izvede
 *    znotraj [DailyLogRepository.updateDailyLog] → Firestore Transaction.
 *    Ni več [SetOptions.merge()] ali [FieldValue.increment()] izven transakcije.
 *
 * 3. PODPORA NEGATIVNI DELTI: Če uporabnik izbriše trening v Health Connect,
 *    se delta izračuna pravilno (negativna) in [burnedCalories] se ustrezno zmanjša.
 *
 * DATA FLOW:
 *   HealthConnect API → totalHcKcal (dnevni seštevek)
 *   Firestore [hcBurnedCalories] → previousHcKcal (referenca za delto)
 *   delta = totalHcKcal - previousHcKcal
 *   Firestore [burnedCalories] += delta  (nova skupna vrednost)
 *   Firestore [hcBurnedCalories] = totalHcKcal  (posodobitev reference)
 */
class SyncHealthConnectUseCase {

    private val dailyLogRepo = DailyLogRepository()

    suspend operator fun invoke(context: Context) {
        val healthManager = HealthConnectManager.getInstance(context)
        if (!healthManager.isAvailable() || !healthManager.hasAllPermissions()) {
            return
        }

        try {
            val now = Clock.System.now().toJavaInstant()
            val tz = TimeZone.currentSystemDefault()
            val startOfDay = Clock.System.now().toLocalDateTime(tz).date
            val startOfDayJavaObj = java.time.LocalDate.of(
                startOfDay.year, startOfDay.monthNumber, startOfDay.dayOfMonth
            ).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            val todayId = startOfDay.toString()

            // Preberi skupne HC kalorije za danes iz Health Connect API
            val totalHcKcal = healthManager.readCalories(startOfDayJavaObj, now)

            // Če HC ne poroča nobenih kalorij, ni kar sinhronizirati
            if (totalHcKcal <= 0) return

            // Atomarna transakcija: branje + izračun + pisanje v enem koraku
            dailyLogRepo.updateDailyLog(todayId) { data ->

                // Referenčna vrednost zadnje HC sinhronizacije — zdaj v FIRESTORE, ne SharedPrefs!
                // Ob Wipe/Reset je vrednost 0 v bazi → delta = vse HC kalorije (pravilno)
                val previousHcKcal = (data["hcBurnedCalories"] as? Number)?.toDouble() ?: 0.0
                val currentBurned  = (data["burnedCalories"]   as? Number)?.toDouble() ?: 0.0

                // Delta podpira negativne vrednosti (brisanje treningov v Health Connect)
                val delta = totalHcKcal.toDouble() - previousHcKcal

                // Direktni overwrite reference (ne increment!) — vedno točna vrednost
                data["hcBurnedCalories"] = totalHcKcal.toDouble()

                // Prilagodi skupne kalorije za delto; ne more biti negativna
                data["burnedCalories"] = (currentBurned + delta).coerceAtLeast(0.0)

                Log.d("SyncHealthConnect",
                    "HC Sync OK | previousHc=$previousHcKcal kcal → newHc=$totalHcKcal kcal | " +
                    "delta=$delta | newBurned=${data["burnedCalories"]}")
            }

        } catch (e: Exception) {
            Log.e("SyncHealthConnect", "Sinhronizacija Health Connect spodletela", e)
        }
    }
}
