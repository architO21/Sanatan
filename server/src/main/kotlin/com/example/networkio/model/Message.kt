package com.example.networkio.model

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val from: String,
    val content: String,
    val timestamp: Long,
    val sendTo: String,
    val visibleTo: List<String>? = null
)
