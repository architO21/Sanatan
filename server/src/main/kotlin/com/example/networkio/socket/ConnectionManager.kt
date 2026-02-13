package com.example.networkio.socket

    import com.example.networkio.model.Message
    import com.example.networkio.model.ServerEvent
    import kotlinx.coroutines.sync.Mutex
    import kotlinx.coroutines.sync.withLock
    import kotlinx.serialization.json.Json

    class ConnectionManager(
        private val json:Json =Json{
            ignoreUnknownKeys=true
            encodeDefaults=true
            allowTrailingComma=true
        }
    ){
    private val sessions=mutableMapOf<String,ClientSession>()
    private val lock= Mutex()
    // Minimal in-memory group membership: groupId -> set of userIds
    private val groupMembers = mutableMapOf<String, MutableSet<String>>()

        suspend fun add(session: ClientSession){
            lock.withLock { sessions[session.id]=session }
            // Broadcast updated online users list to everyone
            val currentUsers = lock.withLock { sessions.keys.toList() }
            broadcastEvent(ServerEvent.OnlineUsers(currentUsers))
        }

        suspend fun remove(id:String) {
            lock.withLock { sessions.remove(id) }
            // Broadcast updated online users list to everyone
            val currentUsers = lock.withLock { sessions.keys.toList() }
            broadcastEvent(ServerEvent.OnlineUsers(currentUsers))
        }

        suspend fun broadcastEvent(event: ServerEvent) {
            val snapshot=lock.withLock { sessions.values.toList() }
            snapshot.forEach() { it.sendEvent(event) }
        }

        suspend fun broadcastMessage(message: Message){
            val snapshot=lock.withLock { sessions.values.toList() }
            snapshot.forEach { it.sendSerialized(message) }
        }

        suspend fun sendPrivateMessage(message: Message, recipientId: String) {
            val recipient = lock.withLock { sessions[recipientId] }
            val sender = lock.withLock { sessions[message.from] }
            
            // Send to recipient
            recipient?.sendSerialized(message)
        }

        // --- Group membership helpers (minimal) ---
        suspend fun addGroupMember(groupId: String, userId: String) {
            lock.withLock {
                val members = groupMembers.getOrPut(groupId) { mutableSetOf() }
                members.add(userId)
            }
        }

        suspend fun removeGroupMember(groupId: String, userId: String) {
            lock.withLock {
                groupMembers[groupId]?.remove(userId)
            }
        }

        suspend fun getGroupMemberIds(groupId: String): List<String> {
            return lock.withLock { groupMembers[groupId]?.toList() ?: emptyList() }
        }

        suspend fun getOnlineUsers(): List<String> {
            return lock.withLock { sessions.keys.toList() }
        }
        /**
         * Send a message to a group of recipients by their user/session IDs.
         * Minimal addition: relies on caller to provide the member IDs.
         * Does not echo back to the sender (keeps parity with sendPrivateMessage).
         */
        suspend fun sendGroupMessage(message: Message, recipientIds: Collection<String>) {
            // Snapshot target sessions without holding the lock during network I/O
            val targets = lock.withLock {
                recipientIds.mapNotNull { id -> sessions[id] }
            }
            targets.forEach { session ->
                session.sendSerialized(message)
            }
        }

        suspend fun broadcastGroupCreated(groupId: String, groupName: String, members: List<String>) {
            // Add members to group tracking
            members.forEach { userId ->
                addGroupMember(groupId, userId)
            }
            // Include members in the event so clients know who's in the group
            broadcastEvent(ServerEvent.GroupCreated(groupId, groupName, members))
        }

        suspend fun broadcastGroupJoined(groupId: String, userId: String) {
            addGroupMember(groupId, userId)
            broadcastEvent(ServerEvent.GroupJoined(groupId, userId))
        }

        suspend fun broadcastGroupLeft(groupId: String, userId: String) {
            removeGroupMember(groupId, userId)
            broadcastEvent(ServerEvent.GroupLeft(groupId, userId))
        }
    }