package com.kiosk.browser.core.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log

/**
 * Блокировщик входящих телефонных вызовов.
 * Предотвращает прерывание работы киоска/экрана кухни входящими звонками на SIM-карту.
 */
class CallBlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (state == TelephonyManager.EXTRA_STATE_RINGING) {
            Log.i("CallBlockReceiver", "Обнаружен входящий звонок. Выполняется немедленный сброс...")

            // 1. Заглушаем рингтон немедленно
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.ringerMode = AudioManager.RINGER_MODE_SILENT
            } catch (e: Exception) {
                Log.w("CallBlockReceiver", "Не удалось заглушить рингтон: ${e.message}")
            }

            // 2. Сброс вызова через TelecomManager (Android 9+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try {
                    val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    @Suppress("DEPRECATION")
                    telecomManager?.endCall()
                    Log.i("CallBlockReceiver", "Входящий вызов успешно сброшен через TelecomManager")
                    return
                } catch (e: Exception) {
                    Log.w("CallBlockReceiver", "TelecomManager.endCall error: ${e.message}")
                }
            }

            // 3. Сброс через рефлексию ITelephony для старых версий Android
            try {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val m = tm?.javaClass?.getDeclaredMethod("getITelephony")
                m?.isAccessible = true
                val telephonyService = m?.invoke(tm)
                val endCallMethod = telephonyService?.javaClass?.getDeclaredMethod("endCall")
                endCallMethod?.invoke(telephonyService)
                Log.i("CallBlockReceiver", "Входящий вызов сброшен через ITelephony reflection")
            } catch (e: Exception) {
                Log.w("CallBlockReceiver", "Reflection endCall error: ${e.message}")
            }
        }
    }
}
