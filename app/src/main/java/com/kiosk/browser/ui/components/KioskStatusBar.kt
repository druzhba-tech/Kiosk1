package com.kiosk.browser.ui.components

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Информационная панель (HUD) для Kiosk Mode.
 *
 * Особенности:
 * 1. Интеллектуальный индикатор сети:
 *    - Wi-Fi (с уровнем и переходом к выбору точек)
 *    - Мобильная связь (Cellular 4G/LTE со шкалой делений и переходом к сотовым настройкам)
 *    - Красный индикатор отсутствия сети
 * 2. Увеличение при касании (Zoom/Expand):
 *    - В покое: миниатюрная, не закрывает контент сайта
 *    - При касании: плавно масштабируется и раскрывается в увеличенные кнопки
 *    - Автоматически сворачивается обратно через 5 секунд покоя
 * 3. Быстрая регулировка яркости экрана со слайдером
 * 4. Батарея с отображением точного % заряда и индикацией питания
 * 5. Вертикальная или горизонтальная ориентация
 */
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
    updateVersion: String? = null,
    updateProgress: Int? = null,
    isReadyToInstall: Boolean = false,
    isInstallingUpdate: Boolean = false,
    onUpdateClick: (() -> Unit)? = null,
    onLauncherClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // Состояние диалога яркости
    var showBrightnessDialog by remember { mutableStateOf(false) }
    var currentBrightness by remember { mutableFloatStateOf(0.8f) }

    // Состояние увеличения панели при касании
    var isExpanded by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    // Автоматическое сворачивание через 5 секунд после последнего касания
    LaunchedEffect(isExpanded, lastInteractionTime) {
        if (isExpanded) {
            delay(5000L)
            isExpanded = false
        }
    }

    // Отслеживание сети (Wi-Fi и Мобильная сеть / Сотовая связь)
    var isWifiConnected by remember { mutableStateOf(false) }
    var isCellularConnected by remember { mutableStateOf(false) }
    var cellSignalLevel by remember { mutableIntStateOf(3) } // 0..4
    var networkLabel by remember { mutableStateOf("Wi-Fi") }

    DisposableEffect(context) {
        fun updateNetwork() {
            try {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                val activeNetwork = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(activeNetwork)

                isWifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                isCellularConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

                if (isWifiConnected) {
                    networkLabel = "Wi-Fi"
                } else if (isCellularConnected) {
                    networkLabel = "4G"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val level = tm?.signalStrength?.level ?: 3
                        cellSignalLevel = level
                    }
                } else {
                    networkLabel = "OFF"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        updateNetwork()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                updateNetwork()
            }
        }
        val filter = IntentFilter().apply {
            addAction(WifiManager.RSSI_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            @Suppress("DEPRECATION")
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Анимация пульсации при зарядке
    val chargingAlpha by rememberInfiniteTransition(label = "charging").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "chargingAlpha"
    )

    // Анимация масштабирования панели при касании
    val panelScale by animateFloatAsState(
        targetValue = if (isExpanded) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "panelScale"
    )

    val iconSize by animateDpAsState(
        targetValue = if (isExpanded) 20.dp else 14.dp,
        label = "iconSize"
    )

    val itemPadding by animateDpAsState(
        targetValue = if (isExpanded) 5.dp else 2.dp,
        label = "itemPadding"
    )

    // Анимация пульсации при наличии обновления
    val hasUpdate = updateVersion != null || updateProgress != null || isReadyToInstall || isInstallingUpdate
    val updatePulse = rememberInfiniteTransition(label = "updatePulse")
    val updateGlowAlpha by updatePulse.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "updateGlowAlpha"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            hasUpdate   -> NeonGreen.copy(alpha = updateGlowAlpha)
            isExpanded  -> NeonCyan
            else        -> Color(0x3300F0FF)
        },
        label = "borderColor"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = if (isExpanded) Color(0xF00B0F19) else Color(0xCC111827),
        label = "bgColor"
    )

    // Иконка и цвет батареи
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

    // Выбор сетевой иконки и цвета
    val (netIcon: ImageVector, netColor: Color) = when {
        isWifiConnected -> {
            Icons.Default.Wifi to NeonCyan
        }
        isCellularConnected -> {
            when {
                cellSignalLevel >= 3 -> Icons.Default.SignalCellularAlt to NeonCyan
                cellSignalLevel >= 1 -> Icons.Default.SignalCellularAlt to NeonOrange
                else                 -> Icons.Default.SignalCellularAlt to NeonRed
            }
        }
        else -> {
            Icons.Default.SignalCellularOff to NeonRed
        }
    }

    // Функция обновления времени взаимодействия (продлевает раскрытое состояние)
    fun notifyInteraction() {
        isExpanded = true
        lastInteractionTime = System.currentTimeMillis()
    }

    // Содержимое элементов панели
    val content: @Composable () -> Unit = {
        // 1. Статус KIOSK (зеленый замочек)
        if (showKioskStatus && isKioskActive) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        notifyInteraction()
                    }
                    .padding(itemPadding)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Kiosk Active",
                    tint = NeonGreen,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        // 2. Интеллектуальная кнопка сети (Wi-Fi или Мобильная сеть 4G)
        if (showWifi) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isExpanded) CyberSurface.copy(alpha = 0.6f) else Color.Transparent)
                    .clickable {
                        notifyInteraction()
                        val mainAct = (context as? MainActivity) ?: MainActivity.currentInstance
                        if (mainAct != null) {
                            mainAct.openWifiSettings()
                        } else {
                            try {
                                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(fallback)
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                }
                            }
                        }
                    }
                    .padding(itemPadding)
            ) {
                if (isExpanded && isCellularConnected) {
                    // В увеличенном виде для сотовой связи показываем иконку и подпись "4G"
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Icon(
                            imageVector = netIcon,
                            contentDescription = "Mobile Data",
                            tint = netColor,
                            modifier = Modifier.size(iconSize)
                        )
                        Text(
                            text = "4G",
                            color = netColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Icon(
                        imageVector = netIcon,
                        contentDescription = "Network: $networkLabel",
                        tint = netColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }

        // 3. Кнопка регулировки яркости
        if (showBrightness) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isExpanded) CyberSurface.copy(alpha = 0.6f) else Color.Transparent)
                    .clickable {
                        notifyInteraction()
                        showBrightnessDialog = true
                    }
                    .padding(itemPadding)
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Яркость",
                    tint = NeonOrange,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        // 4. Батарея с процентами
        if (showBattery) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isExpanded) CyberSurface.copy(alpha = 0.6f) else Color.Transparent)
                    .clickable {
                        notifyInteraction()
                    }
                    .padding(itemPadding)
            ) {
                if (isVertical) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Icon(
                            imageVector = batteryIcon,
                            contentDescription = "Battery",
                            tint = if (isCharging) batteryColor.copy(alpha = chargingAlpha) else batteryColor,
                            modifier = Modifier.size(iconSize)
                        )
                        Text(
                            text = "$batteryLevel%",
                            color = TextWhite,
                            fontSize = if (isExpanded) 11.sp else 9.sp,
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
                            modifier = Modifier.size(iconSize)
                        )
                        Text(
                            text = "$batteryLevel%",
                            color = TextWhite,
                            fontSize = if (isExpanded) 12.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 5. Заметный индикатор обновления в панели (OTA) - только иконка
        if (hasUpdate) {
            val badgeColor = when {
                isInstallingUpdate     -> NeonOrange
                isReadyToInstall       -> NeonGreen
                updateProgress != null -> NeonCyan
                else                   -> NeonCyan
            }
            val badgeIcon = when {
                isInstallingUpdate     -> Icons.Default.CloudDownload
                isReadyToInstall       -> Icons.Default.SystemUpdate
                updateProgress != null -> Icons.Default.CloudDownload
                else                   -> Icons.Default.SystemUpdate
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeColor.copy(alpha = 0.2f + updateGlowAlpha * 0.25f))
                    .border(BorderStroke(1.dp, badgeColor.copy(alpha = updateGlowAlpha)), RoundedCornerShape(6.dp))
                    .clickable {
                        notifyInteraction()
                        onUpdateClick?.invoke()
                    }
                    .padding(itemPadding)
            ) {
                Icon(
                    imageVector = badgeIcon,
                    contentDescription = "Update",
                    tint = badgeColor,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        // В увеличенном виде: кнопка сворачивания (крестик)
        if (isExpanded) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable {
                        isExpanded = false
                    }
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Collapse HUD",
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }

    // Контейнер панели (вертикальный или горизонтальный)
    val panelModifier = modifier
        .scale(panelScale)
        .wrapContentSize()
        .clip(RoundedCornerShape(if (isExpanded) 14.dp else 8.dp))
        .background(animatedBgColor)
        .border(
            width = if (isExpanded) 1.5.dp else 0.5.dp,
            color = animatedBorderColor,
            shape = RoundedCornerShape(if (isExpanded) 14.dp else 8.dp)
        )
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            notifyInteraction()
        }

    if (isVertical) {
        Column(
            modifier = panelModifier.padding(
                horizontal = if (isExpanded) 8.dp else 5.dp,
                vertical = if (isExpanded) 10.dp else 6.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (isExpanded) 10.dp else 6.dp)
        ) {
            content()
        }
    } else {
        Row(
            modifier = panelModifier.padding(
                horizontal = if (isExpanded) 10.dp else 7.dp,
                vertical = if (isExpanded) 8.dp else 4.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isExpanded) 10.dp else 6.dp)
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
