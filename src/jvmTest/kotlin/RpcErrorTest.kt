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
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import junit.framework.Assert.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val info = TestInterface.info
        val channel = info.createChannelFor(object : Service(), TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                throw IllegalArgumentException("Failure")
            }
        })
        val serializedChannel = channel.serialized(TestInterface)
        GlobalScope.launch(Dispatchers.IO) {
            serializedChannel.serve(si, output)
        }
        val stub = TestInterface.wrap((input to so).asChannel().deserialized())
        try {
            stub.rpc("Hello" to "world")
            fail("Expected crash")
        } catch (t: Throwable) {
            t.printStackTrace()
            t as RpcException
        }
    }

    @Test
    fun testHttpPath() = runBlockingUnit {
        val info = TestInterface.info
        val channel = info.createChannelFor(object : Service(), TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                throw IllegalArgumentException("Failure")
            }
        })
        val path = "/rpc/"
        val serializedChannel = channel.serialized(
            TestInterface,
            errorListener = {
                it.printStackTrace()
            }
        )
        lateinit var server: ApplicationEngine
        GlobalScope.launch(Dispatchers.IO) {
            server = embeddedServer(Netty, 8080) {
                routing {
                    serve(
                        path, serializedChannel,
                        errorListener = {
                            it.printStackTrace()
                        }
                    )
                }
            }.start()
        }
        val client = HttpClient()
        val stub = TestInterface.wrap(client.asChannel("http://localhost:8080$path").deserialized())
        try {
            stub.rpc("Hello" to "world")
            fail("Expected crash")
        } catch (t: Throwable) {
            t.printStackTrace()
            t as RpcException
        }
    }

    fun RpcChannel.servePipe(): Pair<InputStream, OutputStream> {
        val serializedChannel = serialized(TestTypesInterface)
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        GlobalScope.launch(Dispatchers.IO) {
            serializedChannel.serve(si, output)
        }
        return input to so
    }

    private fun createPipe(): Pair<OutputStream, InputStream> {
        return PipedInputStream().let { PipedOutputStream(it) to it }
    }
}
