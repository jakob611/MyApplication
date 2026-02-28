package com.example.myapplication.persistence

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DailySyncManager — Local-first arhitektura za dnevne podatke.
 *
 * Princip:
 *  - Vsak vnos hrane, vode, kalorij se TAKOJ zapiše lokalno (SharedPreferences).
 *  - Firestore sync se izvede z WorkManager (DailySyncWorker):
 *      → Ko aplikacija gre v ozadje (onPause) — zanesljivo, čaka na omrežje, retry ob napaki
 *      → Ob odprtju — za VČERAJŠNJE podatke (popravek zamujenih syncov)
 */
object DailySyncManager {

    private const val TAG = "DailySyncManager"
    private const val PREFS_SYNC = "daily_sync_prefs"
    private const val KEY_LAST_SYNCED_DATE = "last_synced_date"

    // SharedPreferences ključi (deljeni z NutritionScreen)
    const val PREFS_FOOD = "food_cache"
    const val PREFS_WATER = "water_cache"
    const val PREFS_BURNED = "burned_cache"

    private fun todayStr() = dateStr(Date())
    private fun dateStr(d: Date) = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)
    private fun yesterday(): String {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        return dateStr(cal.time)
    }

    // ───── Lokalno shranjevanje ─────────────────────────────────────────────

    /** Shrani seznam hrane (JSON) lokalno. Kliče se takoj ob vsaki spremembi. */
    fun saveFoodsLocally(context: Context, foodsJson: String, date: String = todayStr()) {
        context.getSharedPreferences(PREFS_FOOD, Context.MODE_PRIVATE)
            .edit().putString("foods_$date", foodsJson).apply()
    }

    /** Preberi lokalno shranjeno hrano za dani dan. */
    fun loadFoodsJson(context: Context, date: String = todayStr()): String? {
        return context.getSharedPreferences(PREFS_FOOD, Context.MODE_PRIVATE)
            .getString("foods_$date", null)
    }

    /** Shrani vodo lokalno. */
    fun saveWaterLocally(context: Context, waterMl: Int, date: String = todayStr()) {
        context.getSharedPreferences(PREFS_WATER, Context.MODE_PRIVATE)
            .edit().putInt("water_$date", waterMl).apply()
    }

    /** Shrani porabljene kalorije lokalno. */
    fun saveBurnedLocally(context: Context, burnedKcal: Int, date: String = todayStr()) {
        context.getSharedPreferences(PREFS_BURNED, Context.MODE_PRIVATE)
            .edit().putInt("burned_$date", burnedKcal).apply()
    }

    // ───── Sync logika ──────────────────────────────────────────────────────

    /** Preveri ali je bil dani datum že sincirano. Internal — kliče ga tudi DailySyncWorker. */
    internal fun isSynced(context: Context, date: String): Boolean {
        return context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SYNCED_DATE, "") == date
    }

    /** Označi dani datum kot sincirano. Internal — kliče ga tudi DailySyncWorker. */
    internal fun markSynced(context: Context, date: String) {
        context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_SYNCED_DATE, date).apply()
    }

    /**
     * Preveri ali obstajajo podatki za dani dan (ne pošiljamo praznih syncov).
     * Internal — kliče ga tudi DailySyncWorker.
     */
    internal fun hasDataForDate(context: Context, date: String): Boolean {
        val water = context.getSharedPreferences(PREFS_WATER, Context.MODE_PRIVATE)
            .getInt("water_$date", 0)
        val burned = context.getSharedPreferences(PREFS_BURNED, Context.MODE_PRIVATE)
            .getInt("burned_$date", 0)
        val foods = context.getSharedPreferences(PREFS_FOOD, Context.MODE_PRIVATE)
            .getString("foods_$date", null)
        return water > 0 || burned > 0 || !foods.isNullOrBlank()
    }

    /**
     * Kliče se ob ODPRTJU aplikacije (iz MainActivity).
     *
     * Razpored — Worker bo sinciiral vse nesincirane datume (yesterday + today):
     *  - Pokliče Worker samo če obstaja vsaj en datum z nesinciranimi podatki
     *  - Worker sam ugotovi katere datume mora sincirati (yesterday, today)
     */
    fun syncOnAppOpen(context: Context, uid: String) {
        val yesterday = yesterday()
        val today = todayStr()

        val needsSync = (!isSynced(context, yesterday) && hasDataForDate(context, yesterday)) ||
                        (!isSynced(context, today) && hasDataForDate(context, today))

        if (needsSync) {
            Log.d(TAG, "syncOnAppOpen: queuing WorkManager (yesterday=$yesterday, today=$today)")
            com.example.myapplication.worker.DailySyncWorker.schedule(context)
        } else {
            Log.d(TAG, "syncOnAppOpen: all data synced — nothing to do")
        }
    }
}
