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
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.charsets.Charsets
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.serialization.receiveDeserializedBase
import io.ktor.websocket.serialization.sendSerializedBase
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json

@OptIn(InternalAPI::class)
class WebsocketPacketChannel(
    scope: CoroutineScope,
    private val socketSession: DefaultWebSocketSession,
    env: KsrpcEnvironment<String>
) : PacketChannelBase<String>(scope, env) {
    private val converter = KotlinxWebsocketSerializationConverter(Json)

    // Use socket max frame with some room for padding.
    // Divide by 2 to allow for manual base64-ing.
    override val maxSize: Long
        get() = socketSession.maxFrameSize / 2 - 1024

    @OptIn(InternalAPI::class)
    override suspend fun sendLocked(packet: Packet<String>) {
        socketSession.sendSerializedBase<Packet<String>>(packet, converter, Charsets.UTF_8)
    }

    override suspend fun receiveLocked(): Packet<String> {
        @Suppress("UNCHECKED_CAST")
        return socketSession.receiveDeserializedBase<Packet<String>>(
            converter,
            Charsets.UTF_8
        ) as Packet<String>
    }

    override suspend fun close() {
        super.close()
        socketSession.close()
    }
}
