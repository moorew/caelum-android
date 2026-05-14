package de.astronarren.allsky.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import de.astronarren.allsky.R
import de.astronarren.allsky.data.dataStore
import kotlinx.coroutines.flow.first
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the application's language settings following Android architecture guidelines.
 * This class is responsible for:
 * 1. Setting the application language
 * 2. Getting the current language
 * 3. Providing a list of supported languages
 */
enum class AppLanguage(val code: String, val nameResId: Int) {
    SYSTEM("", R.string.language_system),
    ENGLISH("en", R.string.language_english),
    GERMAN("de", R.string.language_german),
    SPANISH("es", R.string.language_spanish),
    FRENCH("fr", R.string.language_french),
    ITALIAN("it", R.string.language_italian);

    companion object {
        fun fromCode(code: String): AppLanguage {
            return values().find { it.code == code } ?: SYSTEM
        }
    }
}

/**
 * LanguageManager follows the single responsibility principle and manages all language-related
 * operations in the app.
 */
class LanguageManager(
    private val context: Context,
    private val onLanguageChanged: (() -> Unit)? = null
) {
    private val LANGUAGE_KEY = stringPreferencesKey("selected_language")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isChangingLanguage = false

    init {
        scope.launch {
            val savedLanguage = getSavedLanguage()
            if (savedLanguage != AppLanguage.SYSTEM) {
                applyLanguage(savedLanguage)
            }
        }
    }

    fun setLanguage(language: AppLanguage) {
        if (isChangingLanguage) return
        isChangingLanguage = true
        
        scope.launch {
            try {
                // Only apply and save if it's different from current
                val currentLanguage = getSavedLanguage()
                if (language != currentLanguage) {
                    applyLanguage(language)
                    withContext(Dispatchers.IO) {
                        context.dataStore.edit { preferences ->
                            preferences[LANGUAGE_KEY] = language.code
                        }
                    }
                    // Only trigger callback if language actually changed
                    onLanguageChanged?.invoke()
                }
            } finally {
                isChangingLanguage = false
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private fun applyLanguage(language: AppLanguage) {
        val locale = when (language) {
            AppLanguage.SYSTEM -> getSystemLocale()
            else -> Locale(language.code)
        }

        Locale.setDefault(locale)

        val config = context.resources.configuration
        config.setLocale(locale)
        
        val localeList = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.create(locale)
        }
        AppCompatDelegate.setApplicationLocales(localeList)

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }

    suspend fun getCurrentLanguage(): AppLanguage {
        return getSavedLanguage()
    }

    private suspend fun getSavedLanguage(): AppLanguage = withContext(Dispatchers.IO) {
        val languageCode = context.dataStore.data.first()[LANGUAGE_KEY] ?: ""
        AppLanguage.fromCode(languageCode)
    }
} 
