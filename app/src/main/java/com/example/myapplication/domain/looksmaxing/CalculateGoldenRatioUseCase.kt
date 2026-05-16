package com.example.myapplication.domain.looksmaxing

import com.example.myapplication.domain.looksmaxing.models.*
import com.example.myapplication.domain.math.Point2D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * FAZA 11 — Looksmaxing Engine Algorithmic Overhaul
 *
 * Normalizacija: avtomatska prek razdalje zenica (marker 15 LEFT_EYE ↔ 16 RIGHT_EYE).
 * Edini vir resnice: calculateAdvancedGoldenRatio() — weightedScore = Beauty Score.
 * calculateBeautyScore() ODSTRANJENA (fantomski markerji 21, 25).
 *
 * Markerji (usklajeni z AndroidMLKitFaceDetector):
 *  1  = vrh glave/čelo (bounds.top center)
 *  15 = LEFT_EYE
 *  16 = RIGHT_EYE
 *  19 = levi rob obraza (bounds.left center)
 *  20 = desni rob obraza (bounds.right center)
 *  23 = NOSE_BASE
 *  27 = MOUTH_LEFT
 *  29 = MOUTH_RIGHT
 *  31 = MOUTH_BOTTOM
 *  33 = brada (bounds.bottom center)
 */
class CalculateGoldenRatioUseCase {

    fun distance(p1: Point2D, p2: Point2D): Double = p1.distanceTo(p2).toDouble()

    /**
     * Glavna analiza.
     * Normalizer = razdalja zenica (marker 15 ↔ 16) — Resolution Invariance.
     * Če kateri koli od markerjev 15/16 manjka ali je razdalja 0.0, vrne prazen rezultat.
     */
    fun calculateAdvancedGoldenRatio(
        markers: Map<Int, Point2D>
    ): GoldenRatioAnalysis {
        val phi = (1 + sqrt(5.0)) / 2

        // --- Avtomatska normalizacija prek zenica ---
        val leftEye = markers[15]
        val rightEye = markers[16]
        if (leftEye == null || rightEye == null) return GoldenRatioAnalysis(emptyList(), 1.0)
        val normalizer = distance(leftEye, rightEye)
        if (normalizer == 0.0) return GoldenRatioAnalysis(emptyList(), 1.0)

        // --- Proporci (vsi markerji usklajeni z MLKit detektorjem) ---
        // Marker 1 = vrh glave/čelo (nadomešča nekdanji marker 11, ki ga detektor ne pozna)
        val proportions = listOf(
            Proportion("Središče-zgornja/spodnja", Pair(1, 31), Pair(31, 33), weight = 1.0),
            Proportion("Središče-nos/usta", Pair(23, 31), Pair(31, 33), weight = 1.2),
            Proportion("Širina obraza / razdalja med zenicama", Pair(19, 20), Pair(15, 16), weight = 1.5),
            Proportion("Višina nosu / širina nosu", Pair(1, 23), Pair(15, 16), weight = 1.3),
            Proportion("Višina zgornje ustnice / spodnje ustnice", Pair(23, 31), Pair(31, 33), weight = 1.0),
            // Novi proporec — popolna simetrija = 1.0 (ne phi)
            Proportion("Asimetrija ust (Levo/Desno od nosu)", Pair(27, 23), Pair(29, 23), weight = 1.4),
        )

        val ratioResults = proportions.mapNotNull { prop ->
            val p1 = markers[prop.d1.first]
            val p2 = markers[prop.d1.second]
            val p3 = markers[prop.d2.first]
            val p4 = markers[prop.d2.second]
            // Varno preskoči, če kateri koli marker manjka
            if (p1 == null || p2 == null || p3 == null || p4 == null) return@mapNotNull null

            val d1 = distance(p1, p2) / normalizer
            val d2 = distance(p3, p4) / normalizer
            // Varno preskoči deljenje z ničlo
            if (d2 == 0.0) return@mapNotNull null

            val ratio = d1 / d2
            // Asimetrija → ideal = 1.0 (popolna simetrija); vsi ostali → ideal = phi
            val ideal = if (prop.name.contains("Asimetrija")) 1.0 else phi
            val deviation = abs(ratio - ideal) / ideal
            val score = max(0.0, 1.0 - deviation)
            RatioResult(prop.name, ratio, deviation, score, ideal, prop.weight)
        }

        val totalWeight = ratioResults.sumOf { it.weight }
        val weightedScore = if (totalWeight > 0)
            ratioResults.sumOf { it.score * it.weight } / totalWeight
        else 0.0

        return GoldenRatioAnalysis(ratioResults, weightedScore)
    }

    /**
     * Pretvori ročne mere v sintetične markerje.
     * Pokriva vse markerje, ki jih proportions seznam potrebuje:
     * 1, 15, 16, 19, 20, 23, 27, 29, 31, 33
     */
    fun manualMeasurementsToMarkers(measurements: Map<String, Float>): Map<Int, Point2D> {
        val markers = mutableMapOf<Int, Point2D>()
        val faceHeight = measurements["face_height"] ?: 180f
        val faceWidth = measurements["face_width"] ?: 140f
        val foreheadHeight = measurements["forehead_height"] ?: 60f
        val noseHeight = measurements["nose_height"] ?: 50f
        val lowerFaceHeight = measurements["lower_face_height"] ?: 70f

        val centerX = faceWidth / 2f

        // Marker 1 = vrh glave/čelo (nadomešča marker 11 — detektor ga ne pozna)
        markers[1]  = Point2D(centerX, 0f)
        markers[33] = Point2D(centerX, faceHeight)

        // Nos
        val noseY = foreheadHeight + noseHeight * 0.8f
        markers[23] = Point2D(centerX, noseY)

        // Usta
        val mouthY = faceHeight - lowerFaceHeight * 0.5f
        markers[31] = Point2D(centerX, mouthY)
        // MOUTH_LEFT / MOUTH_RIGHT — razpon ≈ 30% širine obraza od centra
        val mouthHalfWidth = faceWidth * 0.15f
        markers[27] = Point2D(centerX - mouthHalfWidth, mouthY)
        markers[29] = Point2D(centerX + mouthHalfWidth, mouthY)

        // Robovi obraza
        markers[19] = Point2D(0f, faceHeight * 0.5f)
        markers[20] = Point2D(faceWidth, faceHeight * 0.5f)

        // Zenice — potrebne za normalizator!
        val eyeY = foreheadHeight
        markers[15] = Point2D(centerX - 20f, eyeY)
        markers[16] = Point2D(centerX + 20f, eyeY)

        return markers
    }
}
