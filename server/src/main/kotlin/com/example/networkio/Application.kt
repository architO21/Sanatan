package com.example.networkio

import com.example.networkio.socket.ConnectionManager
import com.example.networkio.socket.socketRoutes
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

const val SERVER_PORT = 8080

fun main() {
    embeddedServer(Netty, port = SERVER_PORT, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Configure WebSocket
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 15.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }
    
    // Configure Content Negotiation
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
            allowTrailingComma = true
        })
    }
    
    // Create connection manager
    val connectionManager = ConnectionManager()
    
    routing {
        get("/") {
            call.respondText("Server is running! WebSocket available at ws://0.0.0.0:$SERVER_PORT/ws")
        }
        
        get("/health") {
            call.respondText("OK")
        }
        
        // Add WebSocket routes
        socketRoutes(connectionManager)
    }
    
    // Log server start
    environment.log.info("Server started on port $SERVER_PORT")
    environment.log.info("WebSocket endpoint: ws://0.0.0.0:$SERVER_PORT/ws")
}