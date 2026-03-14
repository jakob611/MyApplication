package com.example.myapplication.utils

/**
 * Ramer-Douglas-Peucker algoritem za kompresijo GPS polilinij.
 *
 * Zmanjša število točk npr. 450 → ~35 točk brez vidne vizualne razlike na zemljevidu.
 * Epsilon = 0.00005 (~5.5m) je optimalna vrednost za tek/hojo/kolesarjenje.
 *
 * Izračun storage prihrankov:
 *   Surove točke (30 min tek, GPS vsake 2s): ~450 točk × 45B = ~20 KB
 *   Po RDP (epsilon=0.00005):               ~35 točk  × 45B = ~1.6 KB
 *   Prihranek: ~92% manj reads/writes v Firestoreu
 */
object RouteCompressor {

    /**
     * Kompresija s RDP algoritmom.
     * @param points  seznam (lat, lng) točk
     * @param epsilon razdalja praga v stopinjah (~0.00001° = ~1.1m, 0.00005° = ~5.5m)
     */
    fun compress(
        points: List<Pair<Double, Double>>,
        epsilon: Double = 0.00005
    ): List<Pair<Double, Double>> {
        if (points.size <= 2) return points
        return rdp(points, epsilon)
    }

    private fun rdp(
        points: List<Pair<Double, Double>>,
        epsilon: Double
    ): List<Pair<Double, Double>> {
        if (points.size <= 2) return points

        val first = points.first()
        val last = points.last()

        // Poišči točko z največjo pravokotno razdaljo od daljice first→last
        var maxDist = 0.0
        var maxIdx = 0
        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], first, last)
            if (dist > maxDist) {
                maxDist = dist
                maxIdx = i
            }
        }

        // Rekurzivno razdeli če je max razdalja večja od epsilona
        return if (maxDist > epsilon) {
            val left  = rdp(points.subList(0, maxIdx + 1), epsilon)
            val right = rdp(points.subList(maxIdx, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    /**
     * Pravokotna razdalja točke P od daljice AB (v stopinjah, dovolj natančno za kratke razdalje).
     */
    private fun perpendicularDistance(
        p: Pair<Double, Double>,
        a: Pair<Double, Double>,
        b: Pair<Double, Double>
    ): Double {
        val (px, py) = p
        val (ax, ay) = a
        val (bx, by) = b

        val dx = bx - ax
        val dy = by - ay

        if (dx == 0.0 && dy == 0.0) {
            // A in B sta ista točka
            return Math.sqrt((px - ax) * (px - ax) + (py - ay) * (py - ay))
        }

        // t = projekcija P na daljico AB (normalizirana)
        val t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)
        val tClamped = t.coerceIn(0.0, 1.0)

        val nearestX = ax + tClamped * dx
        val nearestY = ay + tClamped * dy

        return Math.sqrt((px - nearestX) * (px - nearestX) + (py - nearestY) * (py - nearestY))
    }
}

