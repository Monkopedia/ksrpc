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
import com.monkopedia.ksrpc.packets.internal.MAX_CONTENT_LENGTH
import com.monkopedia.ksrpc.sockets.internal.readContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.errors.IOException
import io.ktor.utils.io.writeStringUtf8
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.withTimeout

/**
 * A `Content-Length` is supplied by the remote peer and the receive buffer is
 * allocated from it before any content is read, so an unbounded value lets one
 * short header cost the host its heap (#249). Both length-prefixed transports
 * must reject an out-of-range length instead of allocating for it.
 */
class ContentLengthBoundTest {

    @Test
    fun testSocketTransportRejectsOversizedContentLength() = runBlockingUnit {
        val channel = ByteChannel(autoFlush = true)
        assertFailsWith<IOException> {
            // withTimeout so an unguarded build fails here rather than hanging:
            // without the bound this allocates the buffer and then blocks in
            // readFully waiting for content that never arrives.
            withTimeout(5000) {
                channel.readContent(mapOf("Content-Length" to "${MAX_CONTENT_LENGTH + 1L}"))
            }
        }
    }

    @Test
    fun testSocketTransportRejectsNegativeContentLength() = runBlockingUnit {
        val channel = ByteChannel(autoFlush = true)
        assertFailsWith<IOException> {
            withTimeout(5000) {
                channel.readContent(mapOf("Content-Length" to "-1"))
            }
        }
    }

    @Test
    fun testSocketTransportAcceptsLengthAtTheLimit() = runBlockingUnit {
        val channel = ByteChannel(autoFlush = true)
        channel.writeStringUtf8("hello")
        assertEquals("hello", channel.readContent(mapOf("Content-Length" to "5")))
    }

    @Test
    fun testJsonRpcTransportRejectsOversizedContentLength() = runBlockingUnit {
        val input = ByteChannel(autoFlush = true)
        val output = ByteChannel(autoFlush = true)
        val transformer = (input to output).jsonHeader(ksrpcEnvironment { })
        input.writeStringUtf8("Content-Length: ${MAX_CONTENT_LENGTH + 1L}\r\n\r\n")
        assertFailsWith<IOException> { withTimeout(5000) { transformer.receive() } }
    }

    @Test
    fun testJsonRpcTransportRejectsNegativeContentLength() = runBlockingUnit {
        val input = ByteChannel(autoFlush = true)
        val output = ByteChannel(autoFlush = true)
        val transformer = (input to output).jsonHeader(ksrpcEnvironment { })
        input.writeStringUtf8("Content-Length: -1\r\n\r\n")
        assertFailsWith<IOException> { withTimeout(5000) { transformer.receive() } }
    }
}
