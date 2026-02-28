package com.example.myapplication.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// --- Custom FontFamily: Inter (ali Montserrat, po želji) ---
// Najprej dodaj font datoteke v res/font/ (inter_regular.ttf, inter_bold.ttf, ...)
val Inter = FontFamily.Default

val DrawerBlue = Color(0xFF2563EB)


// --- Barvna shema (temna in svetla) ---
private val DarkColors = darkColorScheme(
    primary = Color(0xFF2563EB),      // Modra
    onPrimary = Color.White,
    secondary = Color(0xFFFEE440),    // Rumena
    onSecondary = Color.Black,
    background = Color(0xFF1F2937),   // Temno sivo ozadje
    onBackground = Color.White,
    surface = Color(0xFF374151),      // Temno siva kartica
    onSurface = Color.White,
    surfaceVariant = Color(0xFF4B5563),
    onSurfaceVariant = Color(0xFFD1D5DB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),      // Modra
    onPrimary = Color.White,
    secondary = Color(0xFFFEE440),    // Rumena
    onSecondary = Color.Black,
    background = Color.White,         // Belo ozadje
    onBackground = Color.Black,
    surface = Color(0xFFF7F7FA),      // Svetla kartica
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE5E7EB),
    onSurfaceVariant = Color(0xFF6B7280),
)

// --- Tipografija ---
val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
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

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}