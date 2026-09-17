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

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.jsonrpc.internal.jsonLine
import com.monkopedia.ksrpc.packets.internal.MAX_CONTENT_LENGTH
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.writeStringUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * The newline-delimited transport reads a whole message as one line, so without a limit a peer
 * that never sends a newline is bounded only by heap (#284).
 *
 * These live in `jvmTest` rather than `commonTest` on purpose. Exceeding the bound means
 * feeding more than [MAX_CONTENT_LENGTH], which is affordable on a JVM worker and is not
 * something to ask of the JS and Wasm suites.
 */
class JsonRpcLineBoundJvmTest {

    @Test
    fun testLineTransportRefusesAnOverlongLine() = runBlockingUnit {
        val input = ByteChannel(autoFlush = true)
        val transformer = (input to ByteChannel(autoFlush = true)).jsonLine(ksrpcEnvironment { })
        // No newline anywhere: without the bound this accumulates until the heap runs out,
        // and the receive never returns.
        val feeder = launch {
            val chunk = "v".repeat(1 shl 16)
            repeat(MAX_CONTENT_LENGTH / chunk.length + 1) { input.writeStringUtf8(chunk) }
        }
        // withTimeout so an unguarded build fails here rather than hanging.
        assertFailsWith<IOException> { withTimeout(120_000) { transformer.receive() } }
        feeder.cancel()
    }

    /**
     * What the limit actually does, measured rather than assumed — and it is not what
     * "bounded" suggests.
     *
     * Past the limit, ASCII raises `TooLongLineException`. The same content in multi-byte
     * UTF-8 does **not**: the read stops near the limit and returns a string containing
     * `U+FFFD` replacement characters where a character was split across the boundary. So the
     * limit bounds the memory this will hold, which is what #284 is about, but it does not
     * mean an oversized non-ASCII line is refused — it is mangled and handed on.
     *
     * Pinned here so a Ktor upgrade that changes it fails loudly rather than quietly, since
     * `readUTF8Line` is deprecated in 3.5.1 and the obvious replacement takes no limit at all.
     */
    @Test
    fun testTheLimitRefusesAsciiButCorruptsMultiByteText() = runBlockingUnit {
        suspend fun readBounded(text: String, limit: Int) = runCatching {
            val channel = ByteChannel(autoFlush = true)
            launch {
                channel.writeStringUtf8(text)
                channel.writeStringUtf8("\n")
            }
            withTimeout(5000) { channel.readUTF8Line(limit) }
        }

        // ASCII over the limit is refused outright.
        assertTrue(
            readBounded("a".repeat(1200), 1000).exceptionOrNull() != null,
            "ASCII past the limit should be refused"
        )

        // The same byte count as three-byte UTF-8 is not refused; it comes back damaged.
        val cjk = "\u672c".repeat(400)
        assertEquals(1200, cjk.encodeToByteArray().size, "expected 3 bytes per character")
        val got = readBounded(cjk, 1000).getOrNull()
        assertNotNull(got, "multi-byte text past the limit was refused; behaviour changed")
        assertNotEquals(
            cjk,
            got,
            "if this now round-trips, the limit stopped corrupting multi-byte text and the " +
                "comment on readContentLength's sibling should be revisited"
        )
        assertTrue(
            got.any { it == '\uFFFD' },
            "expected replacement characters where a character straddled the limit"
        )

        // Comfortably under the limit in bytes, it round-trips untouched.
        assertEquals("\u672c".repeat(200), readBounded("\u672c".repeat(200), 1000).getOrNull())
    }
}
