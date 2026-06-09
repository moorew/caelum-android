package de.astronarren.allsky.ui.theme

import androidx.compose.ui.graphics.Color

/* ============================================================================
 * Caelum — "Deep Observatory" palette
 * ----------------------------------------------------------------------------
 * One dark field everywhere. Two themes ship: Deep Observatory (default) and
 * Red-Light (night-vision). Every screen pulls from these tokens — no stray
 * hex, no Material default purple. The roles that Material's ColorScheme can't
 * hold are carried by CaelumColors via the LocalCaelum CompositionLocal
 * (see CaelumTheme.kt).
 * ========================================================================== */

// ---------- Deep Observatory ----------
val Field        = Color(0xFF0B1020) // base — the night
val Field2       = Color(0xFF121933) // raised surface
val Field3       = Color(0xFF1A2342) // card
val FieldHi      = Color(0xFF232E54) // hover / active
val Ink          = Color(0xFFE8ECF4) // primary text
val InkDim       = Color(0xFF93A4C6) // secondary
val InkFaint     = Color(0xFF5C6B8E) // tertiary / captions
val Signal       = Color(0xFF5B8DEF) // interactive / links / station
val SignalBright = Color(0xFF83AAFF)
val Gold         = Color(0xFFE9C46A) // celestial highlights
val Good         = Color(0xFF5FD6A0) // clear / live / excellent
val Fair         = Color(0xFFE9C46A)
val Poor         = Color(0xFFE8806B) // overcast / poor
val LineDim      = Color(0x17E8ECF4) // ~9% white — hairline borders
val LineStrong   = Color(0x29E8ECF4) // ~16% white

// ---------- Red-Light (monochrome deep red — preserves dark adaptation) ----------
val RField     = Color(0xFF0A0203)
val RField2    = Color(0xFF170506)
val RField3    = Color(0xFF220809)
val RFieldHi   = Color(0xFF30100F)
val RInk       = Color(0xFFFF6E6E)
val RInkDim    = Color(0xFFC24A4A)
val RInkFaint  = Color(0xFF7C2A2A)
val RSignal    = Color(0xFFFF5252)
val RGold      = Color(0xFFFF7A52)
val RGood      = Color(0xFFFF5252)
val RPoor      = Color(0xFFC24A4A)

/* ---------- Legacy aliases ----------
 * Kept so existing screens compile while they migrate onto the Caelum tokens.
 * Re-pointed at the closest Deep Observatory value so even un-migrated code
 * reads in-brand.
 */
val DeepNavy = Field
val NightPurple = Field2
val ClearNight = Field3
val SkyBlue = SignalBright
val SunsetOrange = Gold
val PrimaryBlue = Signal
val AccentYellow = Gold
val AccentGreen = Good
val AccentRed = Poor
val SurfaceDark = Field
val SurfaceContainerLowest = Field
val SurfaceContainerLow = Field2
val SurfaceContainer = Field3
val SurfaceContainerHigh = FieldHi
val SurfaceContainerHighest = FieldHi
val OutlineDim = LineStrong
val OutlineSoft = LineDim
val ErrorRed = Poor
val CardBackgroundDark = Field3
