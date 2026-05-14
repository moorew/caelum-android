package de.astronarren.allsky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.astronarren.allsky.data.GeocodingResult
import de.astronarren.allsky.data.GeocodingService
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.data.network.WeatherApiProvider
import de.astronarren.allsky.ui.state.SetupUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupViewModel(
    private val userPreferences: UserPreferences,
    /**
     * Injected so tests (or a future offline mode) can swap in a fake. The
     * default is the real Open-Meteo geocoder.
     */
    private val geocodingService: GeocodingService = WeatherApiProvider.provideGeocodingService(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    /** Cancellable handle for the in-flight geocoding request. */
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            val isComplete = userPreferences.isSetupComplete()
            val url = userPreferences.getAllskyUrl()
            val stationName = userPreferences.getStationName()
            val username = userPreferences.getUsername()
            val password = userPreferences.getPassword()
            val latitude = userPreferences.getLatitude()
            val longitude = userPreferences.getLongitude()

            _uiState.update { state ->
                state.copy(
                    isComplete = isComplete,
                    allskyUrl = url,
                    stationName = stationName,
                    username = username,
                    password = password,
                    latitude = latitude,
                    longitude = longitude
                )
            }
        }
    }

    fun nextStep() {
        _uiState.update { state ->
            state.copy(currentStep = state.currentStep + 1)
        }
    }

    fun updateStationName(name: String) {
        viewModelScope.launch {
            userPreferences.saveStationName(name)
            _uiState.update { state ->
                state.copy(stationName = name)
            }
        }
    }

    fun updateAllskyUrl(url: String) {
        viewModelScope.launch {
            userPreferences.saveAllskyUrl(url)
            _uiState.update { state ->
                state.copy(allskyUrl = url)
            }
        }
    }

    fun updateUsername(username: String) {
        viewModelScope.launch {
            userPreferences.saveUsername(username)
            _uiState.update { state ->
                state.copy(username = username)
            }
        }
    }

    fun updatePassword(password: String) {
        viewModelScope.launch {
            userPreferences.savePassword(password)
            _uiState.update { state ->
                state.copy(password = password)
            }
        }
    }

    fun updateLatitude(latitude: String) {
        viewModelScope.launch {
            userPreferences.saveLatitude(latitude)
            _uiState.update { state ->
                // Editing the lat/long manually invalidates any previously
                // selected place — the chip would otherwise misleadingly
                // claim "Edinburgh" while the coords now point at Reykjavik.
                state.copy(latitude = latitude, selectedPlaceLabel = null)
            }
        }
    }

    fun updateLongitude(longitude: String) {
        viewModelScope.launch {
            userPreferences.saveLongitude(longitude)
            _uiState.update { state ->
                state.copy(longitude = longitude, selectedPlaceLabel = null)
            }
        }
    }

    /**
     * Search-as-you-type: debounce 350 ms after the last keystroke before
     * actually firing the geocoding request. Cancels any in-flight job to
     * keep things snappy.
     */
    fun updateLocationQuery(query: String) {
        _uiState.update { it.copy(locationQuery = query, locationError = null) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(locationResults = emptyList(), locationLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _uiState.update { it.copy(locationLoading = true, locationError = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    geocodingService.search(name = trimmed)
                }
            }
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            locationLoading = false,
                            locationResults = response.results.orEmpty(),
                            locationError = if (response.results.isNullOrEmpty()) "No matches — try a different name" else null
                        )
                    }
                }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    _uiState.update {
                        it.copy(
                            locationLoading = false,
                            locationError = e.message ?: "Search failed"
                        )
                    }
                }
        }
    }

    /** User tapped a result row. Save coords + show a confirmation chip. */
    fun pickGeocodedResult(result: GeocodingResult) {
        viewModelScope.launch {
            userPreferences.saveLatitude(result.latitude.toString())
            userPreferences.saveLongitude(result.longitude.toString())
            _uiState.update { state ->
                state.copy(
                    latitude = result.latitude.toString(),
                    longitude = result.longitude.toString(),
                    selectedPlaceLabel = result.label,
                    locationResults = emptyList(),
                    locationQuery = result.name
                )
            }
        }
    }

    fun completeSetup() {
        viewModelScope.launch {
            userPreferences.markSetupComplete()
            _uiState.update { state ->
                state.copy(isComplete = true)
            }
        }
    }
}
