package com.example.myapplication.domain.math

import kotlin.math.sqrt

/**
 * A KMP-friendly alternative to android.graphics.PointF
 */
data class Point2D(val x: Float, val y: Float) {
    fun distanceTo(other: Point2D): Float {
        val dx = this.x - other.x
        val dy = this.y - other.y
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}

