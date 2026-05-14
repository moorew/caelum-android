package de.astronarren.allsky.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.astronarren.allsky.data.UserPreferences
import de.astronarren.allsky.ui.state.LiveImageUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class LiveImageViewModel(
    private val userPreferences: UserPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(LiveImageUiState())
    val uiState: StateFlow<LiveImageUiState> = _uiState.asStateFlow()

    // Resolved live-image endpoint cached across refreshes — re-probed only
    // when the configured base URL changes or after repeated network errors.
    @Volatile private var cachedBase: String = ""
    @Volatile private var cachedStreamKey: String? = null
    @Volatile private var consecutiveErrors: Int = 0

    init {
        startImageRefresh()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun startImageRefresh() {
        viewModelScope.launch {
            combine(
                userPreferences.getAllskyUrlFlow(),
                userPreferences.getUsernameFlow(),
                userPreferences.getPasswordFlow()
            ) { url, username, password ->
                LiveImageConfig(
                    baseUrl = url.trim().trimEnd('/'),
                    username = username,
                    password = password,
                )
            }
                .distinctUntilChanged()
                .flatMapLatest { config ->
                    flow {
                        resetResolvedImage(config.baseUrl)
                        while (currentCoroutineContext().isActive) {
                            emit(config)
                            delay(
                                if (_uiState.value.error != null) {
                                    ERROR_REFRESH_INTERVAL_MS
                                } else {
                                    NORMAL_REFRESH_INTERVAL_MS
                                }
                            )
                        }
                    }
                }
                .collectLatest { config ->
                    try {
                        updateImage(config)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        consecutiveErrors += 1
                        _uiState.update { it.copy(error = "Network drop: ${e.message}") }
                    }
                }
        }
    }

    private fun resetResolvedImage(baseUrl: String) {
        cachedStreamKey = null
        cachedBase = baseUrl
        consecutiveErrors = 0
    }

    /**
     * Pushed in by MainScreen from the Coil onSuccess listener once the
     * BitmapDrawable is available. The sky-overlay needs the source image's
     * intrinsic dimensions to apply the ContentScale.Crop transform when
     * mapping calibrated alt/az → display-pixel offsets.
     */
    fun setImageSize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        _uiState.update { current ->
            if (current.imageWidthPx == widthPx && current.imageHeightPx == heightPx) {
                current
            } else {
                current.copy(imageWidthPx = widthPx, imageHeightPx = heightPx)
            }
        }
    }

    private suspend fun updateImage(config: LiveImageConfig) {
        if (config.baseUrl.isEmpty()) {
            _uiState.update { it.copy(error = "Allsky URL not configured") }
            return
        }

        val cleanUrl = config.baseUrl

        try {
            // Reuse a previously resolved stream URL while the base URL is
            // unchanged and we are not in a sustained error state.
            val reusable = cachedStreamKey != null &&
                cachedBase == cleanUrl &&
                consecutiveErrors < 2

            val resolved: String = if (reusable) {
                cachedStreamKey!!
            } else {
                probeLiveImage(cleanUrl, config.username, config.password) ?: "$cleanUrl/image.jpg"
            }

            cachedBase = cleanUrl
            cachedStreamKey = resolved
            consecutiveErrors = 0

            _uiState.update { currentState ->
                currentState.copy(
                    imageUrl = "$resolved?t=${System.currentTimeMillis()}",
                    streamKey = resolved,
                    lastUpdate = System.currentTimeMillis(),
                    error = null
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            consecutiveErrors += 1
            _uiState.update { it.copy(error = "Stream error: ${e.message}") }
        }
    }

    private suspend fun probeLiveImage(
        cleanUrl: String,
        username: String,
        password: String
    ): String? = withContext(Dispatchers.IO) {
        fun testPath(path: String): Int {
            var conn: java.net.HttpURLConnection? = null
            return try {
                val testUrl = java.net.URL(path)
                conn = (testUrl.openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    if (username.isNotEmpty() && password.isNotEmpty()) {
                        val basicAuth = "Basic " + android.util.Base64.encodeToString(
                            "$username:$password".toByteArray(),
                            android.util.Base64.NO_WRAP
                        )
                        setRequestProperty("Authorization", basicAuth)
                    }
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                conn.responseCode
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                -1
            } finally {
                conn?.disconnect()
            }
        }

        // Priority 1: /current/tmp/image.jpg on the install root (most common
        // for `/allsky` installs)
        val rootUrl = if (cleanUrl.endsWith("/allsky")) cleanUrl.dropLast(7) else cleanUrl
        if (testPath("$rootUrl/current/tmp/image.jpg") == 200) {
            return@withContext "$rootUrl/current/tmp/image.jpg"
        }

        // Priority 2: <base>/image.jpg
        if (testPath("$cleanUrl/image.jpg") == 200) {
            return@withContext "$cleanUrl/image.jpg"
        }

        // Priority 3: <base>/allsky/image.jpg (when user stored the install
        // parent, not the /allsky directory)
        if (!cleanUrl.endsWith("/allsky") && testPath("$cleanUrl/allsky/image.jpg") == 200) {
            return@withContext "$cleanUrl/allsky/image.jpg"
        }

        null
    }

    private data class LiveImageConfig(
        val baseUrl: String,
        val username: String,
        val password: String,
    )

    private companion object {
        const val NORMAL_REFRESH_INTERVAL_MS = 30_000L
        const val ERROR_REFRESH_INTERVAL_MS = 60_000L
    }
}
