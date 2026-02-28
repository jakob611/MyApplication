package com.example.myapplication.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority

/**
 * Callback interface for location updates.
 */
interface LocationUpdateCallback {
    fun onLocationUpdate(location: Location)
    fun onLocationError(error: String)
}

/**
 * Manager for handling location tracking using FusedLocationProviderClient.
 */
class LocationTracker(
    context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {

    private var locationCallback: LocationCallback? = null
    private var updateCallback: LocationUpdateCallback? = null

    /**
     * Start location updates with balanced accuracy and ~5-second intervals.
     */
    @SuppressLint("MissingPermission")
    fun startLocationUpdates(callback: LocationUpdateCallback) {
        this.updateCallback = callback

        // Create location request: balanced accuracy, 5-second interval
        // Optimized for better battery life and performance
        val locationRequest = LocationRequest.Builder(5000)
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMinUpdateIntervalMillis(3000)  // Increased to 3s for better performance
            .build()

        // Create location callback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    updateCallback?.onLocationUpdate(location)
                }
            }
        }

        // Start location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback!!,
            null  // Use main looper
        )
    }

    /**
     * Stop location updates.
     */
    fun stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback!!)
            locationCallback = null
        }
        updateCallback = null
    }

    /**
     * Get the last known location without starting updates.
     */
    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(callback: (Location?) -> Unit) {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                callback(location)
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                callback(null)
            }
    }
}

