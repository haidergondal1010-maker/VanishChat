
package com.example.fieldsync

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fieldsync.data.ChatRepository
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

// ---------------------------------------------------------
// THEME COLORS
// ---------------------------------------------------------

private val LightColors = lightColorScheme(
    primary = Color(0xFF3157D5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EDFF),
    onPrimaryContainer = Color(0xFF172D78),

    secondary = Color(0xFF287A3D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondaryContainer = Color(0xFF205C30),

    tertiary = Color(0xFF91611D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF3DF),
    onTertiaryContainer = Color(0xFF704713),

    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFF8C1D18),

    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF172033),

    surface = Color.White,
    onSurface = Color(0xFF172033),

    surfaceVariant = Color(0xFFEEF1F6),
    onSurfaceVariant = Color(0xFF778197),

    outline = Color(0xFFD7DDE7)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF91A8FF),
    onPrimary = Color(0xFF101B3C),
    primaryContainer = Color(0xFF293D78),
    onPrimaryContainer = Color(0xFFDCE4FF),

    secondary = Color(0xFF8DD69C),
    onSecondary = Color(0xFF103A1B),
    secondaryContainer = Color(0xFF205C30),
    onSecondaryContainer = Color(0xFFB8F0C2),

    tertiary = Color(0xFFFFCC80),
    onTertiary = Color(0xFF482D08),
    tertiaryContainer = Color(0xFF704713),
    onTertiaryContainer = Color(0xFFFFE0AE),

    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF10131A),
    onBackground = Color(0xFFE8ECF5),

    surface = Color(0xFF191E29),
    onSurface = Color(0xFFE8ECF5),

    surfaceVariant = Color(0xFF252C3A),
    onSurfaceVariant = Color(0xFFAAB4C6),

    outline = Color(0xFF414B5D)
)

@Composable
private fun VanishTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}

// ---------------------------------------------------------
// MAIN ACTIVITY
// ---------------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            VanishApp()
        }
    }
}

// ---------------------------------------------------------
// MESSAGE MODEL
// ---------------------------------------------------------

data class ChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val expiresAt: Long,
    val isMine: Boolean,
    val deliveredCount: Int? = null
)

// ---------------------------------------------------------
// APP ROOT
// ---------------------------------------------------------

@Composable
fun VanishApp() {
    var currentScreen by remember { mutableStateOf("join") }
    var displayName by remember { mutableStateOf("") }
    var isJoining by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf("") }

    // A new repository is created for each chat session.
    var repository by remember { mutableStateOf(ChatRepository()) }

    // Shut down the active repository when it is replaced or disposed.
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

    var timerNow by remember {
        mutableLongStateOf(System.currentTimeMillis())
    }

    LaunchedEffect(currentScreen, messages.size) {
        if (currentScreen == "chat" && messages.isNotEmpty()) {
            while (true) {
                delay(250)
                timerNow = System.currentTimeMillis()
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Persist the selected theme preference.
    val preferences = remember {
        context.getSharedPreferences(
            "vanish_settings",
            Context.MODE_PRIVATE
        )
    }

    var themeMode by remember {
        mutableStateOf(
            preferences.getString("theme_mode", "system") ?: "system"
        )
    }

    // Follow the Android system theme unless the user selects
    // Light or Dark manually.
    val darkTheme = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }

    val onThemeModeChange: (String) -> Unit = { mode ->
        themeMode = mode
        preferences.edit()
            .putString("theme_mode", mode)
            .apply()
    }

    VanishTheme(darkTheme = darkTheme) {
        when (currentScreen) {
            "join" -> {
                JoinScreen(
                    displayName = displayName,
                    onDisplayNameChange = { displayName = it },
                    isJoining = isJoining,
                    serverError = joinError,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onJoin = {
                        if (!isJoining) {
                            val name = displayName.trim()

                            when {
                                name.isBlank() -> {
                                    joinError =
                                        "Please enter your display name."
                                }

                                name.length > 30 -> {
                                    joinError =
                                        "Your display name must be 30 characters or fewer."
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
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    isConnected = isConnected,
                    connectionError = relayError.orEmpty(),
                    onlineCount = onlineCount,
                    onlineMembers = onlineMembers,
                    remainingLifetime = { expiresAt, now ->
                        repository.remainingLifetime(expiresAt, now)
                    },
                    timerNow = timerNow,
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
}

// ---------------------------------------------------------
// THEME SELECTOR
// ---------------------------------------------------------

@Composable
fun ThemeModeSelector(
    themeMode: String,
    onThemeModeChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = when (themeMode) {
        "light" -> "Light"
        "dark" -> "Dark"
        else -> "System"
    }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = "Theme: $currentLabel",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("System default") },
                onClick = {
                    onThemeModeChange("system")
                    expanded = false
                }
            )

            DropdownMenuItem(
                text = { Text("Light mode") },
                onClick = {
                    onThemeModeChange("light")
                    expanded = false
                }
            )

            DropdownMenuItem(
                text = { Text("Dark mode") },
                onClick = {
                    onThemeModeChange("dark")
                    expanded = false
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
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onJoin: () -> Unit
) {
    var errorMessage by remember { mutableStateOf("") }

    val nameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        delay(300)
        nameFocusRequester.requestFocus()
        keyboardController?.show()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                ThemeModeSelector(
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App logo
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "V",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Vanish",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "A shared space for everyone.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Choose a display name to continue.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Display name",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Your name can contain up to 30 characters.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                errorMessage =
                                    "Please enter your display name."
                            }

                            displayName.trim().length > 30 -> {
                                errorMessage =
                                    "Your name must be 30 characters or fewer."
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
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (isJoining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onLeave: () -> Unit,
    remainingLifetime: (Long, Long) -> Long,
    timerNow: Long,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
) {
    var messageText by remember { mutableStateOf("") }
    var showParticipants by remember { mutableStateOf(false) }

    val statusText = when {
        connectionError.isNotBlank() -> connectionError
        isConnected -> "Connected to the Vanish relay"
        else -> "Connecting or reconnecting to relay…"
    }

    val statusColor = when {
        connectionError.isNotBlank() ->
            MaterialTheme.colorScheme.errorContainer

        isConnected ->
            MaterialTheme.colorScheme.secondaryContainer

        else ->
            MaterialTheme.colorScheme.tertiaryContainer
    }

    val statusTextColor = when {
        connectionError.isNotBlank() ->
            MaterialTheme.colorScheme.onErrorContainer

        isConnected ->
            MaterialTheme.colorScheme.onSecondaryContainer

        else ->
            MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,

            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Global chat",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Text(
                                text = "Signed in as $displayName",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        ThemeModeSelector(
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange
                        )

                        TextButton(
                            onClick = {
                                showParticipants = !showParticipants
                            }
                        ) {
                            Text(
                                text = "$onlineCount online",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        TextButton(onClick = onLeave) {
                            Text(
                                text = "Leave",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
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
                        color = MaterialTheme.colorScheme.surface,
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
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (onlineMembers.isEmpty()) {
                                Text(
                                    text = "Waiting for participant information...",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                onlineMembers.forEach { member ->
                                    Text(
                                        text = "●  $member",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
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
                    color = MaterialTheme.colorScheme.tertiaryContainer,
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
                            color = MaterialTheme.colorScheme.onTertiaryContainer
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
                            val remainingMs = remainingLifetime(
                                message.expiresAt,
                                timerNow
                            )

                            MessageBubble(
                                message = message,
                                remainingMs = remainingMs
                            )
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
                    MaterialTheme.colorScheme.primaryContainer,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✉",
                fontSize = 32.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "No messages yet",
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Send a message to everyone connected.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------
// MESSAGE BUBBLE
// ---------------------------------------------------------

@Composable
fun MessageBubble(
    message: ChatMessage,
    remainingMs: Long
) {
    val time = remember(message.timestamp) {
        SimpleDateFormat(
            "hh:mm a",
            Locale.getDefault()
        ).format(Date(message.timestamp))
    }

    val remainingSeconds = ceil(remainingMs / 1000.0).toInt()
    val progress = (remainingMs / 30_000f).coerceIn(0f, 1f)

    val timerColor = when {
        remainingMs <= 5_000L ->
            MaterialTheme.colorScheme.error

        remainingMs <= 10_000L ->
            MaterialTheme.colorScheme.tertiary

        else ->
            MaterialTheme.colorScheme.primary
    }

    val bubbleColor = if (message.isMine) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val bubbleTextColor = if (message.isMine) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
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
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        start = 4.dp,
                        bottom = 4.dp
                    )
                )
            }

            Surface(
                color = bubbleColor,
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
                        color = bubbleTextColor,
                        fontSize = 15.sp
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = time,
                        color = if (message.isMine) {
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End)
                    )

                    Spacer(modifier = Modifier.height(9.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${remainingSeconds}s",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (remainingMs <= 10_000L) {
                                timerColor
                            } else {
                                bubbleTextColor
                            }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp),
                            color = timerColor,
                            trackColor = if (message.isMine) {
                                MaterialTheme.colorScheme.onPrimary.copy(
                                    alpha = 0.25f
                                )
                            } else {
                                MaterialTheme.colorScheme.outline.copy(
                                    alpha = 0.45f
                                )
                            }
                        )
                    }
                }
            }

            // The relay's delivered count is shown for our own messages.
            if (message.isMine && message.deliveredCount != null) {
                Text(
                    text = "Received by ${message.deliveredCount} connected apps",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        color = MaterialTheme.colorScheme.surface,
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
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "➤",
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}
