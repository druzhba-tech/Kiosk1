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

    // Защита от выгорания OLED
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
        // ── Полноэкранный WebView с поддержкой свайпа сверху вниз (Pull-To-Refresh) ──
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

                // Обертка SwipeRefreshLayout для смахивания сверху вниз
                val swipeRefresh = SwipeRefreshLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Cyber-стилизация индикатора загрузки
                    setColorSchemeColors(android.graphics.Color.parseColor("#00F0FF")) // NeonCyan
                    setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#111827")) // CyberSurface

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
                        // Завершаем анимацию свайпа после загрузки
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

        // ── Тонкий прогресс-бар загрузки страницы вверху ──────────────────────
        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = loadProgress / 100f,
                color = NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }

        // ── Вертикальный HUD статус-бар в левом верхнем углу (отступ ~1 см) ───
        KioskStatusBar(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isKioskActive = config.isKioskEnabled,
            onLauncherClick = onBackToLauncher,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 28.dp)
        )

        // ── Секретная зона в правом верхнем углу для открытия настроек ────────
        SecretTapOverlay(
            onSecretTap = { mainActivity.secretGestureDetector.onSecretAreaTapped() },
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}