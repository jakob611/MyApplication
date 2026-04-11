package com.example.myapplication.domain.looksmaxing.models

import com.example.myapplication.domain.math.Point2D

data class DetectedFaceData(
    val markers: Map<Int, Point2D>,
    val imageWidth: Int,
    val imageHeight: Int
)

data class Proportion(
    val name: String,
    val d1: Pair<Int, Int>,
    val d2: Pair<Int, Int>,
    val weight: Double = 1.0
)

data class RatioResult(
    val name: String,
    val ratio: Double,
    val deviation: Double,
    val score: Double,
    val ideal: Double,
    val weight: Double
)

data class GoldenRatioAnalysis(
    val ratios: List<RatioResult>,
    val weightedScore: Double
)

