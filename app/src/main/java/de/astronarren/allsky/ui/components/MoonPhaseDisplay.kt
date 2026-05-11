package de.astronarren.allsky.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.astronarren.allsky.R
import de.astronarren.allsky.utils.MoonPhaseCalculator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

@Composable
fun MoonPhaseDisplay() {
    val moonPhase = remember { MoonPhaseCalculator.calculateMoonPhase() }
    val illumination = remember { MoonPhaseCalculator.getIllumination() }
    val daysUntilNewMoon = remember { MoonPhaseCalculator.getDaysUntilNewMoon() }
    val fraction = remember { MoonPhaseCalculator.getCurrentMoonCycleFraction() }
    
    // Compact horizontal layout — the previous 180 dp disc + tall stat column
    // was the heaviest card on the home screen. The user feedback was that it
    // pushed weather and Best Viewing too far down. Now: 96 dp disc on the
    // left, phase name + stats stacked on the right, all in roughly half the
    // vertical space.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.06f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, Color.White.copy(alpha = 0.08f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Moon with Image and Shadow Mask
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                // Base Full Moon Image
                Image(
                    painter = painterResource(id = R.drawable.moon_full),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                
                // Shadow Overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val f = fraction.toFloat()
                    val moonPath = Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) }
                    
                    clipPath(moonPath) {
                        if (f <= 0.5f) {
                            // Waxing: Shadow is on the left, moving right
                            val shadowWidth = size.width * (1f - 2f * f)
                            if (shadowWidth > 0) {
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    size = Size(shadowWidth, size.height)
                                )
                            }
                        } else {
                            // Waning: Shadow is on the right, moving left
                            val shadowWidth = size.width * (2f * (f - 0.5f))
                            if (shadowWidth > 0) {
                                drawRect(
                                    color = Color.Black.copy(alpha = 0.85f),
                                    topLeft = Offset(size.width - shadowWidth, 0f),
                                    size = Size(shadowWidth, size.height)
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.moon_phase).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    ),
                    color = Color.Yellow.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(moonPhase.stringResId).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text(
                            text = "ILLUM",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "%.0f%%".format(illumination),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = "NEW MOON",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "${daysUntilNewMoon.roundToInt()}D",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
