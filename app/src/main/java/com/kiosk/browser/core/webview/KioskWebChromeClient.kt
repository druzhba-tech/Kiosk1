package com.kiosk.browser.core.webview

import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView

class KioskWebChromeClient(
    private val onProgressChanged: (Int) -> Unit,
    private val onFullscreenRequested: (View?) -> Unit,
    private val onFullscreenExit: () -> Unit
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    /**
     * Автоматическое предоставление прав WebRTC для камер Home Assistant
     */
    override fun onPermissionRequest(request: PermissionRequest?) {
        // Одобряем ресурсы (аудио/видео захват для WebRTC)
        request?.grant(request.resources)
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        onFullscreenRequested(view)
    }

    override fun onHideCustomView() {
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        onFullscreenExit()
    }
}
