package de.astronarren.allsky.data.astro

import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

/**
 * Aurora visibility outlook for the Tonight card.
 *
 * The data source is NOAA SWPC's planetary-Kp 3-day forecast. We pull the
 * forecast, find the peak Kp over the next 24 hours, and combine that with
 * the user's geomagnetic latitude (approximated from saved lat/lon) to
 * decide whether to show the row at all.
 *
 * Aurora ovals expand toward the equator as Kp rises:
 *
 *   Kp 0–2: visible ~67° geomag and poleward
 *   Kp 3:   ~63° geomag
 *   Kp 5:   ~58° geomag (G1 storm)
 *   Kp 7:   ~52° geomag (G3 storm)
 *   Kp 9:   ~45° geomag (G5; once-a-decade)
 *
 * So at e.g. 45° N geomag latitude (most of mid-US, central Europe) the
 * row only fires on a strong storm; below ~40° geomag it essentially
 * never does. We use 45° as the gate: above that, always show; below,
 * suppress entirely. A traveller can flip the gate in Settings if they
 * want to see forecasts for somewhere else.
 *
 * Cache: in-memory, 1 hour TTL. The underlying forecast updates every
 * ~30 minutes on the SWPC side, so refreshing more often is wasted bytes
 * and refreshing less often misses meaningful changes.
 */
class AuroraRepository(
    private val service: AuroraService = AuroraService.create(),
    private val clock: () -> Instant = Instant::now,
) {
    @Volatile private var cached: CacheEntry? = null

    /**
     * Compute and return tonight's aurora outlook for the observer at the
     * given geographic coordinates, or null if the row should be hidden
     * (latitude too low, no forecast available, or network failure).
     *
     * Tonight is defined as the next 24 hours from now. Callers should
     * treat any non-null result as fresh enough to display.
     */
    suspend fun tonight(latitudeDeg: Double, longitudeDeg: Double): AuroraOutlook? {
        if (!isGeomagneticallyRelevant(latitudeDeg, longitudeDeg)) return null
        val forecast = forecast() ?: return null
        val now = clock()
        val cutoff = now.plusSeconds(24 * 3600)
        val window = forecast.filter { it.time >= now && it.time <= cutoff }
        if (window.isEmpty()) return null
        val peak = window.maxBy { it.kp }
        return AuroraOutlook(
            peakKp = peak.kp,
            peakAt = peak.time,
            geomagneticLatitudeDeg = geomagneticLatitudeDeg(latitudeDeg, longitudeDeg),
        )
    }

    /** Centred-dipole geomagnetic latitude approximation. Public for the unit test. */
    fun geomagneticLatitudeDeg(latitudeDeg: Double, longitudeDeg: Double): Double {
        // IGRF-13 dipole pole, epoch ~2025. The pole drifts ~10 km/year, so
        // this is good for the next decade without tuning. North magnetic
        // pole geodetic position: 80.65° N, 72.68° W.
        val poleLat = 80.65 * AstroMath.DEG_TO_RAD
        val poleLon = -72.68 * AstroMath.DEG_TO_RAD
        val lat = latitudeDeg * AstroMath.DEG_TO_RAD
        val lon = longitudeDeg * AstroMath.DEG_TO_RAD
        val sinPhi = sin(lat) * sin(poleLat) +
            cos(lat) * cos(poleLat) * cos(lon - poleLon)
        return asin(sinPhi.coerceIn(-1.0, 1.0)) * AstroMath.RAD_TO_DEG
    }

    private fun isGeomagneticallyRelevant(latDeg: Double, lonDeg: Double): Boolean {
        // |geomag lat| ≥ 45° lets a Kp ≈ 7 storm reach the horizon. Below
        // that, the user effectively never sees aurora regardless of the
        // forecast — hide the row entirely rather than dangle a permanent
        // "nope" string.
        return kotlin.math.abs(geomagneticLatitudeDeg(latDeg, lonDeg)) >= 45.0
    }

    private suspend fun forecast(): List<KpSample>? {
        val now = clock()
        val cached = cached
        if (cached != null && now.isBefore(cached.expires)) return cached.samples
        val fresh = try {
            fetch()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return cached?.samples
        }
        this.cached = CacheEntry(
            samples = fresh,
            expires = now.plusSeconds(3600),
        )
        return fresh
    }

    private suspend fun fetch(): List<KpSample> = withContext(Dispatchers.IO) {
        val body = service.getKpForecast()
        val root = JsonParser.parseString(body).asJsonArray
        if (root.size() < 2) return@withContext emptyList()
        // Row 0 is the header — confirm shape before trusting indices.
        val header = root[0].asJsonArray
        val timeIdx = (0 until header.size()).firstOrNull { header[it].asString == "time_tag" } ?: 0
        val kpIdx = (0 until header.size()).firstOrNull { header[it].asString == "kp" } ?: 1
        val parser = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        (1 until root.size()).mapNotNull { i ->
            val row = root[i].asJsonArray
            val tStr = row[timeIdx].asString
            val kpStr = row[kpIdx].asString
            val t = runCatching {
                LocalDateTime.parse(tStr, parser).toInstant(ZoneOffset.UTC)
            }.getOrNull() ?: return@mapNotNull null
            val kp = kpStr.toDoubleOrNull() ?: return@mapNotNull null
            KpSample(time = t, kp = kp)
        }
    }

    private data class CacheEntry(val samples: List<KpSample>, val expires: Instant)
}

/** One time-stamped Kp forecast sample. */
data class KpSample(val time: Instant, val kp: Double)

/**
 * What the Tonight card renders for the aurora row. The card uses the band
 * helper below to pick a colour and a one-word label ("QUIET", "ACTIVE",
 * "STORM", etc.) — kept here so the same wording stays in sync if we ever
 * expose this elsewhere (notification, widget).
 */
data class AuroraOutlook(
    val peakKp: Double,
    val peakAt: Instant,
    val geomagneticLatitudeDeg: Double,
) {
    val band: AuroraBand get() = AuroraBand.forKp(peakKp)
}

/**
 * NOAA's own scale (G0–G5) maps Kp to storm severity. We collapse to four
 * UI buckets — finer detail is noise at our display size.
 */
enum class AuroraBand(val label: String, val description: String) {
    QUIET("QUIET", "Kp ≤ 3 — no auroral activity expected outside the polar oval."),
    UNSETTLED("UNSETTLED", "Kp 4 — aurora overhead at high geomagnetic latitudes."),
    ACTIVE("ACTIVE", "Kp 5–6 (G1–G2) — aurora visible at mid-latitudes."),
    STORM("STORM", "Kp ≥ 7 (G3+) — aurora visible well into mid-latitudes; rare and worth chasing.");

    companion object {
        fun forKp(kp: Double): AuroraBand = when {
            kp >= 7.0 -> STORM
            kp >= 5.0 -> ACTIVE
            kp >= 4.0 -> UNSETTLED
            else -> QUIET
        }
    }
}
