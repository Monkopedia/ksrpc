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
import io.ktor.utils.io.ByteReadChannel
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.CountDownLatch
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Test

interface TestInterface : RpcService {
    suspend fun rpc(u: Pair<String, String>): String = map("/rpc", u)

    class TestInterfaceStub(private val channel: RpcServiceChannel) :
        TestInterface, RpcService by channel

    companion object : RpcObject<TestInterface>(TestInterface::class, ::TestInterfaceStub)
}

class RpcServiceTest {
    @Test
    fun testCreateInfo() = runBlockingUnit {
        val info = TestInterface.info
        assertNotNull(info.findEndpoint("rpc"))
    }

    @Test
    fun testCreateStub() = runBlockingUnit {
        val info = TestInterface.info
        val stub = info.createStubFor(object : Service(), RpcChannel {
            override suspend fun <I, O> call(
                str: String,
                inputSer: KSerializer<I>,
                outputSer: KSerializer<O>,
                input: I
            ): O {
                error("Not implemented")
            }

            override suspend fun <I> callBinary(endpoint: String, inputSer: KSerializer<I>, input: I): ByteReadChannel {
                error("Not implemented")
            }

            override suspend fun <O> callBinaryInput(
                endpoint: String,
                outputSer: KSerializer<O>,
                input: ByteReadChannel
            ): O {
                error("Not implemented")
            }

            override suspend fun <I, O : RpcService> callService(
                endpoint: String,
                service: RpcObject<O>,
                inputSer: KSerializer<I>,
                input: I
            ): O {
                error("Not implemented")
            }
        })
        assertNotNull(stub)
    }

    @Test
    fun testCallStub() = runBlockingUnit {
        val info = TestInterface.info
        val stub = info.createStubFor(object : Service(), RpcChannel {
            override suspend fun <I, O> call(
                str: String,
                inputSer: KSerializer<I>,
                outputSer: KSerializer<O>,
                input: I
            ): O {
                if (input is Pair<*, *>) {
                    return "${input.first} ${input.second}" as O
                }
                error("Not implemented")
            }

            override suspend fun <I> callBinary(endpoint: String, inputSer: KSerializer<I>, input: I): ByteReadChannel {
                error("Not implemented")
            }

            override suspend fun <O> callBinaryInput(
                endpoint: String,
                outputSer: KSerializer<O>,
                input: ByteReadChannel
            ): O {
                error("Not implemented")
            }

            override suspend fun <I, O : RpcService> callService(
                endpoint: String,
                service: RpcObject<O>,
                inputSer: KSerializer<I>,
                input: I
            ): O {
                error("Not implemented")
            }
        })
        assertEquals("Hello world", stub.rpc("Hello" to "world"))
    }

    @Test
    fun testCreateChannel() = runBlockingUnit {
        val info = TestInterface.info
        val channel = info.createChannelFor(object : Service(), TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        })
        assertEquals(
            "Hello world",
            channel.call(
                "rpc",
                PairSerializer(String.serializer(), String.serializer()),
                String.serializer(),
                "Hello" to "world"
            )
        )
    }

    @Test
    fun testPassthrough() = runBlockingUnit {
        val info = TestInterface.info
        val channel = info.createChannelFor(object : Service(), TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        })
        val stub = TestInterface.wrap(channel)
        assertEquals(
            "Hello world",
            stub.rpc("Hello" to "world")
        )
    }

    @Test
    fun testSerializePassthrough() = runBlockingUnit {
        val info = TestInterface.info
        val channel = info.createChannelFor(object : Service(), TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        })
        val serializedChannel = channel.serialized(TestInterface)
        val stub = TestInterface.wrap(serializedChannel.deserialized())
        assertEquals(
            "Hello world",
            stub.rpc("Hello" to "world")
        )
    }

    @Test
    fun testSerializePassthrough_twoCalls() = runBlockingUnit {
        val info = TestInterface.info
        val channel = info.createChannelFor(object : Service(), TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        })
        val serializedChannel = channel.serialized(TestInterface)
        val stub = TestInterface.wrap(serializedChannel.deserialized())
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
        val info = TestInterface.info
        val channel = info.createChannelFor(object : Service(), TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        })
        val serializedChannel = channel.serialized(TestInterface)
        GlobalScope.launch(Dispatchers.IO) {
            serializedChannel.serve(si, output)
        }
        val stub = TestInterface.wrap((input to so).asChannel().deserialized())
        assertEquals(
            "Hello world",
            stub.rpc("Hello" to "world")
        )
    }

    @Test
    fun testPipePassthrough_twoCalls() = runBlockingUnit {
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val info = TestInterface.info
        val channel = info.createChannelFor(object : Service(), TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        })
        val serializedChannel = channel.serialized(TestInterface)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                serializedChannel.serve(si, output)
            } catch (t: Throwable) {
                t.printStackTrace()
                fail("Exception")
            }
        }
        val stub = TestInterface.wrap((input to so).asChannel().deserialized())
        stub.rpc("Hello" to "world")
        assertEquals(
            "Hello world",
            stub.rpc("Hello" to "world")
        )
    }

    @Test
    fun testHttpPath() = runBlockingUnit {
        val info = TestInterface.info
        val channel = info.createChannelFor(object : Service(), TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        })
        val path = "/rpc/"
        val serializedChannel = channel.serialized(TestInterface)
        lateinit var server: ApplicationEngine
        GlobalScope.launch(Dispatchers.IO) {
            server = embeddedServer(Netty, 8081) {
                routing {
                    serve(path, serializedChannel)
                }
            }.start()
        }
        val client = HttpClient()
        val stub = TestInterface.wrap(client.asChannel("http://localhost:8081$path").deserialized())
        assertEquals(
            "Hello world",
            stub.rpc("Hello" to "world")
        )
        GlobalScope.launch(Dispatchers.IO) {
            server.stop(10000, 10000)
        }
    }

    private fun createPipe(): Pair<OutputStream, InputStream> {
        return PipedInputStream().let { PipedOutputStream(it) to it }
    }
}

internal fun runBlockingUnit(function: suspend () -> Unit) {
    val block = CountDownLatch(1)
    val executor = newSingleThreadContext("Test thread")
    var exc: Throwable? = null
    GlobalScope.launch(executor) {
        try {
            function()
        } catch (t: Throwable) {
            exc = t
        } finally {
            block.countDown()
        }
    }
    block.await()
    exc?.let { throw it }
}
