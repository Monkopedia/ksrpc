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
@file:OptIn(ExperimentalForeignApi::class)

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.sockets.asConnection
import com.monkopedia.ksrpc.sockets.posixFileReadChannel
import com.monkopedia.ksrpc.sockets.posixFileWriteChannel
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer
import platform.posix.close
import platform.posix.pipe

/**
 * Regression test for issue #201 (Kotlin/Native posix analog of #200): when the write side of a
 * posix-backed connection fails (e.g. the peer closed the read end of the fd) while the read side
 * stays open, an in-flight `call()` used to await a response that could never arrive and hang
 * forever. The fix force-closes the connection on write-side failure (via the `onWriteFailure`
 * hook threaded through [posixFileWriteChannel]) so the pending call wakes with an exception
 * instead of hanging.
 */
class CallHangsOnWriteFailureNativeTest {

    @Test
    fun callWakesWhenWriteSideFails() = runBlockingUnit {
        memScoped {
            val env = ksrpcEnvironment { }

            // Read side: a pipe whose write end stays open for the duration of the test, so the
            // read fd never sees EOF — the receive loop keeps waiting. This is the asymmetric
            // case that used to hang (#201): only the write side dies.
            val readPipe = allocArray<IntVar>(2)
            require(pipe(readPipe) >= 0) { "Failed to create read pipe" }
            val readFd = readPipe[0]
            val readPipeWriteEnd = readPipe[1]

            // Write side: a pipe we fully close so the fd is invalid. Posix write then returns
            // EBADF (not EPIPE, which would raise SIGPIPE and kill the test process), mimicking a
            // dead peer whose stream went away. The non-EINTR failure trips the onWriteFailure
            // hook exactly as a real write-side death would.
            val writePipe = allocArray<IntVar>(2)
            require(pipe(writePipe) >= 0) { "Failed to create write pipe" }
            val writeFd = writePipe[1]
            close(writePipe[0])
            close(writePipe[1])

            // The connection isn't built until after the write channel, so hand it to the
            // failure hook via a deferred — exactly the withStdInOut wiring.
            val connectionReady = CompletableDeferred<Connection<String>>()

            val input = posixFileReadChannel(readFd)
            val output = posixFileWriteChannel(writeFd) {
                // Mirrors the withStdInOut wiring: force-close the connection when the write
                // side dies so pending receivers complete exceptionally.
                GlobalScope.launch {
                    runCatching { connectionReady.await().close() }
                }
            }

            val connection = (input to output).asConnection(env)
            connection.registerDefault(EchoSerializedService(env))
            connectionReady.complete(connection)

            val request = env.serialization.createCallData(String.serializer(), "hello")

            // The call must terminate (throw) rather than hang. Bound it so a regression surfaces
            // as a test failure (assertNotNull below) instead of wedging the suite.
            val outcome = withTimeoutOrNull(5_000) {
                runCatching {
                    connection.defaultChannel().call("echo", request, callId = null)
                }
            }

            assertNotNull(
                outcome,
                "call() did not terminate within 5s after the write side failed — hung (#201)"
            )

            runCatching { close(readPipeWriteEnd) }
            runCatching { connection.close() }
        }
    }

    private class EchoSerializedService(override val env: KsrpcEnvironment<String>) :
        SerializedService<String> {
        override suspend fun call(
            endpoint: String,
            input: CallData<String>,
            callId: RpcCallId?
        ): CallData<String> = input

        override suspend fun close() = Unit

        override suspend fun onClose(onClose: suspend () -> Unit) = Unit
    }
}
