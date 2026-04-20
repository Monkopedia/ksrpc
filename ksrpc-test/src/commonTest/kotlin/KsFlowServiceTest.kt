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

import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.flow.AutoClosingFlow
import com.monkopedia.ksrpc.flow.KsCollectionToken
import com.monkopedia.ksrpc.flow.KsFlowCollector
import com.monkopedia.ksrpc.flow.KsFlowService
import com.monkopedia.ksrpc.flow.KsFlowServiceObject
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.serializer

/**
 * Tests for the KsFlowService sub-service protocol using manual wiring
 * (no compiler plugin needed).
 */
class KsFlowServiceTest {

    private val flowServiceObject = KsFlowServiceObject(String.serializer())

    private fun <T : RpcService> T.manualSerialized(
        rpcObject: RpcObject<T>,
        env: KsrpcEnvironment<String>
    ) = serialized(rpcObject, env)

    /**
     * Protocol test: Items flow from server to client through the sub-service
     * protocol over a bidirectional pipe.
     */
    @Test
    fun testBasicFlowCollection() = executePipe(
        serviceJob = { c ->
            val flowService = flow {
                emit("a")
                emit("b")
                emit("c")
            }.asKsFlow()
            c.registerDefault(
                flowService.manualSerialized(flowServiceObject, c.env)
            )
        },
        clientJob = { c ->
            val stub = flowServiceObject.createStub(c.defaultChannel())
            val items = stub.toList()
            assertEquals(listOf("a", "b", "c"), items)
        }
    )

    /**
     * Auto-close test: Flow<T> wrapper closes the sub-service after collection.
     */
    @Test
    fun testAutoClosingFlow() = executePipe(
        serviceJob = { c ->
            val flowService = flow {
                emit("x")
                emit("y")
            }.asKsFlow()
            c.registerDefault(
                flowService.manualSerialized(flowServiceObject, c.env)
            )
        },
        clientJob = { c ->
            val stub = flowServiceObject.createStub(c.defaultChannel())
            val autoClosing = AutoClosingFlow(stub)
            val items = autoClosing.toList()
            assertEquals(listOf("x", "y"), items)
        }
    )

    /**
     * Multi-collection test: KsFlowService allows multiple sequential
     * collections.
     */
    @Test
    fun testMultipleCollections() = executePipe(
        serviceJob = { c ->
            val flowService = object : KsFlowService<String> {
                private var counter = 0
                override suspend fun startCollection(
                    collector: KsFlowCollector<String>
                ): KsCollectionToken {
                    val batch = counter++
                    return flow {
                        emit("batch$batch-1")
                        emit("batch$batch-2")
                    }.asKsFlow().startCollection(collector)
                }

                override suspend fun collect(
                    collector: kotlinx.coroutines.flow.FlowCollector<String>
                ) {
                    error("Server should not use collect directly")
                }
            }
            c.registerDefault(
                flowService.manualSerialized(flowServiceObject, c.env)
            )
        },
        clientJob = { c ->
            val stub = flowServiceObject.createStub(c.defaultChannel())
            val first = stub.toList()
            assertEquals(listOf("batch0-1", "batch0-2"), first)
            val second = stub.toList()
            assertEquals(listOf("batch1-1", "batch1-2"), second)
        }
    )

    /**
     * Error propagation: Server-side flow throws, onError fires, client-side
     * collect throws.
     */
    @Test
    fun testErrorPropagation() = executePipe(
        serviceJob = { c ->
            val flowService = flow<String> {
                emit("ok")
                throw RuntimeException("test error from server")
            }.asKsFlow()
            c.registerDefault(
                flowService.manualSerialized(flowServiceObject, c.env)
            )
        },
        clientJob = { c ->
            val stub = flowServiceObject.createStub(c.defaultChannel())
            val items = mutableListOf<String>()
            val exception = assertFailsWith<RpcException> {
                stub.collect { item ->
                    items.add(item)
                }
            }
            assertEquals(listOf("ok"), items)
            assertTrue(exception.message.contains("test error from server"))
        }
    )

    /**
     * Cancellation: Client cancels collection, cancelCollection fires,
     * server-side collection job cancelled.
     */
    @Test
    fun testCancellation() = executePipe(
        serviceJob = { c ->
            val serverCancelled = CompletableDeferred<Boolean>()
            val flowService = flow<String> {
                try {
                    emit("1")
                    emit("2")
                    delay(10_000)
                    emit("3")
                } catch (e: kotlinx.coroutines.CancellationException) {
                    serverCancelled.complete(true)
                    throw e
                }
            }.asKsFlow()
            c.registerDefault(
                flowService.manualSerialized(flowServiceObject, c.env)
            )
            withTimeout(5000) {
                assertTrue(serverCancelled.await())
            }
        },
        clientJob = { c ->
            val stub = flowServiceObject.createStub(c.defaultChannel())
            val items = mutableListOf<String>()
            val completion = CompletableDeferred<Unit>()
            val collectorImpl = object : KsFlowCollector<String> {
                override suspend fun onItem(item: String) {
                    items.add(item)
                }

                override suspend fun onError(error: RpcFailure) {
                    completion.completeExceptionally(error.toException())
                }

                override suspend fun onComplete(u: Unit) {
                    completion.complete(Unit)
                }
            }
            val token = stub.startCollection(collectorImpl)
            delay(500)
            token.cancelCollection(Unit)
            assertEquals(listOf("1", "2"), items)
        }
    )

    /**
     * Back-pressure: Server's onItem blocks until client processes (since it's
     * a suspend call through sub-service).
     */
    @Test
    fun testBackPressure() = executePipe(
        serviceJob = { c ->
            val flowService = flow {
                emit("fast-1")
                emit("fast-2")
                emit("fast-3")
            }.asKsFlow()
            c.registerDefault(
                flowService.manualSerialized(flowServiceObject, c.env)
            )
        },
        clientJob = { c ->
            val stub = flowServiceObject.createStub(c.defaultChannel())
            val items = mutableListOf<String>()
            val processOrder = mutableListOf<String>()
            stub.collect { item ->
                processOrder.add("start-$item")
                delay(100)
                processOrder.add("end-$item")
                items.add(item)
            }
            assertEquals(listOf("fast-1", "fast-2", "fast-3"), items)
            assertEquals(
                listOf(
                    "start-fast-1", "end-fast-1",
                    "start-fast-2", "end-fast-2",
                    "start-fast-3", "end-fast-3"
                ),
                processOrder
            )
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
