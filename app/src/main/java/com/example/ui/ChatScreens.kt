package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatAppRoute(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (userProfile == null || userProfile?.username?.isBlank() == true) {
            // Screen 1: Username Input
            UsernameInputScreen(
                onSaveName = { name -> viewModel.saveUsername(name) }
            )
        } else {
            // Screen 2: Interactive Chatroom
            val currentUsername = userProfile?.username ?: ""
            ChatRoomScreen(
                currentUsername = currentUsername,
                messages = messages,
                isRefreshing = isRefreshing,
                isOnline = isOnline,
                onSendMessage = { text -> viewModel.sendMessage(text) },
                onRetryMessage = { msg -> viewModel.retryMessage(msg) },
                onForceRefresh = { viewModel.forceRefresh() },
                onClearChat = { viewModel.clearChatHistory() }
            )
        }
    }
}

@Composable
fun UsernameInputScreen(
    onSaveName: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var nameInput by remember { mutableStateFlowOf("") }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .navigationBarsPadding()
            .statusBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Aesthetic App Branding Title
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1A237E),
                            Color(0xFF4A148C)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "User Avatar",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to Global Chat",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Connect with anyone running the app globally instantly. Choose a handle to enter the chatroom.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(36.dp))

        // Username Text Field Custom Styling
        OutlinedTextField(
            value = nameInput,
            onValueChange = { nameInput = it },
            label = { Text("Enter Your Name") },
            placeholder = { Text("e.g. Alice, Bob") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("username_input"),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (nameInput.isNotBlank()) {
                        onSaveName(nameInput)
                    }
                }
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                focusManager.clearFocus()
                if (nameInput.isNotBlank()) {
                    onSaveName(nameInput)
                }
            },
            enabled = nameInput.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp)
                .testTag("join_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = "Enter Chatroom",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    currentUsername: String,
    messages: List<ChatMessage>,
    isRefreshing: Boolean,
    isOnline: Boolean,
    onSendMessage: (String) -> Unit,
    onRetryMessage: (ChatMessage) -> Unit,
    onForceRefresh: () -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var textInput by remember { mutableStateFlowOf("") }
    val lazyListState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Scroll to the bottom as soon as new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            lazyListState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Global Chat",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isOnline) Color(0xFF4CAF50) else Color(0xFFE53935))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isOnline) "Live Sync Online" else "Offline - Locally Cached",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                actions = {
                    // Manual refresh action
                    IconButton(
                        onClick = onForceRefresh,
                        modifier = Modifier.testTag("refresh_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Manual Refresh",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Dangerous reset action
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.testTag("delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Chat History",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Message...") },
                        maxLines = 4,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Send
                        ),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (textInput.isNotBlank()) {
                                    onSendMessage(textInput)
                                    textInput = ""
                                }
                            }
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onSendMessage(textInput)
                                textInput = ""
                            }
                        },
                        enabled = textInput.isNotBlank(),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (textInput.isNotBlank()) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Message",
                            tint = if (textInput.isNotBlank()) MaterialTheme.colorScheme.onPrimary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (messages.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "💬",
                            fontSize = 64.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Messages Yet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Be the first to break the ice! Everyone in this global room will see your text.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // Messages List Room
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val isMe = message.sender.trim() == currentUsername.trim()
                        ChatMessageBubble(
                            message = message,
                            isMe = isMe,
                            onRetry = { onRetryMessage(message) }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Clear Chat Room Hierarchy?") },
            text = { Text("This will delete all global chat history on the server and cache files for ALL users of this app. Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearChat()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val contentColor = if (isMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val bubbleShape = if (isMe) {
        RoundedCornerShape(18.dp, 18.dp, 2.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 2.dp)
    }

    val alignment = if (isMe) Alignment.End else Alignment.Start

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chat_bubble_${message.id}"),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (!isMe) {
                // Sender Avatar Circle
                AvatarIcon(
                    name = message.sender,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Column(
                horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
            ) {
                // Name handle tag
                if (!isMe) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
                    )
                }

                Surface(
                    color = bubbleColor,
                    shape = bubbleShape,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        // Timestamp tag
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text(
                                text = formatTime(message.timestamp),
                                fontSize = 10.sp,
                                color = contentColor.copy(alpha = 0.65f)
                            )
                            
                            // Delivery status indicators
                            if (isMe) {
                                Spacer(modifier = Modifier.width(4.dp))
                                when (message.status) {
                                    "SENDING" -> {
                                        CircularProgressIndicator(
                                            color = contentColor.copy(alpha = 0.7f),
                                            strokeWidth = 1.dp,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                    "FAILED" -> {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Failed to Send",
                                            tint = Color.Yellow,
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clickable { onRetry() }
                                        )
                                    }
                                    "SENT" -> {
                                        // Standard check indicator
                                        Text(
                                            text = "✓",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isMe) {
                // Sender Avatar Circle
                Spacer(modifier = Modifier.width(8.dp))
                AvatarIcon(
                    name = message.sender,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun AvatarIcon(
    name: String,
    modifier: Modifier = Modifier
) {
    val avatarLetters = name.trim().take(2).uppercase()
    val backgroundColor = getAvatarColor(name)

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = avatarLetters,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
    }
}

// Pastel generator of colors derived from sender's name hash
fun getAvatarColor(name: String): Color {
    val hash = abs(name.hashCode())
    val colors = listOf(
        Color(0xFFE57373), // Red
        Color(0xFFF06292), // Pink
        Color(0xFFBA68C8), // Purple
        Color(0xFF9575CD), // Deep Purple
        Color(0xFF7986CB), // Indigo
        Color(0xFF64B5F6), // Blue
        Color(0xFF4FC3F7), // Light Blue
        Color(0xFF4DB6AC), // Teal
        Color(0xFF81C784), // Green
        Color(0xFFAED581), // Light Green
        Color(0xFFFFD54F), // Amber
        Color(0xFFFFB74D), // Orange
    )
    return colors[hash % colors.size]
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> mutableStateFlowOf(value: T): MutableState<T> = mutableStateOf(value)
