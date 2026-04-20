package com.gemofgemma.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gemofgemma.core.data.ToolPreferencesRepository
import com.gemofgemma.core.model.ToolDefinition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val toolPreferencesRepository: ToolPreferencesRepository
) : ViewModel() {

    val enabledTools: StateFlow<Set<String>> = toolPreferencesRepository.enabledToolsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    fun toggleTool(tool: ToolDefinition, enabled: Boolean) {
        viewModelScope.launch {
            toolPreferencesRepository.setToolEnabled(tool.id, enabled)
        }
    }
}
