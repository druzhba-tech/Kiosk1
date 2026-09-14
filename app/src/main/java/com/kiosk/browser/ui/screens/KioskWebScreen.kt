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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.kiosk.browser.MainActivity
import com.kiosk.browser.core.webview.JavaScriptBridge
import com.kiosk.browser.core.webview.KioskWebChromeClient
import com.kiosk.browser.core.webview.KioskWebViewClient
import com.kiosk.browser.core.webview.UrlFilterManager
import com.kiosk.browser.ui.components.KioskStatusBar
import com.kiosk.browser.ui.components.SecretTapOverlay
import com.kiosk.browser.ui.theme.NeonCyan
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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

    // Защита от выгорания OLED дисплея
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
                if (config.oledBurnInProtection) IntOffset(shiftX.roundToInt(), 0)
                else IntOffset.Zero
            }
    ) {
        // ── Полноэкранный WebView с Pull-to-Refresh ──
        AndroidView(
            factory = { context ->
                val webView = WebView(context).apply {
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
                }

                val swipeRefresh = SwipeRefreshLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setColorSchemeColors(android.graphics.Color.parseColor("#00F0FF"))
                    setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#111827"))

                    setOnRefreshListener {
                        webView.reload()
                    }
                    addView(webView)
                }

                val filterManager = UrlFilterManager(config.allowedUrls, config.blockedUrls)
                webView.webViewClient = KioskWebViewClient(
                    filterManager = filterManager,
                    isIgnoreSslErrors = { config.ignoreSslErrors },
                    onCrashRecover = { webView.post { webView.loadUrl(config.startUrl) } },
                    onPageLoaded = { _ ->
                        swipeRefresh.isRefreshing = false
                    }
                )

                webView.webChromeClient = KioskWebChromeClient(
                    onProgressChanged = { progress ->
                        loadProgress = progress
                        if (progress >= 95) {
                            swipeRefresh.isRefreshing = false
                        }
                    },
                    onFullscreenRequested = { },
                    onFullscreenExit = { }
                )

                val jsBridge = JavaScriptBridge(context, mainActivity.batteryTracker) { turnOn ->
                    mainActivity.controlScreen(turnOn)
                }
                webView.addJavascriptInterface(jsBridge, "kiosk")
                webView.addJavascriptInterface(jsBridge, "fully")

                webView.loadUrl(config.startUrl)
                mainActivity.currentWebView = webView

                swipeRefresh
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Тонкий индикатор загрузки страницы ──
        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = loadProgress / 100f,
                color = NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }

        // ── Вычисление позиции и отступа панели по настройкам ──
        val hudAlignment = when (config.hudPosition) {
            "TOP_LEFT" -> Alignment.TopStart
            "BOTTOM_RIGHT" -> Alignment.BottomEnd
            "BOTTOM_LEFT" -> Alignment.BottomStart
            else -> Alignment.TopEnd
        }

        // 1 см ≈ 28 dp, отступ 2 см = 56 dp
        val topMarginDp = (config.hudTopMarginCm * 28f).coerceAtLeast(0f).dp

        val hudModifier = Modifier
            .align(hudAlignment)
            .padding(
                top = if (hudAlignment == Alignment.TopEnd || hudAlignment == Alignment.TopStart) topMarginDp else 0.dp,
                bottom = if (hudAlignment == Alignment.BottomEnd || hudAlignment == Alignment.BottomStart) 20.dp else 0.dp,
                end = if (hudAlignment == Alignment.TopEnd || hudAlignment == Alignment.BottomEnd) 12.dp else 0.dp,
                start = if (hudAlignment == Alignment.TopStart || hudAlignment == Alignment.BottomStart) 12.dp else 0.dp
            )

        // ── Получение состояния фонового обновления
        val updateState by mainActivity.updateManager.updateState.collectAsState()
        val updateVersion = (updateState as? com.kiosk.browser.core.update.UpdateState.Available)?.versionName
        val updateProgress = (updateState as? com.kiosk.browser.core.update.UpdateState.Downloading)?.progressPercent
        val isInstalling = updateState is com.kiosk.browser.core.update.UpdateState.Installing

        // Информационная панель (вертикальная/горизонтальная, со слайдером яркости и Wi-Fi)
        KioskStatusBar(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isKioskActive = config.isKioskEnabled,
            isVertical = config.hudOrientation.equals("VERTICAL", ignoreCase = true),
            showBrightness = config.hudShowBrightness,
            showWifi = config.hudShowWifi,
            showBattery = config.hudShowBattery,
            showKioskStatus = config.hudShowKioskStatus,
            updateVersion = updateVersion,
            updateProgress = updateProgress,
            isInstallingUpdate = isInstalling,
            onUpdateClick = {
                if (updateState is com.kiosk.browser.core.update.UpdateState.Available) {
                    val url = (updateState as com.kiosk.browser.core.update.UpdateState.Available).downloadUrl
                    mainActivity.lifecycleScope.launch {
                        mainActivity.updateManager.downloadAndInstall(url)
                    }
                }
            },
            onLauncherClick = onBackToLauncher,
            modifier = hudModifier
        )

        // ── Секретная зона 5 тапов для открытия настроек (правый верхний угол) ──
        SecretTapOverlay(
            onSecretTap = { mainActivity.secretGestureDetector.onSecretAreaTapped() },
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}