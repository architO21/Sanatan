package com.example.networkio.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.networkio.ui.components.MessageBubble
import com.example.networkio.ui.components.OnlineUsersList
import com.example.networkio.ui.components.GroupList
import com.example.networkio.ui.components.GroupCreationDialog
import com.example.networkio.ui.components.UsernameInputDialog
import com.example.networkio.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(viewModel: ChatViewModel = remember { ChatViewModel() }) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredDirect = remember(uiState.selectedUser, uiState.allMessages) { viewModel.getFilteredMessages() }
    val filteredGroup = remember(uiState.selectedGroupId, uiState.allMessages) { viewModel.getFilteredGroupMessages() }
    
    MaterialTheme {
        if (!uiState.isUsernameSet) {
            UsernameInputDialog(
                onUsernameSet = { username ->
                    viewModel.setUsername(username)
                }
            )
        } else {
            // Top nav tabs: Chat and Groups
            var selectedTab by remember { mutableStateOf(0) }
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Chat") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Groups") })
                }
                if (selectedTab == 0) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Users sidebar
                        Column(modifier = Modifier.width(300.dp).fillMaxHeight()) {
                            Box(modifier = Modifier.weight(1f)) {
                                OnlineUsersList(
                                    onlineUsers = uiState.onlineUsers,
                                    selectedUser = uiState.selectedUser,
                                    onUserSelected = { user -> viewModel.selectUser(user) }
                                )
                            }
                        }
                        DirectChatArea(
                            username = uiState.username,
                            selectedUser = uiState.selectedUser,
                            messages = filteredDirect,
                            isConnected = uiState.isConnected,
                            connectionStatus = uiState.connectionStatus,
                            onSendMessage = { content -> viewModel.sendMessage(content) }
                        )
                    }
                } else {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Groups sidebar with create button
                        Column(modifier = Modifier.width(300.dp).fillMaxHeight()) {
                            GroupList(
                                groups = uiState.groups.map { com.example.networkio.ui.components.GroupItem(it.id, it.name, it.members) },
                                selectedGroupId = uiState.selectedGroupId,
                                onSelect = { id -> viewModel.selectGroup(id) },
                                onCreateGroup = { viewModel.openGroupDialog() }
                            )
                        }
                        GroupChatArea(
                            groupName = uiState.groups.find { it.id == uiState.selectedGroupId }?.name ?: "Group",
                            messages = filteredGroup,
                            username = uiState.username,
                            isConnected = uiState.isConnected,
                            connectionStatus = uiState.connectionStatus,
                            onSendMessage = { content -> viewModel.sendGroupChatMessage(content) }
                        )
                    }
                }
                if (uiState.showGroupDialog) {
                    GroupCreationDialog(
                        onlineUsers = uiState.onlineUsers,
                        currentUser = uiState.username,
                        onDismiss = { viewModel.closeGroupDialog() },
                        onCreateGroup = { name, members -> viewModel.createGroup(name, members) }
                    )
                }
            }
        }
        
        // Error snackbar
        uiState.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(16.dp),
                action = {
                    TextButton(onClick = { viewModel.clearError() }) {
                        Text("Dismiss")
                    }
                }
            ) {
                Text(error)
            }
        }
    }
}

@Composable
private fun DirectChatArea(
    username: String,
    selectedUser: String?,
    messages: List<com.example.networkio.network.Message>,
    isConnected: Boolean,
    connectionStatus: String,
    onSendMessage: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = selectedUser ?: "Select a user to chat",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = connectionStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
            }
            
            // Messages area
            if (selectedUser == null) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "👈 Select a user from the list to start chatting",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(
                            message = message,
                            isCurrentUser = message.from == username
                        )
                    }
                }
            }
            
            // Input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { 
                            Text(
                                if (selectedUser != null) 
                                    "Type a message to $selectedUser..." 
                                else 
                                    "Select a user first..."
                            ) 
                        },
                        enabled = isConnected && selectedUser != null
                    )
                    Button(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText)
                                messageText = ""
                            }
                        },
                        enabled = isConnected && messageText.isNotBlank() && selectedUser != null
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupChatArea(
    groupName: String,
    messages: List<com.example.networkio.network.Message>,
    username: String,
    isConnected: Boolean,
    connectionStatus: String,
    onSendMessage: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 4.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(groupName, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        text = connectionStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message = message, isCurrentUser = message.from == username)
                }
            }
            Surface(modifier = Modifier.fillMaxWidth(), shadowElevation = 8.dp) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message to group $groupName...") },
                        enabled = isConnected
                    )
                    Button(onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    }, enabled = isConnected && messageText.isNotBlank()) {
                        Text("Send")
                    }
                }
            }
        }
    }
}
