package com.gemofgemma.actions.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLaunchHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(params: Map<String, Any>): ActionResult {
        val appName = params["appName"] as? String
            ?: return ActionResult.Error("Missing appName parameter")

        return try {
            val pm = context.packageManager
            val intent = findAppLaunchIntent(pm, appName)
                ?: return ActionResult.Error("App '$appName' not found")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ActionResult.Success("Opened $appName")
        } catch (e: Exception) {
            ActionResult.Error("Failed to open app: ${e.message}", e)
        }
    }

    private fun findAppLaunchIntent(pm: PackageManager, appName: String): Intent? {
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val matchingApp = installedApps.firstOrNull { app ->
            val label = pm.getApplicationLabel(app).toString()
            label.equals(appName, ignoreCase = true)
        }
        return matchingApp?.let { pm.getLaunchIntentForPackage(it.packageName) }
    }
}
