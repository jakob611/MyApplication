package com.example.myapplication.utils

import com.example.myapplication.data.LocationPoint
import kotlin.math.sqrt

/**
 * RouteCompressor — Douglas-Peucker (RDP) algoritem za kompresijo GPS tras.
 *
 * Problem: Firestore dokument ima trdi limit 1 MB. Tek dolžine maratonca (~4h,
 *          GPS 1/s) ustvari ~14.400 točk ≈ 720+ KB, kar ob ostalih poljih
 *          preseže limit in povzroči FAILED_PRECONDITION crash.
 *
 * Rešitev: RDP ohrani obliko poti (zavoji, vzponi) in tipično reducira
 *           3.600 točk (1h tek) na ~80–200, maraton na ~150–400.
 *          Safety cap: po RDP-ju je seznam vedno <= MAX_POINTS.
 *
 * Uporaba:
 *   val compressed = RouteCompressor.compress(finalLocationPoints)
 */
object RouteCompressor {

    /** Maksimalno število točk za Firestore inline shranjevanje.
     *  ~ 500 × ~50B/točko = ~25 KB (varno pod 1 MB limit) */
    const val MAX_POINTS = 500

    /**
     * Kompresija z RDP algoritmom.
     * @param points  vhodna lista GPS točk
     * @param epsilon toleranca v metrih (privzeto 2.0 m — neopazna napaka na karti)
     * @return skrčena lista, garantirano <= MAX_POINTS
     */
    fun compress(
        points: List<LocationPoint>,
        epsilon: Double = 2.0
    ): List<LocationPoint> {
        if (points.size <= 2) return points

        // 1. korak: RDP redukcija
        val rdpResult = rdpReduce(points, epsilon)

        // 2. korak: safety cap — če je RDP še vedno prevelik, vzorčenje
        return if (rdpResult.size <= MAX_POINTS) {
            rdpResult
        } else {
            uniformSample(rdpResult, MAX_POINTS)
        }
    }

    // ── Rekurzivni Douglas-Peucker ─────────────────────────────────────────

    private fun rdpReduce(points: List<LocationPoint>, epsilon: Double): List<LocationPoint> {
        if (points.size < 3) return points

        val first = points.first()
        val last = points.last()

        var maxDist = 0.0
        var maxIndex = 0

        for (i in 1 until points.size - 1) {
            val d = perpendicularDistanceMeters(points[i], first, last)
            if (d > maxDist) {
                maxDist = d
                maxIndex = i
            }
        }

        return if (maxDist > epsilon) {
            val left  = rdpReduce(points.subList(0, maxIndex + 1), epsilon)
            val right = rdpReduce(points.subList(maxIndex, points.size), epsilon)
            // Spoji — izogni se podvajanju točke maxIndex
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    /**
     * Pravokotna razdalja točke P od daljice AB (v metrih, aproksimacija).
     * Za majhne razdalje (<100 km) je napaka zanemarljiva.
     */
    private fun perpendicularDistanceMeters(
        p: LocationPoint,
        a: LocationPoint,
        b: LocationPoint
    ): Double {
        val dx = b.longitude - a.longitude
        val dy = b.latitude  - a.latitude

        if (dx == 0.0 && dy == 0.0) {
            // a == b: vrni razdaljo do točke a
            return haversineMeters(p.latitude, p.longitude, a.latitude, a.longitude)
        }

        // Skaliramo na metre (approximacija)
        val latScale = 111_320.0
        val lngScale = 111_320.0 * cosDeg(a.latitude)

        val ax = 0.0;        val ay = 0.0
        val bx = dx * lngScale; val by = dy * latScale
        val px = (p.longitude - a.longitude) * lngScale
        val py = (p.latitude  - a.latitude)  * latScale

        val len2 = bx * bx + by * by
        val t    = ((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / len2
        val tc   = t.coerceIn(0.0, 1.0)

        val nearX = ax + tc * (bx - ax)
        val nearY = ay + tc * (by - ay)

        return sqrt((px - nearX) * (px - nearX) + (py - nearY) * (py - nearY))
    }

    /** Enakomerno vzorčenje — ohrani prvo in zadnjo točko. */
    private fun uniformSample(points: List<LocationPoint>, max: Int): List<LocationPoint> {
        if (points.size <= max) return points
        val step = (points.size - 1).toDouble() / (max - 1)
        val out = mutableListOf<LocationPoint>()
        for (i in 0 until max) {
            out.add(points[(i * step).toInt().coerceAtMost(points.size - 1)])
        }
        if (out.last() != points.last()) out[out.size - 1] = points.last()
        return out
    }

    // ── Math helpers ───────────────────────────────────────────────────────

    private fun cosDeg(deg: Double): Double =
        Math.cos(Math.toRadians(deg))

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6_371_000.0
        val phi1 = Math.toRadians(lat1); val phi2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
                Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLon / 2).let { it * it }
        return R * 2 * Math.asin(sqrt(a))
    }
}


