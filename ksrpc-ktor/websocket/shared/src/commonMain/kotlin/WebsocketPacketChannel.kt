/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.ktor.websocket.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.packets.internal.Packet
import com.monkopedia.ksrpc.packets.internal.PacketChannelBase
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.charsets.Charsets
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

@OptIn(InternalAPI::class)
class WebsocketPacketChannel(
    scope: CoroutineScope,
    private val socketSession: DefaultWebSocketSession,
    env: KsrpcEnvironment<String>
) : PacketChannelBase<String>(scope, env) {
    private val converter = KotlinxWebsocketSerializationConverter(Json)

    init {
        startReceiveLoop()
    }

    // Use socket max frame with some room for padding.
    // Divide by 2 to allow for manual base64-ing.
    override val maxSize: Long
        get() = socketSession.maxFrameSize / 2 - 1024

    @OptIn(InternalAPI::class)
    override suspend fun sendLocked(packet: Packet<String>) {
        val frame = converter.serialize(Charsets.UTF_8, packetTypeInfo, packet)
        socketSession.outgoing.send(frame)
    }

    override suspend fun receiveLocked(): Packet<String> {
        val frame = socketSession.incoming.receive()
        if (!converter.isApplicable(frame)) {
            throw IllegalStateException(
                "Converter doesn't support frame type ${frame.frameType.name}"
            )
        }
        val result = converter.deserialize(
            charset = Charsets.UTF_8,
            typeInfo = packetTypeInfo,
            content = frame
        )
        if (result is Packet<*>) {
            @Suppress("UNCHECKED_CAST")
            return result as Packet<String>
        }
        if (result == null) {
            throw IllegalStateException("Frame has null content")
        }
        throw IllegalStateException(
            "Can't deserialize value: expected value of type Packet, got ${result::class.simpleName}"
        )
    }

    override suspend fun close() {
        super.close()
        socketSession.close()
    }

    private companion object {
        val packetTypeInfo = typeInfo<Packet<String>>()
    }
}
