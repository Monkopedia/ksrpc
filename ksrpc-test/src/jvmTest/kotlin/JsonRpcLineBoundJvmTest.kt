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
     * What the limit actually does, measured rather than assumed.
     *
     * Past the limit ASCII raises `TooLongLineException`, and so does multi-byte text far
     * past it. Between those sits a narrow overshoot: text that exceeds the limit by less
     * than roughly the reader's buffer comes back **whole**, with the one character that
     * straddled the boundary replaced by `U+FFFD`, and no exception.
     *
     * So the bound does hold — a peer cannot make this read without end, which is what #284
     * is about — but immediately above it there is a band where the message is altered
     * instead of refused.
     */
    @Test
    fun testTheLimitRefusesExceptForANarrowMultiByteOvershoot() = runBlockingUnit {
        suspend fun readBounded(text: String, limit: Int) = runCatching {
            val channel = ByteChannel(autoFlush = true)
            launch {
                channel.writeStringUtf8(text)
                channel.writeStringUtf8("\n")
            }
            withTimeout(10_000) { channel.readUTF8Line(limit) }
        }

        // Refused: ASCII over the limit, and multi-byte text well over it.
        assertNotNull(readBounded("a".repeat(30_000), 1000).exceptionOrNull())
        assertNotNull(readBounded("\u672c".repeat(10_000), 1000).exceptionOrNull())
        assertNotNull(readBounded("\u672c".repeat(1000), 1000).exceptionOrNull())

        // Admitted, and damaged: 1200 bytes against a 1000 limit.
        val cjk = "\u672c".repeat(400)
        assertEquals(1200, cjk.encodeToByteArray().size, "expected 3 bytes per character")
        val got = assertNotNull(readBounded(cjk, 1000).getOrNull(), "the overshoot was refused")
        assertNotEquals(cjk, got, "the overshoot round-tripped; the damage window has closed")
        assertTrue(got.any { it == '\uFFFD' }, "expected a replacement character")

        // Comfortably under the limit it round-trips untouched.
        assertEquals("\u672c".repeat(200), readBounded("\u672c".repeat(200), 1000).getOrNull())
    }

    /**
     * The damaged text decodes. That is the part worth a test of its own.
     *
     * An earlier version of this change asserted in a comment that the decode "fails on the
     * damaged text rather than on a clean refusal". It does not. The replacement characters
     * land inside a JSON string literal, so the document stays well-formed: the envelope
     * arrives intact and a handler is called with a silently altered argument.
     *
     * A truncation that reliably fails to parse would be a loud bound. One that reliably
     * parses is a transport delivering changed arguments as valid requests, which is why this
     * is pinned at the layer the claim was made about rather than one below it.
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
