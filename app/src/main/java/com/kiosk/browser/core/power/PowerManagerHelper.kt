package com.kiosk.browser.core.power

import android.app.Activity
import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.view.WindowManager

class PowerManagerHelper(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * Захватывать локи только по необходимости, а не держать постоянную нагрузку на аккумулятор
     */
    fun acquireLocks() {
        try {
            if (wifiLock == null) {
                // Используем энергоэффективный режим вместо агрессивного постоянного High Perf
                @Suppress("DEPRECATION")
                wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "Kiosk:WifiLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld != true) {
                wifiLock?.acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            if (wifiLock?.isHeld == true) wifiLock?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setKeepScreenOn(activity: Activity, keepOn: Boolean) {
        activity.runOnUiThread {
            if (keepOn) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    /**
     * Снижение яркости до абсолютного минимума в режиме виртуального сна,
     * что экономит до 85% энергии подсветки экрана
     */
        /**
     * Ручная установка рабочей яркости дисплея (от 0.05 до 1.0)
     */
    fun setScreenBrightness(activity: Activity, brightness: Float) {
        activity.runOnUiThread {
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = brightness.coerceIn(0.05f, 1.0f)
            activity.window.attributes = layoutParams
        }
    }

    fun setVirtualSleepBrightness(activity: Activity, sleep: Boolean) {
        activity.runOnUiThread {
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = if (sleep) 0.005f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = layoutParams
        }
    }

    /**
     * Аппаратное пробуждение дисплея (зажигание экрана) при поступлении заказа
     */
    fun wakeUpScreenInstantly(activity: Activity) {
        try {
            @Suppress("DEPRECATION")
            val screenWakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "kiosk:OrderInstantWake"
            )
            screenWakeLock.acquire(3000L)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        activity.runOnUiThread {
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = layoutParams
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                )
            }
        }
    }
}