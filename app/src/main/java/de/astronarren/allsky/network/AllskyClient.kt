package de.astronarren.allsky.network

import android.content.Context
import android.content.SharedPreferences
import de.astronarren.allsky.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP client for the Allsky WebUI.
 *
 * Allsky v2026.x replaced HTTP Basic Auth with a PHP session login form
 * (CSRF token + `PHPSESSID` cookie, plus an optional 30-day rotating
 * `allsky_remember` cookie). This client:
 *
 *  - keeps a cookie jar persisted to SharedPreferences, so the session and the
 *    remember-me token survive app restarts and we rarely need to POST the
 *    password at all;
 *  - detects "login required" (a redirect to `index.php?page=login`, or the
 *    JSON `{"redirect": ".../page=login"}` that AJAX endpoints return), logs in
 *    once and retries the request;
 *  - always sends `remember_login=1`. Logging in *without* it makes Allsky
 *    revoke every remember-me token for the user, which would sign the user's
 *    own browser out each time the app connected;
 *  - still sends a Basic `Authorization` header when credentials are set, so
 *    older (≤ v2024.12) installs and reverse proxies using Basic Auth keep
 *    working unchanged.
 */
class AllskyClient(
    private val cookieJar: ClearableCookieJar,
    private val configProvider: suspend () -> Config,
) {
    class LoginRequiredException(message: String) : IOException(message)

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        // list_images on a night with ~12k frames takes 10 s+ on a Pi.
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val loginMutex = Mutex()

    /** Last failed login (time + config fingerprint) — avoids tripping Allsky's login throttle. */
    @Volatile private var lastLoginFailure: Pair<Long, Int>? = null

    data class Config(val baseUrl: String, val username: String, val password: String) {
        val basicAuth: String?
            get() = if (username.isEmpty() && password.isEmpty()) null
            else "Basic " + java.util.Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        val hasCredentials: Boolean get() = username.isNotEmpty() && password.isNotEmpty()
    }

    suspend fun config(): Config = configProvider()

    /**
     * Allsky's WebUI always lives at the web root, but users often save the
     * `/allsky` Website URL instead. Strip that so WebUI paths resolve.
     */
    fun webUiRoot(baseUrl: String): String = stripWebsitePath(baseUrl)

    /**
     * GET [url] and return the body, logging in and retrying once if Allsky
     * bounces us to its login page. Returns null on 404 so callers can fall
     * back to legacy scraping on older Allsky versions.
     */
    suspend fun getText(url: String, ajax: Boolean = false): String? = withContext(Dispatchers.IO) {
        val cfg = config()
        var result = fetch(url, ajax, cfg)
        if (result is Fetch.NeedsLogin) {
            login(cfg)
            result = fetch(url, ajax, cfg)
        }
        when (result) {
            is Fetch.Ok -> result.body
            is Fetch.NotFound -> null
            is Fetch.NeedsLogin -> throw LoginRequiredException(
                "Allsky login failed — check the WebUI username and password in Settings."
            )
        }
    }

    private sealed class Fetch {
        class Ok(val body: String) : Fetch()
        object NotFound : Fetch()
        object NeedsLogin : Fetch()
    }

    private fun fetch(url: String, ajax: Boolean, cfg: Config): Fetch {
        val builder = Request.Builder().url(url)
        cfg.basicAuth?.let { builder.header("Authorization", it) }
        if (ajax) {
            builder.header("X-Requested-With", "XMLHttpRequest")
            builder.header("Accept", "text/html")
        }
        httpClient.newCall(builder.build()).execute().use { resp ->
            if (isLoginPage(resp)) return Fetch.NeedsLogin
            if (resp.code == 401 || resp.code == 403) {
                throw IOException("Authentication required (HTTP ${resp.code}). Check your username and password.")
            }
            if (resp.code == 404) return Fetch.NotFound
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} from Allsky")
            val body = resp.body?.string().orEmpty()
            if (ajax && body.length < 300 && body.contains("\"redirect\"") && body.contains("page=login")) {
                return Fetch.NeedsLogin
            }
            return Fetch.Ok(body)
        }
    }

    private fun isLoginPage(resp: Response): Boolean =
        resp.request.url.queryParameter("page") == "login"

    private suspend fun login(cfg: Config) = loginMutex.withLock {
        // Another request may have just logged in while we waited for the lock.
        val probeUrl = "${webUiRoot(cfg.baseUrl)}/index.php?page=login"
        if (!cfg.hasCredentials) {
            throw LoginRequiredException(
                "Your Allsky WebUI requires a login. Add its username and password in Settings."
            )
        }
        val fingerprint = cfg.hashCode()
        lastLoginFailure?.let { (at, fp) ->
            if (fp == fingerprint && System.currentTimeMillis() - at < LOGIN_BACKOFF_MS) {
                throw LoginRequiredException(
                    "Allsky login failed — check the WebUI username and password in Settings."
                )
            }
        }

        val loginPage = httpClient.newCall(Request.Builder().url(probeUrl).build()).execute().use { resp ->
            // Remember-me cookie may have silently restored the session, in
            // which case Allsky redirects us away from the login page.
            if (!isLoginPage(resp)) return@withLock
            resp.body?.string().orEmpty()
        }
        val csrf = CSRF_REGEX.find(loginPage)?.groupValues?.get(1).orEmpty()

        val form = FormBody.Builder()
            .add("username", cfg.username)
            .add("password", cfg.password)
            .add("csrf_token", csrf)
            .add("remember_login", "1")
            .build()
        val ok = httpClient.newCall(Request.Builder().url(probeUrl).post(form).build()).execute().use { resp ->
            !isLoginPage(resp) && resp.isSuccessful
        }
        if (!ok) {
            lastLoginFailure = System.currentTimeMillis() to fingerprint
            throw LoginRequiredException(
                "Allsky login failed — check the WebUI username and password in Settings."
            )
        }
        lastLoginFailure = null
    }

    /** Forget the session, e.g. after the user changes URL or credentials. */
    fun clearSession() {
        cookieJar.clear()
        lastLoginFailure = null
    }

    companion object {
        /** Production instance: cookies in SharedPreferences, config from DataStore. */
        fun create(context: Context, userPreferences: UserPreferences): AllskyClient {
            val jar = PersistentCookieJar(
                context.applicationContext.getSharedPreferences("allsky_cookies", Context.MODE_PRIVATE)
            )
            return AllskyClient(jar) {
                val raw = userPreferences.getAllskyUrl().trim().trimEnd('/')
                val (clean, urlAuth) = AllskyAuth.extractAuth(raw)
                var user = userPreferences.getUsername()
                var pass = userPreferences.getPassword()
                if (urlAuth != null && user.isEmpty()) {
                    // Credentials embedded in the URL (legacy config).
                    val decoded = String(
                        android.util.Base64.decode(urlAuth.removePrefix("Basic "), android.util.Base64.NO_WRAP)
                    )
                    user = decoded.substringBefore(':')
                    pass = decoded.substringAfter(':', "")
                }
                Config(clean.trimEnd('/'), user, pass)
            }
        }

        /**
         * `http://pi/allsky` → `http://pi`. Parses the URL so a *host* called
         * "allsky" (`http://allsky`) is left alone — a plain endsWith check
         * turned that into `http:/`.
         */
        fun stripWebsitePath(baseUrl: String): String {
            val url = baseUrl.trimEnd('/').toHttpUrlOrNull() ?: return baseUrl.trimEnd('/')
            val segs = url.pathSegments.filter { it.isNotEmpty() }
            if (segs.lastOrNull()?.equals("allsky", ignoreCase = true) != true) return baseUrl.trimEnd('/')
            return url.newBuilder().apply {
                // Remove all segments, then re-add all but the last.
                for (i in url.pathSegments.indices.reversed()) removePathSegment(i)
                segs.dropLast(1).forEach { addPathSegment(it) }
            }.build().toString().trimEnd('/')
        }

        private val CSRF_REGEX = Regex("""name=["']csrf_token["']\s+value=["']([^"']+)["']""")
        private const val LOGIN_BACKOFF_MS = 60_000L
    }
}

interface ClearableCookieJar : CookieJar {
    fun clear()
}

/** Cookie jar for tests / ephemeral use. */
class InMemoryCookieJar : ClearableCookieJar {
    private val cookies = mutableListOf<Cookie>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) {
            this.cookies.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            this.cookies += c
        }
    }
    @Synchronized override fun loadForRequest(url: HttpUrl) = cookies.filter { it.matches(url) }
    @Synchronized override fun clear() = cookies.clear()
}

/**
 * Minimal persistent cookie jar. Allsky only sets a handful of cookies
 * (PHPSESSID, allsky_remember, allsky_remember_username), so a flat JSON blob
 * in SharedPreferences is plenty.
 */
private class PersistentCookieJar(private val prefs: SharedPreferences) : ClearableCookieJar {
    private val cookies = mutableListOf<Cookie>()

    init {
        runCatching {
            val arr = JSONArray(prefs.getString(KEY, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.getString("url").toHttpUrlOrNull() ?: continue
                Cookie.parse(url, o.getString("cookie"))?.let { cookies += it }
            }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        for (c in cookies) {
            this.cookies.removeAll { it.name == c.name && it.domain == c.domain && it.path == c.path }
            if (c.expiresAt > System.currentTimeMillis()) this.cookies += c
        }
        persist(url)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        this.cookies.removeAll { it.expiresAt <= now }
        return this.cookies.filter { it.matches(url) }
    }

    @Synchronized
    override fun clear() {
        cookies.clear()
        prefs.edit().remove(KEY).apply()
    }

    private fun persist(url: HttpUrl) {
        val arr = JSONArray()
        for (c in cookies) {
            val origin = "${if (c.secure) "https" else url.scheme}://${c.domain}${c.path}"
            arr.put(JSONObject().put("url", origin).put("cookie", c.toString()))
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object { const val KEY = "cookies" }
}
