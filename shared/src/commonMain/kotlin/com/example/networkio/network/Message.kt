package com.example.networkio.network

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val from: String,
    val content: String,
    val timestamp: Long,
    val sendTo: String,
    val visibleTo: List<String>? = null  // null = visible to all, list = visible to specific members
)