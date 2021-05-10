/*
 * Copyright 2020 Jason Monk
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

import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.readBytes
import io.ktor.http.cio.websocket.readText
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val SIZE = 16 * 1024

fun ByteReadChannel.toFrameFlow(maxSize: Int = SIZE): Flow<Frame> = flow {
    while (!isClosedForRead) {
        val packet = readRemaining(maxSize.toLong())
        emit(Frame.Binary(isClosedForRead, packet.readBytes()))
    }
}

suspend fun ReceiveChannel<Frame>.toReadChannel(onClose: () -> Unit): ByteReadChannel {
    val scope = CoroutineScope(coroutineContext)
    return ByteChannel(autoFlush = true).also { channel ->
        val job = scope.launch {
            while (true) {
                when (val frame = receive()) {
                    is Frame.Binary -> {
                        val src = frame.readBytes()
                        if (!channel.isClosedForWrite) {
                            channel.writeFully(src, 0, src.size)
                        }
                        if (frame.fin) {
                            channel.close()
                            return@launch
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
                        throw IllegalStateException("Unexpected response $text")
                    }
                    is Frame.Close -> {
                        channel.close()
                        return@launch
                    }
                    is Frame.Ping,
                    is Frame.Pong,
                    -> {
                    }
                }
            }
        }
        job.invokeOnCompletion {
            onClose()
        }
        channel.attachJob(job)
    }
}
