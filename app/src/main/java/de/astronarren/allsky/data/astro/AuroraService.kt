package de.astronarren.allsky.data.astro

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET

/**
 * NOAA Space Weather Prediction Center — 3-day planetary Kp forecast.
 *
 * The endpoint returns JSON shaped as a CSV-with-headers:
 *
 *   [
 *     ["time_tag", "kp", "observed", "noaa_scale"],
 *     ["2026-05-11 00:00:00", "3.33", "predicted", ""],
 *     ...
 *   ]
 *
 * The header row makes Gson POJO mapping awkward. We bypass that by asking
 * Retrofit for the raw response body as a String and parsing manually in
 * [AuroraRepository]. The scalars converter is already in the dependency
 * list (added for OpenWeather's text endpoints), so no new dep here.
 *
 * Public endpoint, no API key, no rate limits documented — SWPC serves
 * static files behind a CDN. We still cache for an hour to be a polite
 * citizen.
 */
interface AuroraService {
    @GET("products/noaa-planetary-k-index-forecast.json")
    suspend fun getKpForecast(): String

    companion object {
        const val BASE_URL = "https://services.swpc.noaa.gov/"

        fun create(): AuroraService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(AuroraService::class.java)
    }
}
