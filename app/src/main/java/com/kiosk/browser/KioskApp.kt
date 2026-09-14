package com.kiosk.browser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.kiosk.browser.core.update.UpdateManager
import com.kiosk.browser.data.repository.ConfigRepository

class KioskApp : Application() {

    lateinit var configRepository: ConfigRepository
        private set

    lateinit var updateManager: UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        configRepository = ConfigRepository(this)
        updateManager = UpdateManager(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Kiosk System Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Поддержание работы MQTT, датчиков и веб-сервера киоска"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "kiosk_service_channel"
        lateinit var instance: KioskApp
            private set
    }
}
