package de.astronarren.allsky.data.astro

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geocentric apparent position of the Moon + rise/set/transit timing for a
 * given observer.
 *
 * Implementation is the very-low-precision form of Meeus chapter 47 — five
 * dominant periodic terms for longitude/latitude, a single cosine for
 * parallax. Position accuracy is ~0.3°, more than enough to time rise/set to
 * within a minute or two. The full ELP-2000 truncation would buy us another
 * decimal we'll never read on a phone screen.
 *
 * Rise/set is found by stepping moon altitude over the local day on a
 * 5-minute grid and linearly interpolating across the h = h₀ crossing.
 * Cheaper and more robust than the iterative method in Meeus 15 for our
 * purpose: we never need sub-minute timing, and the moon's motion is fast
 * enough (~13°/day) that the grid resolution dominates the error anyway.
 *
 * The altitude threshold at rise/set is h₀ = 0.7275·π − 34′ where π is the
 * moon's horizontal parallax (~0.95°), per Meeus 15.1. Numerically that
 * works out near +0.125° — i.e. the geocentric centre is slightly *above*
 * the geometric horizon when the upper limb appears to touch it, because
 * parallax outweighs refraction at lunar distances.
 */
object MoonAlmanac {

    /** Rise/set altitude threshold for the Moon, in degrees. See class-doc. */
    private const val MOON_H0_DEG = 0.125

    /** Sampling grid for the rise/set scan — every five minutes of the day. */
    private const val SCAN_STEP_MINUTES = 5L

    /**
     * Geocentric apparent equatorial position of the Moon at the given JD.
     *
     * Returns RA/Dec referred to the equinox of date. Distance in km is also
     * exposed for callers (parallax adjustment, brightness scaling).
     */
    fun position(jd: Double): MoonPosition {
        val t = AstroMath.julianCenturies(jd)

        // Fundamental arguments — Meeus 47.1–47.5
        val lPrime = AstroMath.normalizeDegrees(218.3164591 + 481267.88134236 * t)
        val d = AstroMath.normalizeDegrees(297.8502042 + 445267.1115168 * t)
        val mSun = AstroMath.normalizeDegrees(357.5291092 + 35999.0502909 * t)
        val mPrime = AstroMath.normalizeDegrees(134.9634114 + 477198.8676313 * t)
        val f = AstroMath.normalizeDegrees(93.2720993 + 483202.0175273 * t)

        // Very-low-precision longitude / latitude / parallax (Meeus, end of
        // chapter 47). Five terms gives ~0.3° in λ — fine for naked-eye work.
        val mPrimeRad = mPrime * AstroMath.DEG_TO_RAD
        val dRad = d * AstroMath.DEG_TO_RAD
        val mSunRad = mSun * AstroMath.DEG_TO_RAD
        val fRad = f * AstroMath.DEG_TO_RAD

        val lambda = lPrime +
            6.289 * sin(mPrimeRad) -
            1.274 * sin(mPrimeRad - 2 * dRad) +
            0.658 * sin(2 * dRad) -
            0.186 * sin(mSunRad) -
            0.059 * sin(2 * mPrimeRad - 2 * dRad)
        val beta = 5.128 * sin(fRad) +
            0.281 * sin(mPrimeRad + fRad) +
            0.278 * sin(mPrimeRad - fRad) +
            0.173 * sin(2 * dRad - fRad)
        // Distance in km — we never actually surface it but other modules
        // might want it for apparent diameter / illumination computations.
        val distanceKm = 385000.56 - 20905.355 * cos(mPrimeRad)

        // Ecliptic → equatorial. Use mean obliquity; the nutation correction
        // matters only at the 0.01° level which is below our position error
        // budget.
        val eps = AstroMath.meanObliquityDeg(t) * AstroMath.DEG_TO_RAD
        val lambdaRad = lambda * AstroMath.DEG_TO_RAD
        val betaRad = beta * AstroMath.DEG_TO_RAD

        val ra = atan2(
            sin(lambdaRad) * cos(eps) - kotlin.math.tan(betaRad) * sin(eps),
            cos(lambdaRad),
        )
        val dec = asin(
            sin(betaRad) * cos(eps) + cos(betaRad) * sin(eps) * sin(lambdaRad),
        )

        // Illumination phase angle (Meeus 48.4 simplified): the geocentric
        // elongation between Moon and Sun. Good enough for "% lit" text;
        // not used for rise/set.
        val sun = AstroMath.sunEquatorial(jd)
        val cosElong = sin(dec) * sin(sun.decDeg * AstroMath.DEG_TO_RAD) +
            cos(dec) * cos(sun.decDeg * AstroMath.DEG_TO_RAD) *
            cos((ra * AstroMath.RAD_TO_DEG - sun.raDeg) * AstroMath.DEG_TO_RAD)
        val phaseAngleDeg = (180.0 - kotlin.math.acos(cosElong.coerceIn(-1.0, 1.0)) * AstroMath.RAD_TO_DEG)
        val illumination = (1.0 + cos(phaseAngleDeg * AstroMath.DEG_TO_RAD)) / 2.0

        return MoonPosition(
            equatorial = EquatorialCoords(
                raDeg = AstroMath.normalizeDegrees(ra * AstroMath.RAD_TO_DEG),
                decDeg = dec * AstroMath.RAD_TO_DEG,
            ),
            distanceKm = distanceKm,
            illuminatedFraction = illumination.coerceIn(0.0, 1.0),
        )
    }

    /**
     * Rise / transit / set times for the Moon over the local calendar date
     * [date] at [latitudeDeg]/[longitudeDeg], expressed in [zone].
     *
     * Any of the three may be null on a given day — the Moon can rise without
     * setting (and vice versa) on consecutive sidereal days, particularly at
     * high latitudes. Callers must handle nulls.
     *
     * Algorithm: sample moon altitude every [SCAN_STEP_MINUTES] across the
     * local day, then linearly interpolate sign changes of (altitude − h₀)
     * to find rise/set, and find the local maximum for the transit.
     */
    fun riseSetTransit(
        date: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): MoonEvents {
        val startLocal = ZonedDateTime.of(date, LocalTime.MIDNIGHT, zone)
        val endLocal = startLocal.plusDays(1)

        // Walk the day once, building a parallel array of (instant, altitude)
        // samples. Cheaper than recomputing positions twice for the rise/set
        // and transit passes.
        val stepSeconds = SCAN_STEP_MINUTES * 60
        val samples = mutableListOf<Sample>()
        var cursor = startLocal.toInstant()
        val stop = endLocal.toInstant()
        while (!cursor.isAfter(stop)) {
            val jd = AstroMath.julianDate(cursor)
            val pos = position(jd)
            val horizon = AstroMath.equatorialToHorizontal(
                raDeg = pos.equatorial.raDeg,
                decDeg = pos.equatorial.decDeg,
                latitudeDeg = latitudeDeg,
                longitudeDeg = longitudeDeg,
                jd = jd,
            )
            samples += Sample(cursor, horizon.altitudeDeg)
            cursor = cursor.plusSeconds(stepSeconds)
        }

        var rise: Instant? = null
        var set: Instant? = null
        var transit: Instant? = null
        var maxAlt = Double.NEGATIVE_INFINITY

        for (i in 1 until samples.size) {
            val prev = samples[i - 1]
            val now = samples[i]

            // Rise: prev below threshold, now at/above → linear interp.
            if (prev.altitude < MOON_H0_DEG && now.altitude >= MOON_H0_DEG && rise == null) {
                rise = interpolateCrossing(prev, now, MOON_H0_DEG)
            }
            // Set: prev at/above threshold, now below → linear interp.
            if (prev.altitude >= MOON_H0_DEG && now.altitude < MOON_H0_DEG && set == null) {
                set = interpolateCrossing(prev, now, MOON_H0_DEG)
            }

            if (now.altitude > maxAlt) {
                maxAlt = now.altitude
                transit = now.instant
            }
        }

        return MoonEvents(
            rise = rise,
            transit = if (maxAlt > MOON_H0_DEG) transit else null,
            set = set,
            transitAltitudeDeg = maxAlt,
        )
    }

    private fun interpolateCrossing(a: Sample, b: Sample, threshold: Double): Instant {
        val dAlt = b.altitude - a.altitude
        if (dAlt == 0.0) return a.instant
        val frac = (threshold - a.altitude) / dAlt
        val deltaNs = ((b.instant.toEpochMilli() - a.instant.toEpochMilli()) * frac * 1_000_000.0).toLong()
        return a.instant.plusNanos(deltaNs)
    }

    private data class Sample(val instant: Instant, val altitude: Double)
}

/**
 * Geocentric Moon snapshot. Distance is included so other modules (apparent
 * magnitude of objects passing near the Moon, light-pollution overlays) can
 * use it; the Tonight card itself only renders illumination.
 */
data class MoonPosition(
    val equatorial: EquatorialCoords,
    val distanceKm: Double,
    val illuminatedFraction: Double,
)

/**
 * Result of a rise/set/transit query. Any field may be null on the rare day
 * the Moon doesn't perform that event locally (lat-dependent and orbit-phase
 * dependent — happens roughly once every 25 days).
 */
data class MoonEvents(
    val rise: Instant?,
    val transit: Instant?,
    val set: Instant?,
    /** Peak altitude during the day at the observer's site, degrees. */
    val transitAltitudeDeg: Double,
)
