package de.astronarren.allsky.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.astronarren.allsky.ui.theme.LocalCaelum

/* ============================================================================
 * Caelum component kit. Every screen draws from these — they read the palette
 * from LocalCaelum so they re-theme instantly in Red-Light mode.
 * ========================================================================== */

/** Card — field2 fill, 1dp hairline border, 22dp radius, 18dp pad. No shadows. */
@Composable
fun CaelumCard(
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val c = LocalCaelum.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (tonal) c.field3 else c.field2)
            .border(1.dp, c.line, MaterialTheme.shapes.large)
            .padding(contentPadding),
        content = content,
    )
}

/** Eyebrow — uppercase labelMedium, inkFaint, optional leading tick or icon. */
@Composable
fun Eyebrow(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color? = null,
) {
    val c = LocalCaelum.current
    val tint = accent ?: c.inkFaint
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        } else {
            Box(
                Modifier
                    .size(width = 14.dp, height = 2.dp)
                    .background(tint, RoundedCornerShape(1.dp))
            )
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

/** Viewing rating used by ViewingBadge. */
enum class ViewingRating { Excellent, Fair, Poor }

/** Pill: bg = role color @ ~15% alpha, text = role color, bold uppercase. */
@Composable
fun ViewingBadge(rating: ViewingRating, modifier: Modifier = Modifier) {
    val c = LocalCaelum.current
    val role = when (rating) {
        ViewingRating.Excellent -> c.good
        ViewingRating.Fair -> c.fair
        ViewingRating.Poor -> c.poor
    }
    BadgePill(text = rating.name.uppercase(), role = role, modifier = modifier)
}

/** Signal-coloured badge, e.g. "ZHR 50". */
@Composable
fun SignalBadge(text: String, modifier: Modifier = Modifier) {
    BadgePill(text = text, role = LocalCaelum.current.signal, modifier = modifier)
}

/** Gold-coloured badge. */
@Composable
fun GoldBadge(text: String, modifier: Modifier = Modifier) {
    BadgePill(text = text, role = LocalCaelum.current.gold, modifier = modifier)
}

@Composable
private fun BadgePill(text: String, role: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = role,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(role.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** Caption + value + optional unit. Used in 3-up MetricCell rows. */
@Composable
fun MetricCell(
    label: String,
    value: String,
    unit: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = LocalCaelum.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = c.inkFaint,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = c.ink,
            )
            if (unit != null) {
                Text(
                    text = " $unit",
                    style = MaterialTheme.typography.titleSmall,
                    color = c.inkDim,
                )
            }
        }
    }
}

/** A row of 3 MetricCells separated by 1dp line dividers. */
@Composable
fun MetricRow(
    cells: List<Triple<String, String, String?>>,
    modifier: Modifier = Modifier,
) {
    val c = LocalCaelum.current
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        cells.forEachIndexed { i, (label, value, unit) ->
            MetricCell(label, value, unit, modifier = Modifier.weight(1f))
            if (i < cells.lastIndex) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(34.dp)
                        .background(c.line)
                )
            }
        }
    }
}

/** field2 row with icon + label, optional trailing; active = signal-tinted. */
@Composable
fun NavRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = LocalCaelum.current
    val bg = if (active) c.signal.copy(alpha = 0.16f) else c.field2
    val border = if (active) c.signal.copy(alpha = 0.35f) else c.line
    val fg = if (active) c.signalBright else c.ink
    val iconTint = if (active) c.signalBright else c.inkDim
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .border(1.dp, border, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = fg,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

enum class CaelumButtonStyle { Primary, Ghost, Blue }

@Composable
fun CaelumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: CaelumButtonStyle = CaelumButtonStyle.Primary,
    leadingIcon: ImageVector? = null,
) {
    val c = LocalCaelum.current
    val (bg, fg, border) = when (style) {
        CaelumButtonStyle.Primary -> Triple(c.ink, c.field, null)
        CaelumButtonStyle.Blue -> Triple(c.signal, Color(0xFF06122E), null)
        CaelumButtonStyle.Ghost -> Triple(Color.Transparent, c.ink, c.lineStrong)
    }
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, CircleShape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}

/** 42dp circular icon button — field2 fill, 1dp hairline border. */
@Composable
fun CaelumIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null,
) {
    val c = LocalCaelum.current
    Box(
        modifier = modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(c.field2)
            .border(1.dp, c.line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint ?: c.ink, modifier = Modifier.size(20.dp))
    }
}

/** Translucent LIVE pill with a glowing good-coloured LED. */
@Composable
fun LivePill(modifier: Modifier = Modifier) {
    val c = LocalCaelum.current
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(c.good)
        )
        Text(
            text = "LIVE",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Mono instrument chip, e.g. a timestamp over the live image. */
@Composable
fun MonoChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        textAlign = TextAlign.End,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
