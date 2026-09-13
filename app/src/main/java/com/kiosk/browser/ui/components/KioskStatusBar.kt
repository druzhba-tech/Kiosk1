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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiosk.browser.ui.theme.*

/**
 * Компактный горизонтальный мини-бейдж (pill / капсула):
 * - Зеленый замочек (KIOSK)
 * - Уровень Wi-Fi / сети
 * - Батарея (иконка + проценты)
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

    // Сеть: WiFi / Мобильная
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

    val chargingAlpha by rememberInfiniteTransition(label = "charging").animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chargingAlpha"
    )

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

    // Компактная горизонтальная капсула как на фото
    Row(
        modifier = modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC111827)) // Полупрозрачный темно-серый фон
            .border(
                width = 0.5.dp,
                color = Color(0x3300F0FF),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // 1. Иконка KIOSK (зеленый замочек)
        if (isKioskActive) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Kiosk",
                tint = NeonGreen,
                modifier = Modifier.size(13.dp)
            )
        }

        // 2. Иконка Wi-Fi / Сети
        when {
            isWifiConnected -> {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = "Wi-Fi",
                    tint = NeonCyan,
                    modifier = Modifier.size(13.dp)
                )
            }
            isMobileConnected -> {
                Icon(
                    imageVector = Icons.Default.SignalCellularAlt,
                    contentDescription = "LTE",
                    tint = NeonGreen,
                    modifier = Modifier.size(13.dp)
                )
            }
            else -> {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = "No Network",
                    tint = NeonRed,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        // 3. Иконка батареи
        Icon(
            imageVector = batteryIcon,
            contentDescription = "Battery",
            tint = if (isCharging) batteryColor.copy(alpha = chargingAlpha) else batteryColor,
            modifier = Modifier.size(14.dp)
        )

        // 4. Текст процентов батареи
        Text(
            text = "$batteryLevel%",
            color = TextWhite,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}