package com.kiosk.browser.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.kiosk.browser.KioskApp

/**
 * BroadcastReceiver — получает результат Silent Install от PackageInstaller.
 * Должен быть зарегистрирован в AndroidManifest.xml.
 */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Unknown"

        Log.d(TAG, "Install result: status=$status, message=$message")

        val updateManager = (context.applicationContext as? KioskApp)?.updateManager ?: return

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "✅ Приложение успешно обновлено!")
                updateManager.notifyInstallSuccess()
            }
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // На не-Device Owner устройствах — запустить диалог установки
                val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirmIntent != null) {
                    context.startActivity(confirmIntent)
                }
            }
            else -> {
                Log.e(TAG, "❌ Ошибка установки: $message (status=$status)")
                updateManager.notifyInstallError("Ошибка: $message")
            }
        }
    }

    companion object {
        private const val TAG = "InstallResultReceiver"
    }
}
