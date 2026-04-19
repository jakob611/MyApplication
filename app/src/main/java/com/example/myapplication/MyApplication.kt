package com.example.myapplication

import android.app.Application
import com.example.myapplication.data.settings.AndroidSettingsProvider
import com.example.myapplication.domain.settings.SettingsManager

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize KMP SettingsManager before any ViewModels or Activities try to access it.
        SettingsManager.provider = AndroidSettingsProvider(this)
    }
}

