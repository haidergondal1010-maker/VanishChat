
package com.example.fieldsync

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fieldsync.data.ChatRepository
import com.example.fieldsync.data.RelayMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private val AppBackground = Color(0xFFF7F8FA)
private val PrimaryBlue = Color(0xFF3157D5)
private val DarkText = Color(0xFF172033)
private val SecondaryText = Color(0xFF778197)
private val OwnMessageColor = Color(0xFF3157D5)
private val OtherMessageColor = Color(0xFFFFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                VanishApp()
            }
        }
    }
}

data class ChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val expiresAt: Long,
    val isMine: Boolean,
    val deliveredCount: Int? = null
)

@Composable
fun VanishApp() {
    var currentScreen by remember { mutableStateOf("join") }
    var displayName by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf("") }

    // A new repository is created for each chat session.
    var repository by remember { mutableStateOf(ChatRepository()) }

    // Shut down the active repository when it is replaced or the screen is disposed.
    DisposableEffect(repository) {
        val activeRepository = repository
        onDispose {
            activeRepository.shutdown()
        }
    }

    // Observe the repository's live state.
    val relayMessages by repository.messages.collectAsState()
    val isConnected by repository.connected.collectAsState()
    val relayError by repository.error.collectAsState()
    val onlineMembers by repository.onlineMembers.collectAsState()
    val onlineCount by repository.onlineCount.collectAsState()

    // Convert relay messages into the UI's message model.
    // The repository converts server timestamps using the server clock offset.
    val messages = relayMessages.map { message ->
        ChatMessage(
            id = message.id,
            sender = message.sender,
            text = message.text,
            timestamp = repository.toLocalTime(message.sentAt),
            expiresAt = message.expiresAt,
            isMine = message.isMine,
            deliveredCount = message.deliveredCount
        )
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    when (currentScreen) {
        "join" -> {
            JoinScreen(
                displayName = displayName,
                onDisplayNameChange = { displayName = it },
                isJoining = isJoining,
                serverError = joinError,
                onJoin = {
                    if (!isJoining) {
                        val name = displayName.trim()

                        when {
                            name.isBlank() -> {
                                joinError = "Please enter your display name."
                            }

                            name.length > 30 -> {
                                joinError = "Your display name must be 30 characters or fewer."
                            }

                            else -> {
                                isJoining = true
                                joinError = ""

                                try {
                                    // Keep the same device ID across app sessions.
                                    val prefs = context.getSharedPreferences(
                                        "sync",
                                        Context.MODE_PRIVATE
                                    )

                                    val deviceId = prefs.getString(
                                        "device_id",
                                        null
                                    ) ?: UUID.randomUUID().toString().also {
                                        prefs.edit()
                                            .putString("device_id", it)
                                            .apply()
                                    }

                                    // Start the WebSocket connection.
                                    // The repository handles connection, heartbeat,
                                    // reconnection, message queueing and expiry.
                                    repository.connect(
                                        deviceId = deviceId,
                                        displayName = name
                                    )

                                    displayName = name
                                    currentScreen = "chat"
                                } catch (e: Exception) {
                                    joinError = e.localizedMessage
                                        ?: "Unable to start the relay connection."
                                } finally {
                                    isJoining = false
                                }
                            }
                        }
                    }
                }
            )
        }

        "chat" -> {
            ChatScreen(
                displayName = displayName,
                messages = messages,
                isConnected = isConnected,
                connectionError = relayError.orEmpty(),
                onlineCount = onlineCount,
                onlineMembers = onlineMembers,
                onSendMessage = { text ->
                    repository.sendMessage(text)
                },
                onLeave = {
                    repository.shutdown()
                    repository = ChatRepository()

                    displayName = ""
                    joinError = ""
                    currentScreen = "join"
                }
            )
        }
    }
}

// ---------------------------------------------------------
// JOIN SCREEN
// ---------------------------------------------------------

@Composable
fun JoinScreen(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    isJoining: Boolean,
    serverError: String,
    onJoin: () -> Unit
) {
    var errorMessage by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // App logo
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(PrimaryBlue, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "V",
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Vanish",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "A shared space for everyone.",
                fontSize = 15.sp,
                color = SecondaryText
            )

            Spacer(modifier = Modifier.height(40.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Join Vanish",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Choose a display name to continue.",
                    fontSize = 14.sp,
                    color = SecondaryText
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Display name",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = DarkText
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = displayName,
                    onValueChange = {
                        onDisplayNameChange(it.take(30))
                        errorMessage = ""
                    },
                    placeholder = {
                        Text("e.g. Haider")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your name can contain up to 30 characters.",
                    fontSize = 12.sp,
                    color = SecondaryText
                )

                if (errorMessage.isNotEmpty() || serverError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = errorMessage.ifBlank { serverError },
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        when {
                            displayName.isBlank() -> {
                                errorMessage = "Please enter your display name."
                            }

                            displayName.trim().length > 30 -> {
                                errorMessage = "Your name must be 30 characters or fewer."
                            }

                            else -> {
                                errorMessage = ""
                                onJoin()
                            }
                        }
                    },
                    enabled = !isJoining,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    if (isJoining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Join chat",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Messages disappear 30 seconds after relay acceptance.",
                fontSize = 12.sp,
                color = SecondaryText
            )
        }
    }
}

// ---------------------------------------------------------
// CHAT SCREEN
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    displayName: String,
    messages: List<ChatMessage>,
    isConnected: Boolean,
    connectionError: String,
    onlineCount: Int,
    onlineMembers: List<String>,
    onSendMessage: (String) -> Boolean,
    onLeave: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }

    var showParticipants by remember { mutableStateOf(false) }

    val statusText = when {
        connectionError.isNotBlank() -> connectionError
        isConnected -> "Connected to the Vanish relay"
        else -> "Connecting or reconnecting to relay…"
    }

    val statusColor = when {
        connectionError.isNotBlank() -> Color(0xFFFFEBEE)
        isConnected -> Color(0xFFE8F5E9)
        else -> Color(0xFFFFF3DF)
    }

    val statusTextColor = when {
        connectionError.isNotBlank() -> MaterialTheme.colorScheme.error
        isConnected -> Color(0xFF287A3D)
        else -> Color(0xFF91611D)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppBackground
    ) {
        Scaffold(
            containerColor = AppBackground,

            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Global chat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = DarkText
                            )

                            Text(
                                text = "Signed in as $displayName",
                                fontSize = 12.sp,
                                color = SecondaryText
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                showParticipants = !showParticipants
                            }
                        ) {
                            Text(
                                text = "$onlineCount online",
                                color = PrimaryBlue,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(onClick = onLeave) {
                            Text(
                                text = "Leave",
                                color = PrimaryBlue
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppBackground
                    )
                )
            },

            bottomBar = {
                MessageInput(
                    messageText = messageText,
                    onMessageChange = { messageText = it.take(500) },
                    onSend = {
                        val trimmed = messageText.trim()

                        if (trimmed.isNotEmpty() && onSendMessage(trimmed)) {
                            messageText = ""
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Relay connection status
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = statusColor,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = statusTextColor
                    )
                }

                // Expandable online participants list
                if (showParticipants) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        color = Color.White,
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp)
                        ) {
                            Text(
                                text = "Online participants ($onlineCount)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = DarkText
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (onlineMembers.isEmpty()) {
                                Text(
                                    text = "Waiting for participant information...",
                                    fontSize = 12.sp,
                                    color = SecondaryText
                                )
                            } else {
                                onlineMembers.forEach { member ->
                                    Text(
                                        text = "●  $member",
                                        fontSize = 13.sp,
                                        color = DarkText,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Expiry information
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    color = Color(0xFFFFF3DF),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "⏱",
                            fontSize = 17.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "Messages disappear 30 seconds after relay acceptance.",
                            fontSize = 12.sp,
                            color = Color(0xFF91611D)
                        )
                    }
                }

                if (messages.isEmpty()) {
                    EmptyChat()
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = 16.dp,
                            vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.id }
                        ) { message ->
                            MessageBubble(message)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// EMPTY CHAT STATE
// ---------------------------------------------------------

@Composable
fun EmptyChat() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    Color(0xFFE8EDFF),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✉",
                fontSize = 32.sp,
                color = PrimaryBlue
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "No messages yet",
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = DarkText
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Send a message to everyone connected.",
            fontSize = 14.sp,
            color = SecondaryText
        )
    }
}

// ---------------------------------------------------------
// MESSAGE BUBBLE
// ---------------------------------------------------------

@Composable
fun MessageBubble(message: ChatMessage) {
    val time = remember(message.timestamp) {
        SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        ).format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Column(
            modifier = Modifier.widthIn(max = 290.dp),
            horizontalAlignment = if (message.isMine) {
                Alignment.End
            } else {
                Alignment.Start
            }
        ) {
            if (!message.isMine) {
                Text(
                    text = message.sender,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                    modifier = Modifier.padding(
                        start = 4.dp,
                        bottom = 4.dp
                    )
                )
            }

            Surface(
                color = if (message.isMine) {
                    OwnMessageColor
                } else {
                    OtherMessageColor
                },
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isMine) 16.dp else 4.dp,
                    bottomEnd = if (message.isMine) 4.dp else 16.dp
                ),
                shadowElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 10.dp
                    )
                ) {
                    Text(
                        text = message.text,
                        color = if (message.isMine) {
                            Color.White
                        } else {
                            DarkText
                        },
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = time,
                        color = if (message.isMine) {
                            Color(0xFFDCE4FF)
                        } else {
                            SecondaryText
                        },
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            // The relay's delivered count is shown for our own messages.
            if (message.isMine && message.deliveredCount != null) {
                Text(
                    text = "Received by ${message.deliveredCount} connected apps",
                    fontSize = 10.sp,
                    color = SecondaryText,
                    modifier = Modifier.padding(
                        end = 4.dp,
                        top = 3.dp
                    )
                )
            }
        }
    }
}

// ---------------------------------------------------------
// MESSAGE INPUT
// ---------------------------------------------------------

@Composable
fun MessageInput(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Write a message...")
                },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onSend,
                enabled = messageText.isNotBlank() &&
                        messageText.trim().length <= 500,
                modifier = Modifier.size(52.dp),
                contentPadding = PaddingValues(0.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                )
            ) {
                Text(
                    text = "➤",
                    fontSize = 20.sp,
                    color = Color.White
                )
            }
        }
    }
}
