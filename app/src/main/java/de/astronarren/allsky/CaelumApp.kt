package de.astronarren.allsky

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import de.astronarren.allsky.data.AllskyRepository
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.network.AllskyAuthInterceptor
import de.astronarren.allsky.network.AllskyClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide singletons. Living here (rather than in MainActivity) means
 * the Allsky session/cookie jar, the HTTP connection pool and Coil's image
 * loader are created once and are also available when only the widget worker
 * runs, without the Activity.
 */
class CaelumApp : Application(), ImageLoaderFactory {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val userPreferences by lazy { UserPreferences(this) }
    val allskyClient by lazy { AllskyClient.create(this, userPreferences) }
    val allskyRepository by lazy { AllskyRepository(cacheDir.resolve("allsky_lists"), allskyClient) }

    override fun newImageLoader(): ImageLoader {
        // Share the Allsky client's connection pool (keep-alive to the Pi),
        // plus the Basic-Auth interceptor for older installs / proxies.
        val okHttp = allskyClient.httpClient.newBuilder()
            .addInterceptor(AllskyAuthInterceptor(userPreferences, appScope))
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            // No VideoFrameDecoder on purpose: for a remote URL Coil would
            // download the *entire* timelapse (hundreds of MB) just to grab
            // one frame. Grids use Allsky's server-side video thumbnails.
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("allsky_image_cache"))
                    .maxSizeBytes(300L * 1024 * 1024)
                    .build()
            }
            // Archive files never change once written (Allsky itself serves
            // them `immutable`); the live image uses a ?t= cache buster.
            .respectCacheHeaders(false)
            .build()
    }

    companion object {
        fun from(context: android.content.Context): CaelumApp =
            context.applicationContext as CaelumApp
    }
}
