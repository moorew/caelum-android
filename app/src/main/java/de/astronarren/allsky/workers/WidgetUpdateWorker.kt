package de.astronarren.allsky.workers

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Base64
import android.view.View
import android.widget.RemoteViews
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import de.astronarren.allsky.R
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.data.network.WeatherApiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class WidgetUpdateWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val appWidgetId = inputData.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return@withContext Result.failure()

        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.widget_allsky)
        val userPrefs = UserPreferences(context)

        try {
            // Initializing view to "Refreshing" state
            views.setTextViewText(R.id.widget_last_update, context.getString(R.string.widget_refreshing))
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)

            // 1. Weather Update
            updateWeather(userPrefs, views)
            appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)

            // 2. Image Update
            val bitmap = fetchAllskyImage(context, userPrefs)
            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_image, bitmap)
                views.setTextViewText(
                    R.id.widget_last_update,
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
            } else {
                views.setTextViewText(R.id.widget_last_update, context.getString(R.string.widget_error))
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            views.setTextViewText(R.id.widget_last_update, context.getString(R.string.widget_error))
            appWidgetManager.updateAppWidget(appWidgetId, views)
            Result.retry()
        }
    }

    private suspend fun updateWeather(userPrefs: UserPreferences, views: RemoteViews) {
        try {
            val apiKey = userPrefs.getApiKey()
            val lat = userPrefs.getLatitude().toDoubleOrNull()
            val lon = userPrefs.getLongitude().toDoubleOrNull()

            if (lat != null && lon != null && apiKey.isNotBlank()) {
                val weatherService = WeatherApiProvider.provideWeatherService()
                val response = weatherService.getForecast(lat = lat, lon = lon, apiKey = apiKey)

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val displaySdf = SimpleDateFormat("EEE", Locale.getDefault())

                val dailyForecasts = response.list
                    .groupBy { sdf.format(Date(it.dt * 1000)) }
                    .map { it.value.first() }
                    .take(3)

                if (dailyForecasts.size == 3) {
                    views.setViewVisibility(R.id.widget_weather_container, View.VISIBLE)
                    views.setTextViewText(R.id.widget_weather_day1_date, displaySdf.format(Date(dailyForecasts[0].dt * 1000)).uppercase())
                    views.setTextViewText(R.id.widget_weather_day1_desc, "${Math.round(dailyForecasts[0].main.temp)}°C | ${dailyForecasts[0].clouds.all}%")
                    views.setTextViewText(R.id.widget_weather_day2_date, displaySdf.format(Date(dailyForecasts[1].dt * 1000)).uppercase())
                    views.setTextViewText(R.id.widget_weather_day2_desc, "${Math.round(dailyForecasts[1].main.temp)}°C | ${dailyForecasts[1].clouds.all}%")
                    views.setTextViewText(R.id.widget_weather_day3_date, displaySdf.format(Date(dailyForecasts[2].dt * 1000)).uppercase())
                    views.setTextViewText(R.id.widget_weather_day3_desc, "${Math.round(dailyForecasts[2].main.temp)}°C | ${dailyForecasts[2].clouds.all}%")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Weather is optional
        }
    }

    private suspend fun fetchAllskyImage(context: Context, userPrefs: UserPreferences): Bitmap? {
        var allskyUrl = userPrefs.getAllskyUrl()
        val username = userPrefs.getUsername()
        val password = userPrefs.getPassword()

        if (allskyUrl.isEmpty()) return null

        allskyUrl = allskyUrl.trimEnd('/')
        
        // Find best path
        val rootUrl = if (allskyUrl.endsWith("/allsky")) allskyUrl.substring(0, allskyUrl.length - 7) else allskyUrl
        val testPaths = listOf(
            "$rootUrl/current/tmp/image.jpg",
            "$allskyUrl/image.jpg",
            "$allskyUrl/allsky/image.jpg"
        )

        var finalUrl: String? = null
        for (path in testPaths) {
            if (testPath(path, username, password)) {
                finalUrl = path
                break
            }
        }
        
        if (finalUrl == null) finalUrl = "$allskyUrl/image.jpg"
        
        // Add timestamp to prevent caching old image
        val requestUrl = "$finalUrl?t=${System.currentTimeMillis()}"

        val imageLoader = Coil.imageLoader(context)
        val requestBuilder = ImageRequest.Builder(context)
            .data(requestUrl)
            .allowHardware(false) // CRITICAL for RemoteViews bitmaps
            .size(512, 384) // Keep binder payload below RemoteViews transaction limits.
            
        if (username.isNotEmpty() && password.isNotEmpty()) {
            val authHeader = "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
            requestBuilder.setHeader("Authorization", authHeader)
        }

        val request = requestBuilder.build()
        val result = imageLoader.execute(request)
        
        return (result as? SuccessResult)
            ?.drawable
            ?.let { it as? BitmapDrawable }
            ?.bitmap
    }

    private fun testPath(path: String, user: String, pass: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(path)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            if (user.isNotEmpty() && pass.isNotEmpty()) {
                val basicAuth = "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
                conn.setRequestProperty("Authorization", basicAuth)
            }
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val code = conn.responseCode
            code == 200
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }
}
