package com.kiosk.browser.ui.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiosk.browser.MainActivity
import com.kiosk.browser.core.update.UpdateState
import com.kiosk.browser.data.model.KioskConfig
import com.kiosk.browser.ui.components.AppPickerDialog
import com.kiosk.browser.ui.theme.*
import kotlinx.coroutines.launch

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
    var showPrimaryAppPicker by remember { mutableStateOf(false) }
    var primaryMode by remember { mutableStateOf(currentConfig.primaryMode) }
    var primaryAppPackage by remember { mutableStateOf(currentConfig.primaryAppPackage) }
    var isFirstLaunchCompleted by remember { mutableStateOf(currentConfig.isFirstLaunchCompleted) }

    // Настройки информационной панели
    var hudOrientation by remember { mutableStateOf(currentConfig.hudOrientation) }
    var hudPosition by remember { mutableStateOf(currentConfig.hudPosition) }
    var hudMarginCm by remember { mutableFloatStateOf(currentConfig.hudTopMarginCm) }
    var hudShowBrightness by remember { mutableStateOf(currentConfig.hudShowBrightness) }
    var hudShowWifi by remember { mutableStateOf(currentConfig.hudShowWifi) }
    var hudShowBattery by remember { mutableStateOf(currentConfig.hudShowBattery) }
    var hudShowKioskStatus by remember { mutableStateOf(currentConfig.hudShowKioskStatus) }

    val coroutineScope = rememberCoroutineScope()
    val packageInfo = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
    }
    val appVersionName = packageInfo?.versionName ?: "1.0.9"
    val appVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo?.longVersionCode ?: 10L
    } else {
        @Suppress("DEPRECATION")
        packageInfo?.versionCode?.toLong() ?: 10L
    }
    val updateState by mainActivity.updateManager.updateState.collectAsState()

    fun saveAll() {
        mainActivity.configRepository.updateConfig {
            it.copy(
                startUrl = startUrl,
                primaryMode = primaryMode,
                primaryAppPackage = primaryAppPackage,
                isFirstLaunchCompleted = isFirstLaunchCompleted,
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
                    Column {
                        Text(
                            text = "KIOSK CONTROL CENTER",
                            color = NeonCyan,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Версия: v$appVersionName (сборка $appVersionCode)",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
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
            // Карточка версии приложения и статуса обновлений
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = NeonCyan.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = CyberCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ВЕРСИЯ ПРИЛОЖЕНИЯ",
                                    color = NeonCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "v$appVersionName (сборка $appVersionCode)",
                                    color = TextWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        // Кнопка принудительной проверки обновлений
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    Toast.makeText(context, "Проверка обновлений...", Toast.LENGTH_SHORT).show()
                                    mainActivity.updateManager.checkForUpdates(appVersionName)
                                }
                            },
                            enabled = updateState !is UpdateState.Checking && updateState !is UpdateState.Downloading,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.7f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Проверить", fontSize = 12.sp)
                        }
                    }

                    // Статус процесса обновления
                    when (val state = updateState) {
                        is UpdateState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = NeonCyan, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Поиск новой версии на GitHub...", color = TextMuted, fontSize = 12.sp)
                            }
                        }
                        is UpdateState.Downloading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Загрузка обновления v${state.versionName}...", color = NeonCyan, fontSize = 12.sp)
                                    Text("${state.progressPercent}%", color = NeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                LinearProgressIndicator(
                                    progress = { state.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = NeonCyan,
                                    trackColor = CyberSurface
                                )
                            }
                        }
                        is UpdateState.ReadyToInstall -> {
                            Button(
                                onClick = { mainActivity.updateManager.installApk(state.apkFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, tint = CyberBlack)
                                Spacer(Modifier.width(8.dp))
                                Text("Установить обновление v${state.versionName}", color = CyberBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                        is UpdateState.Available -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Доступно обновление v${state.versionName}!", color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Button(
                                    onClick = { mainActivity.updateManager.startBackgroundDownload(state.downloadUrl, state.versionName) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen)
                                ) {
                                    Text("Скачать", color = CyberBlack, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        else -> {
                            Text("У вас установлена актуальная версия Kiosk Browser", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
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

            // ── Карточка: Назначение Домашним экраном (Лаунчером) по умолчанию ──
            val isDefaultHome = remember { mainActivity.deviceOwnerManager.isDefaultLauncher() }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isDefaultHome) NeonGreen.copy(alpha = 0.6f) else NeonCyan.copy(alpha = 0.4f),
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
                            text = "Домашний экран (Лаунчер)",
                            color = TextWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isDefaultHome) "Kiosk уже назначен главным экраном устройства" else "Сделать Kiosk постоянным домашним экраном",
                            color = if (isDefaultHome) NeonGreen else TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    if (!isDefaultHome) {
                        Button(
                            onClick = { mainActivity.deviceOwnerManager.requestDefaultLauncher(mainActivity) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Назначить", color = CyberBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Активен",
                            tint = NeonGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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

            // ── ОСНОВНОЙ РЕЖИМ РАБОТЫ (САЙТ ИЛИ ПРИЛОЖЕНИЕ) ──
            SettingsCard(title = "Основной режим работы (по умолчанию)", icon = Icons.Default.Language) {
                Text("Что запускать в киоске по умолчанию:", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = primaryMode == "WEB",
                        onClick = { primaryMode = "WEB" },
                        label = { Text("Веб-сайт (URL)") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = primaryMode == "APP",
                        onClick = { primaryMode = "APP" },
                        label = { Text("Android-приложение") },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (primaryMode == "WEB") {
                    OutlinedTextField(
                        value = startUrl,
                        onValueChange = { newValue: String -> startUrl = newValue },
                        label = { Text("Стартовый веб-сайт (URL)") },
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
                } else {
                    // Режим приложения
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (primaryAppPackage.isNotEmpty()) {
                            val pm = context.packageManager
                            val appLabel = remember(primaryAppPackage) {
                                runCatching {
                                    val info = pm.getApplicationInfo(primaryAppPackage, 0)
                                    pm.getApplicationLabel(info).toString()
                                }.getOrDefault(primaryAppPackage)
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0D2030))
                                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NeonCyan)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(appLabel, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(primaryAppPackage, color = TextMuted, fontSize = 10.sp)
                                }
                                TextButton(onClick = { showPrimaryAppPicker = true }) {
                                    Text("Сменить", color = NeonCyan)
                                }
                            }
                        } else {
                            Button(
                                onClick = { showPrimaryAppPicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSurface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.Apps, contentDescription = null, tint = NeonCyan)
                                Spacer(Modifier.width(8.dp))
                                Text("Выбрать приложение по умолчанию", color = TextWhite)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        isFirstLaunchCompleted = false
                        Toast.makeText(context, "Мастер настройки запустится при следующем входе", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.RocketLaunch, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Сбросить и показать мастер настройки при старте", fontSize = 11.sp)
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

            // Экран и энергосбережение
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

                Spacer(modifier = Modifier.height(14.dp))
                // Автопробуждение по заказу
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D2030)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = NeonGreen, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Автопробуждение экрана при заказе", color = NeonGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Text(
                            "Экран зажигается автоматически при любом звуке заказа на сайте или звуковом сигнале приложения.",
                            color = TextWhite,
                            fontSize = 11.sp
                        )
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Откройте настройки уведомлений Android", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Доступ к push-уведомлениям (для сторонних приложений)", fontSize = 11.sp, color = NeonCyan)
                        }
                    }
                }
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