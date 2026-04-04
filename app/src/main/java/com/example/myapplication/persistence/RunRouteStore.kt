package com.example.myapplication.persistence

import android.content.Context
import com.example.myapplication.data.LocationPoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Lokalno shranjevanje GPS poti tekov.
 * Shrani v app-private datoteko (ne Firestore) → brez stroškov.
 * Format: JSON datoteka po sessionId v app files dir.
 */
object RunRouteStore {

    private fun routeFile(context: Context, sessionId: String): File {
        val dir = File(context.filesDir, "run_routes")
        dir.mkdirs()
        return File(dir, "$sessionId.json")
    }

    /** Shrani seznam lokacij in telemetrije za dani sessionId */
    fun saveRoute(context: Context, sessionId: String, points: List<LocationPoint>) {
        if (points.isEmpty()) return
        val arr = JSONArray()
        points.forEach { pt ->
            arr.put(JSONObject().apply {
                put("lat", pt.latitude)
                put("lng", pt.longitude)
                put("alt", pt.altitude)
                put("spd", pt.speed.toDouble())
                put("acc", pt.accuracy.toDouble())
                put("ts", pt.timestamp)
            })
        }
        routeFile(context, sessionId).writeText(arr.toString())
    }

    /** Naloži seznam lokacij za dani sessionId (ali null če ne obstaja) */
    fun loadRoute(context: Context, sessionId: String): List<LocationPoint>? {
        val f = routeFile(context, sessionId)
        if (!f.exists()) return null
        return try {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                LocationPoint(
                    latitude = obj.getDouble("lat"),
                    longitude = obj.getDouble("lng"),
                    altitude = obj.optDouble("alt", 0.0),
                    speed = obj.optDouble("spd", 0.0).toFloat(),
                    accuracy = obj.optDouble("acc", 0.0).toFloat(),
                    timestamp = obj.optLong("ts", 0L)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Ali obstaja ruta za dani sessionId */
    fun hasRoute(context: Context, sessionId: String): Boolean =
        routeFile(context, sessionId).exists()

    /** Zbriši ruto za dani sessionId */
    fun deleteRoute(context: Context, sessionId: String) {
        routeFile(context, sessionId).delete()
    }

    /** Vrni seznam vseh shranjenih sessionId-jev */
    fun getAllSessionIds(context: Context): List<String> {
        val dir = File(context.filesDir, "run_routes")
        return dir.listFiles()?.map { it.nameWithoutExtension } ?: emptyList()
    }
}
