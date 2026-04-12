package com.example.myapplication.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ---------------------------------------------------------------
// Pomožna funkcija za razdaljo (Haversine)
// ---------------------------------------------------------------
fun haversineDist(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val r = 6371e3 // meters
    val p1 = lat1 * (kotlin.math.PI / 180.0)
    val p2 = lat2 * (kotlin.math.PI / 180.0)
    val dp = (lat2 - lat1) * (kotlin.math.PI / 180.0)
    val dl = (lon2 - lon1) * (kotlin.math.PI / 180.0)

    val a = sin(dp / 2) * sin(dp / 2) +
            cos(p1) * cos(p2) *
            sin(dl / 2) * sin(dl / 2)

    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (r * c).toFloat()
}

