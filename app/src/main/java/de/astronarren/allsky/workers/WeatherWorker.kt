package de.astronarren.allsky.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.astronarren.allsky.data.SkyRater
import de.astronarren.allsky.data.SkyRating
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.data.network.WeatherApiProvider
import de.astronarren.allsky.utils.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Periodic (3-hourly) weather refresh. Two responsibilities:
 *
 *   1. Pull the OWM 5-day/3-hour forecast so the widget and main UI have
 *      fresh data on their next read.
 *   2. When the user has opted in via the drawer toggle, detect when
 *      *tonight's* viewing rating jumps from POOR/FAIR up to GOOD/EXCELLENT
 *      and fire a single notification per night for that improvement.
 *
 * The transition detector is intentionally one-way (only "got better" wakes
 * the user) and per-night (the date is captured as `last_night_rating` so
 * the rating from yesterday can't suppress today's alert).
 */
class WeatherWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = UserPreferences(applicationContext)
        val apiKey = prefs.getApiKey()
        if (apiKey.isBlank()) return Result.failure()

        val lat = prefs.getLatitude().toDoubleOrNull()
        val lon = prefs.getLongitude().toDoubleOrNull()
        if (lat == null || lon == null) return Result.failure()

        val response = try {
            WeatherApiProvider.provideWeatherService()
                .getForecast(lat = lat, lon = lon, apiKey = apiKey)
        } catch (e: Exception) {
            return Result.retry()
        }

        // Done with the network — everything below is local logic. We always
        // succeed from here so a refresh that produced data isn't retried
        // even if the user has alerts off.
        if (!prefs.isSkyAlertsEnabled()) return Result.success()

        // The "night key" is today's date in the device timezone. We only
        // use this to suppress duplicates — the actual rating window is
        // anchored on sunset returned by OWM, so high-latitude users with
        // 23:00 sunsets aren't penalised.
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))

        val newRating = SkyRater.rateNight(
            forecasts = response.list,
            sunsetEpochSec = response.city.sunset,
        ) ?: return Result.success() // not enough night-window points yet

        val previous = prefs.getLastNightRating()
        val prevRatingForToday: SkyRating? = previous
            ?.takeIf { it.first == todayKey }
            ?.let { runCatching { SkyRating.valueOf(it.second) }.getOrNull() }

        // Always persist the latest reading. We do this before the alert
        // check so two workers racing can't both push the same notification.
        prefs.saveLastNightRating(todayKey, newRating.name)

        // Fire only when this run is the one that *crossed* the line. If
        // there was no previous reading for today, we treat a GOOD/EXCELLENT
        // reading as the first knowledge of tonight and notify — that's the
        // "you opened the app at noon and conditions just resolved" case.
        val crossed = newRating.isGoodEnoughToNotify &&
            (prevRatingForToday == null || !prevRatingForToday.isGoodEnoughToNotify)

        if (crossed) {
            // Cloud % to put in the body — use the average over the night
            // window for honesty. SkyRater already filtered for us; recompute
            // here rather than threading it back out of the rater.
            val nightWindow = response.list.filter {
                it.dt in response.city.sunset..(response.city.sunset + 10 * 60 * 60)
            }
            val avgClouds = if (nightWindow.isNotEmpty()) {
                nightWindow.map { it.clouds.all }.average().toInt()
            } else 0

            NotificationHelper(applicationContext).showSkyRatingImprovedNotification(
                previousLabel = prevRatingForToday?.label ?: SkyRating.POOR.label,
                newLabel = newRating.label,
                cloudCoverPct = avgClouds,
            )
        }

        return Result.success()
    }
}
