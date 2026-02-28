package com.example.myapplication.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

object HapticFeedback {
    
    enum class FeedbackType {
        LIGHT_CLICK,      // Light tap for simple buttons
        CLICK,            // Standard click
        HEAVY_CLICK,      // Heavy click for important actions
        SUCCESS,          // Success confirmation (e.g., save successful)
        ERROR,            // Error indication
        SELECTION,        // Item selection
        LONG_PRESS,       // Long press feedback
        DOUBLE_TAP,       // Double tap feedback
        DRAWER_OPEN       // Smooth, longer feedback for drawer opening
    }

    // Throttling: prevent vibrations from executing too frequently
    private var lastVibrationTime = 0L
    private const val MIN_VIBRATION_INTERVAL_MS = 50L // Minimum 50ms between vibrations

    private fun getVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun performHapticFeedback(context: Context, type: FeedbackType) {
        // Throttle: skip if too soon after last vibration
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastVibrationTime < MIN_VIBRATION_INTERVAL_MS) {
            return
        }
        lastVibrationTime = currentTime

        val vibrator = getVibrator(context) ?: return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29+) - Modern haptic feedback with predefined effects
            val effect = when (type) {
                FeedbackType.LIGHT_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                FeedbackType.CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                FeedbackType.HEAVY_CLICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                FeedbackType.DRAWER_OPEN -> {
                    // Smooth, longer feedback for drawer - gentle crescendo
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 80, 40, 60),
                            intArrayOf(0, 60, 80, 100),
                            -1
                        )
                    } else {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    }
                }
                FeedbackType.SUCCESS -> {
                    // Custom success pattern: short-long-short
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 50, 50, 100, 50, 50),
                            intArrayOf(0, 80, 0, 180, 0, 80),
                            -1
                        )
                    } else {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    }
                }
                FeedbackType.ERROR -> {
                    // Custom error pattern: double buzz
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 100, 100, 100),
                            intArrayOf(0, 255, 0, 255),
                            -1
                        )
                    } else {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    }
                }
                FeedbackType.SELECTION -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                FeedbackType.LONG_PRESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                FeedbackType.DOUBLE_TAP -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
            }
            vibrator.vibrate(effect)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8+ (API 26+) - VibrationEffect without predefined effects - STRONGER
            val effect = when (type) {
                FeedbackType.LIGHT_CLICK -> VibrationEffect.createOneShot(15, 100)
                FeedbackType.CLICK -> VibrationEffect.createOneShot(30, 150)
                FeedbackType.HEAVY_CLICK -> VibrationEffect.createOneShot(60, 255)
                FeedbackType.DRAWER_OPEN -> VibrationEffect.createWaveform(
                    longArrayOf(0, 80, 40, 60),
                    intArrayOf(0, 60, 80, 100),
                    -1
                )
                FeedbackType.SUCCESS -> VibrationEffect.createWaveform(
                    longArrayOf(0, 60, 60, 120, 60, 60),
                    intArrayOf(0, 120, 0, 220, 0, 120),
                    -1
                )
                FeedbackType.ERROR -> VibrationEffect.createWaveform(
                    longArrayOf(0, 120, 120, 120),
                    intArrayOf(0, 255, 0, 255),
                    -1
                )
                FeedbackType.SELECTION -> VibrationEffect.createOneShot(15, 100)
                FeedbackType.LONG_PRESS -> VibrationEffect.createOneShot(60, 255)
                FeedbackType.DOUBLE_TAP -> VibrationEffect.createWaveform(
                    longArrayOf(0, 40, 60, 40),
                    intArrayOf(0, 200, 0, 200),
                    -1
                )
            }
            vibrator.vibrate(effect)
        } else {
            // Android 7 and below - Simple vibration - STRONGER
            @Suppress("DEPRECATION")
            val duration = when (type) {
                FeedbackType.LIGHT_CLICK -> 15L
                FeedbackType.CLICK -> 30L
                FeedbackType.HEAVY_CLICK -> 60L
                FeedbackType.DRAWER_OPEN -> 120L
                FeedbackType.SUCCESS -> 120L
                FeedbackType.ERROR -> 250L
                FeedbackType.SELECTION -> 15L
                FeedbackType.LONG_PRESS -> 60L
                FeedbackType.DOUBLE_TAP -> 40L
            }
            vibrator.vibrate(duration)
        }
    }

    // Convenience function for View-based haptic feedback
    fun View.performHaptic(type: FeedbackType) {
        performHapticFeedback(context, type)
    }

    // System haptic feedback (uses Android's built-in haptics)
    fun View.performSystemHaptic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }
}

