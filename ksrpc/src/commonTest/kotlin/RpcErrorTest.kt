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
import kotlin.test.fail

class RpcErrorTest {

    @Test
    fun testSerializePassthrough() = runBlockingUnit {
        val info = TestInterface
        val channel = object : TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                throw IllegalArgumentException("Failure")
            }
        }
        val serializedChannel = channel.serialized(TestInterface)
        val stub = TestInterface.createStub(serializedChannel)
        try {
            stub.rpc("Hello" to "world")
            fail("Expected crash")
        } catch (t: Throwable) {
            t.printStackTrace()
            t as RpcException
        }
    }

    @Test
    fun testPipePassthrough() = runBlockingUnit {
        val channel = object : TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                throw IllegalArgumentException("Failure")
            }
        }
        channel.servePipe(TestInterface) { client ->
            val stub = TestInterface.createStub(client.asChannel())
            try {
                stub.rpc("Hello" to "world")
                fail("Expected crash")
            } catch (t: Throwable) {
                t.printStackTrace()
                t as RpcException
            }
        }
    }

    @Test
    fun testHttpPath() = runBlockingUnit {
        val path = "/rpc/"
        httpTest(
            serve = {
                val channel = object : TestInterface {
                    override suspend fun rpc(u: Pair<String, String>): String {
                        throw IllegalArgumentException("Failure")
                    }
                }
                val serializedChannel = channel.serialized(
                    TestInterface,
                    errorListener = {
                        it.printStackTrace()
                    }
                )
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
                val stub = TestInterface.createStub(client.asChannel("http://localhost:$it$path"))
                try {
                    stub.rpc("Hello" to "world")
                    fail("Expected crash")
                } catch (t: Throwable) {
                    t.printStackTrace()
                    t as RpcException
                }
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
                        throw IllegalArgumentException("Failure")
                    }
                }
                val serializedChannel = channel.serialized(
                    TestInterface,
                    errorListener = {
                        it.printStackTrace()
                    }
                )
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
                try {
                    client.asWebsocketChannel("http://localhost:$it$path").use { channel ->
                        val stub = TestInterface.createStub(channel)
                        try {
                            stub.rpc("Hello" to "world")
                            fail("Expected crash")
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            t as RpcException
                        }
                    }
                } finally {
                    client.close()
                }
            }
        )
    }
}
