/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.sockets.internal

import com.monkopedia.ksrpc.CallDataSerializer
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.packets.internal.CONTENT_LENGTH
import com.monkopedia.ksrpc.packets.internal.Packet
import com.monkopedia.ksrpc.packets.internal.PacketChannelBase
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readFully
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.builtins.serializer

internal class ReadWritePacketChannel(
    scope: CoroutineScope,
    private val read: ByteReadChannel,
    private val write: ByteWriteChannel,
    env: KsrpcEnvironment<String>
) : PacketChannelBase<String>(scope, env) {

    override suspend fun sendLocked(packet: Packet<String>) {
        write.send(packet, env.serialization)
    }

    override suspend fun receiveLocked(): Packet<String> {
        return read.readPacket(env.serialization)
    }

    override suspend fun close() {
        super.close()
        write.close(Throwable())
        read.cancel(Throwable())
    }
}

private suspend fun ByteWriteChannel.send(
    packet: Packet<String>,
    serialization: CallDataSerializer<String>
) {
    val content =
        serialization.createCallData(Packet.serializer(String.serializer()), packet)
            .readSerialized()
            .encodeToByteArray(throwOnInvalidSequence = true)
    appendLine("$CONTENT_LENGTH: ${content.size}")
    appendLine()
    writeFully(content, 0, content.size)
    flush()
}

private suspend fun ByteReadChannel.readPacket(
    serialization: CallDataSerializer<String>
): Packet<String> {
    val params = readFields()
    val data = readContent(params) ?: return readPacket(serialization)
    val callData = CallData.create(data)
    return serialization.decodeCallData(Packet.serializer(String.serializer()), callData)
}

private suspend fun ByteReadChannel.readContent(
    params: Map<String, String>
): String? {
    val length = params[CONTENT_LENGTH]?.toIntOrNull() ?: return null
    val byteArray = ByteArray(length)
    readFully(byteArray)
    return byteArray.decodeToString()
}
