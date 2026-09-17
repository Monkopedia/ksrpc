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
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The newline-delimited transport reads a whole message as one line, so without a limit a peer
 * that never sends a newline is bounded only by heap (#284).
 *
 * These live in `jvmTest` rather than `commonTest` on purpose. Exceeding the bound means
 * feeding more than [MAX_CONTENT_LENGTH], which is affordable on a JVM worker and is not
 * something to ask of the JS and Wasm suites.
 *
 * Only the first test goes through `jsonLine`. The other two are characterisation of Ktor's
 * `readUTF8Line` and are marked as such individually — they exist so that migrating off it
 * (#295) fails loudly rather than silently changing what this transport accepts.
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
     * **Characterisation, not red-first.** This passes with and without the bound, because it
     * describes what Ktor's `readUTF8Line` does rather than what this repository changed. It
     * earns its place because `readUTF8Line` is deprecated in 3.5.1 and the obvious
     * replacement takes no limit at all, so the migration in #295 has to make this fail
     * loudly instead of quietly changing behaviour. It reads a bare [ByteChannel] and never
     * touches production code.
     *
     * Past the limit ASCII is refused, and so is text far past it. Between sits a band where
     * a message is admitted and altered, and the two edges are in different units: damage
     * begins once the *byte* length passes the limit, refusal waits for the decoded
     * *character* count to pass it. So the band runs from the limit to about
     * bytes-per-character times the limit, and it scales with the limit rather than being a
     * fixed width.
     *
     * Encoding width also decides whether damage occurs at all — two-byte text is clean up to
     * its refusal edge, while four-byte text at the same byte count is damaged — and the
     * replacements recur as the reader refills rather than marking one straddled character,
     * so the count grows with length. All of that is sampled below at one and two byte
     * widths, three and four, and at two limits.
     */
    @Test
    fun testTheLimitRefusesExceptWithinAnEncodingWidthBand() = runBlockingUnit {
        suspend fun readBounded(text: String, limit: Int) = runCatching {
            val channel = ByteChannel(autoFlush = true)
            launch {
                channel.writeStringUtf8(text)
                channel.writeStringUtf8("\n")
            }
            withTimeout(10_000) { channel.readUTF8Line(limit) }
        }

        // Refused *by the limit*, not by hanging until the timeout. The distinction is the
        // whole point — not-hanging is what #284 is about, and an assertion that only checks
        // "something was thrown" goes green on the very failure this guards against.
        suspend fun assertRefused(text: String, limit: Int) {
            val thrown = readBounded(text, limit).exceptionOrNull()
            assertIs<IOException>(
                thrown,
                "expected the limit to refuse ${text.encodeToByteArray().size} bytes, but got " +
                    (thrown?.let { it::class.simpleName } ?: "no exception")
            )
        }

        suspend fun damageOf(text: String, limit: Int): Int {
            val line = assertNotNull(
                readBounded(text, limit).getOrNull(),
                "${text.encodeToByteArray().size} bytes at limit $limit was refused"
            )
            return line.count { it == '\uFFFD' }
        }

        val oneByte = "a"
        val twoByte = "\u00e9"
        val threeByte = "\u672c"
        val fourByte = "\uD83D\uDE00"
        assertEquals(2, twoByte.encodeToByteArray().size)
        assertEquals(3, threeByte.encodeToByteArray().size)
        assertEquals(4, fourByte.encodeToByteArray().size)

        // One byte per character: no band at all, refusal begins one character over.
        assertEquals(0, damageOf(oneByte.repeat(1000), 1000))
        assertRefused(oneByte.repeat(1001), 1000)

        // Three-byte: damaged across the band, refused above it.
        assertEquals(3, damageOf(threeByte.repeat(334), 1000), "1002 bytes")
        assertEquals(8, damageOf(threeByte.repeat(800), 1000), "2400 bytes")
        assertEquals(15, damageOf(threeByte.repeat(980), 1000), "2940 bytes")
        assertRefused(threeByte.repeat(999), 1000)

        // The band scales with the limit rather than with a fixed buffer: doubling the limit
        // moves the refusal edge from ~3000 bytes to ~6000.
        assertTrue(damageOf(threeByte.repeat(1900), 2000) > 0, "5700 bytes at limit 2000")
        assertRefused(threeByte.repeat(1999), 2000)

        // Encoding width decides whether damage happens at all. Two-byte text straddles
        // nothing at this limit, so it is clean right up to its refusal edge; four-byte text
        // at the same byte count is damaged.
        assertEquals(0, damageOf(twoByte.repeat(900), 1000), "1800 bytes of two-byte text")
        assertRefused(twoByte.repeat(999), 1000)
        assertTrue(damageOf(fourByte.repeat(450), 1000) > 0, "1800 bytes of four-byte text")

        // Comfortably under the limit in bytes, text round-trips untouched.
        assertEquals(
            threeByte.repeat(200),
            readBounded(threeByte.repeat(200), 1000).getOrNull()
        )
    }

    /**
     * The damaged text decodes, which is the part worth its own test.
     *
     * **Characterisation, not red-first**, for the same reason as above: it exercises
     * `readUTF8Line` and `kotlinx.serialization` on a bare channel, not this change. It
     * performs the same two steps `receive` does — a bounded read, then a decode — rather
     * than calling `receive` itself, because reaching the band through `receive` costs more
     * than 64 MiB. That is the cost `testLineTransportRefusesAnOverlongLine` pays, and why
     * that one carries a long timeout.
     *
     * An earlier revision of this change asserted in a comment that the decode "fails on the
     * damaged text rather than on a clean refusal". It does not. The replacements land inside
     * a JSON string literal, so the document stays well-formed: the envelope arrives intact
     * and a handler is called with a silently altered argument. A truncation that reliably
     * fails to parse is a loud bound; one that reliably parses is a transport delivering
     * changed arguments as valid requests.
     */
    @Test
    fun testTextDamagedByTheLimitStillDecodesAsAValidRequest() = runBlockingUnit {
        // Whether a character straddles the limit depends on the byte alignment of the value,
        // so sweep the three offsets rather than depending on one. Two of every three damage;
        // the aligned one comes through clean, which is why a single-offset test can pass by
        // luck and say nothing.
        var damaged = 0
        for (pad in 0..2) {
            val method = "m".repeat(1 + pad)
            val head = """{"jsonrpc":"2.0","id":7,"method":"$method","params":{"s":""""
            val value = "\u672c".repeat(382)
            val message = head + value + """"}}"""
            val bytes = message.encodeToByteArray().size
            assertTrue(bytes in 1001..1999, "must land in the overshoot band, was $bytes")

            val channel = ByteChannel(autoFlush = true)
            launch {
                channel.writeStringUtf8(message)
                channel.writeStringUtf8("\n")
            }
            val line = assertNotNull(withTimeout(10_000) { channel.readUTF8Line(1000) })
            if (line.none { it == '\uFFFD' }) continue
            damaged++

            // The whole point: damaged text does not fail to parse.
            val decoded = Json.parseToJsonElement(line).jsonObject
            assertEquals(
                method,
                decoded["method"]?.jsonPrimitive?.content,
                "envelope survived intact at pad=$pad"
            )
            assertEquals("7", decoded["id"]?.jsonPrimitive?.content, "envelope survived intact")
            assertNotEquals(
                value,
                decoded["params"]?.jsonObject?.get("s")?.jsonPrimitive?.content,
                "the argument was altered and the request still decoded as well-formed"
            )
        }
        assertEquals(2, damaged, "expected two of three byte alignments to damage the value")
    }
}
