package com.kiosk.browser.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kiosk.browser.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun WifiControlDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    val connectivityManager = remember {
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    var isWifiEnabled by remember { mutableStateOf(wifiManager.isWifiEnabled) }
    var currentSsid by remember { mutableStateOf<String?>(null) }
    var ipAddress by remember { mutableStateOf<String?>(null) }
    var signalLevel by remember { mutableIntStateOf(0) }
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // Состояние для подключения к выбранной сети
    var selectedNetworkForPassword by remember { mutableStateOf<ScanResult?>(null) }
    var enteredPassword by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }

    // Функция обновления текущего статуса подключения
    fun updateCurrentConnection() {
        isWifiEnabled = wifiManager.isWifiEnabled
        if (!isWifiEnabled) {
            currentSsid = null
            ipAddress = null
            signalLevel = 0
            return
        }

        val info: WifiInfo? = wifiManager.connectionInfo
        if (info != null && info.networkId != -1) {
            val rawSsid = info.ssid?.trim('\"')
            currentSsid = if (rawSsid != null && rawSsid != "<unknown ssid>") rawSsid else "Подключено к Wi-Fi"
            val ip = info.ipAddress
            ipAddress = if (ip != 0) Formatter.formatIpAddress(ip) else null
            signalLevel = WifiManager.calculateSignalLevel(info.rssi, 5)
        } else {
            currentSsid = null
            ipAddress = null
            signalLevel = 0
        }
    }

    // Функция сканирования сетей
    fun startScan() {
        if (!wifiManager.isWifiEnabled) return
        isScanning = true
        try {
            @Suppress("DEPRECATION")
            wifiManager.startScan()
            val results = runCatching { wifiManager.scanResults }.getOrNull() ?: emptyList()
            // Фильтруем дубликаты SSID и пустые имена
            scanResults = results
                .filter { !it.SSID.isNullOrBlank() }
                .distinctBy { it.SSID }
                .sortedByDescending { it.level }
        } catch (_: Exception) {}
        isScanning = false
    }

    // Подключение к сети по паролю (WPA/WPA2/WPA3 или открытая)
    fun connectToNetwork(scanResult: ScanResult, password: String) {
        val ssid = scanResult.SSID ?: return
        isConnecting = true

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // В Android 10+ открываем системную панель подключения к Wi-Fi
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
                selectedNetworkForPassword = null
                isConnecting = false
                return
            }

            @Suppress("DEPRECATION")
            val conf = WifiConfiguration().apply {
                SSID = "\"$ssid\""
                if (password.isNotEmpty()) {
                    preSharedKey = "\"$password\""
                } else {
                    allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE)
                }
            }

            @Suppress("DEPRECATION")
            val netId = wifiManager.addNetwork(conf)
            if (netId != -1) {
                @Suppress("DEPRECATION")
                wifiManager.disconnect()
                @Suppress("DEPRECATION")
                wifiManager.enableNetwork(netId, true)
                @Suppress("DEPRECATION")
                wifiManager.reconnect()
                Toast.makeText(context, "Подключение к $ssid...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Не удалось настроить сеть $ssid", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Ошибка подключения: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            selectedNetworkForPassword = null
            enteredPassword = ""
            isConnecting = false
        }
    }

    // Слушатель событий изменения Wi-Fi
    DisposableEffect(Unit) {
        updateCurrentConnection()
        startScan()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                updateCurrentConnection()
                val results = runCatching { wifiManager.scanResults }.getOrNull() ?: emptyList()
                scanResults = results
                    .filter { !it.SSID.isNullOrBlank() }
                    .distinctBy { it.SSID }
                    .sortedByDescending { it.level }
            }
        }

        val filter = IntentFilter().apply {
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        }
        context.registerReceiver(receiver, filter)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CyberCard),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(8.dp)
                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // 1. Заголовок
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "УПРАВЛЕНИЕ WI-FI",
                            color = NeonCyan,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = TextMuted)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 2. Блок текущего статуса
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(1.dp, if (currentSsid != null) NeonGreen.copy(alpha = 0.4f) else CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentSsid != null) "Подключено: $currentSsid" else if (isWifiEnabled) "Не подключено" else "Wi-Fi выключен",
                                color = if (currentSsid != null) NeonGreen else TextWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (ipAddress != null) {
                                Text(
                                    text = "IP: $ipAddress",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Кнопка вызова официальной шторки Wi-Fi Android (Settings.Panel)
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    try {
                                        val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(panelIntent)
                                        return@OutlinedButton
                                    } catch (_: Exception) {}

                                    try {
                                        val internetPanel = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(internetPanel)
                                        return@OutlinedButton
                                    } catch (_: Exception) {}
                                }

                                // Для Android 9 и ниже открываем только Wi-Fi окно без перехода в общие настройки
                                try {
                                    val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(wifiIntent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Панель Wi-Fi недоступна", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Панель", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 3. Заголовок списка доступных сетей и кнопка обновить
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ДОСТУПНЫЕ СЕТИ (${scanResults.size})",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(
                        onClick = { startScan() },
                        enabled = !isScanning && isWifiEnabled
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isScanning) "Поиск..." else "Обновить", color = NeonCyan, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 4. Список найденных Wi-Fi сетей
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (scanResults.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (!isWifiEnabled) "Включите Wi-Fi для поиска сетей" else "Поиск сетей поблизости...",
                                    color = TextMuted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(scanResults) { result ->
                            val ssid = result.SSID
                            val isCurrent = ssid == currentSsid
                            val isSecure = result.capabilities.contains("WPA") || result.capabilities.contains("WEP")
                            val bars = WifiManager.calculateSignalLevel(result.level, 4)

                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) NeonCyan.copy(alpha = 0.12f) else CyberSurface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isCurrent) NeonCyan else Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        if (isSecure) {
                                            selectedNetworkForPassword = result
                                            enteredPassword = ""
                                        } else {
                                            connectToNetwork(result, "")
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = if (isCurrent) NeonGreen else if (bars >= 2) NeonCyan else TextMuted,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = ssid,
                                                color = if (isCurrent) NeonCyan else TextWhite,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 14.sp
                                            )
                                            if (isCurrent) {
                                                Text(
                                                    text = "Подключено",
                                                    color = NeonGreen,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }

                                    if (isSecure) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Защищенная сеть",
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Кнопка закрыть
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                    border = BorderStroke(1.dp, CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Закрыть", color = TextWhite)
                }
            }
        }
    }

    // Всплывающий диалог ввода пароля к выбранной сети
    if (selectedNetworkForPassword != null) {
        val targetNetwork = selectedNetworkForPassword!!
        AlertDialog(
            onDismissRequest = { selectedNetworkForPassword = null },
            containerColor = CyberCard,
            title = {
                Text(
                    text = "Подключение к ${targetNetwork.SSID}",
                    color = NeonCyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Введите пароль безопасности:",
                        color = TextWhite,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = enteredPassword,
                        onValueChange = { enteredPassword = it },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        connectToNetwork(targetNetwork, enteredPassword)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Подключиться", color = CyberBlack, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedNetworkForPassword = null }) {
                    Text("Отмена", color = TextMuted)
                }
            }
        )
    }
}
