package com.kiosk.browser.ui.screens

import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.kiosk.browser.MainActivity
import com.kiosk.browser.core.security.PasswordManager
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
    var pendingPasswordPrompt by remember { mutableStateOf<Triple<String, String, String>?>(null) }

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
                    // Блокировка выделения текста и всплывающего меню
                    isLongClickable = false
                    isHapticFeedbackEnabled = false
                    setOnLongClickListener { true }

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

                        // Защита от случайного масштабирования на сенсорных экранах
                        if (config.preventZoom) {
                            setSupportZoom(false)
                            builtInZoomControls = false
                            displayZoomControls = false
                        } else {
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }

                        // Запоминание паролей и автозаполнение
                        saveFormData = true
                        @Suppress("DEPRECATION")
                        savePassword = true
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                }

                lateinit var jsBridge: JavaScriptBridge

                val swipeRefresh = SwipeRefreshLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setColorSchemeColors(android.graphics.Color.parseColor("#00F0FF"))
                    setProgressBackgroundColorSchemeColor(android.graphics.Color.parseColor("#111827"))
                    isEnabled = config.enablePullToRefresh

                    // Строгий коллбэк: если страница хоть немного прокручена вниз, жест обновления полностью запрещен!
                    setOnChildScrollUpCallback { _, _ ->
                        val isAtTop = webView.scrollY <= 0 && !webView.canScrollVertically(-1) && jsBridge.isPageAtTop
                        !isAtTop
                    }

                    setOnRefreshListener {
                        webView.reload()
                    }
                    addView(webView)
                }

                // Слушатель скролла: если scrollY > 0, сразу отключаем SwipeRefreshLayout,
                // чтобы свайп сверху вниз гарантированно прокручивал сайт вверх, а не вызывал перезагрузку!
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                        if (config.enablePullToRefresh) {
                            swipeRefresh.isEnabled = (scrollY <= 0 && !webView.canScrollVertically(-1) && jsBridge.isPageAtTop)
                        }
                    }
                }

                val filterManager = UrlFilterManager(config.allowedUrls, config.blockedUrls)
                webView.webViewClient = KioskWebViewClient(
                    filterManager = filterManager,
                    isIgnoreSslErrors = { config.ignoreSslErrors },
                    isBlockCallsAndSms = { config.blockPhoneCallsAndSms },
                    isPreventZoom = { config.preventZoom },
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

                jsBridge = JavaScriptBridge(
                    context = context,
                    batteryTracker = mainActivity.batteryTracker,
                    onScreenControl = { turnOn -> mainActivity.controlScreen(turnOn) },
                    onSavePasswordPrompt = { domain, username, password ->
                        mainActivity.runOnUiThread {
                            pendingPasswordPrompt = Triple(domain, username, password)
                        }
                    },
                    onScrollStateChange = { isAtTop ->
                        mainActivity.runOnUiThread {
                            if (config.enablePullToRefresh) {
                                swipeRefresh.isEnabled = (isAtTop && webView.scrollY <= 0 && !webView.canScrollVertically(-1))
                            }
                        }
                    }
                )
                webView.addJavascriptInterface(jsBridge, "kiosk")
                webView.addJavascriptInterface(jsBridge, "fully")

                if (config.startUrl.isNotBlank()) {
                    webView.loadUrl(config.startUrl)
                }
                mainActivity.currentWebView = webView

                swipeRefresh
            },
            update = { swipeRefresh ->
                val webView = swipeRefresh.getChildAt(0) as? WebView
                if (webView != null && config.startUrl.isNotBlank()) {
                    val current = webView.url ?: ""
                    if (current != config.startUrl && !current.startsWith(config.startUrl.trimEnd('/'))) {
                        webView.loadUrl(config.startUrl)
                    }
                }
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
        val updateVersion = when (val s = updateState) {
            is com.kiosk.browser.core.update.UpdateState.Available -> s.versionName
            is com.kiosk.browser.core.update.UpdateState.Downloading -> s.versionName
            is com.kiosk.browser.core.update.UpdateState.ReadyToInstall -> s.versionName
            else -> null
        }
        val updateProgress = (updateState as? com.kiosk.browser.core.update.UpdateState.Downloading)?.progressPercent
        val isReadyToInstall = updateState is com.kiosk.browser.core.update.UpdateState.ReadyToInstall
        val isInstalling = updateState is com.kiosk.browser.core.update.UpdateState.Installing

        // Информационная панель (вертикальная/горизонтальная, со слайдером яркости и Wi-Fi)
        KioskStatusBar(
            batteryLevel = batteryLevel,
            isCharging = isCharging,
            isKioskActive = config.isKioskEnabled,
            isVertical = config.hudOrientation.equals("VERTICAL", ignoreCase = true),
            showBrightness = config.hudShowBrightness,
            showVolume = config.hudShowVolume,
            showWifi = config.hudShowWifi,
            showBattery = config.hudShowBattery,
            showKioskStatus = config.hudShowKioskStatus,
            updateVersion = updateVersion,
            updateProgress = updateProgress,
            isReadyToInstall = isReadyToInstall,
            isInstallingUpdate = isInstalling,
            onUpdateClick = { mainActivity.onUpdateBadgeClicked() },
            onLauncherClick = onBackToLauncher,
            modifier = hudModifier
        )

        // ── Секретная зона 5 тапов для открытия настроек (правый верхний угол) ──
        SecretTapOverlay(
            onSecretTap = { mainActivity.secretGestureDetector.onSecretAreaTapped() },
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // ── Диалог сохранения пароля ──
        pendingPasswordPrompt?.let { (domain, username, password) ->
            AlertDialog(
                onDismissRequest = { pendingPasswordPrompt = null },
                containerColor = Color(0xFF161F30),
                titleContentColor = Color.White,
                textContentColor = Color(0xFF94A3B8),
                icon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = NeonCyan
                    )
                },
                title = {
                    Text(
                        text = "Сохранить пароль?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Сохранить учетные данные для сайта:")
                        Text(
                            text = domain,
                            color = NeonCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (username.isNotBlank()) {
                            Text(
                                text = "Логин: $username",
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            PasswordManager(mainActivity).saveCredentials(domain, username, password)
                            pendingPasswordPrompt = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF0F172A))
                    ) {
                        Text("Сохранить", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { pendingPasswordPrompt = null }
                    ) {
                        Text("Не сейчас", color = Color(0xFF64748B))
                    }
                }
            )
        }
    }
}