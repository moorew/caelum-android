package de.astronarren.allsky.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import de.astronarren.allsky.MainActivity

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID = "allsky_weather_alerts"
        const val CHANNEL_NAME = "Sky Alerts"

        // Distinct IDs so a future re-fire doesn't replace a still-relevant
        // existing alert in the shade.
        const val NOTIFICATION_ID_NIGHT = 1002
        const val NOTIFICATION_ID_IMPROVED = 1003

        /**
         * Deep-link extra read by MainActivity. Value is the canonical
         * module name (matches the layout entries in UserPreferences), e.g.
         * "BEST_VIEWING".
         */
        const val EXTRA_SCROLL_TO = "extra_scroll_to"
        const val SCROLL_TARGET_BEST_VIEWING = "BEST_VIEWING"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Heads-up when tonight's viewing conditions improve."
            }
            val manager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Build a PendingIntent that opens MainActivity and, via the EXTRA_SCROLL_TO
     * extra, asks MainScreen to scroll the given card into view. Uses
     * SINGLE_TOP + CLEAR_TOP so the user lands on the running instance
     * (intent surfaces via onNewIntent) rather than a fresh task.
     */
    private fun deepLinkTo(scrollTarget: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SCROLL_TO, scrollTarget)
        }
        return PendingIntent.getActivity(
            context,
            scrollTarget.hashCode(), // distinct requestCode per target so extras aren't merged
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Primary "tonight just got better" alert. Fires once per day when
     * the WeatherWorker detects the rating jumped from POOR/FAIR up to
     * GOOD/EXCELLENT.
     */
    fun showSkyRatingImprovedNotification(
        previousLabel: String,
        newLabel: String,
        cloudCoverPct: Int,
    ) {
        if (!canPost()) return

        val title = "Tonight just got better"
        val body = "$previousLabel → $newLabel — cloud cover down to $cloudCoverPct%. Tap for tonight's window."

        val pending = deepLinkTo(SCROLL_TARGET_BEST_VIEWING)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(de.astronarren.allsky.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val manager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_IMPROVED, notif)
    }

    /**
     * Legacy night-conditions notification, retained so the existing
     * once-per-day "Tonight's Viewing Conditions" trigger still has a path
     * if a future caller wants it. The new improvement alert above is the
     * recommended entry point and is what's wired to the drawer toggle.
     */
    fun showNightConditionsNotification(cloudCover: Int, minTemp: Double) {
        if (!canPost()) return

        val condition = when {
            cloudCover < 20 -> "Excellent"
            cloudCover < 50 -> "Fair"
            else -> "Poor"
        }

        val pending = deepLinkTo(SCROLL_TARGET_BEST_VIEWING)
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(de.astronarren.allsky.R.drawable.ic_notification)
            .setContentTitle("Tonight's Viewing Conditions: $condition")
            .setContentText("Forecasted cloud cover: $cloudCover%. Min temp: ${Math.round(minTemp)}°C.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val manager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_NIGHT, notif)
    }
}
