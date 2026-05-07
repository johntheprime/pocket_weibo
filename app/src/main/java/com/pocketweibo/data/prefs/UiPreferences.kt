package com.pocketweibo.data.prefs

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.uiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_prefs")

private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
private val KEY_DIAGNOSTIC_LOG_CAPTURE = booleanPreferencesKey("diagnostic_log_capture")

private val KEY_SHAKE_EV_H = intPreferencesKey("shake_rem_evening_h")
private val KEY_SHAKE_EV_M = intPreferencesKey("shake_rem_evening_m")
private val KEY_SHAKE_MO_H = intPreferencesKey("shake_rem_morning_h")
private val KEY_SHAKE_MO_M = intPreferencesKey("shake_rem_morning_m")

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

    fun diagnosticLogCaptureFlow(context: Context): Flow<Boolean> =
        context.uiPreferencesDataStore.data.map { prefs -> prefs[KEY_DIAGNOSTIC_LOG_CAPTURE] == true }

    suspend fun isDiagnosticLogCaptureEnabled(context: Context): Boolean =
        context.uiPreferencesDataStore.data.map { it[KEY_DIAGNOSTIC_LOG_CAPTURE] == true }.first()

    suspend fun setDiagnosticLogCaptureEnabled(context: Context, enabled: Boolean) {
        context.uiPreferencesDataStore.edit { it[KEY_DIAGNOSTIC_LOG_CAPTURE] = enabled }
    }

    /** Shake on post detail: default “today evening” and “next morning” reminder anchors. */
    fun shakeReminderSettingsFlow(context: Context): Flow<ShakeReminderSettings> =
        context.uiPreferencesDataStore.data.map { prefs ->
            ShakeReminderSettings(
                eveningHour = (prefs[KEY_SHAKE_EV_H] ?: 20).coerceIn(0, 23),
                eveningMinute = (prefs[KEY_SHAKE_EV_M] ?: 0).coerceIn(0, 59),
                morningHour = (prefs[KEY_SHAKE_MO_H] ?: 9).coerceIn(0, 23),
                morningMinute = (prefs[KEY_SHAKE_MO_M] ?: 0).coerceIn(0, 59),
            )
        }

    suspend fun getShakeReminderSettings(context: Context): ShakeReminderSettings =
        shakeReminderSettingsFlow(context).first()

    suspend fun setShakeReminderEvening(context: Context, hour: Int, minute: Int) {
        context.uiPreferencesDataStore.edit {
            it[KEY_SHAKE_EV_H] = hour.coerceIn(0, 23)
            it[KEY_SHAKE_EV_M] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setShakeReminderMorning(context: Context, hour: Int, minute: Int) {
        context.uiPreferencesDataStore.edit {
            it[KEY_SHAKE_MO_H] = hour.coerceIn(0, 23)
            it[KEY_SHAKE_MO_M] = minute.coerceIn(0, 59)
        }
    }
}
