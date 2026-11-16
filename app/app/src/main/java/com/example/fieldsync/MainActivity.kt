
package com.example.fieldsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

// Represents a message in the temporary UI preview.
// The relay will provide the authoritative message data later.
data class ChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val isMine: Boolean
)

@Composable
fun VanishApp() {
    var currentScreen by remember { mutableStateOf("join") }

    var displayName by remember { mutableStateOf("") }
    var serverUrl by remember {
        mutableStateOf("http://192.168.1.100:8000/")
    }

    var messages by remember {
        mutableStateOf(listOf<ChatMessage>())
    }

    when (currentScreen) {
        "join" -> {
            JoinScreen(
                displayName = displayName,
                onDisplayNameChange = { displayName = it },
                serverUrl = serverUrl,
                onServerUrlChange = { serverUrl = it },
                onJoin = {
                    currentScreen = "chat"
                }
            )
        }

        "chat" -> {
            ChatScreen(
                displayName = displayName,
                messages = messages,
                onSendMessage = { text ->
                    messages = messages + ChatMessage(
                        id = java.util.UUID.randomUUID().toString(),
                        sender = displayName,
                        text = text,
                        timestamp = System.currentTimeMillis(),
                        isMine = true
                    )
                },
                onLeave = {
                    messages = emptyList()
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
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
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
                text = "A private space for your class.",
                fontSize = 15.sp,
                color = SecondaryText
            )

            Spacer(modifier = Modifier.height(40.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Join your class",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DarkText
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Enter your details to continue.",
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
                        onDisplayNameChange(it)
                        errorMessage = ""
                    },
                    placeholder = {
                        Text("e.g. Haider")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Relay server URL",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = DarkText
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        onServerUrlChange(it)
                        errorMessage = ""
                    },
                    placeholder = {
                        Text("http://192.168.1.100:8000/")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Room: class",
                    fontSize = 13.sp,
                    color = SecondaryText
                )

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val validUrl =
                            serverUrl.startsWith("http://") ||
                                    serverUrl.startsWith("https://")

                        when {
                            displayName.isBlank() -> {
                                errorMessage =
                                    "Please enter your display name."
                            }

                            !validUrl -> {
                                errorMessage =
                                    "Enter a valid HTTP or HTTPS server URL."
                            }

                            else -> {
                                errorMessage = ""
                                onJoin()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    Text(
                        text = "Join class",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
    onSendMessage: (String) -> Unit,
    onLeave: () -> Unit
) {
    var messageText by remember { mutableStateOf("") }

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
                                text = "Class room",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = DarkText
                            )

                            Text(
                                text = "Shared class chat",
                                fontSize = 12.sp,
                                color = SecondaryText
                            )
                        }
                    },
                    actions = {
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
                    onMessageChange = { messageText = it },
                    onSend = {
                        val trimmed = messageText.trim()

                        if (trimmed.isNotEmpty()) {
                            onSendMessage(trimmed)
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

                // Temporary development indicator
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color(0xFFE8EDFF),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "UI preview — relay not connected yet",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 12.sp,
                        color = PrimaryBlue
                    )
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
                            text = "Messages disappear 30 seconds after acceptance.",
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
            text = "Start a conversation with your class.",
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
                enabled = messageText.isNotBlank(),
                modifier = Modifier.size(52.dp),
                contentPadding = PaddingValues(0.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                )
            ) {
                Text(
                    text = "➤",
                    fontSize = 21.sp
                )
            }
        }
    }
}
