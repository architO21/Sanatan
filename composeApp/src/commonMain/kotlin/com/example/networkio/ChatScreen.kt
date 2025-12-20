package com.example.networkio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.networkio.network.Message
import com.example.networkio.network.SocketRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.random.Random

@Composable
fun ChatScreen() {
    val repository = remember { SocketRepository() }
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var isUsernameSet by remember { mutableStateOf(false) }
    var from by remember { mutableStateOf("") }

    var messages by remember { mutableStateOf(listOf<Message>()) }
    var messageText by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Disconnected") }
    var lastAck by remember { mutableStateOf("") }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    MaterialTheme {
        // Show username input screen if username not set
        if (!isUsernameSet) {
            UsernameInputScreen(
                onUsernameSet = { name ->
                    username = name
                    from = name
                    isUsernameSet = true
                }
            )
        } else {
            // Show chat screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(16.dp)
            ) {
                // Connection Status
                Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF5722)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = connectionStatus,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (lastAck.isNotEmpty()) {
                            Text(
                                text = lastAck,
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                if (isConnected) {
                                    repository.disconnect()
                                    isConnected = false
                                    connectionStatus = "Disconnected"
                                } else {
                                    connectionStatus = "Connecting..."
                                    try {
                                        repository.connectToChat("ws://localhost:8080/ws").collect { response ->
                                            connectionStatus = "Connected as $from"
                                            isConnected = true
                                            println("Received: $response")

                                            // Check if it's an acknowledgment
                                            if (response.contains("MessageReceived")) {
                                                lastAck = "✓ Delivered"
                                                println("Server acknowledged message")
                                            }
                                            // Check if it's an incoming message
                                            else if (response.contains("\"from\"") && response.contains("\"content\"")) {
                                                try {
                                                    val jsonParser = Json { ignoreUnknownKeys = true }
                                                    val incomingMsg = jsonParser.decodeFromString<Message>(response)
                                                    // Only add if it's not already in the list and not from ourselves
                                                    if (messages.none { it.from == incomingMsg.from && it.timestamp == incomingMsg.timestamp }) {
                                                        messages = messages + incomingMsg
                                                    }
                                                } catch (e: Exception) {
                                                    println("Failed to parse message: $e")
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        connectionStatus = "Connection Failed: ${e.message}"
                                        isConnected = false
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = if (isConnected) Color(0xFF4CAF50) else Color(0xFFFF5722)
                        )
                    ) {
                        Text(if (isConnected) "Disconnect" else "Connect")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Messages List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState
            ) {
                items(messages) { message ->
                    MessageItem(
                        message = message,
                        isOwnMessage = message.from == from
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Message Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a message...") },
                    enabled = isConnected
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            scope.launch {
                                val message = Message(
                                    from = from,
                                    content = messageText,
                                    timestamp = System.currentTimeMillis(),
                                )
                                repository.sendMessage(message)
                                messageText = ""
                            }
                        }
                    },
                    enabled = isConnected && messageText.isNotBlank()
                ) {
                    Text("Send")
                }
            }
            }
        }
    }
}

@Composable
fun UsernameInputScreen(onUsernameSet: (String) -> Unit) {
    var username by remember { mutableStateOf("") }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome to NetworkIO Chat! 💬",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Enter your username") },
                placeholder = { Text("e.g., Alice, Bob") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (username.isNotBlank()) {
                        onUsernameSet(username.trim())
                    }
                },
                enabled = username.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Chatting")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "💡 Tip: Open multiple windows to chat with different users",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MessageItem(message: Message, isOwnMessage: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isOwnMessage)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = message.content,
                    color = if (isOwnMessage) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.from,
                    color = if (isOwnMessage) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
