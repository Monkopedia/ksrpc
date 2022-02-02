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

import com.monkopedia.ksrpc.CHANNEL
import com.monkopedia.ksrpc.CONTENT_LENGTH
import com.monkopedia.ksrpc.CallData
import com.monkopedia.ksrpc.INPUT
import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.METHOD
import com.monkopedia.ksrpc.SendType
import com.monkopedia.ksrpc.TYPE
import io.ktor.util.InternalAPI
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext

internal class ReadWritePacketChannel(
    scope: CoroutineScope,
    context: CoroutineContext,
    private val read: ByteReadChannel,
    private val write: ByteWriteChannel,
    env: KsrpcEnvironment
) : PacketChannelBase(scope, context, env) {
    private val sendLock = Mutex()
    private val receiveLock = Mutex()

    override suspend fun send(packet: Packet) {
        sendLock.withLock {
            write.send(packet)
        }
    }

    override suspend fun receive(): Packet {
        receiveLock.withLock {
            return read.readPacket()
        }
    }

    override suspend fun close() {
        super.close()
        write.close(Throwable())
        read.cancel(Throwable())
    }
}

private suspend fun ByteWriteChannel.appendLine(s: String = "") = writeStringUtf8("$s\n")
private suspend fun ByteWriteChannel.append(content: String) = writeStringUtf8(content)

@OptIn(InternalAPI::class)
private suspend fun ByteWriteChannel.send(
    packet: Packet
) {
    val data = packet.data
    val content = if (data.isBinary) {
        data.readBinary().readRemaining().encodeBase64()
    } else {
        data.readSerialized()
    }
    appendLine("$METHOD: ${packet.endpoint}")
    appendLine("$INPUT: ${packet.input}")
    appendLine("$CHANNEL: ${packet.id}")
    appendLine("$CONTENT_LENGTH: ${content.length}")
    appendLine("$TYPE: ${if (data.isBinary) SendType.BINARY.name else SendType.NORMAL.name}")
    appendLine()
    append(content)
    flush()
}

private suspend fun ByteReadChannel.readPacket(): Packet {
    val params = readFields()
    val input = params[INPUT]?.toBoolean() ?: true
    val channel = params[CHANNEL] ?: ""
    val endpoint = params[METHOD] ?: return readPacket()
    val data = readContent(params)
    return Packet(input, channel, endpoint, data)
}

@OptIn(InternalAPI::class)
private suspend fun ByteReadChannel.readContent(
    params: Map<String, String>
): CallData {
    var length = params[CONTENT_LENGTH]?.toIntOrNull() ?: error("Missing content length in $params")
    val type = enumValueOf<SendType>(params[TYPE] ?: SendType.NORMAL.name)
    var byteArray = ByteArray(length)
    readFully(byteArray)
    return when (type) {
        SendType.NORMAL -> CallData.create(byteArray.decodeToString())
        SendType.BINARY,
        SendType.BINARY_INPUT ->
            CallData.create(ByteReadChannel(byteArray.decodeToString().decodeBase64Bytes()))
    }
}

private suspend fun ByteReadChannel.readFields(): Map<String, String> {
    val fields = mutableListOf<String>()
    var line = readUTF8Line()
    while (line == null || line.isNotEmpty()) {
        if (line != null) {
            fields.add(line)
        }
        line = readUTF8Line()
    }
    return parseParams(fields)
}

private fun parseParams(fields: List<String>): Map<String, String> {
    return fields.filter { it.contains(":") }.map {
        val (first, second) = it.splitSingle(':')
        return@map first.trim() to second.trim()
    }.toMap()
}

private fun String.splitSingle(s: Char): Pair<String, String> {
    val index = indexOf(s)
    if (index < 0) throw IllegalArgumentException("Can't find param")
    return substring(0, index) to substring(index + 1, length)
}
