    package com.example.networkio.socket

    import com.example.networkio.model.Message
    import com.example.networkio.model.ServerEvent
    import io.ktor.websocket.DefaultWebSocketSession
    import io.ktor.websocket.Frame
    import kotlinx.coroutines.channels.ClosedSendChannelException
    import kotlinx.serialization.json.Json

    class ClientSession (
        val id:String,
        private val session:DefaultWebSocketSession,
        private val json:Json
    ){
        suspend fun sendText(text:String){
            try{
                session.send(Frame.Text(text))
            }
            catch (e:ClosedSendChannelException){
                println(e)
            }
        }
        suspend fun sendSerialized(message: Message){
                val text=json.encodeToString(Message.serializer(), message)
            sendText(text)
        }
        suspend fun sendEvent(event:ServerEvent){
            val text=json.encodeToString(ServerEvent.serializer(), event)
            sendText(text)
        }
    }