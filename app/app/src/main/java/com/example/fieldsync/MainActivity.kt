package com.example.fieldsync

import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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

private val BrandStart = Color(0xFF7C5CFF)
private val BrandMid = Color(0xFF5B8CFF)
private val BrandEnd = Color(0xFF3BA7FF)
private val AuroraPink = Color(0xFFFF5CAA)

private val BrandBrush = Brush.linearGradient(listOf(BrandStart, BrandEnd))

private val StatusGreen = Color(0xFF34D399)
private val StatusAmber = Color(0xFFFBBF24)
private val StatusRed = Color(0xFFF87171)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4BDB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E2FF),
    onPrimaryContainer = Color(0xFF2A1F7A),

    secondary = Color(0xFF1E8E5A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF7EA),
    onSecondaryContainer = Color(0xFF0F5C37),

    tertiary = Color(0xFFB36A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEFD0),
    onTertiaryContainer = Color(0xFF6B4000),

    error = Color(0xFFD13A3A),
    errorContainer = Color(0xFFFFE3E1),
    onErrorContainer = Color(0xFF8C1D18),

    background = Color(0xFFEEF1FF),
    onBackground = Color(0xFF161B33),

    surface = Color.White,
    onSurface = Color(0xFF161B33),

    surfaceVariant = Color(0xFFE8EBF8),
    onSurfaceVariant = Color(0xFF5A6482),

    outline = Color(0xFFCBD2E8)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4A9FF),
    onPrimary = Color(0xFF1B1257),
    primaryContainer = Color(0xFF3A2FA0),
    onPrimaryContainer = Color(0xFFE5E0FF),

    secondary = Color(0xFF7BE0AE),
    onSecondary = Color(0xFF05321C),
    secondaryContainer = Color(0xFF14573A),
    onSecondaryContainer = Color(0xFFBFF5D8),

    tertiary = Color(0xFFFFCC80),
    onTertiary = Color(0xFF482D08),
    tertiaryContainer = Color(0xFF6B4313),
    onTertiaryContainer = Color(0xFFFFE0AE),

    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF601410),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0B0E1A),
    onBackground = Color(0xFFE9ECF8),

    surface = Color(0xFF151A2B),
    onSurface = Color(0xFFE9ECF8),

    surfaceVariant = Color(0xFF232A40),
    onSurfaceVariant = Color(0xFFB3BDD6),

    outline = Color(0xFF3F4863)
)

private val LocalIsDark = staticCompositionLocalOf { false }

@Composable
private fun VanishTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalIsDark provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content
        )
    }
}

// ---------------------------------------------------------
// GLASSMORPHISM HELPERS
// ---------------------------------------------------------

/**
 * Frosted-glass surface: translucent gradient fill plus a bright,
 * fading edge highlight. Sits on top of [AuroraBackground] so the
 * colour orbs show through.
 */
@Composable
private fun Modifier.glass(
    shape: Shape = RoundedCornerShape(20.dp),
    strong: Boolean = false,
    highlighted: Boolean = false
): Modifier {
    val dark = LocalIsDark.current
    val primary = MaterialTheme.colorScheme.primary

    val fill = if (dark) {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (strong) 0.16f else 0.10f),
                Color.White.copy(alpha = if (strong) 0.07f else 0.04f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = if (strong) 0.74f else 0.55f),
                Color.White.copy(alpha = if (strong) 0.46f else 0.32f)
            )
        )
    }

    val border = when {
        highlighted -> Brush.linearGradient(
            listOf(primary, primary.copy(alpha = 0.45f))
        )

        dark -> Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.36f), Color.White.copy(alpha = 0.05f))
        )

        else -> Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.35f))
        )
    }

    return this
        .clip(shape)
        .background(fill, shape)
        .border(if (highlighted) 1.5.dp else 1.dp, border, shape)
}

private fun DrawScope.drawOrb(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/** Slowly drifting colour orbs on a deep gradient – the "backdrop" for the glass. */
@Composable
private fun AuroraBackground(content: @Composable BoxScope.() -> Unit) {
    val dark = LocalIsDark.current

    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_phase"
    )

    val baseColors = if (dark) {
        listOf(Color(0xFF0B0E1A), Color(0xFF140F33))
    } else {
        listOf(Color(0xFFEAF0FF), Color(0xFFF6ECFF))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(baseColors))
            .drawBehind {
                val a = if (dark) 0.55f else 0.45f
                val w = size.width
                val h = size.height

                drawOrb(
                    Offset(w * (0.10f + 0.35f * phase), h * 0.10f),
                    w * 0.85f,
                    BrandStart.copy(alpha = a)
                )
                drawOrb(
                    Offset(w * (0.95f - 0.35f * phase), h * 0.48f),
                    w * 0.75f,
                    BrandEnd.copy(alpha = a * 0.85f)
                )
                drawOrb(
                    Offset(w * (0.15f + 0.45f * phase), h * 0.95f),
                    w * 0.85f,
                    AuroraPink.copy(alpha = a * 0.65f)
                )
            },
        content = content
    )
}

@Composable
private fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(color, CircleShape)
    )
}

// ---------------------------------------------------------
// MAIN ACTIVITY
// ---------------------------------------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Let the aurora background run behind the system bars.
        enableEdgeToEdge()

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
    onThemeModeChange: (String) -> Unit,
    compact: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }

    val currentLabel = when (themeMode) {
        "light" -> "Light"
        "dark" -> "Dark"
        else -> "System"
    }

    val icon = when (themeMode) {
        "light" -> "☀"
        "dark" -> "☾"
        else -> "◐"
    }

    Box {
        Box(
            modifier = Modifier
                .glass(CircleShape)
                .clickable { expanded = true }
                .padding(
                    horizontal = if (compact) 12.dp else 16.dp,
                    vertical = 8.dp
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (compact) icon else "$icon  $currentLabel",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                fontSize = if (compact) 16.sp else 14.sp
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(if (themeMode == "system") "✓  System default" else "System default")
                },
                onClick = {
                    onThemeModeChange("system")
                    expanded = false
                }
            )

            DropdownMenuItem(
                text = {
                    Text(if (themeMode == "light") "✓  Light mode" else "Light mode")
                },
                onClick = {
                    onThemeModeChange("light")
                    expanded = false
                }
            )

            DropdownMenuItem(
                text = {
                    Text(if (themeMode == "dark") "✓  Dark mode" else "Dark mode")
                },
                onClick = {
                    onThemeModeChange("dark")
                    expanded = false
                }
            )
        }
    }
}

// ---------------------------------------------------------
// GLASS TEXT FIELD
// ---------------------------------------------------------

@Composable
private fun InputText(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    maxLines: Int = 1,
    interactionSource: MutableInteractionSource? = null
) {
    val colors = MaterialTheme.colorScheme

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        maxLines = if (singleLine) 1 else maxLines,
        textStyle = TextStyle(color = colors.onSurface, fontSize = 16.sp),
        cursorBrush = SolidColor(colors.primary),
        interactionSource = interactionSource ?: remember { MutableInteractionSource() },
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 16.sp
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp)
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Box(
        modifier = modifier
            .glass(shape, strong = true, highlighted = focused)
            .padding(horizontal = 16.dp, vertical = 15.dp)
    ) {
        InputText(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            interactionSource = interaction
        )
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

    val submit = {
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
    }

    AuroraBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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

            Spacer(modifier = Modifier.weight(1f))

            // App logo with a soft glow
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(
                                    BrandStart.copy(alpha = 0.55f),
                                    Color.Transparent
                                )
                            ),
                            radius = size.minDimension * 0.95f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(BrandBrush)
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.85f),
                                    Color.White.copy(alpha = 0.1f)
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.vanish_logo),
                        contentDescription = "Vanish logo",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Vanish",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "A shared space for everyone.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Glass join card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glass(RoundedCornerShape(28.dp), strong = true)
                    .padding(22.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Join Vanish",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Choose a display name to continue.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Display name",
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                GlassTextField(
                    value = displayName,
                    onValueChange = {
                        onDisplayNameChange(it.take(30))
                        errorMessage = ""
                    },
                    placeholder = "e.g. Haider",
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester)
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

                Spacer(modifier = Modifier.height(22.dp))

                // Gradient join button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .alpha(if (isJoining) 0.7f else 1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(BrandStart, BrandMid, BrandEnd)
                            )
                        )
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.6f),
                                    Color.White.copy(alpha = 0.05f)
                                )
                            ),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = !isJoining) { submit() },
                    contentAlignment = Alignment.Center
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
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Messages disappear 30 seconds after relay acceptance.",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------
// CHAT HEADER
// ---------------------------------------------------------

@Composable
private fun GlassHeader(
    displayName: String,
    onlineCount: Int,
    onToggleParticipants: () -> Unit,
    themeMode: String,
    onThemeModeChange: (String) -> Unit,
    onLeave: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(26.dp), strong = true)
                .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Global chat",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Signed in as $displayName",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Online pill – tap to expand participants
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .clickable(onClick = onToggleParticipants)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(StatusGreen)

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "$onlineCount online",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            ThemeModeSelector(
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                compact = true
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onLeave)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Leave",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ---------------------------------------------------------
// CHAT SCREEN
// ---------------------------------------------------------

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

    val listState = rememberLazyListState()

    // Keep the newest message in view.
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val statusText = when {
        connectionError.isNotBlank() -> connectionError
        isConnected -> "Connected to the Vanish relay"
        else -> "Connecting or reconnecting to relay…"
    }

    val statusDotColor = when {
        connectionError.isNotBlank() -> StatusRed
        isConnected -> StatusGreen
        else -> StatusAmber
    }

    AuroraBackground {
        Scaffold(
            containerColor = Color.Transparent,

            topBar = {
                GlassHeader(
                    displayName = displayName,
                    onlineCount = onlineCount,
                    onToggleParticipants = {
                        showParticipants = !showParticipants
                    },
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    onLeave = onLeave
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .glass(RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDot(statusDotColor, size = 9.dp)

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = if (connectionError.isNotBlank()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }

                // Expandable online participants list
                AnimatedVisibility(visible = showParticipants) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .glass(RoundedCornerShape(20.dp), strong = true)
                            .padding(16.dp)
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(BrandBrush),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = member.take(1).uppercase(),
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Text(
                                        text = member,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    StatusDot(StatusGreen, size = 7.dp)
                                }
                            }
                        }
                    }
                }

                // Expiry information
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .glass(RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⏱",
                        fontSize = 16.sp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = "Messages disappear 30 seconds after relay acceptance.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (messages.isEmpty()) {
                    EmptyChat()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
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
                .size(84.dp)
                .glass(CircleShape, strong = true),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✉",
                fontSize = 34.sp,
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

    val mine = message.isMine

    // Colours are chosen so the timer stays visible on the gradient bubble too.
    val timerColor = when {
        remainingMs <= 5_000L ->
            if (mine) Color(0xFFFFB4AB) else MaterialTheme.colorScheme.error

        remainingMs <= 10_000L ->
            if (mine) Color(0xFFFFE0A3) else MaterialTheme.colorScheme.tertiary

        else ->
            if (mine) Color.White else MaterialTheme.colorScheme.primary
    }

    val bubbleTextColor = if (mine) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val subTextColor = if (mine) {
        Color.White.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (mine) 20.dp else 6.dp,
        bottomEnd = if (mine) 6.dp else 20.dp
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        Column(
            modifier = Modifier.widthIn(max = 290.dp),
            horizontalAlignment = if (mine) {
                Alignment.End
            } else {
                Alignment.Start
            }
        ) {
            if (!mine) {
                Text(
                    text = message.sender,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        start = 6.dp,
                        bottom = 4.dp
                    )
                )
            }

            val bubbleModifier = if (mine) {
                Modifier
                    .clip(bubbleShape)
                    .background(BrandBrush, bubbleShape)
                    .border(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        bubbleShape
                    )
            } else {
                Modifier.glass(bubbleShape, strong = true)
            }

            Column(
                modifier = bubbleModifier.padding(
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
                    color = subTextColor,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.End)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${remainingSeconds}s",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = timerColor
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Countdown bar
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (mine) {
                                    Color.White.copy(alpha = 0.28f)
                                } else {
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                }
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(timerColor)
                        )
                    }
                }
            }

            // The relay's delivered count is shown for our own messages.
            if (mine && message.deliveredCount != null) {
                Text(
                    text = "Received by ${message.deliveredCount} connected apps",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        end = 6.dp,
                        top = 4.dp
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
    val canSend = messageText.isNotBlank() &&
            messageText.trim().length <= 500

    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .glass(
                RoundedCornerShape(30.dp),
                strong = true,
                highlighted = focused
            )
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        InputText(
            value = messageText,
            onValueChange = onMessageChange,
            placeholder = "Write a message...",
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
            maxLines = 4,
            interactionSource = interaction
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    if (canSend) {
                        BrandBrush
                    } else {
                        SolidColor(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                        )
                    }
                )
                .clickable(enabled = canSend, onClick = onSend),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "➤",
                fontSize = 18.sp,
                color = if (canSend) {
                    Color.White
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}
