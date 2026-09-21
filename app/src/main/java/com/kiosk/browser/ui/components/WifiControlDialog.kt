package com.kiosk.browser.ui.components

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import com.kiosk.browser.ui.theme.*
import java.net.Inet4Address

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
    val telephonyManager = remember {
        context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
    }

    var isWifiEnabled by remember { mutableStateOf(wifiManager.isWifiEnabled) }
    var isWifiConnected by remember { mutableStateOf(false) }
    var currentSsid by remember { mutableStateOf<String?>(null) }
    var ipAddress by remember { mutableStateOf<String?>(null) }
    var signalLevel by remember { mutableIntStateOf(0) }
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // Состояние сотовой связи / SIM-карты
    var isCellularConnected by remember { mutableStateOf(false) }
    var cellularOperator by remember { mutableStateOf<String?>(null) }
    var cellularNetworkType by remember { mutableStateOf("4G / LTE") }
    var cellularIpAddress by remember { mutableStateOf<String?>(null) }

    // Состояние для подключения к выбранной сети
    var selectedNetworkForPassword by remember { mutableStateOf<ScanResult?>(null) }
    var enteredPassword by remember { mutableStateOf("") }
    var isConnecting by remember { mutableStateOf(false) }

    // Функция обновления текущего статуса всех сетевых подключений
    fun updateCurrentConnection() {
        isWifiEnabled = wifiManager.isWifiEnabled
        try {
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            val linkProps = connectivityManager.getLinkProperties(activeNetwork)

            val realIp = linkProps?.linkAddresses
                ?.map { it.address }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull()?.hostAddress

            isWifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            isCellularConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

            if (isWifiConnected) {
                var detectedSsid: String? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val info = caps?.transportInfo as? WifiInfo
                    val s = info?.ssid?.trim('\"')
                    if (!s.isNullOrBlank() && s != "<unknown ssid>") {
                        detectedSsid = s
                    }
                }
                if (detectedSsid == null) {
                    val s = wifiManager.connectionInfo?.ssid?.trim('\"')
                    if (!s.isNullOrBlank() && s != "<unknown ssid>") {
                        detectedSsid = s
                    }
                }
                currentSsid = detectedSsid ?: "Беспроводная сеть Wi-Fi"
                ipAddress = realIp ?: run {
                    val ip = wifiManager.connectionInfo?.ipAddress ?: 0
                    if (ip != 0) Formatter.formatIpAddress(ip) else null
                }
                val info = wifiManager.connectionInfo
                signalLevel = if (info != null) WifiManager.calculateSignalLevel(info.rssi, 5) else 4
            } else {
                currentSsid = null
                ipAddress = null
                signalLevel = 0
            }

            val tm = telephonyManager
            if (tm != null && (tm.simState == TelephonyManager.SIM_STATE_READY || isCellularConnected)) {
                val opName = tm.networkOperatorName.takeIf { !it.isNullOrBlank() }
                    ?: tm.simOperatorName.takeIf { !it.isNullOrBlank() }
                    ?: "SIM карта"
                cellularOperator = opName
                cellularIpAddress = if (isCellularConnected) realIp else null
                cellularNetworkType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    when (tm.dataNetworkType) {
                        TelephonyManager.NETWORK_TYPE_LTE -> "4G / LTE"
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA -> "3G / H+"
                        else -> "Мобильная сеть"
                    }
                } else "4G / LTE"
            } else {
                cellularOperator = null
                cellularIpAddress = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            scanResults = results
                .filter { !it.SSID.isNullOrBlank() }
                .distinctBy { it.SSID }
                .sortedByDescending { it.level }
        } catch (_: Exception) {}
        isScanning = false
    }

    // Запрос разрешения геолокации для сканирования сетей на Android 10+
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startScan()
        }
    }

    // Функция вызова системных панелей Android
    fun openSystemNetworkPanel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val panelIntent = Intent(Settings.Panel.ACTION_WIFI).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(panelIntent)
                return
            } catch (_: Exception) {}

            try {
                val internetPanel = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(internetPanel)
                return
            } catch (_: Exception) {}
        }

        try {
            val wifiIntent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(wifiIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "Системные настройки сети недоступны", Toast.LENGTH_SHORT).show()
        }
    }

    fun openMobileDataSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val internetPanel = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(internetPanel)
                return
            } catch (_: Exception) {}
        }

        try {
            val roamingIntent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(roamingIntent)
        } catch (_: Exception) {
            try {
                val netIntent = Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(netIntent)
            } catch (_: Exception) {
                openSystemNetworkPanel()
            }
        }
    }

    // Подключение к сети по паролю
    fun connectToNetwork(scanResult: ScanResult, password: String) {
        val ssid = scanResult.SSID ?: return
        isConnecting = true

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                openSystemNetworkPanel()
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

    // Слушатель событий изменения Wi-Fi и сети
    DisposableEffect(Unit) {
        updateCurrentConnection()
        startScan()

        // Проверяем разрешение локации для поиска Wi-Fi сетей
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

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
            @Suppress("DEPRECATION")
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
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
                .fillMaxHeight(0.88f)
                .padding(6.dp)
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
                            text = "СЕТЬ И ПОДКЛЮЧЕНИЯ",
                            color = NeonCyan,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = TextMuted)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 2. Блок статуса Wi-Fi
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberSurface),
                    border = BorderStroke(1.dp, if (isWifiConnected || currentSsid != null) NeonGreen.copy(alpha = 0.4f) else CyberBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = if (isWifiConnected || currentSsid != null) NeonGreen else TextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (currentSsid != null) "Wi-Fi: $currentSsid" else if (isWifiEnabled) "Wi-Fi: Не подключено" else "Wi-Fi выключен",
                                    color = if (currentSsid != null) NeonGreen else TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (ipAddress != null) {
                                    Text(
                                        text = "IP: $ipAddress",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { openSystemNetworkPanel() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Панель", fontSize = 11.sp)
                        }
                    }
                }

                // 3. Блок статуса сотовой связи / SIM-карты (если поддерживается)
                if (cellularOperator != null || isCellularConnected) {
                    Spacer(Modifier.height(8.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberSurface),
                        border = BorderStroke(1.dp, if (isCellularConnected) NeonCyan.copy(alpha = 0.4f) else CyberBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = Icons.Default.SignalCellularAlt,
                                    contentDescription = null,
                                    tint = if (isCellularConnected) NeonCyan else TextMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "${cellularOperator ?: "SIM"} ($cellularNetworkType)",
                                        color = if (isCellularConnected) NeonCyan else TextWhite,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = if (isCellularConnected) "Передача данных активна${if (cellularIpAddress != null) " (IP: $cellularIpAddress)" else ""}" else "Мобильные данные отключены",
                                        color = if (isCellularConnected) NeonGreen else TextMuted,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = { openMobileDataSettings() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.SettingsCell, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Настройки", fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 4. Заголовок списка сетей и кнопка обновить
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "ДОСТУПНЫЕ WI-FI СЕТИ (${scanResults.size})",
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

                Spacer(Modifier.height(4.dp))

                // 5. Список найденных сетей
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (scanResults.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (!isWifiEnabled) "Включите Wi-Fi для поиска сетей" else "Сети поблизости сканируются...",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                Button(
                                    onClick = { openSystemNetworkPanel() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.WifiFind, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Выбрать сеть через панель Android", color = NeonCyan, fontSize = 12.sp)
                                }
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
                                    .padding(vertical = 3.dp)
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
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
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
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = ssid,
                                                color = if (isCurrent) NeonCyan else TextWhite,
                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                fontSize = 13.sp
                                            )
                                            if (isCurrent) {
                                                Text(
                                                    text = "Подключено",
                                                    color = NeonGreen,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }

                                    if (isSecure) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Защищенная сеть",
                                            tint = TextMuted,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

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
