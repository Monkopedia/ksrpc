/*
 * Copyright 2020 Jason Monk
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
import junit.framework.Assert.fail
import org.junit.Test

class RpcErrorTest {

    @Test
    fun testSerializePassthrough() = runBlockingUnit {
        val info = TestInterface.info
        val channel = info.createChannelFor(object : Service(), TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                throw IllegalArgumentException("Failure")
            }
        })
        val serializedChannel = channel.serialized(TestInterface)
        val stub = TestInterface.wrap(serializedChannel.deserialized())
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
        val info = TestInterface.info
        val channel = info.createChannelFor(object : Service(), TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                throw IllegalArgumentException("Failure")
            }
        })
        channel.servePipe(TestInterface) { client ->
            val stub = TestInterface.wrap(client.asChannel().deserialized())
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
                val info = TestInterface.info
                val channel = info.createChannelFor(object : Service(), TestInterface {
                    override suspend fun rpc(u: Pair<String, String>): String {
                        throw IllegalArgumentException("Failure")
                    }
                })
                val serializedChannel = channel.serialized(
                    TestInterface,
                    errorListener = {
                        it.printStackTrace()
                    }
                )
                serve(
                    path, serializedChannel,
                    errorListener = {
                        it.printStackTrace()
                    }
                )
            },
            test = {
                HttpClient().use { client ->
                    val stub =
                        TestInterface.wrap(
                            client.asChannel("http://localhost:8081$path").deserialized()
                        )
                    try {
                        stub.rpc("Hello" to "world")
                        fail("Expected crash")
                    } catch (t: Throwable) {
                        t.printStackTrace()
                        t as RpcException
                    }
                }
            }
        )
    }

    @Test
    fun testWebsocketPath() = runBlockingUnit {
        val path = "/rpc/"
        httpTest(
            serve = {
                val info = TestInterface.info
                val channel = info.createChannelFor(object : Service(), TestInterface {
                    override suspend fun rpc(u: Pair<String, String>): String {
                        throw IllegalArgumentException("Failure")
                    }
                })
                val serializedChannel = channel.serialized(
                    TestInterface,
                    errorListener = {
                        it.printStackTrace()
                    }
                )
                serveWebsocket(
                    path, serializedChannel,
                    errorListener = {
                        it.printStackTrace()
                    }
                )
            },
            test = {
                HttpClient {
                    install(WebSockets)
                }.use { client ->
                    TestInterface.wrap(
                        client.asWebsocketChannel("http://localhost:8081$path").deserialized()
                    )
                        .use { stub ->
                            try {
                                stub.rpc("Hello" to "world")
                                fail("Expected crash")
                            } catch (t: Throwable) {
                                t.printStackTrace()
                                t as RpcException
                            }
                        }
                }
            }
        )
    }
}
