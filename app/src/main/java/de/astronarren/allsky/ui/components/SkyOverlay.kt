package de.astronarren.allsky.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import de.astronarren.allsky.data.astro.FisheyeCalibration
import de.astronarren.allsky.data.astro.FisheyeProjection
import de.astronarren.allsky.data.astro.HorizontalCoords
import de.astronarren.allsky.data.astro.Planet
import de.astronarren.allsky.viewmodel.PlanetRow

/**
 * Overlay that paints the moon and any above-horizon naked-eye planets onto
 * the live allsky frame using the persisted [FisheyeCalibration].
 *
 * Positioned as a child of the same Box that hosts the [coil.compose.AsyncImage]
 * with [androidx.compose.ui.layout.ContentScale.Crop]; we mirror that crop
 * transform here so a body's pixel position on the source JPEG ends up on the
 * same display pixel as the underlying image content. The card-level
 * `RoundedCornerShape` clip naturally crops anything we'd draw outside the
 * card — no extra clipping needed here.
 *
 * Sat-pass arcs and meteor-shower radiants are not drawn yet; they use the
 * same projection and will be a small follow-up.
 *
 * Renders nothing (returns an empty Canvas) when [enabled] is false or the
 * image hasn't loaded yet — callers don't need to guard the call site.
 */
@Composable
fun SkyOverlay(
    enabled: Boolean,
    calibration: FisheyeCalibration,
    imageWidthPx: Int,
    imageHeightPx: Int,
    moon: HorizontalCoords?,
    planets: List<PlanetRow>,
    modifier: Modifier = Modifier,
) {
    if (!enabled || imageWidthPx <= 0 || imageHeightPx <= 0) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val (imgToDispScale, offsetX, offsetY) = FisheyeProjection.transformImagePixelToDisplay(
            imageWidthPx = imageWidthPx,
            imageHeightPx = imageHeightPx,
            displayWidthPx = size.width,
            displayHeightPx = size.height,
            fit = false, // matches ContentScale.Crop on the AsyncImage
        )

        fun project(coords: HorizontalCoords): Offset? {
            val imgPx = FisheyeProjection.altAzToImagePixel(
                coords = coords,
                calibration = calibration,
                imageWidthPx = imageWidthPx,
                imageHeightPx = imageHeightPx,
            ) ?: return null
            return Offset(
                x = imgPx.x * imgToDispScale + offsetX,
                y = imgPx.y * imgToDispScale + offsetY,
            )
        }

        // Moon: large pale-yellow disc with a soft halo so it reads above
        // the underlying frame even in a milky-twilight image.
        if (moon != null) {
            project(moon)?.let { p ->
                drawCircle(Color.Black.copy(alpha = 0.45f), radius = 11.dp.toPx(), center = p)
                drawCircle(Color(0xFFFFF59D).copy(alpha = 0.30f), radius = 10.dp.toPx(), center = p)
                drawCircle(Color(0xFFFFF59D), radius = 5.dp.toPx(), center = p)
                drawLabel("Moon", p, Color(0xFFFFF59D))
            }
        }

        // Planets: colour-keyed dot + name. Bright outline so the dot still
        // reads on a near-white horizon glow.
        planets.forEach { row ->
            val h = row.snapshot.horizontal
            if (h.altitudeDeg <= 0) return@forEach
            val p = project(h) ?: return@forEach
            val colour = planetColour(row.snapshot.planet)
            drawCircle(Color.Black.copy(alpha = 0.55f), radius = 5.dp.toPx(), center = p)
            drawCircle(colour, radius = 3.dp.toPx(), center = p)
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = 3.dp.toPx(),
                center = p,
                style = Stroke(width = 0.6.dp.toPx()),
            )
            drawLabel(row.snapshot.planet.displayName, p, colour)
        }
    }
}

/**
 * Tiny outlined label drawn ~10dp below the marker. We go through the native
 * canvas because Compose doesn't expose a `drawText` on `DrawScope` directly,
 * and we want the same Paint-based outline trick the Tonight card uses for
 * its sky-map labels.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(
    text: String,
    anchor: Offset,
    fill: Color,
) {
    val nativePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textSize = 11.dp.toPx()
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val outline = android.graphics.Paint(nativePaint).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 3.dp.toPx()
        color = android.graphics.Color.argb(220, 0, 0, 0)
    }
    val labelY = anchor.y + 22.dp.toPx()
    drawContext.canvas.nativeCanvas.drawText(text, anchor.x, labelY, outline)
    nativePaint.color = fill.toArgb()
    drawContext.canvas.nativeCanvas.drawText(text, anchor.x, labelY, nativePaint)
}

private fun planetColour(planet: Planet): Color = when (planet) {
    Planet.MERCURY -> Color(0xFFFFCC80)
    Planet.VENUS -> Color(0xFFFFE082)
    Planet.MARS -> Color(0xFFFF8A65)
    Planet.JUPITER -> Color(0xFFFFD54F)
    Planet.SATURN -> Color(0xFFFFAB91)
    Planet.EARTH -> Color.White // never plotted; exhaustiveness
}
