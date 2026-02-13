package com.example.networkio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
                            onSendMessage = { content -> viewModel.sendGroupChatMessage(content) },
                            groupMembers = uiState.groups.find { it.id == uiState.selectedGroupId }?.members ?: emptyList(),
                            selectedVisibleMembers = uiState.selectedVisibleMembers,
                            onOpenVisibilityDialog = { viewModel.openVisibilityDialog() },
                            onCloseVisibilityDialog = { viewModel.closeVisibilityDialog() },
                            onSetVisibleMembers = { members -> viewModel.setVisibleMembers(members) },
                            showVisibilityDialog = uiState.showVisibilityDialog
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
            // Header with modern gradient
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = androidx.compose.ui.graphics.Color(0xFF6B4CE8),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        text = selectedUser ?: "Select a user to chat",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = androidx.compose.ui.graphics.Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isConnected) 
                                        androidx.compose.ui.graphics.Color(0xFF4ADE80)
                                    else 
                                        androidx.compose.ui.graphics.Color(0xFFEF4444),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                        Text(
                            text = connectionStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
            
            // Messages area with gradient background
            if (selectedUser == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    androidx.compose.ui.graphics.Color(0xFFFAFAFC),
                                    androidx.compose.ui.graphics.Color(0xFFF5F5FA)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "�",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Text(
                            text = "Select a user to start chatting",
                            style = MaterialTheme.typography.titleMedium,
                            color = androidx.compose.ui.graphics.Color(0xFF6B7280)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    androidx.compose.ui.graphics.Color(0xFFFAFAFC),
                                    androidx.compose.ui.graphics.Color(0xFFF5F5FA)
                                )
                            )
                        )
                        .padding(vertical = 12.dp),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(
                            message = message,
                            isCurrentUser = message.from == username,
                            currentUsername = username
                        )
                    }
                }
            }
            
            // Modern input area
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = androidx.compose.ui.graphics.Color.White,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { 
                            Text(
                                if (selectedUser != null) 
                                    "Message $selectedUser..." 
                                else 
                                    "Select a user first...",
                                color = androidx.compose.ui.graphics.Color(0xFF9CA3AF)
                            ) 
                        },
                        enabled = isConnected && selectedUser != null,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = androidx.compose.ui.graphics.Color(0xFF6B4CE8),
                            unfocusedBorderColor = androidx.compose.ui.graphics.Color(0xFFE5E7EB)
                        )
                    )
                    Button(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText)
                                messageText = ""
                            }
                        },
                        enabled = isConnected && messageText.isNotBlank() && selectedUser != null,
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = androidx.compose.ui.graphics.Color(0xFF6B4CE8),
                            disabledContainerColor = androidx.compose.ui.graphics.Color(0xFFE5E7EB)
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                    ) {
                        Text("Send", style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        ))
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
    onSendMessage: (String) -> Unit,
    groupMembers: List<String> = emptyList(),
    selectedVisibleMembers: List<String>? = null,
    onOpenVisibilityDialog: () -> Unit = {},
    onCloseVisibilityDialog: () -> Unit = {},
    onSetVisibleMembers: (List<String>?) -> Unit = {},
    showVisibilityDialog: Boolean = false
) {
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color(0xFFFAFAFC)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Modern group header
            Surface(
                modifier = Modifier.fillMaxWidth(), 
                color = androidx.compose.ui.graphics.Color(0xFF6B4CE8), 
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                groupName, 
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ), 
                                color = androidx.compose.ui.graphics.Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${groupMembers.size} members",
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f)
                            )
                        }
                        // Connection status indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (isConnected) 
                                            androidx.compose.ui.graphics.Color(0xFF4ADE80)
                                        else 
                                            androidx.compose.ui.graphics.Color(0xFFEF4444),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                            Text(
                                text = if (isConnected) "Online" else "Offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
            
            // Messages with gradient background
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                androidx.compose.ui.graphics.Color(0xFFFAFAFC),
                                androidx.compose.ui.graphics.Color(0xFFF5F5FA)
                            )
                        )
                    )
                    .padding(vertical = 12.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        isCurrentUser = message.from == username,
                        currentUsername = username
                    )
                }
            }
            
            // Modern input area
            Surface(
                modifier = Modifier.fillMaxWidth(), 
                color = androidx.compose.ui.graphics.Color.White,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    // Selective visibility chip
                    if (selectedVisibleMembers != null) {
                        Surface(
                            color = androidx.compose.ui.graphics.Color(0xFFEDE9FE),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "🎯",
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Column {
                                        Text(
                                            "Selective message",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = Color(0xFF6B4CE8)
                                        )
                                        Text(
                                            text = "To: ${selectedVisibleMembers.joinToString(", ")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF7C3AED)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onSetVisibleMembers(null) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Text(
                                        "✕", 
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF6B4CE8)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Input row with modern design
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { 
                                Text(
                                    "Message $groupName...",
                                    color = Color(0xFF9CA3AF)
                                ) 
                            },
                            enabled = isConnected,
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF6B4CE8),
                                unfocusedBorderColor = Color(0xFFE5E7EB)
                            )
                        )
                        
                        // Target icon button for selective visibility
                        IconButton(
                            onClick = onOpenVisibilityDialog,
                            enabled = isConnected,
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    color = if (selectedVisibleMembers != null) 
                                        Color(0xFFEDE9FE) 
                                    else 
                                        Color(0xFFF3F4F6),
                                    shape = RoundedCornerShape(20.dp)
                                )
                        ) {
                            Text(
                                text = "🎯",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        
                        Button(
                            onClick = {
                                if (messageText.isNotBlank()) {
                                    onSendMessage(messageText)
                                    messageText = ""
                                }
                            }, 
                            enabled = isConnected && messageText.isNotBlank(),
                            modifier = Modifier.height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF6B4CE8),
                                disabledContainerColor = Color(0xFFE5E7EB)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                "Send", 
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showVisibilityDialog) {
        com.example.networkio.ui.components.VisibilitySelectionDialog(
            members = groupMembers,
            currentUser = username,
            initialSelected = selectedVisibleMembers ?: emptyList(),
            onConfirm = { members ->
                onSetVisibleMembers(members)
            },
            onDismiss = onCloseVisibilityDialog
        )
    }
}
