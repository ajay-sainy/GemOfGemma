package com.gemofgemma.actions.handlers

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmActionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(functionName: String, params: Map<String, Any>): ActionResult {
        return when (functionName) {
            "setAlarm" -> setAlarm(params)
            "setTimer" -> setTimer(params)
            else -> ActionResult.Error("Unknown alarm action: $functionName")
        }
    }

    private fun setAlarm(params: Map<String, Any>): ActionResult {
        val hour = (params["hour"] as? Number)?.toInt()
            ?: return ActionResult.Error("Missing hour parameter")
        val minutes = (params["minutes"] as? Number)?.toInt()
            ?: return ActionResult.Error("Missing minutes parameter")
        val label = params["label"] as? String ?: "Alarm"

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Alarm set for $hour:${minutes.toString().padStart(2, '0')}")
        } catch (e: Exception) {
            ActionResult.Error("Failed to set alarm: ${e.message}", e)
        }
    }

    private fun setTimer(params: Map<String, Any>): ActionResult {
        val durationSeconds = (params["durationSeconds"] as? Number)?.toInt()
            ?: return ActionResult.Error("Missing durationSeconds parameter")
        val label = params["label"] as? String ?: "Timer"

        return try {
            val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                putExtra(AlarmClock.EXTRA_LENGTH, durationSeconds)
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionResult.Success("Timer set for ${durationSeconds}s")
        } catch (e: Exception) {
            ActionResult.Error("Failed to set timer: ${e.message}", e)
        }
    }
}
