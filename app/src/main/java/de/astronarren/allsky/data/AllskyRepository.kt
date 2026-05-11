package de.astronarren.allsky.data

import org.jsoup.Jsoup
import org.jsoup.Connection
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*
import android.util.Base64

class AllskyRepository(
    private val userPreferences: UserPreferences
) {
    /**
     * Each media kind is parsed from one of these documents — we always try
     * the portal `index.php?page=list_*` page first, then fall back to a
     * direct directory listing if the portal page didn't yield any usable
     * media. Both attempts are kept so the parser can union the results — on
     * some Allsky installs the portal page only lists thumbnails for a single
     * day while the directory listing has the full archive.
     */
    private data class FetchPair(
        val portal: org.jsoup.nodes.Document?,
        val directory: org.jsoup.nodes.Document?
    )

    suspend fun getAllContent(date: String? = null): AllskyContent {
        return withContext(Dispatchers.IO) {
            try {
                val baseUrlRaw = userPreferences.getAllskyUrl()
                val baseUrl = baseUrlRaw.trim().trimEnd('/')

                if (baseUrl.isEmpty()) {
                    println("Debug: Allsky URL is empty")
                    return@withContext AllskyContent(emptyList(), emptyList(), emptyList())
                }

                if (!baseUrl.startsWith("http://", ignoreCase = true) &&
                    !baseUrl.startsWith("https://", ignoreCase = true)) {
                    println("Debug: Invalid URL format: $baseUrl")
                    throw IllegalArgumentException("Invalid URL format: URL must start with http:// or https://")
                }

                val username = userPreferences.getUsername()
                val password = userPreferences.getPassword()

                fun createConnection(url: String): Connection {
                    val conn = Jsoup.connect(url)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .timeout(15000)
                        .followRedirects(true)
                        .ignoreHttpErrors(true)

                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        val basicAuth = "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
                        conn.header("Authorization", basicAuth)
                    }
                    return conn
                }

                // Base URL for Jsoup (without credentials to avoid parsing issues)
                val jsoupBaseUrl = baseUrl

                // Base URL with credentials for Coil AsyncImage and ExoPlayer
                val authBaseUrl = if (username.isNotEmpty() && password.isNotEmpty()) {
                    val uri = android.net.Uri.parse(baseUrl)
                    val builder = uri.buildUpon()
                    val authority = "${android.net.Uri.encode(username)}:${android.net.Uri.encode(password)}@${uri.authority}"
                    builder.encodedAuthority(authority).build().toString()
                } else {
                    baseUrl
                }

                println("Debug: Fetching content from Allsky: $jsoupBaseUrl")

                /**
                 * Fetch both the portal listing page and the raw directory
                 * listing in parallel. We keep both — the parsers merge them
                 * — because no single source is reliable across Allsky
                 * versions and proxy configurations.
                 */
                suspend fun fetchPair(path: String, portalPage: String): FetchPair = coroutineScope {
                    val portalDef = async {
                        try {
                            createConnection("$jsoupBaseUrl/index.php?page=$portalPage").get()
                        } catch (e: Exception) {
                            println("Debug: Portal page ($portalPage) fetch failed: ${e.message}")
                            null
                        }
                    }
                    val directoryDef = async {
                        try {
                            createConnection("$jsoupBaseUrl/$path").get()
                        } catch (e: Exception) {
                            println("Debug: Directory ($path) fetch failed: ${e.message}")
                            null
                        }
                    }
                    FetchPair(portalDef.await(), directoryDef.await())
                }

                val dayParam = date ?: "All"

                val content = supervisorScope {
                    val timelapseDef = async { fetchPair("videos/", "list_videos&day=$dayParam") }
                    val keogramDef = async { fetchPair("keograms/", "list_keograms&day=$dayParam") }
                    val startrailDef = async { fetchPair("startrails/", "list_startrails&day=$dayParam") }
                    val meteorDef = async { fetchPair("meteors/", "list_meteors&day=$dayParam") }
                    val imagesDef = async {
                        if (date != null && date != "All") fetchPair("images/", "list_images&day=$date")
                        else fetchPair("images/", "list_days")
                    }

                    val timelapses = parseFromPair(timelapseDef.await(), authBaseUrl, MediaKind.VIDEO, "videos")
                    val keograms = parseFromPair(keogramDef.await(), authBaseUrl, MediaKind.KEOGRAM, "keograms")
                    val startrails = parseFromPair(startrailDef.await(), authBaseUrl, MediaKind.STARTRAIL, "startrails")
                    val meteors = parseFromPair(meteorDef.await(), authBaseUrl, MediaKind.METEOR, "meteors")
                    var images = parseFromPair(imagesDef.await(), authBaseUrl, MediaKind.IMAGE, "images")

                    // Some portals don't expose images directly — only a list
                    // of available days. When that's all we got back, follow
                    // the most recent day link and re-parse.
                    val dayLink = images.firstOrNull { it.url.contains("day=") || it.url.endsWith("/") }
                    if (dayLink != null && (images.isEmpty() || images.all { it.url.contains("page=list_images") || it.url.endsWith("/") })) {
                        try {
                            val fetchUrl = if (dayLink.url.contains("?")) {
                                "$jsoupBaseUrl/index.php?" + dayLink.url.substringAfter("?")
                            } else if (dayLink.url.startsWith("http")) {
                                val uri = android.net.Uri.parse(dayLink.url)
                                uri.buildUpon().encodedAuthority(uri.authority?.substringAfter("@")).build().toString()
                            } else {
                                "$jsoupBaseUrl/${dayLink.url.removePrefix("/")}"
                            }
                            val dayDoc = createConnection(fetchUrl).get()
                            val dailyImages = parseFromPair(
                                FetchPair(dayDoc, null), authBaseUrl, MediaKind.IMAGE, "images"
                            )
                            if (dailyImages.isNotEmpty()) images = dailyImages
                        } catch (e: Exception) {
                            println("Debug: Nested image fetch failed: ${e.message}")
                        }
                    }

                    AllskyContent(
                        timelapses = timelapses,
                        keograms = keograms,
                        startrails = startrails,
                        images = images,
                        meteors = meteors
                    )
                }

                content
            } catch (e: Exception) {
                println("Debug: Error fetching allsky content: ${e.message}")
                if (e is org.jsoup.HttpStatusException) {
                    if (e.statusCode == 401 || e.statusCode == 403) {
                        throw Exception("Authentication Required (401/403). Please check your Username and Password.")
                    }
                }
                throw e
            }
        }
    }

    private enum class MediaKind { VIDEO, IMAGE, KEOGRAM, STARTRAIL, METEOR }

    /**
     * Parses both the portal listing and the directory listing, then unions
     * the results so we don't lose entries that only appear in one of them.
     * Parsing is intentionally permissive — when an Allsky install is hidden
     * behind a custom theme or a stripped-down portal, the inferred
     * directory path becomes the only source of truth.
     */
    private fun parseFromPair(
        pair: FetchPair,
        baseUrl: String,
        kind: MediaKind,
        subDir: String
    ): List<AllskyMedia> {
        val results = mutableListOf<AllskyMedia>()
        pair.portal?.let { results += parseDoc(it, baseUrl, kind, subDir, allowDayLinks = (kind == MediaKind.IMAGE)) }
        pair.directory?.let { results += parseDoc(it, baseUrl, kind, subDir, allowDayLinks = (kind == MediaKind.IMAGE)) }
        return results
            .sortedByDescending { it.date }
            .distinctBy { it.url }
            .take(if (kind == MediaKind.IMAGE) 40 else 20)
    }

    private fun parseDoc(
        doc: org.jsoup.nodes.Document,
        baseUrl: String,
        kind: MediaKind,
        subDir: String,
        allowDayLinks: Boolean
    ): List<AllskyMedia> {
        val elements = doc.select("a[href], img[src], source[src], video[src]")
        val results = mutableListOf<AllskyMedia>()
        val dayLinks = mutableListOf<AllskyMedia>()

        for (element in elements) {
            try {
                var rawHref = element.attr("href").ifEmpty { element.attr("src") }.trim()
                if (rawHref.isEmpty()) continue
                // Query strings are valid on real media URLs (cache busters,
                // signed URLs). Only drop the obvious admin/list-pagination
                // shapes, never the media itself.
                if (rawHref.startsWith("..") || rawHref.contains("delete") || rawHref.contains("edit")) continue
                if (rawHref.contains("javascript:") || rawHref.startsWith("#")) continue

                rawHref = rawHref.replace("thumbnails/", "")

                val url = normalizeUrl(rawHref, baseUrl, subDir) ?: continue
                val lowerUrl = url.lowercase()
                val lowerFileName = url.substringAfterLast("/").substringBefore("?").lowercase()

                val isDayLink = lowerUrl.contains("page=list_images") || lowerUrl.endsWith("/")
                if (isDayLink) {
                    if (allowDayLinks) dayLinks.add(AllskyMedia(extractDate(rawHref, element), url))
                    continue
                }

                if (isJunk(lowerFileName)) continue

                val matched = when (kind) {
                    MediaKind.VIDEO -> isVideoExt(lowerUrl) && !lowerFileName.contains("allsky-logo")
                    MediaKind.IMAGE -> isImageExt(lowerUrl) &&
                        !lowerFileName.contains("keogram") &&
                        !lowerFileName.contains("startrail")
                    // Keograms and startrails are always images. We prefer
                    // ones that include the kind in the URL (the portal does
                    // this), but we still accept anything served from the
                    // category's directory — that catches the “custom
                    // theme strips the filename token” case.
                    MediaKind.KEOGRAM -> isImageExt(lowerUrl) &&
                        (lowerUrl.contains("keogram") || lowerUrl.contains("/keograms/") || lowerUrl.contains("keo"))
                    MediaKind.STARTRAIL -> isImageExt(lowerUrl) &&
                        (lowerUrl.contains("startrail") || lowerUrl.contains("/startrails/") || lowerUrl.contains("trail"))
                    MediaKind.METEOR -> (isImageExt(lowerUrl) || isVideoExt(lowerUrl)) &&
                        (lowerUrl.contains("/meteors/") || lowerUrl.contains("meteor"))
                }

                if (matched) {
                    results.add(AllskyMedia(extractDate(rawHref, element), url))
                }
            } catch (_: Exception) {
                // Best-effort — skip the bad element and keep going.
            }
        }

        return if (results.isEmpty() && allowDayLinks) dayLinks else results
    }

    private fun isVideoExt(lowerUrl: String): Boolean =
        lowerUrl.contains(".mp4") || lowerUrl.contains(".webm") ||
        lowerUrl.contains(".mkv") || lowerUrl.contains(".mov") ||
        lowerUrl.contains(".avi") || lowerUrl.contains(".m3u8")

    private fun isImageExt(lowerUrl: String): Boolean =
        lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") || lowerUrl.contains(".png")

    private fun isJunk(lowerFileName: String): Boolean =
        lowerFileName.contains("allsky-logo") ||
        lowerFileName == "image.jpg" || lowerFileName == "image.png" ||
        lowerFileName.contains("image-resize") ||
        lowerFileName.contains("placeholder") ||
        lowerFileName.contains("default") ||
        lowerFileName.contains("logo")

    private fun extractDate(href: String, element: org.jsoup.nodes.Element): String {
        val specificDate = element.select("div.day-text").text()
        if (specificDate.isNotEmpty()) return specificDate

        val cleanedHref = href.substringAfterLast("/")
        val datePattern = Regex("(\\d{4})[-_]?(\\d{2})[-_]?(\\d{2})")

        val match = datePattern.find(cleanedHref)
        if (match != null) {
            val (year, month, day) = match.destructured
            return "$year-$month-$day"
        }

        val text = element.text().trim()
        val textMatch = datePattern.find(text)
        if (textMatch != null) {
            val (year, month, day) = textMatch.destructured
            return "$year-$month-$day"
        }

        return cleanedHref.substringBeforeLast(".").ifEmpty { cleanedHref }
    }

    private fun normalizeUrl(rawHref: String, baseUrl: String, subDir: String): String? {
        val trimmedHref = rawHref.trim()
        if (trimmedHref.contains("javascript:") || trimmedHref.startsWith("#")) return null

        if (trimmedHref.contains("page=list_")) {
            return if (trimmedHref.startsWith("http")) trimmedHref else "${baseUrl.trimEnd('/')}/${trimmedHref.removePrefix("/")}"
        }

        val lowerHref = trimmedHref.lowercase()
        val isMedia = lowerHref.contains(".jpg") || lowerHref.contains(".png") || lowerHref.contains(".jpeg") ||
                      lowerHref.contains(".mp4") || lowerHref.contains(".webm") ||
                      lowerHref.contains(".mov") || lowerHref.contains(".mkv") ||
                      lowerHref.contains(".m3u8") || lowerHref.contains(".avi") ||
                      trimmedHref.endsWith("/")

        if (!isMedia) return null

        return when {
            trimmedHref.startsWith("http") -> trimmedHref
            trimmedHref.startsWith("/") -> {
                val uri = android.net.Uri.parse(baseUrl)
                val baseScheme = uri.scheme ?: "http"
                val baseAuth = uri.encodedAuthority
                val basePath = uri.path?.trimEnd('/') ?: ""

                if (basePath.isNotEmpty() && trimmedHref.startsWith(basePath)) {
                    "${baseScheme}://${baseAuth}${trimmedHref}"
                } else {
                    "${baseScheme}://${baseAuth}${basePath}/${trimmedHref.removePrefix("/")}"
                }
            }
            else -> {
                val path = trimmedHref.removePrefix("./")
                if (path.startsWith("$subDir/")) {
                    "${baseUrl.trimEnd('/')}/$path"
                } else {
                    "${baseUrl.trimEnd('/')}/$subDir/$path"
                }
            }
        }
    }
}
