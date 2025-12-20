package com.example.networkio.network

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val from: String,
    val content: String,
    val timestamp: Long,
    val sendTo: String = "all",
)