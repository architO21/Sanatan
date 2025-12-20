package com.example.networkio.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.networkio.network.Message
import com.example.networkio.network.SocketRepository
import com.example.networkio.network.WebSocketResponseHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Top-level UI model for groups so it can be referenced by ChatUiState and UI screens
data class GroupUi(val id: String, val name: String, val members: List<String>)

data class ChatUiState(
    val username: String = "",
    val isUsernameSet: Boolean = false,
    val selectedUser: String? = null,
    val selectedGroupId: String? = null,
    val allMessages: List<Message> = emptyList(),
    val onlineUsers: List<String> = emptyList(),
    val groups: List<GroupUi> = emptyList(),
    val showGroupDialog: Boolean = false,
    val isConnected: Boolean = false,
    val connectionStatus: String = "Disconnected",
    val error: String? = null
)

class ChatViewModel {
    private val repository = SocketRepository()
    private val viewModelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private lateinit var responseHandler: WebSocketResponseHandler
    
    fun setUsername(name: String) {
        _uiState.update { it.copy(username = name, isUsernameSet = true) }
        connectToServer(name)
    }
    
    fun selectUser(user: String) {
        _uiState.update { it.copy(selectedUser = user) }
    }

    fun openGroupDialog() {
        _uiState.update { it.copy(showGroupDialog = true) }
    }

    fun closeGroupDialog() {
        _uiState.update { it.copy(showGroupDialog = false) }
    }

    fun selectGroup(groupId: String) {
        _uiState.update { it.copy(selectedGroupId = groupId) }
    }
    
    fun sendMessage(content: String) {
        val currentState = _uiState.value
        if (content.isBlank() || currentState.selectedUser == null) return
        
        viewModelScope.launch {
            val message = Message(
                from = currentState.username,
                content = content,
                timestamp = System.currentTimeMillis(),
                sendTo = currentState.selectedUser!!,
            )
            
            println("📤 SENDING MESSAGE: from=${message.from}, to=${message.sendTo}, content=${message.content}")
            
            // Add message to local list immediately for instant UI update
            _uiState.update { state ->
                state.copy(allMessages = state.allMessages + message)
            }
            
            // Send to server
            try {
                repository.sendMessage(message)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to send message: ${e.message}") }
            }
        }
    }
    
    fun getFilteredMessages(): List<Message> {
        val state = _uiState.value
        val selectedUser = state.selectedUser ?: return emptyList()
        
        return state.allMessages.filter { message ->
            (message.from == state.username && message.sendTo == selectedUser) ||
            (message.from == selectedUser && message.sendTo == state.username)
        }
    }

    fun getFilteredGroupMessages(): List<Message> {
        val state = _uiState.value
        val groupId = state.selectedGroupId ?: return emptyList()
        val tag = "group:$groupId"
        return state.allMessages.filter { it.sendTo == tag }
    }

    fun createGroup(name: String, members: List<String>) {
        val owner = _uiState.value.username
        if (name.isBlank() || owner.isBlank()) return
        // Minimal deterministic ID so different clients can match the same group
        val groupId = "$owner:${name.trim()}"
        val group = GroupUi(id = groupId, name = name.trim(), members = (members + owner).distinct())
        _uiState.update { it.copy(groups = it.groups + group, showGroupDialog = false, selectedGroupId = groupId) }
    }

    fun sendGroupChatMessage(content: String) {
        val state = _uiState.value
        val groupId = state.selectedGroupId ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            val message = Message(
                from = state.username,
                content = content,
                timestamp = System.currentTimeMillis(),
                sendTo = "group:$groupId",
            )
            // Local echo
            _uiState.update { it.copy(allMessages = it.allMessages + message) }
            try {
                repository.sendMessage(message)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to send group message: ${e.message}") }
            }
        }
    }
    
    private fun connectToServer(username: String) {
        // Initialize response handler with callbacks
        responseHandler = WebSocketResponseHandler(
            currentUsername = username,
            onConnectionEstablished = { status ->
                _uiState.update { it.copy(connectionStatus = status, isConnected = true) }
            },
            onOnlineUsersUpdated = { users ->
                _uiState.update { it.copy(onlineUsers = users) }
            },
            onUserJoined = { userId ->
                val currentState = _uiState.value
                if (!currentState.onlineUsers.contains(userId)) {
                    _uiState.update { it.copy(onlineUsers = it.onlineUsers + userId) }
                }
            },
            onUserLeft = { userId ->
                _uiState.update { it.copy(onlineUsers = it.onlineUsers.filter { it != userId }) }
            },
            onMessageReceived = { message ->
                val currentState = _uiState.value
                // Check for duplicates before adding
                if (currentState.allMessages.none { it.from == message.from && it.timestamp == message.timestamp }) {
                    _uiState.update { it.copy(allMessages = it.allMessages + message) }
                }
            },
            onGroupCreated = { groupId, groupName ->
                _uiState.update { state ->
                    val existing = state.groups.find { it.id == groupId }
                    val updatedGroups = if (existing == null) {
                        state.groups + GroupUi(id = groupId, name = groupName.ifBlank { groupId }, members = emptyList())
                    } else {
                        state.groups.map { if (it.id == groupId) it.copy(name = groupName.ifBlank { it.name }) else it }
                    }
                    state.copy(groups = updatedGroups)
                }
            },
            onGroupJoined = { groupId, userId ->
                _uiState.update { state ->
                    val isMe = userId == state.username
                    val existing = state.groups.find { it.id == groupId }
                    val group = existing ?: GroupUi(id = groupId, name = groupId, members = emptyList())
                    val members = if (isMe) (group.members + userId).distinct() else group.members
                    val updated = group.copy(members = members)
                    val newList = if (existing == null) state.groups + updated else state.groups.map { if (it.id == groupId) updated else it }
                    state.copy(groups = newList)
                }
            },
            onGroupLeft = { groupId, userId ->
                _uiState.update { state ->
                    val existing = state.groups.find { it.id == groupId } ?: return@update state
                    val updated = existing.copy(members = existing.members.filterNot { it == userId })
                    state.copy(groups = state.groups.map { if (it.id == groupId) updated else it })
                }
            },
            onError = { error ->
                _uiState.update { it.copy(error = error) }
            }
        )
        
        viewModelScope.launch {
            _uiState.update { it.copy(connectionStatus = "Connecting...") }
            
            try {
                repository.connectToChat("ws://localhost:8080/ws?id=$username").collect { response ->
                    _uiState.update { it.copy(connectionStatus = "Connected as $username", isConnected = true) }
                    responseHandler.handleResponse(response)
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        connectionStatus = "Connection Failed: ${e.message}",
                        isConnected = false,
                        error = e.message
                    )
                }
            }
        }
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
