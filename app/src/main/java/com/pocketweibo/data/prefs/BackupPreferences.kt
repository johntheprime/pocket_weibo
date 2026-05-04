package com.pocketweibo.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.backupPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "backup_prefs")

private val KEY_LAST_AUTO_TEXT_BACKUP_DAY = stringPreferencesKey("last_auto_text_backup_yyyy_MM_dd")

object BackupPreferences {

    suspend fun getLastAutoTextBackupDay(context: Context): String? =
        context.backupPrefsDataStore.data
            .map { it[KEY_LAST_AUTO_TEXT_BACKUP_DAY] }
            .first()

    suspend fun setLastAutoTextBackupDay(context: Context, isoLocalDate: String) {
        context.backupPrefsDataStore.edit { it[KEY_LAST_AUTO_TEXT_BACKUP_DAY] = isoLocalDate }
    }
}
