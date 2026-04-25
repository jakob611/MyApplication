package com.example.myapplication.persistence

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DailySyncManager — Faza 5: Poenostavljen orkestrator sync workerja.
 *
 * Faza 5 sprememba:
 *  - Odstranjeni vsi lokalni cache write-i (saveFoodsLocally, saveWaterLocally itd.)
 *  - Firestore SDK z isPersistenceEnabled = true skrbi za offline delovanje sam
 *  - Ta razred zdaj služi SAMO kot:
 *      1. Koordinator DailySyncWorkerja (schedule ob odprtju)
 *      2. Tracker sinciiranih datumov (da ne dupliciramo requestov)
 *      3. Migracijsko čiščenje starih SharedPrefs cachev
 */
object DailySyncManager {

    private const val TAG = "DailySyncManager"
    private const val PREFS_SYNC = "daily_sync_prefs"
    private const val KEY_SYNCED_DATES = "synced_dates_set"

    private fun todayStr() = dateStr(Date())
    private fun dateStr(d: Date) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)
    private fun yesterday(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return dateStr(cal.time)
    }

    // ───── Sync state tracking ──────────────────────────────────────────────

    /** Preveri ali je bil dani datum že sincirano. Internal — kliče ga tudi DailySyncWorker. */
    internal fun isSynced(context: Context, date: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        val synced = prefs.getStringSet(KEY_SYNCED_DATES, emptySet()) ?: emptySet()
        return date in synced
    }

    /** Označi dani datum kot sincirano. Internal — kliče ga tudi DailySyncWorker. */
    internal fun markSynced(context: Context, date: String) {
        val prefs = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_SYNCED_DATES, emptySet())?.toMutableSet() ?: mutableSetOf()
        existing.add(date)
        // Počisti stare datume (starejše od 7 dni)
        val cutoff = run {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -7)
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        }
        existing.removeAll { it < cutoff }
        prefs.edit().putStringSet(KEY_SYNCED_DATES, existing).apply()
    }

    // ───── Sync orchestration ────────────────────────────────────────────────

    /**
     * Kliče se ob ODPRTJU aplikacije (iz MainActivity).
     * Faza 5: Ker Firestore SDK sam skrbi za offline sync, ta funkcija le sproži
     * DailySyncWorker ki označi datume kot sincirane.
     */
    fun syncOnAppOpen(context: Context, uid: String) {
        Log.d(TAG, "syncOnAppOpen: Firestore je Single Source of Truth — posredovanje DailySyncWorkerju")
        com.example.myapplication.worker.DailySyncWorker.schedule(context)
    }

    // ───── Faza 5: Migracijski čistilec ─────────────────────────────────────

    /**
     * Faza 5 migracija — pobriše stare lokalne SharedPrefs cache datoteke.
     * Kliči enkrat ob prvem zagonu po nadgradnji.
     *
     * Briše:
     *  - water_cache       (waterMl po dnevih)
     *  - burned_cache      (burnedCalories po dnevih)
     *  - calories_cache    (consumedCalories po dnevih)
     *  - food_cache        (foods JSON po dnevih)
     *  - daily_sync_prefs  (sync tracker — počisti izpod starih ključev)
     */
    fun clearLegacyCache(context: Context) {
        val legacyPrefs = listOf("water_cache", "burned_cache", "calories_cache", "food_cache")
        for (name in legacyPrefs) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
        // Počisti stari sync tracker da se fresh start pravilno izvede
        context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE).edit().clear().apply()
        Log.i(TAG, "✅ Faza 5: Stari lokalni SharedPrefs cache počiščen (water/burned/calories/food)")
    }
}
