package com.example.networkio.model

import kotlinx.serialization.Serializable

@Serializable
sealed class ServerEvent{
    @Serializable
    data class Joined(val id:String):ServerEvent()
    @Serializable
    data class Left(val id:String):ServerEvent()
    @Serializable
    data class Broadcast(val message:Message):ServerEvent()
    @Serializable
    data class MessageReceived(val messageId: String, val timestamp: Long, val status: String = "delivered"):ServerEvent()
    @Serializable
    data class OnlineUsers(val users: List<String>):ServerEvent()
    @Serializable
    data class GroupCreated(val groupId: String, val groupName: String, val members: List<String> = emptyList()): ServerEvent()
    @Serializable
    data class GroupJoined(val groupId: String, val userId: String): ServerEvent()
    @Serializable
    data class GroupLeft(val groupId: String, val userId: String): ServerEvent()
    @Serializable
    data class GroupUpdated(val groupId: String, val groupName: String): ServerEvent()  
}