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

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.sockets.asConnection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

@KsService
interface CancelTestService : RpcService {
    @KsMethod("/blockForever")
    suspend fun blockForever(value: String): String

    @KsMethod("/finishesQuickly")
    suspend fun finishesQuickly(value: String): String
}

/**
 * Module-level counter for in-flight handler invocations. The atomicfu plugin only accepts
 * `atomic(...)` allocation in primary-constructor / class-init scopes, so test-local counters
 * use a top-level holder (same pattern as [com.monkopedia.ksrpc.LeakTracker]).
 */
private object PendingCallCounter {
    var value: Int by atomic(0)
    fun reset() {
        value = 0
    }
    fun increment() {
        value++
    }
    fun decrement() {
        value--
    }
}

private class TrackingCancelService : CancelTestService {
    val started = CompletableDeferred<Unit>()

    // Plain volatile-style flags; tests run on a single-thread test dispatcher and only do
    // simple read/write so a value class slot is sufficient. Avoids hitting the atomicfu
    // transformer's restriction against `atomic(...)` outside class init in lambda scopes.
    private val state = BooleanArray(2)
    val cancelled: Boolean get() = state[0]
    val completed: Boolean get() = state[1]

    override suspend fun blockForever(value: String): String {
        started.complete(Unit)
        try {
            awaitCancellation()
        } catch (t: CancellationException) {
            state[0] = true
            throw t
        }
    }

    override suspend fun finishesQuickly(value: String): String {
        state[1] = true
        return "echo:$value"
    }
}

class PacketCancellationTest {

    @Test
    fun callerCancellationCancelsServerHandlerOverPipeTransport() = runBlockingUnit {
        val (clientToServer, serverFromClient) = createPipe()
        val (serverToClient, clientFromServer) = createPipe()
        val service = TrackingCancelService()

        // Pipe 1 ferries client -> server (client writes `clientToServer`, server reads
        // `serverFromClient`). Pipe 2 ferries the return direction.
        val serverConnection = (serverFromClient to serverToClient).asConnection(
            ksrpcEnvironment { }
        )
        val clientConnection = (clientFromServer to clientToServer).asConnection(
            ksrpcEnvironment { }
        )

        val serverJob = launch(Dispatchers.Default) {
            serverConnection.registerDefault(service.serialized(ksrpcEnvironment { }))
        }
        try {
            val stub = clientConnection.defaultChannel().toStub<CancelTestService, String>()

            val callJob = async(Dispatchers.Default) {
                stub.blockForever("never-returns")
            }
            // Wait for the handler to actually start running so the cancel arrives mid-call.
            withTimeout(5_000) { service.started.await() }
            callJob.cancel()
            try {
                callJob.await()
                fail("callJob should have been cancelled before producing a result")
            } catch (_: CancellationException) {
                // expected
            }

            // Server handler must be cancelled by the cancel frame.
            withTimeout(5_000) {
                while (!service.cancelled) {
                    yield()
                }
            }
            assertTrue(service.cancelled, "server handler must observe cancellation")

            // Subsequent unrelated calls still work — the connection stays healthy.
            assertEquals("echo:hi", stub.finishesQuickly("hi"))
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
    fun happyPathStillWorksWithCancellationCapableTransport() = runBlockingUnit {
        val (clientToServer, serverFromClient) = createPipe()
        val (serverToClient, clientFromServer) = createPipe()
        val service = TrackingCancelService()

        // Pipe 1 ferries client -> server (client writes `clientToServer`, server reads
        // `serverFromClient`). Pipe 2 ferries the return direction.
        val serverConnection = (serverFromClient to serverToClient).asConnection(
            ksrpcEnvironment { }
        )
        val clientConnection = (clientFromServer to clientToServer).asConnection(
            ksrpcEnvironment { }
        )

        val serverJob = launch(Dispatchers.Default) {
            serverConnection.registerDefault(service.serialized(ksrpcEnvironment { }))
        }
        try {
            val stub = clientConnection.defaultChannel().toStub<CancelTestService, String>()
            assertEquals("echo:foo", stub.finishesQuickly("foo"))
            assertTrue(service.completed)
            assertFalse(service.cancelled, "no cancellation should be observed for happy path")
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
    fun cancellationBeforeResponseDoesNotLeakPendingState() = runBlockingUnit {
        // Stresses the [MultiChannel.cancelPending] path on the client side: we cancel several
        // pending calls and then make sure new calls still progress and the stale entries
        // don't trip the "No pending receiver" guard.
        val (clientToServer, serverFromClient) = createPipe()
        val (serverToClient, clientFromServer) = createPipe()
        // Module-level counter: avoids the atomicfu-in-lambda restriction by routing through a
        // plain top-level singleton, mirroring the pattern from RpcSubserviceLeakTest.
        PendingCallCounter.reset()

        val service = object : CancelTestService {
            override suspend fun blockForever(value: String): String {
                PendingCallCounter.increment()
                try {
                    awaitCancellation()
                } finally {
                    PendingCallCounter.decrement()
                }
            }

            override suspend fun finishesQuickly(value: String): String = "echo:$value"
        }

        // Pipe 1 ferries client -> server (client writes `clientToServer`, server reads
        // `serverFromClient`). Pipe 2 ferries the return direction.
        val serverConnection = (serverFromClient to serverToClient).asConnection(
            ksrpcEnvironment { }
        )
        val clientConnection = (clientFromServer to clientToServer).asConnection(
            ksrpcEnvironment { }
        )

        val serverJob = launch(Dispatchers.Default) {
            serverConnection.registerDefault(service.serialized(ksrpcEnvironment { }))
        }
        try {
            val stub = clientConnection.defaultChannel().toStub<CancelTestService, String>()
            val iterations = 5
            repeat(iterations) {
                val callJob = async(Dispatchers.Default) {
                    stub.blockForever("iter-$it")
                }
                // Wait for the server to enter blockForever for this iteration.
                withTimeout(5_000) {
                    while (PendingCallCounter.value == 0) yield()
                }
                callJob.cancel()
                try {
                    callJob.await()
                } catch (_: CancellationException) {
                }
                // Cancellation must drain the server-side handler.
                withTimeout(5_000) {
                    while (PendingCallCounter.value > 0) yield()
                }
            }

            // After the cancellation barrage, an unrelated call still resolves promptly.
            assertEquals("echo:after", stub.finishesQuickly("after"))
            assertEquals(
                0,
                PendingCallCounter.value,
                "no server-side handler should remain in flight"
            )
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
}
