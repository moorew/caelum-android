package de.astronarren.allsky.data.astro

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * Shared primitives for the Tonight card's astronomy rows.
 *
 * Everything here is pure: takes inputs, returns numbers, no Android or
 * coroutine dependencies. Each row module (moon, planets, aurora gating,
 * satellite-pass viewing geometry) calls into this file rather than carrying
 * its own copy of the same trigonometry.
 *
 * Implementations follow Meeus, _Astronomical Algorithms_ 2nd ed (1998),
 * truncated to the low-precision forms — for naked-eye horizon timing the
 * full VSOP87 series would be silly. Errors documented per-function.
 *
 * Angle convention: degrees on the API surface, radians internally. The
 * Kotlin stdlib trig functions take radians, so we centralise the
 * conversions here and keep call sites readable.
 */
object AstroMath {

    /** π/180, cached so we don't repeatedly divide. */
    const val DEG_TO_RAD: Double = PI / 180.0
    const val RAD_TO_DEG: Double = 180.0 / PI

    /** Reference epoch J2000.0 as a Julian Day Number. */
    const val J2000: Double = 2451545.0

    /** Mean solar day in Julian centuries: 1 / 36525. */
    private const val INV_CENTURY = 1.0 / 36525.0

    // ---------- Time scales ----------

    /**
     * Julian Day Number for the given UTC instant.
     *
     * Meeus chapter 7, Gregorian calendar branch only — the app cannot
     * possibly need Julian-calendar dates and the extra branch is just rope.
     * Accurate to better than 1 ms over the current millennium.
     */
    fun julianDate(utc: Instant): Double {
        val dt = LocalDateTime.ofInstant(utc, ZoneOffset.UTC)
        var year = dt.year
        var month = dt.monthValue
        val day = dt.dayOfMonth +
            (dt.hour + (dt.minute + dt.second / 60.0) / 60.0) / 24.0

        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = floor(year / 100.0)
        val b = 2.0 - a + floor(a / 4.0)
        return floor(365.25 * (year + 4716)) +
            floor(30.6001 * (month + 1)) +
            day + b - 1524.5
    }

    /** Julian centuries since J2000.0 for the given JD. */
    fun julianCenturies(jd: Double): Double = (jd - J2000) * INV_CENTURY

    /**
     * Greenwich Mean Sidereal Time in degrees [0, 360).
     *
     * Meeus 12.4. Good to ~0.1″ over a few centuries either side of J2000 —
     * far better than anything we use it for.
     */
    fun gmstDegrees(jd: Double): Double {
        val t = julianCenturies(jd)
        val theta = 280.46061837 +
            360.98564736629 * (jd - J2000) +
            t * t * 0.000387933 -
            t * t * t / 38710000.0
        return normalizeDegrees(theta)
    }

    /** Local Apparent Sidereal Time in degrees [0, 360) — GMST + longitude. */
    fun lstDegrees(jd: Double, longitudeDeg: Double): Double =
        normalizeDegrees(gmstDegrees(jd) + longitudeDeg)

    // ---------- Coordinate conversions ----------

    /**
     * Convert equatorial (RA, Dec) to horizontal (altitude, azimuth) for an
     * observer at [latitudeDeg]/[longitudeDeg] at the given JD.
     *
     * Returned azimuth follows the modern convention: 0° = north, 90° = east.
     * Meeus 13.6 with the south-as-origin formula, then +180° to swing the
     * origin to north.
     *
     * Refraction is **not** applied — callers that need it (rise/set timing)
     * tack it on afterwards with [atmosphericRefractionDeg].
     */
    fun equatorialToHorizontal(
        raDeg: Double,
        decDeg: Double,
        latitudeDeg: Double,
        longitudeDeg: Double,
        jd: Double,
    ): HorizontalCoords {
        val ha = normalizeDegrees(lstDegrees(jd, longitudeDeg) - raDeg)
        val haRad = ha * DEG_TO_RAD
        val decRad = decDeg * DEG_TO_RAD
        val latRad = latitudeDeg * DEG_TO_RAD

        val sinAlt = sin(latRad) * sin(decRad) +
            cos(latRad) * cos(decRad) * cos(haRad)
        val altRad = asin(sinAlt.coerceIn(-1.0, 1.0))

        // Meeus 13.6 — south-as-origin azimuth. We add 180° below.
        val azRad = atan2(
            sin(haRad),
            cos(haRad) * sin(latRad) - tan(decRad) * cos(latRad),
        )
        val azFromNorth = normalizeDegrees(azRad * RAD_TO_DEG + 180.0)
        return HorizontalCoords(altRad * RAD_TO_DEG, azFromNorth)
    }

    /**
     * Bennett's atmospheric refraction formula (Meeus 16.4), in degrees.
     *
     * Valid for apparent altitudes ≥ −1°. For rise/set we use it backwards:
     * a body's true altitude at rise/set is −R(0°) ≈ −0.5667°, i.e. the
     * geometric centre sits below the horizon by R when the upper limb
     * appears to touch it.
     */
    fun atmosphericRefractionDeg(apparentAltDeg: Double): Double {
        if (apparentAltDeg < -1.0) return 0.0
        val arg = (apparentAltDeg + 10.3 / (apparentAltDeg + 5.11)) * DEG_TO_RAD
        // 1.02 / tan(arg), expressed in arcminutes per Bennett, then /60 for
        // degrees.
        return 1.02 / tan(arg) / 60.0
    }

    // ---------- Solar position (low precision, Meeus 25) ----------

    /**
     * Geocentric apparent equatorial position of the Sun.
     *
     * Meeus chapter 25 low-precision formulas. Accurate to ~0.01° in
     * ecliptic longitude — plenty for twilight gating and rise/set times.
     * The returned coordinates are referred to the equinox of date.
     */
    fun sunEquatorial(jd: Double): EquatorialCoords {
        val t = julianCenturies(jd)
        // Geometric mean longitude (degrees), mean anomaly (degrees)
        val l0 = normalizeDegrees(280.46646 + 36000.76983 * t + 0.0003032 * t * t)
        val m = normalizeDegrees(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val mRad = m * DEG_TO_RAD

        // Equation of centre
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(mRad) +
            (0.019993 - 0.000101 * t) * sin(2 * mRad) +
            0.000289 * sin(3 * mRad)
        val trueLong = l0 + c            // True longitude, degrees
        // Nutation/aberration combined into the apparent-longitude correction
        val omega = 125.04 - 1934.136 * t
        val apparentLong = trueLong - 0.00569 - 0.00478 * sin(omega * DEG_TO_RAD)
        val apparentLongRad = apparentLong * DEG_TO_RAD

        // Mean obliquity (Meeus 22.2) + nutation in obliquity (low-precision)
        val eps0 = meanObliquityDeg(t)
        val epsRad = (eps0 + 0.00256 * cos(omega * DEG_TO_RAD)) * DEG_TO_RAD

        val ra = atan2(cos(epsRad) * sin(apparentLongRad), cos(apparentLongRad))
        val dec = asin(sin(epsRad) * sin(apparentLongRad))
        return EquatorialCoords(
            raDeg = normalizeDegrees(ra * RAD_TO_DEG),
            decDeg = dec * RAD_TO_DEG,
        )
    }

    /** Solar altitude at the given location/time, in degrees. Convenience wrapper. */
    fun sunAltitudeDeg(
        latitudeDeg: Double,
        longitudeDeg: Double,
        jd: Double,
    ): Double {
        val (ra, dec) = sunEquatorial(jd)
        return equatorialToHorizontal(ra, dec, latitudeDeg, longitudeDeg, jd).altitudeDeg
    }

    /**
     * Mean obliquity of the ecliptic in degrees (Meeus 22.2, IAU 1980).
     *
     * Public because the planet & moon modules use it directly when
     * computing apparent positions.
     */
    fun meanObliquityDeg(t: Double): Double =
        23.0 + 26.0 / 60.0 + 21.448 / 3600.0 -
            (46.8150 * t + 0.00059 * t * t - 0.001813 * t * t * t) / 3600.0

    // ---------- Twilight helpers ----------

    /** Six well-known sun-altitude thresholds. Encoded once so call sites don't fight magic numbers. */
    enum class TwilightPhase(val sunAltDeg: Double) {
        /** Sun's upper limb at the horizon — geometric rise/set with refraction. */
        SUNRISE_SUNSET(-0.833),
        /** Sun centre 6° below — bright planets/stars start to show. */
        CIVIL(-6.0),
        /** Sun centre 12° below — horizon still discernible, brighter stars naked-eye. */
        NAUTICAL(-12.0),
        /** Sun centre 18° below — full dark. Astronomical "night". */
        ASTRONOMICAL(-18.0),
    }

    /**
     * True when the sun is at or below the named twilight altitude — i.e.
     * the sky is at least that dark.
     */
    fun isAtLeastAsDarkAs(
        phase: TwilightPhase,
        latitudeDeg: Double,
        longitudeDeg: Double,
        jd: Double,
    ): Boolean = sunAltitudeDeg(latitudeDeg, longitudeDeg, jd) <= phase.sunAltDeg

    // ---------- Generic helpers ----------

    /** Reduce a degree value to [0, 360). Handles arbitrarily large negatives. */
    fun normalizeDegrees(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** Reduce to [-180, 180) — handy for hour-angle / azimuth deltas. */
    fun signedDegrees(deg: Double): Double {
        val r = normalizeDegrees(deg)
        return if (r >= 180.0) r - 360.0 else r
    }

    /**
     * Convert a JD back to an Instant. Inverse of [julianDate]. Useful for
     * rise/set timing where we solve for a JD and then need to format it as
     * local time for display.
     */
    fun julianDateToInstant(jd: Double): Instant {
        // jd is days since -4712-01-01T12:00 UTC. Shift to the Unix epoch
        // (JD 2440587.5) and convert.
        val seconds = (jd - 2440587.5) * 86400.0
        val whole = seconds.toLong()
        val nanos = ((seconds - whole) * 1_000_000_000.0).toLong()
        return Instant.ofEpochSecond(whole, nanos)
    }

    /** Convenience: noon-local JD for the given date and time zone. */
    fun jdForLocalNoon(date: LocalDate, zone: ZoneId): Double {
        val noonLocal = ZonedDateTime.of(date, LocalTime.NOON, zone)
        return julianDate(noonLocal.toInstant())
    }
}

/** Equatorial coordinates, both in degrees. RA is [0, 360), Dec is [-90, 90]. */
data class EquatorialCoords(val raDeg: Double, val decDeg: Double)

/** Topocentric horizontal coordinates, both in degrees. Azimuth from north, clockwise. */
data class HorizontalCoords(val altitudeDeg: Double, val azimuthDeg: Double) {
    /** Compass cardinal — coarse direction string for at-a-glance UI. */
    val cardinal: String
        get() {
            val sectors = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
            val i = ((AstroMath.normalizeDegrees(azimuthDeg) + 22.5) / 45.0).toInt() % 8
            return sectors[i]
        }
}

/**
 * Internal-use helper: tighten a hash-map of "almost zero" doubles to
 * exactly zero so callers using == on flagged fields aren't bitten by FP
 * noise. Not exposed; used by the unit tests.
 */
internal fun Double.snapZero(epsilon: Double = 1e-9): Double =
    if (abs(this) < epsilon) 0.0 else this
