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
package com.monkopedia.ksrpc.binary.ktor

import com.monkopedia.ksrpc.channels.RpcBinaryData
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bridge from ktor's [ByteReadChannel] onto the transport-agnostic
 * [RpcBinaryData] interface. Lives here (rather than in `ksrpc-core`) so that
 * `ksrpc-core` does not publicly depend on ktor — consumers opt in to the
 * ktor-io adapter by adding `ksrpc-binary-ktor` to their classpath.
 */
class ByteReadChannelBinaryData(
    private val channel: ByteReadChannel,
    override val size: Long? = null
) : RpcBinaryData {
    override suspend fun transferTo(
        sink: suspend (bytes: ByteArray, offset: Int, length: Int) -> Unit
    ) {
        while (!channel.isClosedForRead) {
            val packet = channel.readRemaining(BUFFER_SIZE).readBytes()
            if (packet.isNotEmpty()) {
                sink(packet, 0, packet.size)
            }
        }
    }

    override suspend fun toByteArray(): ByteArray = channel.toByteArray()

    override suspend fun close() {
        channel.cancel(null)
    }

    internal fun unwrap(): ByteReadChannel = channel

    private companion object {
        private const val BUFFER_SIZE = 8L * 1024L
    }
}

/**
 * Wrap a [ByteReadChannel] as an [RpcBinaryData].
 */
fun ByteReadChannel.asRpcBinaryData(size: Long? = null): RpcBinaryData =
    ByteReadChannelBinaryData(this, size)

/**
 * Expose an [RpcBinaryData] as a [ByteReadChannel]. Unwraps directly when the
 * underlying data is already a [ByteReadChannelBinaryData]; otherwise spins up
 * a pump coroutine that drains [RpcBinaryData.transferTo] into a new ktor
 * [ByteChannel].
 */
fun RpcBinaryData.asByteReadChannel(
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
): ByteReadChannel {
    (this as? ByteReadChannelBinaryData)?.let { return it.unwrap() }
    val out = ByteChannel(autoFlush = true)
    scope.launch {
        try {
            transferTo { bytes, offset, length ->
                out.writeFully(bytes, offset, length)
            }
            out.close()
        } catch (t: Throwable) {
            out.close(t)
        }
    }
    return out
}
