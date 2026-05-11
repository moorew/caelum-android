package de.astronarren.allsky.data

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Free, key-less geocoder provided by Open-Meteo. We use it instead of
 * Google's Places SDK or Mapbox so the app stays light (no extra SDK, no API
 * key in CI secrets) and pure-HTTP — calls are made through the same OkHttp
 * stack as the weather endpoint.
 *
 * Docs: https://open-meteo.com/en/docs/geocoding-api
 */
interface GeocodingService {

    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 8,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
    ): GeocodingResponse
}

data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

data class GeocodingResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    val admin1: String? = null,
    val admin2: String? = null,
    val timezone: String? = null,
    val population: Long? = null,
) {
    /** Human-readable label: "Edinburgh · Scotland, United Kingdom". */
    val label: String
        get() {
            val region = listOfNotNull(admin1, country)
                .filter { it.isNotBlank() }
                .joinToString(", ")
            return if (region.isNotBlank()) "$name · $region" else name
        }
}
