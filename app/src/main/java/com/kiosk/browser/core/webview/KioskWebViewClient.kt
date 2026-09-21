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
        // Блокируем выделение текста и контекстное меню на веб-страницах
        injectTextSelectionBlock(view)
    }

    /**
     * Блокировка выделения текста и контекстного меню браузера
     */
    private fun injectTextSelectionBlock(view: WebView?) {
        val script = """
            (function() {
                try {
                    if (document.getElementById('__kiosk_no_select__')) return;
                    var style = document.createElement('style');
                    style.id = '__kiosk_no_select__';
                    style.innerHTML = '* { -webkit-touch-callout: none !important; -webkit-user-select: none !important; user-select: none !important; } input, textarea, [contenteditable="true"] { -webkit-user-select: text !important; user-select: text !important; -webkit-touch-callout: default !important; }';
                    if (document.head) {
                        document.head.appendChild(style);
                    } else if (document.documentElement) {
                        document.documentElement.appendChild(style);
                    }
                    document.addEventListener('contextmenu', function(e) {
                        if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA')) return;
                        e.preventDefault();
                    }, false);
                } catch(e) {}
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
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
                if (window.HTMLMediaElement && HTMLMediaElement.prototype.play) {
                    var origPlay = HTMLMediaElement.prototype.play;
                    HTMLMediaElement.prototype.play = function() {
                        wakeKiosk();
                        return origPlay.apply(this, arguments);
                    };
                }
                if (window.HTMLAudioElement && HTMLAudioElement.prototype.play) {
                    var origAudioPlay = HTMLAudioElement.prototype.play;
                    HTMLAudioElement.prototype.play = function() {
                        wakeKiosk();
                        return origAudioPlay.apply(this, arguments);
                    };
                }

                // 2. Перехват AudioContext (Web Audio API)
                if (window.AudioContext || window.webkitAudioContext) {
                    var AudioCtx = window.AudioContext || window.webkitAudioContext;
                    if (AudioCtx.prototype.resume) {
                        var origResume = AudioCtx.prototype.resume;
                        AudioCtx.prototype.resume = function() {
                            wakeKiosk();
                            return origResume.apply(this, arguments);
                        };
                    }
                    if (AudioCtx.prototype.createBufferSource) {
                        var origCreateBufferSource = AudioCtx.prototype.createBufferSource;
                        AudioCtx.prototype.createBufferSource = function() {
                            var source = origCreateBufferSource.apply(this, arguments);
                            var origStart = source.start;
                            source.start = function() {
                                wakeKiosk();
                                return origStart.apply(this, arguments);
                            };
                            return source;
                        };
                    }
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

                // 4. Перехват голосового синтезатора (SpeechSynthesis)
                if (window.speechSynthesis && window.speechSynthesis.speak) {
                    var origSpeak = window.speechSynthesis.speak;
                    window.speechSynthesis.speak = function(utterance) {
                        wakeKiosk();
                        return origSpeak.apply(this, arguments);
                    };
                }
            })();
        """.trimIndent()

        view?.evaluateJavascript(script, null)
    }
}