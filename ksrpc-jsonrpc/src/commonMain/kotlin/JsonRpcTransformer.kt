/**
 * Copyright (C) 2025 Jason Monk <monkopedia@gmail.com>
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
package com.monkopedia.ksrpc.jsonrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.packets.internal.CONTENT_LENGTH
import com.monkopedia.ksrpc.packets.internal.CONTENT_TYPE
import com.monkopedia.ksrpc.sockets.internal.appendLine
import com.monkopedia.ksrpc.sockets.internal.readFields
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

const val DEFAULT_CONTENT_TYPE = "application/vscode-jsonrpc; charset=utf-8"

abstract class JsonRpcTransformer {
    abstract val isOpen: Boolean

    abstract suspend fun send(message: JsonElement)
    abstract suspend fun receive(): JsonElement?
    abstract fun close(cause: Throwable?)
}

fun Pair<ByteReadChannel, ByteWriteChannel>.jsonHeader(
    env: KsrpcEnvironment<String>
): JsonRpcTransformer = JsonRpcHeader(env, first, second)

internal class JsonRpcHeader(
    env: KsrpcEnvironment<String>,
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel
) : JsonRpcTransformer() {
    private val json = (env.serialization as? Json) ?: Json
    private val sendLock = Mutex()
    private val receiveLock = Mutex()
    private val serializer = JsonElement.serializer()

    override val isOpen: Boolean
        get() = !input.isClosedForRead

    override suspend fun send(message: JsonElement) {
        sendLock.withLock {
            val content = json.encodeToString(serializer, message)
            val contentBytes = content.encodeToByteArray()
            output.appendLine("$CONTENT_LENGTH: ${contentBytes.size}")
            output.appendLine("$CONTENT_TYPE: $DEFAULT_CONTENT_TYPE")
            output.appendLine()
            output.writeFully(contentBytes, 0, contentBytes.size)
            output.flush()
        }
    }

    override suspend fun receive(): JsonElement? {
        receiveLock.withLock {
            val params = input.readFields()
            val length = params[CONTENT_LENGTH]?.toIntOrNull() ?: return null
            var byteArray = ByteArray(length)
            input.readFully(byteArray)
            return json.decodeFromString(serializer, byteArray.decodeToString())
        }
    }

    override fun close(cause: Throwable?) {
        output.close(cause)
    }
}

fun Pair<ByteReadChannel, ByteWriteChannel>.jsonLine(
    env: KsrpcEnvironment<String>
): JsonRpcTransformer = JsonRpcLine(env, first, second)

internal class JsonRpcLine(
    env: KsrpcEnvironment<String>,
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel
) : JsonRpcTransformer() {
    private val json = (env.serialization as? Json) ?: Json
    private val sendLock = Mutex()
    private val receiveLock = Mutex()
    private val serializer = JsonElement.serializer()

    override val isOpen: Boolean
        get() = !input.isClosedForRead

    override suspend fun send(message: JsonElement) {
        sendLock.withLock {
            val content = json.encodeToString(serializer, message)
            require('\n' !in content) {
                "Cannot have new-lines in encoding check environment json config"
            }
            output.appendLine(content)
            output.flush()
        }
    }

    override suspend fun receive(): JsonElement? {
        receiveLock.withLock {
            val line = input.readUTF8Line() ?: return null
            return json.decodeFromString(serializer, line)
        }
    }

    override fun close(cause: Throwable?) {
        output.close(cause)
    }
}
