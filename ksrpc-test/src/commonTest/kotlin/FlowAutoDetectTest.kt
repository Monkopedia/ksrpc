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
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.flow.KsFlowService
import com.monkopedia.ksrpc.flow.asKsFlow
import com.monkopedia.ksrpc.sockets.asConnection
import io.ktor.utils.io.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable

/**
 * End-to-end tests for the compiler plugin's `Flow<T>` / `KsFlowService<T>`
 * auto-detection (issue #39). The user-visible service interface uses only
 * `Flow<T>`; the compiler plugin wires both the client stub and the host-side
 * executor through the `KsFlowService` sub-service protocol transparently.
 *
 * Uses a bidirectional pipe transport — Flow support requires bidirectional
 * channels so the server can call back into the `KsFlowCollector` sub-service.
 */
class FlowAutoDetectTest {

    @Serializable
    data class Update(val value: String)

    @Serializable
    data class Chunk(val bytes: String)

    @Serializable
    data class Receipt(val count: Int)

    /**
     * User-facing service: `Flow<T>` return, `Flow<T>` param, and a mixed-style
     * raw `KsFlowService<T>` return to verify the compiler plugin still routes
     * raw `KsFlowService<T>` signatures through the pre-#39 codegen path.
     */
    @KsService
    interface StreamService : RpcBidiService {
        @KsMethod("/updates")
        suspend fun updates(filter: String): Flow<Update>

        @KsMethod("/upload")
        suspend fun upload(items: Flow<Chunk>): Receipt

        @KsMethod("/raw")
        suspend fun raw(u: Unit): KsFlowService<Update>
    }

    /**
     * Basic round-trip: server returns `Flow<Update>`, client collects via the
     * `Flow<T>` API, items flow end-to-end and auto-close fires.
     */
    @Test
    fun flowReturnRoundTrips() = executePipe(
        serviceJob = { c ->
            val impl = object : StreamService {
                override suspend fun updates(filter: String): Flow<Update> = flow {
                    emit(Update("$filter-a"))
                    emit(Update("$filter-b"))
                    emit(Update("$filter-c"))
                }

                override suspend fun upload(items: Flow<Chunk>): Receipt =
                    Receipt(items.toList().size)

                override suspend fun raw(u: Unit): KsFlowService<Update> =
                    error("not exercised here")

                override suspend fun close() = Unit
            }
            c.registerDefault(impl.serialized<StreamService, String>(c.env))
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<StreamService, String>()
            val result = stub.updates("x").toList()
            assertEquals(listOf(Update("x-a"), Update("x-b"), Update("x-c")), result)
        }
    )

    /**
     * Param position: client sends `Flow<Chunk>`, server collects via the
     * auto-injected `Flow<T>` param.
     */
    @Test
    fun flowParamRoundTrips() = executePipe(
        serviceJob = { c ->
            val impl = object : StreamService {
                override suspend fun updates(filter: String): Flow<Update> =
                    error("not exercised here")

                override suspend fun upload(items: Flow<Chunk>): Receipt {
                    val collected = items.toList()
                    return Receipt(collected.size)
                }

                override suspend fun raw(u: Unit): KsFlowService<Update> =
                    error("not exercised here")

                override suspend fun close() = Unit
            }
            c.registerDefault(impl.serialized<StreamService, String>(c.env))
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<StreamService, String>()
            val receipt = stub.upload(
                flow {
                    emit(Chunk("a"))
                    emit(Chunk("b"))
                    emit(Chunk("c"))
                    emit(Chunk("d"))
                }
            )
            assertEquals(Receipt(4), receipt)
        }
    )

    /**
     * Auto-close: a single terminal collection on the client's `Flow<T>` tears
     * down the sub-service. Verified indirectly here by confirming a second
     * iteration re-initiates a fresh collection on the server (each call to
     * `updates(...)` on the stub reaches a fresh server-side flow).
     */
    @Test
    fun flowAutoClosesOnCollect() = executePipe(
        serviceJob = { c ->
            val invocations = CompletableDeferred<Int>()
            var counter = 0
            val impl = object : StreamService {
                override suspend fun updates(filter: String): Flow<Update> = flow {
                    val n = ++counter
                    emit(Update("$filter-$n"))
                    if (n == 2) {
                        invocations.complete(n)
                    }
                }

                override suspend fun upload(items: Flow<Chunk>): Receipt =
                    error("not exercised here")

                override suspend fun raw(u: Unit): KsFlowService<Update> =
                    error("not exercised here")

                override suspend fun close() = Unit
            }
            c.registerDefault(impl.serialized<StreamService, String>(c.env))
            withTimeout(5000) {
                assertEquals(2, invocations.await())
            }
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<StreamService, String>()
            val first = stub.updates("x").toList()
            assertEquals(listOf(Update("x-1")), first)
            val second = stub.updates("x").toList()
            assertEquals(listOf(Update("x-2")), second)
        }
    )

    /**
     * Error propagation through a `Flow<T>` return — server-side exception
     * surfaces as an [RpcException] on the client's collect.
     */
    @Test
    fun flowErrorPropagates() = executePipe(
        serviceJob = { c ->
            val impl = object : StreamService {
                override suspend fun updates(filter: String): Flow<Update> = flow {
                    emit(Update("ok"))
                    throw RuntimeException("server boom")
                }

                override suspend fun upload(items: Flow<Chunk>): Receipt =
                    error("not exercised here")

                override suspend fun raw(u: Unit): KsFlowService<Update> =
                    error("not exercised here")

                override suspend fun close() = Unit
            }
            c.registerDefault(impl.serialized<StreamService, String>(c.env))
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<StreamService, String>()
            val got = mutableListOf<Update>()
            val exception = assertFailsWith<RpcException> {
                stub.updates("x").collect { got.add(it) }
            }
            assertEquals(listOf(Update("ok")), got)
            assertTrue(
                exception.message.contains("server boom"),
                "expected server message to propagate; got: ${exception.message}"
            )
        }
    )

/**
     * Raw `KsFlowService<T>` must still work after the `Flow<T>` auto-detection
     * was added — the same service interface exposes both shapes, and each
     * compiles through its own codegen path (#39 must not regress PR #61's
     * raw-`KsFlowService` integration tests).
     */
    @Test
    fun rawKsFlowServiceStillWorks() = executePipe(
        serviceJob = { c ->
            val impl = object : StreamService {
                override suspend fun updates(filter: String): Flow<Update> =
                    error("not exercised here")

                override suspend fun upload(items: Flow<Chunk>): Receipt =
                    error("not exercised here")

                override suspend fun raw(u: Unit): KsFlowService<Update> =
                    flow {
                        emit(Update("r-1"))
                        emit(Update("r-2"))
                    }.asKsFlow()

                override suspend fun close() = Unit
            }
            c.registerDefault(impl.serialized<StreamService, String>(c.env))
        },
        clientJob = { c ->
            val stub = c.defaultChannel().toStub<StreamService, String>()
            val raw = stub.raw(Unit)
            val items = raw.toList()
            assertEquals(listOf(Update("r-1"), Update("r-2")), items)
            // Unlike Flow<T>, raw KsFlowService<T> does NOT auto-close after a
            // single collection — a second collect should still succeed.
            val again = raw.toList()
            assertEquals(listOf(Update("r-1"), Update("r-2")), again)
            raw.close()
        }
    )

    private fun executePipe(
        serviceJob: suspend (Connection<String>) -> Unit,
        clientJob: suspend (Connection<String>) -> Unit
    ) = runBlockingUnit {
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val serviceChannel = (si to output).asConnection(
            ksrpcEnvironment {
                errorListener = ErrorListener { }
            }
        )
        val clientChannel = (input to so).asConnection(
            ksrpcEnvironment {
                errorListener = ErrorListener { }
            }
        )
        val bgJob = GlobalScope.launch(Dispatchers.Default) {
            serviceJob(serviceChannel)
        }
        try {
            clientJob(clientChannel)
        } finally {
            try { clientChannel.close() } catch (_: Throwable) {}
            try { serviceChannel.close() } catch (_: Throwable) {}
            try { input.cancel(null) } catch (_: Throwable) {}
            try { si.cancel(null) } catch (_: Throwable) {}
            output.close(null)
            so.close(null)
            bgJob.join()
        }
    }
}
