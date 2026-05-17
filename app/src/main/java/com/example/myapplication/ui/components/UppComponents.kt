package com.example.myapplication.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.ui.theme.UppColors

// ═══════════════════════════════════════════════════════════════════════════
// UPP Design System — Shared Composables (Figma-aligned)
//
// Pravila:
//   • PrimaryButton     → oranžen (#FF6411), za vse CTA akcije
//   • SecondaryButton   → obroba #E0E2DB, prosojno ozadje — sekundarni
//   • GoogleButton      → belo ozadje, siva obroba — Material spec
//   • UppTextField      → temno ozadje #1E1E1E, obroba #E0E2DB
//   • UppCard           → površina #222222, obroba #E0E2DB
//   • GradientHeader    → OrangeToBlackGradient SAMO za prominentne naslove
// ═══════════════════════════════════════════════════════════════════════════

// ── Primarni gumb ────────────────────────────────────────────────────────────
/**
 * Standardni akcijski gumb. Oranžen (#FF6411), zaobljen 8dp.
 * Uporaba: "Continue", "Start Workout", "Add Food", "Save"
 */
@Composable
fun UppPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(50.dp),
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor         = UppColors.Orange,
            contentColor           = UppColors.White,
            disabledContainerColor = UppColors.Orange.copy(alpha = 0.4f),
            disabledContentColor   = UppColors.White.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = UppColors.White
        )
    }
}

// ── Sekundarni gumb (obroba) ─────────────────────────────────────────────────
/**
 * Izriše obrisan gumb — obroba #E0E2DB, prosojno ozadje.
 * Uporaba: "Cancel", "Back", sekundarni flow.
 */
@Composable
fun UppSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(50.dp),
    borderColor: Color = UppColors.LightGray,
    textColor: Color = UppColors.LightGray,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        border = BorderStroke(1.5.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = textColor,
            containerColor = Color.Transparent
        )
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

// ── Google gumb ───────────────────────────────────────────────────────────────
/**
 * "Continue with Google" gumb — belo ozadje, siva obroba (Material Brand spec).
 * Ne menjaj barv tega gumba!
 */
@Composable
fun UppGoogleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(50.dp),
    shape: Shape = RoundedCornerShape(8.dp)
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = UppColors.GoogleButtonBg,
            contentColor   = UppColors.GoogleButtonText
        ),
        border = BorderStroke(1.dp, UppColors.GoogleButtonBorder)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.google_icon),
            contentDescription = "Google",
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Continue with Google",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = UppColors.GoogleButtonText
        )
    }
}

// ── Vhodno polje ─────────────────────────────────────────────────────────────
/**
 * Standardno vhodno polje: temno ozadje #1E1E1E, obroba #E0E2DB.
 * Ujema se z onboarding zasloni v Figmi.
 */
@Composable
fun UppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                text = placeholder,
                color = UppColors.MutedText,
                fontSize = 14.sp
            )
        },
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
        shape = shape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor   = UppColors.InputSurface,
            unfocusedContainerColor = UppColors.InputSurface,
            focusedBorderColor      = UppColors.Orange,
            unfocusedBorderColor    = UppColors.LightGray,
            focusedTextColor        = UppColors.White,
            unfocusedTextColor      = UppColors.White,
            cursorColor             = UppColors.Orange,
            errorBorderColor        = UppColors.Error,
            errorContainerColor     = UppColors.InputSurface,
            errorTextColor          = UppColors.White
        )
    )
}

// ── Kartica ───────────────────────────────────────────────────────────────────
/**
 * Standardna kartica: ozadje #222222, obroba #E0E2DB (ali custom).
 * Uporaba: streak modul, daily plan, workout session kartice.
 */
@Composable
fun UppCard(
    modifier: Modifier = Modifier,
    borderColor: Color = UppColors.LightGray.copy(alpha = 0.3f),
    backgroundColor: Color = UppColors.CardSurface,
    borderWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(content = content)
    }
}

// ── Gradient naslov (samo za prominentne lokacije) ────────────────────────────
/**
 * Gradient besedilo SAMO za naslovne module:
 * "UPP" splash screen, "Dnevni Pregled", header modulov.
 * Ne uporablja za navadne naslove kartic!
 */
@Composable
fun GradientHeaderText(
    text: String,
    fontSize: Int = 36,
    modifier: Modifier = Modifier
) {
    // Gradient text effect prek Box+Surface
    Box(
        modifier = modifier
            .background(UppColors.OrangeGradient, RoundedCornerShape(4.dp))
            .padding(horizontal = 2.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.Transparent // transparenten, gradient ga pokrije
        )
        Text(
            text = text,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.ExtraBold,
            color = UppColors.White
        )
    }
}

// ── "Or" razdelilna vrstica ───────────────────────────────────────────────────
@Composable
fun UppDividerOr(
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = UppColors.Divider
        )
        Text(
            text = "  or  ",
            color = UppColors.MutedText,
            fontSize = 14.sp
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = UppColors.Divider
        )
    }
}

// ── Stat chip (za workout/run stat vrednosti) ─────────────────────────────────
@Composable
fun UppStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = UppColors.Orange
) {
    Column(
        modifier = modifier
            .background(UppColors.CardSurface, RoundedCornerShape(10.dp))
            .border(1.dp, UppColors.Divider, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = UppColors.MutedText
        )
    }
}

