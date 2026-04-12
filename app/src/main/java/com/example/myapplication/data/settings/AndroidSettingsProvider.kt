package com.example.myapplication.data.settings

import android.content.Context
import com.example.myapplication.domain.settings.SettingsProvider
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings

class AndroidSettingsProvider(private val context: Context) : SettingsProvider {
    override fun getSettings(name: String): Settings {
        return SharedPreferencesSettings(context.getSharedPreferences(name, Context.MODE_PRIVATE))
    }
}

