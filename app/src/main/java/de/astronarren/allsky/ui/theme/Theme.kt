package de.astronarren.allsky.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Caelum is night-only. Two themes ship — Deep Observatory (default) and
 * Red-Light — both dark. There is no light theme and no Material You dynamic
 * color: the brand palette is fixed (see CaelumTheme.kt).
 *
 * This thin wrapper keeps the historical `AllskyTheme` entry point and owns the
 * system-bar appearance; the actual palette/typography/shapes live in
 * [CaelumTheme].
 */
@Composable
fun AllskyTheme(
    mode: CaelumThemeMode = CaelumThemeMode.DeepObservatory,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, view)
            // Always dark surfaces — light icons on the system bars.
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }
    CaelumTheme(mode = mode, content = content)
}
