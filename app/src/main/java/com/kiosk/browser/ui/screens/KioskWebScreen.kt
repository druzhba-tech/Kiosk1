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
import com.kiosk.browser.MainActivity
import com.kiosk.browser.core.webview.JavaScriptBridge
import com.kiosk.browser.core.webview.KioskWebChromeClient
import com.kiosk.browser.core.webview.KioskWebViewClient
import com.kiosk.browser.core.webview.UrlFilterManager
import com.kiosk.browser.ui.components.KioskStatusBar
import com.kiosk.browser.ui.components.SecretTapOverlay
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

    var loadProgress by remember { mutableStateOf(0) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

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

    Box(
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
                        onPageLoaded = { url ->
                            // Страница загружена
                        }
                    )

                    webChromeClient = KioskWebChromeClient(
                        onProgressChanged = { progress -> loadProgress = progress },
                        onFullscreenRequested = { /* обработка полноэкранного видео */ },
                        onFullscreenExit = { }
                    )

                    // Внедрение JavaScript Bridge (window.kiosk)
                    val jsBridge = JavaScriptBridge(context, mainActivity.batteryTracker) { turnOn ->
                        mainActivity.controlScreen(turnOn)
                    }
                    addJavascriptInterface(jsBridge, "kiosk")
                    addJavascriptInterface(jsBridge, "fully") // Совместимость с Fully Kiosk

                    loadUrl(config.startUrl)
                    webViewInstance = this
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

        // Верхняя строка статуса: часы, WiFi/сигнал, батарея, статус киоска, кнопка перехода в лаунчер
        KioskStatusBar(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isKioskActive = config.isKioskEnabled,
            onLauncherClick = onBackToLauncher,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Скрытая зона тапа в правом верхнем углу (для вызова PIN-кода и настроек)
        SecretTapOverlay(
            onSecretTap = {
                mainActivity.secretGestureDetector.onSecretAreaTapped()
            },
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}
