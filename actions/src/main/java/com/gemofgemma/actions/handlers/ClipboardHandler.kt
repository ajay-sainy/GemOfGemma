package com.gemofgemma.actions.handlers

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ClipboardHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun executeRead(): ActionResult {
        return withContext(Dispatchers.Main) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip == null || clip.itemCount == 0) {
                    ActionResult.Success("Clipboard is empty")
                } else {
                    val text = clip.getItemAt(0).coerceToText(context).toString()
                    ActionResult.Success("Clipboard contents: $text")
                }
            } catch (e: Exception) {
                ActionResult.Error("Failed to read clipboard: ${e.message}", e)
            }
        }
    }

    suspend fun executeWrite(params: Map<String, Any>): ActionResult {
        return withContext(Dispatchers.Main) {
            try {
                val text = params["text"]?.toString()
                    ?: return@withContext ActionResult.Error("Missing 'text' parameter for clipboard write")
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("gemofgemma", text)
                clipboard.setPrimaryClip(clip)
                ActionResult.Success("Copied to clipboard: $text")
            } catch (e: Exception) {
                ActionResult.Error("Failed to write to clipboard: ${e.message}", e)
            }
        }
    }
}
