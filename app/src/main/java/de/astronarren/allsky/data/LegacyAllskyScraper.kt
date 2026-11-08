package de.astronarren.allsky.data

import org.jsoup.Jsoup
import org.jsoup.Connection
import android.util.Base64

/**
 * Pre-2026 Allsky support: scrapes the `index.php?page=list_*` portal pages
 * (HTTP Basic Auth) and raw directory listings. Allsky v2026 loads those
 * lists over AJAX and disables directory listings, so this path is only used
 * when [AllskyRepository] detects an older install.
 */
class LegacyAllskyScraper {

    fun fetchCategory(
        category: MediaCategory,
        baseUrl: String,
        username: String,
        password: String,
        date: String?,
    ): List<AllskyMedia> {
        fun createConnection(url: String): Connection {
            val conn = Jsoup.connect(url)
                .userAgent("Caelum/Android")
                .timeout(15000)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .maxBodySize(0)
            if (username.isNotEmpty() && password.isNotEmpty()) {
                val basicAuth = "Basic " + Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)
                conn.header("Authorization", basicAuth)
            }
            return conn
        }

        fun safeGet(url: String): org.jsoup.nodes.Document? = try {
            val response = createConnection(url).execute()
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw IllegalStateException("Authentication Required (${response.statusCode()}). Please check your Username and Password.")
            }
            if (response.statusCode() >= 400) null else response.parse()
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            null
        }

        val dayParam = date ?: "All"
        val kind = when (category) {
            MediaCategory.TIMELAPSES -> MediaKind.VIDEO
            MediaCategory.KEOGRAMS -> MediaKind.KEOGRAM
            MediaCategory.STARTRAILS -> MediaKind.STARTRAIL
            MediaCategory.METEORS -> MediaKind.METEOR
            MediaCategory.IMAGES -> MediaKind.IMAGE
        }
        val subDir = category.legacyDir.trimEnd('/')
        val portalPage = when {
            category != MediaCategory.IMAGES -> "${category.legacyPortalPage}&day=$dayParam"
            date != null && date != "All" -> "list_images&day=$date"
            else -> "list_days"
        }
        val pair = FetchPair(
            safeGet("$baseUrl/index.php?page=$portalPage"),
            safeGet("$baseUrl/${category.legacyDir}")
        )
        var results = parseFromPair(pair, baseUrl, kind, subDir)

        if (kind == MediaKind.IMAGE) {
            // Some portals only list days; follow the most recent day link.
            val dayLink = results.firstOrNull { it.url.contains("day=") || it.url.endsWith("/") }
            if (dayLink != null && results.all { it.url.contains("page=list_images") || it.url.endsWith("/") }) {
                val fetchUrl = if (dayLink.url.contains("?")) {
                    "$baseUrl/index.php?" + dayLink.url.substringAfter("?")
                } else {
                    dayLink.url
                }
                safeGet(fetchUrl)?.let { dayDoc ->
                    val daily = parseFromPair(FetchPair(dayDoc, null), baseUrl, kind, subDir)
                    if (daily.isNotEmpty()) results = daily
                }
            }
        }
        return results
    }

    private data class FetchPair(
        val portal: org.jsoup.nodes.Document?,
        val directory: org.jsoup.nodes.Document?
    )

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
            .groupBy { it.url }
            .map { (_, dupes) -> dupes.firstOrNull { it.thumbUrl != null } ?: dupes.first() }
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

                val thumbHref = if (rawHref.contains("thumbnail")) rawHref else null
                rawHref = rawHref.replace(Regex("[a-z]*thumbnails?/"), "")

                val url = normalizeUrl(rawHref, baseUrl, subDir) ?: continue
                val thumbUrl = thumbHref?.let { normalizeUrl(it, baseUrl, subDir) }
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
                    results.add(AllskyMedia(extractDate(rawHref, element), url, thumbUrl))
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
