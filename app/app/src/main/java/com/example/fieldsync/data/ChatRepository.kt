package com.example.fieldsync.data
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatRepository {

    companion object {
        private const val MESSAGE_LIFETIME = 30_000L
        private const val EXPIRY_CHECK_INTERVAL = 250L
    }

    private val relay = RelayWebSocketClient()

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    private val _messages = MutableStateFlow<List<RelayMessage>>(emptyList())
    val messages: StateFlow<List<RelayMessage>> = _messages.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _deliveryUpdates = MutableStateFlow<Map<String, Int>>(emptyMap())
    val deliveryUpdates: StateFlow<Map<String, Int>> =
        _deliveryUpdates.asStateFlow()

    private data class QueuedMessage(
        val id: String,
        val text: String,
        val queuedAt: Long
    )

    private val outgoingQueue = mutableListOf<QueuedMessage>()
    private val seenMessageIds = mutableSetOf<String>()
    private val ownMessageIds = mutableSetOf<String>()

    @Volatile
    private var clockOffset = 0L

    private var eventJob: Job? = null
    private var expiryJob: Job? = null

    @Volatile
    private var started = false

    /**
     * Connect to the relay and join using the supplied identity.
     */
    fun connect(
        deviceId: String,
        displayName: String
    ) {
        if (started) return

        started = true

        // Listen for relay events before opening the connection.
        eventJob = scope.launch {
            relay.events.collect { event ->
                handleEvent(event)
            }
        }

        // Remove messages when their server-authoritative expiry passes.
        expiryJob = scope.launch {
            while (true) {
                delay(EXPIRY_CHECK_INTERVAL)
                removeExpiredMessages()
                removeExpiredQueuedMessages()
            }
        }

        relay.connect(
            deviceId = deviceId,
            displayName = displayName
        )
    }

    /**
     * Send immediately if connected, otherwise queue locally.
     */
    fun sendMessage(text: String): Boolean {
        val trimmed = text.trim()

        if (trimmed.isEmpty() || trimmed.length > 500) {
            _error.value = "Messages must contain 1 to 500 characters."
            return false
        }

        val queuedMessage = QueuedMessage(
            id = UUID.randomUUID().toString(),
            text = trimmed,
            queuedAt = System.currentTimeMillis()
        )

        synchronized(ownMessageIds) {
            ownMessageIds.add(queuedMessage.id)
        }

        synchronized(outgoingQueue) {
            if (relay.isConnected()) {
                val sent = relay.sendMessage(
                    id = queuedMessage.id,
                    text = queuedMessage.text
                )

                if (sent) {
                    _error.value = null
                    return true
                }
            }

            outgoingQueue.add(queuedMessage)
            _error.value = "Message queued. Waiting for relay connection."
            return true
        }
    }

    private fun handleEvent(event: RelayEvent) {
        when (event) {

            is RelayEvent.Welcome -> {
                clockOffset = event.clockOffset
                _connected.value = true
                _error.value = null

                flushOutgoingQueue()
                removeExpiredMessages()
            }

            is RelayEvent.ConnectionChanged -> {
                _connected.value = event.connected
            }

            is RelayEvent.IncomingMessage -> {
                synchronized(seenMessageIds) {
                    if (!seenMessageIds.add(event.id)) {
                        return
                    }
                }

                val message = RelayMessage(
                    id = event.id,
                    sender = event.sender,
                    text = event.text,
                    sentAt = event.sentAt,
                    expiresAt = event.expiresAt,
                    isMine = synchronized(ownMessageIds) {
                        event.id in ownMessageIds
                    }
                )

                val stillValid =
                    event.expiresAt - clockOffset > System.currentTimeMillis()

                if (stillValid) {
                    _messages.value = _messages.value + message
                }
            }

            is RelayEvent.Delivered -> {
                _deliveryUpdates.value =
                    _deliveryUpdates.value + (event.id to event.count)

                _messages.value = _messages.value.map { message ->
                    if (message.id == event.id) {
                        message.copy(deliveredCount = event.count)
                    } else {
                        message
                    }
                }
            }

            is RelayEvent.Expired -> {
                _messages.value = _messages.value.filterNot {
                    it.id == event.id
                }
            }

            is RelayEvent.Error -> {
                _error.value = "${event.code}: ${event.message}"
            }

            is RelayEvent.Pong -> {
                // Heartbeat response received.
            }
        }
    }

    /**
     * Send queued messages after receiving a welcome event.
     */
    private fun flushOutgoingQueue() {
        synchronized(outgoingQueue) {
            val now = System.currentTimeMillis()

            val iterator = outgoingQueue.iterator()

            while (iterator.hasNext()) {
                val queued = iterator.next()

                // The offline queue has a 30-second lifetime.
                if (now - queued.queuedAt >= MESSAGE_LIFETIME) {
                    iterator.remove()
                    continue
                }

                val sent = relay.sendMessage(
                    id = queued.id,
                    text = queued.text
                )

                if (sent) {
                    iterator.remove()
                } else {
                    // Preserve this message and try again after reconnecting.
                    break
                }
            }

            if (outgoingQueue.isEmpty()) {
                _error.value = null
            }
        }
    }

    private fun removeExpiredMessages() {
        val now = System.currentTimeMillis()

        _messages.value = _messages.value.filter { message ->
            message.expiresAt - clockOffset > now
        }
    }

    private fun removeExpiredQueuedMessages() {
        val now = System.currentTimeMillis()

        synchronized(outgoingQueue) {
            outgoingQueue.removeAll { queued ->
                now - queued.queuedAt >= MESSAGE_LIFETIME
            }

            if (outgoingQueue.isEmpty() && _connected.value) {
                _error.value = null
            }
        }
    }

    /**
     * Convert server timestamps into local display timestamps.
     */
    fun toLocalTime(serverTimestamp: Long): Long {
        return serverTimestamp - clockOffset
    }

    fun remainingLifetime(expiresAt: Long): Long {
        return (expiresAt - clockOffset - System.currentTimeMillis())
            .coerceAtLeast(0L)
    }

    fun disconnect() {
        if (!started) return

        started = false

        relay.disconnect()

        eventJob?.cancel()
        expiryJob?.cancel()

        _connected.value = false
    }

    fun shutdown() {
        disconnect()
        relay.shutdown()
        scope.cancel()
    }
}