package de.astronarren.allsky.ui.modules

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.astronarren.allsky.data.astro.AuroraBand
import de.astronarren.allsky.data.astro.AuroraOutlook
import de.astronarren.allsky.data.astro.MoonEvents
import de.astronarren.allsky.data.astro.SatellitePass
import de.astronarren.allsky.ui.components.GlassCard
import de.astronarren.allsky.viewmodel.PlanetRow
import de.astronarren.allsky.viewmodel.TonightUiState
import de.astronarren.allsky.viewmodel.TonightViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "What's worth looking at tonight" — a multi-row card that aggregates every
 * location-aware naked-eye astronomy data source we can compute or fetch
 * cheaply:
 *
 *   - **METEORS** — strongest active annual shower (offline IMO 2024 table)
 *   - **MOON**    — rise / transit / set today + illumination (offline Meeus)
 *   - **PLANETS** — naked-eye planets currently above the horizon (offline)
 *   - **AURORA**  — NOAA SWPC 24h Kp forecast, gated by geomagnetic latitude
 *   - **PASSES**  — bright satellite passes from CelesTrak TLEs + SGP4
 *
 * Each row is independently collapsible. Rows that have nothing useful to
 * surface (no active shower, no aurora at this latitude, no bright passes
 * tonight) hide themselves rather than waste vertical space — so the card
 * shape adapts to what's actually going on in the sky above the user.
 *
 * Location: we read the lat/lon the user set during onboarding (or in
 * Settings → Location). We deliberately don't ask for runtime GPS — those
 * coordinates were captured at first launch and a typical home observer's
 * position doesn't change. If the coordinates are blank, the location-
 * dependent rows hide and the shower row stays visible by itself.
 */

/**
 * Public entry point. The legacy zero-arg call site in MainScreen still
 * compiles because [viewModel] has a default factory.
 */
@Composable
fun TonightModule(
    viewModel: TonightViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("HH:mm")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            elevated = true
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                CardHeader()
                Spacer(modifier = Modifier.height(14.dp))

                val rowsRendered = renderRows(
                    state = state,
                    zone = zone,
                    timeFormatter = timeFormatter,
                )

                if (!state.isLoading && rowsRendered == 0) {
                    Text(
                        "Quiet night. No major shower active and nothing else worth flagging.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                if (!state.hasLocation && !state.isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Set your location in Settings to unlock moon, planets, aurora and satellite passes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CardHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "TONIGHT",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
        )
    }
}

/**
 * Render every applicable row in fixed order. Returns the count of rows
 * actually drawn so the parent can show a "quiet night" message when zero
 * data sources had anything to surface.
 */
@Composable
private fun ColumnScope.renderRows(
    state: TonightUiState,
    zone: ZoneId,
    timeFormatter: DateTimeFormatter,
): Int {
    var drawn = 0

    state.activeShower?.let {
        if (drawn > 0) RowDivider()
        MeteorRow(it)
        drawn++
    }

    state.moonEvents?.let { events ->
        if (drawn > 0) RowDivider()
        MoonRow(events, state.moonIlluminationPercent, zone, timeFormatter)
        drawn++
    }

    val planets = state.visiblePlanets
    if (planets.isNotEmpty()) {
        if (drawn > 0) RowDivider()
        PlanetsRow(planets, zone, timeFormatter)
        drawn++
    }

    state.aurora?.let { outlook ->
        if (drawn > 0) RowDivider()
        AuroraRow(outlook, zone, timeFormatter)
        drawn++
    }

    if (state.satellitePasses.isNotEmpty()) {
        if (drawn > 0) RowDivider()
        SatelliteRow(state.satellitePasses, zone, timeFormatter)
        drawn++
    }

    return drawn
}

@Composable
private fun RowDivider() {
    Spacer(modifier = Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.08f))
    )
    Spacer(modifier = Modifier.height(12.dp))
}

// ---------- Meteor row ----------

@Composable
private fun MeteorRow(active: ActiveShower) {
    var expanded by remember { mutableStateOf(false) }
    val days = active.daysFromPeak
    val timing = when {
        days == 0 -> "peaks tonight"
        days > 0 -> "peaked ${days}d ago"
        else -> "peaks in ${abs(days)}d"
    }

    Column(modifier = Modifier.clickable { expanded = !expanded }) {
        ExpandableRowHeader(
            icon = Icons.Default.AutoAwesome,
            label = "METEORS",
            primaryContent = {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        active.shower.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        timing,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
                ZhrPill(active.shower.zhr)
            },
            expanded = expanded,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            ShowerDetails(active.shower)
        }
    }
}

@Composable
private fun ZhrPill(zhr: Int) {
    val pillAlpha = (zhr / 200f).coerceIn(0.18f, 0.5f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF66BB6A).copy(alpha = pillAlpha))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            "ZHR $zhr",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun ShowerDetails(shower: MeteorShower) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(
            text = shower.description,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(shower.wikipediaUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(intent) }
            },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                "Learn more on Wikipedia",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// ---------- Moon row ----------

@Composable
private fun MoonRow(
    events: MoonEvents,
    illuminationPercent: Int,
    zone: ZoneId,
    timeFormatter: DateTimeFormatter,
) {
    var expanded by remember { mutableStateOf(false) }
    val riseStr = events.rise?.let { formatLocal(it, zone, timeFormatter) } ?: "—"
    val setStr = events.set?.let { formatLocal(it, zone, timeFormatter) } ?: "—"
    val phaseLabel = moonPhaseLabel(illuminationPercent)

    Column(modifier = Modifier.clickable { expanded = !expanded }) {
        ExpandableRowHeader(
            icon = Icons.Default.NightsStay,
            label = "MOON",
            primaryContent = {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "$phaseLabel · ${illuminationPercent}% lit",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        "Rises $riseStr · sets $setStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            },
            expanded = expanded,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                events.transit?.let { transit ->
                    Text(
                        "Transit (highest in sky) at ${formatLocal(transit, zone, timeFormatter)}, ${events.transitAltitudeDeg.roundToInt()}° altitude.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Bright moonlight washes out fainter stars and meteors. Plan deep-sky targets for hours either side of moonrise/set.",
                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * Map illuminated-fraction percentage to a friendly phase name. We don't
 * distinguish waxing/waning here because our moon module doesn't track the
 * derivative — adding it would mean a second position computation 24h ahead
 * just to label the row, which isn't worth the code.
 */
private fun moonPhaseLabel(percent: Int): String = when {
    percent < 5 -> "New Moon"
    percent < 25 -> "Crescent"
    percent < 55 -> "Quarter"
    percent < 75 -> "Gibbous"
    percent < 95 -> "Almost Full"
    else -> "Full Moon"
}

// ---------- Planets row ----------

@Composable
private fun PlanetsRow(
    planets: List<PlanetRow>,
    zone: ZoneId,
    timeFormatter: DateTimeFormatter,
) {
    var expanded by remember { mutableStateOf(false) }
    val summary = planets.take(3).joinToString(", ") { it.snapshot.planet.displayName }

    Column(modifier = Modifier.clickable { expanded = !expanded }) {
        ExpandableRowHeader(
            icon = Icons.Default.Public,
            label = "PLANETS",
            primaryContent = {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (planets.size == 1) "Visible: $summary"
                        else "${planets.size} visible: $summary",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        "Tap for altitude, magnitude, and rise/set",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            },
            expanded = expanded,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                planets.forEach { row ->
                    PlanetDetail(row, zone, timeFormatter)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PlanetDetail(
    row: PlanetRow,
    zone: ZoneId,
    timeFormatter: DateTimeFormatter,
) {
    val altDeg = row.snapshot.horizontal.altitudeDeg.roundToInt()
    val cardinal = row.snapshot.horizontal.cardinal
    val mag = "%+.1f".format(row.snapshot.apparentMagnitude)
    val setStr = row.events.set?.let { formatLocal(it, zone, timeFormatter) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            row.snapshot.planet.displayName,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${altDeg}° $cardinal · mag $mag" + (setStr?.let { " · sets $it" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
        )
    }
    Text(
        "In ${row.snapshot.constellation}",
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.5f),
    )
}

// ---------- Aurora row ----------

@Composable
private fun AuroraRow(
    outlook: AuroraOutlook,
    zone: ZoneId,
    timeFormatter: DateTimeFormatter,
) {
    var expanded by remember { mutableStateOf(false) }
    val color = auroraColor(outlook.band)

    Column(modifier = Modifier.clickable { expanded = !expanded }) {
        ExpandableRowHeader(
            icon = Icons.Default.WbSunny,
            label = "AURORA",
            primaryContent = {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${outlook.band.label} · peak Kp ${"%.1f".format(outlook.peakKp)}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        "Around ${formatLocal(outlook.peakAt, zone, timeFormatter)} · geomag lat ${outlook.geomagneticLatitudeDeg.roundToInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
                KpPill(outlook.peakKp, color)
            },
            expanded = expanded,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Text(
                outlook.band.description,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun KpPill(kp: Double, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.35f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            "Kp ${"%.1f".format(kp)}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
            ),
            color = Color.White,
        )
    }
}

private fun auroraColor(band: AuroraBand): Color = when (band) {
    AuroraBand.QUIET -> Color(0xFF607D8B)
    AuroraBand.UNSETTLED -> Color(0xFFFFB300)
    AuroraBand.ACTIVE -> Color(0xFFFF6F00)
    AuroraBand.STORM -> Color(0xFFE53935)
}

// ---------- Satellite row ----------

@Composable
private fun SatelliteRow(
    passes: List<SatellitePass>,
    zone: ZoneId,
    timeFormatter: DateTimeFormatter,
) {
    var expanded by remember { mutableStateOf(false) }
    val next = passes.first()

    Column(modifier = Modifier.clickable { expanded = !expanded }) {
        ExpandableRowHeader(
            icon = Icons.Default.Satellite,
            label = "PASSES",
            primaryContent = {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${next.satelliteName} at ${formatLocal(next.start, zone, timeFormatter)}",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                    Text(
                        "${next.pathSummary()} · max ${next.maxElevationDeg.roundToInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
            },
            expanded = expanded,
        )
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                passes.forEach { pass ->
                    val dur = Duration.between(pass.start, pass.end).seconds
                    Text(
                        text = "${formatLocal(pass.start, zone, timeFormatter)} · ${pass.satelliteName} · ${pass.pathSummary()} · max ${pass.maxElevationDeg.roundToInt()}° · ${dur}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Visible passes only — satellite illuminated by sun, observer in twilight. Source: CelesTrak.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ---------- Common row scaffolding ----------

@Composable
private fun ExpandableRowHeader(
    icon: ImageVector,
    label: String,
    primaryContent: @Composable RowScope.() -> Unit,
    expanded: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Left rail icon — small, dim, lets the row's primary text breathe.
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            ),
            color = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.width(64.dp),
        )
        primaryContent()
        // Per-row chevron rotates on expand, just like the original card-
        // level chevron used to.
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            label = "tonight-row-chevron",
        )
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = Color.White.copy(alpha = 0.45f),
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation),
        )
    }
}

private fun formatLocal(
    instant: Instant,
    zone: ZoneId,
    formatter: DateTimeFormatter,
): String = formatter.format(instant.atZone(zone))

// ---------- Meteor-shower lookup (moved unchanged from the v1 card) ----------

/**
 * Single entry in the meteor-shower lookup table.
 *
 *   [start]..[end] is the activity window (day-of-year ranges, expressed as
 *   month/day pairs that wrap correctly across year boundaries — Quadrantids
 *   straddles new year).
 *   [peak] is the night of strongest activity.
 *   [zhr] is the published peak Zenithal Hourly Rate (meteors visible per
 *   hour at the zenith under perfect dark skies).
 *   [description] is the 2-3 sentence blurb shown when the row is
 *   expanded. Facts only — parent body, radiant constellation, what makes
 *   the shower notable. No marketing prose.
 *   [wikipediaUrl] is the canonical en.wikipedia.org article. Opened in the
 *   user's browser via Intent.ACTION_VIEW when "Learn more" is tapped.
 */
data class MeteorShower(
    val name: String,
    val start: MonthDay,
    val peak: MonthDay,
    val end: MonthDay,
    val zhr: Int,
    val description: String,
    val wikipediaUrl: String,
)

/**
 * IMO 2024 Working List of Visual Meteor Showers, abbreviated to the headline
 * annual showers most likely to produce a visible event from suburban skies.
 *
 * Source: imo.net/files/meteor-shower/cal2024.pdf. Re-validate every couple
 * of years against the IMO calendar — radiants are stable but ZHR estimates
 * occasionally get revised (notably the Geminids trending upward over the
 * past two decades).
 */
private val ANNUAL_SHOWERS = listOf(
    MeteorShower(
        name = "Quadrantids",
        start = MonthDay.of(Month.DECEMBER, 28),
        peak = MonthDay.of(Month.JANUARY, 4),
        end = MonthDay.of(Month.JANUARY, 12),
        zhr = 110,
        description = "One of the year's three strongest showers, but with a famously narrow peak — just six hours wide, so timing matters. " +
            "The radiant sits in the obsolete constellation Quadrans Muralis, now part of Boötes, low in the northeast after midnight. " +
            "Parent body is the minor planet 2003 EH₁, almost certainly a dormant comet fragment.",
        wikipediaUrl = "https://en.wikipedia.org/wiki/Quadrantids",
    ),
    MeteorShower(
        name = "Lyrids",
        start = MonthDay.of(Month.APRIL, 16),
        peak = MonthDay.of(Month.APRIL, 22),
        end = MonthDay.of(Month.APRIL, 25),
        zhr = 18,
        description = "Among the oldest recorded meteor showers — Chinese astronomers logged a Lyrid outburst in 687 BC. " +
            "Radiates near the bright star Vega in Lyra, climbing high overhead by pre-dawn for northern observers. " +
            "Parent body is the long-period comet C/1861 G1 Thatcher, which won't return until about 2283.",
        wikipediaUrl = "https://en.wikipedia.org/wiki/Lyrids",
    ),
    MeteorShower(
        name = "Eta Aquariids",
        start = MonthDay.of(Month.APRIL, 19),
        peak = MonthDay.of(Month.MAY, 6),
        end = MonthDay.of(Month.MAY, 28),
        zhr = 50,
        description = "Debris from Halley's Comet — the same parent body that feeds the October Orionids, sampled from the other side of the comet's orbit. " +
            "Best seen from the southern hemisphere, where the radiant rises higher; northern observers catch a more grazing display in the hour or two before dawn. " +
            "Fast meteors (~66 km/s) with long, glancing trails.",
        wikipediaUrl = "https://en.wikipedia.org/wiki/Eta_Aquariids",
    ),
    MeteorShower(
        name = "Southern δ Aquariids",
        start = MonthDay.of(Month.JULY, 18),
        peak = MonthDay.of(Month.JULY, 30),
        end = MonthDay.of(Month.AUGUST, 21),
        zhr = 25,
        description = "A long, low-rate southern-hemisphere shower with a broad plateau rather than a sharp peak — useful nights stretch across two weeks. " +
            "Parent body is most likely comet 96P/Machholz, though the link is not as clean as for the major showers. " +
            "Coincides with the early build-up of the Perseids, often producing a busier-than-usual sky for a few weeks.",
        wikipediaUrl = "https://en.wikipedia.org/wiki/Southern_delta_Aquariids",
    ),
    MeteorShower(
        name = "Perseids",
        start = MonthDay.of(Month.JULY, 17),
        peak = MonthDay.of(Month.AUGUST, 12),
        end = MonthDay.of(Month.AUGUST, 24),
        zhr = 100,
        description = "The classic summer shower for the northern hemisphere, radiating from Perseus high in the northeast after midnight. " +
            "Parent body is comet 109P/Swift–Tuttle, last at perihelion in 1992 and on a 133-year orbit. " +
            "Bright, fast meteors with a healthy fraction of fireballs — one of the most reliable nights of the astronomical year.",
        wikipediaUrl = "https://en.wikipedia.org/wiki/Perseids",
    ),
    MeteorShower(
        name = "Draconids",
        start = MonthDay.of(Month.OCTOBER, 6),
        peak = MonthDay.of(Month.OCTOBER, 8),
        end = MonthDay.of(Month.OCTOBER, 10),
        zhr = 10,
        description = "Short, sharp, and occasionally explosive — historically called the Giacobinids after parent comet 21P/Giacobini–Zinner. " +
            "Most years are quiet, but storm-level rates hit in 1933 and 1946, and a notable outburst occurred in 2011. " +
            "Unusual among showers in being best in the early evening, with the radiant in Draco already high at dusk.",
        wikipediaUrl = "https://en.wikipedia.org/wiki/Draconids",
    ),
    MeteorShower(
        name = "Orionids",
        start = MonthDay.of(Month.OCTOBER, 2),
        peak = MonthDay.of(Month.OCTOBER, 21),
        end = MonthDay.of(Month.NOVEMBER, 7),
        zhr = 25,
        description = "The second annual shower fed by Halley's Comet, radiating near the club of Orion above the famous belt. " +
            "Very fast meteors (~66 km/s) with a high fraction of persistent trains that hang in the sky for a second or two. " +
            "Broad maximum, so rates stay decent for a week either side of peak.",
        wikipediaUrl = "https://en.wikipedia.org/wiki/Orionids",
    ),
    MeteorShower(
        name = "Leonids",
        start = MonthDay.of(Month.NOVEMBER, 6),
        peak = MonthDay.of(Month.NOVEMBER, 17),
        end = MonthDay.of(Month.NOVEMBER, 30),
        zhr = 15,
        description = "Famous for its 33-year storm cycle tied to parent comet 55P/Tempel–Tuttle, with historic outbursts in 1833, 1866, 1966, and 2001 that reached tens of thousands of meteors per hour. " +
            "Normal years are modest, but the meteors are exceptionally fast (~71 km/s) and bright. " +
            "Radiant in the sickle of Leo rises after midnight.",
        wikipediaUrl = "https://en.wikipedia.org/wiki/Leonids",
    ),
    MeteorShower(
        name = "Geminids",
        start = MonthDay.of(Month.DECEMBER, 4),
        peak = MonthDay.of(Month.DECEMBER, 14),
        end = MonthDay.of(Month.DECEMBER, 20),
        zhr = 150,
        description = "Now the strongest reliable annual shower, with rates that have trended upward each decade since the mid-20th century. " +
            "Unique among the majors in that the parent body is an asteroid — 3200 Phaethon — not a comet, suggesting either a dormant comet nucleus or a rock-comet hybrid. " +
            "Slow, bright, multi-coloured meteors visible from mid-evening onward, with the radiant near Castor climbing all night.",
        wikipediaUrl = "https://en.wikipedia.org/wiki/Geminids",
    ),
    MeteorShower(
        name = "Ursids",
        start = MonthDay.of(Month.DECEMBER, 17),
        peak = MonthDay.of(Month.DECEMBER, 22),
        end = MonthDay.of(Month.DECEMBER, 26),
        zhr = 10,
        description = "A short, low-rate northern shower from comet 8P/Tuttle, often overlooked because it falls right after the Geminids. " +
            "Radiates near the bowl of Ursa Minor, close to Polaris, so it's circumpolar and visible all night from mid-northern latitudes. " +
            "Occasional outbursts have pushed rates above ZHR 50, most recently in 1945 and 1986.",
        wikipediaUrl = "https://en.wikipedia.org/wiki/Ursids",
    ),
)

/**
 * Result of a meteor-shower query against a given date: the active shower
 * with the highest ZHR (if any), plus a signed [daysFromPeak] (negative
 * before peak, positive after).
 */
data class ActiveShower(
    val shower: MeteorShower,
    val daysFromPeak: Int,
)

/**
 * Returns the strongest meteor shower currently active on [date], or null
 * if none of the annual showers' activity windows include this date.
 *
 * Window matching handles the year-boundary case (Quadrantids' Dec 28 → Jan
 * 12 span) by comparing month-day tuples directly rather than going through
 * day-of-year arithmetic — the latter is brittle around leap years.
 */
fun findActiveShower(date: LocalDate = LocalDate.now()): ActiveShower? {
    val today = MonthDay.from(date)
    return ANNUAL_SHOWERS
        .filter { it.isActiveOn(today) }
        .maxByOrNull { it.zhr }
        ?.let { ActiveShower(it, daysBetween(today, it.peak, date.year)) }
}

private fun MeteorShower.isActiveOn(day: MonthDay): Boolean {
    // The window wraps the year boundary when [start] is in December and
    // [end] is in January (Quadrantids). For wrap-around cases the
    // activity range is "≥ start OR ≤ end" rather than the usual "between".
    val wraps = start.month.value == 12 && end.month.value == 1
    return if (wraps) day >= start || day <= end
           else day in start..end
}

/**
 * Signed day count from [from] to [to], approximated by treating the
 * MonthDay pair as concrete dates in [year]. For wrap-around showers we
 * accept the small inaccuracy near the boundary (1-2 days) in exchange for
 * not having to thread an actual calendar through this code.
 */
private fun daysBetween(from: MonthDay, to: MonthDay, year: Int): Int {
    val a = from.atYear(year)
    val b = to.atYear(year)
    return (b.toEpochDay() - a.toEpochDay()).toInt()
}
