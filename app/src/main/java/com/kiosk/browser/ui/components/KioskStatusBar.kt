package com.kiosk.browser.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip

/**
 * Верхняя строка статуса в стиле Cyber-HUD.
 * Слева — статус киоска и кнопка меню, по центру — часы, справа — сигнал и батарея.
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

    // ── Часы ──────────────────────────────────────────────────────────────────
    var currentTime by remember { mutableStateOf(getCurrentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = getCurrentTime()
            delay(1000L)
        }
    }

    // ── WiFi / мобильный сигнал ───────────────────────────────────────────────
    var wifiLevel by remember { mutableStateOf(-1) }
    var mobileSignal by remember { mutableStateOf(-1) }
    var isWifiConnected by remember { mutableStateOf(false) }
    var isMobileConnected by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        fun updateSignal() {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(network)
            isWifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            isMobileConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            if (isWifiConnected) {
                val wifiMgr = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val rssi = wifiMgr.connectionInfo.rssi
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

    // ── Анимация зарядки ──────────────────────────────────────────────────────
    val chargingAlpha by rememberInfiniteTransition(label = "charging").animateFloat(
        initialValue = 1f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chargingAlpha"
    )

    // ── Layout ────────────────────────────────────────────────────────────────
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xF5060C18), Color(0xEE090F1E), Color(0xF5060C18))
                )
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // ── Левая часть: Меню приложений и статус KIOSK ──────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            if (onLauncherClick != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onLauncherClick)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        Icons.Default.Apps,
                        contentDescription = "Приложения",
                        tint = NeonCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "МЕНЮ",
                        color = NeonCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            if (isKioskActive) {
                Icon(
                    Icons.Default.Lock, contentDescription = "Kiosk Active",
                    tint = NeonGreen, modifier = Modifier.size(11.dp)
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

        // ── Центр: Часы ───────────────────────────────────────────────────────
        Text(
            text = currentTime,
            color = TextWhite,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace
        )

        // ── Правая часть: Сигнал + Батарея ────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f).wrapContentWidth(Alignment.End)
        ) {
            when {
                isWifiConnected && wifiLevel >= 0 -> WifiSignalIcon(wifiLevel)
                isMobileConnected && mobileSignal >= 0 -> MobileSignalIcon(mobileSignal)
                else -> Icon(
                    Icons.Default.WifiOff, contentDescription = "No network",
                    tint = TextMuted, modifier = Modifier.size(13.dp)
                )
            }

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

            Icon(
                batteryIcon, contentDescription = "Battery",
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
    }
}

@Composable
private fun WifiSignalIcon(level: Int) {
    Icon(
        Icons.Default.Wifi,
        contentDescription = "WiFi $level",
        tint = NeonCyan,
        modifier = Modifier.size(13.dp)
    )
}

@Composable
private fun MobileSignalIcon(level: Int) {
    Icon(
        Icons.Default.SignalCellularAlt,
        contentDescription = "Mobile $level",
        tint = NeonCyan,
        modifier = Modifier.size(13.dp)
    )
}

private fun getCurrentTime(): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
