package de.astronarren.allsky.data

/**
 * 4-level rating for a night's viewing conditions. The split between
 * [FAIR] and [GOOD] is what drives the "tonight just got better" push
 * notification — anything moving from the BAD half to the GOOD half is
 * what users want to know about, the smaller wobbles are noise.
 *
 * The category boundary is intentionally pessimistic: [FAIR] sits in the
 * BAD half because a 40–65% cloudy night is rarely worth setting up gear
 * for, and we'd rather under-notify than spam.
 */
enum class SkyRating(val label: String) {
    POOR("Poor"),
    FAIR("Fair"),
    GOOD("Good"),
    EXCELLENT("Excellent");

    val isGoodEnoughToNotify: Boolean
        get() = this == GOOD || this == EXCELLENT
}

object SkyRater {

    /**
     * Filters the forecast list to the 21:00–05:00 local window (using the
     * timezone offset from OWM's `city.timezone` if available — passing
     * `tzOffsetSeconds = 0` falls back to the device timezone the way
     * SimpleDateFormat normally would). Returns null when no forecast
     * points fall in the window — that's the "we don't know yet" case and
     * the caller should *not* fire an alert.
     */
    fun rateNight(
        forecasts: List<WeatherData>,
        sunsetEpochSec: Long? = null,
    ): SkyRating? {
        if (forecasts.isEmpty()) return null

        // Window: from sunset (or 21:00 if we don't have sunset) for the
        // next ~10 hours. Using sunset when available means the rating is
        // robust to high-latitude summer nights where 21:00 is still
        // daylight.
        val nightWindow = if (sunsetEpochSec != null) {
            val start = sunsetEpochSec
            val end = sunsetEpochSec + 10 * 60 * 60
            forecasts.filter { it.dt in start..end }
        } else {
            forecasts.filter {
                val hour = java.text.SimpleDateFormat("HH", java.util.Locale.US)
                    .format(java.util.Date(it.dt * 1000)).toInt()
                hour >= 21 || hour <= 5
            }
        }

        if (nightWindow.isEmpty()) return null

        // Penalize precipitation hard — a 30% cloudy night with rain is
        // worse for viewing than an 80% cloudy night that's dry.
        val hasPrecip = nightWindow.any { wd ->
            wd.weather.any { w ->
                val m = w.main
                m.contains("Rain", true) ||
                m.contains("Snow", true) ||
                m.contains("Thunder", true) ||
                m.contains("Drizzle", true)
            }
        }
        if (hasPrecip) return SkyRating.POOR

        val avgClouds = nightWindow.map { it.clouds.all }.average()
        return when {
            avgClouds < 20.0 -> SkyRating.EXCELLENT
            avgClouds < 40.0 -> SkyRating.GOOD
            avgClouds < 65.0 -> SkyRating.FAIR
            else -> SkyRating.POOR
        }
    }
}
