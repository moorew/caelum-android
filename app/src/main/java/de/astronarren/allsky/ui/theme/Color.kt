package de.astronarren.allsky.ui.theme

import androidx.compose.ui.graphics.Color

/* ---------- Brand palette ----------
 * The Allsky aesthetic is a cosmic dark theme: navy → indigo gradient with a
 * single warm accent reserved for "best viewing" and moon highlights. The
 * palette below is intentionally narrow — every screen should pull from these
 * tokens rather than inventing new colours inline.
 */

// Backdrop gradient stops used by AppBackground.
val DeepNavy = Color(0xFF0A0F1F)
val NightPurple = Color(0xFF141A33)
val ClearNight = Color(0xFF1A2247)

// Legacy aliases kept so existing references compile while we migrate inline
// gradient definitions; values pulled in line with the new backdrop.
val SkyBlue = Color(0xFF7AA2FF)
val SunsetOrange = Color(0xFFFFB35A)

// Accents
val PrimaryBlue = Color(0xFF6FA8FF)  // calmer, less iOS-blue than before
val AccentYellow = Color(0xFFFFD166) // warm "best viewing" highlight
val AccentGreen = Color(0xFF7CE0A4)
val AccentRed = Color(0xFFFF6B6B)

/* ---------- Material 3 dark surface tiers ----------
 * These match the official M3 "container" elevation scale, calibrated against
 * the navy backdrop so cards read crisply at every elevation.
 */
val SurfaceDark = Color(0xFF0F1426)
val SurfaceContainerLowest = Color(0xFF0B1020)
val SurfaceContainerLow = Color(0xFF12182C)
val SurfaceContainer = Color(0xFF161D36)
val SurfaceContainerHigh = Color(0xFF1B2340)
val SurfaceContainerHighest = Color(0xFF222A4A)
val OutlineDim = Color(0x1FFFFFFF)        // 12% white — borders on glass cards
val OutlineSoft = Color(0x14FFFFFF)        // 8% white — quieter dividers

/* ---------- Light scheme (kept minimal — the app is primarily dark) ---------- */
val SurfaceLight = Color(0xFFF6F7FB)
val CardBackgroundLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF11142A)

/* ---------- Legacy Material 3 sample tokens kept for compatibility ---------- */
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)
val Purple40 = Color(0xFF6650A4)
val PurpleGrey40 = Color(0xFF625B71)
val Pink40 = Color(0xFF7D5260)
val ErrorRed = AccentRed
val CardBackgroundDark = SurfaceContainer
