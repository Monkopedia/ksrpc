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
package com.monkopedia.ksrpc.sockets.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.packets.internal.CONTENT_LENGTH
import com.monkopedia.ksrpc.packets.internal.Packet
import com.monkopedia.ksrpc.packets.internal.PacketChannelBase
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.StringFormat
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

internal class ReadWritePacketChannel(
    scope: CoroutineScope,
    private val read: ByteReadChannel,
    private val write: ByteWriteChannel,
    env: KsrpcEnvironment
) : PacketChannelBase(scope, env) {
    private val sendLock = Mutex()
    private val receiveLock = Mutex()

    override suspend fun send(packet: Packet) {
        sendLock.lock()
        try {
            write.send(packet, env.serialization)
        } finally {
            sendLock.unlock()
        }
    }

    override suspend fun receive(): Packet {
        receiveLock.lock()
        try {
            return read.readPacket(env.serialization)
        } finally {
            receiveLock.unlock()
        }
    }

    override suspend fun close() {
        super.close()
        write.close(Throwable())
        read.cancel(Throwable())
    }
}

private suspend fun ByteWriteChannel.send(
    packet: Packet,
    serialization: StringFormat
) {
    val content =
        serialization.encodeToString(packet).encodeToByteArray(throwOnInvalidSequence = true)
    appendLine("$CONTENT_LENGTH: ${content.size}")
    appendLine()
    writeFully(content, 0, content.size)
    flush()
}

private suspend fun ByteReadChannel.readPacket(serialization: StringFormat): Packet {
    val params = readFields()
    val data = readContent(params) ?: return readPacket(serialization)
    return serialization.decodeFromString(data)
}

private suspend fun ByteReadChannel.readContent(
    params: Map<String, String>
): String? {
    val length = params[CONTENT_LENGTH]?.toIntOrNull() ?: return null
    val byteArray = ByteArray(length)
    readFully(byteArray)
    return byteArray.decodeToString()
}
