package com.example.networkio.network

import kotlinx.serialization.Serializable

@Serializable
data class GroupAction(
    val action: String, // "create_group" | "add_member" | "remove_member"
    val groupId: String,
    val groupName: String? = null,
    val members: List<String> = emptyList(),
    val requesterId: String
)
