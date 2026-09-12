package com.kiosk.browser.core.webview

import android.net.Uri

class UrlFilterManager(
    private var allowedUrls: List<String>,
    private var blockedUrls: List<String>
) {

    fun updateFilters(allowed: List<String>, blocked: List<String>) {
        this.allowedUrls = allowed
        this.blockedUrls = blocked
    }

    /**
     * Проверяет, разрешен ли переход по URL
     */
    fun isUrlAllowed(url: String): Boolean {
        if (url.startsWith("file://") || url.startsWith("about:blank") || url.startsWith("data:")) {
            return true
        }

        // Проверка черного списка
        for (pattern in blockedUrls) {
            if (matchPattern(url, pattern)) {
                return false
            }
        }

        // Если белый список пуст, разрешаем всё, что не в черном
        if (allowedUrls.isEmpty()) {
            return true
        }

        // Проверка белого списка
        for (pattern in allowedUrls) {
            if (matchPattern(url, pattern)) {
                return true
            }
        }

        return false
    }

    private fun matchPattern(url: String, pattern: String): Boolean {
        if (pattern == "*" || pattern == "http://*" || pattern == "https://*") {
            return true
        }
        val regexPattern = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
        return try {
            Regex(regexPattern, RegexOption.IGNORE_CASE).containsMatchIn(url)
        } catch (e: Exception) {
            url.contains(pattern, ignoreCase = true)
        }
    }
}
