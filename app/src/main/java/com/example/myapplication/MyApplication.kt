package com.example.myapplication

import android.app.Application
import android.util.Log
import com.example.myapplication.data.settings.AndroidSettingsProvider
import com.example.myapplication.domain.settings.SettingsManager
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ── Faza 5: Firestore Offline Persistence ──────────────────────────────
        // Firestore SDK skrbi za offline delovanje sam — aplikacija ne potrebuje
        // lokalnih JSON/SharedPrefs cachev za water/burned/calories.
        try {
            val persistentCache = PersistentCacheSettings.newBuilder()
                .setSizeBytes(100L * 1024 * 1024) // 100 MB
                .build()
            Firebase.firestore.firestoreSettings =
                com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(persistentCache)
                    .build()
            Log.i("MyApplication", "✅ Firestore persistence nastavljen: 100 MB offline cache")
        } catch (e: Exception) {
            Log.e("MyApplication", "Firestore persistence error: ${e.message}")
        }
        // ──────────────────────────────────────────────────────────────────────

        // Initialize KMP SettingsManager before any ViewModels or Activities try to access it.
        SettingsManager.provider = AndroidSettingsProvider(this)
    }
}
