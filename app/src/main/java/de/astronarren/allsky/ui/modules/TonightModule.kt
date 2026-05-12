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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.astronarren.allsky.ui.components.GlassCard
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import kotlin.math.abs

/**
 * "What's worth looking at tonight" — a durable, future-proof module that
 * supersedes the original ISS-pass overlay idea.
 *
 * v1 (this file): meteor-shower lookup against the IMO 2024 working list.
 * Dates are stable year-over-year (radiants are tied to Earth's orbit, not
 * the calendar year), so a hard-coded table is correct and offline. The card
 * surfaces the brightest active shower with days-from-peak context, a
 * curated description, and a "Learn more" link out to Wikipedia.
 *
 * Why curated text + web link instead of an AI call:
 *   - The set of headline annual showers is small and fixed (~10), so a
 *     hand-written blurb per row is cheaper than any API integration.
 *   - Astronomy is a domain where hallucinated radiants or peak rates would
 *     be embarrassing; static text doesn't drift.
 *   - Zero infrastructure, zero quotas, works offline, ships today.
 *
 * Future slices, added without rewriting this module:
 *   - Visible planet round-up (Meeus algorithms; pure local compute)
 *   - Moon rise / set tonight (Meeus chapter 15)
 *   - Bright satellite passes from CelesTrak TLEs (network, cached)
 *   - Aurora KP forecast (NOAA SWPC, opt-in by latitude)
 *
 * Each is its own row in the card. Today we only show one — the active
 * shower — and a "more coming" hint, so the module ships with non-zero value
 * even before the rest of the data sources are wired up.
 */

/**
 * Single entry in the meteor-shower lookup table.
 *
 *   [start]..[end] is the activity window (day-of-year ranges, expressed as
 *   month/day pairs that wrap correctly across year boundaries — Quadrantids
 *   straddles new year).
 *   [peak] is the night of strongest activity.
 *   [zhr] is the published peak Zenithal Hourly Rate (meteors visible per
 *   hour at the zenith under perfect dark skies).
 *   [description] is the 2-3 sentence blurb shown when the card is
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

@Composable
fun TonightModule(modifier: Modifier = Modifier) {
    // Recompute once on composition. The active shower changes day-to-day,
    // not by the second, so we don't bother re-keying on time.
    val active = remember { findActiveShower() }

    // Expansion state lives in the card so each Tonight row can independently
    // collapse — sets us up for the future planet / moon / aurora rows that
    // will each carry their own description.
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        GlassCard(
            modifier = if (active != null) {
                Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            } else {
                Modifier.fillMaxWidth()
            },
            cornerRadius = 24.dp,
            elevated = true
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
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
                        modifier = Modifier.weight(1f)
                    )
                    if (active != null) {
                        // Chevron rotates 180° on expand to telegraph the
                        // tap target without needing a visible "expand" word.
                        val rotation by animateFloatAsState(
                            targetValue = if (expanded) 180f else 0f,
                            label = "tonight-chevron-rotation"
                        )
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(rotation)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (active != null) {
                    ActiveShowerRow(active)
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        ShowerDetails(active.shower)
                    }
                } else {
                    Text(
                        "No major shower active tonight.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Roadmap hint — tells users (and our future selves) that
                // this card is going to grow. Removed once at least two
                // data sources are wired in.
                Text(
                    text = "More — visible planets, moon rise/set, bright satellite passes — coming in future releases.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun ActiveShowerRow(active: ActiveShower) {
    val days = active.daysFromPeak
    val timing = when {
        days == 0 -> "peaks tonight"
        days > 0  -> "peaked ${days}d ago"
        else      -> "peaks in ${abs(days)}d"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // ZHR pill — visual weight scales with how strong the shower is.
        // Geminids (150) gets brighter green than Ursids (10).
        val pillAlpha = (active.shower.zhr / 200f).coerceIn(0.18f, 0.5f)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF66BB6A).copy(alpha = pillAlpha))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                "ZHR ${active.shower.zhr}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                ),
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                active.shower.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
            Text(
                timing,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.65f)
            )
        }
    }
}

/**
 * Expanded section: curated description + a button that hands the
 * Wikipedia URL to whatever browser the user has set as default. We use a
 * plain Intent.ACTION_VIEW rather than a Custom Tab to avoid pulling in the
 * androidx.browser dependency just for this one link — most users have a
 * preferred browser and Android's chooser handles the rest.
 */
@Composable
private fun ShowerDetails(shower: MeteorShower) {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Text(
            text = shower.description,
            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
            color = Color.White.copy(alpha = 0.85f)
        )
        Spacer(modifier = Modifier.height(10.dp))
        TextButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(shower.wikipediaUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Activity-not-found is vanishingly rare on Android (every
                // device ships with at least one browser), but guarding
                // keeps a corrupted ROM from crashing the card.
                runCatching { context.startActivity(intent) }
            },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                "Learn more on Wikipedia",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
