package de.astronarren.allsky.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Allsky shape scale — softer than M3 defaults to match the "glass / nebula"
 * surface treatment. Components should pull from these tokens rather than
 * hard-coding `RoundedCornerShape(N.dp)` inline.
 */
val AllskyShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)
