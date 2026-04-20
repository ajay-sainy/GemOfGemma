package com.gemofgemma.actions.handlers

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarActionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(params: Map<String, Any>): ActionResult {
        val title = params["title"] as? String
            ?: return ActionResult.Error("Missing title parameter")
        val startTime = params["startTime"] as? String
            ?: return ActionResult.Error("Missing startTime parameter")
        val endTime = params["endTime"] as? String ?: startTime
        val location = params["location"] as? String ?: ""

        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            val startMillis = dateFormat.parse(startTime)?.time
                ?: return ActionResult.Error("Invalid startTime format. Use yyyy-MM-dd'T'HH:mm")
            val endMillis = dateFormat.parse(endTime)?.time
                ?: (startMillis + 3_600_000L) // Default 1 hour

            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = CalendarContract.Events.CONTENT_URI
                putExtra(CalendarContract.Events.TITLE, title)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                putExtra(CalendarContract.Events.EVENT_LOCATION, location)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Calendar event '$title' created")
        } catch (e: Exception) {
            ActionResult.Error("Failed to create calendar event: ${e.message}", e)
        }
    }
}
