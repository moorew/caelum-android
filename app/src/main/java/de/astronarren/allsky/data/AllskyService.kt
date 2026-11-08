package de.astronarren.allsky.data

/**
 * The kinds of archived media an Allsky install produces.
 *
 * [dir], [fileName], [formalName] and [type] are the exact parameters the
 * Allsky v2026 WebUI passes to `uiutil.php?request=ListFileTypeContent`
 * (see `ListFileType(...)` calls in Allsky's html/index.php). [legacyDir] and
 * [legacyPortalPage] are what the pre-2026 scraper used.
 */
enum class MediaCategory(
    val key: String,
    val dir: String,
    val fileName: String,
    val formalName: String,
    val type: String,
    val legacyDir: String,
    val legacyPortalPage: String,
) {
    TIMELAPSES("timelapses", "", "allsky", "Timelapse", "video", "videos/", "list_videos"),
    KEOGRAMS("keograms", "keogram/", "keogram", "Keogram", "picture", "keograms/", "list_keograms"),
    STARTRAILS("startrails", "startrails/", "startrails", "Startrails", "picture", "startrails/", "list_startrails"),
    METEORS("meteors", "meteors/", "meteors", "Meteors", "picture", "meteors/", "list_meteors"),
    IMAGES("images", "", "image", "Images", "picture", "images/", "list_images");

    companion object {
        fun fromKey(key: String): MediaCategory? = entries.firstOrNull { it.key == key }
    }
}

data class AllskyMedia(
    /** Display label — `yyyy-MM-dd`, or `yyyy-MM-dd HH:mm:ss` for single frames. */
    val date: String,
    /** Full-resolution image or video URL. */
    val url: String,
    /**
     * Small server-generated thumbnail (typically 5–40 KB vs 1–4 MB for the
     * full image, or hundreds of MB for a timelapse). Null when the server
     * has none — tiles must then show a placeholder, never decode a video.
     */
    val thumbUrl: String? = null,
)
