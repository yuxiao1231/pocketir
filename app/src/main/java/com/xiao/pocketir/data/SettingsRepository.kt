package com.xiao.pocketir.data

import android.content.Context
import android.os.Environment
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "config"))
    }
)

data class UserSettings(
    val currentPath: String = SettingsRepository.defaultLibraryPath(),
    val isDarkMode: Boolean = false
)

class SettingsRepository(private val context: Context) {
    val settings: Flow<UserSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferencesCompat())
            } else {
                throw error
            }
        }
        .map { preferences ->
            UserSettings(
                currentPath = preferences[Keys.CurrentPath] ?: defaultLibraryPath(),
                isDarkMode = preferences[Keys.DarkMode] ?: false
            )
        }

    suspend fun setCurrentPath(path: String) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.CurrentPath] = path
        }
    }

    suspend fun setDarkMode(isDarkMode: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.DarkMode] = isDarkMode
        }
    }

    private object Keys {
        val CurrentPath = stringPreferencesKey("db_path")
        val DarkMode = booleanPreferencesKey("dark_mode")
    }

    companion object {
        fun defaultLibraryPath(): String {
            return Environment.getExternalStorageDirectory().absolutePath + "/pocketir/pocket_ir.db"
        }
    }
}

private fun emptyPreferencesCompat() = androidx.datastore.preferences.core.emptyPreferences()
