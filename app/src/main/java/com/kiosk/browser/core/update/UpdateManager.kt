package com.kiosk.browser.core.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Менеджер OTA-обновлений приложения.
 *
 * Алгоритм:
 *  1. Периодически проверяет JSON-манифест на сервере.
 *  2. Если найдена новая версия — устанавливает флаг updateAvailable.
 *  3. При вызове downloadAndInstall() — скачивает APK фоново через DownloadManager.
 *  4. После загрузки — устанавливает APK ТИХО через PackageInstaller (Device Owner).
 */
class UpdateManager(private val context: Context) {

    // ── Состояния ─────────────────────────────────────────────────────────────

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        data class UpdateAvailable(val versionName: String, val apkUrl: String) : UpdateState()
        data class Downloading(val progress: Int) : UpdateState()         // 0..100
        object Installing : UpdateState()
        object InstallSuccess : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val updateAvailable: Boolean
        get() = _state.value is UpdateState.UpdateAvailable

    private val scope = CoroutineScope(Dispatchers.IO)

    // ── URL конфигурации (задаётся из KioskConfig) ─────────────────────────────

    private var checkUrl: String = ""
    private var checkIntervalMs: Long = 3_600_000L // 1 час по умолчанию

    // ── Запуск периодической проверки ──────────────────────────────────────────

    fun startPeriodicCheck(manifestUrl: String, intervalMs: Long = 3_600_000L) {
        checkUrl = manifestUrl
        checkIntervalMs = intervalMs
        scope.launch {
            while (isActive) {
                checkForUpdate()
                delay(checkIntervalMs)
            }
        }
    }

    // ── Разовая проверка обновления ────────────────────────────────────────────

    fun checkForUpdate() {
        if (checkUrl.isBlank()) return
        scope.launch { checkForUpdateSuspend() }
    }

    private suspend fun checkForUpdateSuspend() {
        _state.value = UpdateState.Checking
        try {
            val json = fetchJson(checkUrl)
            val remoteVersion = json.getString("version_name")
            val apkUrl = json.getString("apk_url")

            val currentVersion = getCurrentVersionName()
            Log.d(TAG, "Текущая версия: $currentVersion, Серверная: $remoteVersion")

            if (isNewer(remoteVersion, currentVersion)) {
                _state.value = UpdateState.UpdateAvailable(remoteVersion, apkUrl)
                Log.i(TAG, "Доступно обновление: $remoteVersion")
            } else {
                _state.value = UpdateState.Idle
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ошибка проверки обновления: ${e.message}")
            _state.value = UpdateState.Idle // Тихая ошибка — не мешаем работе
        }
    }

    // ── Скачать и установить APK ───────────────────────────────────────────────

    fun downloadAndInstall(apkUrl: String) {
        scope.launch { downloadAndInstallSuspend(apkUrl) }
    }

    private suspend fun downloadAndInstallSuspend(apkUrl: String) {
        val outputFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "kiosk_update.apk"
        )

        // Удалить старый файл если есть
        if (outputFile.exists()) outputFile.delete()

        _state.value = UpdateState.Downloading(0)

        try {
            // ── Фоновая загрузка APK через HTTP ─────────────────────────────────
            withContext(Dispatchers.IO) {
                val connection = URL(apkUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.connect()

                val totalBytes = connection.contentLengthLong
                var downloadedBytes = 0L

                outputFile.outputStream().use { out ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                _state.value = UpdateState.Downloading(progress)
                            }
                        }
                    }
                    connection.disconnect()
                }
            }

            Log.i(TAG, "APK загружен: ${outputFile.absolutePath}")
            _state.value = UpdateState.Installing

            // ── Тихая установка через PackageInstaller ───────────────────────────
            silentInstall(outputFile)

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка загрузки APK: ${e.message}")
            _state.value = UpdateState.Error("Ошибка загрузки: ${e.message}")
        }
    }

    /**
     * Устанавливает APK без диалогов (Silent Install) через PackageInstaller API.
     * Работает только если приложение является Device Owner.
     */
    private fun silentInstall(apkFile: File) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

            // Для Device Owner — автоматическое разрешение без диалога
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            // Записываем APK в сессию
            FileInputStream(apkFile).use { input ->
                session.openWrite("kiosk_update", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            // Создаем PendingIntent для получения результата
            val intent = Intent(context, InstallResultReceiver::class.java).apply {
                action = ACTION_INSTALL_COMPLETE
            }
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context, sessionId, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )

            session.commit(pendingIntent.intentSender)
            session.close()

            Log.i(TAG, "Silent install запущен для сессии $sessionId")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка Silent Install: ${e.message}")
            _state.value = UpdateState.Error("Ошибка установки: ${e.message}")
        }
    }

    // ── Уведомление об успешной установке ─────────────────────────────────────

    fun notifyInstallSuccess() {
        _state.value = UpdateState.InstallSuccess
        scope.launch {
            delay(3000L)
            _state.value = UpdateState.Idle
        }
    }

    fun notifyInstallError(message: String) {
        _state.value = UpdateState.Error(message)
    }

    // ── Вспомогательные методы ─────────────────────────────────────────────────

    private fun fetchJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        connection.connect()
        val text = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        return JSONObject(text)
    }

    private fun getCurrentVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) { "1.0.0" }
    }

    /**
     * Сравнение версий вида "1.2.3".
     * Возвращает true если remote > current.
     */
    private fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        val size = maxOf(r.size, c.size)
        for (i in 0 until size) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv > cv) return true
            if (rv < cv) return false
        }
        return false
    }

    companion object {
        private const val TAG = "UpdateManager"
        const val ACTION_INSTALL_COMPLETE = "com.kiosk.UPDATE_INSTALL_COMPLETE"
    }
}
