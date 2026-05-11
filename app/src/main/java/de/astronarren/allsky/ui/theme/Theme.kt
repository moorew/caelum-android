package de.astronarren.allsky.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Allsky dark scheme — a calibrated navy stack with a single warm accent.
 * `dynamicColor` is opt-in (off by default) so the carefully-tuned cosmic look
 * isn't replaced by whatever wallpaper the user happens to be running.
 */
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color(0xFF002C71),
    primaryContainer = Color(0xFF1F3A78),
    onPrimaryContainer = Color(0xFFDCE4FF),

    secondary = AccentYellow,
    onSecondary = Color(0xFF3D2D00),
    secondaryContainer = Color(0xFF5C4500),
    onSecondaryContainer = Color(0xFFFFE2A1),

    tertiary = AccentGreen,
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF005236),
    onTertiaryContainer = Color(0xFFA9F4C7),

    background = DeepNavy,
    onBackground = Color(0xFFE6EAF3),

    surface = SurfaceDark,
    onSurface = Color(0xFFE6EAF3),

    surfaceVariant = SurfaceContainerHigh,
    onSurfaceVariant = Color(0xFFC2C8DE),

    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,

    outline = Color(0x55FFFFFF),
    outlineVariant = OutlineDim,

    error = AccentRed,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    inverseSurface = Color(0xFFE6EAF3),
    inverseOnSurface = SurfaceDark,
    inversePrimary = Color(0xFF1F3A78),

    scrim = Color(0x99000000)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = AccentYellow,
    tertiary = SkyBlue,
    surface = SurfaceLight,
    background = SurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = OnSurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = CardBackgroundLight,
    onSurfaceVariant = Color(0xFF55607A)
)

@Composable
fun AllskyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default — the app has a curated dark identity. Set to true to
    // honour Material You wallpaper colours on Android 12+.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> DarkColorScheme // Force the cosmic look in light system theme too.
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, view)
            // App is dark-first; status & nav icons should be light.
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AllskyShapes,
        content = content
    )
}
