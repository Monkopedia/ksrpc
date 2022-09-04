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
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.utils.io.charsets.Charsets
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.serialization.receiveDeserializedBase
import io.ktor.websocket.serialization.sendSerializedBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex

internal class WebsocketPacketChannel(
    scope: CoroutineScope,
    private val socketSession: DefaultWebSocketSession,
    env: KsrpcEnvironment
) : PacketChannelBase(scope, env) {
    private val sendLock = Mutex()
    private val receiveLock = Mutex()
    private val converter = KotlinxWebsocketSerializationConverter(env.serialization)

    // Use socket max frame with some room for padding.
    // Divide by 2 to allow for manual base64-ing.
    override val maxSize: Long
        get() = socketSession.maxFrameSize / 2 - 1024

    override suspend fun send(packet: Packet) {
        sendLock.lock()
        try {
            socketSession.sendSerializedBase(packet, converter, Charsets.UTF_8)
        } finally {
            sendLock.unlock()
        }
    }

    override suspend fun receive(): Packet {
        receiveLock.lock()
        try {
            return socketSession.receiveDeserializedBase(converter, Charsets.UTF_8)
        } finally {
            receiveLock.unlock()
        }
    }

    override suspend fun close() {
        super.close()
        socketSession.close()
    }
}
