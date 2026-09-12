package com.kiosk.browser.core.webview

import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class KioskWebViewClient(
    private val filterManager: UrlFilterManager,
    private val isIgnoreSslErrors: () -> Boolean,
    private val onCrashRecover: () -> Unit,
    private val onPageLoaded: (String) -> Unit
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        if (filterManager.isUrlAllowed(url)) {
            return false // Разрешаем стандартную загрузку в WebView
        }
        // Блокируем переход на запрещенный URL
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return false
        if (filterManager.isUrlAllowed(url)) {
            return false
        }
        return true
    }

    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        if (isIgnoreSslErrors()) {
            // Разрешаем самоподписанные SSL сертификаты локальных серверов (Home Assistant)
            handler?.proceed()
        } else {
            handler?.cancel()
        }
    }

    /**
     * Защита от OOM и падения рендерера: вместо краша приложения, мягко восстанавливаем WebView
     */
    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        view?.let {
            it.destroy()
        }
        onCrashRecover()
        return true // Сообщаем Android, что мы сами обработали падение процесса
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
        // Можно отобразить кастомный экран Offline
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        url?.let { onPageLoaded(it) }
    }
}
