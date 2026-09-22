package com.kiosk.browser

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import com.kiosk.browser.admin.DeviceOwnerManager
import com.kiosk.browser.data.repository.ConfigRepository
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date
import kotlin.system.exitProcess

class KioskApp : Application() {

    lateinit var configRepository: ConfigRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        setupCrashWatchdog()
        configRepository = ConfigRepository(this)
        createNotificationChannel()

        // Восстановление Kiosk лаунчера по умолчанию при старте приложения / системы
        try {
            val config = configRepository.getConfig()
            val isKioskDesired = config.isKioskEnabled &&
                    (config.preferredLauncherPackage.isEmpty() || config.preferredLauncherPackage == packageName)
            val dom = DeviceOwnerManager(this)
            if (isKioskDesired && dom.isDeviceOwner && !dom.isDefaultLauncher()) {
                dom.setDefaultLauncher(true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Сторож от падений: при непредвиденном сбое не показывает системное окно ошибки,
     * а автоматически перезапускает Kiosk Browser через AlarmManager.
     */
    private fun setupCrashWatchdog() {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                // 1. Логируем стек-трейс ошибки для администратора
                val logFile = File(filesDir, "crash_log.txt")
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val crashReport = "=== CRASH AT ${Date()} ===\nThread: ${thread.name}\n${sw}\n========================\n\n"
                logFile.appendText(crashReport)
            } catch (_: Exception) {}

            try {
                // 2. Планируем мгновенный перезапуск MainActivity
                val restartIntent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("EXTRA_RESTARTED_AFTER_CRASH", true)
                }
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    9999,
                    restartIntent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                    } else {
                        PendingIntent.FLAG_ONE_SHOT
                    }
                )
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                if (alarmManager != null) {
                    val restartTime = System.currentTimeMillis() + 800
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, restartTime, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, restartTime, pendingIntent)
                    }
                }
            } catch (_: Exception) {}

            // 3. Завершаем упавший процесс
            Process.killProcess(Process.myPid())
            exitProcess(2)
        }
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
