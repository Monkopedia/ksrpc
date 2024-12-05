/**
 * Copyright (C) 2024 Jason Monk <monkopedia@gmail.com>
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

import com.monkopedia.ksrpc.channels.SerializedService
import com.monkopedia.ksrpc.internal.HostSerializedChannelImpl
import com.monkopedia.ksrpc.internal.asClient
import com.monkopedia.ksrpc.ktor.asConnection
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
    private val supportedTypes: List<TestType> = TestType.values().toList(),
    private val serializedChannel: suspend CoroutineScope.() -> SerializedService<String>,
    private val verifyOnChannel: suspend CoroutineScope.(SerializedService<String>) -> Unit
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
        val channel = HostSerializedChannelImpl(createEnv())
        channel.registerDefault(serializedChannel)

        verifyOnChannel(channel.asClient.defaultChannel())
    }

    @Test
    fun testPipePassthrough() = runBlockingUnit {
        if (TestType.PIPE !in supportedTypes) return@runBlockingUnit
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        launch(Dispatchers.Default) {
            val serializedChannel = serializedChannel()
            val connection = (si to output).asConnection(
                createEnv()
            )
            connection.registerDefault(serializedChannel)
        }
        try {
            verifyOnChannel((input to so).asConnection(createEnv()).defaultChannel())
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
                testServe(
                    path,
                    serializedChannel,
                    createEnv()
                )
            },
            test = {
                val client = HttpClient()
                client.asConnection("http://localhost:$it$path", createEnv())
                    .use { channel ->
                        verifyOnChannel(channel.defaultChannel())
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
                client.asWebsocketConnection("http://localhost:$it$path", createEnv())
                    .use { channel ->
                        verifyOnChannel(channel.defaultChannel())
                    }
            },
            isWebsocket = true
        )
    }

    protected open fun createEnv() = ksrpcEnvironment { }
}

expect class RunBlockingReturn
internal expect fun runBlockingUnit(function: suspend CoroutineScope.() -> Unit): RunBlockingReturn

expect interface Routing

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
