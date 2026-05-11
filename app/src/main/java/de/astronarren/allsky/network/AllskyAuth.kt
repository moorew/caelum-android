package de.astronarren.allsky.network

import android.util.Base64
import de.astronarren.allsky.data.UserPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Shared auth helpers used by Coil (image loading), ExoPlayer (video playback),
 * DownloadManager and ad-hoc HEAD probes.
 *
 * Most Allsky installs sit behind HTTP Basic Auth on Apache. The codebase used
 * to bake `user:pass@host` into URLs and hand them to Coil/ExoPlayer, but
 * neither library converts URL userinfo into an `Authorization` header — Coil's
 * OkHttp drops it silently, ExoPlayer's HttpDataSource strips it before
 * connecting. The net result was 401s on every protected install.
 *
 * This object centralises the conversion so callers can pass either form.
 */
object AllskyAuth {

    fun basicAuthHeader(user: String, pass: String): String? {
        if (user.isEmpty() && pass.isEmpty()) return null
        return "Basic " + Base64.encodeToString("$user:$pass".toByteArray(), Base64.NO_WRAP)
    }

    /**
     * Returns `url` with any `user:pass@` segment removed, plus the
     * corresponding `Authorization` header value (or null when no userinfo was
     * present).
     */
    fun extractAuth(url: String): Pair<String, String?> {
        return try {
            val uri = android.net.Uri.parse(url)
            val userInfo = uri.userInfo
            if (userInfo.isNullOrEmpty()) return url to null
            val cleanAuthority = uri.authority?.substringAfter('@') ?: return url to null
            val cleanUrl = uri.buildUpon().encodedAuthority(cleanAuthority).build().toString()
            val decoded = android.net.Uri.decode(userInfo)
            val (u, p) = if (decoded.contains(':')) {
                decoded.substringBefore(':') to decoded.substringAfter(':')
            } else {
                decoded to ""
            }
            cleanUrl to basicAuthHeader(u, p)
        } catch (e: Exception) {
            url to null
        }
    }
}

/**
 * OkHttp interceptor wired into the app-wide Coil ImageLoader. It rewrites
 * three URL shapes to a clean URL + `Authorization` header:
 *
 *  1. `https://user:pass@host/path` — userinfo embedded in URL by repository
 *  2. `https://host/path` where `host` matches the configured Allsky host —
 *     uses stored credentials from DataStore
 *  3. everything else — passes through unmodified
 *
 * Stored credentials are read once via `runBlocking` on the OkHttp dispatcher
 * thread; DataStore reads are fast (cached after first hit) so this avoids
 * making the entire image pipeline suspend-aware.
 */
class AllskyAuthInterceptor(
    private val userPreferences: UserPreferences
) : Interceptor {

    @Volatile private var cachedHost: String = ""
    @Volatile private var cachedHeader: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url

        // Case 1: userinfo embedded in URL
        val urlUser = url.username
        val urlPass = url.password
        if (urlUser.isNotEmpty() || urlPass.isNotEmpty()) {
            val header = AllskyAuth.basicAuthHeader(urlUser, urlPass)
            val clean = url.newBuilder().username("").password("").build()
            val rebuilt = original.newBuilder().url(clean)
            if (header != null && original.header("Authorization") == null) {
                rebuilt.header("Authorization", header)
            }
            return chain.proceed(rebuilt.build())
        }

        // Case 2: host matches saved Allsky host
        if (original.header("Authorization") == null) {
            val (host, header) = resolveStoredAuth()
            if (header != null && host.isNotEmpty() && url.host.equals(host, ignoreCase = true)) {
                return chain.proceed(original.newBuilder().header("Authorization", header).build())
            }
        }

        return chain.proceed(original)
    }

    private fun resolveStoredAuth(): Pair<String, String?> {
        // Refresh on each call to pick up credential changes in Settings;
        // runBlocking is acceptable because DataStore reads are non-blocking
        // after the initial load and OkHttp dispatches us off the main thread.
        val saved = runBlocking {
            val base = userPreferences.getAllskyUrl()
            val u = userPreferences.getUsername()
            val p = userPreferences.getPassword()
            Triple(base, u, p)
        }
        val (base, user, pass) = saved
        val host = try { android.net.Uri.parse(base).host ?: "" } catch (e: Exception) { "" }
        val header = AllskyAuth.basicAuthHeader(user, pass)
        cachedHost = host
        cachedHeader = header
        return host to header
    }
}
