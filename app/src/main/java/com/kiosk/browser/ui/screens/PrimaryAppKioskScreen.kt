package com.kiosk.browser.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.kiosk.browser.MainActivity
import com.kiosk.browser.ui.components.KioskStatusBar
import com.kiosk.browser.ui.components.SecretTapOverlay
import com.kiosk.browser.ui.theme.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Экран киоска в режиме основного приложения (APP Mode):
 * Отображает карточку выбранного приложения по умолчанию,
 * кнопку быстрого запуска, информационную панель HUD и секретную зону входа в настройки.
 */
@Composable
fun PrimaryAppKioskScreen(
    packageName: String,
    mainActivity: MainActivity,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val config by mainActivity.configRepository.configFlow.collectAsState()
    val batteryLevel by mainActivity.batteryTracker.batteryLevel.collectAsState()
    val isCharging by mainActivity.batteryTracker.isCharging.collectAsState()

    var appName by remember { mutableStateOf(packageName) }
    var appIconBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        if (packageName.isNotEmpty()) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                appName = pm.getApplicationLabel(appInfo).toString()
                val drawable = pm.getApplicationIcon(appInfo)
                appIconBitmap = drawable.toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                appName = packageName
            }
        }
    }

    fun launchPrimaryApp() {
        if (packageName.isNotEmpty()) {
            try {
                val intent = pm.getLaunchIntentForPackage(packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Автоматический запуск приложения при входе на экран
    LaunchedEffect(packageName) {
        launchPrimaryApp()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberBlack)
    ) {
        val hudAlignment = when (config.hudPosition) {
            "TOP_LEFT" -> Alignment.TopStart
            "BOTTOM_RIGHT" -> Alignment.BottomEnd
            "BOTTOM_LEFT" -> Alignment.BottomStart
            else -> Alignment.TopEnd
        }

        val hudModifier = Modifier
            .align(hudAlignment)
            .padding(
                top = if (hudAlignment == Alignment.TopEnd || hudAlignment == Alignment.TopStart) {
                    (config.hudTopMarginCm * 28).dp
                } else 0.dp,
                bottom = if (hudAlignment == Alignment.BottomEnd || hudAlignment == Alignment.BottomStart) 16.dp else 0.dp,
                end = if (hudAlignment == Alignment.TopEnd || hudAlignment == Alignment.BottomEnd) 12.dp else 0.dp,
                start = if (hudAlignment == Alignment.TopStart || hudAlignment == Alignment.BottomStart) 12.dp else 0.dp
            )
        // Центр: Карточка приложения и запуск
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Иконка
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CyberCard)
                    .border(1.5.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (appIconBitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = appIconBitmap!!,
                        contentDescription = appName,
                        modifier = Modifier.size(72.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Apps,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Название приложения
            Text(
                text = appName,
                color = TextWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = packageName,
                color = TextMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(8.dp))

            // Кнопка запуска
            Button(
                onClick = { launchPrimaryApp() },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                modifier = Modifier
                    .width(260.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = CyberBlack)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "ЗАПУСТИТЬ ПРИЛОЖЕНИЕ",
                    color = CyberBlack,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    letterSpacing = 1.sp
                )
            }

            // Кнопка перехода в настройки (для оператора)
            OutlinedButton(
                onClick = onOpenSettings,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Настройки киоска (PIN)", fontSize = 12.sp)
            }
        }

        // Получение состояния фонового обновления
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

        // Информационная панель (HUD) в углу
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
            isReadyToInstall = isReadyToInstall,
            isInstallingUpdate = isInstalling,
            onUpdateClick = { mainActivity.onUpdateBadgeClicked() },
            modifier = hudModifier
        )

        // Секретная зона 5 тапов в правом верхнем углу для открытия настроек
        SecretTapOverlay(
            onSecretTap = { mainActivity.secretGestureDetector.onSecretAreaTapped() },
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}
