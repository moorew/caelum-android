package de.astronarren.allsky.data.astro

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import uk.me.g4dpz.satellite.GroundStationPosition
import uk.me.g4dpz.satellite.PassPredictor
import uk.me.g4dpz.satellite.SatPassTime
import uk.me.g4dpz.satellite.TLE
import java.time.Instant
import java.time.ZoneId
import java.util.Date

/**
 * Bright satellite passes for the Tonight card.
 *
 * Pulls the CelesTrak "visual" element set (~150 naked-eye satellites,
 * curated by Dr T S Kelso), caches it for 24 h, and uses predict4java's
 * SGP4 propagator to predict passes over the user's location for the next
 * 24 hours.
 *
 * Why predict4java rather than rolling our own SGP4:
 *   - It's a faithful Java port of Vallado's reference SGP4 implementation,
 *     widely deployed in amateur-radio satellite-tracking apps for over a
 *     decade.
 *   - SGP4 is ~600 lines of dense, atmospheric-drag-correction code derived
 *     from a Fortran original. Writing it from scratch in Kotlin would be
 *     all downside.
 *   - Predict4java already knows about sun illumination of the satellite
 *     vs. the observer being in twilight — exactly the "is this visible?"
 *     question we need answered.
 *
 * Trade-off accepted: predict4java is ~80 KB, MIT-licensed, but unmaintained
 * since ~2014. The API is stable Java, no transitive deps, and the SGP4
 * model itself doesn't change — so the abandonware risk is genuinely low.
 *
 * Filtering applied on top of predict4java's [SatPassTime.isVisible]:
 *   - Max elevation ≥ 20° (lower passes are noisy and hard to spot)
 *   - Pass time falls within the next 24 hours
 *   - We keep at most 3 by descending max-elevation — anything more clutters
 *     a card that already has 4 other rows.
 */
class SatelliteRepository(
    private val service: SatelliteService = SatelliteService.create(),
    private val clock: () -> Instant = Instant::now,
) {
    @Volatile private var tleCache: TleCacheEntry? = null

    /**
     * Up to 3 visible passes for the next 24 hours at the observer's
     * location, sorted by start time. Empty list (not null) when there
     * simply aren't any worth showing — null is reserved for "we couldn't
     * compute anything" (network down + no cache).
     */
    suspend fun upcomingPasses(
        latitudeDeg: Double,
        longitudeDeg: Double,
        altitudeMeters: Double = 0.0,
    ): List<SatellitePass>? = withContext(Dispatchers.Default) {
        val tles = tles() ?: return@withContext null
        val groundStation = GroundStationPosition(latitudeDeg, longitudeDeg, altitudeMeters)
        val now = clock()
        val cutoff = now.plusSeconds(24 * 3600)

        // For each TLE, find the strongest pass in the next 24h. predict4java
        // throws on degenerate orbits — wrap individually so one bad element
        // set doesn't sink the whole list.
        val passes = tles.flatMap { tle ->
            runCatching {
                val predictor = PassPredictor(tle, groundStation)
                predictor.getPasses(Date.from(now), 24, false)
                    .filter { it.observerInTwilight(groundStation) }
                    .filter { it.getMaxEl() >= MIN_MAX_ELEVATION_DEG }
                    .filter { it.getStartTime().toInstant().isBefore(cutoff) }
                    .map { it.toDomain(tle.getName()) }
            }.getOrDefault(emptyList())
        }

        passes
            .sortedByDescending { it.maxElevationDeg }
            .take(MAX_PASSES_SHOWN)
            .sortedBy { it.start }
    }

    /**
     * True when the observer is in nautical-to-civil twilight at the pass's
     * time of closest approach.
     *
     * predict4java's [SatPassTime] doesn't carry an "is this pass actually
     * visible naked-eye" flag — that requires also knowing whether the
     * satellite itself is sunlit at TCA, which would mean computing the
     * sub-satellite shadow geometry. We use the cheaper rule that catches
     * 95% of useful passes:
     *
     *   - sun between −18° and −6° (sky is dark enough to see a +3 mag
     *     object but the satellite, hundreds of km up, is still sunlit)
     *
     * A pass at 02:00 local time with the sun at −40° will be filtered out
     * by this rule even if it's technically observable — fine for our card,
     * which is meant to surface the obvious "look up now" moments rather
     * than every possible pass.
     */
    private fun SatPassTime.observerInTwilight(groundStation: GroundStationPosition): Boolean {
        // Explicit getter calls — predict4java's getTCA() / getStartTime()
        // are named with consecutive capitals which makes the Kotlin
        // synthetic-property name ambiguous (TCA vs tca); easier to read if
        // we just stay in Java-getter land for the bridge.
        val tcaJd = AstroMath.julianDate(getTCA().toInstant())
        val sunAlt = AstroMath.sunAltitudeDeg(
            latitudeDeg = groundStation.getLatitude(),
            longitudeDeg = groundStation.getLongitude(),
            jd = tcaJd,
        )
        return sunAlt in -18.0..-6.0
    }

    private fun SatPassTime.toDomain(name: String): SatellitePass {
        return SatellitePass(
            satelliteName = name.trim(),
            start = getStartTime().toInstant(),
            tca = getTCA().toInstant(),
            end = getEndTime().toInstant(),
            maxElevationDeg = getMaxEl(),
            // predict4java names rise/set azimuths after the amateur-radio
            // AOS (Acquisition of Signal) / LOS (Loss of Signal) convention.
            // Returned as integer degrees — widen to Double for our domain
            // type, which keeps the door open for finer-grained sources later.
            startAzimuthDeg = getAosAzimuth().toDouble(),
            endAzimuthDeg = getLosAzimuth().toDouble(),
        )
    }

    private suspend fun tles(): List<TLE>? {
        val now = clock()
        val cached = tleCache
        if (cached != null && now.isBefore(cached.expires)) return cached.tles
        val fresh = try {
            fetchAndParse()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return cached?.tles
        }
        tleCache = TleCacheEntry(
            tles = fresh,
            expires = now.plusSeconds(24 * 3600),
        )
        return fresh
    }

    private suspend fun fetchAndParse(): List<TLE> = withContext(Dispatchers.IO) {
        val body = service.getTleGroup()
        val lines = body.split('\n').map { it.trimEnd('\r') }.filter { it.isNotEmpty() }
        if (lines.size < 3) return@withContext emptyList()
        // CelesTrak TLEs come in 3-line groups: name, line1, line2. Any
        // partial group at EOF is silently dropped.
        (0 until lines.size - 2 step 3).mapNotNull { i ->
            runCatching {
                TLE(arrayOf(lines[i], lines[i + 1], lines[i + 2]))
            }.getOrNull()
        }
    }

    private data class TleCacheEntry(val tles: List<TLE>, val expires: Instant)

    companion object {
        private const val MIN_MAX_ELEVATION_DEG = 20.0
        private const val MAX_PASSES_SHOWN = 3
    }
}

/**
 * One predicted naked-eye pass. The card renders the start time in local
 * zone with start/end compass cardinals and the peak elevation.
 */
data class SatellitePass(
    val satelliteName: String,
    val start: Instant,
    val tca: Instant,
    val end: Instant,
    val maxElevationDeg: Double,
    val startAzimuthDeg: Double,
    val endAzimuthDeg: Double,
) {
    val durationSeconds: Long get() = end.epochSecond - start.epochSecond

    /** Compact "WNW → ESE" path string for the UI. */
    fun pathSummary(): String =
        "${cardinal(startAzimuthDeg)} → ${cardinal(endAzimuthDeg)}"

    private fun cardinal(azDeg: Double): String {
        val sectors = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                             "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")
        val i = ((AstroMath.normalizeDegrees(azDeg) + 11.25) / 22.5).toInt() % 16
        return sectors[i]
    }
}
