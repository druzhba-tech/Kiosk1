package com.kiosk.browser.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kiosk.browser.KioskApp
import com.kiosk.browser.MainActivity
import com.kiosk.browser.network.mqtt.HomeAssistantDiscovery
import com.kiosk.browser.network.mqtt.KioskMqttClient
import com.kiosk.browser.network.server.RemoteAdminServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class KioskForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var mqttClient: KioskMqttClient? = null
    private var adminServer: RemoteAdminServer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        initServices()
    }

    private fun initServices() {
        val config = KioskApp.instance.configRepository.getConfig()

        // 1. MQTT клиент
        mqttClient = KioskMqttClient { command, payload ->
            when (command) {
                "screen" -> MainActivity.currentInstance?.controlScreen(payload.equals("ON", ignoreCase = true))
                "reload" -> MainActivity.currentInstance?.reloadCurrentPage()
                "url" -> MainActivity.currentInstance?.loadUrl(payload)
                "reboot" -> MainActivity.currentInstance?.deviceOwnerManager?.rebootDevice()
            }
        }
        if (config.mqttEnabled) {
            mqttClient?.connect(config)
            HomeAssistantDiscovery.publishAllEntities(mqttClient!!, config)
        }

        // 2. Локальный HTTP сервер
        adminServer = RemoteAdminServer(config.remoteAdminPort) { turnOn ->
            MainActivity.currentInstance?.controlScreen(turnOn)
        }
        adminServer?.start()

        // 3. Периодическая отправка телеметрии
        serviceScope.launch {
            while (isActive) {
                delay(30_000L) // каждые 30 сек
                publishTelemetry()
            }
        }

        // 4. Проверка OTA-обновлений
        if (config.updateCheckEnabled && config.updateManifestUrl.isNotBlank()) {
            val intervalMs = config.updateCheckIntervalHours.toLong() * 3_600_000L
            KioskApp.instance.updateManager.startPeriodicCheck(
                manifestUrl = config.updateManifestUrl,
                intervalMs = intervalMs
            )
        }
    }

    private fun publishTelemetry() {
        val main = MainActivity.currentInstance ?: return
        val config = KioskApp.instance.configRepository.getConfig()
        if (!config.mqttEnabled) return

        val battery = main.batteryTracker.batteryLevel.value
        val isCharging = main.batteryTracker.isCharging.value
        val temp = main.batteryTracker.batteryTemperature.value
        val isScreenOn = !main.isScreensaverActive.value

        val stateJson = """
            {
                "battery": $battery,
                "is_charging": $isCharging,
                "temperature": $temp,
                "screen_state": "${if (isScreenOn) "ON" else "OFF"}"
            }
        """.trimIndent()

        mqttClient?.publishState(config.mqttTopicPrefix, stateJson)
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, KioskApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Kiosk Browser Service")
            .setContentText("Мониторинг IoT, MQTT и веб-сервер активны")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttClient?.disconnect()
        adminServer?.stop()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
