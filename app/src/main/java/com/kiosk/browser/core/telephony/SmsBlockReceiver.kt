package com.kiosk.browser.core.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Перехватчик входящих SMS-сообщений.
 * Предотвращает появление всплывающих уведомлений, звуков и спам-сообщений на экране киоска.
 */
class SmsBlockReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            Log.i("SmsBlockReceiver", "Перехвачено входящее SMS-сообщение. Прерывание широковещания...")
            try {
                // Прерываем дальнейшее распространение сообщения в систему
                abortBroadcast()
            } catch (e: Exception) {
                Log.w("SmsBlockReceiver", "Не удалось прервать broadcast SMS: ${e.message}")
            }
        }
    }
}
