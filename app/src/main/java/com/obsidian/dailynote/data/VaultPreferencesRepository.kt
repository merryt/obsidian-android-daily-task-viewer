package com.obsidian.dailynote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_preferences")

class VaultPreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val TREE_URI = stringPreferencesKey("vault_tree_uri")
        val VAULT_NAME = stringPreferencesKey("vault_name")
        val DAILY_NOTES_FOLDER = stringPreferencesKey("daily_notes_folder")
        val DATE_FORMAT = stringPreferencesKey("date_format")
        val EXCLUDED_SECTIONS = stringPreferencesKey("excluded_sections")
        val HIDE_COMPLETED_TASKS = booleanPreferencesKey("hide_completed_tasks")
    }

    val preferencesFlow: Flow<VaultPreferences> = context.dataStore.data.map { preferences ->
        VaultPreferences(
            treeUriString = preferences[PreferencesKeys.TREE_URI],
            vaultName = preferences[PreferencesKeys.VAULT_NAME] ?: "",
            dailyNotesFolder = preferences[PreferencesKeys.DAILY_NOTES_FOLDER] ?: "",
            dateFormat = preferences[PreferencesKeys.DATE_FORMAT] ?: "yyyy-MM-dd",
            excludedSections = preferences[PreferencesKeys.EXCLUDED_SECTIONS] ?: "Journaling",
            hideCompletedTasks = preferences[PreferencesKeys.HIDE_COMPLETED_TASKS] ?: true
        )
    }

    suspend fun getPreferencesSnapshot(): VaultPreferences {
        return preferencesFlow.first()
    }

    suspend fun updatePreferences(
        treeUriString: String?,
        vaultName: String,
        dailyNotesFolder: String,
        dateFormat: String,
        excludedSections: String = "Journaling",
        hideCompletedTasks: Boolean = true
    ) {
        context.dataStore.edit { preferences ->
            if (treeUriString != null) {
                preferences[PreferencesKeys.TREE_URI] = treeUriString
            } else {
                preferences.remove(PreferencesKeys.TREE_URI)
            }
            preferences[PreferencesKeys.VAULT_NAME] = vaultName
            preferences[PreferencesKeys.DAILY_NOTES_FOLDER] = dailyNotesFolder
            preferences[PreferencesKeys.DATE_FORMAT] = dateFormat
            preferences[PreferencesKeys.EXCLUDED_SECTIONS] = excludedSections
            preferences[PreferencesKeys.HIDE_COMPLETED_TASKS] = hideCompletedTasks
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
