package com.example.myapplication.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// --- Custom FontFamily: Inter (ali Montserrat, po želji) ---
// Najprej dodaj font datoteke v res/font/ (inter_regular.ttf, inter_bold.ttf, ...)
val Inter = FontFamily.Default

val DrawerBlue = Color(0xFF2563EB)


// --- Barvna shema (temna in svetla) ---
private val DarkColors = darkColorScheme(
    primary = Color(0xFFDCE4FF),      // Svetlo pastelna modra za temno temo
    onPrimary = Color(0xFF38305A),
    secondary = Color(0xFFFCF5C7),    // Rumena
    onSecondary = Color(0xFF38305A),
    tertiary = Color(0xFFF37B50),
    onTertiary = Color.White,
    background = Color(0xFF14121F),   // Zelo temno modra/vijolična
    onBackground = Color(0xFFE6E5EA), // Bledo bela
    surface = Color(0xFF26223B),      // Prigušena mornarska za kartice v dark mode
    onSurface = Color(0xFFE6E5EA),
    surfaceVariant = Color(0xFF363251),
    onSurfaceVariant = Color(0xFFD1D5DB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF38305A),      // Temno modra/vijolična
    onPrimary = Color(0xFFFCFBF8),
    secondary = Color(0xFFDCE4FF),    // Pastelno vijolična
    onSecondary = Color(0xFF38305A),
    tertiary = Color(0xFFF37B50),     // Lososovo oranžna
    onTertiary = Color.White,
    background = Color(0xFFFCFBF8),   // Smetanasto bela
    onBackground = Color(0xFF38305A), // Temno modra
    surface = Color.White,            // Bele kartice za večji kontrast
    onSurface = Color(0xFF38305A),    // Temno modra teksta
    surfaceVariant = Color(0xFFDCE4FF),// Pastelno vijolična preplastitev
    onSurfaceVariant = Color(0xFF38305A),
)

// --- Oblike (Shapes) ---
val AppShapes = androidx.compose.material3.Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
)

// --- Tipografija ---
val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Light mode as default
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}