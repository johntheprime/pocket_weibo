package com.pocketweibo.data.prefs

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.uiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_prefs")

private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")

/** Stored codes: `system`, `zh`, `en`. */
object UiPreferences {

    suspend fun getLanguageCode(context: Context): String =
        context.uiPreferencesDataStore.data
            .map { prefs -> prefs[KEY_APP_LANGUAGE] ?: "system" }
            .first()

    suspend fun setLanguageCode(context: Context, code: String) {
        context.uiPreferencesDataStore.edit { it[KEY_APP_LANGUAGE] = code }
    }

    fun applyLanguageCode(code: String) {
        val locales = when (code) {
            "en" -> LocaleListCompat.forLanguageTags("en")
            "zh" -> LocaleListCompat.forLanguageTags("zh-CN")
            else -> LocaleListCompat.getEmptyLocaleList()
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    suspend fun applyStored(context: Context) {
        applyLanguageCode(getLanguageCode(context))
    }
}
