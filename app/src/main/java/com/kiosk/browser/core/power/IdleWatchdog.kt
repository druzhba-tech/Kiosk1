package com.kiosk.browser.core.power

import android.os.Handler
import android.os.Looper

class IdleWatchdog(
    private val onIdleTimeout: () -> Unit,
    private val onUserActive: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var timeoutMs: Long = 120_000L
    private var isIdle = false
    private var isEnabled = true

    private val idleRunnable = Runnable {
        if (isEnabled && !isIdle) {
            isIdle = true
            onIdleTimeout()
        }
    }

    fun updateTimeoutSeconds(seconds: Int, enabled: Boolean) {
        this.isEnabled = enabled
        this.timeoutMs = (seconds * 1000L).coerceAtLeast(5000L)
        resetTimer()
    }

    fun notifyUserActivity() {
        if (isIdle) {
            isIdle = false
            onUserActive()
        }
        resetTimer()
    }

    fun resetTimer() {
        handler.removeCallbacks(idleRunnable)
        if (isEnabled) {
            handler.postDelayed(idleRunnable, timeoutMs)
        }
    }

    fun stop() {
        handler.removeCallbacks(idleRunnable)
    }
}
