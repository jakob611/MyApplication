package com.example.myapplication.domain.looksmaxing

import com.example.myapplication.domain.looksmaxing.models.*
import com.example.myapplication.domain.math.Point2D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

// Matematične funkcije prestavljene iz UI! KMP Ready!
class CalculateGoldenRatioUseCase {
    fun distance(p1: Point2D, p2: Point2D): Double {
        return p1.distanceTo(p2).toDouble()
    }

    fun calculateAdvancedGoldenRatio(
        markers: Map<Int, Point2D>,
        normalizeBy: Pair<Int, Int>? = null
    ): GoldenRatioAnalysis {
        val phi = (1 + sqrt(5.0)) / 2

        val proportions = listOf(
            Proportion("Središče-zgornja/spodnja", Pair(11, 31), Pair(31, 33), weight = 1.0),
            Proportion("Središče-nos/usta", Pair(23, 31), Pair(31, 33), weight = 1.2),
            Proportion("Širina obraza / razdalja med zenicama", Pair(19, 20), Pair(15, 16), weight = 1.5),
            Proportion("Višina nosu / širina nosu", Pair(11, 23), Pair(15, 16), weight = 1.3),
            Proportion("Višina zgornje ustnice / spodnje ustnice", Pair(23, 31), Pair(31, 33), weight = 1.0),
        )

        val normalizer = if (normalizeBy != null) {
            val pA = markers[normalizeBy.first]
            val pB = markers[normalizeBy.second]
            if (pA != null && pB != null) distance(pA, pB) else 1.0
        } else 1.0

        val ratioResults = proportions.mapNotNull { prop ->
            val p1 = markers[prop.d1.first]
            val p2 = markers[prop.d1.second]
            val p3 = markers[prop.d2.first]
            val p4 = markers[prop.d2.second]
            if (p1 == null || p2 == null || p3 == null || p4 == null) return@mapNotNull null

            val d1 = distance(p1, p2) / normalizer
            val d2 = distance(p3, p4) / normalizer
            if (d2 == 0.0) return@mapNotNull null

            val ratio = d1 / d2
            val deviation = abs(ratio - phi) / phi
            val score = max(0.0, 1.0 - deviation)
            RatioResult(prop.name, ratio, deviation, score, phi, prop.weight)
        }

        val totalWeight = ratioResults.sumOf { it.weight }
        val weightedScore = if (totalWeight > 0)
            ratioResults.sumOf { it.score * it.weight } / totalWeight
        else 0.0

        return GoldenRatioAnalysis(ratioResults, weightedScore)
    }

    fun manualMeasurementsToMarkers(measurements: Map<String, Float>): Map<Int, Point2D> {
        val markers = mutableMapOf<Int, Point2D>()
        val faceHeight = measurements["face_height"] ?: 180f
        val faceWidth = measurements["face_width"] ?: 140f
        val foreheadHeight = measurements["forehead_height"] ?: 60f
        val noseHeight = measurements["nose_height"] ?: 50f
        val lowerFaceHeight = measurements["lower_face_height"] ?: 70f

        val centerX = faceWidth / 2f
        markers[1] = Point2D(centerX, 0f)
        markers[33] = Point2D(centerX, faceHeight)
        markers[11] = Point2D(centerX, foreheadHeight)
        markers[23] = Point2D(centerX, foreheadHeight + noseHeight * 0.8f)
        markers[31] = Point2D(centerX, faceHeight - lowerFaceHeight * 0.5f)
        markers[19] = Point2D(0f, faceHeight * 0.5f)
        markers[20] = Point2D(faceWidth, faceHeight * 0.5f)
        val eyeY = foreheadHeight
        markers[15] = Point2D(centerX - 20f, eyeY)
        markers[16] = Point2D(centerX + 20f, eyeY)
        return markers
    }

    fun calculateBeautyScore(markers: Map<Int, Point2D>): Double {
        val phi = (1 + sqrt(5.0)) / 2
        val scores = mutableListOf<Double>()

        val proportions = listOf(
            Proportion("Prop1", Pair(11, 31), Pair(31, 33)),
            Proportion("Prop2", Pair(11, 23), Pair(23, 33)),
            Proportion("Prop3", Pair(11, 21), Pair(21, 25)),
        )

        for (prop in proportions) {
            val p1 = markers[prop.d1.first]
            val p2 = markers[prop.d1.second]
            val p3 = markers[prop.d2.first]
            val p4 = markers[prop.d2.second]
            if (p1 == null || p2 == null || p3 == null || p4 == null) continue

            val d1 = distance(p1, p2)
            val d2 = distance(p3, p4)
            if (d2 == 0.0) continue

            val ratio = d1 / d2
            val deviation = abs(ratio - phi) / phi
            val score = max(0.0, 1.0 - deviation)
            scores.add(score)
        }

        return if (scores.isEmpty()) 0.0 else scores.average()
    }
}

