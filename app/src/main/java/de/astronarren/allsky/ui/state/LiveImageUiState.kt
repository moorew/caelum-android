package de.astronarren.allsky.ui.state

data class LiveImageUiState(
    /** Resolved, cache-busted live image URL — null until the first probe completes. */
    val imageUrl: String? = null,
    /** Stable URL without the `?t=` cache buster, used as the key for crossfades. */
    val streamKey: String? = null,
    val lastUpdate: Long = 0L,
    val error: String? = null
)
