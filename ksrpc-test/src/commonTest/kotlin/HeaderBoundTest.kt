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
import com.monkopedia.ksrpc.jsonrpc.internal.jsonHeader
import com.monkopedia.ksrpc.packets.internal.MAX_HEADER_LINES
import com.monkopedia.ksrpc.packets.internal.MAX_HEADER_LINE_LENGTH
import com.monkopedia.ksrpc.sockets.internal.readFields
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.writeStringUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.withTimeout

/**
 * Header lines come from the remote peer and are read before any content, so a peer
 * that omits the blank terminator or never sends a newline must be refused rather
 * than accumulated (#263). Same reasoning as `Content-Length` in [ContentLengthBoundTest].
 *
 * Both length-prefixed transports read their own header block, so both are covered
 * here: the packet path via `readFields`, and the JSON-RPC path via the header-framed
 * transformer (#270). The JSON-RPC one is the default — `includeContentHeaders`
 * defaults to true — so its loop is the one an ordinary consumer ends up running.
 */
class HeaderBoundTest {

    @Test
    fun testRefusesMoreHeaderLinesThanTheLimit() = runBlockingUnit {
        val channel = ByteChannel(autoFlush = true)
        // No terminating blank line: without the cap this reads forever.
        repeat(MAX_HEADER_LINES + 1) { channel.writeStringUtf8("Key$it: value\r\n") }
        assertFailsWith<IOException> {
            // withTimeout so an unguarded build fails here rather than hanging.
            withTimeout(5000) { channel.readFields() }
        }
    }

    /**
     * Lines with no `:` never enter the map, so an entry-count cap alone would let this
     * read forever. Pins that the bound is on lines read, not on entries stored.
     */
    @Test
    fun testRefusesTooManyMalformedLinesThatStoreNothing() = runBlockingUnit {
        val channel = ByteChannel(autoFlush = true)
        repeat(MAX_HEADER_LINES + 1) { channel.writeStringUtf8("no-separator-here\r\n") }
        assertFailsWith<IOException> {
            withTimeout(5000) { channel.readFields() }
        }
    }

    @Test
    fun testRefusesAnOverlongSingleLine() = runBlockingUnit {
        val channel = ByteChannel(autoFlush = true)
        // One line longer than the limit, with no newline anywhere in it.
        channel.writeStringUtf8("K: " + "v".repeat(MAX_HEADER_LINE_LENGTH + 1))
        assertFailsWith<IOException> {
            withTimeout(5000) { channel.readFields() }
        }
    }

    /**
     * The guard refuses; it does not break the ordinary path.
     */
    @Test
    fun testReadsOrdinaryHeaders() = runBlockingUnit {
        val channel = ByteChannel(autoFlush = true)
        channel.writeStringUtf8("Content-Length: 5\r\nContent-Type: text/plain\r\n\r\n")
        assertEquals(
            mapOf("Content-Length" to "5", "Content-Type" to "text/plain"),
            channel.readFields()
        )
    }

    /**
     * Pins the boundary from below so `>=` cannot silently become `>`.
     *
     * [MAX_HEADER_LINES] counts the blank terminator too, so the largest accepted
     * header block is one field short of the limit.
     */
    @Test
    fun testAcceptsExactlyTheLimit() = runBlockingUnit {
        val channel = ByteChannel(autoFlush = true)
        val fields = MAX_HEADER_LINES - 1
        repeat(fields) { channel.writeStringUtf8("Key$it: value$it\r\n") }
        channel.writeStringUtf8("\r\n")
        val read = withTimeout(5000) { channel.readFields() }
        assertEquals(fields, read.size)
        assertEquals("value0", read["Key0"])
        assertEquals("value${fields - 1}", read["Key${fields - 1}"])
    }

    @Test
    fun testJsonRpcRefusesMoreHeaderLinesThanTheLimit() = runBlockingUnit {
        val input = ByteChannel(autoFlush = true)
        val transformer = (input to ByteChannel(autoFlush = true)).jsonHeader(ksrpcEnvironment { })
        // No terminating blank line: without the cap this reads forever.
        repeat(MAX_HEADER_LINES + 1) { input.writeStringUtf8("Key$it: value\r\n") }
        assertFailsWith<IOException> {
            // withTimeout so an unguarded build fails here rather than hanging.
            withTimeout(5000) { transformer.receive() }
        }
    }

    /**
     * The JSON-RPC loop skips any line without a `:`, so nothing is retained and an
     * entry-count cap would never end this. Pins that the bound is on lines read.
     */
    @Test
    fun testJsonRpcRefusesTooManyMalformedLinesThatStoreNothing() = runBlockingUnit {
        val input = ByteChannel(autoFlush = true)
        val transformer = (input to ByteChannel(autoFlush = true)).jsonHeader(ksrpcEnvironment { })
        repeat(MAX_HEADER_LINES + 1) { input.writeStringUtf8("no-separator-here\r\n") }
        assertFailsWith<IOException> {
            withTimeout(5000) { transformer.receive() }
        }
    }

    @Test
    fun testJsonRpcRefusesAnOverlongSingleLine() = runBlockingUnit {
        val input = ByteChannel(autoFlush = true)
        val transformer = (input to ByteChannel(autoFlush = true)).jsonHeader(ksrpcEnvironment { })
        // One line longer than the limit, with no newline anywhere in it.
        input.writeStringUtf8("K: " + "v".repeat(MAX_HEADER_LINE_LENGTH + 1))
        assertFailsWith<IOException> {
            withTimeout(5000) { transformer.receive() }
        }
    }

    /**
     * The guard refuses; it does not break the ordinary path.
     */
    @Test
    fun testJsonRpcReadsOrdinaryHeaders() = runBlockingUnit {
        val input = ByteChannel(autoFlush = true)
        val transformer = (input to ByteChannel(autoFlush = true)).jsonHeader(ksrpcEnvironment { })
        input.writeStringUtf8("Content-Length: 2\r\nContent-Type: application/json\r\n\r\n{}")
        assertEquals("{}", withTimeout(5000) { transformer.receive() }.toString())
    }

    /**
     * Pins the boundary from below so `>=` cannot silently become `>`.
     *
     * The count includes the blank terminator, so the largest accepted block is one
     * line short of the limit — one `Content-Length` plus filler.
     */
    @Test
    fun testJsonRpcAcceptsExactlyTheLimit() = runBlockingUnit {
        val input = ByteChannel(autoFlush = true)
        val transformer = (input to ByteChannel(autoFlush = true)).jsonHeader(ksrpcEnvironment { })
        input.writeStringUtf8("Content-Length: 2\r\n")
        repeat(MAX_HEADER_LINES - 2) { input.writeStringUtf8("Key$it: value$it\r\n") }
        input.writeStringUtf8("\r\n{}")
        assertEquals("{}", withTimeout(5000) { transformer.receive() }.toString())
    }
}
