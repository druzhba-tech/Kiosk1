package com.kiosk.browser.core.security

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class SavedCredential(
    val domain: String,
    val username: String,
    val password: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PasswordManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("kiosk_saved_passwords", Context.MODE_PRIVATE)

    fun extractDomain(url: String): String {
        return try {
            val uri = Uri.parse(url)
            val host = uri.host
            if (!host.isNullOrBlank()) host.lowercase() else url.trim().lowercase()
        } catch (_: Exception) {
            url.trim().lowercase()
        }
    }

    fun saveCredentials(urlOrDomain: String, username: String, password: String) {
        if (password.isBlank()) return
        val domain = extractDomain(urlOrDomain)
        if (domain.isBlank()) return

        val all = getAllCredentials().toMutableList()
        // Удаляем предыдущую запись для этого домена, если есть
        all.removeAll { it.domain.equals(domain, ignoreCase = true) }
        all.add(SavedCredential(domain, username.trim(), password))

        val array = JSONArray()
        for (item in all) {
            val obj = JSONObject().apply {
                put("domain", item.domain)
                put("username", item.username)
                put("password", item.password)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("credentials_json", array.toString()).apply()
    }

    fun getCredentials(urlOrDomain: String): SavedCredential? {
        val domain = extractDomain(urlOrDomain)
        return getAllCredentials().firstOrNull { it.domain.equals(domain, ignoreCase = true) }
    }

    fun getAllCredentials(): List<SavedCredential> {
        val jsonStr = prefs.getString("credentials_json", null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val result = mutableListOf<SavedCredential>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    SavedCredential(
                        domain = obj.optString("domain", ""),
                        username = obj.optString("username", ""),
                        password = obj.optString("password", ""),
                        timestamp = obj.optLong("timestamp", 0L)
                    )
                )
            }
            result.filter { it.domain.isNotBlank() && it.password.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deleteCredentials(domain: String) {
        val all = getAllCredentials().toMutableList()
        all.removeAll { it.domain.equals(domain, ignoreCase = true) }
        val array = JSONArray()
        for (item in all) {
            val obj = JSONObject().apply {
                put("domain", item.domain)
                put("username", item.username)
                put("password", item.password)
                put("timestamp", item.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("credentials_json", array.toString()).apply()
    }

    fun clearAll() {
        prefs.edit().remove("credentials_json").apply()
    }
}
