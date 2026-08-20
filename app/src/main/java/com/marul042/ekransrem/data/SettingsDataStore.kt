package com.marul042.ekransrem.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    
    companion object {
        val DAY_RESET_HOUR = intPreferencesKey("day_reset_hour")
    }

    /**
     * Flow of the day reset hour (0-23). Defaults to 0 (midnight/00:00).
     * Supported values: 0, 3, or custom hours 0-23.
     */
    val dayResetHourFlow: Flow<Int> = context.dataStore.data
        .map { preferences ->
            preferences[DAY_RESET_HOUR] ?: 0 // Default: 00:00 (midnight)
        }

    /**
     * Set the day reset hour (0-23).
     */
    suspend fun setDayResetHour(hour: Int) {
        require(hour in 0..23) { "Hour must be between 0 and 23" }
        context.dataStore.edit { preferences ->
            preferences[DAY_RESET_HOUR] = hour
        }
    }

    /**
     * Get the current day reset hour (blocking - use flow for reactive updates).
     */
    suspend fun getDayResetHour(): Int {
        return context.dataStore.data.map { preferences ->
            preferences[DAY_RESET_HOUR] ?: 0
        }.first()
    }
}
