package com.example.fieldsync.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RelayWebSocketClient(
    private val relayUrl: String =
        "wss://vanish-relay-sawaira.bonto.page/ws"
) {

    companion object {
        private const val TAG = "VanishRelay"
        private const val PROTOCOL_VERSION = 1
        private const val HEARTBEAT_INTERVAL = 15_000L
        private const val PONG_TIMEOUT = 30_000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    private val _events = MutableSharedFlow<RelayEvent>(
        extraBufferCapacity = 64
    )

    val events = _events.asSharedFlow()

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var joined = false

    @Volatile
    private var lastPongTime = 0L

    @Volatile
    private var lastWelcomeTime = 0L

    private var heartbeatJob: Job? = null
    private var connectionJob: Job? = null

    @Volatile
    private var deviceId = ""

    @Volatile
    private var displayName = ""

    @Volatile
    private var stopped = true

    /**
     * Start connecting to the relay.
     * The client automatically rejoins after reconnecting.
     */
    fun connect(
        deviceId: String,
        displayName: String
    ) {
        require(deviceId.isNotBlank()) {
            "Device ID cannot be empty."
        }

        require(displayName.isNotBlank() && displayName.length <= 30) {
            "Display name must contain 1 to 30 characters."
        }

        if (connectionJob?.isActive == true) {
            return
        }

        this.deviceId = deviceId
        this.displayName = displayName
        stopped = false

        connectionJob = scope.launch {
            var reconnectDelay = 1_000L

            while (isActive && !stopped) {
                val terminated = CompletableDeferred<Unit>()

                try {
                    Log.d(TAG, "Connecting to $relayUrl")

                    val request = Request.Builder()
                        .url(relayUrl)
                        .build()

                    val listener = createListener(terminated)

                    socket = client.newWebSocket(request, listener)

                    // Wait until this connection closes or fails.
                    terminated.await()

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Connection error", e)
                } finally {
                    heartbeatJob?.cancel()
                    heartbeatJob = null
                    joined = false
                    socket = null

                    _events.emit(
                        RelayEvent.ConnectionChanged(false)
                    )
                }

                if (!stopped && isActive) {
                    Log.d(
                        TAG,
                        "Reconnecting in ${reconnectDelay / 1000} seconds"
                    )

                    delay(reconnectDelay)

                    reconnectDelay = (reconnectDelay * 2)
                        .coerceAtMost(8_000L)
                }
            }
        }
    }

    private fun createListener(
        terminated: CompletableDeferred<Unit>
    ): WebSocketListener {

        return object : WebSocketListener() {

            override fun onOpen(
                webSocket: WebSocket,
                response: Response
            ) {
                Log.d(TAG, "WebSocket opened")

                val joinFrame = JSONObject().apply {
                    put("type", "join")
                    put("protocol", PROTOCOL_VERSION)
                    put("device_id", deviceId)
                    put("name", displayName)
                }

                webSocket.send(joinFrame.toString())
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String
            ) {
                try {
                    handleFrame(webSocket, text)
                } catch (e: Exception) {
                    Log.e(TAG, "Unable to process relay frame", e)
                }
            }

            override fun onClosing(
                webSocket: WebSocket,
                code: Int,
                reason: String
            ) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(
                webSocket: WebSocket,
                code: Int,
                reason: String
            ) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                terminated.complete(Unit)
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                Log.e(TAG, "WebSocket failure", t)
                terminated.complete(Unit)
            }
        }
    }

    private fun handleFrame(
        webSocket: WebSocket,
        frame: String
    ) {
        val json = JSONObject(frame)

        when (json.optString("type")) {

            "welcome" -> {
                val protocol = json.getInt("protocol")
                val serverTime = json.getLong("server_time")

                if (protocol != PROTOCOL_VERSION) {
                    _events.tryEmit(
                        RelayEvent.Error(
                            code = "bad_protocol",
                            message = "The relay uses an unsupported protocol version."
                        )
                    )

                    webSocket.close(1002, "Unsupported protocol")
                    return
                }

                val phoneTime = System.currentTimeMillis()
                val offset = serverTime - phoneTime
                lastServerClockOffset = offset

                lastPongTime = phoneTime
                lastWelcomeTime = phoneTime
                joined = true

                Log.d(TAG, "Joined relay. Clock offset: $offset ms")

                _events.tryEmit(
                    RelayEvent.ConnectionChanged(true)
                )

                _events.tryEmit(
                    RelayEvent.Welcome(
                        protocol = protocol,
                        serverTime = serverTime,
                        clockOffset = offset
                    )
                )

                startHeartbeat(webSocket)
            }

            "message" -> {
                _events.tryEmit(
                    RelayEvent.IncomingMessage(
                        id = json.getString("id"),
                        sender = json.getString("sender"),
                        text = json.getString("text"),
                        sentAt = json.getLong("sent_at"),
                        expiresAt = json.getLong("expires_at")
                    )
                )
            }

            "delivered" -> {
                _events.tryEmit(
                    RelayEvent.Delivered(
                        id = json.getString("id"),
                        count = json.getInt("count")
                    )
                )
            }

            "expired" -> {
                _events.tryEmit(
                    RelayEvent.Expired(
                        id = json.getString("id")
                    )
                )
            }

            "pong" -> {
                lastPongTime = System.currentTimeMillis()
                _events.tryEmit(RelayEvent.Pong)
            }

            "error" -> {
                _events.tryEmit(
                    RelayEvent.Error(
                        code = json.optString("code", "unknown"),
                        message = json.optString(
                            "message",
                            "The relay returned an error."
                        )
                    )
                )
            }

            else -> {
                Log.w(TAG, "Unknown relay event: $frame")
            }
        }
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        heartbeatJob?.cancel()

        heartbeatJob = scope.launch {
            while (isActive && joined && !stopped) {
                delay(HEARTBEAT_INTERVAL)

                val now = System.currentTimeMillis()

                if (now - lastPongTime >= PONG_TIMEOUT) {
                    Log.w(TAG, "Pong timeout. Reconnecting.")

                    joined = false
                    webSocket.close(1001, "Heartbeat timeout")
                    break
                }

                webSocket.send(
                    JSONObject()
                        .put("type", "ping")
                        .toString()
                )
            }
        }
    }

    /**
     * Send a message using the protocol's JSON format.
     * Returns false if the relay is not currently joined.
     */
    fun sendMessage(
        id: String,
        text: String
    ): Boolean {
        if (!joined || stopped) return false

        if (text.isBlank() || text.length > 500) {
            return false
        }

        val frame = JSONObject().apply {
            put("type", "message")
            put("id", id)
            put("sender", displayName)
            put("text", text)
        }

        return socket?.send(frame.toString()) == true
    }

    fun disconnect() {
        stopped = true
        joined = false

        heartbeatJob?.cancel()
        connectionJob?.cancel()

        socket?.close(1000, "User left")
        socket = null

        _events.tryEmit(
            RelayEvent.ConnectionChanged(false)
        )
    }

    fun shutdown() {
        disconnect()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }

    fun isConnected(): Boolean = joined

    fun getClockOffset(): Long = lastWelcomeTime.let {
        if (it == 0L) 0L else lastServerClockOffset
    }

    @Volatile
    private var lastServerClockOffset = 0L
}