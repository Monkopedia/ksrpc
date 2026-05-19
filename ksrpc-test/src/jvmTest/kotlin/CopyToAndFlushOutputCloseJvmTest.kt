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
package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.channels.CallData
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.RpcCallId
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.sockets.asConnection
import java.io.IOException
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer

/**
 * Regression test for issue #169: when the OutputStream backing a
 * `Pair<InputStream, OutputStream>.asConnection` is closed before the read side
 * observes the close (the typical pattern when a subprocess exits), the
 * `copyToAndFlush` coroutine used to leak the resulting `IOException` to the
 * thread's uncaught-exception handler.
 */
class CopyToAndFlushOutputCloseJvmTest {

    /**
     * OutputStream that throws [IOException] from `write` and `flush` once
     * [fail] has been triggered, mimicking
     * `java.lang.ProcessBuilder$NullOutputStream` after the subprocess exits.
     */
    private class ClosableFailingOutputStream : OutputStream() {
        @Volatile
        private var failing = false

        fun fail() {
            failing = true
        }

        override fun write(b: Int) {
            if (failing) throw IOException("Stream closed")
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (failing) throw IOException("Stream closed")
        }

        override fun flush() {
            if (failing) throw IOException("Stream closed")
        }

        override fun close() {
            failing = true
        }
    }

    @Test
    fun outputStreamCloseDoesNotLeakIoExceptionToParentScope() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        // Use a piped pair so the InputStream stays open: this mirrors the
        // subprocess case where the process's stdout is still readable but
        // its stdin (our OutputStream) has been closed mid-write.
        val pipeFeed = PipedOutputStream()
        val input = PipedInputStream(pipeFeed, 64 * 1024)
        val output = ClosableFailingOutputStream()
        val capturedException = AtomicReference<Throwable?>(null)
        val handler = CoroutineExceptionHandler { _: CoroutineContext, t: Throwable ->
            capturedException.set(t)
        }

        var connection: Connection<String>? = null
        try {
            connection = withContext(handler) {
                (input to output).asConnection(env)
            }
            connection.registerDefault(EchoSerializedService(env))

            // Trigger writes through the OutputStream by issuing an RPC call,
            // but mark the output as failed first so the writer hits IOException
            // mid-flight while the read side is still open (mirroring a
            // subprocess that exited before draining its stdin).
            withContext(handler) {
                val request = env.serialization.createCallData(
                    String.serializer(),
                    "hello"
                )
                output.fail()
                // The call will never return: after #169/PR #172 the writer silently
                // swallows the IOException, so the request bytes never reach the peer
                // and no response packet comes back. Bound the wait — what we care
                // about is whether copyToAndFlush leaked the exception to the parent
                // scope, not whether the call completes. 1s is plenty for the writer
                // to observe the failed write.
                withTimeoutOrNull(1000) {
                    runCatching {
                        connection!!.defaultChannel().call("echo", request, callId = null)
                    }
                }
                // Belt-and-suspenders pause so the copy coroutine has a chance to
                // observe the failed write and either swallow it (fixed) or rethrow
                // (buggy) before we read capturedException.
                delay(300)
            }
        } finally {
            runCatching { pipeFeed.close() }
            runCatching { connection?.close() }
        }

        assertNull(
            capturedException.get(),
            "copyToAndFlush leaked exception to parent scope: ${capturedException.get()}"
        )
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
