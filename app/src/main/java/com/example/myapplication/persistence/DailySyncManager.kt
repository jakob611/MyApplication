package com.example.myapplication.persistence

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.json.JSONArray
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

    /** Preveri ali je bil dani datum že sincirano. */
    private fun isSynced(context: Context, date: String): Boolean {
        val synced = context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SYNCED_DATE, "")
        return synced == date
    }

    /** Označi dani datum kot sincirano. */
    private fun markSynced(context: Context, date: String) {
        context.getSharedPreferences(PREFS_SYNC, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_SYNCED_DATE, date).apply()
    }

    /**
     * Preveri ali obstajajo podatki za dani dan (ne pošiljamo praznih syncov).
     */
    private fun hasDataForDate(context: Context, date: String): Boolean {
        val water = context.getSharedPreferences(PREFS_WATER, Context.MODE_PRIVATE)
            .getInt("water_$date", 0)
        val burned = context.getSharedPreferences(PREFS_BURNED, Context.MODE_PRIVATE)
            .getInt("burned_$date", 0)
        val foods = context.getSharedPreferences(PREFS_FOOD, Context.MODE_PRIVATE)
            .getString("foods_$date", null)
        return water > 0 || burned > 0 || !foods.isNullOrBlank()
    }

    /**
     * Pošlje lokalne podatke za izbrani datum v Firestore.
     * Varno za večkratni klic — idempotentno (SetOptions.merge).
     */
    private fun syncDateToFirestore(context: Context, uid: String, date: String, onDone: ((Boolean) -> Unit)? = null) {
        if (!hasDataForDate(context, date)) {
            Log.d(TAG, "No data for $date, skipping sync")
            onDone?.invoke(true)
            return
        }

        val db = Firebase.firestore
        val docRef = db.collection("users").document(uid)
            .collection("dailyLogs").document(date)

        val waterMl = context.getSharedPreferences(PREFS_WATER, Context.MODE_PRIVATE)
            .getInt("water_$date", 0)
        val burnedKcal = context.getSharedPreferences(PREFS_BURNED, Context.MODE_PRIVATE)
            .getInt("burned_$date", 0)
        val foodsJson = loadFoodsJson(context, date)

        val payload = mutableMapOf<String, Any>(
            "date" to date,
            "waterMl" to waterMl,
            "burnedCalories" to burnedKcal,
            "syncedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        if (!foodsJson.isNullOrBlank()) {
            try {
                val arr = JSONArray(foodsJson)
                val items = (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    val map = mutableMapOf<String, Any>()
                    obj.keys().forEach { key -> map[key] = obj.get(key) }
                    map
                }
                if (items.isNotEmpty()) payload["items"] = items
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse foods JSON for $date", e)
            }
        }

        docRef.set(payload, SetOptions.merge())
            .addOnSuccessListener {
                if (date == todayStr()) markSynced(context, date)
                Log.d(TAG, "Sync OK [$date]: water=$waterMl ml, burned=$burnedKcal kcal, foods=${(payload["items"] as? List<*>)?.size ?: 0}")
                onDone?.invoke(true)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Sync FAILED [$date] — will retry later", e)
                onDone?.invoke(false)
            }
    }

    /**
     * Kliče se ob ODPRTJU aplikacije (iz MainActivity).
     * Sinciranle VČERAJŠNJE podatke prek WorkManager-ja, če niso bili syncani.
     * Danes ne syncamo — podatki so šele v nastajanju, WorkManager bo to naredil ob onPause.
     */
    fun syncOnAppOpen(context: Context, uid: String) {
        val yesterday = yesterday()
        if (!isSynced(context, yesterday) && hasDataForDate(context, yesterday)) {
            Log.d(TAG, "syncOnAppOpen: queuing yesterday $yesterday via WorkManager")
            com.example.myapplication.worker.DailySyncWorker.schedule(context)
        } else {
            Log.d(TAG, "syncOnAppOpen: yesterday $yesterday already synced or has no data")
        }
    }
}
