package com.kiosk.browser.core.security

import android.os.SystemClock

/**
 * Детектор скрытого жеста: 5 быстрых тапов в верхнем правом углу экрана
 */
class SecretGestureDetector(
    private val requiredTaps: Int = 5,
    private val timeWindowMs: Long = 2500L,
    private val onGestureDetected: () -> Unit
) {
    private var tapCount = 0
    private var firstTapTime = 0L

    fun onSecretAreaTapped() {
        val now = SystemClock.uptimeMillis()
        if (tapCount == 0 || (now - firstTapTime) > timeWindowMs) {
            tapCount = 1
            firstTapTime = now
        } else {
            tapCount++
            if (tapCount >= requiredTaps) {
                tapCount = 0
                onGestureDetected()
            }
        }
    }

    fun reset() {
        tapCount = 0
        firstTapTime = 0L
    }
}
