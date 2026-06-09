package de.astronarren.allsky.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/* ============================================================================
 * Caelum theme — carries the full "Deep Observatory" / "Red-Light" palettes
 * via a CompositionLocal, while also mapping the closest roles into a Material
 * dark ColorScheme so ripples, dialogs and the date picker inherit the look.
 *
 * Usage in composables:
 *     val c = LocalCaelum.current
 *     ...color = c.gold
 * ========================================================================== */

@Immutable
data class CaelumColors(
    val field: Color, val field2: Color, val field3: Color, val fieldHi: Color,
    val ink: Color, val inkDim: Color, val inkFaint: Color,
    val signal: Color, val signalBright: Color, val gold: Color,
    val good: Color, val fair: Color, val poor: Color,
    val line: Color, val lineStrong: Color,
)

val DeepObservatory = CaelumColors(
    field = Field, field2 = Field2, field3 = Field3, fieldHi = FieldHi,
    ink = Ink, inkDim = InkDim, inkFaint = InkFaint,
    signal = Signal, signalBright = SignalBright, gold = Gold,
    good = Good, fair = Fair, poor = Poor,
    line = LineDim, lineStrong = LineStrong,
)

val RedLight = CaelumColors(
    field = RField, field2 = RField2, field3 = RField3, fieldHi = RFieldHi,
    ink = RInk, inkDim = RInkDim, inkFaint = RInkFaint,
    signal = RSignal, signalBright = RSignal, gold = RGold,
    good = RGood, fair = RGold, poor = RPoor,
    line = Color(0x1AFF6E6E), lineStrong = Color(0x33FF6E6E),
)

val LocalCaelum = staticCompositionLocalOf { DeepObservatory }

/** True when Red-Light night-vision mode is active. Used to tint imagery. */
val LocalRedLight = staticCompositionLocalOf { false }

enum class CaelumThemeMode { DeepObservatory, RedLight }

@Composable
fun CaelumTheme(
    mode: CaelumThemeMode = CaelumThemeMode.DeepObservatory,
    content: @Composable () -> Unit,
) {
    val c = if (mode == CaelumThemeMode.RedLight) RedLight else DeepObservatory
    val scheme = darkColorScheme(
        primary = c.signal,
        onPrimary = if (mode == CaelumThemeMode.RedLight) Color(0xFF1A0203) else Color(0xFF06122E),
        primaryContainer = c.fieldHi,
        onPrimaryContainer = c.signalBright,
        secondary = c.gold,
        onSecondary = c.field,
        tertiary = c.good,
        onTertiary = c.field,
        background = c.field,
        onBackground = c.ink,
        surface = c.field2,
        onSurface = c.ink,
        surfaceVariant = c.field3,
        onSurfaceVariant = c.inkDim,
        surfaceContainerLowest = c.field,
        surfaceContainerLow = c.field2,
        surfaceContainer = c.field3,
        surfaceContainerHigh = c.fieldHi,
        surfaceContainerHighest = c.fieldHi,
        outline = c.lineStrong,
        outlineVariant = c.line,
        error = c.poor,
        onError = c.field,
        scrim = Color(0xB8060912),
    )
    CompositionLocalProvider(
        LocalCaelum provides c,
        LocalRedLight provides (mode == CaelumThemeMode.RedLight),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = CaelumTypography,
            shapes = CaelumShapes,
            content = content,
        )
    }
}

/**
 * Red-Light image tint: maps luminance into the red channel only so live and
 * media imagery preserves dark adaptation. Returns null in Deep Observatory.
 */
@Composable
fun caelumImageColorFilter(): androidx.compose.ui.graphics.ColorFilter? {
    if (!LocalRedLight.current) return null
    val m = androidx.compose.ui.graphics.ColorMatrix(
        floatArrayOf(
            0.5f, 0.5f, 0.2f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
    return androidx.compose.ui.graphics.ColorFilter.colorMatrix(m)
}
