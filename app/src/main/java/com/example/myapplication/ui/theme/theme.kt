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
    onSecondary = Color(0xFF17223B),  // Temno ozadje
    background = Color(0xFF17223B),   // Temno modro ozadje
    onBackground = Color.White,
    surface = Color(0xFF1C274C),      // Temno modra kartica
    onSurface = Color.White,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),      // Modra
    onPrimary = Color.White,
    secondary = Color(0xFFFEE440),    // Rumena
    onSecondary = Color(0xFF17223B),  // Temno ozadje
    background = Color(0xFFF7F7FA),   // Svetlo ozadje
    onBackground = Color(0xFF22223B),
    surface = Color(0xFFEDEDED),      // Svetla kartica
    onSurface = Color(0xFF22223B),
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
    darkTheme: Boolean = true, // nastavljen na temno kot privzeto
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}