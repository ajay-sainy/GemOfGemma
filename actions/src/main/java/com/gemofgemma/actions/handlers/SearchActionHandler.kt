package com.gemofgemma.actions.handlers

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchActionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(params: Map<String, Any>): ActionResult {
        val query = params["query"] as? String
            ?: return ActionResult.Error("Missing query parameter")

        return try {
            val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, query)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                context.startActivity(intent)
                ActionResult.Success("Searching the web for '\${query}'")
            } catch (e: ActivityNotFoundException) {
                // Fallback to standard web URI if Google app or Search handlers aren't found/visible
                val fallbackIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=\${Uri.encode(query)}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                ActionResult.Success("Searching the web for '\${query}'")
            }
        } catch (e: Exception) {
            ActionResult.Error("Failed to start web search: \${e.message}", e)
        }
    }
}
