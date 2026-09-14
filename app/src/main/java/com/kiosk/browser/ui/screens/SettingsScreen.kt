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

    // OTA обновления
    var updateCheckEnabled by remember { mutableStateOf(currentConfig.updateCheckEnabled) }
    var updateManifestUrl by remember { mutableStateOf(currentConfig.updateManifestUrl) }
    var updateCheckIntervalHours by remember { mutableStateOf(currentConfig.updateCheckIntervalHours.toString()) }

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
                allowedApps = allowedApps,
                updateCheckEnabled = updateCheckEnabled,
                updateManifestUrl = updateManifestUrl,
                updateCheckIntervalHours = updateCheckIntervalHours.toIntOrNull() ?: 6
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
            // ── ГЛАВНАЯ КНОПКА: Включить/Выключить режим киоска ──────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isKiosk) 1.5.dp else 0.5.dp,
                        color = if (isKiosk) NeonGreen else NeonOrange,
                        shape = RoundedCornerShape(16.dp)
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isKiosk)
                        Color(0xFF0D2018)  // тёмно-зелёный при активном киоске
                    else
                        Color(0xFF1A1208)  // тёмно-оранжевый при неактивном
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (isKiosk) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (isKiosk) NeonGreen else NeonOrange,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = if (isKiosk) "KIOSK MODE АКТИВЕН" else "KIOSK MODE ВЫКЛЮЧЕН",
                                color = if (isKiosk) NeonGreen else NeonOrange,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (isKiosk)
                                    "Устройство заблокировано в режиме киоска"
                                else
                                    "Нажмите переключатель для включения",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    Switch(
                        checked = isKiosk,
                        onCheckedChange = { isKiosk = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonGreen,
                            checkedTrackColor = Color(0xFF1A4030),
                            uncheckedThumbColor = NeonOrange,
                            uncheckedTrackColor = Color(0xFF2A1A08)
                        )
                    )
                }
            }

            // Раздел: Основные параметры Web
            SettingsCard(title = "ВЕБ-СТРАНИЦА И БРАУЗЕР", icon = Icons.Default.Language) {
                OutlinedTextField(
                    value = startUrl,
                    onValueChange = { startUrl = it },
                    label = { Text("Стартовый URL") },
                    colors = cyberTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                SettingsToggle(
                    title = "Игнорировать ошибки SSL",
                    subtitle = "Необходимо для локальных серверов Home Assistant (https://192.168.x.x)",
                    checked = ignoreSsl,
                    onCheckedChange = { ignoreSsl = it }
                )
            }

            // Раздел: Приложения и Лаунчер
            SettingsCard(title = "ПРИЛОЖЕНИЯ И ЛАУНЧЕР", icon = Icons.Default.Apps) {
                SettingsToggle(
                    title = "Одиночный режим (Single App)",
                    subtitle = "Полноэкранная веб-страница без лаунчера других приложений",
                    checked = isSingleApp,
                    onCheckedChange = { isSingleApp = it }
                )

                if (!isSingleApp) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { showAppPicker = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = CyberBlack)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (allowedApps.isEmpty()) "ВЫБРАТЬ ПРИЛОЖЕНИЯ (РАЗРЕШЕНЫ ВСЕ)"
                            else "ВЫБРАТЬ ПРИЛОЖЕНИЯ (${allowedApps.size} ВЫБРАНО)",
                            color = CyberBlack,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (allowedApps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Разрешённые (${allowedApps.size}):",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            TextButton(
                                onClick = { allowedApps = emptyList() },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Сбросить (все)", color = NeonOrange, fontSize = 11.sp)
                            }
                        }

                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            items(allowedApps.size) { index ->
                                val pkg = allowedApps[index]
                                Surface(
                                    color = CyberSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, CyberBorder)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = pkg.substringAfterLast('.'),
                                            color = TextWhite,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = { allowedApps = allowedApps - pkg },
                                            modifier = Modifier.size(18.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Удалить",
                                                tint = NeonRed,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Сейчас в лаунчере доступны все приложения планшета. Нажмите кнопку выше, чтобы разрешить только нужные.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Раздел: Режим киоска и защита
            SettingsCard(title = "РЕЖИМ КИОСКА И БЕЗОПАСНОСТЬ", icon = Icons.Default.Security) {
                SettingsToggle(
                    title = "Блокировка кнопок громкости",
                    subtitle = "Перехват аппаратных клавиш устройства",
                    checked = blockKeys,
                    onCheckedChange = { blockKeys = it }
                )

                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { pinCode = it },
                    label = { Text("PIN-код администратора") },
                    colors = cyberTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Статус Device Owner: ${if (mainActivity.deviceOwnerManager.isDeviceOwner) "АКТИВЕН ✓" else "НЕ АКТИВЕН (Обычные права)"}",
                    color = if (mainActivity.deviceOwnerManager.isDeviceOwner) NeonGreen else NeonOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                val isDefaultHome = remember { mainActivity.deviceOwnerManager.isDefaultLauncher() }
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        mainActivity.deviceOwnerManager.requestDefaultLauncher(mainActivity)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDefaultHome) Color(0xFF133826) else NeonCyan
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = null,
                        tint = if (isDefaultHome) NeonGreen else CyberBlack
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isDefaultHome) "✓ ГЛАВНЫЙ ЭКРАН ПО УМОЛЧАНИЮ" else "СДЕЛАТЬ ГЛАВНЫМ ЭКРАНОМ (В 1 КЛИК)",
                        color = if (isDefaultHome) NeonGreen else CyberBlack,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Раздел: Экран и питание
            SettingsCard(title = "ЭКРАН И ЭНЕРГОСБЕРЕЖЕНИЕ", icon = Icons.Default.BrightnessMedium) {
                SettingsToggle(
                    title = "Держать экран включенным (Keep Screen On)",
                    subtitle = "Предотвращает засыпание дисплея",
                    checked = keepScreenOn,
                    onCheckedChange = { keepScreenOn = it }
                )

                SettingsToggle(
                    title = "Защита OLED от выгорания (Pixel Shift)",
                    subtitle = "Периодическое смещение интерфейса на 1-2px",
                    checked = oledProtection,
                    onCheckedChange = { oledProtection = it }
                )

                SettingsToggle(
                    title = "Хранитель экрана (Screensaver)",
                    subtitle = "Кибер-часы при бездействии",
                    checked = screensaverEnabled,
                    onCheckedChange = { screensaverEnabled = it }
                )

                OutlinedTextField(
                    value = idleTimeout,
                    onValueChange = { idleTimeout = it },
                    label = { Text("Таймаут бездействия (сек)") },
                    colors = cyberTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Раздел: Сенсоры и антивор
            SettingsCard(title = "ДАТЧИКИ И АНТИВОР", icon = Icons.Default.Sensors) {
                SettingsToggle(
                    title = "Антивор (Детекция перемещения)",
                    subtitle = "Срабатывание при снятии планшета со стены",
                    checked = antiTheft,
                    onCheckedChange = { antiTheft = it }
                )
            }

            // Раздел: MQTT и Home Assistant
            SettingsCard(title = "MQTT & HOME ASSISTANT", icon = Icons.Default.Hub) {
                SettingsToggle(
                    title = "Активировать MQTT клиент",
                    subtitle = "Отправка статуса батареи и прием команд управления",
                    checked = mqttEnabled,
                    onCheckedChange = { mqttEnabled = it }
                )

                OutlinedTextField(
                    value = mqttBroker,
                    onValueChange = { mqttBroker = it },
                    label = { Text("IP адрес MQTT брокера") },
                    colors = cyberTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = mqttPort,
                    onValueChange = { mqttPort = it },
                    label = { Text("Порт MQTT (по умолчанию 1883)") },
                    colors = cyberTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Раздел: Импорт / Экспорт конфигурации
            SettingsCard(title = "УПРАВЛЕНИЕ КОНФИГУРАЦИЕЙ", icon = Icons.Default.Save) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val json = mainActivity.configRepository.exportConfigJson()
                            clipboardManager.setText(AnnotatedString(json))
                            Toast.makeText(context, "Конфиг скопирован в буфер обмена!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                    ) {
                        Text("Экспорт JSON")
                    }

                    OutlinedButton(
                        onClick = {
                            val clip = clipboardManager.getText()?.text
                            if (!clip.isNullOrBlank()) {
                                val result = mainActivity.configRepository.importConfigJson(clip)
                                if (result.isSuccess) {
                                    Toast.makeText(context, "Конфиг успешно импортирован!", Toast.LENGTH_SHORT).show()
                                    onClose()
                                } else {
                                    Toast.makeText(context, "Ошибка разбора JSON!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Буфер обмена пуст!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonGreen)
                    ) {
                        Text("Импорт JSON")
                    }
                }
            }

            // Раздел: OTA Обновления
            SettingsCard(title = "OTA ОБНОВЛЕНИЯ", icon = Icons.Default.SystemUpdate) {
                SettingsToggle(
                    title = "Автоматическая проверка обновлений",
                    subtitle = "Приложение будет проверять наличие новой версии по расписанию",
                    checked = updateCheckEnabled,
                    onCheckedChange = { updateCheckEnabled = it }
                )

                if (updateCheckEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = updateManifestUrl,
                        onValueChange = { updateManifestUrl = it },
                        label = { Text("URL манифеста обновления") },
                        placeholder = { Text("http://192.168.1.100/update.json", color = TextMuted) },
                        colors = cyberTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Формат файла: {\"version_name\":\"1.2.0\",\"apk_url\":\"http://.../app.apk\"}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = updateCheckIntervalHours,
                        onValueChange = { updateCheckIntervalHours = it },
                        label = { Text("Интервал проверки (часов)") },
                        colors = cyberTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Кнопка ручной проверки
                    Button(
                        onClick = {
                            val app = mainActivity.application as com.kiosk.browser.KioskApp
                            if (updateManifestUrl.isNotBlank()) {
                                app.updateManager.checkForUpdate()
                                Toast.makeText(context, "Проверка обновлений запущена...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Укажите URL манифеста!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = CyberBlack, modifier = androidx.compose.ui.Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ПРОВЕРИТЬ СЕЙЧАС", color = CyberBlack, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Раздел: Аварийный выход
            OutlinedButton(
                onClick = {
                    mainActivity.exitKioskMode()
                    Toast.makeText(context, "Киоск выключен", Toast.LENGTH_SHORT).show()
                    onClose()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = NeonRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ВЫЙТИ ИЗ РЕЖИМА КИОСКА (UNLOCK)")
            }
        }
    }

    if (showAppPicker) {
        AppPickerDialog(
            selectedPackages = allowedApps,
            onConfirm = { selectedList ->
                allowedApps = selectedList
                showAppPicker = false
            },
            onDismiss = {
                showAppPicker = false
            }
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
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, CyberBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = NeonCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NeonCyan,
                checkedTrackColor = CyberSurface
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun cyberTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonCyan,
    unfocusedBorderColor = CyberBorder,
    focusedLabelColor = NeonCyan,
    unfocusedLabelColor = TextMuted,
    cursorColor = NeonCyan,
    focusedTextColor = TextWhite,
    unfocusedTextColor = TextWhite
)
