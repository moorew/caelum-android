package de.astronarren.allsky.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserPreferences(private val context: Context) {
    companion object {
        private val ALLSKY_URL = stringPreferencesKey("allsky_url")
        private val API_KEY = stringPreferencesKey("api_key")
        private val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")
        private val LANGUAGE_KEY = stringPreferencesKey("selected_language")
        private val LAST_NOTIFICATION_DATE = stringPreferencesKey("last_notification_date")
        private val USERNAME = stringPreferencesKey("username")
        private val PASSWORD = stringPreferencesKey("password")
        private val STATION_NAME = stringPreferencesKey("station_name")
        private val MAIN_LAYOUT = stringPreferencesKey("main_layout")
        private val MAIN_LAYOUT_VERSION = intPreferencesKey("main_layout_version")
        private val LATITUDE = stringPreferencesKey("latitude")
        private val LONGITUDE = stringPreferencesKey("longitude")

        // ----- Optional focus-motor feature (off by default) -----
        // The user explicitly opts in from Settings; defaults are tuned for
        // the printables.com Allsky v3AF guide that we link out to.
        private val FOCUS_ENABLED = booleanPreferencesKey("focus_enabled")
        private val FOCUS_TRANSPORT = stringPreferencesKey("focus_transport") // "SSH" | "HTTP"
        private val FOCUS_HOST = stringPreferencesKey("focus_host")
        private val FOCUS_PORT = intPreferencesKey("focus_port")
        private val FOCUS_USERNAME = stringPreferencesKey("focus_username")
        private val FOCUS_PASSWORD = stringPreferencesKey("focus_password")
        private val FOCUS_SCRIPT_PATH = stringPreferencesKey("focus_script_path")
        private val FOCUS_HTTP_ENDPOINT = stringPreferencesKey("focus_http_endpoint")
        private val FOCUS_DEFAULT_STEPS = intPreferencesKey("focus_default_steps")

        // ----- Live-image sky overlay + fisheye calibration -----
        // OVERLAY toggles the moon/planet/satellite markers drawn on top of
        // the live image. The four FISHEYE_* keys persist the calibration
        // solved on the calibration screen — stored as doubles encoded into
        // strings so a future format change (extra distortion coefficient,
        // confidence weight…) can be added without a migration. SOLVED_AT is
        // 0 when no calibration has been performed; the renderer treats that
        // as "fall back to the default inscribed-circle geometry, north up".
        private val SKY_OVERLAY_ENABLED = booleanPreferencesKey("sky_overlay_enabled")
        private val FISHEYE_CX_FRAC = stringPreferencesKey("fisheye_cx_frac")
        private val FISHEYE_CY_FRAC = stringPreferencesKey("fisheye_cy_frac")
        private val FISHEYE_RADIUS_FRAC = stringPreferencesKey("fisheye_radius_frac")
        private val FISHEYE_NORTH_OFFSET_DEG = stringPreferencesKey("fisheye_north_offset_deg")
        private val FISHEYE_SOLVED_AT_MS = longPreferencesKey("fisheye_solved_at_ms")
        private val FISHEYE_RMS_ERROR_DEG = stringPreferencesKey("fisheye_rms_error_deg")

        // ----- Sky-condition push alerts (off by default) -----
        // The user opts in via the drawer toggle; when on, the WeatherWorker
        // fires a single notification on the day tonight's rating transitions
        // from POOR/FAIR up to GOOD/EXCELLENT. LAST_NIGHT_RATING is encoded
        // as "YYYY-MM-DD:RATING" so a worker run on day N can't mistakenly
        // suppress an alert when day N+1's first-of-day rating reading is
        // higher than yesterday's final reading.
        private val SKY_ALERTS_ENABLED = booleanPreferencesKey("sky_alerts_enabled")
        private val LAST_NIGHT_RATING = stringPreferencesKey("last_night_rating")

        // ----- Red-Light night-vision mode (off by default) -----
        // When on, the whole app re-themes to the monochrome deep-red palette
        // and live/media imagery is tinted red to preserve dark adaptation.
        private val RED_LIGHT_MODE = booleanPreferencesKey("red_light_mode")

        // v2 (2.2.0): moves MOON to the bottom — feedback was that the big moon
        // disc was crowding out weather and Best Viewing Night, which are the
        // modules most users glance at first. Bump this whenever the canonical
        // order changes meaningfully; existing saved layouts older than the
        // current version are reset to DEFAULT_LAYOUT on next read.
        private const val CURRENT_LAYOUT_VERSION = 2
        private const val DEFAULT_LAYOUT = "LIVE_VIEW,BEST_VIEWING,WEATHER,TIMELAPSES,METEORS,IMAGES,KEOGRAMS,STARTRAILS,MOON"
        private const val DEFAULT_API_KEY = "9908d92979873f12ec6eaecc05335284"
    }

    suspend fun saveStationName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[STATION_NAME] = name
        }
    }

    suspend fun getStationName(): String = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[STATION_NAME] ?: ""
    }

    fun getStationNameFlow(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[STATION_NAME] ?: ""
        }
    }

    suspend fun saveLatitude(lat: String) {
        context.dataStore.edit { preferences ->
            preferences[LATITUDE] = lat
        }
    }

    suspend fun getLatitude(): String = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[LATITUDE] ?: ""
    }

    fun getLatitudeFlow(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[LATITUDE] ?: ""
        }
    }

    suspend fun saveLongitude(lon: String) {
        context.dataStore.edit { preferences ->
            preferences[LONGITUDE] = lon
        }
    }

    suspend fun getLongitude(): String = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[LONGITUDE] ?: ""
    }

    fun getLongitudeFlow(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[LONGITUDE] ?: ""
        }
    }

    suspend fun saveMainLayout(layout: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[MAIN_LAYOUT] = layout.joinToString(",")
            // Saving from the layout editor implicitly opts in to the current
            // canonical version — without this, every save would be reset
            // back to DEFAULT_LAYOUT on the next read.
            preferences[MAIN_LAYOUT_VERSION] = CURRENT_LAYOUT_VERSION
        }
    }

    suspend fun getMainLayout(): List<String> = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        resolveLayout(prefs[MAIN_LAYOUT], prefs[MAIN_LAYOUT_VERSION])
    }

    fun getMainLayoutFlow(): Flow<List<String>> {
        return context.dataStore.data.map { preferences ->
            resolveLayout(preferences[MAIN_LAYOUT], preferences[MAIN_LAYOUT_VERSION])
        }
    }

    /**
     * Returns the effective home layout.
     *
     * If the saved layout version is older than [CURRENT_LAYOUT_VERSION] —
     * the canonical order has changed in a meaningful way, e.g. MOON moving
     * to the bottom in v2 — we reset to [DEFAULT_LAYOUT]. Bumping
     * [CURRENT_LAYOUT_VERSION] is also the mechanism for introducing brand
     * new modules to existing installs: pre-bump users see the new module
     * because they're reset to DEFAULT; post-bump users save explicitly.
     *
     * What we deliberately do NOT do is re-append any DEFAULT_LAYOUT module
     * missing from the saved list. That used to live here and silently
     * undid every uncheck the user made in the layout editor — the moment
     * they hit SAVE, the saved list went round trip through this function
     * and the unchecked modules came back. Trust the saved list verbatim.
     */
    private fun resolveLayout(saved: String?, version: Int?): List<String> {
        val versionOk = (version ?: 0) >= CURRENT_LAYOUT_VERSION
        if (!versionOk || saved.isNullOrBlank()) {
            return DEFAULT_LAYOUT.split(",")
        }
        return saved.split(",").filter { it.isNotBlank() }
    }

    suspend fun saveUsername(username: String) {
        context.dataStore.edit { preferences ->
            preferences[USERNAME] = username
        }
    }

    suspend fun getUsername(): String = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[USERNAME] ?: ""
    }

    fun getUsernameFlow(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[USERNAME] ?: ""
        }
    }

    suspend fun savePassword(password: String) {
        context.dataStore.edit { preferences ->
            preferences[PASSWORD] = password
        }
    }

    suspend fun getPassword(): String = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[PASSWORD] ?: ""
    }

    fun getPasswordFlow(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[PASSWORD] ?: ""
        }
    }

    suspend fun saveLastNotificationDate(dateStr: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_NOTIFICATION_DATE] = dateStr
        }
    }

    suspend fun getLastNotificationDate(): String = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[LAST_NOTIFICATION_DATE] ?: ""
    }

    // -------------------- Sky-condition alerts --------------------

    fun getSkyAlertsEnabledFlow(): Flow<Boolean> {
        return context.dataStore.data.map { it[SKY_ALERTS_ENABLED] ?: false }
    }

    suspend fun isSkyAlertsEnabled(): Boolean = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[SKY_ALERTS_ENABLED] ?: false
    }

    suspend fun setSkyAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { p -> p[SKY_ALERTS_ENABLED] = enabled }
    }

    // -------------------- Red-Light night-vision mode --------------------

    fun getRedLightModeFlow(): Flow<Boolean> {
        return context.dataStore.data.map { it[RED_LIGHT_MODE] ?: false }
    }

    suspend fun setRedLightMode(enabled: Boolean) {
        context.dataStore.edit { p -> p[RED_LIGHT_MODE] = enabled }
    }

    /** Returns Pair(date, ratingName) or null if nothing recorded yet. */
    suspend fun getLastNightRating(): Pair<String, String>? = withContext(Dispatchers.IO) {
        val raw = context.dataStore.data.first()[LAST_NIGHT_RATING] ?: return@withContext null
        val parts = raw.split(":", limit = 2)
        if (parts.size != 2) null else parts[0] to parts[1]
    }

    suspend fun saveLastNightRating(date: String, rating: String) {
        context.dataStore.edit { p -> p[LAST_NIGHT_RATING] = "$date:$rating" }
    }

    suspend fun saveAllskyUrl(url: String) {
        context.dataStore.edit { preferences ->
            preferences[ALLSKY_URL] = url
        }
    }

    suspend fun getAllskyUrl(): String = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[ALLSKY_URL] ?: ""
    }

    fun getAllskyUrlFlow(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[ALLSKY_URL] ?: ""
        }
    }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[API_KEY] = apiKey
        }
    }

    suspend fun getApiKey(): String = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[API_KEY] ?: DEFAULT_API_KEY
    }

    fun getApiKeyFlow(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[API_KEY] ?: DEFAULT_API_KEY
        }
    }

    suspend fun markSetupComplete() {
        context.dataStore.edit { preferences ->
            preferences[SETUP_COMPLETE] = true
        }
    }

    suspend fun isSetupComplete(): Boolean = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[SETUP_COMPLETE] ?: false
    }

    suspend fun saveLanguage(languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = languageCode
        }
    }

    suspend fun getLanguage(): String = withContext(Dispatchers.IO) {
        context.dataStore.data.first()[LANGUAGE_KEY] ?: ""
    }

    // -------------------- Focus-motor feature --------------------

    fun getFocusSettingsFlow(): Flow<FocusSettings> {
        return context.dataStore.data.map { p ->
            FocusSettings(
                enabled = p[FOCUS_ENABLED] ?: false,
                transport = FocusTransport.fromKey(p[FOCUS_TRANSPORT]) ?: FocusTransport.SSH,
                host = p[FOCUS_HOST] ?: "",
                port = p[FOCUS_PORT] ?: 22,
                username = p[FOCUS_USERNAME] ?: "",
                password = p[FOCUS_PASSWORD] ?: "",
                // Allsky v3AF community guide installs focus.py at this path;
                // pre-fill so a user who follows the guide doesn't have to
                // touch it.
                scriptPath = p[FOCUS_SCRIPT_PATH] ?: "~/allsky/scripts/focus.py",
                httpEndpoint = p[FOCUS_HTTP_ENDPOINT] ?: "",
                defaultSteps = p[FOCUS_DEFAULT_STEPS] ?: 256,
            )
        }
    }

    suspend fun getFocusSettings(): FocusSettings = withContext(Dispatchers.IO) {
        getFocusSettingsFlow().first()
    }

    suspend fun saveFocusSettings(settings: FocusSettings) {
        context.dataStore.edit { p ->
            p[FOCUS_ENABLED] = settings.enabled
            p[FOCUS_TRANSPORT] = settings.transport.key
            p[FOCUS_HOST] = settings.host
            p[FOCUS_PORT] = settings.port
            p[FOCUS_USERNAME] = settings.username
            p[FOCUS_PASSWORD] = settings.password
            p[FOCUS_SCRIPT_PATH] = settings.scriptPath
            p[FOCUS_HTTP_ENDPOINT] = settings.httpEndpoint
            p[FOCUS_DEFAULT_STEPS] = settings.defaultSteps
        }
    }

    // -------------------- Sky overlay + fisheye calibration --------------------

    fun getSkyOverlayEnabledFlow(): Flow<Boolean> {
        return context.dataStore.data.map { it[SKY_OVERLAY_ENABLED] ?: false }
    }

    suspend fun setSkyOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { p -> p[SKY_OVERLAY_ENABLED] = enabled }
    }

    /**
     * Returns the persisted fisheye calibration, or
     * [de.astronarren.allsky.data.astro.FisheyeCalibration.DEFAULT_INSCRIBED]
     * when nothing has been saved yet. The renderer can rely on this — every
     * field is always populated — and the `isSolved` flag tells the UI
     * whether to encourage the user to calibrate.
     */
    fun getFisheyeCalibrationFlow(): Flow<de.astronarren.allsky.data.astro.FisheyeCalibration> {
        return context.dataStore.data.map { p ->
            val solvedAt = p[FISHEYE_SOLVED_AT_MS] ?: 0L
            if (solvedAt <= 0L) {
                de.astronarren.allsky.data.astro.FisheyeCalibration.DEFAULT_INSCRIBED
            } else {
                de.astronarren.allsky.data.astro.FisheyeCalibration(
                    cxFrac = p[FISHEYE_CX_FRAC]?.toDoubleOrNull() ?: 0.5,
                    cyFrac = p[FISHEYE_CY_FRAC]?.toDoubleOrNull() ?: 0.5,
                    radiusFrac = p[FISHEYE_RADIUS_FRAC]?.toDoubleOrNull() ?: 0.5,
                    northOffsetDeg = p[FISHEYE_NORTH_OFFSET_DEG]?.toDoubleOrNull() ?: 0.0,
                    solvedAtEpochMs = solvedAt,
                    rmsErrorDeg = p[FISHEYE_RMS_ERROR_DEG]?.toDoubleOrNull(),
                )
            }
        }
    }

    suspend fun saveFisheyeCalibration(cal: de.astronarren.allsky.data.astro.FisheyeCalibration) {
        context.dataStore.edit { p ->
            p[FISHEYE_CX_FRAC] = cal.cxFrac.toString()
            p[FISHEYE_CY_FRAC] = cal.cyFrac.toString()
            p[FISHEYE_RADIUS_FRAC] = cal.radiusFrac.toString()
            p[FISHEYE_NORTH_OFFSET_DEG] = cal.northOffsetDeg.toString()
            p[FISHEYE_SOLVED_AT_MS] = cal.solvedAtEpochMs
            if (cal.rmsErrorDeg != null) {
                p[FISHEYE_RMS_ERROR_DEG] = cal.rmsErrorDeg.toString()
            } else {
                p.remove(FISHEYE_RMS_ERROR_DEG)
            }
        }
    }
}

/**
 * User-visible focus-motor config. SSH is the default because the printables
 * guide builds on the stock Allsky stack which already ships sshd, so most
 * users won't need to deploy anything new on the Pi.
 */
data class FocusSettings(
    val enabled: Boolean,
    val transport: FocusTransport,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val scriptPath: String,
    val httpEndpoint: String,
    val defaultSteps: Int,
)

enum class FocusTransport(val key: String, val label: String) {
    SSH("SSH", "SSH"),
    HTTP("HTTP", "HTTP endpoint");

    companion object {
        fun fromKey(key: String?): FocusTransport? = values().firstOrNull { it.key == key }
    }
}
