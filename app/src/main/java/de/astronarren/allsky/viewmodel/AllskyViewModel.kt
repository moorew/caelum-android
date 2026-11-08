package de.astronarren.allsky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.astronarren.allsky.data.AllskyMedia
import de.astronarren.allsky.data.AllskyRepository
import de.astronarren.allsky.data.MediaCategory
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.network.AllskyClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CategoryUiState(
    val items: List<AllskyMediaUiState> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class AllskyUiState(
    val categories: Map<MediaCategory, CategoryUiState> = emptyMap(),
) {
    fun of(c: MediaCategory): CategoryUiState = categories[c] ?: CategoryUiState()
}

/** State for the full-screen media browser (one category, optionally one night). */
data class BrowseUiState(
    val category: MediaCategory? = null,
    val day: String = AllskyRepository.ALL,
    val items: List<AllskyMediaUiState> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class AllskyMediaUiState(
    val date: String,
    val url: String,
    val thumbUrl: String? = null,
)

private fun AllskyMedia.toUi() = AllskyMediaUiState(date, url, thumbUrl)

/** Home-screen rows show the newest items only; the browser shows everything. */
private const val HOME_ROW_LIMIT = 30
private const val IMAGES_FRESH_MS = 10 * 60 * 1000L

class AllskyViewModel(
    private val allskyRepository: AllskyRepository,
    private val allskyClient: AllskyClient,
    private val userPreferences: UserPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AllskyUiState())
    val uiState: StateFlow<AllskyUiState> = _uiState.asStateFlow()

    private val _browse = MutableStateFlow(BrowseUiState())
    val browse: StateFlow<BrowseUiState> = _browse.asStateFlow()

    private val homeJobs = mutableMapOf<MediaCategory, Job>()
    private var browseJob: Job? = null

    init {
        refresh()
        // When the URL or credentials change, drop the old session and lists.
        viewModelScope.launch {
            combine(
                userPreferences.getAllskyUrlFlow(),
                userPreferences.getUsernameFlow(),
                userPreferences.getPasswordFlow(),
            ) { u, n, p -> Triple(u, n, p) }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    allskyClient.clearSession()
                    allskyRepository.clearCache()
                    _uiState.value = AllskyUiState()
                    refresh()
                }
        }
    }

    /**
     * Reload every home-screen category, each independently and in parallel.
     * [force] (pull-to-refresh) bypasses the freshness window for the
     * expensive raw-frames list.
     */
    fun refresh(force: Boolean = false) {
        MediaCategory.entries.forEach { loadHome(it, force) }
    }

    private fun loadHome(category: MediaCategory, force: Boolean = false) {
        homeJobs[category]?.cancel()
        homeJobs[category] = viewModelScope.launch {
            // 1) Instant: last known list from disk.
            val cached = allskyRepository.cached(category)
            setHome(category) {
                it.copy(
                    items = cached?.take(HOME_ROW_LIMIT)?.map { m -> m.toUi() } ?: it.items,
                    isLoading = true,
                    error = null,
                )
            }
            // The raw-frames list costs the Pi several seconds (it stats
            // every frame), so don't re-ask on every app open.
            if (!force && cached != null && category == MediaCategory.IMAGES) {
                val age = allskyRepository.cacheAgeMs(category)
                if (age != null && age < IMAGES_FRESH_MS) {
                    setHome(category) { it.copy(isLoading = false) }
                    return@launch
                }
            }
            // 2) Revalidate from the network.
            try {
                val fresh = allskyRepository.fetch(category)
                setHome(category) {
                    CategoryUiState(items = fresh.take(HOME_ROW_LIMIT).map { m -> m.toUi() })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setHome(category) { it.copy(isLoading = false, error = e.message ?: "Couldn't load") }
            }
        }
    }

    private inline fun setHome(c: MediaCategory, f: (CategoryUiState) -> CategoryUiState) {
        _uiState.update { s -> s.copy(categories = s.categories + (c to f(s.of(c)))) }
    }

    /**
     * Open (or re-target) the full-screen browser. Shows cached results
     * immediately; past nights that are already cached are not re-fetched
     * unless [force] is set (pull-to-refresh).
     */
    fun browse(category: MediaCategory, day: String = AllskyRepository.ALL, force: Boolean = false) {
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            val cached = allskyRepository.cached(category, day)
            _browse.value = BrowseUiState(
                category = category,
                day = day,
                items = cached?.map { it.toUi() } ?: emptyList(),
                isLoading = true,
            )
            if (!force && cached != null && allskyRepository.isImmutable(day)) {
                _browse.update { it.copy(isLoading = false) }
                return@launch
            }
            try {
                val fresh = allskyRepository.fetch(category, day)
                _browse.update { it.copy(items = fresh.map { m -> m.toUi() }, isLoading = false, error = null) }
                if (day == AllskyRepository.ALL) {
                    setHome(category) { CategoryUiState(items = fresh.take(HOME_ROW_LIMIT).map { m -> m.toUi() }) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _browse.update { it.copy(isLoading = false, error = e.message ?: "Couldn't load") }
            }
        }
    }
}
