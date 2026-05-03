package com.example.myapplication.map

import android.util.Log
import com.example.myapplication.data.LocationPoint
import com.example.myapplication.utils.RouteCompressor

/**
 * MapboxMapMatcher — stara implementacija je klicala Mapbox Map Matching REST API.
 *
 * Mapbox API je bil odstranjen (Faza 2, 2026-05-03) ker:
 *  1. Zahteva BuildConfig.MAPBOX_PUBLIC_KEY (zunanji ključ = varnostno tveganje v APK)
 *  2. Vsak klic = 1 roundtrip omrežnega klica (~200-500ms) × število chunkov
 *  3. Bila je edina zunanja odvisnost; OkHttp ostaja za FatSecret API
 *
 * Nadomestilo: `RouteCompressor.compress()` — lokalni RDP algoritem.
 *  - Odpravlja GPS šum z geometrijsko redukcijo točk
 *  - Ni "snap to road", a precej ublaži GPS skoke pri teku
 *  - 0 ms omrežne latence, deluje offline
 */
object MapboxMapMatcher {
    private const val TAG = "MapboxMapMatcher"

    /**
     * Nadomestilo za stari Mapbox API klic.
     * Vrne RDP-komprimirano pot (lokalno, brez omrežja).
     */
    suspend fun matchRoute(
        points: List<LocationPoint>,
        isRunning: Boolean = true
    ): List<LocationPoint> {
        if (points.size < 2) return points
        val compressed = RouteCompressor.compress(points, epsilon = 2.0)
        Log.d(TAG, "Local RDP: ${points.size} → ${compressed.size} točk (brez Mapbox API)")
        return compressed
    }
}
