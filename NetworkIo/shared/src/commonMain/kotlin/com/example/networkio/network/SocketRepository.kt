package com.example.networkio.network

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

class SocketRepository(
    private val client: HttpClient = HttpClient {
        install(WebSockets)
    }
) {
    private val socketClient = SocketClient(client)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    suspend fun connectToChat(serverUrl: String): Flow<String> {
        return socketClient.connect(serverUrl)
    }
    
    suspend fun sendMessage(message: Message) {
        socketClient.sendMessage(message)
    }
    
    suspend fun disconnect() {
        socketClient.disconnect()
    }
    
    fun isConnected(): Boolean = socketClient.isConnected()
}