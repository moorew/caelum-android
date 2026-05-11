package de.astronarren.allsky.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import de.astronarren.allsky.data.WeatherData
import de.astronarren.allsky.data.City
import java.text.SimpleDateFormat
import java.util.*
import de.astronarren.allsky.ui.state.WeatherUiState
import androidx.compose.ui.res.stringResource
import de.astronarren.allsky.R

@Composable
fun WeatherDisplay(
    modifier: Modifier = Modifier,
    uiState: WeatherUiState
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        when {
            uiState.isLoading && uiState.weatherData == null -> {
                // Only show the full-height spinner on the very first load. On
                // subsequent refreshes we keep the previous data visible to
                // avoid a jarring flicker every 3 hours.
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            uiState.weatherData == null && uiState.error != null -> {
                // Surface the underlying error instead of silently rendering an
                // empty Box. Without this the user has no idea their station
                // coordinates are missing or the API key was rejected.
                WeatherErrorCard(error = uiState.error)
            }

            uiState.weatherData != null -> {
                val (city, forecasts) = uiState.weatherData
                Column {
                    // Location Header
                    Text(
                        text = city.name.uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp
                        ),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                    
                    forecasts.firstOrNull()?.let { current ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 16.dp)
                        ) {
                            Text(
                                text = "${Math.round(current.main.temp)}°",
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 80.sp,
                                    letterSpacing = (-4).sp
                                ),
                                color = Color.White
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))

                            current.weather.firstOrNull()?.let { w ->
                                AsyncImage(
                                    model = "https://openweathermap.org/img/wn/${w.icon}@4x.png",
                                    contentDescription = w.description,
                                    modifier = Modifier.size(100.dp)
                                )
                            }
                            
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text(
                                    text = current.weather.firstOrNull()?.description?.capitalize() ?: "",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = "FEELS LIKE ${Math.round(current.main.feels_like)}°",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // Tonight's Viewing Conditions
                    NightConditionsDisplay(city = city, forecasts = forecasts)

                    // 5-Day Forecast Bento Box
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.05f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(24.dp)
                        ) {
                            // `forecasts` is already daily-deduped by WeatherViewModel
                            // (group-by-day, first entry per day). Slicing by index % 8
                            // was a leftover from when this list held raw 3-hour points
                            // — applied to daily entries it collapses the strip to a
                            // single day, which is why the 5-day forecast appeared
                            // empty.
                            items(forecasts.take(5)) { dayWeather ->
                                DayForecast(dayWeather)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayForecast(weather: WeatherData) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = formatDay(weather.dt).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            ),
            color = Color.White.copy(alpha = 0.4f)
        )
        
        weather.weather.firstOrNull()?.let { w ->
            AsyncImage(
                model = "https://openweathermap.org/img/wn/${w.icon}@2x.png",
                contentDescription = w.description,
                modifier = Modifier.size(40.dp)
            )
        }
        
        Text(
            text = "${Math.round(weather.main.temp)}°",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White
        )
    }
}

@Composable
private fun NightConditionsDisplay(city: City, forecasts: List<WeatherData>) {
    val currentTime = System.currentTimeMillis()
    val sunsetTime = city.sunset * 1000
    val endOfNight = sunsetTime + 12 * 60 * 60 * 1000L
    
    val nightForecasts = forecasts.filter { 
        val dt = it.dt * 1000
        dt in sunsetTime..endOfNight
    }
    
    if (nightForecasts.isNotEmpty()) {
        val avgClouds = nightForecasts.map { it.clouds.all }.average().toInt()
        val minTemp = nightForecasts.minOf { it.main.temp }
        val avgVisibility = nightForecasts.map { it.visibility }.average().toInt() / 1000 // to km
        
        val conditionText = when {
            avgClouds < 20 -> "EXCELLENT"
            avgClouds < 50 -> "FAIR"
            else -> "POOR"
        }
        
        val color = when {
            avgClouds < 20 -> Color(0xFF00E676) // Bright Green
            avgClouds < 50 -> Color(0xFFFFD600) // Bright Amber
            else -> Color(0xFFFF5252) // Bright Red
        }
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.08f)
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TONIGHT'S VIEWING",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        color = color.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = conditionText,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            ),
                            color = color,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("CLOUDS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = Color.White.copy(alpha = 0.4f))
                        Text("$avgClouds%", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = Color.White)
                    }
                    Column {
                        Text("MIN TEMP", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = Color.White.copy(alpha = 0.4f))
                        Text("${Math.round(minTemp)}°", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = Color.White)
                    }
                    Column {
                        Text("VISIBILITY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = Color.White.copy(alpha = 0.4f))
                        Text("${avgVisibility}KM", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black), color = Color.White)
                    }
                }
            }
        }
    }
}

private fun formatDay(timestamp: Long): String {
    val sdf = SimpleDateFormat("EEE", Locale.getDefault())
    return sdf.format(Date(timestamp * 1000))
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}

@Composable
private fun WeatherErrorCard(error: String) {
    // Friendly text for known internal error codes; everything else passes
    // through verbatim so transient network errors are still visible.
    val (title, body) = when {
        error == "weather_api_required" ->
            "WEATHER API KEY MISSING" to "Add your OpenWeather API key in Settings to load forecasts."
        error.contains("Station coordinates", ignoreCase = true) ->
            "STATION LOCATION MISSING" to "Set your station latitude and longitude in Settings (or onboarding) so we know which patch of sky to forecast."
        error.contains("401", ignoreCase = true) ->
            "WEATHER API REJECTED" to "OpenWeather returned 401 Unauthorized — check your API key in Settings."
        error.contains("Unable to resolve host", ignoreCase = true) ||
        error.contains("timeout", ignoreCase = true) ->
            "WEATHER OFFLINE" to "Couldn't reach OpenWeather. Check the device's connection and try refreshing."
        else ->
            "WEATHER UNAVAILABLE" to error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                ),
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )
        }
    }
}
