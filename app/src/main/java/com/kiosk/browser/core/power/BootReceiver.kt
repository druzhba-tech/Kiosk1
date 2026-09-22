package com.kiosk.browser.core.power

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kiosk.browser.MainActivity
import com.kiosk.browser.admin.DeviceOwnerManager
import com.kiosk.browser.data.repository.ConfigRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            try {
                val configRepo = ConfigRepository(context)
                val config = configRepo.getConfig()
                val isKioskDesired = config.isKioskEnabled &&
                        (config.preferredLauncherPackage.isEmpty() || config.preferredLauncherPackage == context.packageName)

                // Если Kiosk должен быть активным лаунчером и мы Device Owner - гарантированно закрепляем его
                val deviceOwnerManager = DeviceOwnerManager(context)
                if (isKioskDesired && deviceOwnerManager.isDeviceOwner) {
                    deviceOwnerManager.setDefaultLauncher(true)
                }

                // Если режим киоска включен - автоматически запускаем Kiosk Browser
                if (config.isKioskEnabled) {
                    val launchIntent = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    try {
                        context.startActivity(launchIntent)
                    } catch (_: Exception) {
                        // Обход ограничений запуска Activity из фона в Android 10+ через PendingIntent
                        try {
                            val pi = PendingIntent.getActivity(
                                context,
                                0,
                                launchIntent,
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                } else {
                                    PendingIntent.FLAG_UPDATE_CURRENT
                                }
                            )
                            pi.send()
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
