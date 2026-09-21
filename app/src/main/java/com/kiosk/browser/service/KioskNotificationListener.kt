package com.kiosk.browser.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.kiosk.browser.MainActivity

/**
 * Служба отслеживания системных Push-уведомлений (для сторонних приложений заказов).
 *
 * При поступлении Push-уведомления от любого приложения доставки/кассы
 * (например, Яндекс.Еда, Wolt, Kaspi, iiko, r_keeper и т.д.)
 * немедленно зажигает экран планшета.
 */
class KioskNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return

        // Игнорируем собственные уведомления Kiosk и системные сервисы
        if (pkg == packageName || pkg == "android" || pkg == "com.android.systemui") {
            return
        }

        Log.i("KioskNotificationListener", "Получено уведомление от $pkg -> пробуждение экрана планшета!")
        MainActivity.currentInstance?.wakeUpFromScreensaver()
    }
}
