package com.kiosk.browser.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiosk.browser.MainActivity
import com.kiosk.browser.data.model.KioskConfig
import com.kiosk.browser.ui.components.AppPickerDialog
import com.kiosk.browser.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    mainActivity: MainActivity,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentConfig by mainActivity.configRepository.configFlow.collectAsState()

    val tfColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = NeonCyan,
        unfocusedBorderColor = CyberBorder,
        focusedLabelColor = NeonCyan,
        unfocusedLabelColor = TextMuted,
        cursorColor = NeonCyan,
        focusedTextColor = TextWhite,
        unfocusedTextColor = TextWhite
    )

    var startUrl by remember { mutableStateOf(currentConfig.startUrl) }
    var pinCode by remember { mutableStateOf(currentConfig.pinCode) }
    var idleTimeout by remember { mutableStateOf(currentConfig.idleTimeoutSeconds.toString()) }
    var mqttBroker by remember { mutableStateOf(currentConfig.mqttBroker) }
    var mqttPort by remember { mutableStateOf(currentConfig.mqttPort.toString()) }

    var isKiosk by remember { mutableStateOf(currentConfig.isKioskEnabled) }
    var isSingleApp by remember { mutableStateOf(currentConfig.isSingleAppMode) }
    var keepScreenOn by remember { mutableStateOf(currentConfig.keepScreenOn) }
    var screensaverEnabled by remember { mutableStateOf(currentConfig.screensaverEnabled) }
    var oledProtection by remember { mutableStateOf(currentConfig.oledBurnInProtection) }
    var ignoreSsl by remember { mutableStateOf(currentConfig.ignoreSslErrors) }
    var blockKeys by remember { mutableStateOf(currentConfig.blockHardwareKeys) }
    var antiTheft by remember { mutableStateOf(currentConfig.antiTheftAlarmEnabled) }
    var mqttEnabled by remember { mutableStateOf(currentConfig.mqttEnabled) }
    var allowedApps by remember { mutableStateOf(currentConfig.allowedApps) }
    var showAppPicker by remember { mutableStateOf(false) }

    fun saveAll() {
        mainActivity.configRepository.updateConfig {
            it.copy(
                startUrl = startUrl,
                pinCode = pinCode,
                idleTimeoutSeconds = idleTimeout.toIntOrNull() ?: 120,
                mqttBroker = mqttBroker,
                mqttPort = mqttPort.toIntOrNull() ?: 1883,
                isKioskEnabled = isKiosk,
                isSingleAppMode = isSingleApp,
                keepScreenOn = keepScreenOn,
                screensaverEnabled = screensaverEnabled,
                oledBurnInProtection = oledProtection,
                ignoreSslErrors = ignoreSsl,
                blockHardwareKeys = blockKeys,
                antiTheftAlarmEnabled = antiTheft,
                mqttEnabled = mqttEnabled,
                allowedApps = allowedApps
            )
        }
        mainActivity.applyConfigUpdates()
        Toast.makeText(context, "Настройки сохранены!", Toast.LENGTH_SHORT).show()
        onClose()
    }

    Scaffold(
        containerColor = CyberBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "KIOSK CONTROL CENTER",
                        color = NeonCyan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextWhite)
                    }
                },
                actions = {
                    Button(
                        onClick = { saveAll() },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Text("СОХРАНИТЬ", color = CyberBlack, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isKiosk) NeonGreen else NeonOrange,
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = CyberCard)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isKiosk) "РЕЖИМ КИОСКА АКТИВЕН" else "РЕЖИМ КИОСКА ОТКЛЮЧЕН",
                            color = if (isKiosk) NeonGreen else NeonOrange,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isKiosk) "Блокировка навигации и выхода включена" else "Свободный доступ к системе",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isKiosk,
                        onCheckedChange = { isKiosk = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberBlack,
                            checkedTrackColor = NeonGreen,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = CyberSurface
                        )
                    )
                }
            }

            SettingsCard(title = "Основной веб-сайт (URL)", icon = Icons.Default.Web) {
                OutlinedTextField(
                    value = startUrl,
                    onValueChange = { startUrl = it },
                    label = { Text("Стартовый URL") },
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { startUrl = "https://demo.home-assistant.io" },
                        label = { Text("Home Assistant") }
                    )
                    AssistChip(
                        onClick = { startUrl = "http://192.168.1.100:8123" },
                        label = { Text("Локальный HA") }
                    )
                }
            }

            SettingsCard(title = "Режим приложений", icon = Icons.Default.Apps) {
                SettingsToggle(
                    title = "Режим одного приложения",
                    subtitle = "Только веб-страница без лаунчера других приложений",
                    checked = isSingleApp,
                    onCheckedChange = { isSingleApp = it }
                )
                if (!isSingleApp) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showAppPicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = NeonCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Выбрать разрешенные приложения (${allowedApps.size})", color = NeonCyan)
                    }
                }
            }

            SettingsCard(title = "Экран и энергосбережение", icon = Icons.Default.WbSunny) {
                SettingsToggle(
                    title = "Держать экран включенным",
                    subtitle = "Предотвращает аппаратное засыпание дисплея",
                    checked = keepScreenOn,
                    onCheckedChange = { keepScreenOn = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggle(
                    title = "Заставка (Скринсейвер)",
                    subtitle = "Cyber HUD часы при отсутствии касаний",
                    checked = screensaverEnabled,
                    onCheckedChange = { screensaverEnabled = it }
                )
                if (screensaverEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = idleTimeout,
                        onValueChange = { idleTimeout = it },
                        label = { Text("Таймаут перехода в заставку (сек)") },
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggle(
                    title = "Защита от выгорания OLED",
                    subtitle = "Микросмещение пикселей каждые несколько минут",
                    checked = oledProtection,
                    onCheckedChange = { oledProtection = it }
                )
            }

            SettingsCard(title = "Веб и сеть", icon = Icons.Default.Wifi) {
                SettingsToggle(
                    title = "Игнорировать ошибки SSL",
                    subtitle = "Необходимо для локальных серверов (https://192.168.x.x)",
                    checked = ignoreSsl,
                    onCheckedChange = { ignoreSsl = it }
                )
            }

            SettingsCard(title = "Безопасность и PIN-код", icon = Icons.Default.Lock) {
                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { pinCode = it },
                    label = { Text("Мастер-PIN код (по умолчанию: 1234)") },
                    colors = tfColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggle(
                    title = "Блокировать аппаратные кнопки",
                    subtitle = "Громкость и навигация системы",
                    checked = blockKeys,
                    onCheckedChange = { blockKeys = it }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Статус Device Owner: ${if (mainActivity.deviceOwnerManager.isDeviceOwner) "АКТИВЕН" else "НЕ АКТИВЕН"}",
                    color = if (mainActivity.deviceOwnerManager.isDeviceOwner) NeonGreen else NeonOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            SettingsCard(title = "Охрана и датчики", icon = Icons.Default.Sensors) {
                SettingsToggle(
                    title = "Антикража (Детектор перемещения)",
                    subtitle = "Срабатывает сирена при попытке снять планшет со стены",
                    checked = antiTheft,
                    onCheckedChange = { antiTheft = it }
                )
            }

            SettingsCard(title = "Интеграция с MQTT & Home Assistant", icon = Icons.Default.Sensors) {
                SettingsToggle(
                    title = "Включить MQTT клиент",
                    subtitle = "Передача телеметрии и удаленное управление планшетом",
                    checked = mqttEnabled,
                    onCheckedChange = { mqttEnabled = it }
                )
                if (mqttEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = mqttBroker,
                        onValueChange = { mqttBroker = it },
                        label = { Text("IP адрес MQTT брокера") },
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = mqttPort,
                        onValueChange = { mqttPort = it },
                        label = { Text("Порт MQTT (по умолчанию 1883)") },
                        colors = tfColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SettingsCard(title = "Резервная копия конфигурации", icon = Icons.Default.Save) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val json = mainActivity.configRepository.exportConfigJson()
                            clipboardManager.setText(AnnotatedString(json))
                            Toast.makeText(context, "Конфигурация скопирована!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Экспорт JSON")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            selectedPackages = allowedApps,
            onConfirm = { selected ->
                allowedApps = selected
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CyberCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = NeonCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = TextMuted, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberBlack,
                checkedTrackColor = NeonCyan,
                uncheckedThumbColor = TextMuted,
                uncheckedTrackColor = CyberSurface
            )
        )
    }
}
