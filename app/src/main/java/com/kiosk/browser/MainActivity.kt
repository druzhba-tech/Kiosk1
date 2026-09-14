package com.kiosk.browser

import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiosk.browser.ui.theme.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kiosk.browser.admin.DeviceOwnerManager
import com.kiosk.browser.core.nfc.KioskNfcManager
import com.kiosk.browser.core.power.BatteryTracker
import com.kiosk.browser.core.power.IdleWatchdog
import com.kiosk.browser.core.power.PowerManagerHelper
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

    lateinit var motionTracker: MotionSensorTracker
    lateinit var idleWatchdog: IdleWatchdog
    lateinit var nfcManager: KioskNfcManager
    lateinit var secretGestureDetector: SecretGestureDetector

    var currentWebView: WebView? = null

    private val _isScreensaverActive = MutableStateFlow(false)
    val isScreensaverActive: StateFlow<Boolean> = _isScreensaverActive.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentInstance = this

        enableImmersiveMode()

        // Инициализация трекера батареи
        batteryTracker.start()

        // Инициализация антивора
        motionTracker = MotionSensorTracker(this) {
            triggerAntiTheftAlarm()
        }
        motionTracker.start()

        // Инициализация таймера бездействия
        idleWatchdog = IdleWatchdog(
            onIdleTimeout = {
                val config = configRepository.getConfig()
                if (config.screensaverEnabled) {
                    _isScreensaverActive.value = true
                    if (config.virtualSleepEnabled) {
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

        // Секретный жест (5 тапов в правом верхнем углу)
        secretGestureDetector = SecretGestureDetector {
            triggerOpenSettings()
        }

        // NFC Менеджер
        nfcManager = KioskNfcManager(this) { tagId ->
            // При считывании NFC передаем ID в WebView
            runOnUiThread {
                currentWebView?.evaluateJavascript("window.onNfcScanned && window.onNfcScanned('$tagId');", null)
            }
        }

        applyConfigUpdates()

        // Запуск фонового сервиса для MQTT и веб-сервера
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

                // Блокировка системного жеста и кнопки Назад
                BackHandler(enabled = config.isKioskEnabled) {
                    // Глушим нажатие назад в режиме киоска
                }

                var showPinDialog by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var isWebMode by remember { mutableStateOf(config.isSingleAppMode) }

                LaunchedEffect(config.isSingleAppMode) {
                    if (config.isSingleAppMode) {
                        isWebMode = true
                    }
                }

                // Регистрация коллбэка открытия настроек
                openSettingsCallback = { showPinDialog = true }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (isWebMode || config.isSingleAppMode) {
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

                    // Скринсейвер
                    if (screensaverActive) {
                        TechScreensaver(
                            batteryLevel = batteryLevel,
                            isCharging = isCharging,
                            onWakeUp = { wakeUpFromScreensaver() }
                        )
                    }

                    // PIN диалог
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

                    // Экран настроек
                    if (showSettings) {
                        SettingsScreen(
                            mainActivity = this@MainActivity,
                            onClose = { showSettings = false }
                        )
                    }

                    // Диалог быстрого запроса на установку лаунчером по умолчанию в 1 клик
                    var showLauncherPrompt by remember {
                        mutableStateOf(!deviceOwnerManager.isDefaultLauncher())
                    }

                    if (showLauncherPrompt) {
                        AlertDialog(
                            onDismissRequest = { showLauncherPrompt = false },
                            containerColor = CyberCard,
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = NeonCyan)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "ГЛАВНЫЙ ЭКРАН",
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                            },
                            text = {
                                Text(
                                    "Установите Kiosk Gusar вместо заводского лаунчера, чтобы зафиксировать режим киоска и защитить устройство от выхода на рабочий стол.",
                                    color = TextWhite,
                                    fontSize = 13.sp
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showLauncherPrompt = false
                                        deviceOwnerManager.requestDefaultLauncher(this@MainActivity)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                                ) {
                                    Text("УСТАНОВИТЬ В 1 КЛИК", color = CyberBlack, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showLauncherPrompt = false }) {
                                    Text("ПОЗЖЕ", color = TextMuted)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun applyConfigUpdates() {
        val config = configRepository.getConfig()

        // Удержание экрана включенным
        powerHelper.setKeepScreenOn(this, config.keepScreenOn)

        // Настройка сторожевого таймера
        idleWatchdog.updateTimeoutSeconds(config.idleTimeoutSeconds, config.screensaverEnabled)

        // Антивор
        motionTracker.isAntiTheftEnabled = config.antiTheftAlarmEnabled

        // Применение режима киоска (блокировка экрана)
        if (config.isKioskEnabled) {
            if (deviceOwnerManager.isDeviceOwner) {
                deviceOwnerManager.applyKioskPolicies(
                    blockSafeMode = config.blockSafeMode,
                    blockUsb = config.blockUsbFileTransfer,
                    disableStatusBar = config.blockSystemNavigation
                )
                deviceOwnerManager.setDefaultLauncher(true)
            }
            try {
                startLockTask()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startKioskMode() {
        configRepository.updateConfig { it.copy(isKioskEnabled = true) }
        applyConfigUpdates()
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
        configRepository.updateConfig { it.copy(isKioskEnabled = false) }
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

    fun wakeUpFromScreensaver() {
        _isScreensaverActive.value = false
        powerHelper.setVirtualSleepBrightness(this, false)
        idleWatchdog.resetTimer()
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

    /**
     * Блокировка аппаратных кнопок громкости и кнопки «Назад»
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val config = configRepository.getConfig()
        if (config.isKioskEnabled && config.blockHardwareKeys) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE -> return true // глушим нажатие
            }
        }
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val config = configRepository.getConfig()
        if (config.isKioskEnabled) {
            // В режиме киоска кнопка назад заблокирована
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        currentInstance = null
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
