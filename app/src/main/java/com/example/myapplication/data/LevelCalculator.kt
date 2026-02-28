package com.example.myapplication.data

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

object LevelCalculator {

    /**
     * Calculate level from total XP using quadratic formula to prevent XP farming
     * Formula: level = floor(sqrt(totalXP / 100))
     */
    fun calculateLevel(totalXP: Long): Int {
        if (totalXP <= 0) return 1
        return floor(sqrt(totalXP.toDouble() / 100.0)).toInt().coerceAtLeast(1)
    }

    /**
     * Calculate XP required for next level
     * Formula: nextLevelXP = (level + 1)^2 * 100
     */
    fun xpForNextLevel(currentLevel: Int): Long {
        return ((currentLevel + 1).toDouble().pow(2.0) * 100.0).toLong()
    }

    /**
     * Calculate XP required to reach specific level
     */
    fun xpForLevel(level: Int): Long {
        return (level.toDouble().pow(2.0) * 100.0).toLong()
    }

    /**
     * Calculate current XP within the current level (for progress bar)
     * Returns XP earned in current level out of XP needed to reach next level
     */
    fun currentLevelXP(totalXP: Long): Long {
        val currentLevel = calculateLevel(totalXP)
        val xpForCurrentLevel = xpForLevel(currentLevel)
        return totalXP - xpForCurrentLevel
    }

    /**
     * Calculate XP needed to complete current level
     */
    fun xpNeededForCurrentLevel(currentLevel: Int): Long {
        val nextLevelTotalXP = xpForLevel(currentLevel + 1)
        val currentLevelTotalXP = xpForLevel(currentLevel)
        return nextLevelTotalXP - currentLevelTotalXP
    }

    /**
     * Calculate progress percentage within current level
     */
    fun levelProgressPercentage(totalXP: Long): Float {
        val currentLevel = calculateLevel(totalXP)
        val currentXP = currentLevelXP(totalXP)
        val xpNeeded = xpNeededForCurrentLevel(currentLevel)

        if (xpNeeded <= 0) return 1f
        return (currentXP.toFloat() / xpNeeded.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * Example XP levels:
     * Level 1: 0-99 XP
     * Level 2: 100-399 XP (300 XP needed)
     * Level 3: 400-899 XP (500 XP needed)
     * Level 4: 900-1599 XP (700 XP needed)
     * Level 5: 1600-2499 XP (900 XP needed)
     * Level 10: 10000-12099 XP (2100 XP needed)
     * Level 25: 62500-67599 XP (5100 XP needed)
     * Level 50: 250000-260099 XP (10100 XP needed)
     */
}
