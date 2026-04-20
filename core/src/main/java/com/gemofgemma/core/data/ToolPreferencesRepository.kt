package com.gemofgemma.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gemofgemma.core.model.ToolDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.toolPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "tool_preferences")

class ToolPreferencesRepository(private val context: Context) {

    private val ENABLED_TOOLS_KEY = stringSetPreferencesKey("enabled_tools")
    private val INITIALIZED_KEY = booleanPreferencesKey("tools_initialized_v2")

    val enabledToolsFlow: Flow<Set<String>> = context.toolPreferencesDataStore.data
        .map { preferences ->
            preferences[ENABLED_TOOLS_KEY] ?: emptySet()
        }

    fun getDefaultTools(): Set<String> = ToolDefinition.PHONE_ACTION_IDS

    suspend fun initializeDefaultsIfNeeded() {
        val prefs = context.toolPreferencesDataStore.data.first()
        val initialized = prefs[INITIALIZED_KEY] ?: false
        if (!initialized) {
            context.toolPreferencesDataStore.edit { preferences ->
                val existing = preferences[ENABLED_TOOLS_KEY] ?: emptySet()
                preferences[ENABLED_TOOLS_KEY] = existing + getDefaultTools()
                preferences[INITIALIZED_KEY] = true
            }
        }
    }

    suspend fun setToolEnabled(toolId: String, enabled: Boolean) {
        context.toolPreferencesDataStore.edit { preferences ->
            val currentSet = preferences[ENABLED_TOOLS_KEY] ?: emptySet()
            if (enabled) {
                preferences[ENABLED_TOOLS_KEY] = currentSet + toolId
            } else {
                preferences[ENABLED_TOOLS_KEY] = currentSet - toolId
            }
        }
    }

    suspend fun setAllToolsEnabled(toolIds: Set<String>, enabled: Boolean) {
        context.toolPreferencesDataStore.edit { preferences ->
            val currentSet = preferences[ENABLED_TOOLS_KEY] ?: emptySet()
            if (enabled) {
                preferences[ENABLED_TOOLS_KEY] = currentSet + toolIds
            } else {
                preferences[ENABLED_TOOLS_KEY] = currentSet - toolIds
            }
        }
    }
}
