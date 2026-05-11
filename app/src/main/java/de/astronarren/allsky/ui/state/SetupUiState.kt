package de.astronarren.allsky.ui.state

import de.astronarren.allsky.data.GeocodingResult

data class SetupUiState(
    val currentStep: Int = 1,
    val allskyUrl: String = "",
    val stationName: String = "",
    val username: String = "",
    val password: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val isComplete: Boolean = false,
    val error: String? = null,

    /**
     * Free-form search query the user has typed into the location step. The
     * actual debounced HTTP request is fired off in SetupViewModel — the UI
     * is otherwise dumb.
     */
    val locationQuery: String = "",
    val locationResults: List<GeocodingResult> = emptyList(),
    val locationLoading: Boolean = false,
    val locationError: String? = null,
    /**
     * When non-null, this is the formatted "Edinburgh · Scotland, UK" string
     * for the user's currently-selected place. We show it as a chip above
     * the search field so they can confirm at a glance that the right
     * coordinates are saved.
     */
    val selectedPlaceLabel: String? = null,
)
