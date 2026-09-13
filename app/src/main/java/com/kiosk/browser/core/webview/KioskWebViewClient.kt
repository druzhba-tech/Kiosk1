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
            return false
        }
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
            handler?.proceed()
        } else {
            handler?.cancel()
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        view?.let {
            it.destroy()
        }
        onCrashRecover()
        return true
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        super.onReceivedError(view, request, error)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        url?.let { onPageLoaded(it) }

        // Внедряем автоматический перехват звуков и оповещений о новом заказе
        injectOrderAlertWakeHook(view)
    }

    /**
     * Автоматически будит экран, если сайт издает звуковой сигнал заказа (HTML5 Audio, Web Audio)
     * или показывает HTML5 Notification о новом заказе.
     */
    private fun injectOrderAlertWakeHook(view: WebView?) {
        val script = """
            (function() {
                if (window.__kioskWakeHookInjected) return;
                window.__kioskWakeHookInjected = true;

                function wakeKiosk() {
                    try {
                        if (window.kiosk && window.kiosk.turnScreenOn) {
                            window.kiosk.turnScreenOn();
                        } else if (window.fully && window.fully.turnScreenOn) {
                            window.fully.turnScreenOn();
                        }
                    } catch(e) {}
                }

                // 1. Перехват вызова HTML5 Audio (.play())
                var origPlay = HTMLMediaElement.prototype.play;
                HTMLMediaElement.prototype.play = function() {
                    wakeKiosk();
                    return origPlay.apply(this, arguments);
                };

                // 2. Перехват AudioContext (Web Audio API)
                if (window.AudioContext || window.webkitAudioContext) {
                    var AudioCtx = window.AudioContext || window.webkitAudioContext;
                    var origResume = AudioCtx.prototype.resume;
                    AudioCtx.prototype.resume = function() {
                        wakeKiosk();
                        return origResume.apply(this, arguments);
                    };
                }

                // 3. Перехват Web Notifications (new Notification)
                if (window.Notification) {
                    var OrigNotif = window.Notification;
                    window.Notification = function(title, options) {
                        wakeKiosk();
                        return new OrigNotif(title, options);
                    };
                    window.Notification.permission = "granted";
                    window.Notification.requestPermission = function(cb) {
                        if (cb) cb("granted");
                        return Promise.resolve("granted");
                    };
                }
            })();
        """.trimIndent()

        view?.evaluateJavascript(script, null)
    }
}