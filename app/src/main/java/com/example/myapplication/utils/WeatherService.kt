package com.example.myapplication.utils

import android.content.Context
import android.location.LocationManager
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import com.example.myapplication.BuildConfig

object WeatherService {

    private val API_KEY = BuildConfig.OPEN_WEATHER_API_KEY

    data class WeatherResult(
        val isNiceWeather: Boolean,
        val tempC: Int,
        val description: String
    )

    private var cachedResult: WeatherResult? = null
    private var lastFetchTime: Long = 0

    suspend fun checkWeatherForRunning(context: Context): WeatherResult {
        // Cache weather for 2 hours to limit API calls
        if (cachedResult != null && System.currentTimeMillis() - lastFetchTime < 2 * 60 * 60 * 1000) {
            return cachedResult!!
        }

        return withContext(Dispatchers.IO) {
            try {
                // Determine location
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                var lat = 46.0569 // Default: Ljubljana
                var lon = 14.5058
                try {
                    val hasPerm = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.ACCESS_FINE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        val providers = locationManager.getProviders(true)
                        var bestLoc: Location? = null
                        for (p in providers) {
                            val l = locationManager.getLastKnownLocation(p) ?: continue
                            if (bestLoc == null || l.accuracy < bestLoc.accuracy) {
                                bestLoc = l
                            }
                        }
                        if (bestLoc != null) {
                            lat = bestLoc.latitude
                            lon = bestLoc.longitude
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                val urlString = "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=$API_KEY"
                val response = URL(urlString).readText()
                val json = JSONObject(response)

                val main = json.getJSONObject("main")
                val tempC = main.getDouble("temp").toInt()

                val weatherArray = json.getJSONArray("weather")
                var description = "Clear"
                var icon = "01d"
                if (weatherArray.length() > 0) {
                    val wObj = weatherArray.getJSONObject(0)
                    description = wObj.getString("main")
                    icon = wObj.getString("icon")
                }

                // Definition of nice weather:
                // Temperature between 5°C and 30°C, and NOT raining/snowing (icon usually not 'd' rain like 09d, 10d, 11d, 13d)
                val isRainingOrSnowing = icon.startsWith("09") || icon.startsWith("10") || icon.startsWith("11") || icon.startsWith("13")
                val isNice = (tempC in 5..28) && !isRainingOrSnowing

                val res = WeatherResult(isNice, tempC, description)
                cachedResult = res
                lastFetchTime = System.currentTimeMillis()
                res
            } catch (e: Exception) {
                e.printStackTrace()
                // Default or fallback
                WeatherResult(false, 0, "Unknown")
            }
        }
    }
}

