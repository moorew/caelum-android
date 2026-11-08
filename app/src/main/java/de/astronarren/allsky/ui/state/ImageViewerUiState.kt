package de.astronarren.allsky.ui.state

data class ImageViewerUiState(
    val isFullScreen: Boolean = false,
    val currentImageUrl: String? = null,
    /** Thumbnail already in Coil's memory cache — shown while the full image loads. */
    val currentThumbUrl: String? = null
) 