package com.example.networkio.network

import kotlinx.serialization.Serializable

@Serializable
sealed class ServerEvent {
    @Serializable
    data class Joined(val id: String) : ServerEvent()
    
    @Serializable
    data class Left(val id: String) : ServerEvent()
    
    @Serializable
    data class MessageReceived(val messageId: String, val timestamp: Long, val status: String = "delivered") : ServerEvent()
    
    @Serializable
    data class OnlineUsers(val users: List<String>) : ServerEvent()
}