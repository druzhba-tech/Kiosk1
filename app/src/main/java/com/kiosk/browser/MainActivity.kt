package com.kiosk.browser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiosk.browser.ui.theme.*
import com.kiosk.browser.core.update.KioskUpdateManager
import com.kiosk.browser.core.update.UpdateState
import com.kiosk.browser.ui.components.FirstRunSetupDialog
import com.kiosk.browser.ui.screens.PrimaryAppKioskScreen
import com.kiosk.browser.ui.components.UpdateDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kiosk.browser.admin.DeviceOwnerManager
import com.kiosk.browser.core.nfc.KioskNfcManager
import com.kiosk.browser.core.power.BatteryTracker
import com.kiosk.browser.core.power.IdleWatchdog
import com.kiosk.browser.core.power.PowerManagerHelper
import com.kiosk.browser.core.power.AudioOrderDetector
import com.kiosk.browser.core.security.SecretGestureDetector
import com.kiosk.browser.core.sensors.MotionSensorTracker
import com.kiosk.browser.service.KioskForegroundService
import com.kiosk.browser.ui.components.PinAuthDialog
import com.kiosk.browser.ui.screens.AppLauncherScreen
import com.kiosk.browser.ui.screens.KioskWebScreen
import com.kiosk.browser.ui.screens.SettingsScreen
import com.kiosk.browser.ui.screens.TechScreensaver
import com.kiosk.browser.ui.theme.KioskBrowserTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    val configRepository by lazy { (application as KioskApp).configRepository }
    val deviceOwnerManager by lazy { DeviceOwnerManager(this) }
    val powerHelper by lazy { PowerManagerHelper(this) }
    val batteryTracker by lazy { BatteryTracker(this) }
    val updateManager by lazy { KioskUpdateManager(this) }

    lateinit var motionTracker: MotionSensorTracker
    lateinit var idleWatchdog: IdleWatchdog
    lateinit var nfcManager: KioskNfcManager
    lateinit var secretGestureDetector: SecretGestureDetector
    lateinit var audioOrderDetector: AudioOrderDetector

    var currentWebView: WebView? = null

    private val _isScreensaverActive = MutableStateFlow(false)
    val isScreensaverActive: StateFlow<Boolean> = _isScreensaverActive.asStateFlow()

    private val _shouldPromptLauncher = MutableStateFlow(false)
    val shouldPromptLauncher: StateFlow<Boolean> = _shouldPromptLauncher.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this

        enableImmersiveMode()

        batteryTracker.start()

        motionTracker = MotionSensorTracker(this) {
            triggerAntiTheftAlarm()
        }
        motionTracker.start()

        // Детектор звуковых сигналов нового заказа (для сайта и сторонних приложений)
        audioOrderDetector = AudioOrderDetector(this) {
            wakeUpFromScreensaver()
        }
        audioOrderDetector.start()

        idleWatchdog = IdleWatchdog(
            onIdleTimeout = {
                val config = configRepository.getConfig()
                if (config.screensaverEnabled) {
                    _isScreensaverActive.value = true
                    if (config.virtualSleepEnabled) {
                        // Энергосбережение: темный экран, но веб-сокет и аудио заказов слушают 24/7
                        powerHelper.setVirtualSleepBrightness(this, true)
                    }
                }
                if (config.clearDataOnIdle) {
                    runOnUiThread { currentWebView?.clearCache(true) }
                }
            },
            onUserActive = {
                wakeUpFromScreensaver()
            }
        )

        secretGestureDetector = SecretGestureDetector {
            triggerOpenSettings()
        }

        nfcManager = KioskNfcManager(this) { tagId ->
            runOnUiThread {
                currentWebView?.evaluateJavascript("window.onNfcScanned && window.onNfcScanned('$tagId');", null)
            }
        }

        applyConfigUpdates()
        // Фоновая автоматическая проверка обновлений: через 5 секунд после старта и далее каждые 15 минут
        lifecycleScope.launch {
            delay(5_000L)
            while (isActive) {
                val versionName = runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName
                }.getOrNull() ?: "1.0.0"
                updateManager.checkForUpdates(versionName)
                delay(15 * 60 * 1000L)
            }
        }

        // Ежедневная тихая ночная перезагрузка веб-страницы в заданный час для очистки кэша/памяти WebKit
        lifecycleScope.launch {
            while (isActive) {
                delay(60_000L)
                val config = configRepository.getConfig()
                val calendar = java.util.Calendar.getInstance()
                val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                val minute = calendar.get(java.util.Calendar.MINUTE)
                if (hour == config.dailyRebootTimeHour && minute == 0) {
                    runOnUiThread {
                        currentWebView?.reload()
                    }
                    delay(65_000L)
                }
            }
        }

        // Слушатель изменения громкости для предотвращения случайного выключения звука заказов на кухне
        val volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                checkAndEnforceMinVolume()
            }
        }
        try {
            registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        } catch (_: Exception) {}

        val serviceIntent = Intent(this, KioskForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            KioskBrowserTheme {
                val config by configRepository.configFlow.collectAsState()
                val screensaverActive by isScreensaverActive.collectAsState()
                val batteryLevel by batteryTracker.batteryLevel.collectAsState()
                val isCharging by batteryTracker.isCharging.collectAsState()

                var showPinDialog by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var isWebMode by remember { mutableStateOf(config.isSingleAppMode) }

                BackHandler(enabled = showPinDialog || showSettings || config.isKioskEnabled) {
                    when {
                        showPinDialog -> showPinDialog = false
                        showSettings -> showSettings = false
                        config.isKioskEnabled -> { /* Блокировка выхода в режиме киоска */ }
                    }
                }

                LaunchedEffect(config.isSingleAppMode) {
                    if (config.isSingleAppMode) {
                        isWebMode = true
                    }
                }

                openSettingsCallback = { showPinDialog = true }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (!config.isFirstLaunchCompleted) {
                        // Чистый фон при первоначальной настройке — никакой Home Assistant не запускается в фоне!
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(CyberBlack)
                        )
                        FirstRunSetupDialog(
                            initialUrl = config.startUrl,
                            initialPin = config.pinCode,
                            onComplete = { mode, url, appPackage, pin ->
                                configRepository.updateConfig {
                                    it.copy(
                                        primaryMode = mode,
                                        startUrl = url,
                                        primaryAppPackage = appPackage,
                                        pinCode = pin,
                                        isFirstLaunchCompleted = true,
                                        allowedApps = if (appPackage.isNotEmpty() && !it.allowedApps.contains(appPackage))
                                            it.allowedApps + appPackage else it.allowedApps
                                    )
                                }
                                applyConfigUpdates()
                            }
                        )
                    } else if (config.primaryMode == "APP" && config.primaryAppPackage.isNotEmpty()) {
                        PrimaryAppKioskScreen(
                            packageName = config.primaryAppPackage,
                            mainActivity = this@MainActivity,
                            onOpenSettings = { showPinDialog = true }
                        )
                    } else if (isWebMode || config.isSingleAppMode) {
                        KioskWebScreen(
                            mainActivity = this@MainActivity,
                            onOpenSettingsRequested = { showPinDialog = true },
                            onBackToLauncher = if (!config.isSingleAppMode) { { isWebMode = false } } else null
                        )
                    } else {
                        AppLauncherScreen(
                            allowedPackages = config.allowedApps,
                            onOpenWeb = { isWebMode = true },
                            onOpenSettingsWithPin = { showPinDialog = true }
                        )
                    }

                    if (screensaverActive) {
                        TechScreensaver(
                            batteryLevel = batteryLevel,
                            isCharging = isCharging,
                            onWakeUp = { wakeUpFromScreensaver() }
                        )
                    }

                    if (showPinDialog) {
                        PinAuthDialog(
                            expectedPin = config.pinCode,
                            onPinCorrect = {
                                showPinDialog = false
                                showSettings = true
                            },
                            onDismiss = { showPinDialog = false }
                        )
                    }

                    if (showSettings) {
                        SettingsScreen(
                            mainActivity = this@MainActivity,
                            onClose = { showSettings = false }
                        )
                    }

                    val shouldPrompt by shouldPromptLauncher.collectAsState()
                    var showLauncherPrompt by remember { mutableStateOf(false) }

                    LaunchedEffect(shouldPrompt) {
                        if (shouldPrompt) {
                            showLauncherPrompt = true
                            _shouldPromptLauncher.value = false
                        }
                    }

                    LaunchedEffect(Unit) {
                        if (!deviceOwnerManager.isDefaultLauncher()) {
                            showLauncherPrompt = true
                        }
                    }

                    if (showLauncherPrompt && !deviceOwnerManager.isDefaultLauncher()) {
                        AlertDialog(
                            onDismissRequest = { showLauncherPrompt = false },
                            containerColor = CyberCard,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.border(1.dp, NeonCyan.copy(alpha = 0.8f), RoundedCornerShape(16.dp)),
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(26.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "ВЫБОР ЛАУНЧЕРА ПО УМОЛЧАНИЮ",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Kiosk Browser сейчас не назначен главным экраном (активен One UI или другой лаунчер).",
                                        color = TextWhite,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "Включить Kiosk по умолчанию с полной блокировкой кнопок «Назад», «Домой» и «Недавние»?",
                                        color = NeonGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showLauncherPrompt = false
                                        configRepository.updateConfig {
                                            it.copy(
                                                preferredLauncherPackage = "",
                                                isKioskEnabled = true,
                                                blockSystemNavigation = true
                                            )
                                        }
                                        if (deviceOwnerManager.isDeviceOwner) {
                                            deviceOwnerManager.setDefaultLauncher(true)
                                        } else {
                                            deviceOwnerManager.requestDefaultLauncher(this@MainActivity)
                                        }
                                        startKioskMode()
                                        android.widget.Toast.makeText(
                                            this@MainActivity,
                                            "Kiosk установлен главным лаунчером. Кнопки Назад, Домой и Недавние заблокированы.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = CyberBlack, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("ВКЛЮЧИТЬ КИОСК (БЛОКИРОВКА)", color = CyberBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            },
                            dismissButton = {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            showLauncherPrompt = false
                                            showSettings = true
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                        border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Другой", fontSize = 11.sp)
                                    }
                                    TextButton(onClick = { showLauncherPrompt = false }) {
                                        Text("Оставить One UI", color = TextMuted, fontSize = 11.sp)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun enableImmersiveMode() {
        val config = runCatching { configRepository.getConfig() }.getOrNull()
        if (config != null && !config.isKioskEnabled) {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.show(WindowInsetsCompat.Type.systemBars())
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun applyConfigUpdates() {
        val config = configRepository.getConfig()

        powerHelper.setKeepScreenOn(this, config.keepScreenOn)
        if (config.keepScreenOn) {
            powerHelper.acquireLocks()
        } else {
            powerHelper.releaseLocks()
        }
        idleWatchdog.updateTimeoutSeconds(config.idleTimeoutSeconds, config.screensaverEnabled)

        motionTracker.isAntiTheftEnabled = config.antiTheftAlarmEnabled

        if (config.isKioskEnabled) {
            if (deviceOwnerManager.isDeviceOwner) {
                val allowed = (config.allowedApps + config.primaryAppPackage).filter { it.isNotBlank() }
                deviceOwnerManager.applyKioskPolicies(
                    blockSafeMode = config.blockSafeMode,
                    blockUsb = config.blockUsbFileTransfer,
                    disableStatusBar = config.blockSystemNavigation,
                    blockCallsAndSms = config.blockPhoneCallsAndSms,
                    blockTethering = config.blockTethering,
                    whitelistedPackages = allowed
                )
                val isKioskDesired = config.preferredLauncherPackage.isEmpty() || config.preferredLauncherPackage == packageName
                if (isKioskDesired) {
                    deviceOwnerManager.setDefaultLauncher(true)
                }
            }
            checkAndEnforceMinVolume()
            try {
                startLockTask()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            try {
                stopLockTask()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (deviceOwnerManager.isDeviceOwner) {
                deviceOwnerManager.clearKioskPolicies()
            }
        }
    }

    fun checkAndEnforceMinVolume() {
        val config = configRepository.getConfig()
        if (config.enforceMinOrderVolume) {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val minVol = ((maxVol * config.minOrderVolumePercent) / 100).coerceAtLeast(1)
                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (currentVol < minVol) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, minVol, 0)
                }
            } catch (_: Exception) {}
        }
    }

    fun startKioskMode() {
        configRepository.updateConfig {
            it.copy(
                isKioskEnabled = true,
                blockSystemNavigation = true,
                preferredLauncherPackage = ""
            )
        }
        if (deviceOwnerManager.isDeviceOwner) {
            deviceOwnerManager.setDefaultLauncher(true)
        }
        applyConfigUpdates()
        runOnUiThread {
            enableImmersiveMode()
        }
    }

    fun exitKioskMode() {
        try {
            stopLockTask()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (deviceOwnerManager.isDeviceOwner) {
            deviceOwnerManager.clearKioskPolicies()
            deviceOwnerManager.setDefaultLauncher(false)
        }
        powerHelper.setKeepScreenOn(this, false)
        powerHelper.releaseLocks()
        configRepository.updateConfig { it.copy(isKioskEnabled = false) }
        runOnUiThread {
            enableImmersiveMode()
        }
    }

    /**
     * Сворачивание Kiosk Browser и принудительный переход на рабочий стол (One UI / системный лаунчер)
     */
    fun exitToHomeScreen() {
        exitKioskMode()
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            moveTaskToBack(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Открытие системного окна / панели выбора сетей Wi-Fi
     */
    fun openWifiSettings() {
        runOnUiThread {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val panelIntent = Intent(android.provider.Settings.Panel.ACTION_WIFI).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(panelIntent)
                    return@runOnUiThread
                } catch (_: Exception) {}

                try {
                    val internetPanelIntent = Intent(android.provider.Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(internetPanelIntent)
                    return@runOnUiThread
                } catch (_: Exception) {}
            }

            try {
                val wifiIntent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(wifiIntent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Панель Wi-Fi недоступна", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Обработка нажатия на значок обновления в строке состояния:
     * - если скачано: запускает установку APK
     * - если скачивается: показывает прогресс (киоск продолжает работать)
     * - если доступно: запускает фоновую загрузку
     */
    fun onUpdateBadgeClicked() {
        when (val state = updateManager.updateState.value) {
            is com.kiosk.browser.core.update.UpdateState.ReadyToInstall -> {
                try {
                    stopLockTask()
                } catch (_: Exception) {}
                updateManager.installApk(state.apkFile)
            }
            is com.kiosk.browser.core.update.UpdateState.Available -> {
                android.widget.Toast.makeText(this, "Загрузка обновления v${state.versionName} в фоне...", android.widget.Toast.LENGTH_SHORT).show()
                updateManager.startBackgroundDownload(state.downloadUrl, state.versionName)
            }
            is com.kiosk.browser.core.update.UpdateState.Downloading -> {
                android.widget.Toast.makeText(this, "Скачивание обновления: ${state.progressPercent}% (киоск работает)", android.widget.Toast.LENGTH_SHORT).show()
            }
            is com.kiosk.browser.core.update.UpdateState.Installing -> {
                android.widget.Toast.makeText(this, "Установка обновления...", android.widget.Toast.LENGTH_SHORT).show()
            }
            else -> {
                val versionName = runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName
                }.getOrNull() ?: "1.0.0"
                lifecycleScope.launch {
                    android.widget.Toast.makeText(this@MainActivity, "Проверка обновлений...", android.widget.Toast.LENGTH_SHORT).show()
                    updateManager.checkForUpdates(versionName)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveMode()
        val config = configRepository.getConfig()
        val isKioskDesired = config.preferredLauncherPackage.isEmpty() || config.preferredLauncherPackage == packageName
        if (isKioskDesired && deviceOwnerManager.isDeviceOwner && !deviceOwnerManager.isDefaultLauncher()) {
            deviceOwnerManager.setDefaultLauncher(true)
        }
        if (config.isKioskEnabled) {
            try {
                startLockTask()
            } catch (_: Exception) {}
        }
        // Если Kiosk не является главным лаунчером - запрашиваем выбор при повторном заходе
        if (!deviceOwnerManager.isDefaultLauncher()) {
            _shouldPromptLauncher.value = true
        }
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "1.0.0"
        lifecycleScope.launch {
            updateManager.checkForUpdates(versionName)
        }
    }

    fun controlScreen(turnOn: Boolean) {
        runOnUiThread {
            if (turnOn) {
                wakeUpFromScreensaver()
            } else {
                _isScreensaverActive.value = true
                powerHelper.setVirtualSleepBrightness(this, true)
            }
        }
    }

    /**
     * Моментальное пробуждение экрана при новом заказе или активности
     */
    fun wakeUpFromScreensaver() {
        runOnUiThread {
            _isScreensaverActive.value = false
            powerHelper.wakeUpScreenInstantly(this)
            idleWatchdog.resetTimer()
        }
    }

    fun reloadCurrentPage() {
        runOnUiThread { currentWebView?.reload() }
    }

    fun loadUrl(url: String) {
        runOnUiThread { currentWebView?.loadUrl(url) }
    }

    private var openSettingsCallback: (() -> Unit)? = null

    private fun triggerOpenSettings() {
        runOnUiThread { openSettingsCallback?.invoke() }
    }

    private fun triggerAntiTheftAlarm() {
        runOnUiThread {
            val alertSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(applicationContext, alertSound)
            ringtone?.play()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        nfcManager.handleIntent(intent)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        idleWatchdog.notifyUserActivity()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val config = configRepository.getConfig()
        if (config.isKioskEnabled) {
            // Полная блокировка системных кнопок в режиме киоска
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_APP_SWITCH -> return true // Блокировка Назад, Домой, Недавние
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    if (config.blockHardwareKeys) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val config = configRepository.getConfig()
        if (config.isKioskEnabled) {
            // Если была попытка свернуть киоск кнопкой Домой или Недавние - возвращаем на экран
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val config = configRepository.getConfig()
        if (!hasFocus) {
            if (config.isKioskEnabled) {
                // Закрываем шторку уведомлений и панель недавних приложений
                runCatching {
                    @Suppress("DEPRECATION")
                    val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    sendBroadcast(closeDialogs)
                }
            }
        } else {
            enableImmersiveMode()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val config = configRepository.getConfig()
        if (config.isKioskEnabled) {
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentInstance = null
        audioOrderDetector.stop()
        batteryTracker.stop()
        motionTracker.stop()
        idleWatchdog.stop()
        powerHelper.releaseLocks()
    }

    companion object {
        var currentInstance: MainActivity? = null
            private set
    }
}