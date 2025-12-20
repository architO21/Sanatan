    package com.example.networkio.socket

    import com.example.networkio.model.Message
    import io.ktor.server.routing.*
    import io.ktor.server.websocket.*
    import io.ktor.websocket.*
    import kotlinx.coroutines.channels.consumeEach
    import kotlinx.serialization.json.Json
    import java.util.UUID

    fun Route.socketRoutes(manager: ConnectionManager) {
        webSocket("/ws") {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            val clientId = call.request.queryParameters["id"] ?: UUID.randomUUID().toString()
            val client = ClientSession(clientId, this, json)
            manager.add(client)
            val onlineUsers = manager.getOnlineUsers()
            client.sendEvent(com.example.networkio.model.ServerEvent.OnlineUsers(onlineUsers))
            try {
                incoming.consumeEach { frame ->
                    if (frame is Frame.Text) {
                        val message = json.decodeFromString<Message>(frame.readText())
                        client.sendEvent(
                            com.example.networkio.model.ServerEvent.MessageReceived(
                                messageId = message.from,
                                timestamp = System.currentTimeMillis(),
                                status = "received"
                            )
                        )
                        val updatedMessage = message.copy(
                            from = message.from.ifBlank { clientId },
                            timestamp = System.currentTimeMillis()
                        )
                        
                        // Route based on sendTo: private, group, or broadcast
                        if (message.sendTo.startsWith("group:")) {
                            val groupId = message.sendTo.removePrefix("group:")
                            val members = manager.getGroupMemberIds(groupId)
                            manager.sendGroupMessage(updatedMessage, members)
                            println("👥 Group message from ${updatedMessage.from} to group:$groupId (members=${members.size})")
                        } else if (message.sendTo.isNotBlank() && message.sendTo != "all") {
                            manager.sendPrivateMessage(updatedMessage, message.sendTo)
                            println("📨 Private message from ${updatedMessage.from} to ${message.sendTo}")
                        } else if (message.sendTo == "all") {
                            manager.broadcastMessage(updatedMessage)
                            println("📢 Broadcast message from ${updatedMessage.from}")
                        }
                    }
                }
            } finally {
                manager.remove(clientId)
            }
        }
    }