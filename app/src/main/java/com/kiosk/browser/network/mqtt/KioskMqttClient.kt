package com.kiosk.browser.network.mqtt

import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.kiosk.browser.data.model.KioskConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class KioskMqttClient(
    private val onCommandReceived: (command: String, payload: String) -> Unit
) {

    private var client: Mqtt3AsyncClient? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(config: KioskConfig) {
        if (!config.mqttEnabled || config.mqttBroker.isBlank()) return

        disconnect()

        try {
            val builder = MqttClient.builder()
                .useMqttVersion3()
                .identifier(config.mqttClientId)
                .serverHost(config.mqttBroker)
                .serverPort(config.mqttPort)

            client = builder.buildAsync()

            val connectBuilder = client!!.connectWith()
                .cleanSession(true)

            if (config.mqttUser.isNotBlank()) {
                connectBuilder.simpleAuth()
                    .username(config.mqttUser)
                    .password(config.mqttPassword.toByteArray(StandardCharsets.UTF_8))
                    .applySimpleAuth()
            }

            connectBuilder.send()
                .whenComplete { _, throwable ->
                    if (throwable != null) {
                        Log.e("KioskMQTT", "Ошибка подключения к брокеру: ${throwable.message}")
                    } else {
                        Log.i("KioskMQTT", "Успешное подключение к MQTT брокеру!")
                        subscribeToCommands(config.mqttTopicPrefix)
                    }
                }
        } catch (e: Exception) {
            Log.e("KioskMQTT", "Исключение при инициализации MQTT: ${e.message}")
        }
    }

    private fun subscribeToCommands(prefix: String) {
        val topicFilter = "$prefix/cmd/#"
        client?.subscribeWith()
            ?.topicFilter(topicFilter)
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.callback { publish ->
                val topic = publish.topic.toString()
                val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                val subTopic = topic.removePrefix("$prefix/cmd/").trim('/')
                scope.launch(Dispatchers.Main) {
                    onCommandReceived(subTopic, payload)
                }
            }
            ?.send()
    }

    fun publishState(prefix: String, jsonState: String) {
        client?.publishWith()
            ?.topic("$prefix/state")
            ?.payload(jsonState.toByteArray(StandardCharsets.UTF_8))
            ?.qos(MqttQos.AT_MOST_ONCE)
            ?.send()
    }

    fun publishRaw(topic: String, payload: String, retain: Boolean = false) {
        client?.publishWith()
            ?.topic(topic)
            ?.payload(payload.toByteArray(StandardCharsets.UTF_8))
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.retain(retain)
            ?.send()
    }

    fun disconnect() {
        try {
            client?.disconnect()
            client = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
