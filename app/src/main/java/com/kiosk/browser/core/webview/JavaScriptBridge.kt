package com.kiosk.browser.core.webview

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.kiosk.browser.MainActivity
import com.kiosk.browser.core.power.BatteryTracker
import com.kiosk.browser.core.security.PasswordManager
import java.util.Locale

class JavaScriptBridge(
    private val context: Context,
    private val batteryTracker: BatteryTracker,
    val passwordManager: PasswordManager = PasswordManager(context),
    private val onScreenControl: (turnOn: Boolean) -> Unit,
    private val onSavePasswordPrompt: ((domain: String, username: String, password: String) -> Unit)? = null,
    private val onScrollStateChange: ((isAtTop: Boolean) -> Unit)? = null
) {

    @Volatile
    var isPageAtTop: Boolean = true

    private var tts: TextToSpeech? = null

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
    }

    @JavascriptInterface
    fun getBatteryLevel(): Int {
        return batteryTracker.batteryLevel.value
    }

    @JavascriptInterface
    fun isCharging(): Boolean {
        return batteryTracker.isCharging.value
    }

    @JavascriptInterface
    fun getBatteryTemperature(): Float {
        return batteryTracker.batteryTemperature.value
    }

    @JavascriptInterface
    fun turnScreenOn() {
        onScreenControl(true)
    }

    @JavascriptInterface
    fun turnScreenOff() {
        onScreenControl(false)
    }

    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    @JavascriptInterface
    fun notifyScrollAtTop(atTop: Boolean) {
        isPageAtTop = atTop
        onScrollStateChange?.invoke(atTop)
    }

    @JavascriptInterface
    fun textToSpeech(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kiosk_tts")
    }

    @JavascriptInterface
    fun startApplication(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }

    @JavascriptInterface
    fun reload() {
        MainActivity.currentInstance?.reloadCurrentPage()
    }

    @JavascriptInterface
    fun getSavedLogin(url: String): String {
        return passwordManager.getCredentials(url)?.username ?: ""
    }

    @JavascriptInterface
    fun getSavedPassword(url: String): String {
        return passwordManager.getCredentials(url)?.password ?: ""
    }

    @JavascriptInterface
    fun onFormSubmit(url: String, username: String, password: String) {
        if (password.isBlank()) return
        val domain = passwordManager.extractDomain(url)
        val existing = passwordManager.getCredentials(domain)
        if (existing != null && existing.username == username.trim() && existing.password == password) {
            return
        }
        onSavePasswordPrompt?.invoke(domain, username.trim(), password)
    }

    @JavascriptInterface
    fun saveCredentialsDirectly(url: String, username: String, password: String) {
        passwordManager.saveCredentials(url, username, password)
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
