package de.astronarren.allsky.data.astro

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * CelesTrak general-perturbations element-set distribution.
 *
 * We hit the `GROUP=visual` slice — Dr T S Kelso's curated list of
 * satellites bright enough (and large enough) to see naked-eye during
 * favourable passes. ~150 objects, including the ISS, the brightest
 * Starlinks, and the active rocket bodies that produce visible flares.
 *
 * Response is a TLE text file: a header line, then 2 orbital-element lines,
 * repeating. We ask for FORMAT=tle and parse the triples in
 * [SatelliteRepository].
 *
 * No API key, no rate limits documented, but TLE files are cached on a CDN
 * and update every ~8 hours. We cache locally for 24 h — the element sets
 * stay accurate for at least a week, so daily fetches are overkill but
 * polite.
 */
interface SatelliteService {
    @GET("NORAD/elements/gp.php")
    suspend fun getTleGroup(
        @Query("GROUP") group: String = "visual",
        @Query("FORMAT") format: String = "tle",
    ): String

    companion object {
        const val BASE_URL = "https://celestrak.org/"

        fun create(): SatelliteService = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(SatelliteService::class.java)
    }
}
