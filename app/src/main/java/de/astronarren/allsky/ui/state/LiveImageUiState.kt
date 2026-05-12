package de.astronarren.allsky.ui.state

data class LiveImageUiState(
    /** Resolved, cache-busted live image URL — null until the first probe completes. */
    val imageUrl: String? = null,
    /** Stable URL without the `?t=` cache buster, used as the key for crossfades. */
    val streamKey: String? = null,
    val lastUpdate: Long = 0L,
    val error: String? = null,
    /**
     * Intrinsic dimensions of the most recently loaded JPEG, in pixels.
     * Populated by the [coil.compose.AsyncImage] onSuccess listener in
     * MainScreen so the sky overlay knows how to map fractional fisheye
     * calibration coordinates back to image-pixel space at draw time.
     * Zero before the first successful load.
     */
    val imageWidthPx: Int = 0,
    val imageHeightPx: Int = 0,
)
