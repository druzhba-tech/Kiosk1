package com.kiosk.browser.network.server

import android.util.Log
import com.kiosk.browser.MainActivity
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

class RemoteAdminServer(
    private val port: Int = 8080,
    private val onScreenCommand: (turnOn: Boolean) -> Unit
) {

    private var server: ApplicationEngine? = null

    fun start() {
        if (server != null) return

        try {
            server = embeddedServer(CIO, port = port) {
                routing {
                    // HTML Панель управления
                    get("/") {
                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <meta charset="UTF-8">
                                <title>Kiosk Admin HUD</title>
                                <style>
                                    body { background: #0B0F19; color: #00F0FF; font-family: sans-serif; padding: 20px; }
                                    .card { background: #161F30; border: 1px solid #00F0FF; border-radius: 8px; padding: 20px; max-width: 600px; margin: auto; }
                                    button { background: #00F0FF; color: #000; border: none; padding: 10px 20px; border-radius: 4px; font-weight: bold; cursor: pointer; margin-right: 10px; margin-top: 10px; }
                                    button:hover { background: #00B4D8; }
                                </style>
                            </head>
                            <body>
                                <div class="card">
                                    <h2>KIOSK BROWSER // REMOTE CONSOLE</h2>
                                    <p>Удаленное управление устройством активно.</p>
                                    <button onclick="fetch('/api/screen?state=on', {method:'POST'})">Экран ON</button>
                                    <button onclick="fetch('/api/screen?state=off', {method:'POST'})">Экран OFF</button>
                                    <button onclick="fetch('/api/reload', {method:'POST'})">Обновить страницу</button>
                                </div>
                            </body>
                            </html>
                        """.trimIndent()
                        call.respondText(html, ContentType.Text.Html)
                    }

                    // REST API
                    get("/api/status") {
                        call.respondText(
                            """{"status":"OK","kiosk":"active"}""",
                            ContentType.Application.Json
                        )
                    }

                    post("/api/screen") {
                        val state = call.parameters["state"] ?: "on"
                        onScreenCommand(state.equals("on", ignoreCase = true))
                        call.respondText("""{"result":"success"}""", ContentType.Application.Json)
                    }

                    post("/api/reload") {
                        MainActivity.currentInstance?.reloadCurrentPage()
                        call.respondText("""{"result":"reloading"}""", ContentType.Application.Json)
                    }

                    post("/api/load") {
                        val url = call.parameters["url"]
                        if (!url.isNullOrBlank()) {
                            MainActivity.currentInstance?.loadUrl(url)
                            call.respondText("""{"result":"loaded"}""", ContentType.Application.Json)
                        } else {
                            call.respondText("""{"error":"missing url"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                        }
                    }
                }
            }.start(wait = false)
            Log.i("RemoteServer", "Локальный REST сервер запущен на порту $port")
        } catch (e: Exception) {
            Log.e("RemoteServer", "Ошибка запуска локального сервера: ${e.message}")
        }
    }

    fun stop() {
        try {
            server?.stop(1000L, 2000L)
            server = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
