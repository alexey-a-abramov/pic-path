package com.imageviewer.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object LanguageManager {
    data class Language(val code: String, val name: String)

    private val LANGUAGE_KEY = stringPreferencesKey("language")

    fun getSelectedLanguage(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[LANGUAGE_KEY] ?: "system"
        }
    }

    suspend fun setLanguage(context: Context, languageCode: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = languageCode
        }
        applyLanguage(languageCode)
    }

    fun applyLanguage(languageCode: String) {
        val appLocale: LocaleListCompat = if (languageCode == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageCode)
        }
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    val supportedLanguages = listOf(
        Language("system", "System Language (Default)"),
        Language("en", "English"),
        Language("zh", "中文 (Chinese)"),
        Language("es", "Español (Spanish)"),
        Language("hi", "हिन्दी (Hindi)"),
        Language("ar", "العربية (Arabic)"),
        Language("fr", "Français (French)"),
        Language("bn", "বাংলা (Bengali)"),
        Language("pt", "Português (Portuguese)"),
        Language("ru", "Русский (Russian)"),
        Language("ja", "日本語 (Japanese)"),
        Language("pa", "ਪੰਜਾਬੀ (Punjabi)"),
        Language("mr", "मराठी (Marathi)"),
        Language("te", "తెలుగు (Telugu)"),
        Language("tr", "Türkçe (Turkish)"),
        Language("ko", "한국어 (Korean)"),
        Language("vi", "Tiếng Việt (Vietnamese)"),
        Language("ta", "தமிழ் (Tamil)"),
        Language("de", "Deutsch (German)"),
        Language("ur", "اردو (Urdu)"),
        Language("jv", "Basa Jawa (Javanese)"),
        Language("it", "Italiano (Italian)"),
        Language("gu", "ગુજરાતી (Gujarati)"),
        Language("fa", "فارسی (Persian)"),
        Language("pl", "Polski (Polish)"),
        Language("uk", "Українська (Ukrainian)"),
        Language("ml", "മലയാളം (Malayalam)"),
        Language("kn", "ಕನ್ನಡ (Kannada)"),
        Language("or", "ଓଡ଼ିଆ (Odia)"),
        Language("my", "ဗမာစာ (Burmese)"),
        Language("ro", "Română (Romanian)")
    )
}
