package com.kiosk.browser.core.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.kiosk.browser.KioskApp
import com.kiosk.browser.MainActivity

class EmergencyBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = KioskApp.instance
        val config = app.configRepository.getConfig()

        when (intent.action) {
            "com.kiosk.RESET_LOCK" -> {
                val key = intent.getStringExtra("key")
                if (key == config.emergencyAdbKey) {
                    // Аварийный сброс блокировки
                    app.configRepository.updateConfig {
                        it.copy(
                            isKioskEnabled = false,
                            pinCode = "1234"
                        )
                    }
                    MainActivity.currentInstance?.exitKioskMode()
                    Toast.makeText(context, "KIOSK: Аварийный сброс выполнен! PIN: 1234", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "KIOSK: Неверный аварийный ключ!", Toast.LENGTH_SHORT).show()
                }
            }

            "com.kiosk.RELOAD_PAGE" -> {
                MainActivity.currentInstance?.reloadCurrentPage()
            }

            "com.kiosk.SET_URL" -> {
                val url = intent.getStringExtra("url")
                if (!url.isNullOrBlank()) {
                    app.configRepository.updateConfig { it.copy(startUrl = url) }
                    MainActivity.currentInstance?.loadUrl(url)
                }
            }
        }
    }
}
