package com.kiosk.browser.admin

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager

class DeviceOwnerManager(private val context: Context) {

    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, KioskDeviceAdminReceiver::class.java)

    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(context.packageName)

    val isAdminActive: Boolean
        get() = dpm.isAdminActive(adminComponent)

    fun requestDeviceAdmin(activity: android.app.Activity) {
        val intent = android.content.Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Необходимо для обеспечения режима киоска и блокировки фона."
            )
        }
        activity.startActivity(intent)
    }

    /**
     * Применение политик Kiosk: изоляция одного приложения, запрет Safe Mode, USB и шторки
     */
    fun applyKioskPolicies(
        blockSafeMode: Boolean,
        blockUsb: Boolean,
        disableStatusBar: Boolean
    ) {
        if (!isDeviceOwner) return

        try {
            dpm.setLockTaskPackages(
                adminComponent,
                arrayOf(
                    context.packageName,
                    "com.android.settings",
                    "com.google.android.settings",
                    "com.android.settings.intelligence"
                )
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (blockSafeMode) {
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                    dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                } else {
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
                    dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
                }
            }

            if (blockUsb) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setStatusBarDisabled(adminComponent, disableStatusBar)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(
                    adminComponent,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NONE
                )
            }

            // Автоматически включаем жесткие ограничения для сторонних приложений
            enforceStrictBackgroundRestrictions(listOf(context.packageName))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Заморозка (Suspend) всех сторонних приложений и очистка их фоновых процессов,
     * чтобы они вообще не могли просыпаться, слать уведомления или потреблять батарею.
     */
    fun enforceStrictBackgroundRestrictions(whitelistedPackages: List<String>) {
        if (!isDeviceOwner) return

        try {
            val pm = context.packageManager
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val packagesToSuspend = mutableListOf<String>()

            for (app in installedApps) {
                val pkg = app.packageName
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

                // Не трогаем себя и разрешенные приложения
                if (whitelistedPackages.contains(pkg) || pkg == context.packageName) {
                    continue
                }

                // Замораживаем несистемные пользовательские приложения (игры, соцсети, браузеры и т.д.)
                if (!isSystem || isUpdatedSystem) {
                    packagesToSuspend.add(pkg)
                    try {
                        am.killBackgroundProcesses(pkg)
                    } catch (_: Exception) {}
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && packagesToSuspend.isNotEmpty()) {
                dpm.setPackagesSuspended(adminComponent, packagesToSuspend.toTypedArray(), true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Снятие ограничений фоновых процессов при выходе из киоска
     */
    fun clearKioskPolicies() {
        if (!isDeviceOwner) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                dpm.setStatusBarDisabled(adminComponent, false)
            }
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_USB_FILE_TRANSFER)

            // Разморозка приложений
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val pm = context.packageManager
                val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val suspended = installedApps.map { it.packageName }.toTypedArray()
                dpm.setPackagesSuspended(adminComponent, suspended, false)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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