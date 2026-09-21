package com.kiosk.browser.core.power

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Детектор воспроизведения аудио (звуковых сигналов нового заказа).
 *
 * Перехватывает системные звуковые оповещения как из WebView (HTML5 Audio, Web Audio),
 * так и из любых сторонних Android-приложений заказов (Yandex Eda, Kaspi, iiko, r_keeper, Wolt и т.д.).
 * При начале воспроизведения звука мгновенно вызывает [onOrderSoundDetected].
 */
class AudioOrderDetector(
    private val context: Context,
    private val onOrderSoundDetected: () -> Unit
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var callback: AudioManager.AudioPlaybackCallback? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastTriggerTime: Long = 0L

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null) {
            callback = object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>?) {
                    super.onPlaybackConfigChanged(configs)
                    if (configs.isNullOrEmpty()) return

                    val now = System.currentTimeMillis()
                    // Защита от спама: не чаще одного раза в 1.5 секунды
                    if (now - lastTriggerTime > 1500L) {
                        lastTriggerTime = now
                        Log.i("AudioOrderDetector", "Звуковой сигнал заказа зафиксирован -> пробуждение экрана!")
                        handler.post {
                            onOrderSoundDetected()
                        }
                    }
                }
            }
            try {
                audioManager.registerAudioPlaybackCallback(callback!!, handler)
                Log.i("AudioOrderDetector", "AudioPlaybackCallback зарегистрирован успешно")
            } catch (e: Exception) {
                Log.e("AudioOrderDetector", "Ошибка регистрации AudioPlaybackCallback: ${e.message}")
            }
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioManager != null && callback != null) {
            try {
                audioManager.unregisterAudioPlaybackCallback(callback!!)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            callback = null
        }
    }
}
