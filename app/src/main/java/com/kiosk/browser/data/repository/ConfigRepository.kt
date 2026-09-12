package com.kiosk.browser.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.kiosk.browser.data.model.KioskConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConfigRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _configFlow = MutableStateFlow(loadConfig())
    val configFlow: StateFlow<KioskConfig> = _configFlow.asStateFlow()

    fun getConfig(): KioskConfig = _configFlow.value

    fun updateConfig(update: (KioskConfig) -> KioskConfig) {
        val newConfig = update(_configFlow.value)
        saveConfig(newConfig)
    }

    fun saveConfig(config: KioskConfig) {
        val jsonString = json.encodeToString(config)
        prefs.edit().putString(KEY_CONFIG, jsonString).apply()
        _configFlow.value = config
    }

    fun exportConfigJson(): String {
        return json.encodeToString(_configFlow.value)
    }

    fun importConfigJson(jsonString: String): Result<KioskConfig> {
        return runCatching {
            val parsed = json.decodeFromString<KioskConfig>(jsonString)
            saveConfig(parsed)
            parsed
        }
    }

    fun resetPin(newPin: String) {
        updateConfig { it.copy(pinCode = newPin) }
    }

    private fun loadConfig(): KioskConfig {
        val raw = prefs.getString(KEY_CONFIG, null) ?: return KioskConfig()
        return try {
            json.decodeFromString<KioskConfig>(raw)
        } catch (e: Exception) {
            KioskConfig()
        }
    }

    companion object {
        private const val PREFS_NAME = "kiosk_browser_prefs"
        private const val KEY_CONFIG = "kiosk_config_json"
    }
}
