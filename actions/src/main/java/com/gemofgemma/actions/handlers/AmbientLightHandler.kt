package com.gemofgemma.actions.handlers

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.gemofgemma.core.model.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class AmbientLightHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(): ActionResult {
        return try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
                ?: return ActionResult.Error("No light sensor available on this device")

            val lux = withTimeoutOrNull(3000L) {
                suspendCancellableCoroutine<Float> { cont ->
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            sensorManager.unregisterListener(this)
                            cont.resume(event.values[0])
                        }
                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }
                    sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
                    cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
                }
            } ?: return ActionResult.Error("Light sensor timed out")

            val description = when {
                lux < 10 -> "dark"
                lux < 100 -> "dim"
                lux < 1000 -> "bright"
                else -> "very bright"
            }

            ActionResult.Success("Ambient light: ${lux} lux ($description)")
        } catch (e: Exception) {
            ActionResult.Error("Failed to read ambient light: ${e.message}", e)
        }
    }
}
