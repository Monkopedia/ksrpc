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

import io.ktor.client.HttpClient
import io.ktor.client.features.websocket.WebSockets
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

abstract class RpcFunctionalityTest(
    private val supportedTypes: List<TestType> = TestType.values().toList(),
    private val serializedChannel: suspend () -> SerializedChannel,
    private val verifyOnChannel: suspend (SerializedChannel) -> Unit
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
        verifyOnChannel(serializedChannel)
    }

    @Test
    fun testPipePassthrough() = runBlockingUnit {
        if (TestType.PIPE !in supportedTypes) return@runBlockingUnit
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        GlobalScope.launch(Dispatchers.Default) {
            val serializedChannel = serializedChannel()
            serializedChannel.serve(
                si,
                output,
                errorListener = {
                    it.printStackTrace()
                }
            )
        }
        try {
            verifyOnChannel((input to so).asChannel())
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
                    errorListener = {
                        it.printStackTrace()
                    }
                )
            },
            test = {
                val client = HttpClient()
                client.asChannel("http://localhost:$it$path").use { channel ->
                    verifyOnChannel(channel)
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
                    errorListener = {
                        it.printStackTrace()
                    }
                )
            },
            test = {
                val client = HttpClient {
                    install(WebSockets)
                }
                client.asWebsocketChannel("http://localhost:$it$path").use { channel ->
                    verifyOnChannel(channel)
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
expect fun Routing.testServe(
    basePath: String,
    channel: SerializedChannel,
    errorListener: ErrorListener = ErrorListener { }
)
expect fun Routing.testServeWebsocket(
    basePath: String,
    channel: SerializedChannel,
    errorListener: ErrorListener = ErrorListener { }
)

suspend inline fun <reified T : RpcService> T.servePipe(
    test: suspend (Pair<ByteReadChannel, ByteWriteChannel>) -> Unit
) {
}

fun createPipe(): Pair<ByteWriteChannel, ByteReadChannel> {
    val channel = ByteChannel(autoFlush = true)
    return channel to channel
}
