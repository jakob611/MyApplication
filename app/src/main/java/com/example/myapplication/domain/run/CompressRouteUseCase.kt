package com.example.myapplication.domain.run
class CompressRouteUseCase {
    operator fun invoke(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val limit = 100
        if (points.size <= limit) return points
        val step = points.size / limit.toDouble()
        val compressed = mutableListOf<Pair<Double, Double>>()
        for (i in 0 until limit) {
            compressed.add(points[(i * step).toInt()])
        }
        if (!compressed.contains(points.last())) {
            compressed.add(points.last())
        }
        return compressed
    }
}
