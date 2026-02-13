package com.example.networkio.socket

    import com.example.networkio.model.Message
    import com.example.networkio.model.GroupAction
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
                        val text = frame.readText()
                        println("📨 Received frame: $text")
                        
                        // Try to parse as GroupAction first
                        try {
                            val groupAction = json.decodeFromString<GroupAction>(text)
                            println("📋 GroupAction received: action=${groupAction.action}, groupId=${groupAction.groupId}")
                            
                            when (groupAction.action) {
                                "create_group" -> {
                                    // Broadcast group created event to all members including the creator
                                    val allMembers = (groupAction.members + groupAction.requesterId).distinct()
                                    manager.broadcastGroupCreated(
                                        groupId = groupAction.groupId,
                                        groupName = groupAction.groupName ?: groupAction.groupId,
                                        members = allMembers
                                    )
                                    println("✅ Group created and broadcasted: ${groupAction.groupName} with members: $allMembers")
                                }
                                "join_group" -> {
                                    manager.addGroupMember(groupAction.groupId, clientId)
                                    manager.broadcastGroupJoined(groupAction.groupId, clientId)
                                    println("➕ User $clientId joined group ${groupAction.groupId}")
                                }
                                "leave_group" -> {
                                    manager.removeGroupMember(groupAction.groupId, clientId)
                                    manager.broadcastGroupLeft(groupAction.groupId, clientId)
                                    println("➖ User $clientId left group ${groupAction.groupId}")
                                }
                            }
                        } catch (e: Exception) {
                            // Not a GroupAction, try parsing as Message
                            try {
                                val message = json.decodeFromString<Message>(text)
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
                                    
                                    // Filter recipients by visibleTo list if specified
                                    val recipients = if (message.visibleTo != null && message.visibleTo.isNotEmpty()) {
                                        // Always include sender so they can see their own message
                                        (members.filter { memberId -> message.visibleTo.contains(memberId) } + updatedMessage.from)
                                            .distinct()
                                    } else {
                                        members
                                    }
                                    
                                    manager.sendGroupMessage(updatedMessage, recipients)
                                    println("👥 Group message from ${updatedMessage.from} to group:$groupId (visibleTo=${message.visibleTo}, recipients=${recipients.size})")
                                } else if (message.sendTo.isNotBlank() && message.sendTo != "all") {
                                    manager.sendPrivateMessage(updatedMessage, message.sendTo)
                                    println("📨 Private message from ${updatedMessage.from} to ${message.sendTo}")
                                } else if (message.sendTo == "all") {
                                    manager.broadcastMessage(updatedMessage)
                                    println("📢 Broadcast message from ${updatedMessage.from}")
                                }
                            } catch (e2: Exception) {
                                println("❌ Failed to parse frame: $text")
                                println("❌ Error: ${e2.message}")
                                e2.printStackTrace()
                            }
                        }
                    }
                }
            } finally {
                manager.remove(clientId)
            }
        }
    }