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

package com.monkopedia.ksrpc.sockets

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.sockets.internal.swallow
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.pool.ByteArrayPool
import io.ktor.utils.io.readAvailable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CompletableDeferred
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
    // The connection isn't built until after this coroutine launches, so hand it to the
    // copy coroutine via a deferred — it needs it to force-close on write-side failure (#200).
    val connectionReady = CompletableDeferred<Connection<String>>()
    connectionScope.launch(Dispatchers.IO) {
        val cleanEof = channel.copyToAndFlush(output)
        if (!cleanEof) {
            // The write side died (e.g. subprocess closed its stdin) while the read side may
            // still be open. Without this, the receive loop keeps waiting on the live read
            // side, MultiChannel.close() never fires, and any pending call() awaits a response
            // that can never come — hanging forever (#200). Closing the connection runs the
            // normal teardown, which completes pending receivers exceptionally so callers fail
            // fast instead of hanging.
            swallow { connectionReady.await().close() }
        }
    }
    val connection = (input.toByteReadChannel(Dispatchers.IO) to channel)
        .asConnection(connectionScope, env)
    connection.onClose {
        swallow { connectionScope.cancel() }
        swallow { input.close() }
        swallow { output.close() }
    }
    connectionReady.complete(connection)
    return connection
}

/**
 * Copies bytes from [this] byte channel to [out] stream, suspending on read and blocking on
 * the output write.
 *
 * @return `true` if the copy ended because the read side reached a clean EOF; `false` if it
 *   ended because the destination stream failed (e.g. the subprocess closed its stdin). The
 *   caller uses a `false` return to force-close the connection so pending calls don't hang on
 *   a write side that silently went away (#200).
 */
private suspend fun ByteReadChannel.copyToAndFlush(
    out: OutputStream,
    limit: Long = Long.MAX_VALUE
): Boolean {
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
                    try {
                        out.write(buffer, 0, rc)
                        out.flush()
                        copied += rc
                    } catch (t: IOException) {
                        // Destination stream is gone (e.g. subprocess exited and closed
                        // its stdin). Signal the failure to the caller so it can tear the
                        // connection down — see #200.
                        swallow { out.close() }
                        return false
                    }
                }
            } catch (t: Throwable) {
                if (!isClosedForRead) {
                    throw t
                }
            }
        }
        swallow { out.close() }

        return true
    } finally {
        ByteArrayPool.recycle(buffer)
    }
}
