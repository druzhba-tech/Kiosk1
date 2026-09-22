package com.kiosk.browser.admin

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
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
        disableStatusBar: Boolean,
        blockCallsAndSms: Boolean = true,
        blockTethering: Boolean = true,
        whitelistedPackages: List<String> = emptyList()
    ) {
        if (!isDeviceOwner) return

        try {
            val defaultWhitelist = listOf(
                context.packageName,
                "com.android.settings",
                "com.google.android.settings",
                "com.android.settings.intelligence",
                "com.android.packageinstaller",
                "com.google.android.packageinstaller"
            )
            val allPackages = (defaultWhitelist + whitelistedPackages)
                .filter { it.isNotBlank() }
                .distinct()
                .toTypedArray()

            dpm.setLockTaskPackages(adminComponent, allPackages)

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

            if (blockCallsAndSms) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_OUTGOING_CALLS)
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_SMS)
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_OUTGOING_CALLS)
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SMS)
            }

            if (blockTethering) {
                dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_TETHERING)
            } else {
                dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_TETHERING)
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

            // Защита системных настроек: блокируем доступ к Bluetooth, приложениям, аккаунтам и сбросу
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_BLUETOOTH)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)

            // Автоматически включаем ограничения для сторонних приложений (кроме разрешенных)
            enforceStrictBackgroundRestrictions(listOf(context.packageName) + whitelistedPackages)
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
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_BLUETOOTH)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_APPS_CONTROL)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_MODIFY_ACCOUNTS)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_OUTGOING_CALLS)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_SMS)
            dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_TETHERING)

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

    fun setPreferredLauncher(packageName: String, activityName: String) {
        if (!isDeviceOwner) return
        try {
            // Очищаем текущий Kiosk
            dpm.clearPackagePersistentPreferredActivities(adminComponent, context.packageName)
            // Очищаем целевой перед повторным назначением
            dpm.clearPackagePersistentPreferredActivities(adminComponent, packageName)

            val targetComponent = ComponentName(packageName, activityName)
            try {
                val filter = android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_HOME)
                    addCategory(android.content.Intent.CATEGORY_DEFAULT)
                }
                dpm.addPersistentPreferredActivity(adminComponent, filter, targetComponent)
            } catch (_: Exception) {
                val simpleFilter = android.content.IntentFilter(android.content.Intent.ACTION_MAIN).apply {
                    addCategory(android.content.Intent.CATEGORY_HOME)
                }
                dpm.addPersistentPreferredActivity(adminComponent, simpleFilter, targetComponent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearDefaultLauncher() {
        if (!isDeviceOwner) return
        try {
            dpm.clearPackagePersistentPreferredActivities(adminComponent, context.packageName)
            val current = getCurrentDefaultLauncher()
            if (current != null && current.packageName != context.packageName) {
                dpm.clearPackagePersistentPreferredActivities(adminComponent, current.packageName)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCurrentDefaultLauncher(): ComponentName? {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
        }
        val resolveInfo = context.packageManager.resolveActivity(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val pkg = resolveInfo?.activityInfo?.packageName
        // Если "android" или ResolverActivity - это системный диалог выбора, а не конкретный лаунчер
        if (pkg == null || pkg == "android" || pkg.contains("resolver", ignoreCase = true)) {
            return null
        }
        return resolveInfo.activityInfo?.let {
            ComponentName(it.packageName, it.name)
        }
    }

    fun isDefaultLauncher(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                return roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)
            }
        }
        val current = getCurrentDefaultLauncher()
        return current?.packageName == context.packageName
    }

    fun getInstalledLaunchers(): List<LauncherAppInfo> {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
        }
        val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }

        val currentDefault = getCurrentDefaultLauncher()

        return resolveList.mapNotNull { resolveInfo ->
            val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
            val pkg = activityInfo.packageName
            // Исключаем системный резолвер выбора
            if (pkg == "android" || pkg.contains("resolver", ignoreCase = true)) return@mapNotNull null

            val label = runCatching { resolveInfo.loadLabel(pm).toString() }.getOrNull()?.ifBlank { pkg } ?: pkg
            val icon = runCatching { resolveInfo.loadIcon(pm) }.getOrNull()
            val isDefault = currentDefault?.packageName == pkg
            val isKiosk = pkg == context.packageName

            LauncherAppInfo(
                label = label,
                packageName = pkg,
                activityName = activityInfo.name,
                icon = icon,
                isCurrentDefault = isDefault,
                isKiosk = isKiosk
            )
        }.distinctBy { it.packageName }
        .sortedWith(compareByDescending<LauncherAppInfo> { it.isKiosk }.thenBy { it.label.lowercase() })
    }

    fun requestDefaultLauncher(activity: android.app.Activity) {
        if (isDeviceOwner) {
            setDefaultLauncher(true)
            android.widget.Toast.makeText(activity, "Kiosk установлен главным лаунчером", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        try {
            activity.stopLockTask()
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(android.app.role.RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
                    try {
                        @Suppress("DEPRECATION")
                        activity.startActivityForResult(intent, 1001)
                        return
                    } catch (_: Exception) {}
                }
            }
        }
        openHomeSettings(activity)
    }

    fun openHomeSettings(activity: android.app.Activity) {
        try {
            activity.stopLockTask()
        } catch (_: Exception) {}

        val intents = listOf(
            android.content.Intent(android.provider.Settings.ACTION_HOME_SETTINGS),
            android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            android.content.Intent(android.provider.Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                activity.startActivity(intent)
                return
            } catch (_: Exception) {}
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

data class LauncherAppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable?,
    val isCurrentDefault: Boolean,
    val isKiosk: Boolean
)