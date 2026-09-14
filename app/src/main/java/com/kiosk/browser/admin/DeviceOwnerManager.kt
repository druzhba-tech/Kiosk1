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
     * Запрос прав администратора устройства через системный диалог
     */
    fun requestDeviceAdmin(activity: android.app.Activity) {
        val intent = android.content.Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Необходимо для включения блокировки режима киоска."
            )
        }
        activity.startActivity(intent)
    }

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
     * Установка/снятие постоянного лаунчера по умолчанию (требует Device Owner)
     */
    fun setDefaultLauncher(enable: Boolean) {
        if (!isDeviceOwner) return
        try {
            val filter = android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
                addCategory(android.content.Intent.CATEGORY_DEFAULT)
            }
            val mainActivityComponent = ComponentName(context, "com.kiosk.browser.MainActivity")
            if (enable) {
                dpm.addPersistentPreferredActivity(adminComponent, filter, mainActivityComponent)
            } else {
                dpm.clearPackagePersistentPreferredActivities(adminComponent, context.packageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Проверка, является ли Kiosk Gusar лаунчером (домашним экраном) по умолчанию
     */
    fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
            }
        }
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(
            intent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    /**
     * Запрос на установку лаунчером по умолчанию в 1 клик (через системный диалог RoleManager)
     */
    fun requestDefaultLauncher(activity: android.app.Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
                    activity.startActivity(intent)
                    return
                }
            }
        }

        // Fallback для Android 9 и ниже
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
            activity.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                activity.startActivity(intent)
            } catch (e2: Exception) {
                val intent = android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
                activity.startActivity(intent)
            }
        }
    }

    /**
     * Перезагрузка устройства (требует Device Owner, Android 7.0+)
     */
    fun rebootDevice() {
        if (!isDeviceOwner) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.reboot(adminComponent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
