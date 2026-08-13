// SPDX-FileCopyrightText: 2015 - 2025 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.clipboard.sync

import com.osfans.trime.data.clipboard.model.ProfileDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class SignalRClient(
    private val hubUrl: String,
    private val authHeader: String?,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "SignalRClient"
        private const val RECORD_SEPARATOR = '\u001e'
        private const val INITIAL_BACKOFF_MS = 2000L
        private const val MAX_BACKOFF_MS = 30000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }

    enum class ConnectionState {
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        DISCONNECTED,
    }

    interface Listener {
        fun onProfileChanged(profile: ProfileDto)
        fun onHistoryChanged(hash: String, type: String)
        fun onStateChanged(state: ConnectionState)
    }

    var listener: Listener? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val json: Json = Json { ignoreUnknownKeys = true }

    @Volatile private var webSocket: WebSocket? = null

    @Volatile private var currentState: ConnectionState = ConnectionState.DISCONNECTED

    private var connectJob: Job? = null

    private var reconnectAttempt = 0

    fun connect() {
        if (currentState == ConnectionState.CONNECTED ||
            currentState == ConnectionState.CONNECTING
        ) {
            return
        }
        connectJob?.cancel()
        connectJob = scope.launch(Dispatchers.IO) {
            doConnect()
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        reconnectAttempt = 0
        try {
            webSocket?.close(1000, "Client disconnect")
        } catch (e: Throwable) {
            Timber.tag(TAG).e(e, "Failed to close WebSocket")
        }
        webSocket = null
        updateState(ConnectionState.DISCONNECTED)
    }

    private suspend fun doConnect() {
        var attempt = 0
        while (coroutineContext.isActive) {
            try {
                updateState(
                    if (attempt == 0) {
                        ConnectionState.CONNECTING
                    } else {
                        ConnectionState.RECONNECTING
                    },
                )
                val url = buildWebSocketUrl()

                val builder = Request.Builder().url(url)
                authHeader?.let { builder.header("Authorization", it) }

                var closed = false
                val socket = client.newWebSocket(
                    builder.build(),
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            Timber.tag(TAG).d("WebSocket opened")
                            reconnectAttempt = 0
                            updateState(ConnectionState.CONNECTED)
                            sendHandshake(webSocket)
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            handleMessage(text)
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            Timber.tag(TAG).d("WebSocket closing: $code $reason")
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            Timber.tag(TAG).d("WebSocket closed: $code $reason")
                            closed = true
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            Timber.tag(TAG).e(t, "WebSocket failure")
                            closed = true
                        }
                    },
                )

                webSocket = socket

                while (coroutineContext.isActive && !closed) {
                    delay(500)
                }
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "WebSocket connect failed")
            }

            if (currentState == ConnectionState.DISCONNECTED) return

            attempt++
            if (attempt > MAX_RECONNECT_ATTEMPTS) {
                Timber.tag(TAG).w("Max reconnect attempts reached")
                updateState(ConnectionState.DISCONNECTED)
                return
            }

            val backoff = calculateBackoff(attempt)
            Timber.tag(TAG).d("Reconnecting in ${backoff}ms (attempt $attempt)")
            delay(backoff)
        }
    }

    private fun sendHandshake(socket: WebSocket) {
        val message = """{"protocol":"json","version":1}$RECORD_SEPARATOR"""
        socket.send(message)
    }

    private fun handleMessage(text: String) {
        val messages = text.split(RECORD_SEPARATOR)
        for (message in messages) {
            val trimmed = message.trim()
            if (trimmed.isEmpty()) continue
            try {
                val obj = json.parseToJsonElement(trimmed) as? JsonObject ?: continue

                val messageType = obj["type"]?.jsonPrimitive?.content?.toIntOrNull()
                when (messageType) {
                    1 -> handleInvocation(obj)

                    6 -> {
                        val socket = webSocket ?: continue
                        socket.send("""{"type":6}$RECORD_SEPARATOR""")
                    }

                    null -> {
                        Timber.tag(TAG).d("Handshake response: $trimmed")
                    }

                    else -> {
                        Timber.tag(TAG).d("Unknown message type: $messageType")
                    }
                }
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to parse SignalR message")
            }
        }
    }

    private fun handleInvocation(obj: JsonObject) {
        val target = obj["target"]?.jsonPrimitive?.content ?: return
        val arguments = obj["arguments"] as? JsonArray ?: return

        when (target) {
            "RemoteProfileChanged" -> {
                val arg0 = arguments.firstOrNull() as? JsonObject ?: return
                val profile = parseProfileDto(arg0)
                listener?.onProfileChanged(profile)
            }

            "RemoteHistoryChanged" -> {
                val arg0 = arguments.firstOrNull() as? JsonObject ?: return
                val hash = arg0["Hash"]?.jsonPrimitive?.content
                    ?: arg0["hash"]?.jsonPrimitive?.content ?: ""
                val type = arg0["Type"]?.jsonPrimitive?.content
                    ?: arg0["type"]?.jsonPrimitive?.content ?: ""
                listener?.onHistoryChanged(hash, type)
            }
        }
    }

    private fun parseProfileDto(obj: JsonObject): ProfileDto {
        val type = obj["Type"]?.jsonPrimitive?.content
            ?: obj["type"]?.jsonPrimitive?.content ?: "Text"
        val hash = obj["Hash"]?.jsonPrimitive?.content
            ?: obj["hash"]?.jsonPrimitive?.content
        val text = obj["Text"]?.jsonPrimitive?.content
            ?: obj["text"]?.jsonPrimitive?.content ?: ""
        val hasData = obj["HasData"]?.jsonPrimitive?.content?.toBooleanStrictOrNull()
            ?: obj["hasData"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val dataName = obj["DataName"]?.jsonPrimitive?.content
            ?: obj["dataName"]?.jsonPrimitive?.content
        val size = obj["Size"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: obj["size"]?.jsonPrimitive?.content?.toLongOrNull()
        return ProfileDto(
            type = type,
            hash = hash,
            text = text,
            hasData = hasData,
            dataName = dataName,
            size = size,
        )
    }

    private fun buildWebSocketUrl(): String {
        val lower = hubUrl.lowercase()
        return if (lower.startsWith("http://")) {
            hubUrl.replace("http://", "ws://")
        } else if (lower.startsWith("https://")) {
            hubUrl.replace("https://", "wss://")
        } else {
            "wss://$hubUrl"
        }
    }

    private fun updateState(state: ConnectionState) {
        if (currentState != state) {
            currentState = state
            try {
                listener?.onStateChanged(state)
            } catch (e: Throwable) {
                Timber.tag(TAG).e(e, "Failed to notify state change")
            }
        }
    }

    private fun calculateBackoff(attempt: Int): Long = minOf(INITIAL_BACKOFF_MS * (1L shl minOf(attempt - 1, 10)), MAX_BACKOFF_MS)
}
