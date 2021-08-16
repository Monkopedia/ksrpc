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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

@KsService
interface TestSubInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String
}

@KsService
interface TestRootInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String

    @KsMethod("/service")
    suspend fun subservice(prefix: String): TestSubInterface
}

class RpcSubserviceTest {

    @Test
    fun testCreateInfo() = runBlockingUnit {
        val info = TestRootInterface
        assertNotNull(info.findEndpoint("rpc"))
    }

    @Test
    fun testSerializePassthrough() = runBlockingUnit {
        val channel = basicImpl
        val serializedChannel = channel.serialized(TestRootInterface)
        val stub = TestRootInterface.createStub(serializedChannel)
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").rpc("Hello" to "world")
        )
    }

    @Test
    fun testSerializePassthrough_twoCalls() = runBlockingUnit {
        val channel = basicImpl
        val serializedChannel = channel.serialized(TestRootInterface)
        val stub = TestRootInterface.createStub(serializedChannel)
        stub.subservice("oh,").rpc("Hello" to "world")
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").rpc("Hello" to "world")
        )
    }

    @Test
    fun testPipePassthrough() = runBlockingUnit {
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val channel = basicImpl
        GlobalScope.launch(Dispatchers.Default) {
            val serializedChannel = channel.serialized(TestRootInterface)
            serializedChannel.serve(si, output)
        }
        val stub = TestRootInterface.createStub((input to so).asChannel())
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").rpc("Hello" to "world")
        )
    }

    @Test
    fun testPipePassthrough_twoCalls() = runBlockingUnit {
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val channel = basicImpl
        GlobalScope.launch(Dispatchers.Default) {
            val serializedChannel = channel.serialized(TestRootInterface)
            try {
                serializedChannel.serve(si, output)
            } catch (t: Throwable) {
                t.printStackTrace()
                fail("Exception")
            }
        }
        val stub = TestRootInterface.createStub((input to so).asChannel())
        val ohService = stub.subservice("oh,")
        stub.subservice("!!!").rpc("Hello" to "world")
        assertEquals(
            "oh, Hello world",
            ohService.rpc("Hello" to "world")
        )
    }

    @Test
    fun testHttpPath() = runBlockingUnit {
        val path = "/rpc/"
        httpTest(
            serve = {
                val channel = basicImpl
                val serializedChannel = channel.serialized(TestRootInterface)
                testServe(path, serializedChannel)
            },
            test = {
                val client = HttpClient()
                val stub = TestRootInterface.createStub(
                    client.asChannel("http://localhost:$it$path")
                )
                assertEquals(
                    "oh, Hello world",
                    stub.subservice("oh,").rpc("Hello" to "world")
                )
            }
        )
    }

    @Test
    fun testWebsocketPath() = runBlockingUnit {
        val path = "/rpc/"
        httpTest(
            serve = {
                val channel = basicImpl
                val serializedChannel = channel.serialized(TestRootInterface)
                testServeWebsocket(path, serializedChannel)
            },
            test = {
                val client = HttpClient {
                    install(WebSockets)
                }
                val stub = TestRootInterface.createStub(
                    client.asWebsocketChannel("http://localhost:$it$path")
                )
                assertEquals(
                    "oh, Hello world",
                    stub.subservice("oh,").rpc("Hello" to "world")
                )
            }
        )
    }

    val basicImpl: TestRootInterface
        get() = object : TestRootInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }

            override suspend fun subservice(prefix: String): TestSubInterface {
                return object : TestSubInterface {
                    override suspend fun rpc(u: Pair<String, String>): String {
                        return "$prefix ${u.first} ${u.second}"
                    }
                }
            }
        }
}
