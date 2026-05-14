package de.astronarren.allsky.data.network

import de.astronarren.allsky.data.GeocodingService
import de.astronarren.allsky.data.UpdateService
import de.astronarren.allsky.data.WeatherService
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object WeatherApiProvider {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    private val weatherService: WeatherService by lazy {
        retrofit("https://api.openweathermap.org/")
            .create(WeatherService::class.java)
    }

    private val geocodingService: GeocodingService by lazy {
        retrofit("https://geocoding-api.open-meteo.com/")
            .create(GeocodingService::class.java)
    }

    private val updateService: UpdateService by lazy {
        retrofit("https://api.github.com/repos/acocalypso/allskyviewer-companion/")
            .create(UpdateService::class.java)
    }

    fun provideWeatherService(): WeatherService = weatherService

    fun provideGeocodingService(): GeocodingService = geocodingService

    fun provideUpdateService(): UpdateService = updateService
}
