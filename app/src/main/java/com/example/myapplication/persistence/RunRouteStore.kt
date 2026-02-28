package com.example.myapplication.persistence

import android.content.Context
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

    /** Shrani seznam lat/lng točk za dani sessionId */
    fun saveRoute(context: Context, sessionId: String, points: List<Pair<Double, Double>>) {
        if (points.isEmpty()) return
        val arr = JSONArray()
        points.forEach { (lat, lng) ->
            arr.put(JSONObject().apply {
                put("lat", lat)
                put("lng", lng)
            })
        }
        routeFile(context, sessionId).writeText(arr.toString())
    }

    /** Naloži seznam lat/lng točk za dani sessionId (ali null če ne obstaja) */
    fun loadRoute(context: Context, sessionId: String): List<Pair<Double, Double>>? {
        val f = routeFile(context, sessionId)
        if (!f.exists()) return null
        return try {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                Pair(obj.getDouble("lat"), obj.getDouble("lng"))
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
