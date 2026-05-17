package com.example.myapplication.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// ── Tipografija ──────────────────────────────────────────────────────────────
val Inter = FontFamily.Default

// Ostane za legacy reference, ne vključi v novih datotekah
val DrawerBlue = UppColors.Blue

// ── Figma Dark Color Scheme (edina uradna shema, od Faze 17) ─────────────────
private val UppDarkColors = darkColorScheme(
    primary             = UppColors.Orange,
    onPrimary           = UppColors.White,
    primaryContainer    = Color(0xFF6B2500),   // temno oranžna za filled variante
    onPrimaryContainer  = UppColors.OrangeLight,

    secondary           = UppColors.Blue,
    onSecondary         = UppColors.White,
    secondaryContainer  = Color(0xFF1A2A54),
    onSecondaryContainer = UppColors.Blue,

    tertiary            = UppColors.LightGray,
    onTertiary          = UppColors.Background,

    background          = UppColors.Background,
    onBackground        = UppColors.White,

    surface             = UppColors.CardSurface,
    onSurface           = UppColors.White,
    surfaceVariant      = UppColors.InputSurface,
    onSurfaceVariant    = UppColors.LightGray,

    outline             = UppColors.LightGray,
    outlineVariant      = UppColors.Divider,

    error               = UppColors.Error,
    onError             = UppColors.White,
)

// Svetla shema ostane za sistemske komponente (npr. dialog sistem)
private val UppLightColors = lightColorScheme(
    primary             = UppColors.Orange,
    onPrimary           = UppColors.White,
    secondary           = UppColors.Blue,
    onSecondary         = UppColors.White,
    background          = Color(0xFFF5F5F5),
    onBackground        = Color(0xFF181818),
    surface             = Color(0xFFFFFFFF),
    onSurface           = Color(0xFF181818),
    outline             = Color(0xFFCCCCCC),
)

// ── Oblike ───────────────────────────────────────────────────────────────────
val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small      = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium     = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large      = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

// ── Tipografija ───────────────────────────────────────────────────────────────
val AppTypography = Typography(
    displayLarge  = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, color = UppColors.White),
    titleLarge    = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = UppColors.White),
    titleMedium   = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold,      fontSize = 18.sp, color = UppColors.White),
    bodyLarge     = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal,    fontSize = 16.sp, color = UppColors.LightGray),
    bodyMedium    = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal,    fontSize = 14.sp, color = UppColors.LightGray),
    labelLarge    = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, color = UppColors.White),
    labelSmall    = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium,    fontSize = 11.sp, color = UppColors.MutedText),
)

// ── Tema composable ───────────────────────────────────────────────────────────
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,   // ← Figma specifikacija: temna tema je privzeta
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) UppDarkColors else UppLightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = UppColors.Background.toArgb()
            window.navigationBarColor = UppColors.Background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography  = AppTypography,
        shapes      = AppShapes,
        content     = content
    )
}