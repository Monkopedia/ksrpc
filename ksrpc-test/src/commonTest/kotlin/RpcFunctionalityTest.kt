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
import com.monkopedia.ksrpc.channels.Connection
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.asClient
import com.monkopedia.ksrpc.ktor.asHttpChannelClient
import com.monkopedia.ksrpc.ktor.websocket.asWebsocketConnection
import com.monkopedia.ksrpc.sockets.asConnection
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class RpcFunctionalityTest(
    supportedTypes: List<TestType> = TestType.values().toList(),
    private val serializedChannel: suspend CoroutineScope.() -> SerializedService<String>,
    private val verifyOnChannel: suspend CoroutineScope.(SerializedService<String>) -> Unit,
    private val workerServiceName: String? = null,
    /**
     * Optional HTTP-only verification that additionally receives a [reconnect] factory producing a
     * fresh, independent channel to the same server. [RpcServiceCancelTest] uses this to issue the
     * call it cancels mid-flight on a throwaway connection: the native ktor-curl engine wedges its
     * shared processor when an in-flight request is cancelled, so the cancelled call must not share
     * a client with the call that verifies the channel still works afterwards. When null, the HTTP
     * path falls back to [verifyOnChannel].
     */
    private val verifyOnHttpChannel: (
        suspend CoroutineScope.(
            channel: SerializedService<String>,
            reconnect: suspend () -> SerializedService<String>
        ) -> Unit
    )? = null
) {
    private val supportedTypes = run {
        val effective = supportedTypes.toSet() intersect platformSupportedTestTypes()
        if (workerServiceName == null) effective - TestType.SERVICE_WORKER else effective
    }

    enum class TestType {
        SERIALIZE,
        PIPE,
        HTTP,
        WEBSOCKET,
        SERVICE_WORKER
    }

    @Test
    fun testSerializePassthrough() = runBlockingUnit {
        if (TestType.SERIALIZE !in supportedTypes) return@runBlockingUnit
        val serializedChannel = serializedChannel()
        val channel = HostSerializedChannelImpl(createEnv())
        try {
            channel.registerDefault(serializedChannel)
            verifyOnChannel(channel.asClient.defaultChannel())
        } finally {
            try {
                channel.close()
            } catch (t: Throwable) {
            }
        }
    }

    @Test
    fun testPipePassthrough() = runBlockingUnit {
        if (TestType.PIPE !in supportedTypes) return@runBlockingUnit
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val serverConnection = (si to output).asConnection(createEnv())
        val clientConnection = (input to so).asConnection(createEnv())
        val serverJob = launch(Dispatchers.Default) {
            val serializedChannel = serializedChannel()
            serverConnection.registerDefault(serializedChannel)
        }
        try {
            verifyOnChannel(clientConnection.defaultChannel())
        } finally {
            try {
                clientConnection.close()
            } catch (t: Throwable) {
            }
            try {
                serverConnection.close()
            } catch (t: Throwable) {
            }
            try {
                serverJob.cancel()
            } catch (t: Throwable) {
            }
            try {
                serverJob.join()
            } catch (t: Throwable) {
            }
            try {
                input.cancel(null)
            } catch (t: Throwable) {
            }
            try {
                si.cancel(null)
            } catch (t: Throwable) {
            }
            output.close(null)
            so.close(null)
        }
    }

    @Test
    fun testHttpPath() = runBlockingUnit {
        if (TestType.HTTP !in supportedTypes) return@runBlockingUnit
        val path = "/rpc/"
        httpTest(
            serve = {
                val serializedChannel = serializedChannel()
                testServe(
                    path,
                    serializedChannel,
                    createEnv()
                )
            },
            test = { port ->
                val url = "http://localhost:$port$path"
                val extraClients = mutableListOf<HttpClient>()
                val reconnect: suspend () -> SerializedService<String> = {
                    val extraClient = httpTestClient()
                    extraClients.add(extraClient)
                    extraClient.asHttpChannelClient(url, createEnv()).defaultChannel()
                }
                val client = httpTestClient()
                try {
                    client.asHttpChannelClient(url, createEnv())
                        .use { channel ->
                            val httpVerify = verifyOnHttpChannel
                            if (httpVerify != null) {
                                httpVerify(channel.defaultChannel(), reconnect)
                            } else {
                                verifyOnChannel(channel.defaultChannel())
                            }
                        }
                } finally {
                    extraClients.forEach { it.close() }
                    client.close()
                }
            },
            isWebsocket = false
        )
    }

    @Test
    fun testWebsocketPath() = runBlockingUnit {
        if (TestType.WEBSOCKET !in supportedTypes) return@runBlockingUnit
        val path = "/rpc/"
        httpTest(
            serve = {
                val serializedChannel = serializedChannel()
                testServeWebsocket(
                    path,
                    serializedChannel,
                    createEnv()
                )
            },
            test = {
                val client = HttpClient {
                    install(WebSockets)
                }
                try {
                    client.asWebsocketConnection("http://localhost:$it$path", createEnv())
                        .use { channel ->
                            verifyOnChannel(channel.defaultChannel())
                        }
                } finally {
                    client.close()
                }
            },
            isWebsocket = true
        )
    }

    @Test
    fun testServiceWorkerPassthrough() = runBlockingUnit {
        if (TestType.SERVICE_WORKER !in supportedTypes) return@runBlockingUnit
        serviceWorkerTest(workerServiceName) { connection ->
            verifyOnChannel(connection.defaultChannel())
        }
    }

    protected open fun createEnv() = ksrpcEnvironment { }
}

expect class RunBlockingReturn
internal expect fun runBlockingUnit(function: suspend CoroutineScope.() -> Unit): RunBlockingReturn

expect interface Routing

internal expect fun platformSupportedTestTypes(): Set<RpcFunctionalityTest.TestType>

/**
 * The [HttpClient] used by [RpcFunctionalityTest.testHttpPath] (one per channel created in the
 * test). Provided per-platform so each target builds its default engine, and so the HTTP reconnect
 * factory can produce additional independent clients (see [RpcServiceCancelTest]).
 */
internal expect fun httpTestClient(): HttpClient

internal expect suspend fun serviceWorkerTest(
    serviceName: String?,
    test: suspend (Connection<String>) -> Unit
)

expect suspend inline fun httpTest(
    crossinline serve: suspend Routing.() -> Unit,
    test: suspend (Int) -> Unit,
    isWebsocket: Boolean
)

expect suspend fun Routing.testServe(
    basePath: String,
    channel: SerializedService<String>,
    env: KsrpcEnvironment<String> = ksrpcEnvironment { }
)

expect fun Routing.testServeWebsocket(
    basePath: String,
    channel: SerializedService<String>,
    env: KsrpcEnvironment<String> = ksrpcEnvironment { }
)

expect fun createPipe(): Pair<ByteWriteChannel, ByteReadChannel>
