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
     * Returns the effective home layout, applying any necessary migrations:
     *
     *   - If the saved layout version is older than [CURRENT_LAYOUT_VERSION]
     *     (i.e. the canonical order has changed), reset to [DEFAULT_LAYOUT].
     *     This is how MOON moves to the bottom for users upgrading from 2.1.x.
     *   - Otherwise honour the saved layout and append any modules added in a
     *     later version that aren't yet present (BEST_VIEWING is special-cased
     *     to slot in just below LIVE_VIEW).
     */
    private fun resolveLayout(saved: String?, version: Int?): List<String> {
        val versionOk = (version ?: 0) >= CURRENT_LAYOUT_VERSION
        if (!versionOk || saved.isNullOrBlank()) {
            return DEFAULT_LAYOUT.split(",")
        }
        val list = saved.split(",").filter { it.isNotBlank() }.toMutableList()
        DEFAULT_LAYOUT.split(",").forEach { module ->
            if (!list.contains(module)) {
                if (module == "BEST_VIEWING") {
                    val index = list.indexOf("LIVE_VIEW")
                    if (index != -1) list.add(index + 1, module) else list.add(0, module)
                } else {
                    list.add(module)
                }
            }
        }
        return list
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
