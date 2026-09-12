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

    init {
        // Удержание Wi-Fi в активном состоянии при 24/7 работе
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Kiosk:WifiLock").apply {
            setReferenceCounted(false)
        }
    }

    fun acquireLocks() {
        try {
            if (wakeLock == null) {
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Kiosk:PartialWakeLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(10 * 60 * 1000L /* 10 min safe timeout or hold */)
            wifiLock?.acquire()
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

    /**
     * Включение флага KeepScreenOn для Activity
     */
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
     * Виртуальный сон: уменьшение яркости экрана до 0 (без выключения ОС и блокировки)
     */
    fun setVirtualSleepBrightness(activity: Activity, sleep: Boolean) {
        activity.runOnUiThread {
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = if (sleep) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = layoutParams
        }
    }
}
