package com.example.networkio.network

import kotlinx.serialization.json.Json

class WebSocketResponseHandler(
    private val currentUsername: String,
    private val onConnectionEstablished: (String) -> Unit,
    private val onOnlineUsersUpdated: (List<String>) -> Unit,
    private val onUserJoined: (String) -> Unit,
    private val onUserLeft: (String) -> Unit,
    private val onMessageReceived: (Message) -> Unit,
    // Group callbacks
    private val onGroupCreated: (groupId: String, groupName: String, members: List<String>) -> Unit,
    private val onGroupJoined: (groupId: String, userId: String) -> Unit,
    private val onGroupLeft: (groupId: String, userId: String) -> Unit,
    private val onError: (String) -> Unit
) {
    private val jsonParser = Json { 
        ignoreUnknownKeys = true 
    }
    
    fun handleResponse(response: String) {
        println("Received: $response")
        
        when {
            response.contains("OnlineUsers") -> handleOnlineUsersEvent(response)
            response.contains("\"type\":\"com.example.networkio.model.ServerEvent.Joined\"") -> handleUserJoinedEvent(response)
            response.contains("\"type\":\"com.example.networkio.model.ServerEvent.Left\"") -> handleUserLeftEvent(response)
            response.contains("\"from\"") && response.contains("\"content\"") -> handleIncomingMessage(response)
            // Group events
            response.contains("\"type\":\"com.example.networkio.model.ServerEvent.GroupCreated\"") -> handleGroupCreated(response)
            response.contains("\"type\":\"com.example.networkio.model.ServerEvent.GroupJoined\"") -> handleGroupJoined(response)
            response.contains("\"type\":\"com.example.networkio.model.ServerEvent.GroupLeft\"") -> handleGroupLeft(response)
        }
    }
    
    private fun handleOnlineUsersEvent(response: String) {
        try {
            val usersPattern = """"users":\[(.*?)\]""".toRegex()
            val match = usersPattern.find(response)
            if (match != null) {
                val usersList = match.groupValues[1]
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it != currentUsername } // Exclude self
                
                onOnlineUsersUpdated(usersList)
                println("📋 Online users: $usersList")
            }
        } catch (e: Exception) {
            val error = "Failed to parse online users: ${e.message}"
            println(error)
            onError(error)
        }
    }
    
    private fun handleUserJoinedEvent(response: String) {
        try {
            val idPattern = """"id":"(.*?)"""".toRegex()
            val match = idPattern.find(response)
            if (match != null) {
                val newUserId = match.groupValues[1]
                if (newUserId != currentUsername) {
                    onUserJoined(newUserId)
                    println("👋 User joined: $newUserId")
                }
            }
        } catch (e: Exception) {
            val error = "Failed to parse joined event: ${e.message}"
            println(error)
            onError(error)
        }
    }
    
    private fun handleUserLeftEvent(response: String) {
        try {
            val idPattern = """"id":"(.*?)"""".toRegex()
            val match = idPattern.find(response)
            if (match != null) {
                val leftUserId = match.groupValues[1]
                onUserLeft(leftUserId)
                println("👋 User left: $leftUserId")
            }
        } catch (e: Exception) {
            val error = "Failed to parse left event: ${e.message}"
            println(error)
            onError(error)
        }
    }
    
    private fun handleIncomingMessage(response: String) {
        try {
            println("📥 RAW MESSAGE RECEIVED: $response")
            val incomingMsg = jsonParser.decodeFromString<Message>(response)
            println("📥 Parsed message: from=${incomingMsg.from}, to=${incomingMsg.sendTo}, content=${incomingMsg.content}")
            
            onMessageReceived(incomingMsg)
            println("💬 Message received: from ${incomingMsg.from} to ${incomingMsg.sendTo}")
        } catch (e: Exception) {
            val error = "Failed to parse message: ${e.message}"
            println(error)
            onError(error)
        }
    }

    private fun handleGroupCreated(response: String) {
        try {
            println("DEBUG: Full GroupCreated response: $response")
            val idPattern = "\"groupId\":\"(.*?)\"".toRegex()
            val namePattern = "\"groupName\":\"(.*?)\"".toRegex()
            val membersPattern = "\"members\":\\[(.*?)\\]".toRegex()

            val groupId = idPattern.find(response)?.groupValues?.get(1) ?: return
            val groupName = namePattern.find(response)?.groupValues?.get(1) ?: ""
            
            // Extract members list
            val members = mutableListOf<String>()
            val membersMatch = membersPattern.find(response)
            if (membersMatch != null) {
                val membersStr = membersMatch.groupValues[1]
                if (membersStr.isNotBlank()) {
                    membersStr.split(",")
                        .map { it.trim().removeSurrounding("\"") }
                        .filter { it.isNotBlank() }
                        .forEach { members.add(it) }
                }
            }

            onGroupCreated(groupId, groupName, members)
            println("👥 Group created: $groupId name=$groupName members=$members")
        } catch (e: Exception) {
            println("DEBUG: Error parsing GroupCreated: ${e.stackTrace.joinToString("\n")}")
            onError("Failed to parse GroupCreated: ${e.message}")
        }
    }

    private fun handleGroupJoined(response: String) {
        try {
            val idPattern = "\"groupId\":\"(.*?)\"".toRegex()
            val userPattern = "\"userId\":\"(.*?)\"".toRegex()
            val groupId = idPattern.find(response)?.groupValues?.get(1) ?: return
            val userId = userPattern.find(response)?.groupValues?.get(1) ?: return
            onGroupJoined(groupId, userId)
            println("➕ Group joined: group=$groupId user=$userId")
        } catch (e: Exception) {
            onError("Failed to parse GroupJoined: ${e.message}")
        }
    }

    private fun handleGroupLeft(response: String) {
        try {
            val idPattern = "\"groupId\":\"(.*?)\"".toRegex()
            val userPattern = "\"userId\":\"(.*?)\"".toRegex()
            val groupId = idPattern.find(response)?.groupValues?.get(1) ?: return
            val userId = userPattern.find(response)?.groupValues?.get(1) ?: return
            onGroupLeft(groupId, userId)
            println("➖ Group left: group=$groupId user=$userId")
        } catch (e: Exception) {
            onError("Failed to parse GroupLeft: ${e.message}")
        }
    }
}
