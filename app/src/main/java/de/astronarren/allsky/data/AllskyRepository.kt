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
                    // Reconstruct authority with encoded credentials
                    val authority = "${android.net.Uri.encode(username)}:${android.net.Uri.encode(password)}@${uri.authority}"
                    builder.encodedAuthority(authority).build().toString()
                } else {
                    baseUrl
                }

                println("Debug: Fetching content from Allsky: $jsoupBaseUrl")
                
                suspend fun fetchDoc(path: String, portalPage: String): org.jsoup.nodes.Document? {
                    var doc: org.jsoup.nodes.Document? = null
                    try {
                        val portalUrl = "$jsoupBaseUrl/index.php?page=$portalPage"
                        doc = createConnection(portalUrl).get()
                    } catch (e: Exception) {
                        println("Debug: Portal page ($portalPage) fetch failed: ${e.message}")
                    }

                    // Heuristic: check if the document has actual links to media or portal list markers
                    val hasPortalContent = doc != null && (
                        doc.select("a[href*=.mp4], a[href*=.jpg], img[src*=.jpg], source[src*=.mp4]").isNotEmpty() ||
                        doc.select("div.functionsListFileType, div.functionsListTypeImg").isNotEmpty()
                    )

                    if (doc != null && hasPortalContent) {
                        return doc
                    }

                    return try {
                        val dirUrl = "$jsoupBaseUrl/$path"
                        println("Debug: Falling back to direct directory: $dirUrl")
                        createConnection(dirUrl).get()
                    } catch (e: Exception) {
                        doc // Return the portal doc even if it didn't have obvious content, as a last resort
                    }
                }

                val dayParam = date ?: "All"
                
                // Parallel fetching with supervisorScope so one failure doesn't kill others
                val content = supervisorScope {
                    val timelapseDef = async { fetchDoc("videos/", "list_videos&day=$dayParam") }
                    val keogramDef = async { fetchDoc("keograms/", "list_keograms&day=$dayParam") }
                    val startrailDef = async { fetchDoc("startrails/", "list_startrails&day=$dayParam") }
                    val meteorDef = async { fetchDoc("meteors/", "list_meteors&day=$dayParam") }
                    val imagesDef = async { 
                        if (date != null && date != "All") fetchDoc("images/", "list_images&day=$date") 
                        else fetchDoc("images/", "list_days") 
                    }

                    val timelapses = timelapseDef.await()?.let { parseTimelapses(it, authBaseUrl) } ?: emptyList()
                    val keograms = keogramDef.await()?.let { parseKeograms(it, authBaseUrl) } ?: emptyList()
                    val startrails = startrailDef.await()?.let { parseStartrails(it, authBaseUrl) } ?: emptyList()
                    val meteors = meteorDef.await()?.let { parseMeteors(it, authBaseUrl) } ?: emptyList()
                    var images = imagesDef.await()?.let { parseImages(it, authBaseUrl) } ?: emptyList()

                    // Nested day logic for images
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
                            val dailyImages = parseImages(dayDoc, authBaseUrl)
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

    private fun parseImages(doc: org.jsoup.nodes.Document, baseUrl: String): List<AllskyMedia> {
        val elements = doc.select("a[href], img[src]")
        // Day-list navigation links (e.g. `page=list_images&day=…`, or trailing
        // slash directory links). These are kept *only* so the caller can drill
        // into the latest day; they are filtered out of the final image list.
        val dayLinks = mutableListOf<AllskyMedia>()
        val realImages = elements.mapNotNull { element ->
            try {
                var rawHref = element.attr("href").ifEmpty { element.attr("src") }.trim()
                if (rawHref.contains("?") && !rawHref.contains("page=list_")) return@mapNotNull null
                if (rawHref.startsWith("..") || rawHref.contains("delete") || rawHref.contains("edit")) return@mapNotNull null

                rawHref = rawHref.replace("thumbnails/", "")
                val url = normalizeUrl(rawHref, baseUrl, "images") ?: return@mapNotNull null

                val lowerUrl = url.lowercase()
                val lowerFileName = url.substringAfterLast("/").substringBefore("?").lowercase()

                val isDayLink = lowerUrl.contains("page=list_images") || lowerUrl.endsWith("/")
                if (isDayLink) {
                    dayLinks.add(AllskyMedia(date = extractDate(rawHref, element), url = url))
                    return@mapNotNull null
                }

                val isImage = (lowerUrl.contains(".jpg") || lowerUrl.contains(".png") || lowerUrl.contains(".jpeg")) &&
                    !lowerFileName.contains("keogram") &&
                    !lowerFileName.contains("startrail") &&
                    !lowerFileName.contains("image.jpg") &&
                    !lowerFileName.contains("logo")

                if (isImage) {
                    AllskyMedia(date = extractDate(rawHref, element), url = url)
                } else null
            } catch (e: Exception) {
                null
            }
        }
        // If the portal listed days rather than images, surface the day links
        // so the caller's nested-day fetch can follow into the most recent.
        val combined = if (realImages.isEmpty() && dayLinks.isNotEmpty()) dayLinks else realImages
        return combined.sortedByDescending { it.date }.distinctBy { it.url }.take(40)
    }

    private fun parseTimelapses(doc: org.jsoup.nodes.Document, baseUrl: String): List<AllskyMedia> {
        val elements = doc.select("a[href], source[src], video[src]")
        return elements.mapNotNull { element ->
            try {
                val rawHref = element.attr("href").ifEmpty { element.attr("src") }.trim()
                if (rawHref.contains("?") && !rawHref.contains("page=list_")) return@mapNotNull null
                if (rawHref.startsWith("..") || rawHref.contains("delete") || rawHref.contains("edit")) return@mapNotNull null

                val url = normalizeUrl(rawHref, baseUrl, "videos") ?: return@mapNotNull null
                val lowerUrl = url.lowercase()
                val lowerFileName = url.substringAfterLast("/").substringBefore("?").lowercase()

                val isVideo = lowerUrl.contains(".mp4") || lowerUrl.contains(".webm") || 
                              lowerUrl.contains(".mkv") || lowerUrl.contains(".mov") ||
                              lowerUrl.contains(".avi")

                if (isVideo && !lowerFileName.contains("allsky-logo")) {
                    AllskyMedia(
                        date = extractDate(rawHref, element),
                        url = url
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.date }.distinctBy { it.url }.take(20)
    }

    private fun parseKeograms(doc: org.jsoup.nodes.Document, baseUrl: String): List<AllskyMedia> {
        val elements = doc.select("a[href], img[src]")
        return elements.mapNotNull { element ->
            try {
                val rawHref = element.attr("href").ifEmpty { element.attr("src") }.trim()
                if (rawHref.contains("?") && !rawHref.contains("page=list_")) return@mapNotNull null
                if (rawHref.startsWith("..") || rawHref.contains("delete") || rawHref.contains("edit")) return@mapNotNull null
                
                val url = normalizeUrl(rawHref, baseUrl, "keograms") ?: return@mapNotNull null
                val lowerUrl = url.lowercase()
                val lowerFileName = url.substringAfterLast("/").substringBefore("?").lowercase()

                val isImage = lowerUrl.contains(".jpg") || lowerUrl.contains(".png") || lowerUrl.contains(".jpeg")
                val isExcluded = lowerFileName.contains("allsky-logo") || 
                                 lowerFileName.contains("image.jpg") || 
                                 lowerFileName.contains("image.png") ||
                                 lowerFileName.contains("image-resize") ||
                                 lowerFileName.contains("placeholder") || 
                                 lowerFileName.contains("default") ||
                                 lowerFileName.contains("logo")

                if (isImage && !isExcluded && (lowerUrl.contains("keogram") || lowerUrl.contains("keo"))) {
                    AllskyMedia(
                        date = extractDate(rawHref, element),
                        url = url
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.date }.distinctBy { it.url }.take(20)
    }

    private fun parseStartrails(doc: org.jsoup.nodes.Document, baseUrl: String): List<AllskyMedia> {
        val elements = doc.select("a[href], img[src]")
        return elements.mapNotNull { element ->
            try {
                val rawHref = element.attr("href").ifEmpty { element.attr("src") }.trim()
                if (rawHref.contains("?") && !rawHref.contains("page=list_")) return@mapNotNull null
                if (rawHref.startsWith("..") || rawHref.contains("delete") || rawHref.contains("edit")) return@mapNotNull null
                
                val url = normalizeUrl(rawHref, baseUrl, "startrails") ?: return@mapNotNull null
                val lowerUrl = url.lowercase()
                val lowerFileName = url.substringAfterLast("/").substringBefore("?").lowercase()
                
                val isImage = lowerUrl.contains(".jpg") || lowerUrl.contains(".png") || lowerUrl.contains(".jpeg")
                val isExcluded = lowerFileName.contains("allsky-logo") || 
                                 lowerFileName.contains("image.jpg") || 
                                 lowerFileName.contains("image.png") ||
                                 lowerFileName.contains("image-resize") ||
                                 lowerFileName.contains("placeholder") || 
                                 lowerFileName.contains("default") ||
                                 lowerFileName.contains("logo")

                if (isImage && !isExcluded && (lowerUrl.contains("startrail") || lowerUrl.contains("trail"))) {
                    AllskyMedia(
                        date = extractDate(rawHref, element),
                        url = url
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.date }.distinctBy { it.url }.take(20)
    }

    private fun parseMeteors(doc: org.jsoup.nodes.Document, baseUrl: String): List<AllskyMedia> {
        val elements = doc.select("a[href], img[src], source[src]")
        return elements.mapNotNull { element ->
            try {
                val rawHref = element.attr("href").ifEmpty { element.attr("src") }.trim()
                if (rawHref.contains("?") && !rawHref.contains("page=list_")) return@mapNotNull null
                if (rawHref.startsWith("..") || rawHref.contains("delete") || rawHref.contains("edit")) return@mapNotNull null
                
                val url = normalizeUrl(rawHref, baseUrl, "meteors") ?: return@mapNotNull null
                val lowerUrl = url.lowercase()
                val lowerFileName = url.substringAfterLast("/").substringBefore("?").lowercase()
                
                val isExcluded = lowerFileName.contains("allsky-logo") || 
                                 lowerFileName.contains("image.jpg") || 
                                 lowerFileName.contains("image.png") ||
                                 lowerFileName.contains("image-resize") ||
                                 lowerFileName.contains("placeholder") || 
                                 lowerFileName.contains("default") ||
                                 lowerFileName.contains("logo")
                
                val isMedia = lowerUrl.contains(".jpg") || lowerUrl.contains(".png") || lowerUrl.contains(".jpeg") || 
                              lowerUrl.contains(".mp4") || lowerUrl.contains(".webm")
                
                if (isMedia && !isExcluded) {
                    AllskyMedia(
                        date = extractDate(rawHref, element),
                        url = url
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }.sortedByDescending { it.date }.distinctBy { it.url }.take(20)
    }
}