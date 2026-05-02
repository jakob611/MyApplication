package com.example.myapplication.data.settings

import android.content.Context
import android.util.Log
import com.example.myapplication.domain.settings.SettingsManager
import com.example.myapplication.domain.settings.SettingsProvider
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * KMP Migration backlog (Faza 10): Ta razred je tesno vezan na Android Context.
 * Migracija na čisto KMP bo mögoča ko bo SettingsManager v celoti zamenjal SharedPreferences.
 * Sledeno v CODE_ISSUES.md — "UserPreferencesRepository KMP migration".
 */
class UserPreferencesRepository(private val context: Context) {

    // Using Multiplatform Settings instead of SharedPreferences directly
    private val settings: Settings = com.russhwolf.settings.SharedPreferencesSettings(context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE))
    private val bmSettings: Settings = com.russhwolf.settings.SharedPreferencesSettings(context.getSharedPreferences("bm_prefs", Context.MODE_PRIVATE))

    // Obstojeći SharedPrefs prenašamo v Settings objekt, ampak trenutno še vedno podpiramo star behavior do popolne menjave

    fun getUserDataChanges(): Flow<com.example.myapplication.data.UserProfile?> {
        return flowOf(null) // Mocked for now - wait for serialization setup or use custom string mapping
    }

    fun getUserId(): String? {
        return settings.getStringOrNull("user_id")
    }

    fun setUserId(userId: String?) {
        if (userId == null) settings.remove("user_id") else settings.putString("user_id", userId)
    }

    fun getUserToken(): String? {
        return settings.getStringOrNull("user_token")
    }

    fun getWeeklyTargetFlow(): Flow<Int> {
        return flowOf(bmSettings.getInt("weekly_target", 0))
    }

    fun getLastWorkoutEpochFlow(): Flow<Long> {
        return flowOf(bmSettings.getLong("last_workout_epoch", 0L))
    }

    fun isWorkoutDoneToday(): Boolean {
        val lastEpoch = bmSettings.getLong("last_workout_epoch", 0L)
        return if (lastEpoch == 0L) false
        else {
            val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val lastDate = kotlinx.datetime.LocalDate.fromEpochDays(lastEpoch.toInt())
            lastDate == now
        }
    }

    fun getPlanDay(): Int {
        return bmSettings.getInt("plan_day", 1)
    }

    fun getWeeklyTarget(): Int {
        return bmSettings.getInt("weekly_target", 3)
    }

    fun setUserToken(token: String?) {
        if (token == null) settings.remove("user_token") else settings.putString("user_token", token)
    }

    fun isDarkModeEnabled(): Boolean {
        return settings.getBoolean("dark_mode", true)
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        settings.putBoolean("dark_mode", enabled)
    }

    fun isFreshStartOnLogin(): Boolean {
        return settings.getBoolean("fresh_start_on_login", false)
    }

    fun setFreshStartOnLogin(freshStart: Boolean) {
        settings.putBoolean("fresh_start_on_login", freshStart)
    }

    /**
     * ⚠️ DELNO DEPRECATED (Faza 9.2): Ta funkcija piše v bm_prefs kot lokalni fallback cache.
     * Resnični SSOT je Firestore prek UserProfileManager.saveWorkoutStats().
     * Ostane funkcionalna samo za offline fallback v GetBodyMetricsUseCase.getPlanDay().
     * Faza 10 backlog: Odstrani bm_prefs zapis ko GetBodyMetricsUseCase ne bo več potreboval localnega fallbacka.
     * (sledeno v CODE_ISSUES.md — "bm_prefs deprecation backlog")
     */
    suspend fun updateWorkoutStats(completedDay: Int, timestamp: Long) {
        bmSettings.putInt("plan_day", completedDay)
        val epochDay = timestamp / (1000 * 60 * 60 * 24)
        bmSettings.putLong("last_workout_epoch", epochDay)
    }

    /**
     * ⚠️ NO-OP — DEPRECATED (Faza 9.1+9.2): Kalorije se zapisujejo prek DailyLogRepository.updateDailyLog().
     * bm_prefs.daily_calories ni več SSOT. Klic je bil odstranjen iz UpdateBodyMetricsUseCase v Fazi 9.1.
     */
    suspend fun updateDailyCalories(calories: Double, timestamp: Long) {
        // NO-OP: SSOT je DailyLogRepository. Bm_prefs zapis je bil ukinjen v Fazi 9.1.
    }

    fun getDailyCalories(): Double {
        val lastSavedEpochDay = bmSettings.getLong("daily_calories_epoch", 0L)
        val todayEpochDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        return if (todayEpochDay == lastSavedEpochDay) {
            bmSettings.getFloat("daily_calories", 0f).toDouble()
        } else {
            0.0
        }
    }

    fun getDailyCaloriesFlow(): Flow<Double> {
        return kotlinx.coroutines.flow.flow {
            val lastSavedEpochDay = bmSettings.getLong("daily_calories_epoch", 0L)
            val todayEpochDay = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
            if (todayEpochDay == lastSavedEpochDay) {
                emit(bmSettings.getFloat("daily_calories", 0f).toDouble())
            } else {
                emit(0.0)
            }
        }
    }

    fun clearAllSettings() {
        settings.clear()
        setFreshStartOnLogin(true)
    }
}
