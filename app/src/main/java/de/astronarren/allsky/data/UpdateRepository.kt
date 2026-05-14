package de.astronarren.allsky.data

import de.astronarren.allsky.data.network.WeatherApiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

class UpdateRepository(
    private val updateService: UpdateService = WeatherApiProvider.provideUpdateService()
) {

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val release = updateService.getLatestRelease()
                val latestVersion = release.tag_name.removePrefix("v")
                
                if (isNewerVersion(latestVersion, currentVersion)) {
                    val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                    if (apkAsset != null) {
                        UpdateInfo(
                            latestVersion = latestVersion,
                            downloadUrl = apkAsset.browser_download_url,
                            releaseNotes = release.body,
                            apkSize = apkAsset.size
                        )
                    } else null
                } else null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                println("Debug: Update check failed: ${e.message}")
                null
            }
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0..2) {
            val latestPart = latestParts.getOrNull(i) ?: 0
            val currentPart = currentParts.getOrNull(i) ?: 0
            
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }
}

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String,
    val apkSize: Long
)
