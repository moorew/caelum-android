package de.astronarren.allsky.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CancellationException

class WeatherRepository(
    private val weatherService: WeatherService,
    private val userPreferences: UserPreferences
) {
    suspend fun getForecast(lat: Double? = null, lon: Double? = null): Result<WeatherResponse> =
        withContext(Dispatchers.IO) {
            try {
                val apiKey = userPreferences.getApiKey()
                if (apiKey.isEmpty()) {
                    return@withContext Result.failure(Exception("API key not configured"))
                }

                val finalLat: Double
                val finalLon: Double

                if (lat != null && lon != null) {
                    finalLat = lat
                    finalLon = lon
                } else {
                    return@withContext Result.failure(Exception("Station location not set in preferences"))
                }

                val response = weatherService.getForecast(
                    lat = finalLat,
                    lon = finalLon,
                    apiKey = apiKey
                )
                Result.success(response)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
