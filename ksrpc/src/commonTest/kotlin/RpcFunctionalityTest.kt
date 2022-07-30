/*
 * Copyright 2021 Jason Monk
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
import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.channels.asConnection
import com.monkopedia.ksrpc.channels.asWebsocketConnection
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.ThreadSafeManager.threadSafe
import com.monkopedia.ksrpc.internal.asClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

abstract class RpcFunctionalityTest(
    private val supportedTypes: List<TestType> = TestType.values().toList(),
    private val serializedChannel: suspend () -> SerializedService,
    private val verifyOnChannel: suspend (SerializedService) -> Unit
) {
    enum class TestType {
        SERIALIZE,
        PIPE,
        HTTP,
        WEBSOCKET
    }

    @Test
    fun testSerializePassthrough() = runBlockingUnit {
        if (TestType.SERIALIZE !in supportedTypes) return@runBlockingUnit
        val serializedChannel = serializedChannel()
        val channel = threadSafe<Connection> {
            HostSerializedChannelImpl(ksrpcEnvironment { })
        }
        channel.registerDefault(serializedChannel)

        verifyOnChannel(channel.asClient.defaultChannel())
    }

    @Test
    fun testPipePassthrough() = runBlockingUnit {
        if (TestType.PIPE !in supportedTypes) return@runBlockingUnit
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        GlobalScope.launch(Dispatchers.Default) {
            val serializedChannel = serializedChannel()
            val connection = (si to output).asConnection(
                ksrpcEnvironment {
                    errorListener = ErrorListener {
                        it.printStackTrace()
                    }
                }
            )
            connection.registerDefault(serializedChannel)
        }
        try {
            verifyOnChannel((input to so).asConnection(ksrpcEnvironment { }).defaultChannel())
        } finally {
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
                val routing = testServe(
                    path,
                    serializedChannel,
                    ksrpcEnvironment {
                        errorListener = ErrorListener {
                            it.printStackTrace()
                        }
                    }
                )
                routing()
            },
            test = {
                val client = HttpClient()
                client.asConnection("http://localhost:$it$path", ksrpcEnvironment { })
                    .use { channel ->
                        verifyOnChannel(channel.defaultChannel())
                    }
            }
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
                    ksrpcEnvironment {
                        errorListener = ErrorListener {
                            it.printStackTrace()
                        }
                    }
                )
            },
            test = {
                val client = HttpClient {
                    install(WebSockets)
                }
                client.asWebsocketConnection("http://localhost:$it$path", ksrpcEnvironment { })
                    .use { channel ->
                        verifyOnChannel(channel.defaultChannel())
                    }
            }
        )
    }
}

internal expect fun runBlockingUnit(function: suspend () -> Unit)

expect class Routing

expect suspend inline fun httpTest(
    crossinline serve: suspend Routing.() -> Unit,
    test: suspend (Int) -> Unit
)

expect suspend fun testServe(
    basePath: String,
    channel: SerializedService,
    env: KsrpcEnvironment = ksrpcEnvironment { }
): Routing.() -> Unit

expect fun Routing.testServeWebsocket(
    basePath: String,
    channel: SerializedService,
    env: KsrpcEnvironment = ksrpcEnvironment { }
)

expect fun createPipe(): Pair<ByteWriteChannel, ByteReadChannel>
