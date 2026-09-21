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
    private val isBlockCallsAndSms: () -> Boolean = { true },
    private val isPreventZoom: () -> Boolean = { true },
    private val onCrashRecover: () -> Unit,
    private val onPageLoaded: (String) -> Unit
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val lower = url.lowercase()
        if (isBlockCallsAndSms() && (lower.startsWith("tel:") || lower.startsWith("sms:") || lower.startsWith("smsto:") || lower.startsWith("mms:") || lower.startsWith("mmsto:"))) {
            return true // Блокируем вызовы и SMS из веб-страниц
        }
        if (filterManager.isUrlAllowed(url)) {
            return false
        }
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return false
        val lower = url.lowercase()
        if (isBlockCallsAndSms() && (lower.startsWith("tel:") || lower.startsWith("sms:") || lower.startsWith("smsto:") || lower.startsWith("mms:") || lower.startsWith("mmsto:"))) {
            return true // Блокируем вызовы и SMS из веб-страниц
        }
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
        // Автозаполнение сохраненных логинов/паролей и перехват отправки форм
        injectPasswordAutofillAndCapture(view)
        // Защита от случайного зума и double-tap масштабирования
        if (isPreventZoom()) {
            injectZoomPrevention(view)
        }
        // Трекинг скролла для предотвращения ложного pull-to-refresh
        injectScrollTracker(view)
    }

    /**
     * Автозаполнение паролей и перехват формы авторизации
     */
    private fun injectPasswordAutofillAndCapture(view: WebView?) {
        val script = """
            (function() {
                try {
                    var url = window.location.href;
                    var savedLogin = (window.kiosk && window.kiosk.getSavedLogin) ? window.kiosk.getSavedLogin(url) : "";
                    var savedPass = (window.kiosk && window.kiosk.getSavedPassword) ? window.kiosk.getSavedPassword(url) : "";

                    function autofill() {
                        if (!savedLogin && !savedPass) return;
                        var passInputs = document.querySelectorAll('input[type="password"]');
                        passInputs.forEach(function(passInput) {
                            if (savedPass && !passInput.value) {
                                passInput.value = savedPass;
                                passInput.dispatchEvent(new Event('input', { bubbles: true }));
                                passInput.dispatchEvent(new Event('change', { bubbles: true }));
                            }
                            var form = passInput.form || passInput.closest('form');
                            var userInput = null;
                            if (form) {
                                userInput = form.querySelector('input[type="text"], input[type="email"], input[type="tel"], input[name*="user"], input[name*="login"], input[name*="email"]');
                            }
                            if (!userInput) {
                                var all = Array.from(document.querySelectorAll('input'));
                                var idx = all.indexOf(passInput);
                                if (idx > 0) userInput = all[idx - 1];
                            }
                            if (userInput && savedLogin && !userInput.value) {
                                userInput.value = savedLogin;
                                userInput.dispatchEvent(new Event('input', { bubbles: true }));
                                userInput.dispatchEvent(new Event('change', { bubbles: true }));
                            }
                        });
                    }

                    autofill();
                    setTimeout(autofill, 500);
                    setTimeout(autofill, 1500);

                    function captureCredentials() {
                        var passInputs = document.querySelectorAll('input[type="password"]');
                        passInputs.forEach(function(passInput) {
                            var password = passInput.value;
                            if (!password) return;
                            var form = passInput.form || passInput.closest('form');
                            var username = "";
                            if (form) {
                                var userInput = form.querySelector('input[type="text"], input[type="email"], input[type="tel"], input[name*="user"], input[name*="login"], input[name*="email"]');
                                if (userInput) username = userInput.value;
                            }
                            if (!username) {
                                var all = Array.from(document.querySelectorAll('input'));
                                var idx = all.indexOf(passInput);
                                if (idx > 0) username = all[idx - 1].value;
                            }
                            if (password && window.kiosk && window.kiosk.onFormSubmit) {
                                window.kiosk.onFormSubmit(window.location.href, username, password);
                            }
                        });
                    }

                    if (!window.__kioskPassHooksInjected) {
                        window.__kioskPassHooksInjected = true;
                        document.addEventListener('submit', function(e) {
                            captureCredentials();
                        }, true);
                        document.addEventListener('click', function(e) {
                            var target = e.target;
                            if (target && target.closest('button, input[type="submit"], [role="button"], a[class*="btn"], a[class*="login"], div[class*="submit"]')) {
                                setTimeout(captureCredentials, 100);
                            }
                        }, true);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
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

    /**
     * Блокировка масштабирования и double-tap zoom для сенсорных экранов кухни
     */
    private fun injectZoomPrevention(view: WebView?) {
        val script = """
            (function() {
                try {
                    var meta = document.querySelector('meta[name="viewport"]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'viewport';
                        document.head.appendChild(meta);
                    }
                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';

                    var lastTouchEnd = 0;
                    document.addEventListener('touchend', function(event) {
                        var now = (new Date()).getTime();
                        if (now - lastTouchEnd <= 300) {
                            event.preventDefault();
                        }
                        lastTouchEnd = now;
                    }, false);
                } catch(e) {}
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }

    /**
     * Инъекция слушателя прокрутки страницы и внутренних контейнеров
     * для надежной работы pull-to-refresh только в самом верху
     */
    private fun injectScrollTracker(view: WebView?) {
        val script = """
            (function() {
                try {
                    function notifyScroll() {
                        var isTop = (window.scrollY <= 0) &&
                            (!document.documentElement || document.documentElement.scrollTop <= 0) &&
                            (!document.body || document.body.scrollTop <= 0);
                        if (isTop) {
                            var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
                            while (el && el !== document.body && el !== document.documentElement) {
                                if (el.scrollTop > 0) {
                                    isTop = false;
                                    break;
                                }
                                el = el.parentElement;
                            }
                        }
                        if (window.kiosk && window.kiosk.notifyScrollAtTop) {
                            window.kiosk.notifyScrollAtTop(isTop);
                        }
                    }
                    window.addEventListener('scroll', notifyScroll, { passive: true });
                    document.addEventListener('scroll', notifyScroll, { passive: true, capture: true });
                    document.addEventListener('touchstart', notifyScroll, { passive: true });
                    notifyScroll();
                } catch(e) {}
            })();
        """.trimIndent()
        view?.evaluateJavascript(script, null)
    }
}