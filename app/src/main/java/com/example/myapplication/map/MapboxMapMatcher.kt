package com.example.myapplication.map

import android.util.Log
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.LocationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max

object MapboxMapMatcher {
    private const val TAG = "MapboxMapMatcher"
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Map Matching vzame surove GPS točke in jih pripne na najbližje ceste (snap to road).
     * Uporablja Mapbox Direction API "walking" ali "cycling" profil.
     * Naenkrat Max 100 točk, zato poenostavimo listo.
     */
    suspend fun matchRoute(points: List<LocationPoint>, isRunning: Boolean = true): List<LocationPoint> = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext points

        val profile = if (isRunning) "walking" else "cycling"
        // Uporabimo JAVNI ključ namesto skrivnega za klicence iz strank (Android apk)
        val token = BuildConfig.MAPBOX_PUBLIC_KEY
        if (token.isBlank()) {
            Log.e(TAG, "Mapbox public ključ manjka!")
            return@withContext points
        }

        // Split points into chunks of max 90 to allow processing large routes without crazy compression
        val chunks = points.chunked(90)
        val allMatchedPoints = mutableListOf<LocationPoint>()

        for (chunk in chunks) {
            if (chunk.size < 2) {
                allMatchedPoints.addAll(chunk)
                continue
            }
            // Use very light compression just to clean up overlapping points within the small chunk
            var tolerance = 0.00001
            var compressed = compress(chunk.map { Pair(it.latitude, it.longitude) }, tolerance)
            while (compressed.size > 95 && tolerance < 0.005) {
                tolerance += 0.0001
                compressed = compress(chunk.map { Pair(it.latitude, it.longitude) }, tolerance)
            }

            val coordinatesStr = compressed.joinToString(";") { "${it.second},${it.first}" }
            val url = "https://api.mapbox.com/matching/v5/mapbox/$profile/$coordinatesStr" +
                    "?geometries=geojson&tidy=true&overview=full&access_token=$token"

            // Dodamo 'Referer' glavo, sicer Mapbox ne registrira URL restrikcij iz Android klica!
            val request = Request.Builder()
                .url(url)
                .header("Referer", "android-app://com.example.myapplication/")
                .build()

            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Mapbox Error: ${response.code} $body")
                    allMatchedPoints.addAll(chunk)
                    continue
                }

                val json = JSONObject(body)
                val match = json.optJSONArray("matchings")?.optJSONObject(0)
                if (match != null) {
                    val geometry = match.optJSONObject("geometry")
                    val coordsArray = geometry?.optJSONArray("coordinates")
                    if (coordsArray != null) {
                        val totalOriginalTime = chunk.last().timestamp - chunk.first().timestamp
                        val dt = if (coordsArray.length() > 1) totalOriginalTime / (coordsArray.length() - 1).toFloat() else 0f

                        for (i in 0 until coordsArray.length()) {
                            val pt = coordsArray.getJSONArray(i)
                            val lng = pt.getDouble(0)
                            val lat = pt.getDouble(1)
                            val ts = chunk.first().timestamp + (i * dt).toLong()
                            allMatchedPoints.add(LocationPoint(latitude = lat, longitude = lng, timestamp = ts))
                        }
                    } else {
                        allMatchedPoints.addAll(chunk)
                    }
                } else {
                    allMatchedPoints.addAll(chunk)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mapbox call failed for chunk", e)
                allMatchedPoints.addAll(chunk)
            }
        }

        Log.d(TAG, "Mapbox Map Matching Uspešno potekel! Prejšnjih točk: ${points.size}, novih z Mapbox: ${allMatchedPoints.size}")
        return@withContext if (allMatchedPoints.isNotEmpty()) allMatchedPoints else points
    }

    /**
     * Ramer-Douglas-Peucker zmanjševanje točk
     */
    private fun compress(points: List<Pair<Double, Double>>, epsilon: Double): List<Pair<Double, Double>> {
        if (points.size < 3) return points
        var dmax = 0.0
        var index = 0
        val end = points.size - 1

        for (i in 1 until end) {
            val d = perpendicularDistance(points[i], points[0], points[end])
            if (d > dmax) {
                index = i
                dmax = d
            }
        }

        return if (dmax > epsilon) {
            val recResults1 = compress(points.subList(0, index + 1), epsilon)
            val recResults2 = compress(points.subList(index, end + 1), epsilon)
            val result = mutableListOf<Pair<Double, Double>>()
            result.addAll(recResults1.subList(0, recResults1.size - 1))
            result.addAll(recResults2)
            result
        } else {
            listOf(points[0], points[end])
        }
    }

    private fun perpendicularDistance(pt: Pair<Double, Double>, lineStart: Pair<Double, Double>, lineEnd: Pair<Double, Double>): Double {
        val dx = lineEnd.first - lineStart.first
        val dy = lineEnd.second - lineStart.second
        val mag = Math.hypot(dx, dy)
        if (mag > 0.0) {
            val area = Math.abs(dx * (lineStart.second - pt.second) - dy * (lineStart.first - pt.first))
            return area / mag
        }
        return 0.0
    }
}
