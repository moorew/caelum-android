package de.astronarren.allsky.data

import de.astronarren.allsky.network.AllskyClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Lists archived Allsky media (timelapses, keograms, startrails, meteors and
 * the raw frames for a night).
 *
 * Allsky v2026 (current) path — fast:
 *  - `uiutil.php?request=ListFileTypeContent` returns a small HTML fragment
 *    for one category with full + thumbnail URLs (~0.1 s, ~6 KB).
 *  - `index.php?page=list_images&day=…` embeds the night's frames as JSON.
 *    It is slow server-side (the Pi stats every frame — 3–11 s for ~12k
 *    images), so those lists are cached on disk and never re-fetched for
 *    nights that are over.
 *
 * Older Allsky versions fall back to [LegacyAllskyScraper].
 *
 * Every list is cached on disk so screens render instantly from the last
 * known result while a refresh runs in the background.
 */
class AllskyRepository(
    private val cacheDir: File,
    private val client: AllskyClient,
) {
    private val legacy = LegacyAllskyScraper()

    init { cacheDir.mkdirs() }

    /** Null until we know; then whether the v2026 list endpoint exists. */
    @Volatile private var modernApi: Pair<String, Boolean>? = null

    /** Last cached list for [category]/[day], or null if never fetched. */
    suspend fun cached(category: MediaCategory, day: String = ALL): List<AllskyMedia>? =
        withContext(Dispatchers.IO) {
            val base = client.config().baseUrl
            if (base.isEmpty()) return@withContext null
            readCache(cacheFile(base, category, day))
        }

    /** Age of the cached list in ms, or null if there is none. */
    suspend fun cacheAgeMs(category: MediaCategory, day: String = ALL): Long? =
        withContext(Dispatchers.IO) {
            val base = client.config().baseUrl
            if (base.isEmpty()) return@withContext null
            val f = cacheFile(base, category, day)
            if (f.exists()) System.currentTimeMillis() - f.lastModified() else null
        }

    /**
     * True when the cached list for this past night can't change any more, so
     * a network round-trip (potentially 10 s for raw frames) is pointless.
     */
    fun isImmutable(day: String): Boolean {
        val date = parseDay(day) ?: return false
        // A night's folder is named for the evening; its keogram/timelapse are
        // written the following morning. Two days back is safely final.
        return date.isBefore(LocalDate.now().minusDays(1))
    }

    /**
     * Fetch [category] from the server. [day] is `"All"` or `yyyyMMdd`. For
     * [MediaCategory.IMAGES], `"All"` means "the most recent night".
     */
    suspend fun fetch(category: MediaCategory, day: String = ALL): List<AllskyMedia> =
        withContext(Dispatchers.IO) {
            val cfg = client.config()
            val base = cfg.baseUrl
            if (base.isEmpty()) throw IllegalStateException("Allsky URL not configured")
            if (!base.startsWith("http://", true) && !base.startsWith("https://", true)) {
                throw IllegalArgumentException("Invalid URL format: URL must start with http:// or https://")
            }

            val root = client.webUiRoot(base)
            val result = if (useModernApi(root)) {
                if (category == MediaCategory.IMAGES) fetchImagesModern(root, day)
                else fetchListModern(root, category, day)
            } else {
                legacy.fetchCategory(category, base, cfg.username, cfg.password, day.takeIf { it != ALL })
            }
            writeCache(cacheFile(base, category, day), result)
            result
        }

    /** Available night folders, newest first (`yyyyMMdd`). */
    suspend fun fetchDays(): List<String> = withContext(Dispatchers.IO) {
        val root = client.webUiRoot(client.config().baseUrl)
        val html = client.getText("$root/index.php?page=list_days") ?: return@withContext emptyList()
        DAY_LINK.findAll(html).map { it.groupValues[1] }.distinct().sortedDescending().toList()
    }

    // ---- v2026 API ---------------------------------------------------------

    private suspend fun useModernApi(root: String): Boolean {
        modernApi?.let { (r, ok) -> if (r == root) return ok }
        // Cheapest call that exists only on v2026+: meteors list (usually tiny).
        val probe = client.getText(listUrl(root, MediaCategory.METEORS, ALL), ajax = true)
        val ok = probe != null && probe.contains("functions-listfiletype")
        modernApi = root to ok
        return ok
    }

    private fun listUrl(root: String, c: MediaCategory, day: String): String =
        "$root/includes/uiutil.php?request=ListFileTypeContent" +
            "&day=${enc(day)}&dir=${enc(c.dir)}&imageFileName=${enc(c.fileName)}" +
            "&formalImageTypeName=${enc(c.formalName)}&type=${c.type}&listNames=0&useThumbnails=1"

    private suspend fun fetchListModern(root: String, c: MediaCategory, day: String): List<AllskyMedia> {
        val html = client.getText(listUrl(root, c, day), ajax = true)
            ?: throw IllegalStateException("Allsky list endpoint not found")
        val doc = Jsoup.parse(html, "$root/")
        return doc.select("a.functions-listfiletype-item").mapNotNull { a ->
            val full = a.attr("abs:data-download-url").ifEmpty { a.attr("abs:href") }
            if (full.isEmpty() || full.contains("video_player.php")) return@mapNotNull null
            val thumb = a.selectFirst("img")?.attr("src")
                ?.takeIf { it.isNotEmpty() && !it.startsWith("data:") }
                ?.let { a.selectFirst("img")!!.absUrl("src") }
                ?.takeIf { it != full } // no real thumbnail — server echoed the full image
            val dateEl = a.selectFirst(".functions-listfiletype-date")
            val label = dateEl?.attr("data-listfiletype-day")?.takeIf { it.isNotEmpty() }?.let(::formatDay)
                ?: dateEl?.attr("data-listfiletype-date")?.takeIf { it.isNotEmpty() }?.replace('T', ' ')
                ?: DAY_IN_PATH.find(full)?.groupValues?.get(1)?.let(::formatDay)
                ?: full.substringAfterLast('/')
            AllskyMedia(label, full, thumb)
        }.sortedByDescending { it.url }
    }

    private suspend fun fetchImagesModern(root: String, day: String): List<AllskyMedia> {
        val target = if (day == ALL) {
            fetchDays().firstOrNull() ?: return emptyList()
        } else day
        val html = client.getText("$root/index.php?page=list_images&day=${enc(target)}")
            ?: return emptyList()
        val json = IMAGE_ITEMS.find(html)?.groupValues?.get(1) ?: return emptyList()
        val arr = JSONArray(json)
        val out = ArrayList<AllskyMedia>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val name = o.optString("name").takeIf { it.isNotEmpty() } ?: continue
            val ts = o.optString("timestamp").replace('T', ' ')
            val encName = enc(name).replace("+", "%20")
            out += AllskyMedia(
                date = ts.ifEmpty { formatDay(target) },
                url = "$root/images/$target/$encName",
                thumbUrl = "$root/images/$target/thumbnails/$encName",
            )
        }
        out.sortByDescending { it.url }
        return out
    }

    // ---- disk cache --------------------------------------------------------

    private fun cacheFile(base: String, c: MediaCategory, day: String) =
        File(cacheDir, "${Integer.toHexString(base.hashCode())}_${c.key}_$day.json")

    private fun readCache(f: File): List<AllskyMedia>? = try {
        if (!f.exists()) null else {
            val arr = JSONArray(f.readText())
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                AllskyMedia(o.getString("d"), o.getString("u"), o.optString("t").ifEmpty { null })
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun writeCache(f: File, items: List<AllskyMedia>) {
        try {
            val arr = JSONArray()
            for (m in items) arr.put(JSONObject().put("d", m.date).put("u", m.url).put("t", m.thumbUrl ?: ""))
            val tmp = File(f.path + ".tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(f)
        } catch (_: Exception) {
            // Cache is best-effort.
        }
    }

    fun clearCache() {
        modernApi = null
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val ALL = "All"
        private val DAY_LINK = Regex("""page=list_images&(?:amp;)?day=(\d{8})""")
        private val DAY_IN_PATH = Regex("""/(\d{8})/""")
        private val IMAGE_ITEMS = Regex("""const imageItems = (\[.*?]);""", RegexOption.DOT_MATCHES_ALL)
        private val DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd")

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        fun parseDay(day: String): LocalDate? = try {
            LocalDate.parse(day, DAY_FMT)
        } catch (_: Exception) {
            null
        }

        fun formatDay(day: String): String =
            parseDay(day)?.toString() ?: day
    }
}
