package com.kiosk.browser.data.model

import kotlinx.serialization.Serializable

@Serializable
data class KioskConfig(
    // Основные настройки веб-киоска
    val startUrl: String = "https://demo.home-assistant.io",
    val isKioskEnabled: Boolean = true,
    val isSingleAppMode: Boolean = true,
    val pinCode: String = "1234",
    val emergencyAdbKey: String = "SECRET_KIOSK_KEY_777",

    // Питание, экран и заставка
    val keepScreenOn: Boolean = true,
    val idleTimeoutSeconds: Int = 120,
    val screensaverEnabled: Boolean = true,
    val oledBurnInProtection: Boolean = true,
    val virtualSleepEnabled: Boolean = true,

    // Блокировки и безопасность
    val blockHardwareKeys: Boolean = true,
    val blockSystemNavigation: Boolean = true,
    val blockUsbFileTransfer: Boolean = false,
    val blockSafeMode: Boolean = false,

    // Веб-движок и надежность
    val ignoreSslErrors: Boolean = true,
    val clearDataOnIdle: Boolean = false,
    val autoReloadOnNetworkRecover: Boolean = true,
    val dailyRebootTimeHour: Int = 4, // 04:00 утра плановый рестарт WebView
    val allowedUrls: List<String> = listOf("http://*", "https://*"),
    val blockedUrls: List<String> = emptyList(),

    // Приложения лаунчера
    val allowedApps: List<String> = emptyList(),

    // Сенсоры и антивор
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
    val remoteAdminEnabled: Boolean = true,

    // OTA Обновления
    val updateCheckEnabled: Boolean = false,
    val updateManifestUrl: String = "",        // URL JSON-манифеста: {"version_name":"1.1.0","apk_url":"http://..."}
    val updateCheckIntervalHours: Int = 6      // Интервал проверки в часах
)
