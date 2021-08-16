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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@KsService
interface TestInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String
}

class RpcServiceTest {
    @Test
    fun testCreateInfo() = runBlockingUnit {
        val info = TestInterface
        assertNotNull(info.findEndpoint("rpc"))
    }

    @Test
    fun testCreateStub() = runBlockingUnit {
        val info = TestInterface
        val stub = info.createStub(object : SerializedChannel {

            override val serialization: StringFormat
                get() = error("Not implemented")

            override suspend fun call(endpoint: String, input: CallData): CallData {
                error("Not implemented")
            }

            override suspend fun close() {
                error("Not implemented")
            }
        })
        assertNotNull(stub)
    }

    @Test
    fun testCallStub() = runBlockingUnit {
        val info = TestInterface
        val stub = info.createStub(object : SerializedChannel {

            override val serialization: StringFormat
                get() = Json

            override suspend fun call(endpoint: String, input: CallData): CallData {
                val input = Json.decodeFromString(
                    PairSerializer(String.serializer(), String.serializer()),
                    input.readSerialized()
                )
                return CallData.create(
                    Json.encodeToString(String.serializer(), "${input.first} ${input.second}")
                )
            }

            override suspend fun close() {
                error("Not implemented")
            }
        })
        assertEquals("Hello world", stub.rpc("Hello" to "world"))
    }

    @Test
    fun testCreateChannel() = runBlockingUnit {
        val info = TestInterface
        val channel = object : TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        }.serialized(TestInterface)
        assertEquals(
            "Hello world",
            Json.decodeFromString(
                String.serializer(),
                channel.call(
                    "rpc",
                    CallData.create(
                        Json.encodeToString(
                            PairSerializer(String.serializer(), String.serializer()),
                            "Hello" to "world"
                        )
                    )
                ).readSerialized()
            )
        )
    }

    @Test
    fun testSerializePassthrough() = runBlockingUnit {
        val info = TestInterface
        val channel = object : TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        }
        val serializedChannel = channel.serialized(TestInterface)
        val stub = TestInterface.createStub(serializedChannel)
        assertEquals(
            "Hello world",
            stub.rpc("Hello" to "world")
        )
    }

    @Test
    fun testSerializePassthrough_twoCalls() = runBlockingUnit {
        val info = TestInterface
        val channel = object : TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        }
        val serializedChannel = channel.serialized(TestInterface)
        val stub = TestInterface.createStub(serializedChannel)
        stub.rpc("Hello" to "world")
        assertEquals(
            "Hello world",
            stub.rpc("Hello" to "world")
        )
    }

    @Test
    fun testPipePassthrough() = runBlockingUnit {
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val info = TestInterface
        val channel = object : TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        }
        val serializedChannel = channel.serialized(TestInterface)
        GlobalScope.launch(Dispatchers.Default) {
            serializedChannel.serve(si, output)
        }
        val stub = TestInterface.createStub((input to so).asChannel())
        assertEquals(
            "Hello world",
            stub.rpc("Hello" to "world")
        )
    }

    @Test
    fun testPipePassthrough_twoCalls() = runBlockingUnit {
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val channel = object : TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        }
        val serializedChannel = channel.serialized(TestInterface)
        GlobalScope.launch(Dispatchers.Default) {
            try {
                serializedChannel.serve(si, output)
            } catch (t: Throwable) {
                t.printStackTrace()
                fail("Exception")
            }
        }
        val stub = TestInterface.createStub((input to so).asChannel())
        stub.rpc("Hello" to "world")
        assertEquals(
            "Hello world",
            stub.rpc("Hello" to "world")
        )
    }

    @Test
    fun testHttpPath() = runBlockingUnit {
        val path = "/rpc/"
        httpTest(
            serve = {
                val info = TestInterface
                val channel = object : TestInterface {
                    override suspend fun rpc(u: Pair<String, String>): String {
                        return "${u.first} ${u.second}"
                    }
                }
                val serializedChannel = channel.serialized(TestInterface)

                testServe(path, serializedChannel)
            },
            test = {
                val client = HttpClient()
                val stub = TestInterface.createStub(client.asChannel("http://localhost:$it$path"))
                assertEquals(
                    "Hello world",
                    stub.rpc("Hello" to "world")
                )
            }
        )
    }

    @Test
    fun testWebsocketPath() = runBlockingUnit {
        val path = "/rpc/"
        httpTest(
            serve = {
                val info = TestInterface
                val channel = object : TestInterface {
                    override suspend fun rpc(u: Pair<String, String>): String {
                        return "${u.first} ${u.second}"
                    }
                }
                val serializedChannel = channel.serialized(TestInterface)

                testServeWebsocket(path, serializedChannel)
            },
            test = {
                val client = HttpClient {
                    install(WebSockets)
                }
                val stub = TestInterface.createStub(
                    client.asWebsocketChannel("http://localhost:$it$path")
                )
                assertEquals(
                    "Hello world",
                    stub.rpc("Hello" to "world")
                )
            }
        )
    }
}

suspend inline fun <T : RpcService> T.servePipe(
    obj: RpcObject<T>,
    test: suspend (Pair<ByteReadChannel, ByteWriteChannel>) -> Unit
) {
    val serializedChannel = serialized(obj)
    val (output, input) = createPipe()
    val (so, si) = createPipe()
    GlobalScope.launch(Dispatchers.Default) {
        serializedChannel.serve(
            si,
            output,
            errorListener = {
                it.printStackTrace()
            }
        )
    }
    try {
        test(input to so)
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

fun createPipe(): Pair<ByteWriteChannel, ByteReadChannel> {
    val channel = ByteChannel(autoFlush = true)
    return channel to channel
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
