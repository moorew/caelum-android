package de.astronarren.allsky.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * surfaces the brightest active shower with days-from-peak context.
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
 */
data class MeteorShower(
    val name: String,
    val start: MonthDay,
    val peak: MonthDay,
    val end: MonthDay,
    val zhr: Int,
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
    MeteorShower("Quadrantids",        MonthDay.of(Month.DECEMBER, 28), MonthDay.of(Month.JANUARY,  4), MonthDay.of(Month.JANUARY,  12), 110),
    MeteorShower("Lyrids",             MonthDay.of(Month.APRIL,    16), MonthDay.of(Month.APRIL,   22), MonthDay.of(Month.APRIL,    25),  18),
    MeteorShower("Eta Aquariids",      MonthDay.of(Month.APRIL,    19), MonthDay.of(Month.MAY,      6), MonthDay.of(Month.MAY,      28),  50),
    MeteorShower("Southern δ Aquariids", MonthDay.of(Month.JULY,  18), MonthDay.of(Month.JULY,    30), MonthDay.of(Month.AUGUST,   21),  25),
    MeteorShower("Perseids",           MonthDay.of(Month.JULY,     17), MonthDay.of(Month.AUGUST,  12), MonthDay.of(Month.AUGUST,   24), 100),
    MeteorShower("Draconids",          MonthDay.of(Month.OCTOBER,   6), MonthDay.of(Month.OCTOBER,  8), MonthDay.of(Month.OCTOBER,  10),  10),
    MeteorShower("Orionids",           MonthDay.of(Month.OCTOBER,   2), MonthDay.of(Month.OCTOBER, 21), MonthDay.of(Month.NOVEMBER,  7),  25),
    MeteorShower("Leonids",            MonthDay.of(Month.NOVEMBER,  6), MonthDay.of(Month.NOVEMBER,17), MonthDay.of(Month.NOVEMBER, 30),  15),
    MeteorShower("Geminids",           MonthDay.of(Month.DECEMBER,  4), MonthDay.of(Month.DECEMBER,14), MonthDay.of(Month.DECEMBER, 20), 150),
    MeteorShower("Ursids",             MonthDay.of(Month.DECEMBER, 17), MonthDay.of(Month.DECEMBER,22), MonthDay.of(Month.DECEMBER, 26),  10),
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
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (active != null) {
                    ActiveShowerRow(active)
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
