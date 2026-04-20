package com.gemofgemma.actions.handlers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryActionHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun executeBatteryDiagnostics(): ActionResult {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

            val batteryPct = if (scale > 0) (level * 100 / scale.toFloat()) else -1
            val tempCelsius = temperature / 10.0
            
            ActionResult.Success("Battery: ${batteryPct}%, Temp: ${tempCelsius}C, Charging: $isCharging")
        } catch (e: Exception) {
            ActionResult.Error("Failed battery diagnostics: ${e.message}", e)
        }
    }

    suspend fun executeNetworkInfo(): ActionResult {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            ActionResult.Success("Network: Connected=$isConnected, WiFi=$isWifi, Cellular=$isCellular")
        } catch (e: Exception) {
            ActionResult.Error("Failed to get network info: ${e.message}", e)
        }
    }

    suspend fun executeStorageMemory(): ActionResult {
        return try {
            val freeSpace = Environment.getDataDirectory().usableSpace
            val totalSpace = Environment.getDataDirectory().totalSpace
            
            val freeGb = freeSpace / (1024 * 1024 * 1024)
            val totalGb = totalSpace / (1024 * 1024 * 1024)
            
            ActionResult.Success("Storage: ${freeGb}GB free of ${totalGb}GB total")
        } catch (e: Exception) {
            ActionResult.Error("Failed storage diagnostics: ${e.message}", e)
        }
    }
}
