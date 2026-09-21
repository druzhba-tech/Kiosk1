package com.kiosk.browser.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class Available(val versionName: String, val downloadUrl: String, val releaseNotes: String) : UpdateState()
    data class Downloading(val progressPercent: Int, val downloadedBytes: Long, val totalBytes: Long) : UpdateState()
    object Installing : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class KioskUpdateManager(private val context: Context) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val githubRepo = "druzhba-tech/Kiosk1"

    /**
     * Проверка наличия новой версии через GitHub Releases API
     */
    suspend fun checkForUpdates(currentVersionName: String): UpdateState = withContext(Dispatchers.IO) {
        _updateState.value = UpdateState.Checking
        try {
            android.util.Log.i("KioskUpdateManager", "Проверка обновлений GitHub. Текущая версия: $currentVersionName")
            val apiUrl = "https://api.github.com/repos/$githubRepo/releases/latest"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "Kiosk-App-Updater")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(jsonStr)
                val latestTag = json.optString("tag_name", "").removePrefix("v").trim()
                val releaseNotes = json.optString("body", "Улучшения стабильности и новые функции")
                val assets = json.optJSONArray("assets")

                var apkDownloadUrl = ""
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                android.util.Log.i("KioskUpdateManager", "Найдена версия на GitHub: $latestTag, URL: $apkDownloadUrl")

                if (apkDownloadUrl.isNotEmpty() && isNewerVersion(latestTag, currentVersionName)) {
                    android.util.Log.i("KioskUpdateManager", "Обновление $latestTag доступно! (новее чем $currentVersionName)")
                    val available = UpdateState.Available(latestTag, apkDownloadUrl, releaseNotes)
                    _updateState.value = available
                    return@withContext available
                } else {
                    android.util.Log.i("KioskUpdateManager", "Текущая версия актуальна ($currentVersionName >= $latestTag)")
                }
            } else {
                android.util.Log.w("KioskUpdateManager", "GitHub API ответил кодом: ${conn.responseCode}")
            }
            _updateState.value = UpdateState.Idle
            UpdateState.Idle
        } catch (e: Exception) {
            android.util.Log.e("KioskUpdateManager", "Ошибка проверки обновления: ${e.message}")
            _updateState.value = UpdateState.Error(e.localizedMessage ?: "Ошибка проверки обновления")
            UpdateState.Error(e.localizedMessage ?: "Ошибка проверки обновления")
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        if (latest.isEmpty() || current.isEmpty()) return false
        val cleanLatest = latest.removePrefix("v").trim()
        val cleanCurrent = current.removePrefix("v").trim()
        if (cleanLatest == cleanCurrent) return false

        val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    /**
     * Скачивание APK файла с подсчетом процентов
     */
    suspend fun downloadAndInstall(downloadUrl: String) = withContext(Dispatchers.IO) {
        try {
            val updateFile = File(context.cacheDir, "kiosk-update.apk")
            if (updateFile.exists()) updateFile.delete()

            val conn = URL(downloadUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Kiosk-App-Updater")
            conn.instanceFollowRedirects = true
            conn.connect()

            val fileLength = conn.contentLengthLong

            conn.inputStream.use { input ->
                FileOutputStream(updateFile).use { output ->
                    val data = ByteArray(8192)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        output.write(data, 0, count)
                        if (fileLength > 0) {
                            val percent = ((total * 100) / fileLength).toInt()
                            _updateState.value = UpdateState.Downloading(percent, total, fileLength)
                        }
                    }
                }
            }

            _updateState.value = UpdateState.Installing
            installApk(updateFile)
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error("Ошибка скачивания: ${e.message}")
        }
    }

    /**
     * Запуск установки APK
     */
    fun installApk(file: File) {
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)

            if (isDeviceOwner) {
                // Если мы Device Owner — устанавливаем тихо через PackageInstaller без запросов
                installSilentlyAsDeviceOwner(file)
            } else {
                // Иначе стандартный системный установщик Android
                installViaIntent(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            installViaIntent(file)
        }
    }

    private fun installSilentlyAsDeviceOwner(apkFile: File) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)

        FileInputStream(apkFile).use { input ->
            session.openWrite("kiosk_update", 0, apkFile.length()).use { output ->
                input.copyTo(output)
                session.fsync(output)
            }
        }

        val intent = Intent(context, KioskUpdateReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            sessionId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        session.commit(pendingIntent.intentSender)
        session.close()
    }

    private fun installViaIntent(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun dismiss() {
        _updateState.value = UpdateState.Idle
    }
}