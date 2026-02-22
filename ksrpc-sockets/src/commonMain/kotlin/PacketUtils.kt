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

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8

inline fun swallow(function: () -> Unit) {
    try {
        function()
    } catch (t: Throwable) {
    }
}

suspend fun ByteWriteChannel.appendLine(s: String = "") {
    writeStringUtf8(s)
    writeStringUtf8("\r\n")
}

suspend fun ByteReadChannel.readFields(): Map<String, String> {
    val fields = LinkedHashMap<String, String>()
    while (true) {
        val line = readUTF8Line() ?: throw IOException("$this is closed for reading")
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
