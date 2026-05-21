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
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.serializer

/**
 * Regression test for issue #200: when the write side of a
 * `Pair<InputStream, OutputStream>.asConnection` fails (e.g. a subprocess closed
 * its stdin) while the read side stays open, an in-flight `call()` used to await a
 * response that could never arrive and hang forever. The fix force-closes the
 * connection on write-side failure so the pending call wakes with an exception
 * instead of hanging.
 */
class CallHangsOnWriteFailureJvmTest {

    /** OutputStream that throws once [fail] is triggered (mimics a dead subprocess stdin). */
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
    fun callWakesWhenWriteSideFails() = runBlockingUnit {
        val env = ksrpcEnvironment { }
        // Piped input stays open (no EOF) — this is the asymmetric case where only the
        // write side dies, which is the one that used to hang (#200). If the read side
        // also closed, the receive loop would EOF and tear down the connection anyway.
        val pipeFeed = PipedOutputStream()
        val input = PipedInputStream(pipeFeed, 64 * 1024)
        val output = ClosableFailingOutputStream()

        val connection = (input to output).asConnection(env)
        connection.registerDefault(EchoSerializedService(env))

        val request = env.serialization.createCallData(String.serializer(), "hello")
        output.fail()

        // The call must terminate (throw) rather than hang. Bound it so a regression
        // surfaces as a test failure (assertNotNull below) instead of wedging the suite.
        val outcome = withTimeoutOrNull(5_000) {
            runCatching {
                connection.defaultChannel().call("echo", request, callId = null)
            }
        }

        assertNotNull(
            outcome,
            "call() did not terminate within 5s after the write side failed — it hung (#200)"
        )

        runCatching { pipeFeed.close() }
        runCatching { connection.close() }
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
