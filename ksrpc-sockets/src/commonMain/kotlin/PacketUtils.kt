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
package com.monkopedia.ksrpc.sockets.internal

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.packets.internal.MAX_CONTENT_LENGTH
import com.monkopedia.ksrpc.packets.internal.MAX_HEADER_LINES
import com.monkopedia.ksrpc.packets.internal.MAX_HEADER_LINE_LENGTH
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8

@KsrpcInternal
inline fun swallow(function: () -> Unit) {
    try {
        function()
    } catch (t: Throwable) {
    }
}

@KsrpcInternal
suspend fun ByteWriteChannel.appendLine(s: String = "") {
    writeStringUtf8(s)
    writeStringUtf8("\r\n")
}

/**
 * Reads header lines up to the blank terminator.
 *
 * Both bounds come from the peer being untrusted: the line count is capped at
 * [MAX_HEADER_LINES] and each line at [MAX_HEADER_LINE_LENGTH], so a peer that omits
 * the terminator or never sends a newline cannot grow the map or the read forever.
 * Same reasoning as [MAX_CONTENT_LENGTH] on the content that follows.
 */
@KsrpcInternal
suspend fun ByteReadChannel.readFields(): Map<String, String> {
    val fields = LinkedHashMap<String, String>()
    var lines = 0
    while (true) {
        if (lines++ >= MAX_HEADER_LINES) {
            throw IOException("Refusing more than $MAX_HEADER_LINES header lines")
        }
        val line = readUTF8Line(MAX_HEADER_LINE_LENGTH)
            ?: throw IOException("$this is closed for reading")
        if (line.isEmpty()) {
            return fields
        }
        val separatorIndex = line.indexOf(':')
        if (separatorIndex < 0) {
            continue
        }
        val key = line.substring(0, separatorIndex).trim()
        val value = line.substring(separatorIndex + 1).trim()
        fields[key] = value
    }
}
