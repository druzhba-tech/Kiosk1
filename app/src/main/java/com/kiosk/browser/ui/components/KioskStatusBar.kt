package com.kiosk.browser.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiosk.browser.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/**
 * Вертикальный HUD-виджет в левом верхнем углу экрана (overlay поверх WebView).
 * Отображает: кнопку лаунчера, время, уровень WiFi/мобильной сети, заряд батареи, статус KIOSK.
 * Отступ от верхнего края задаётся снаружи через modifier (padding top ~28dp ≈ 1 см).
 */
@Composable
fun KioskStatusBar(
    batteryLevel: Int,
    isCharging: Boolean,
    isKioskActive: Boolean,
    onLauncherClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ── Время ──────────────────────────────────────────────────────────────
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTime()
            delay(1000L)
        }
    }

    // ── Сеть: WiFi / Мобильный ─────────────────────────────────────────────
    var isWifiConnected by remember { mutableStateOf(false) }
    var isMobileConnected by remember { mutableStateOf(false) }
    var wifiLevel by remember { mutableIntStateOf(-1) }
    var mobileSignal by remember { mutableIntStateOf(-1) }

    DisposableEffect(context) {
        fun updateSignal() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            isWifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            isMobileConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            if (isWifiConnected) {
                @Suppress("DEPRECATION")
                val rssi = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                    .connectionInfo.rssi
                wifiLevel = WifiManager.calculateSignalLevel(rssi, 5)
                mobileSignal = -1
            } else if (isMobileConnected) {
                mobileSignal = 3
                wifiLevel = -1
            } else {
                wifiLevel = -1
                mobileSignal = -1
            }
        }
        updateSignal()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) { updateSignal() }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            @Suppress("DEPRECATION")
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        context.registerReceiver(receiver, filter)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // ── Анимация зарядки ───────────────────────────────────────────────────
    val chargingAlpha by rememberInfiniteTransition(label = "charging").animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chargingAlpha"
    )

    // ── Мигающая точка-статус ──────────────────────────────────────────────
    val dotAlpha by rememberInfiniteTransition(label = "dot").animateFloat(
        initialValue = 1f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    // ── Сам виджет: вертикальная колонка ──────────────────────────────────
    Column(
        modifier = modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 0.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xEE060C18), Color(0xDD090F1E), Color(0xCC060C18))
                )
            )
            .border(
                width = 0.5.dp,
                brush = Brush.verticalGradient(
                    listOf(NeonCyan.copy(alpha = 0.5f), NeonCyan.copy(alpha = 0.1f), Color.Transparent)
                ),
                shape = RoundedCornerShape(topStart = 0.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 0.dp)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        // ── Кнопка лаунчера ───────────────────────────────────────────────
        if (onLauncherClick != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onLauncherClick)
                    .background(NeonCyan.copy(alpha = 0.12f))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Apps,
                    contentDescription = "Лаунчер",
                    tint = NeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "APPS",
                    color = NeonCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Разделитель
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(CyberBorder)
        )

        // ── Время ──────────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = "Время",
                tint = TextMuted,
                modifier = Modifier.size(11.dp)
            )
            Text(
                text = currentTime,
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // ── Сеть / WiFi (уровень сигнала) ─────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            when {
                isWifiConnected && wifiLevel >= 0 -> {
                    val wifiIcon = when {
                        wifiLevel <= 1 -> Icons.Default.SignalWifi1Bar
                        wifiLevel == 2 -> Icons.Default.SignalWifi2Bar
                        wifiLevel == 3 -> Icons.Default.SignalWifi3Bar
                        else           -> Icons.Default.Wifi
                    }
                    Icon(wifiIcon, contentDescription = "WiFi", tint = NeonCyan, modifier = Modifier.size(13.dp))
                    Text(
                        text = "Wi-Fi  " + "▮".repeat(wifiLevel + 1) + "▯".repeat(4 - wifiLevel),
                        color = NeonCyan,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
                isMobileConnected && mobileSignal >= 0 -> {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = "LTE", tint = NeonGreen, modifier = Modifier.size(13.dp))
                    Text(
                        text = "LTE  " + "▮".repeat(mobileSignal + 1) + "▯".repeat(4 - mobileSignal),
                        color = NeonGreen,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )
                }
                else -> {
                    Icon(Icons.Default.WifiOff, contentDescription = "Нет сети", tint = NeonRed, modifier = Modifier.size(13.dp))
                    Text(
                        text = "NO NET",
                        color = NeonRed,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // ── Батарея ────────────────────────────────────────────────────────
        val batteryIcon: ImageVector = when {
            isCharging        -> Icons.Default.BatteryChargingFull
            batteryLevel < 15 -> Icons.Default.BatteryAlert
            else              -> Icons.Default.BatteryFull
        }
        val batteryColor = when {
            batteryLevel < 15 -> NeonRed
            batteryLevel < 30 -> NeonOrange
            else              -> NeonCyan
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                batteryIcon,
                contentDescription = "Батарея",
                tint = if (isCharging) batteryColor.copy(alpha = chargingAlpha) else batteryColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = "$batteryLevel%",
                color = batteryColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace
            )
        }

        // ── Статус KIOSK ───────────────────────────────────────────────────
        if (isKioskActive) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(NeonGreen.copy(alpha = dotAlpha), shape = CircleShape)
                )
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Kiosk Active",
                    tint = NeonGreen,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "KIOSK",
                    color = NeonGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun getCurrentTime(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())