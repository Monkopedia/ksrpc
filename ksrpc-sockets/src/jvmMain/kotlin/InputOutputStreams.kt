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
package com.monkopedia.ksrpc.sockets

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.sockets.internal.swallow
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.pool.ByteArrayPool
import io.ktor.utils.io.readAvailable
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Helper that calls into Pair<ByteReadChannel, ByteWriteChannel>.asConnection.
 */
suspend fun Pair<InputStream, OutputStream>.asConnection(
    env: KsrpcEnvironment<String>
): Connection<String> {
    val (input, output) = this
    val channel = ByteChannel(autoFlush = true)
    val connectionScope = CoroutineScope(coroutineContext + SupervisorJob())
    connectionScope.launch(Dispatchers.IO) {
        channel.copyToAndFlush(output)
    }
    return (input.toByteReadChannel(Dispatchers.IO) to channel)
        .asConnection(connectionScope, env)
        .also {
            it.onClose {
                swallow { connectionScope.cancel() }
                swallow { input.close() }
                swallow { output.close() }
            }
        }
}

/**
 * Copies up to [limit] bytes from [this] byte channel to [out] stream suspending on read channel
 * and blocking on output
 *
 * @return number of bytes copied
 */
private suspend fun ByteReadChannel.copyToAndFlush(
    out: OutputStream,
    limit: Long = Long.MAX_VALUE
): Long {
    require(limit >= 0) { "Limit shouldn't be negative: $limit" }

    val buffer = ByteArrayPool.borrow()
    try {
        var copied = 0L
        val bufferSize = buffer.size.toLong()

        while (copied < limit && !isClosedForRead) {
            try {
                val rc = readAvailable(buffer, 0, minOf(limit - copied, bufferSize).toInt())
                if (rc == -1 && isClosedForRead) break
                if (rc > 0) {
                    out.write(buffer, 0, rc)
                    out.flush()
                    copied += rc
                }
            } catch (t: Throwable) {
                if (!isClosedForRead) {
                    throw t
                }
            }
        }
        swallow { out.close() }

        return copied
    } finally {
        ByteArrayPool.recycle(buffer)
    }
}
