
package com.example.fieldsync.data

sealed class RelayEvent {

    data class Welcome(
        val protocol: Int,
        val serverTime: Long,
        val clockOffset: Long
    ) : RelayEvent()

    data class IncomingMessage(
        val id: String,
        val sender: String,
        val text: String,
        val sentAt: Long,
        val expiresAt: Long
    ) : RelayEvent()

    data class Delivered(
        val id: String,
        val count: Int
    ) : RelayEvent()

    data class Expired(
        val id: String
    ) : RelayEvent()

    data class Presence(
        val count: Int,
        val members: List<String>
    ) : RelayEvent()

    data object Pong : RelayEvent()

    data class Error(
        val code: String,
        val message: String
    ) : RelayEvent()

    data class ConnectionChanged(
        val connected: Boolean
    ) : RelayEvent()
}

data class RelayMessage(
    val id: String,
    val sender: String,
    val text: String,
    val sentAt: Long,
    val expiresAt: Long,
    val isMine: Boolean,
    val deliveredCount: Int? = null
)
