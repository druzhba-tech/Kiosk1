package com.kiosk.browser.ui.screens

import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kiosk.browser.KioskApp
import com.kiosk.browser.MainActivity
import com.kiosk.browser.core.update.UpdateManager
import com.kiosk.browser.core.webview.JavaScriptBridge
import com.kiosk.browser.core.webview.KioskWebChromeClient
import com.kiosk.browser.core.webview.KioskWebViewClient
import com.kiosk.browser.core.webview.UrlFilterManager
import com.kiosk.browser.ui.components.KioskStatusBar
import com.kiosk.browser.ui.components.SecretTapOverlay
import com.kiosk.browser.ui.components.UpdateDialog
import com.kiosk.browser.ui.theme.NeonCyan
import kotlin.math.roundToInt

@Composable
fun KioskWebScreen(
    mainActivity: MainActivity,
    onOpenSettingsRequested: () -> Unit,
    onBackToLauncher: (() -> Unit)? = null
) {
    val config by mainActivity.configRepository.configFlow.collectAsState()
    val batteryLevel by mainActivity.batteryTracker.batteryLevel.collectAsState()
    val isCharging by mainActivity.batteryTracker.isCharging.collectAsState()

    // ── OTA Обновления ────────────────────────────────────────────────────────
    val updateManager = (mainActivity.application as KioskApp).updateManager
    val updateState by updateManager.state.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Автоматически показываем диалог ТОЛЬКО при установке/успехе/ошибке.
    // Во время ЗАГРУЗКИ — приложение работает в фоне, прогресс виден в статус-баре.
    LaunchedEffect(updateState) {
        when (updateState) {
            is UpdateManager.UpdateState.Installing,
            is UpdateManager.UpdateState.InstallSuccess,
            is UpdateManager.UpdateState.Error -> showUpdateDialog = true
            else -> {}
        }
    }

    // Для значка в статус-баре: версия или процент загрузки
    val updateVersionName = when (val s = updateState) {
        is UpdateManager.UpdateState.UpdateAvailable -> s.versionName
        is UpdateManager.UpdateState.Downloading -> "${s.progress}%"
        is UpdateManager.UpdateState.Installing -> "..."
        else -> ""
    }

    val showUpdateBadge = updateState is UpdateManager.UpdateState.UpdateAvailable
            || updateState is UpdateManager.UpdateState.Downloading
            || updateState is UpdateManager.UpdateState.Installing

    var loadProgress by remember { mutableStateOf(0) }

    // Защита от выгорания OLED: микро-сдвиг на 1-2 пикселя
    val pixelShiftAnim = rememberInfiniteTransition(label = "pixelShift")
    val shiftX by pixelShiftAnim.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 180_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shiftX"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset {
                if (config.oledBurnInProtection) {
                    IntOffset(shiftX.roundToInt(), 0)
                } else {
                    IntOffset.Zero
                }
            }
    ) {
        // Верхняя строка статуса: часы, WiFi, батарея, статус киоска, значок обновления
        KioskStatusBar(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isKioskActive = config.isKioskEnabled,
            onLauncherClick = onBackToLauncher,
            updateAvailable = showUpdateBadge,
            updateVersionName = updateVersionName,
            onUpdateClick = {
                // При клике: если скачивается — показать диалог прогресса, иначе начать скачку
                when (val s = updateState) {
                    is UpdateManager.UpdateState.UpdateAvailable -> {
                        updateManager.downloadAndInstall(s.apkUrl)
                    }
                    is UpdateManager.UpdateState.Downloading,
                    is UpdateManager.UpdateState.Installing -> {
                        showUpdateDialog = true
                    }
                    else -> {}
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Контейнер веб-страницы
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            mediaPlaybackRequiresUserGesture = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }

                        val filterManager = UrlFilterManager(config.allowedUrls, config.blockedUrls)
                        webViewClient = KioskWebViewClient(
                            filterManager = filterManager,
                            isIgnoreSslErrors = { config.ignoreSslErrors },
                            onCrashRecover = {
                                post { loadUrl(config.startUrl) }
                            },
                            onPageLoaded = { _ -> }
                        )

                        webChromeClient = KioskWebChromeClient(
                            onProgressChanged = { progress -> loadProgress = progress },
                            onFullscreenRequested = { },
                            onFullscreenExit = { }
                        )

                        // JavaScript Bridge (window.kiosk и window.fully)
                        val jsBridge = JavaScriptBridge(context, mainActivity.batteryTracker) { turnOn ->
                            mainActivity.controlScreen(turnOn)
                        }
                        addJavascriptInterface(jsBridge, "kiosk")
                        addJavascriptInterface(jsBridge, "fully") // Совместимость с Fully Kiosk

                        loadUrl(config.startUrl)
                        mainActivity.currentWebView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Индикатор загрузки страницы
            if (loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = loadProgress / 100f,
                    color = NeonCyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )
            }

            // Скрытая зона тапа в правом верхнем углу (вызов PIN-кода)
            SecretTapOverlay(
                onSecretTap = {
                    mainActivity.secretGestureDetector.onSecretAreaTapped()
                },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }

    // ── Диалог обновления (загрузка / установка / успех / ошибка) ────────────
    if (showUpdateDialog &&
        updateState !is UpdateManager.UpdateState.Idle &&
        updateState !is UpdateManager.UpdateState.Checking
    ) {
        UpdateDialog(
            updateState = updateState,
            onInstall = { apkUrl ->
                updateManager.downloadAndInstall(apkUrl)
            },
            onDismiss = {
                showUpdateDialog = false
            }
        )
    }
}
