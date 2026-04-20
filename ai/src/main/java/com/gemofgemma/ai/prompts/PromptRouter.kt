package com.gemofgemma.ai.prompts

import com.gemofgemma.core.model.AiRequest
import com.gemofgemma.core.data.ToolPreferencesRepository
import com.gemofgemma.core.model.ToolDefinition
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes each [AiRequest] type to the correct system prompt template.
 * Tool definitions are registered natively via LiteRT-LM's ToolSet API;
 * the prompt only lists which tools the user has enabled.
 */
@Singleton
class PromptRouter @Inject constructor(
    private val toolPreferencesRepository: ToolPreferencesRepository
) {

    suspend fun getSystemPrompt(request: AiRequest): String {
        val enabledTools = getEnabledTools()
        return PromptTemplates.buildSystemPrompt(enabledTools)
    }

    private suspend fun getEnabledTools(): List<ToolDefinition> {
        val enabledToolIds = toolPreferencesRepository.enabledToolsFlow.first()
        return ToolDefinition.ALL_TOOLS.filter { enabledToolIds.contains(it.id) }
    }
}
