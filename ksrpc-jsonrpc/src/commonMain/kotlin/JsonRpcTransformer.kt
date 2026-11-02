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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc.jsonrpc.internal

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.packets.internal.CONTENT_LENGTH
import com.monkopedia.ksrpc.packets.internal.CONTENT_TYPE
import com.monkopedia.ksrpc.packets.internal.MAX_CONTENT_LENGTH
import com.monkopedia.ksrpc.packets.internal.MAX_HEADER_LINES
import com.monkopedia.ksrpc.packets.internal.MAX_HEADER_LINE_LENGTH
import com.monkopedia.ksrpc.sockets.internal.appendLine
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.readFully
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

@KsrpcInternal
const val DEFAULT_CONTENT_TYPE = "application/vscode-jsonrpc; charset=utf-8"

@KsrpcInternal
abstract class JsonRpcTransformer {
    abstract val isOpen: Boolean

    abstract suspend fun send(message: JsonElement)
    abstract suspend fun receive(): JsonElement?
    abstract fun close(cause: Throwable?)
}

@KsrpcInternal
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
            val length = readContentLength() ?: return null
            if (length < 0 || length > MAX_CONTENT_LENGTH) {
                throw IOException("Refusing $CONTENT_LENGTH of $length (limit $MAX_CONTENT_LENGTH)")
            }
            var byteArray = ByteArray(length)
            input.readFully(byteArray)
            return json.decodeFromString(serializer, byteArray.decodeToString())
        }
    }

    private suspend fun readContentLength(): Int? {
        var contentLength: Int? = null
        var lines = 0
        while (true) {
            // Bounded on lines read, not on headers kept: a malformed line hits the
            // `continue` below and stores nothing, so a cap on what is retained would
            // never close this loop. Same shape, and the same constants, as
            // `readFields` in the packet transport (#263/#270).
            if (lines++ >= MAX_HEADER_LINES) {
                throw IOException("Refusing more than $MAX_HEADER_LINES header lines")
            }
            // A null line is end of stream, which `receive` reports as a clean close;
            // only an over-long line is an error, and readUTF8Line throws for that.
            val line = input.readUTF8Line(MAX_HEADER_LINE_LENGTH) ?: return null
            if (line.isEmpty()) {
                return contentLength
            }
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val headerName = line.substring(0, separator).trim()
            if (headerName.equals(CONTENT_LENGTH, ignoreCase = true)) {
                val headerValue = line.substring(separator + 1).trim()
                // Refused rather than skipped, for the same reason as the socket
                // transport: the reader loop in JsonRpcWriterBase does
                // `comm.receive() ?: continue`, so a null length resumes header
                // parsing with the frame body still queued — the next
                // well-formed frame is swallowed and the connection then hangs.
                // toIntOrNull() also returns null above Int.MAX_VALUE, so this
                // is the path `Content-Length: 99999999999` takes.
                contentLength = headerValue.toIntOrNull()
                    ?: throw IOException("Unparseable $CONTENT_LENGTH: '$headerValue'")
            }
        }
    }

    override fun close(cause: Throwable?) {
        output.close(cause)
    }
}

@KsrpcInternal
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
            // Here the line *is* the message, so it is bounded by what the other transport
            // allows a message to be, not by the much smaller header-line limit: a peer that
            // never sends a newline is otherwise bounded only by heap (#284).
            //
            // The bound holds — a peer cannot make this read without end — but below the
            // refusal point there is a band where a message is altered rather than refused.
            // Refusal tracks the length the decoder *emits*, the input characters plus the
            // replacements it inserted, rather than the input's own length; so text that
            // decodes to more than the limit is refused, and text that decodes to less is
            // returned however damaged it is.
            //
            // Every encoding wider than one byte has such a band — only single-byte text is
            // clean right up to the limit and refused one past it. Where the band begins is
            // not simply "the byte count passed the limit": measured at limit 1000,
            // three-byte text is damaged from 1002 bytes, while two-byte text is still clean
            // at 1874 and four-byte at 1748.
            //
            // How wide each band is depends on the width, and only three-byte's scales.
            // Measured at limits 1000, 2000 and 4000, the two-byte band is 112 bytes at every
            // limit and the four-byte band 236, because for those widths both ends track
            // twice the limit and so move together. Three-byte's ends track the limit and
            // three times it, so its band is about twice the limit. At MAX_CONTENT_LENGTH
            // that puts three-byte text in the band from 64 MiB to 192 MiB, while the even
            // widths stay a hundred-odd bytes wide. The damage is also not one character:
            // replacements recur as the reader refills, so the count grows with length.
            //
            // That damaged text still decodes. The replacements land inside a JSON string
            // literal, so the envelope survives and a handler is called with a silently
            // altered argument — measured, and pinned by JsonRpcLineBoundJvmTest. Reaching
            // the band needs one JSON-RPC line above 64 MiB, which is why this is treated as
            // hardening rather than a live defect. That is not a claim that no peer can
            // produce one: this transport does no chunking of its own — `send` writes the
            // whole message in a single appendLine — so nothing here bounds what a peer may
            // put on one line, and nobody has constructed the case either way.
            //
            // It is also not the ceiling the header-framed path enforces: that one compares
            // MAX_CONTENT_LENGTH against a byte count the peer declares, before reading. Same
            // constant, different question asked of it.
            val line = input.readUTF8Line(MAX_CONTENT_LENGTH) ?: return null
            return json.decodeFromString(serializer, line)
        }
    }

    override fun close(cause: Throwable?) {
        output.close(cause)
    }
}
