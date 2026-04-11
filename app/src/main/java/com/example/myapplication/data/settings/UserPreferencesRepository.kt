package com.example.myapplication.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * 🚧 TODO (KMP MIGRATION):
 * This repository isolates Android's SharedPreferences.
 * To make the app KMP-ready, replace `SharedPreferences` with `androidx.datastore.preferences.core`
 * or `russhwolf:multiplatform-settings`.
 */
class UserPreferencesRepository(private val context: Context) {

    private val userPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    }

    private val algoPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("algorithm_prefs", Context.MODE_PRIVATE)
    }

    private val widgetPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("app_flags", Context.MODE_PRIVATE)
    }

    // Example methods for migrating shared preference usages

    fun isDarkModeEnabled(): Boolean {
        return userPrefs.getBoolean("dark_mode", true)
    }

    fun setDarkModeEnabled(enabled: Boolean) {
        userPrefs.edit().putBoolean("dark_mode", enabled).apply()
    }

    fun isFreshStartOnLogin(): Boolean {
        return widgetPrefs.getBoolean("fresh_start_on_login", false)
    }

    fun setFreshStartOnLogin(freshStart: Boolean) {
        widgetPrefs.edit().putBoolean("fresh_start_on_login", freshStart).apply()
    }

    fun clearAllSettings() {
        val prefNames = listOf(
            "user_prefs", "body_module", "nutrition_xp", "bm_prefs",
            "smartwatch_prefs", "algorithm_prefs", "weight_widget_prefs",
            "water_widget_prefs", "food_cache", "water_cache", "burned_cache",
            "daily_sync_prefs", "streak_widget_prefs", "app_flags"
        )
        for (name in prefNames) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
        }
        setFreshStartOnLogin(true)
    }
}

