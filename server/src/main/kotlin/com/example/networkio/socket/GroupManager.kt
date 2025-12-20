package com.example.networkio.socket

import com.example.networkio.model.Group
import com.example.networkio.model.Message
import java.util.UUID

class GroupManager {
    val groups: MutableMap<String, Group> = mutableMapOf()
    val groupmembers: MutableMap<String, MutableSet<String>> = mutableMapOf()

    fun createGroup(groupownerId: String, name: String) {
        val groupId = UUID.randomUUID().toString()
        val newGroup = Group(
            id = groupId,
            name = name,
            description = "",
            imagurUrl = "",
            ownwerId = groupownerId
        )
        groups[groupId] = newGroup
        groupmembers[groupId] = mutableSetOf(groupownerId)
    }

    fun deleteGroup(groupId: String) {
        groups.remove(groupId)
        groupmembers.remove(groupId)
    }

    fun addMemberToGroup(groupId: String, userId: String) {
        groupmembers[groupId]?.add(userId)
    }

    fun removeMemberFromGroup(groupId: String, userId: String) {
        groupmembers[groupId]?.remove(userId)
    }

    suspend fun sendGroupMessage(groupId: String, message: Message, connectionManager: ConnectionManager) {
        val members = groupmembers[groupId] ?: return
        members.forEach { memberId ->
            connectionManager.sendPrivateMessage(message, memberId)
        }
    }
}