package com.example.myapplication.domain.settings

import com.russhwolf.settings.Settings

interface SettingsProvider {
    fun getSettings(name: String): Settings
}

