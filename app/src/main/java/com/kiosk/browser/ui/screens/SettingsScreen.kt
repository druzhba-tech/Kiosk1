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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

    // Настройки информационной панели
    var hudOrientation by remember { mutableStateOf(currentConfig.hudOrientation) }
    var hudPosition by remember { mutableStateOf(currentConfig.hudPosition) }
    var hudMarginCm by remember { mutableFloatStateOf(currentConfig.hudTopMarginCm) }
    var hudShowBrightness by remember { mutableStateOf(currentConfig.hudShowBrightness) }
    var hudShowWifi by remember { mutableStateOf(currentConfig.hudShowWifi) }
    var hudShowBattery by remember { mutableStateOf(currentConfig.hudShowBattery) }
    var hudShowKioskStatus by remember { mutableStateOf(currentConfig.hudShowKioskStatus) }

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
                hudOrientation = hudOrientation,
                hudPosition = hudPosition,
                hudTopMarginCm = hudMarginCm,
                hudShowBrightness = hudShowBrightness,
                hudShowWifi = hudShowWifi,
                hudShowBattery = hudShowBattery,
                hudShowKioskStatus = hudShowKioskStatus
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
            // Главный статус киоска
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

            // ── НОВАЯ ВКЛАДКА: НАСТРОЙКИ ИНФОРМАЦИОННОЙ ПАНЕЛИ (HUD) ──
            SettingsCard(title = "Информационная панель (HUD)", icon = Icons.Default.Dashboard) {
                // Ориентация панели
                Text("Ориентация панели:", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = hudOrientation == "VERTICAL",
                        onClick = { hudOrientation = "VERTICAL" },
                        label = { Text("Вертикальная") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = hudOrientation == "HORIZONTAL",
                        onClick = { hudOrientation = "HORIZONTAL" },
                        label = { Text("Горизонтальная") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Расположение на экране
                Text("Расположение панели:", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = hudPosition == "TOP_RIGHT",
                        onClick = { hudPosition = "TOP_RIGHT" },
                        label = { Text("Верх-Право") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = hudPosition == "TOP_LEFT",
                        onClick = { hudPosition = "TOP_LEFT" },
                        label = { Text("Верх-Лево") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Отступ от верхнего края
                Text("Отступ от верхнего края: ${String.format("%.1f", hudMarginCm)} см", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Slider(
                    value = hudMarginCm,
                    onValueChange = { hudMarginCm = it },
                    valueRange = 0.5f..4.0f,
                    steps = 6,
                    colors = SliderDefaults.colors(thumbColor = NeonCyan, activeTrackColor = NeonCyan)
                )

                Spacer(Modifier.height(8.dp))

                // Переключатели элементов
                SettingsToggle(
                    title = "Кнопка регулировки яркости",
                    subtitle = "Быстрый слайдер яркости при нажатии",
                    checked = hudShowBrightness,
                    onCheckedChange = { hudShowBrightness = it }
                )
                SettingsToggle(
                    title = "Кнопка переключения Wi-Fi",
                    subtitle = "Статус сети и открытие настроек Wi-Fi",
                    checked = hudShowWifi,
                    onCheckedChange = { hudShowWifi = it }
                )
                SettingsToggle(
                    title = "Индикатор батареи и проценты",
                    subtitle = "Иконка батареи и заряд в %",
                    checked = hudShowBattery,
                    onCheckedChange = { hudShowBattery = it }
                )
                SettingsToggle(
                    title = "Иконка статуса KIOSK",
                    subtitle = "Зеленый замочек активного режима",
                    checked = hudShowKioskStatus,
                    onCheckedChange = { hudShowKioskStatus = it }
                )
            }

            // Стартовый URL
            SettingsCard(title = "Основной веб-сайт (URL)", icon = Icons.Default.Web) {
                OutlinedTextField(
                    value = startUrl,
                    onValueChange = { newValue: String -> startUrl = newValue },
                    label = { Text("Стартовый URL") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = NeonCyan,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
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

            // Режим одного приложения
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

            // Экран и заставка
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
                    subtitle = "Яркие Cyber HUD часы при отсутствии касаний",
                    checked = screensaverEnabled,
                    onCheckedChange = { screensaverEnabled = it }
                )
                if (screensaverEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = idleTimeout,
                        onValueChange = { newValue: String -> idleTimeout = newValue },
                        label = { Text("Таймаут перехода в заставку (сек)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberBorder,
                            focusedLabelColor = NeonCyan,
                            unfocusedLabelColor = TextMuted,
                            cursorColor = NeonCyan,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
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

            // Веб и сеть
            SettingsCard(title = "Веб и сеть", icon = Icons.Default.Wifi) {
                SettingsToggle(
                    title = "Игнорировать ошибки SSL",
                    subtitle = "Необходимо для локальных серверов (https://192.168.x.x)",
                    checked = ignoreSsl,
                    onCheckedChange = { ignoreSsl = it }
                )
            }

            // Безопасность и PIN
            SettingsCard(title = "Безопасность и PIN-код", icon = Icons.Default.Lock) {
                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { newValue: String -> pinCode = newValue },
                    label = { Text("Мастер-PIN код (по умолчанию: 1234)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = CyberBorder,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = TextMuted,
                        cursorColor = NeonCyan,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggle(
                    title = "Блокировать аппаратные кнопки",
                    subtitle = "Громкость и навигация системы",
                    checked = blockKeys,
                    onCheckedChange = { blockKeys = it }
                )
            }

            // Резервная копия
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
            onConfirm = { selected: List<String> ->
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