package com.example.myapplication.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * UPP Design System — Figma Brand Colors (SSOT)
 *
 * Vse barve aplikacije morajo izhajati iz tega objekta.
 * NE hardcodaj hex vrednosti po zaslonih — vedno uporabi UppColors.XYZ.
 *
 * Figma specifikacija:
 *   Background:      #181818  (temno sivo — ozadje)
 *   Orange:          #FF6411  (primarni akcijski gumb, CTA)
 *   Blue:            #648DE5  (sekundarno info, statistike, "Reset password" link)
 *   LightGray:       #E0E2DB  (obrobe kartic, vhodna polja, sekundarno besedilo)
 *   White:           #FCFCFC  (primarna besedila, naslovi)
 *   CardSurface:     #222222  (ozadje kartic / modulov)
 *   InputSurface:    #1E1E1E  (ozadje vhodnih polj)
 *   Divider:         #2C2C2C  (razdelilne črte)
 *   Error:           #FF4444  (napake)
 */
object UppColors {

    // ── Primer ──────────────────────────────────────────────────────────────
    val Background   = Color(0xFF181818)
    val CardSurface  = Color(0xFF222222)
    val InputSurface = Color(0xFF1E1E1E)
    val Divider      = Color(0xFF2C2C2C)

    // ── Brand ────────────────────────────────────────────────────────────────
    /** Primarni akcijski gumb — "Continue", "Start Workout", "Add Food" */
    val Orange       = Color(0xFFFF6411)
    val OrangeLight  = Color(0xFFFF8C52)  // hover / ripple verzija

    /** Sekundarna info barva — statistike, Reset Password link */
    val Blue         = Color(0xFF648DE5)

    // ── Tekst ────────────────────────────────────────────────────────────────
    /** Primarni naslovi in ključne vrednosti */
    val White        = Color(0xFFFCFCFC)

    /** Sekundarno besedilo, obrobe kartic, napisi vhodnih polj */
    val LightGray    = Color(0xFFE0E2DB)
    val MutedText    = Color(0xFF9E9E9E)

    // ── Stanje ───────────────────────────────────────────────────────────────
    val Error        = Color(0xFFFF4444)
    val Success      = Color(0xFF4CAF50)
    val Warning      = Color(0xFFFFC107)

    // ── Gradients ────────────────────────────────────────────────────────────
    /**
     * Primarni UPP gradient — samo za naslovne module (např. "UPP", "Dnevni Pregled").
     * NE používaj za gumbe ali kartice.
     */
    val OrangeGradient = Brush.verticalGradient(
        colors = listOf(Orange, Color(0xFFBC3A00))
    )

    val OrangeToBlackGradient = Brush.verticalGradient(
        colors = listOf(Color(0xCCFF6411), Color(0xFF181818))
    )

    val SplashGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xCC8B2500),
            Color(0xCC3D1200),
            Background
        )
    )

    // ── Google gumb (ostane svetel po Material spec) ──────────────────────
    val GoogleButtonBg     = Color(0xFFFFFFFF)
    val GoogleButtonBorder = Color(0xFFDDDDDD)
    val GoogleButtonText   = Color(0xFF333333)
}

