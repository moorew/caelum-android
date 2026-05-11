package de.astronarren.allsky.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.astronarren.allsky.ui.theme.ClearNight
import de.astronarren.allsky.ui.theme.DeepNavy
import de.astronarren.allsky.ui.theme.NightPurple
import de.astronarren.allsky.ui.theme.OutlineDim

/**
 * Consistent dark nebula gradient used by every screen. Pass [colors] to
 * override (e.g. weather-aware backdrop on the home screen).
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(DeepNavy, NightPurple, ClearNight),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(colors = colors))
    ) {
        content()
    }
}

/**
 * Translucent "glass" card — the signature surface for grouped content on top
 * of the dark gradient. Use this instead of hand-tuning Card alpha and border
 * stroke at every call site.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    elevated: Boolean = false,
    content: @Composable () -> Unit
) {
    val containerAlpha = if (elevated) 0.10f else 0.06f
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = containerAlpha)
        ),
        border = BorderStroke(1.dp, OutlineDim),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        content()
    }
}

/**
 * Small section heading: caps-lock label with generous tracking and a thin
 * primary-tinted underline, used above carousels and form groups.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontSize = 13.sp
            ),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) trailing()
    }
}
