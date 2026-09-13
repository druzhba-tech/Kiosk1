package com.kiosk.browser.ui.components

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kiosk.browser.MainActivity
import com.kiosk.browser.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun KioskStatusBar(
    batteryLevel: Int,
    isCharging: Boolean,
    isKioskActive: Boolean,
    isVertical: Boolean = true,
    showBrightness: Boolean = true,
    showWifi: Boolean = true,
    showBattery: Boolean = true,
    showKioskStatus: Boolean = true,
    onLauncherClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var showBrightnessDialog by remember { mutableStateOf(false) }
    var currentBrightness by remember { mutableFloatStateOf(0.8f) }

    // Отслеживание сети Wi-Fi / Мобильной
    var isWifiConnected by remember { mutableStateOf(false) }
    var isMobileConnected by remember { mutableStateOf(false) }
    var wifiLevel by remember { mutableIntStateOf(-1) }

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
            } else {
                wifiLevel = -1
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

    // Содержимое панели
    val content: @Composable () -> Unit = {
        // 1. Статус KIOSK (зеленый замочек)
        if (showKioskStatus && isKioskActive) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Kiosk",
                tint = NeonGreen,
                modifier = Modifier.size(14.dp)
            )
        }

        // 2. Кнопка Wi-Fi (кликабельная -> переход к выбору сетей)
        if (showWifi) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable {
                        try {
                            val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(wifiIntent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = if (isWifiConnected) Icons.Default.Wifi else Icons.Default.WifiOff,
                    contentDescription = "Wi-Fi Settings",
                    tint = if (isWifiConnected) NeonCyan else NeonRed,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // 3. Кнопка регулировки яркости (кликабельная -> слайдер)
        if (showBrightness) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { showBrightnessDialog = true }
                    .padding(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Яркость",
                    tint = NeonOrange,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // 4. Батарея
        if (showBattery) {
            if (isVertical) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Icon(
                        imageVector = batteryIcon,
                        contentDescription = "Battery",
                        tint = if (isCharging) batteryColor.copy(alpha = chargingAlpha) else batteryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$batteryLevel%",
                        color = TextWhite,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = batteryIcon,
                        contentDescription = "Battery",
                        tint = if (isCharging) batteryColor.copy(alpha = chargingAlpha) else batteryColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$batteryLevel%",
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    // Компоновка: вертикальная или горизонтальная
    if (isVertical) {
        Column(
            modifier = modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC111827))
                .border(0.5.dp, Color(0x3300F0FF), RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    } else {
        Row(
            modifier = modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xCC111827))
                .border(0.5.dp, Color(0x3300F0FF), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }

    // Диалог плавной регулировки яркости
    if (showBrightnessDialog) {
        Dialog(onDismissRequest = { showBrightnessDialog = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WbSunny, contentDescription = null, tint = NeonOrange)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Яркость экрана: ${(currentBrightness * 100).roundToInt()}%",
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = currentBrightness,
                        onValueChange = { newVal ->
                            currentBrightness = newVal
                            if (activity != null) {
                                (activity as? MainActivity)?.powerHelper?.setScreenBrightness(activity, newVal)
                            }
                        },
                        valueRange = 0.05f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = CyberSurface
                        )
                    )

                    Button(
                        onClick = { showBrightnessDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Готово", color = CyberBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}