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

import com.monkopedia.ksrpc.CallData
import com.monkopedia.ksrpc.ERROR_PREFIX
import com.monkopedia.ksrpc.RpcFailure
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.WebSocketSession
import io.ktor.http.cio.websocket.readBytes
import io.ktor.http.cio.websocket.readText
import io.ktor.http.cio.websocket.send
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val SIZE = 16 * 1024

internal fun ByteReadChannel.toFrameFlow(maxSize: Int = SIZE): Flow<Frame> = flow {
    while (!isClosedForRead) {
        val packet = readRemaining(maxSize.toLong())
        emit(Frame.Binary(isClosedForRead, packet.readBytes()))
    }
}

internal suspend fun ReceiveChannel<Frame>.toReadChannel(
    first: Frame? = null,
    onClose: () -> Unit
): ByteReadChannel {
    val scope = CoroutineScope(coroutineContext)
    return ByteChannel(autoFlush = true).also { channel ->
        val job = scope.launch {
            if (first != null) {
                if (handleFrame(first, channel)) return@launch
            }
            while (true) {
                val frame = receive()
                if (handleFrame(frame, channel)) return@launch
            }
        }
        job.invokeOnCompletion {
            onClose()
        }
        channel.attachJob(job)
    }
}

private suspend fun handleFrame(
    frame: Frame,
    channel: ByteChannel
): Boolean {
    when (frame) {
        is Frame.Binary -> {
            val src = frame.readBytes()
            if (!channel.isClosedForWrite) {
                channel.writeFully(src, 0, src.size)
            }
            if (frame.fin) {
                channel.close()
                return true
            }
        }
        is Frame.Text -> {
            val text = frame.readText()
            if (text.startsWith(ERROR_PREFIX)) {
                throw Json.decodeFromString(
                    RpcFailure.serializer(),
                    text.substring(ERROR_PREFIX.length)
                ).toException()
            }
            throw IllegalStateException("Unexpected response $frame")
        }
        is Frame.Close -> {
            channel.close()
            return true
        }
        is Frame.Ping,
        is Frame.Pong -> {
        }
    }
    return false
}

internal suspend fun WebSocketSession.send(packet: Packet) {
    val input = packet.data
    send(packet.input.toString())
    send(packet.endpoint)
    if (input.isBinary) {
        input.readBinary().toFrameFlow().collect {
            outgoing.send(it)
        }
    } else {
        outgoing.send(Frame.Text(input.readSerialized()))
    }
}

internal suspend fun WebSocketSession.receivePacket(onClose: () -> Unit): Packet {
    return incoming.receivePacket(onClose)
}

internal suspend fun ReceiveChannel<Frame>.receivePacket(onClose: () -> Unit): Packet {
    val inputPacket = receive().expectText().toBoolean()
    val endpoint = receive().expectText()
    val input = receive()
    return Packet(
        inputPacket, endpoint,
        if (input is Frame.Text) {
            CallData.create(input.expectText()).also { onClose() }
        } else {
            CallData.create(toReadChannel(input, onClose))
        }
    )
}

internal fun Frame.expectText(): String {
    if (this is Frame.Text) {
        return readText()
    } else {
        throw IllegalStateException("Unexpected frame $this")
    }
}
