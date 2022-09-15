package io.opencola.relay.common

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive

class WebSocketSessionWrapper(private val webSocketSession : WebSocketSession) : SocketSession {
    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun isReady(): Boolean {
        return webSocketSession.isActive
                && !webSocketSession.incoming.isClosedForReceive
                && !webSocketSession.outgoing.isClosedForSend
    }

    override suspend fun readSizedByteArray(): ByteArray {
        return (webSocketSession.incoming.receive() as Frame.Binary).data
    }

    override suspend fun writeSizedByteArray(byteArray: ByteArray) {
        webSocketSession.send(Frame.Binary(true, byteArray))
    }

    override suspend fun close() {
        webSocketSession.close()
    }
}