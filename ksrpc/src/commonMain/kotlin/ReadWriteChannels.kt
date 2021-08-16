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
package com.monkopedia.ksrpc

import io.ktor.util.InternalAPI
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.completeWith
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.StringFormat
import kotlinx.serialization.json.Json

internal const val CONTENT_LENGTH = "Content-Length"
internal const val METHOD = "Method"
internal const val TYPE = "Type"

internal enum class SendType {
    NORMAL,
    BINARY,
    BINARY_INPUT
}

expect val DEFAULT_DISPATCHER: CoroutineDispatcher

@OptIn(InternalAPI::class)
suspend fun SerializedChannel.serve(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    asyncDispatcher: CoroutineDispatcher = DEFAULT_DISPATCHER,
    errorListener: ErrorListener = ErrorListener { }
) {
    val channel = this
    val lock = Mutex()

    while (true) {
        try {
            coroutineScope {
                flow {
                    while (!input.isClosedForRead) {
                        lock.lock()
                        try {
                            val params = input.readFields()
                            val method = params[METHOD] ?: error("Missing method")
                            val input = input.readContent(params)
                            lock.unlock()
                            emit(
                                async(asyncDispatcher) {
                                    channel.call(method, input)
                                }
                            )
                        } catch (t: Throwable) {
                            lock.unlock()
                            throw t
                        }
                    }
                }.buffer(10).flowOn(asyncDispatcher).map {
                    val v: Any? = it.await()
                    (v as? CallData)
                        ?: error("Wrong type $v")
                }.buffer().onEach { packet ->
                    output.writeContent(packet)
                }.collect()
                input.cancel()
                output.close(null)
            }
        } catch (t: CancellationException) {
            input.cancel()
            output.close(null)
            return
        } catch (t: Throwable) {
            errorListener.onError(t)
            input.cancel()
            output.close(null)
            return
        }
    }
}

private data class Message(
    val type: SendType,
    val endpoint: String,
    val data: CallData,
)

@OptIn(InternalAPI::class)
fun Pair<ByteReadChannel, ByteWriteChannel>.asChannel(
    asyncDispatcher: CoroutineDispatcher = DEFAULT_DISPATCHER
): SerializedChannel {
    val reader = first
    val writer = second
    val lock = Mutex()
    val calls = Channel<Message>(5)
    val responses = Channel<CompletableDeferred<CallData>>(5)
    GlobalScope.launch(asyncDispatcher) {
        try {
            while (true) {
                val callback = responses.receive()
                callback.completeWith(
                    Result.runCatching {
                        var params = reader.readFields()
                        if (params.isEmpty()) {
                            params = reader.readFields()
                        }
                        reader.readContent(params)
                    }
                )
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            responses.close(t)
        }
    }
    GlobalScope.launch(asyncDispatcher) {
        try {
            while (true) {
                val (type, str, input) = calls.receive()
                writer.appendLine("$METHOD: $str")
                writer.appendLine("$TYPE: ${type.name}")
                writer.writeContent(input)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            calls.close(t)
        }
    }

    return object : SerializedChannel {
        override val serialization: StringFormat
            get() = Json

        override suspend fun call(endpoint: String, input: CallData): CallData {
            val response = CompletableDeferred<CallData>()
            lock.withLock {
                calls.send(Message(SendType.NORMAL, endpoint, input))
                responses.send(response)
            }
            return response.await()
        }

        override suspend fun close() {
            reader.cancel()
            writer.close(null)
        }
    }
}

private suspend fun ByteWriteChannel.appendLine(s: String = "") = writeStringUtf8("$s\n")
private suspend fun ByteWriteChannel.append(content: String) = writeStringUtf8(content)

@OptIn(InternalAPI::class)
private suspend fun ByteWriteChannel.writeContent(
    content: CallData
) {
    if (content.isBinary) {
        writeContent(content.readBinary().readRemaining().encodeBase64(), isBinary = true)
    } else {
        writeContent(content.readSerialized())
    }
}

private suspend fun ByteWriteChannel.writeContent(
    content: String,
    isBinary: Boolean = false
) {
    appendLine("$CONTENT_LENGTH: ${content.length}")
    appendLine("$TYPE: ${if (isBinary) SendType.BINARY.name else SendType.NORMAL.name}")
    appendLine()
    append(content)
    flush()
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

fun parseParams(fields: List<String>): Map<String, String> {
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
