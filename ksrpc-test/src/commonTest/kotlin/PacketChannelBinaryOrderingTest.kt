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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.channels.ChannelId
import com.monkopedia.ksrpc.packets.internal.Packet
import com.monkopedia.ksrpc.packets.internal.PacketChannelBase
import io.ktor.utils.io.toByteArray
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer

private class QueuePacketChannel(scope: CoroutineScope, env: KsrpcEnvironment<String>) :
    PacketChannelBase<String>(scope, env) {
    val sentPackets = Channel<Packet<String>>(Channel.UNLIMITED)
    val incomingPackets = Channel<Packet<String>>(Channel.UNLIMITED)

    init {
        startReceiveLoop()
    }

    override suspend fun sendLocked(packet: Packet<String>) {
        sentPackets.send(packet)
    }

    override suspend fun receiveLocked(): Packet<String> = incomingPackets.receive()
}

class PacketChannelBinaryOrderingTest {
    @Test
    fun binaryResponseCanArriveOutOfOrderAndStillReassemble() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        val channel = QueuePacketChannel(this, env)
        try {
            val responseDeferred =
                async {
                    channel.call(
                        ChannelId("svc"),
                        "echo",
                        env.serialization.createCallData(String.serializer(), "request")
                    )
                }
            val request = channel.sentPackets.receive()
            val binaryChannelId = "binary-channel"
            channel.incomingPackets.send(
                Packet(
                    input = false,
                    binary = false,
                    startBinary = true,
                    id = "svc",
                    messageId = request.messageId,
                    endpoint = "echo",
                    data = env.serialization.createCallData(
                        String.serializer(),
                        binaryChannelId
                    ).readSerialized()
                )
            )

            fun binaryPacket(messageId: Int, payload: ByteArray): Packet<String> = Packet(
                input = false,
                binary = true,
                startBinary = false,
                id = binaryChannelId,
                messageId = messageId.toString(),
                endpoint = "echo",
                data = env.serialization.createCallData(
                    ByteArraySerializer(),
                    payload
                ).readSerialized()
            )

            channel.incomingPackets.send(binaryPacket(1, "second".encodeToByteArray()))
            channel.incomingPackets.send(binaryPacket(0, "first".encodeToByteArray()))
            channel.incomingPackets.send(binaryPacket(2, ByteArray(0)))

            val response = responseDeferred.await()
            val payload = response.readBinary().toByteArray()
            assertContentEquals("firstsecond".encodeToByteArray(), payload)
        } finally {
            runCatching { channel.close() }
            channel.incomingPackets.close()
            channel.sentPackets.close()
        }
    }
}
