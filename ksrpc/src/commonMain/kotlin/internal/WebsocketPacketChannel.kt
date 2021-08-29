package com.monkopedia.ksrpc.internal

import io.ktor.http.cio.websocket.DefaultWebSocketSession
import io.ktor.http.cio.websocket.close
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

internal class WebsocketPacketChannel(private val client: DefaultWebSocketSession) : PacketChannelBase(Json) {
    private val sendLock = Mutex()
    private val receiveLock = Mutex()

    override suspend fun send(packet: Packet) {
        sendLock.withLock {
            client.send(packet)
        }
    }

    override suspend fun receiveImpl(): Packet {
        receiveLock.lock(null)
        return client.receivePacket {
            receiveLock.unlock(null)
        }
    }

    override suspend fun close() {
        super.close()
        client.close()
    }
}