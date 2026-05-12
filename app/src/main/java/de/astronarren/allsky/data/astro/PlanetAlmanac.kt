package de.astronarren.allsky.data.astro

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Apparent positions, magnitudes, rise/set, and rough constellation for the
 * five naked-eye planets — Mercury, Venus, Mars, Jupiter, Saturn.
 *
 * The driver is JPL's "Approximate Positions of the Planets" (Solar System
 * Dynamics group), a six-element Keplerian model valid 1800–2050 with
 * accuracies of:
 *   - <1° in ecliptic longitude for the inner planets
 *   - <0.5° for Jupiter and Saturn
 * Translation: more precise than a phone screen can render, and never
 * embarrassing to a naked-eye observer.
 *
 * What this file is **not**: a planetarium engine. We don't compute
 * conjunctions, occultations, ring inclinations, or anything beyond
 * topocentric Alt/Az + apparent magnitude + rise/set. The Tonight card
 * surfaces "what's up and how bright" — that's the whole brief.
 */
object PlanetAlmanac {

    /**
     * Same 5-minute grid as [MoonAlmanac] — planets move <0.5°/day so a 5
     * minute step over-resolves the problem, but reusing the cadence keeps
     * the rise/set logic copy-pasteable and the runtime negligible (~300
     * trig calls per planet per day).
     */
    private const val SCAN_STEP_MINUTES = 5L

    /**
     * Standard altitude threshold for stellar/planetary rise/set: −34′,
     * Meeus 15.1. Atmospheric refraction lifts apparent positions by about
     * that amount when a body is on the geometric horizon.
     */
    private const val PLANET_H0_DEG = -0.5667

    /**
     * Tonight's snapshot for one planet at the observer's location, computed
     * at the given instant.
     */
    fun snapshot(
        planet: Planet,
        latitudeDeg: Double,
        longitudeDeg: Double,
        at: Instant,
    ): PlanetSnapshot {
        val jd = AstroMath.julianDate(at)
        val (geoEq, helioR, earthDist, phaseDeg) = geocentricApparent(planet, jd)
        val horiz = AstroMath.equatorialToHorizontal(
            raDeg = geoEq.raDeg,
            decDeg = geoEq.decDeg,
            latitudeDeg = latitudeDeg,
            longitudeDeg = longitudeDeg,
            jd = jd,
        )
        val mag = apparentMagnitude(planet, helioR, earthDist, phaseDeg)
        val constellation = zodiacConstellation(geoEq.raDeg, geoEq.decDeg, jd)
        return PlanetSnapshot(
            planet = planet,
            equatorial = geoEq,
            horizontal = horiz,
            apparentMagnitude = mag,
            constellation = constellation,
        )
    }

    /**
     * Rise / transit / set across the local day at the observer's site.
     * Same scan-and-interpolate approach as the Moon — see [MoonAlmanac].
     */
    fun riseSetTransit(
        planet: Planet,
        date: LocalDate,
        latitudeDeg: Double,
        longitudeDeg: Double,
        zone: ZoneId,
    ): PlanetEvents {
        val startLocal = ZonedDateTime.of(date, LocalTime.MIDNIGHT, zone)
        val endLocal = startLocal.plusDays(1)
        val stepSeconds = SCAN_STEP_MINUTES * 60

        var prevInstant: Instant? = null
        var prevAlt = Double.NaN
        var rise: Instant? = null
        var set: Instant? = null
        var transit: Instant? = null
        var maxAlt = Double.NEGATIVE_INFINITY

        var cursor = startLocal.toInstant()
        val stop = endLocal.toInstant()
        while (!cursor.isAfter(stop)) {
            val jd = AstroMath.julianDate(cursor)
            val geoEq = geocentricApparent(planet, jd).equatorial
            val alt = AstroMath.equatorialToHorizontal(
                raDeg = geoEq.raDeg,
                decDeg = geoEq.decDeg,
                latitudeDeg = latitudeDeg,
                longitudeDeg = longitudeDeg,
                jd = jd,
            ).altitudeDeg

            if (prevInstant != null && !prevAlt.isNaN()) {
                if (prevAlt < PLANET_H0_DEG && alt >= PLANET_H0_DEG && rise == null) {
                    rise = interp(prevInstant, prevAlt, cursor, alt, PLANET_H0_DEG)
                }
                if (prevAlt >= PLANET_H0_DEG && alt < PLANET_H0_DEG && set == null) {
                    set = interp(prevInstant, prevAlt, cursor, alt, PLANET_H0_DEG)
                }
            }
            if (alt > maxAlt) {
                maxAlt = alt
                transit = cursor
            }
            prevInstant = cursor
            prevAlt = alt
            cursor = cursor.plusSeconds(stepSeconds)
        }
        return PlanetEvents(
            rise = rise,
            transit = if (maxAlt > PLANET_H0_DEG) transit else null,
            set = set,
            transitAltitudeDeg = maxAlt,
        )
    }

    private fun interp(
        a: Instant, altA: Double,
        b: Instant, altB: Double,
        threshold: Double,
    ): Instant {
        val d = altB - altA
        if (d == 0.0) return a
        val frac = (threshold - altA) / d
        val deltaNs = ((b.toEpochMilli() - a.toEpochMilli()) * frac * 1_000_000.0).toLong()
        return a.plusNanos(deltaNs)
    }

    // ---------- Heliocentric / geocentric machinery ----------

    /**
     * Returns the planet's apparent geocentric equatorial position at JD,
     * plus the heliocentric distance r, geocentric distance Δ, and phase
     * angle α (all needed downstream for apparent-magnitude computation).
     *
     * No light-time correction — at naked-eye precision the worst-case
     * error is ~0.07° for Saturn (80-minute light-time × 0.0006°/min motion),
     * smaller than our model uncertainty.
     */
    private fun geocentricApparent(planet: Planet, jd: Double): ApparentPosition {
        val t = AstroMath.julianCenturies(jd)
        val planetHelio = heliocentricEclipticXYZ(planet, t)
        val earthHelio = heliocentricEclipticXYZ(Planet.EARTH, t)

        // Geocentric ecliptic = planet − earth (both heliocentric).
        val gx = planetHelio.x - earthHelio.x
        val gy = planetHelio.y - earthHelio.y
        val gz = planetHelio.z - earthHelio.z
        val earthDistance = sqrt(gx * gx + gy * gy + gz * gz)

        // Ecliptic (geocentric) → equatorial (geocentric).
        val eps = AstroMath.meanObliquityDeg(t) * AstroMath.DEG_TO_RAD
        val xe = gx
        val ye = gy * cos(eps) - gz * sin(eps)
        val ze = gy * sin(eps) + gz * cos(eps)

        val raRad = atan2(ye, xe)
        val decRad = atan2(ze, hypot(xe, ye))

        // Phase angle α at the planet: angle Sun–Planet–Earth.
        // cos α = (r² + Δ² − R²) / (2 r Δ), where R is Earth-Sun distance.
        val r = sqrt(planetHelio.x * planetHelio.x + planetHelio.y * planetHelio.y + planetHelio.z * planetHelio.z)
        val earthSun = sqrt(earthHelio.x * earthHelio.x + earthHelio.y * earthHelio.y + earthHelio.z * earthHelio.z)
        val cosPhase = (r * r + earthDistance * earthDistance - earthSun * earthSun) / (2.0 * r * earthDistance)
        val phaseDeg = acos(cosPhase.coerceIn(-1.0, 1.0)) * AstroMath.RAD_TO_DEG

        return ApparentPosition(
            equatorial = EquatorialCoords(
                raDeg = AstroMath.normalizeDegrees(raRad * AstroMath.RAD_TO_DEG),
                decDeg = decRad * AstroMath.RAD_TO_DEG,
            ),
            heliocentricDistanceAu = r,
            geocentricDistanceAu = earthDistance,
            phaseAngleDeg = phaseDeg,
        )
    }

    /**
     * Heliocentric ecliptic XYZ position of [planet] in AU, at Julian
     * centuries [t] from J2000.
     *
     * Six-element Keplerian propagation with linear element rates, JPL
     * "Approximate Positions of the Planets" (valid 1800–2050).
     */
    private fun heliocentricEclipticXYZ(planet: Planet, t: Double): Vec3 {
        val elem = planet.elements.at(t)
        val a = elem.a
        val e = elem.e
        val iRad = elem.iDeg * AstroMath.DEG_TO_RAD
        val omegaBarRad = elem.omegaBarDeg * AstroMath.DEG_TO_RAD
        val nodeRad = elem.nodeDeg * AstroMath.DEG_TO_RAD
        val omega = omegaBarRad - nodeRad     // argument of perihelion
        val meanAnomalyRad = (elem.lDeg - elem.omegaBarDeg) * AstroMath.DEG_TO_RAD

        val eccAnom = solveKepler(meanAnomalyRad, e)
        // Position in orbital plane (perihelion on +x).
        val xPrime = a * (cos(eccAnom) - e)
        val yPrime = a * sqrt(1.0 - e * e) * sin(eccAnom)

        // Standard rotation sequence: Rz(−ω), Rx(−i), Rz(−Ω) (applied in
        // reverse to match the perifocal → ecliptic transform).
        val cosOmega = cos(omega); val sinOmega = sin(omega)
        val cosI = cos(iRad); val sinI = sin(iRad)
        val cosNode = cos(nodeRad); val sinNode = sin(nodeRad)

        // Perifocal → ecliptic (Curtis, "Orbital Mechanics for Engineering
        // Students", eq. 4.49 — same as Meeus 33.9–11 with the elements
        // already reduced to the J2000 ecliptic by JPL's table).
        val x = (cosNode * cosOmega - sinNode * sinOmega * cosI) * xPrime +
                (-cosNode * sinOmega - sinNode * cosOmega * cosI) * yPrime
        val y = (sinNode * cosOmega + cosNode * sinOmega * cosI) * xPrime +
                (-sinNode * sinOmega + cosNode * cosOmega * cosI) * yPrime
        val z = (sinOmega * sinI) * xPrime + (cosOmega * sinI) * yPrime
        return Vec3(x, y, z)
    }

    /** Newton iteration on E − e sin E = M. Converges in <5 iterations for e<0.1. */
    private fun solveKepler(meanAnomaly: Double, e: Double): Double {
        // Normalise M to [−π, π] to keep the initial guess well-behaved.
        var m = meanAnomaly % (2 * PI)
        if (m > PI) m -= 2 * PI
        if (m < -PI) m += 2 * PI

        var ecc = if (e < 0.8) m else PI    // standard initial-guess heuristic
        repeat(20) {
            val f = ecc - e * sin(ecc) - m
            val fPrime = 1.0 - e * cos(ecc)
            val delta = f / fPrime
            ecc -= delta
            if (abs(delta) < 1e-9) return ecc
        }
        return ecc
    }

    // ---------- Apparent magnitudes ----------

    /**
     * V-band apparent magnitude. Formulas from the _Astronomical Almanac_
     * (2017 supplement, table E1) using observed phase coefficients.
     * Saturn's ring contribution is omitted — including it requires the ring
     * inclination, which our truncated element set doesn't carry; the
     * resulting error is at most ~0.7 mag at ring-plane crossings.
     */
    private fun apparentMagnitude(
        planet: Planet,
        rAu: Double,
        deltaAu: Double,
        alphaDeg: Double,
    ): Double {
        val a = alphaDeg
        val baseTerm = 5.0 * log10(rAu * deltaAu)
        return when (planet) {
            Planet.MERCURY -> -0.42 + baseTerm + 0.038 * a - 0.000273 * a * a + 0.000002 * a * a * a
            Planet.VENUS -> -4.40 + baseTerm + 0.0009 * a + 0.000239 * a * a - 0.00000065 * a * a * a
            Planet.MARS -> -1.52 + baseTerm + 0.016 * a
            Planet.JUPITER -> -9.40 + baseTerm + 0.005 * a
            Planet.SATURN -> -8.88 + baseTerm + 0.044 * a
            Planet.EARTH -> Double.NaN     // never asked of us; defensive default
        }
    }

    // ---------- Constellation lookup ----------

    /**
     * Returns the IAU constellation name for the given equatorial point.
     *
     * Implementation is a zodiac-only lookup keyed on ecliptic longitude —
     * we project RA/Dec back to ecliptic longitude and bin into the 13
     * IAU-recognised zodiac constellations (12 classical + Ophiuchus). The
     * planets never stray more than ~7° from the ecliptic, so the zodiac
     * lookup is correct >95% of the time. Off-zodiac edge cases (Mars near
     * the Cetus border, very rare) get the nearest zodiac constellation;
     * acceptable for a one-line "TONIGHT" surface.
     *
     * If we ever need the full 88-constellation answer, Roman's 1987 boundary
     * table is the canonical reference — but it's ~300 entries and not
     * justified for naked-eye work.
     */
    private fun zodiacConstellation(raDeg: Double, decDeg: Double, jd: Double): String {
        val t = AstroMath.julianCenturies(jd)
        val eps = AstroMath.meanObliquityDeg(t) * AstroMath.DEG_TO_RAD
        val raRad = raDeg * AstroMath.DEG_TO_RAD
        val decRad = decDeg * AstroMath.DEG_TO_RAD

        // Ecliptic longitude from equatorial (inverse of the eq→ec rotation).
        val sinLambda = sin(raRad) * cos(eps) + kotlin.math.tan(decRad) * sin(eps)
        val cosLambda = cos(raRad)
        val lambdaDeg = AstroMath.normalizeDegrees(atan2(sinLambda, cosLambda) * AstroMath.RAD_TO_DEG)

        // Boundaries projected onto the ecliptic, IAU 1930 → present.
        // Ranges open on the right.
        return when {
            lambdaDeg >= 351.6 || lambdaDeg < 28.7 -> "Pisces"
            lambdaDeg < 53.5 -> "Aries"
            lambdaDeg < 90.4 -> "Taurus"
            lambdaDeg < 118.3 -> "Gemini"
            lambdaDeg < 138.2 -> "Cancer"
            lambdaDeg < 173.9 -> "Leo"
            lambdaDeg < 217.8 -> "Virgo"
            lambdaDeg < 241.2 -> "Libra"
            lambdaDeg < 247.5 -> "Scorpius"
            lambdaDeg < 266.6 -> "Ophiuchus"
            lambdaDeg < 299.7 -> "Sagittarius"
            lambdaDeg < 327.6 -> "Capricornus"
            else -> "Aquarius"
        }
    }
}

/** Five visible planets, plus Earth for internal use only. */
enum class Planet(val displayName: String, internal val elements: PlanetaryElements) {
    MERCURY("Mercury", PlanetaryElements(
        a0 = 0.38709843, aRate = 0.00000000,
        e0 = 0.20563661, eRate = 0.00002123,
        i0 = 7.00497902, iRate = -0.00594749,
        l0 = 252.25032350, lRate = 149472.67411175,
        omegaBar0 = 77.45779628, omegaBarRate = 0.16047689,
        node0 = 48.33076593, nodeRate = -0.12534081,
    )),
    VENUS("Venus", PlanetaryElements(
        a0 = 0.72332102, aRate = -0.00000026,
        e0 = 0.00676399, eRate = -0.00005107,
        i0 = 3.39777545, iRate = 0.00043494,
        l0 = 181.97970850, lRate = 58517.81538729,
        omegaBar0 = 131.76755713, omegaBarRate = 0.00268329,
        node0 = 76.67261496, nodeRate = -0.27274174,
    )),
    EARTH("Earth", PlanetaryElements(
        a0 = 1.00000018, aRate = -0.00000003,
        e0 = 0.01673163, eRate = -0.00003661,
        i0 = -0.00054346, iRate = -0.01337178,
        l0 = 100.46691572, lRate = 35999.37306329,
        omegaBar0 = 102.93005885, omegaBarRate = 0.31795260,
        node0 = -5.11260389, nodeRate = -0.24123856,
    )),
    MARS("Mars", PlanetaryElements(
        a0 = 1.52371243, aRate = 0.00000097,
        e0 = 0.09336511, eRate = 0.00009149,
        i0 = 1.85181869, iRate = -0.00724757,
        l0 = -4.56813164, lRate = 19140.29934243,
        omegaBar0 = -23.91744784, omegaBarRate = 0.45223625,
        node0 = 49.71320984, nodeRate = -0.26852431,
    )),
    JUPITER("Jupiter", PlanetaryElements(
        a0 = 5.20248019, aRate = -0.00002864,
        e0 = 0.04853590, eRate = 0.00018026,
        i0 = 1.29861416, iRate = -0.00322699,
        l0 = 34.33479152, lRate = 3034.90371757,
        omegaBar0 = 14.27495244, omegaBarRate = 0.18199196,
        node0 = 100.29282654, nodeRate = 0.13024619,
    )),
    SATURN("Saturn", PlanetaryElements(
        a0 = 9.54149883, aRate = -0.00003065,
        e0 = 0.05550825, eRate = -0.00032044,
        i0 = 2.49424102, iRate = 0.00451969,
        l0 = 50.07571329, lRate = 1222.11494724,
        omegaBar0 = 92.86136063, omegaBarRate = 0.54179478,
        node0 = 113.63998702, nodeRate = -0.25015002,
    ));

    companion object {
        /** Just the five naked-eye targets — Earth is internal. */
        val NAKED_EYE: List<Planet> = listOf(MERCURY, VENUS, MARS, JUPITER, SATURN)
    }
}

/**
 * Six-element Keplerian set plus per-century linear rates. JPL provides this
 * as the simplest possible model that still hits the precision we need.
 */
internal data class PlanetaryElements(
    val a0: Double, val aRate: Double,           // semi-major axis (AU)
    val e0: Double, val eRate: Double,           // eccentricity
    val i0: Double, val iRate: Double,           // inclination (deg)
    val l0: Double, val lRate: Double,           // mean longitude (deg)
    val omegaBar0: Double, val omegaBarRate: Double, // longitude of perihelion (deg)
    val node0: Double, val nodeRate: Double,     // longitude of ascending node (deg)
) {
    fun at(t: Double): InstantElements = InstantElements(
        a = a0 + aRate * t,
        e = e0 + eRate * t,
        iDeg = i0 + iRate * t,
        lDeg = l0 + lRate * t,
        omegaBarDeg = omegaBar0 + omegaBarRate * t,
        nodeDeg = node0 + nodeRate * t,
    )
}

internal data class InstantElements(
    val a: Double, val e: Double, val iDeg: Double,
    val lDeg: Double, val omegaBarDeg: Double, val nodeDeg: Double,
)

/** Tiny 3-vector. Inlined rather than pulled from a vector lib for clarity. */
private data class Vec3(val x: Double, val y: Double, val z: Double)

private data class ApparentPosition(
    val equatorial: EquatorialCoords,
    val heliocentricDistanceAu: Double,
    val geocentricDistanceAu: Double,
    val phaseAngleDeg: Double,
)

/** One-line snapshot for the Tonight card. */
data class PlanetSnapshot(
    val planet: Planet,
    val equatorial: EquatorialCoords,
    val horizontal: HorizontalCoords,
    /** V-band apparent magnitude, lower = brighter. Venus ≈ −4, Mercury ≈ −1. */
    val apparentMagnitude: Double,
    /** IAU zodiac constellation the planet currently sits in. */
    val constellation: String,
)

data class PlanetEvents(
    val rise: Instant?,
    val transit: Instant?,
    val set: Instant?,
    val transitAltitudeDeg: Double,
)
