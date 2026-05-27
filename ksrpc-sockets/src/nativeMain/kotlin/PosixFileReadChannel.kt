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
@file:OptIn(ExperimentalForeignApi::class, KsrpcInternal::class)

package com.monkopedia.ksrpc.sockets

import com.monkopedia.ksrpc.KsrpcEnvironment
import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.sockets.internal.swallow
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.read
import io.ktor.utils.io.reader
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import platform.posix.EINTR
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.close
import platform.posix.errno
import platform.posix.read
import platform.posix.write

private const val BUFFER_SIZE = 4096

actual suspend inline fun withStdInOut(
    ksrpcEnvironment: KsrpcEnvironment<String>,
    withConnection: (Connection<String>) -> Unit
) {
    val input = posixFileReadChannel(STDIN_FILENO)
    // The connection isn't built until after the write channel, so hand it to the writer's
    // failure hook via a deferred — it needs the connection to force-close on write-side
    // failure so a pending call doesn't hang on a still-open read side (#201, analog of #200).
    val connectionReady = CompletableDeferred<Connection<String>>()
    val output = posixFileWriteChannel(STDOUT_FILENO) {
        // Runs on the dedicated writer thread once the posix write has failed. Closing the
        // connection runs the normal teardown, which completes pending receivers exceptionally
        // (MultiChannel.close already does this) so callers fail fast instead of hanging.
        GlobalScope.launch {
            swallow { connectionReady.await().close() }
        }
    }
    withoutIcanon {
        val connection = (input to output).asConnection(ksrpcEnvironment)
        connectionReady.complete(connection)
        try {
            withConnection(connection)
        } finally {
            swallow { connection.close() }
            swallow { output.close() }
        }
    }
}

/**
 * Creates a [ByteReadChannel] that will read bytes from the specified file descriptor [fd].
 *
 * This is accomplished by creating a dedicated thread that blocks on reads before queueing to
 * a [ByteChannel] for suspended reading, so only use when needed.
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
fun posixFileReadChannel(fd: Int): ByteReadChannel {
    val thread = newSingleThreadContext("read-channel-$fd")
    return GlobalScope.writer(thread, autoFlush = true) {
        memScoped {
            val buffer = allocArray<ByteVar>(BUFFER_SIZE)
            try {
                while (true) {
                    val readCount = read(fd, buffer, BUFFER_SIZE.toULong())
                    if (readCount < 0) break
                    // On a blocking fd, read() == 0 is EOF (the peer closed the write end), not
                    // "no data yet" — break so the reader thread terminates. `continue` here
                    // busy-spins forever once EOF is reached, which wedges process exit (#201).
                    if (readCount == 0L) break

                    channel.writeFully(buffer, 0, readCount)
                    channel.flush()
                }
            } catch (cause: Throwable) {
                channel.close(cause)
            } finally {
                channel.close()
                close(fd)
                thread.close()
            }
        }
    }.channel
}

/**
 * Creates a [ByteWriteChannel] that will write bytes to the specified file descriptor [fd].
 *
 * This is accomplished by creating a dedicated thread that blocks on reads before queueing to
 * a [ByteChannel] for suspended reading, so only use when needed.
 *
 * @param onWriteFailure invoked once if the dedicated writer thread aborts because the underlying
 *   posix `write` failed (e.g. the peer closed the read end of the fd). Cancelling the write
 *   [ByteChannel] only surfaces to *new* sends; a call that has already sent its request and is
 *   awaiting a response is parked on the connection, not on the write channel. If the read side
 *   stays open, that pending call would hang forever (#201, the K/N analog of #200). The caller
 *   uses this hook to force-close the connection so pending receivers complete exceptionally and
 *   callers fail fast instead of hanging. It is never invoked on a clean shutdown (the write
 *   channel being closed for read, e.g. via `connection.close()`).
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
fun posixFileWriteChannel(fd: Int, onWriteFailure: () -> Unit = {}): ByteWriteChannel {
    val thread = newSingleThreadContext("write-channel-$fd")
    return GlobalScope.reader(thread, autoFlush = true) {
        var writeFailed = false
        try {
            while (!channel.isClosedForRead) {
                channel.read { source, start, end ->
                    var writeOffset = start
                    while (writeOffset < end) {
                        val written = source.usePinned { pinned ->
                            write(
                                fd,
                                pinned.addressOf(writeOffset),
                                (end - writeOffset).toULong()
                            )
                        }
                        if (written < 0) {
                            if (errno == EINTR) {
                                continue
                            }
                            error("posix write failed for fd=$fd")
                        }
                        if (written == 0L) {
                            error("posix write made no progress for fd=$fd")
                        }
                        writeOffset += written.toInt()
                    }
                    end - start
                }
            }
        } catch (cause: Throwable) {
            cause.printStackTrace()
            writeFailed = true
            channel.cancel(cause)
        } finally {
            // Cancelling the write channel above only wakes new sends; force-close the connection
            // so a call already parked awaiting a response wakes too (#201). Run before
            // thread.close() so the hook is dispatched while this coroutine's thread is still
            // live. Guarded so a clean shutdown (channel closed for read) is a no-op.
            if (writeFailed) {
                swallow { onWriteFailure() }
            }
            close(fd)
            thread.close()
        }
    }.channel
}
