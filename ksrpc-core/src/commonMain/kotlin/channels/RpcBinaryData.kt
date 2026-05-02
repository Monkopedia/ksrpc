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
package com.monkopedia.ksrpc.channels

import com.monkopedia.ksrpc.SuspendCloseable

/**
 * Transport-agnostic binary data source. Implementations adapt user-facing
 * types (`ByteReadChannel`, `kotlinx.io.Source`, `okio.BufferedSource`, ...)
 * to a shape the ksrpc wire protocol can consume.
 *
 * Source-oriented only: in ksrpc the binary payload is always "something the
 * receiver reads from," regardless of which side is sending.
 */
interface RpcBinaryData : SuspendCloseable {
    /** Known byte length, or `null` for unknown / streaming sources. */
    val size: Long?
        get() = null

    /**
     * Pull all bytes from this source into [sink]. The [sink] callback may be
     * invoked many times with different offsets / lengths into a buffer the
     * implementation owns; callers must not retain the `bytes` array beyond
     * the duration of the call.
     */
    suspend fun transferTo(sink: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit)

    /**
     * Drain this source into a single [ByteArray]. Default implementation
     * accumulates [transferTo] chunks; subclasses that already hold the data
     * in memory should override this to return it directly.
     */
    suspend fun toByteArray(): ByteArray {
        val knownSize = size
        if (knownSize != null && knownSize <= Int.MAX_VALUE) {
            val out = ByteArray(knownSize.toInt())
            var pos = 0
            transferTo { bytes, offset, length ->
                bytes.copyInto(out, pos, offset, offset + length)
                pos += length
            }
            return out
        }
        val chunks = mutableListOf<ByteArray>()
        var total = 0
        transferTo { bytes, offset, length ->
            chunks.add(bytes.copyOfRange(offset, offset + length))
            total += length
        }
        val out = ByteArray(total)
        var pos = 0
        for (chunk in chunks) {
            chunk.copyInto(out, pos)
            pos += chunk.size
        }
        return out
    }
}

/**
 * [RpcBinaryData] backed by an in-memory [ByteArray]. Avoids all streaming
 * and coroutine overhead — [transferTo] delivers the entire buffer in a single
 * callback, and [toByteArray] returns the array directly.
 */
class ByteArrayBinaryData(
    private val data: ByteArray,
    private val offset: Int = 0,
    private val length: Int = data.size - offset
) : RpcBinaryData {
    override val size: Long get() = length.toLong()

    override suspend fun transferTo(
        sink: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit
    ) {
        if (length > 0) sink(data, offset, length)
    }

    override suspend fun toByteArray(): ByteArray =
        if (offset == 0 && length == data.size) data
        else data.copyOfRange(offset, offset + length)

    override suspend fun close() {} // nothing to release
}
