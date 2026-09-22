package com.kiosk.browser.data.model

import kotlinx.serialization.Serializable

@Serializable
data class KioskConfig(
    // Стартовый веб-сайт и основной режим
    val startUrl: String = "",
    val primaryMode: String = "WEB", // "WEB" (Веб-сайт) или "APP" (Android-приложение)
    val primaryAppPackage: String = "", // Пакет приложения по умолчанию (например com.example.app)
    val isFirstLaunchCompleted: Boolean = false, // Завершена ли первоначальная настройка
    val isKioskEnabled: Boolean = true,
    val isSingleAppMode: Boolean = true,
    val pinCode: String = "1234",
    val emergencyAdbKey: String = "SECRET_KIOSK_KEY_777",
    val preferredLauncherPackage: String = "", // Пусто или com.kiosk.browser -> Kiosk по умолчанию. Иначе сторонний выбранный лаунчер

    // Экран, сон и заставка
    val keepScreenOn: Boolean = true,
    val idleTimeoutSeconds: Int = 120,
    val screensaverEnabled: Boolean = true,
    val oledBurnInProtection: Boolean = true,
    val virtualSleepEnabled: Boolean = true,

    // Настройки информационной панели (HUD)
    val hudPosition: String = "TOP_RIGHT", // "TOP_RIGHT", "TOP_LEFT", "BOTTOM_RIGHT", "BOTTOM_LEFT"
    val hudTopMarginCm: Float = 2.0f,
    val hudOrientation: String = "VERTICAL", // "VERTICAL", "HORIZONTAL"
    val hudShowBrightness: Boolean = true,
    val hudShowVolume: Boolean = true,
    val hudShowWifi: Boolean = true,
    val hudShowBattery: Boolean = true,
    val hudShowKioskStatus: Boolean = true,

    // Блокировки и безопасность
    val blockHardwareKeys: Boolean = true,
    val blockSystemNavigation: Boolean = true,
    val blockUsbFileTransfer: Boolean = false,
    val blockSafeMode: Boolean = false,
    val blockPhoneCallsAndSms: Boolean = true, // Запрет звонков и SMS при работе через SIM
    val blockTethering: Boolean = true, // Запрет раздачи Wi-Fi точки доступа с SIM-карты

    // Браузер и сеть
    val ignoreSslErrors: Boolean = true,
    val clearDataOnIdle: Boolean = false,
    val autoReloadOnNetworkRecover: Boolean = true,
    val enablePullToRefresh: Boolean = true, // Обновление страницы жестом вниз (только вверху)
    val preventZoom: Boolean = true, // Блокировка случайного зума и double-tap
    val enforceMinOrderVolume: Boolean = true, // Защита от случайного выключения звука заказов
    val minOrderVolumePercent: Int = 60, // Минимальный порог громкости (60%)
    val dailyRebootTimeHour: Int = 4,
    val allowedUrls: List<String> = listOf("http://*", "https://*"),
    val blockedUrls: List<String> = emptyList(),

    // Разрешенные приложения
    val allowedApps: List<String> = emptyList(),

    // Охрана и датчики
    val antiTheftAlarmEnabled: Boolean = false,
    val motionSensitivity: Float = 2.5f,
    val lightSensorAdaptiveBrightness: Boolean = false,

    // MQTT & Home Assistant
    val mqttEnabled: Boolean = false,
    val mqttBroker: String = "192.168.1.100",
    val mqttPort: Int = 1883,
    val mqttUser: String = "",
    val mqttPassword: String = "",
    val mqttClientId: String = "kiosk_tablet_device",
    val mqttTopicPrefix: String = "kiosk/device",
    val haDiscoveryEnabled: Boolean = true,
    val remoteAdminPort: Int = 8080,
    val remoteAdminEnabled: Boolean = true
)
