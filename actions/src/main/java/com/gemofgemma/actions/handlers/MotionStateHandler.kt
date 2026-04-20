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
import kotlin.math.sqrt
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class MotionStateHandler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun execute(): ActionResult {
        return try {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                ?: return ActionResult.Error("No accelerometer available on this device")

            val magnitude = withTimeoutOrNull(3000L) {
                suspendCancellableCoroutine<Float> { cont ->
                    val listener = object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent) {
                            sensorManager.unregisterListener(this)
                            val x = event.values[0]
                            val y = event.values[1]
                            val z = event.values[2]
                            cont.resume(sqrt(x * x + y * y + z * z))
                        }
                        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                    }
                    sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
                    cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
                }
            } ?: return ActionResult.Error("Accelerometer timed out")

            val state = if (magnitude in 8.8f..10.8f) "stationary" else "moving"

            ActionResult.Success("Motion state: $state (acceleration magnitude: ${"%.1f".format(magnitude)} m/s²)")
        } catch (e: Exception) {
            ActionResult.Error("Failed to read motion state: ${e.message}", e)
        }
    }
}
