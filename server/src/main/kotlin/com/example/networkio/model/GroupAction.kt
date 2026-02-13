package com.example.networkio.model

import kotlinx.serialization.Serializable

@Serializable
data class GroupAction(
    val action: String, 
    val groupId: String,
    val groupName: String? = null,
    val members: List<String> = emptyList(),
    val requesterId: String
)
