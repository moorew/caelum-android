package de.astronarren.allsky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.data.astro.AuroraOutlook
import de.astronarren.allsky.data.astro.AuroraRepository
import de.astronarren.allsky.data.astro.MoonAlmanac
import de.astronarren.allsky.data.astro.MoonEvents
import de.astronarren.allsky.data.astro.Planet
import de.astronarren.allsky.data.astro.PlanetAlmanac
import de.astronarren.allsky.data.astro.PlanetEvents
import de.astronarren.allsky.data.astro.PlanetSnapshot
import de.astronarren.allsky.data.astro.SatellitePass
import de.astronarren.allsky.data.astro.SatelliteRepository
import de.astronarren.allsky.ui.modules.ActiveShower
import de.astronarren.allsky.ui.modules.findActiveShower
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Aggregates every row that the [de.astronarren.allsky.ui.modules.TonightModule]
 * card renders. One ViewModel keeps:
 *   - the meteor shower lookup (offline, instant)
 *   - moon rise/set/transit at the user's lat/lon (offline, ~ms)
 *   - visible-planet snapshots above-horizon now (offline, ~ms per planet)
 *   - aurora KP outlook over the next 24h (network, 1h cache)
 *   - bright satellite passes (network, 24h TLE cache + SGP4 per recompute)
 *
 * The work is kicked off once at construction. Recomputes are cheap enough
 * (~5 ms total for the offline rows) that we don't bother with explicit
 * refresh — the card refreshes on screen open via the ViewModel's natural
 * recreation when MainScreen recomposes after the user navigates away and
 * back. Network rows lean on their repositories' caches to stay polite.
 */
class TonightViewModel(
    private val userPreferences: UserPreferences,
    private val auroraRepository: AuroraRepository = AuroraRepository(),
    private val satelliteRepository: SatelliteRepository = SatelliteRepository(),
    private val clock: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _state = MutableStateFlow(TonightUiState())
    val state: StateFlow<TonightUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val latStr = userPreferences.getLatitudeFlow().first()
            val lonStr = userPreferences.getLongitudeFlow().first()
            val lat = latStr.toDoubleOrNull()
            val lon = lonStr.toDoubleOrNull()

            // Meteor shower row is location-independent — always available.
            val shower = withContext(Dispatchers.Default) { findActiveShower() }
            _state.update { it.copy(activeShower = shower) }

            if (lat == null || lon == null) {
                // No coordinates set → location-dependent rows stay null and
                // the card just shows the shower row. The user is nudged to
                // fill in Settings via the small note rendered at the bottom.
                _state.update { it.copy(hasLocation = false, isLoading = false) }
                return@launch
            }
            _state.update { it.copy(hasLocation = true) }

            // Offline rows (moon, planets) — Default dispatcher because the
            // 5-minute scan-and-interpolate work is pure CPU.
            val today = LocalDate.now(zone)
            val now = clock()

            val moonEvents = withContext(Dispatchers.Default) {
                MoonAlmanac.riseSetTransit(today, lat, lon, zone)
            }
            val moonPosition = withContext(Dispatchers.Default) {
                val jd = de.astronarren.allsky.data.astro.AstroMath.julianDate(now)
                MoonAlmanac.position(jd)
            }

            val planetData = withContext(Dispatchers.Default) {
                Planet.NAKED_EYE.map { planet ->
                    val snap = PlanetAlmanac.snapshot(planet, lat, lon, now)
                    val events = PlanetAlmanac.riseSetTransit(planet, today, lat, lon, zone)
                    PlanetRow(snap, events)
                }
            }

            _state.update {
                it.copy(
                    moonEvents = moonEvents,
                    moonIlluminationPercent = (moonPosition.illuminatedFraction * 100).toInt(),
                    planets = planetData,
                )
            }

            // Network rows in parallel. Each lights its own row independently.
            launch {
                val aurora = runCatching { auroraRepository.tonight(lat, lon) }.getOrNull()
                _state.update { it.copy(aurora = aurora) }
            }
            launch {
                val passes = runCatching { satelliteRepository.upcomingPasses(lat, lon) }
                    .getOrNull()
                    ?: emptyList()
                _state.update { it.copy(satellitePasses = passes) }
            }

            _state.update { it.copy(isLoading = false) }
        }
    }
}

/**
 * Whole-card UI state. Each row checks its own field for null/empty to
 * decide whether to render itself.
 */
data class TonightUiState(
    val isLoading: Boolean = true,
    val hasLocation: Boolean = false,

    val activeShower: ActiveShower? = null,

    val moonEvents: MoonEvents? = null,
    val moonIlluminationPercent: Int = 0,

    val planets: List<PlanetRow> = emptyList(),

    val aurora: AuroraOutlook? = null,

    val satellitePasses: List<SatellitePass> = emptyList(),
) {
    /** Planets currently above the horizon — only these are rendered. */
    val visiblePlanets: List<PlanetRow>
        get() = planets
            .filter { it.snapshot.horizontal.altitudeDeg > 0 }
            .sortedBy { it.snapshot.apparentMagnitude }     // brightest first
}

/** One planet's full "tonight" bundle: where it is now + rise/set timing. */
data class PlanetRow(
    val snapshot: PlanetSnapshot,
    val events: PlanetEvents,
)

class TonightViewModelFactory(
    private val userPreferences: UserPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return TonightViewModel(userPreferences) as T
    }
}
