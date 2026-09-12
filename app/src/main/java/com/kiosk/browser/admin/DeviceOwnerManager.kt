package com.kiosk.browser.admin

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager

class DeviceOwnerManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, KioskDeviceAdminReceiver::class.java)

    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(context.packageName)

    val isAdminActive: Boolean
        get() = dpm.isAdminActive(adminComponent)

    /**
     * Применение политик жесткой защиты (Safe Mode, USB, статус-бар)
     */
    fun applyKioskPolicies(
        blockSafeMode: Boolean,
        blockUsb: Boolean,
        disableStatusBar: Boolean
    ) {
        if (!isDeviceOwner) return

        try {
            // Разрешаем текущему приложению использовать LockTaskMode без диалогов
            dpm.setLockTaskPackages(adminComponent, arrayOf(context.packageName))

            // Блокировка безопасного режима Safe Mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (blockSafeMode) {
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                } else {
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                }
            }

            // Блокировка USB
            if (blockUsb) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            }

            // Полное отключение статус-бара (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setStatusBarDisabled(adminComponent, disableStatusBar)
            }

            // Настройка фич LockTask (блокировка системных диалогов выключения)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Снятие блокировок перед выходом из режима киоска
     */
    fun clearKioskPolicies() {
        if (!isDeviceOwner) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setStatusBarDisabled(adminComponent, false)
            }
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Удаленная перезагрузка (только для Device Owner)
     */
    fun rebootDevice(): Boolean {
        if (!isDeviceOwner) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.reboot(adminComponent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
