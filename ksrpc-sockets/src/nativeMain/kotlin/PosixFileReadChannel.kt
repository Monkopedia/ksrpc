/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.ksrpc.sockets

import com.monkopedia.ksrpc.KsrpcEnvironment
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
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toCValues
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.newSingleThreadContext
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.close
import platform.posix.fflush
import platform.posix.fsync
import platform.posix.read
import platform.posix.write

private const val bufferSize = 4096

actual suspend inline fun withStdInOut(
    ksrpcEnvironment: KsrpcEnvironment<String>,
    withConnection: (Connection<String>) -> Unit
) {
    val input = posixFileReadChannel(STDIN_FILENO)
    val output = posixFileWriteChannel(STDOUT_FILENO)
    withoutIcanon {
        val connection = (input to output).asConnection(ksrpcEnvironment)
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
            val buffer = allocArray<ByteVar>(bufferSize)
            try {
                while (true) {
                    val readCount = read(fd, buffer, bufferSize.toULong())
                    if (readCount < 0) break
                    if (readCount == 0L) continue

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
 */
@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
fun posixFileWriteChannel(fd: Int): ByteWriteChannel {
    val thread = newSingleThreadContext("write-channel-$fd")
    return GlobalScope.reader(thread, autoFlush = true) {
        memScoped {
            try {
                while (!channel.isClosedForRead) {
                    channel.read { source, start, end ->
                        val size = end - start
                        write(fd, source.sliceArray(start until end).toCValues(), size.toULong())
                        fsync(fd)
                        fflush(null)
                        size.toInt()
                    }
                }
            } catch (cause: Throwable) {
                cause.printStackTrace()
                channel.cancel(cause)
            } finally {
                close(fd)
                thread.close()
            }
        }
    }.channel
}
