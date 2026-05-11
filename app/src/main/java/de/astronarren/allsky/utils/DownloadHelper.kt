package de.astronarren.allsky.utils

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.network.AllskyAuth

class DownloadHelper(
    private val context: Context,
    private val userPreferences: UserPreferences
) {
    suspend fun downloadMedia(url: String, fileName: String, isVideo: Boolean = false) {
        try {
            // DownloadManager refuses URLs with embedded userinfo on some
            // Android builds — promote any `user:pass@host` to a proper
            // Authorization header before enqueueing.
            val (cleanUrl, urlAuth) = AllskyAuth.extractAuth(url)
            val request = DownloadManager.Request(Uri.parse(cleanUrl))
                .setTitle(fileName)
                .setDescription("Downloading Allsky media...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES,
                    "Allsky/$fileName"
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val auth = urlAuth ?: AllskyAuth.basicAuthHeader(
                userPreferences.getUsername(),
                userPreferences.getPassword()
            )
            if (auth != null) {
                request.addRequestHeader("Authorization", auth)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)

            Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
