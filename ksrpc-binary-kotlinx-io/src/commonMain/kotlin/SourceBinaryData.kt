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
package com.monkopedia.ksrpc.binary.kxio

import com.monkopedia.ksrpc.channels.RpcBinaryData
import kotlinx.io.Buffer
import kotlinx.io.Source

/**
 * Bridge from kotlinx.io's [Source] onto the transport-agnostic
 * [RpcBinaryData] interface. Lives here (rather than in `ksrpc-core`) so that
 * `ksrpc-core` does not publicly depend on kotlinx.io — consumers opt in to
 * the kotlinx.io adapter by adding `ksrpc-binary-kotlinx-io` to their
 * classpath.
 */
class SourceBinaryData(private val source: Source, override val size: Long? = null) :
    RpcBinaryData {
    override suspend fun transferTo(
        sink: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (!source.exhausted()) {
            val read = source.readAtMostTo(buffer, 0, buffer.size)
            if (read > 0) {
                sink(buffer, 0, read)
            } else if (read < 0) {
                break
            }
        }
    }

    override suspend fun close() {
        source.close()
    }

    internal fun unwrap(): Source = source

    private companion object {
        private const val BUFFER_SIZE = 8 * 1024
    }
}

/**
 * Wrap a [Source] as an [RpcBinaryData].
 */
fun Source.asRpcBinaryData(size: Long? = null): RpcBinaryData = SourceBinaryData(this, size)

/**
 * Expose an [RpcBinaryData] as a kotlinx.io [Source]. Unwraps directly when
 * the underlying data is already a [SourceBinaryData]; otherwise drains
 * [RpcBinaryData.transferTo] into a [Buffer] (a full materialization) — a
 * [Buffer] is itself a [Source], so callers can read from the returned value
 * immediately.
 *
 * Streaming reconstruction (piping `transferTo` into a live [Source] without
 * first materializing) is a follow-up — kotlinx.io's pipe primitives evolve
 * between versions and the full-buffer path is adequate for RPC payloads
 * currently flowing through ksrpc.
 */
suspend fun RpcBinaryData.asSource(): Source {
    (this as? SourceBinaryData)?.let { return it.unwrap() }
    val buffer = Buffer()
    transferTo { bytes, offset, length ->
        buffer.write(bytes, offset, offset + length)
    }
    return buffer
}
