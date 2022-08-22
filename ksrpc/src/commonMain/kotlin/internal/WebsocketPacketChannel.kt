/*
 * Copyright 2021 Jason Monk
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.ksrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.Frame.Text
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal class WebsocketPacketChannel(
    scope: CoroutineScope,
    private val socketSession: DefaultWebSocketSession,
    env: KsrpcEnvironment
) : PacketChannelBase(scope, env) {
    private val sendLock = Mutex()
    private val receiveLock = Mutex()

    // Use socket max frame with some room for padding.
    // Divide by 2 to allow for manual base64-ing.
    override val maxSize: Long
        get() = socketSession.maxFrameSize / 2 - 1024

    override suspend fun send(packet: Packet) {
        sendLock.withLock {
            val serialized = env.serialization.encodeToString(packet)
            socketSession.send(Text(serialized))
        }
    }

    override suspend fun receive(): Packet {
        receiveLock.lock()
        try {
            val packetText = socketSession.incoming.receive()
            return env.serialization.decodeFromString(packetText.expectText())
        } finally {
            receiveLock.unlock()
        }
    }

    override suspend fun close() {
        super.close()
        socketSession.close()
    }
}

private fun Frame.expectText(): String {
    if (this is Text) {
        return readText()
    } else {
        throw IllegalStateException("Unexpected frame $this")
    }
}
