package com.example.myapplication.data.settings

import android.content.Context
import android.util.Log
import com.example.myapplication.domain.settings.SettingsManager
import com.example.myapplication.domain.settings.SettingsProvider
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * TODO: Migration to pure KMP. This is currently tightly coupled with Context.
 */
class UserPreferencesRepository(private val context: Context) {

    // Using Multiplatform Settings instead of SharedPreferences directly
    private val settings: Settings = com.russhwolf.settings.SharedPreferencesSettings(context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE))

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

    fun clearAllSettings() {
        settings.clear()
        setFreshStartOnLogin(true)
    }
}

