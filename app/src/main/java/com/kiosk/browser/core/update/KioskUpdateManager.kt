package com.kiosk.browser.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    data class Downloading(val progressPercent: Int, val downloadedBytes: Long, val totalBytes: Long, val versionName: String) : UpdateState()
    data class ReadyToInstall(val apkFile: File, val versionName: String) : UpdateState()
    object Installing : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class KioskUpdateManager(private val context: Context) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val githubRepo = "druzhba-tech/Kiosk1"
    private var isDownloading = false

    /**
     * Получить файл APK в кэше для конкретной версии
     */
    fun getApkFile(versionName: String): File {
        return File(context.cacheDir, "kiosk-update-v$versionName.apk")
    }

    /**
     * Удалить старые закэшированные версии APK
     */
    private fun cleanOldApkCache(keepVersionTag: String) {
        try {
            val keepName = "kiosk-update-v$keepVersionTag.apk"
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("kiosk-update") && file.name.endsWith(".apk") && file.name != keepName) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Проверка наличия новой версии через GitHub Releases API (в фоне)
     */
    suspend fun checkForUpdates(currentVersionName: String): UpdateState = withContext(Dispatchers.IO) {
        if (isDownloading || _updateState.value is UpdateState.ReadyToInstall || _updateState.value is UpdateState.Installing) {
            return@withContext _updateState.value
        }

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
                var apkSize = 0L
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }
                }

                android.util.Log.i("KioskUpdateManager", "Найдена версия на GitHub: $latestTag, URL: $apkDownloadUrl, размер: $apkSize байт")

                if (apkDownloadUrl.isNotEmpty() && isNewerVersion(latestTag, currentVersionName)) {
                    val cachedApk = getApkFile(latestTag)
                    // Если обновление уже полностью скачано в кэш — не скачиваем повторно!
                    if (cachedApk.exists() && cachedApk.length() > 0 && (apkSize <= 0L || cachedApk.length() == apkSize)) {
                        android.util.Log.i("KioskUpdateManager", "Обновление v$latestTag уже в кэше (${cachedApk.length()} байт). Готово к установке без повторной загрузки!")
                        val ready = UpdateState.ReadyToInstall(cachedApk, latestTag)
                        _updateState.value = ready
                        return@withContext ready
                    }

                    cleanOldApkCache(latestTag)

                    android.util.Log.i("KioskUpdateManager", "Обновление $latestTag доступно. Запуск фоновой докачки...")
                    val available = UpdateState.Available(latestTag, apkDownloadUrl, releaseNotes)
                    _updateState.value = available

                    // Сразу запускаем скачивание/докачку в фоне, не блокируя работу пользователя в киоске
                    startBackgroundDownload(apkDownloadUrl, latestTag, apkSize)
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
            _updateState.value = UpdateState.Idle
            UpdateState.Idle
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
     * Фоновая загрузка/докачка APK файла с поддержкой HTTP Range без блокировки киоска
     */
    fun startBackgroundDownload(downloadUrl: String, versionName: String, expectedSize: Long = 0L) {
        if (isDownloading) return
        scope.launch {
            isDownloading = true
            try {
                val updateFile = getApkFile(versionName)
                val existingBytes = if (updateFile.exists()) updateFile.length() else 0L

                // Если уже полностью скачан
                if (expectedSize > 0 && existingBytes == expectedSize) {
                    android.util.Log.i("KioskUpdateManager", "Файл v$versionName уже полностью загружен ($existingBytes байт).")
                    _updateState.value = UpdateState.ReadyToInstall(updateFile, versionName)
                    return@launch
                }

                val conn = URL(downloadUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Kiosk-App-Updater")
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 30000

                // Докачка (HTTP Range) если есть частично скачанный файл
                val resume = existingBytes > 0 && (expectedSize <= 0 || existingBytes < expectedSize)
                if (resume) {
                    conn.setRequestProperty("Range", "bytes=$existingBytes-")
                    android.util.Log.i("KioskUpdateManager", "Возобновление докачки v$versionName с байта $existingBytes...")
                }
                conn.connect()

                val responseCode = conn.responseCode
                val append = resume && responseCode == HttpURLConnection.HTTP_PARTIAL

                val totalLength = if (append) {
                    existingBytes + conn.contentLengthLong
                } else {
                    conn.contentLengthLong
                }

                val startOffset = if (append) existingBytes else 0L
                if (!append && updateFile.exists()) {
                    updateFile.delete()
                }

                var lastReportedPercent = -1

                conn.inputStream.use { input ->
                    FileOutputStream(updateFile, append).use { output ->
                        val data = ByteArray(8192)
                        var currentTotal = startOffset
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            currentTotal += count
                            output.write(data, 0, count)
                            val targetTotal = if (expectedSize > 0) expectedSize else totalLength
                            if (targetTotal > 0) {
                                val percent = ((currentTotal * 100) / targetTotal).toInt().coerceIn(0, 100)
                                if (percent != lastReportedPercent && (percent % 2 == 0 || percent == 100)) {
                                    lastReportedPercent = percent
                                    _updateState.value = UpdateState.Downloading(percent, currentTotal, targetTotal, versionName)
                                }
                            }
                        }
                    }
                }

                if (updateFile.exists() && updateFile.length() > 0) {
                    android.util.Log.i("KioskUpdateManager", "Обновление v$versionName готово (${updateFile.length()} байт)!")
                    _updateState.value = UpdateState.ReadyToInstall(updateFile, versionName)
                } else {
                    _updateState.value = UpdateState.Error("Файл обновления пуст")
                }
            } catch (e: Exception) {
                android.util.Log.e("KioskUpdateManager", "Ошибка фонового скачивания: ${e.message}")
                _updateState.value = UpdateState.Error("Ошибка скачивания: ${e.message}")
            } finally {
                isDownloading = false
            }
        }
    }

    /**
     * Установка APK файла (тихая для Device Owner, либо через стандартный диалог Android)
     */
    fun installApk(file: File) {
        _updateState.value = UpdateState.Installing
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val isDeviceOwner = dpm.isDeviceOwnerApp(context.packageName)

            if (isDeviceOwner) {
                installSilentlyAsDeviceOwner(file)
            } else {
                installViaIntent(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            installViaIntent(file)
        }
    }

    private fun installSilentlyAsDeviceOwner(apkFile: File) {
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("KioskUpdateManager", "Silent install failed, falling back to intent: ${e.message}")
            installViaIntent(apkFile)
        }
    }

    private fun installViaIntent(file: File) {
        try {
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
        } catch (e: Exception) {
            android.util.Log.e("KioskUpdateManager", "Ошибка запуска установки: ${e.message}")
            _updateState.value = UpdateState.Error("Не удалось запустить установщик: ${e.message}")
        }
    }

    fun dismiss() {
        _updateState.value = UpdateState.Idle
    }
}