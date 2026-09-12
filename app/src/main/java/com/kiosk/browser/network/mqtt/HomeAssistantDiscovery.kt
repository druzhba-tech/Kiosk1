package com.kiosk.browser.network.mqtt

import com.kiosk.browser.data.model.KioskConfig

object HomeAssistantDiscovery {

    fun publishAllEntities(mqttClient: KioskMqttClient, config: KioskConfig) {
        if (!config.haDiscoveryEnabled) return

        val deviceId = config.mqttClientId
        val prefix = config.mqttTopicPrefix
        val stateTopic = "$prefix/state"

        val deviceJson = """
            "device": {
                "identifiers": ["$deviceId"],
                "name": "Kiosk Tablet",
                "model": "Android Kiosk Browser",
                "manufacturer": "Kiosk Systems"
            }
        """.trimIndent()

        // 1. Сенсор батареи
        val batteryConfig = """
            {
                "name": "Battery Level",
                "unique_id": "${deviceId}_battery",
                "state_topic": "$stateTopic",
                "value_template": "{{ value_json.battery }}",
                "unit_of_measurement": "%",
                "device_class": "battery",
                $deviceJson
            }
        """.trimIndent()
        mqttClient.publishRaw("homeassistant/sensor/$deviceId/battery/config", batteryConfig, retain = true)

        // 2. Бинарный сенсор зарядки
        val chargingConfig = """
            {
                "name": "Battery Charging",
                "unique_id": "${deviceId}_charging",
                "state_topic": "$stateTopic",
                "value_template": "{{ value_json.is_charging }}",
                "payload_on": "true",
                "payload_off": "false",
                "device_class": "battery_charging",
                $deviceJson
            }
        """.trimIndent()
        mqttClient.publishRaw("homeassistant/binary_sensor/$deviceId/charging/config", chargingConfig, retain = true)

        // 3. Переключатель экрана (Switch)
        val screenSwitchConfig = """
            {
                "name": "Screen Power",
                "unique_id": "${deviceId}_screen",
                "state_topic": "$stateTopic",
                "value_template": "{{ value_json.screen_state }}",
                "command_topic": "$prefix/cmd/screen",
                "payload_on": "ON",
                "payload_off": "OFF",
                "state_on": "ON",
                "state_off": "OFF",
                $deviceJson
            }
        """.trimIndent()
        mqttClient.publishRaw("homeassistant/switch/$deviceId/screen/config", screenSwitchConfig, retain = true)

        // 4. Кнопка перезагрузки страницы (Button)
        val reloadButtonConfig = """
            {
                "name": "Reload Webview",
                "unique_id": "${deviceId}_reload",
                "command_topic": "$prefix/cmd/reload",
                "payload_press": "RELOAD",
                $deviceJson
            }
        """.trimIndent()
        mqttClient.publishRaw("homeassistant/button/$deviceId/reload/config", reloadButtonConfig, retain = true)
    }
}
