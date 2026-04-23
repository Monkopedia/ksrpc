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

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.annotation.KsTimeout
import com.monkopedia.ksrpc.jsonrpc.JsonRpcCancellationConvention
import com.monkopedia.ksrpc.jsonrpc.asJsonRpcConnection
import com.monkopedia.ksrpc.sockets.asSocketConnection
import io.ktor.utils.io.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

@KsService
interface TimeoutCancellationService : RpcService {
    @KsMethod("/slow")
    @KsTimeout(millis = 200)
    suspend fun slow(input: String): String

    @KsMethod("/fast")
    suspend fun fast(input: String): String
}

/**
 * Integration tests verifying that a @KsTimeout-triggered timeout propagates cancellation
 * to the remote server handler, exercising the intersection of issue #10 (native cancellation)
 * and issue #14 (@KsTimeout).
 */
class KsTimeoutCancellationTest {

    @Test
    fun timeoutCancelsRemoteHandlerOverPipeTransport() = runBlockingUnit {
        val cancelled = booleanArrayOf(false)
        val completed = booleanArrayOf(false)

        val service = object : TimeoutCancellationService {
            override suspend fun slow(input: String): String {
                try {
                    delay(10_000)
                    return "slow:$input"
                } catch (t: CancellationException) {
                    cancelled[0] = true
                    throw t
                }
            }

            override suspend fun fast(input: String): String {
                completed[0] = true
                return "fast:$input"
            }
        }

        val (clientToServer, serverFromClient) = createPipe()
        val (serverToClient, clientFromServer) = createPipe()

        val serverConnection = (serverFromClient to serverToClient).asSocketConnection(
            ksrpcEnvironment { }
        )
        val clientConnection = (clientFromServer to clientToServer).asSocketConnection(
            ksrpcEnvironment { }
        )

        val serverJob = launch(Dispatchers.Default) {
            serverConnection.registerDefault(service.serialized(ksrpcEnvironment { }))
        }
        try {
            val stub = clientConnection.defaultChannel()
                .toStub<TimeoutCancellationService, String>()

            // Client call should throw due to @KsTimeout(millis = 200)
            var clientTimedOut = false
            try {
                stub.slow("hello")
            } catch (_: Exception) {
                clientTimedOut = true
            }
            assertTrue(clientTimedOut, "client call must fail with timeout/cancellation")

            // Wait for the cancel frame to propagate to the server handler
            withTimeout(5_000) {
                while (!cancelled[0]) {
                    yield()
                }
            }
            assertTrue(
                cancelled[0],
                "server handler must be cancelled when @KsTimeout fires"
            )

            // Connection should remain healthy after the timeout
            assertEquals("fast:ping", stub.fast("ping"))
            assertTrue(completed[0])
        } finally {
            try {
                clientConnection.close()
            } catch (_: Throwable) {
            }
            try {
                serverConnection.close()
            } catch (_: Throwable) {
            }
            try {
                serverJob.cancel()
            } catch (_: Throwable) {
            }
        }
    }

    @Test
    fun timeoutCancelsRemoteHandlerOverJsonRpcWithLspConvention() = runBlockingUnit {
        val cancelled = booleanArrayOf(false)
        val completed = booleanArrayOf(false)

        val service = object : TimeoutCancellationService {
            override suspend fun slow(input: String): String {
                try {
                    delay(10_000)
                    return "slow:$input"
                } catch (t: CancellationException) {
                    cancelled[0] = true
                    throw t
                }
            }

            override suspend fun fast(input: String): String {
                completed[0] = true
                return "fast:$input"
            }
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
            val stub = channel.defaultChannel()
                .toStub<TimeoutCancellationService, String>()

            // Client call should throw due to @KsTimeout(millis = 200)
            var clientTimedOut = false
            try {
                stub.slow("hello")
            } catch (_: Exception) {
                clientTimedOut = true
            }
            assertTrue(clientTimedOut, "client call must fail with timeout/cancellation")

            // Wait for the LSP cancel notification to propagate to the server handler
            withTimeout(5_000) {
                while (!cancelled[0]) {
                    yield()
                }
            }
            assertTrue(
                cancelled[0],
                "server handler must be cancelled via LSP convention when @KsTimeout fires"
            )

            // Connection should remain healthy after the timeout
            assertEquals("fast:ping", stub.fast("ping"))
            assertTrue(completed[0])
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
