package com.example.networkio.network

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class SocketClient(
    private val client: HttpClient
) {
    private var session: DefaultClientWebSocketSession? = null
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        allowTrailingComma = true
    }
    
    suspend fun connect(url: String): Flow<String> = flow {
        session = client.webSocketSession(url)
        session?.incoming?.receiveAsFlow()?.collect { frame ->
            if (frame is Frame.Text) {
                emit(frame.readText())
            }
        }
    }
    
    suspend fun sendMessage(message: Message) {
        val jsonMessage = json.encodeToString(message)
        session?.send(Frame.Text(jsonMessage))
    }
    
    suspend fun disconnect() {
        session?.close()
        session = null
    }
    
    fun isConnected(): Boolean = session != null
}