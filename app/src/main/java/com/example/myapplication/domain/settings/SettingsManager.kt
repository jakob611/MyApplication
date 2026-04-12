package com.example.myapplication.domain.settings

object SettingsManager {
    private var _provider: SettingsProvider? = null

    var provider: SettingsProvider
        get() = _provider ?: throw IllegalStateException("SettingsManager strictly needs to be initialized first.")
        set(value) {
            _provider = value
        }
}

