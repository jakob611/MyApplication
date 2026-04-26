package com.example.myapplication

import android.app.Application
import android.util.Log
import com.example.myapplication.data.settings.AndroidSettingsProvider
import com.example.myapplication.domain.settings.SettingsManager
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import org.osmdroid.config.Configuration
import java.io.File

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ── Faza 14: OSMdroid Tile Cache konfiguracija ────────────────────────────
        // Centralna nastavitev — velja za vse MapView instance v aplikaciji.
        // 50 MB disk cache + 100 tiles v pomnilniku → dramatično zmanjša omrežne zahteve.
        try {
            val osmConfig = Configuration.getInstance()
            osmConfig.userAgentValue = packageName
            osmConfig.osmdroidTileCache   = File(cacheDir, "osm_tile_cache")
            osmConfig.tileFileSystemCacheMaxBytes   = 50L * 1024 * 1024   // 50 MB max disk
            osmConfig.tileFileSystemCacheTrimBytes  = 40L * 1024 * 1024   // trim na 40 MB
            osmConfig.cacheMapTileCount             = 100.toShort()        // RAM cache: 100 tiles
            osmConfig.cacheMapTileOvershoot         = 16.toShort()         // pred-naloži 16 ekstra
            Log.i("MyApplication", "✅ OSMdroid tile cache nastavljen: 50MB disk, 100 tiles RAM")
        } catch (e: Exception) {
            Log.e("MyApplication", "OSMdroid config error: ${e.message}")
        }
        // ──────────────────────────────────────────────────────────────────────────

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
