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

    // Защита OLED: смещение на 1-2px раз в 3 минуты
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

    // Корневой Box: WebView + оверлеи поверх него
    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset {
                if (config.oledBurnInProtection) IntOffset(shiftX.roundToInt(), 0)
                else IntOffset.Zero
            }
    ) {
        // ── WebView на весь экран ──────────────────────────────────────────
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
                        onCrashRecover = { post { loadUrl(config.startUrl) } },
                        onPageLoaded = { _ -> }
                    )
                    webChromeClient = KioskWebChromeClient(
                        onProgressChanged = { progress -> loadProgress = progress },
                        onFullscreenRequested = { },
                        onFullscreenExit = { }
                    )
                    val jsBridge = JavaScriptBridge(context, mainActivity.batteryTracker) { turnOn ->
                        mainActivity.controlScreen(turnOn)
                    }
                    addJavascriptInterface(jsBridge, "kiosk")
                    addJavascriptInterface(jsBridge, "fully")
                    loadUrl(config.startUrl)
                    mainActivity.currentWebView = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Прогресс загрузки ──────────────────────────────────────────────
        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = loadProgress / 100f,
                color = NeonCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            )
        }

        // ── HUD-виджет: вертикальный, левый верхний угол, отступ ~28dp ────
        // ~28dp ≈ 1 см на стандартной плотности (160 dpi)
        KioskStatusBar(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isKioskActive = config.isKioskEnabled,
            onLauncherClick = onBackToLauncher,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 28.dp)   // ≈ 1 см отступ от верхнего края
        )

        // ── Секретная зона (5 тапов → PIN) — правый верхний угол ─────────
        SecretTapOverlay(
            onSecretTap = { mainActivity.secretGestureDetector.onSecretAreaTapped() },
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}