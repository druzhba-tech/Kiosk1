package com.kiosk.browser.core.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class MotionSensorTracker(
    context: Context,
    private val onAntiTheftTriggered: () -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _lightLevel = MutableStateFlow(100f)
    val lightLevel: StateFlow<Float> = _lightLevel.asStateFlow()

    private val _isDeviceMoved = MutableStateFlow(false)
    val isDeviceMoved: StateFlow<Boolean> = _isDeviceMoved.asStateFlow()

    var isAntiTheftEnabled: Boolean = false
    var sensitivityThreshold: Float = 3.0f

    private var lastAccelX = 0f
    private var lastAccelY = 0f
    private var lastAccelZ = 0f
    private var isFirstAccel = true

    fun start() {
        // Регистрируем акселерометр только если включена антикражная сигнализация,
        // чтобы не расходовать батарею постоянной обработкой прерываний
        if (isAntiTheftEnabled) {
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
        // Датчик света с медленной частотой обновления
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                if (!isAntiTheftEnabled) return

                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                if (isFirstAccel) {
                    lastAccelX = x
                    lastAccelY = y
                    lastAccelZ = z
                    isFirstAccel = false
                    return
                }

                val deltaX = Math.abs(lastAccelX - x)
                val deltaY = Math.abs(lastAccelY - y)
                val deltaZ = Math.abs(lastAccelZ - z)
                val totalDelta = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()).toFloat()

                lastAccelX = x
                lastAccelY = y
                lastAccelZ = z

                if (totalDelta > sensitivityThreshold) {
                    _isDeviceMoved.value = true
                    onAntiTheftTriggered()
                }
            }

            Sensor.TYPE_LIGHT -> {
                _lightLevel.value = event.values[0]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}