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

import com.monkopedia.ksrpc.jsonrpc.JsonRpcCancellationConvention
import com.monkopedia.ksrpc.jsonrpc.asJsonRpcConnection
import io.ktor.utils.io.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

class JsonRpcCancellationTest {

    @Test
    fun configuredConventionPropagatesClientCancelToServerHandler() = runBlockingUnit {
        val started = CompletableDeferred<Unit>()
        val cancelled = booleanArrayOf(false)

        val service = object : CancelTestService {
            override suspend fun blockForever(value: String): String {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } catch (t: CancellationException) {
                    cancelled[0] = true
                    throw t
                }
            }

            override suspend fun finishesQuickly(value: String): String = "echo:$value"
        }

        val (output, input) = createPipe()
        val (so, si) = createPipe()
        GlobalScope.launch(Dispatchers.Default) {
            val connection = (input to so).asJsonRpcConnection(
                ksrpcEnvironment { },
                includeContentHeaders = false,
                cancellationConvention = JsonRpcCancellationConvention.Lsp
            )
            connection.registerDefault(service.serialized(ksrpcEnvironment { }))
        }
        try {
            val channel = (si to output).asJsonRpcConnection(
                ksrpcEnvironment { },
                includeContentHeaders = false,
                cancellationConvention = JsonRpcCancellationConvention.Lsp
            )
            val stub = channel.defaultChannel().toStub<CancelTestService, String>()

            val callJob = async(Dispatchers.Default) { stub.blockForever("never") }
            withTimeout(5_000) { started.await() }
            callJob.cancel()
            try {
                callJob.await()
                fail("expected cancellation")
            } catch (_: CancellationException) {
            }

            withTimeout(5_000) {
                while (!cancelled[0]) yield()
            }
            assertTrue(cancelled[0], "server handler must be cancelled by the LSP convention")

            assertEquals("echo:after", stub.finishesQuickly("after"))
        } finally {
            try {
                input.cancel(null)
            } catch (_: Throwable) {
            }
            try {
                si.cancel(null)
            } catch (_: Throwable) {
            }
            output.close(null)
            so.close(null)
        }
    }

    @Test
    fun defaultConventionIsLocalOnly_NoRemoteSignalSent() = runBlockingUnit {
        val started = CompletableDeferred<Unit>()
        val cancelled = booleanArrayOf(false)

        val service = object : CancelTestService {
            override suspend fun blockForever(value: String): String {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } catch (t: CancellationException) {
                    cancelled[0] = true
                    throw t
                }
            }

            override suspend fun finishesQuickly(value: String): String = "echo:$value"
        }

        val (output, input) = createPipe()
        val (so, si) = createPipe()
        GlobalScope.launch(Dispatchers.Default) {
            val connection = (input to so).asJsonRpcConnection(
                ksrpcEnvironment { },
                includeContentHeaders = false
                // default cancellation convention = None
            )
            connection.registerDefault(service.serialized(ksrpcEnvironment { }))
        }
        try {
            val channel = (si to output).asJsonRpcConnection(
                ksrpcEnvironment { },
                includeContentHeaders = false
            )
            val stub = channel.defaultChannel().toStub<CancelTestService, String>()

            val callJob = async(Dispatchers.Default) { stub.blockForever("never") }
            withTimeout(5_000) { started.await() }
            callJob.cancel()
            try {
                callJob.await()
                fail("expected cancellation")
            } catch (_: CancellationException) {
            }

            // Without a configured convention, the server handler keeps running. Allow a
            // brief window for any erroneous cancel notification to arrive — none should.
            repeat(20) { yield() }
            assertFalse(
                cancelled[0],
                "server handler must not be cancelled when convention is None"
            )
        } finally {
            try {
                input.cancel(null)
            } catch (_: Throwable) {
            }
            try {
                si.cancel(null)
            } catch (_: Throwable) {
            }
            output.close(null)
            so.close(null)
        }
    }
}
