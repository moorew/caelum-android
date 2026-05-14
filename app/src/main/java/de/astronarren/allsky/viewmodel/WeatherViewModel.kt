package de.astronarren.allsky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.astronarren.allsky.data.WeatherRepository
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.data.WeatherData
import de.astronarren.allsky.ui.state.WeatherUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class WeatherViewModel(
    private val weatherRepository: WeatherRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(WeatherUiState())
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()
    private var updateJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                userPreferences.getApiKeyFlow(),
                userPreferences.getLatitudeFlow(),
                userPreferences.getLongitudeFlow()
            ) { apiKey, lat, lon ->
                Triple(apiKey, lat, lon)
            }
                .distinctUntilChanged()
                .collect { (apiKey, lat, lon) ->
                    if (apiKey.isNotBlank() && lat.isNotBlank() && lon.isNotBlank()) {
                        scheduleUpdate(apiKey, lat, lon)
                    }
                }
        }
    }

    fun updateWeather() {
        viewModelScope.launch {
            val apiKey = userPreferences.getApiKey()
            val lat = userPreferences.getLatitude()
            val lon = userPreferences.getLongitude()
            scheduleUpdate(apiKey, lat, lon)
        }
    }

    private fun scheduleUpdate(apiKey: String, lat: String, lon: String) {
        updateJob?.cancel()
        updateJob = viewModelScope.launch {
            performUpdate(apiKey, lat, lon)
        }
    }

    private suspend fun performUpdate(apiKey: String, latStr: String, lonStr: String) {
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        if (apiKey.isBlank()) {
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    error = "weather_api_required"
                )
            }
            return
        }

        val lat = latStr.toDoubleOrNull()
        val lon = lonStr.toDoubleOrNull()

        if (lat == null || lon == null) {
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    error = "Station coordinates required in Settings"
                )
            }
            return
        }
        
        weatherRepository.getForecast(lat, lon)
            .onSuccess { response ->
                val dailyForecasts = response.list
                    .groupBy { formatDay(it.dt) }
                    .map { it.value.first() }
                
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        weatherData = Pair(response.city, dailyForecasts),
                        fullForecast = response.list
                    )
                }
            }
            .onFailure { error ->
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Unknown error occurred"
                    )
                }
            }
    }

    fun getBestViewingNight(): WeatherData? {
        val data = _uiState.value.fullForecast ?: return null
        // Find forecast points between 21:00 and 05:00
        val nightPoints = data.filter { 
            val hour = SimpleDateFormat("HH", Locale.getDefault()).format(Date(it.dt * 1000)).toInt()
            hour >= 21 || hour <= 5
        }
        
        if (nightPoints.isEmpty()) return null

        // Pick the point with lowest cloud cover and no rain
        return nightPoints.minByOrNull { 
            it.clouds.all + (if (it.weather.any { w -> w.main.contains("Rain", true) || w.main.contains("Snow", true) }) 1000 else 0)
        }
    }

    private fun formatDay(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp * 1000))
    }
} 
