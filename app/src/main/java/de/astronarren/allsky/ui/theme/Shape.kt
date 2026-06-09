package de.astronarren.allsky.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Caelum shape scale. Depth comes from field tiers + hairline borders — never
 * drop shadows — so radii are the main expressive lever.
 */
val CaelumShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),   // chips
    medium = RoundedCornerShape(16.dp),  // nav rows, inner tiles
    large = RoundedCornerShape(22.dp),   // cards
    extraLarge = RoundedCornerShape(28.dp),
)

// Legacy alias.
val AllskyShapes = CaelumShapes
