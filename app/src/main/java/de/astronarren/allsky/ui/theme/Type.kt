package de.astronarren.allsky.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.astronarren.allsky.R

/* ============================================================================
 * Caelum type ramp — Space Grotesk (headers/body) + Space Mono (instrument
 * readouts: timestamps, magnitudes, alt/az, temps, file sizes — tabular).
 * Space Grotesk ships as a single variable font; we pin the weight axis per
 * style via FontVariation (minSdk 29 supports variable fonts).
 * ========================================================================== */

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun grotesk(weight: FontWeight) = Font(
    R.font.space_grotesk,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Grotesk = FontFamily(
    grotesk(FontWeight.Normal),
    grotesk(FontWeight.Medium),
    grotesk(FontWeight.SemiBold),
    grotesk(FontWeight.Bold),
)

val Mono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

// Tabular figures for the mono instrument readouts so numeric columns align.
private const val TNUM = "\"tnum\" 1"

val CaelumTypography = Typography(
    // big temps "9°"
    displayLarge = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Bold,
        fontSize = 64.sp, lineHeight = 60.sp, letterSpacing = (-2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Bold,
        fontSize = 45.sp, lineHeight = 48.sp, letterSpacing = (-1).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 32.sp, letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 26.sp, letterSpacing = (-0.3).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp, lineHeight = 24.sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    // color = inkDim at call sites
    bodyMedium = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    // buttons
    labelLarge = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 18.sp,
    ),
    // EYEBROW — uppercase, color inkFaint at call sites
    labelMedium = TextStyle(
        fontFamily = Grotesk, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 14.sp, letterSpacing = 1.6.sp,
    ),
    // data readouts, tabular mono
    labelSmall = TextStyle(
        fontFamily = Mono, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, lineHeight = 16.sp, fontFeatureSettings = TNUM,
    ),
)

// A bare mono style for inline instrument readouts that aren't a named role.
val MonoReadout = TextStyle(
    fontFamily = Mono, fontWeight = FontWeight.Normal,
    fontSize = 13.sp, fontFeatureSettings = TNUM,
)

// Legacy alias — existing references compile while screens migrate.
val Typography = CaelumTypography
