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
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.fail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Test

interface TestSubInterface : RpcService {
    suspend fun rpc(u: Pair<String, String>): String = map("/rpc", u)

    class TestSubInterfaceStub(private val channel: RpcServiceChannel) :
        TestSubInterface, RpcService by channel

    companion object : RpcObject<TestSubInterface>(TestSubInterface::class, ::TestSubInterfaceStub)
}

interface TestRootInterface : RpcService {
    suspend fun rpc(u: Pair<String, String>): String = map("/rpc", u)
    suspend fun subservice(prefix: String) = service("/service", TestSubInterface, prefix)

    class TestRootInterfaceStub(private val channel: RpcServiceChannel) :
        TestRootInterface, RpcService by channel

    companion object : RpcObject<TestRootInterface>(
        TestRootInterface::class,
        ::TestRootInterfaceStub
    )
}

class RpcSubserviceTest {

    internal val info = TestRootInterface.info

    @Test
    fun testCreateInfo() = runBlockingUnit {
        val info = TestRootInterface.info
        assertNotNull(info.findEndpoint("rpc"))
    }

    @Test
    fun testPassthrough() = runBlockingUnit {
        val channel = info.createChannelFor(basicImpl)
        val stub = TestRootInterface.wrap(channel)
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").rpc("Hello" to "world")
        )
    }

    @Test
    fun testSerializePassthrough() = runBlockingUnit {
        val channel = info.createChannelFor(basicImpl)
        val serializedChannel = channel.serialized(TestRootInterface)
        val stub = TestRootInterface.wrap(serializedChannel.deserialized())
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").rpc("Hello" to "world")
        )
    }

    @Test
    fun testSerializePassthrough_twoCalls() = runBlockingUnit {
        val channel = info.createChannelFor(basicImpl)
        val serializedChannel = channel.serialized(TestRootInterface)
        val stub = TestRootInterface.wrap(serializedChannel.deserialized())
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
        val channel = info.createChannelFor(basicImpl)
        val serializedChannel = channel.serialized(TestRootInterface)
        GlobalScope.launch(Dispatchers.IO) {
            serializedChannel.serve(si, output)
        }
        val stub = TestRootInterface.wrap((input to so).asChannel().deserialized())
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").rpc("Hello" to "world")
        )
    }

    @Test
    fun testPipePassthrough_twoCalls() = runBlockingUnit {
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        val channel = info.createChannelFor(basicImpl)
        val serializedChannel = channel.serialized(TestRootInterface)
        GlobalScope.launch(Dispatchers.IO) {
            try {
                serializedChannel.serve(si, output)
            } catch (t: Throwable) {
                t.printStackTrace()
                fail("Exception")
            }
        }
        val stub = TestRootInterface.wrap((input to so).asChannel().deserialized())
        val ohService = stub.subservice("oh,")
        stub.subservice("!!!").rpc("Hello" to "world")
        assertEquals(
            "oh, Hello world",
            ohService.rpc("Hello" to "world")
        )
    }

    @Test
    fun testHttpPath() = runBlockingUnit {
        val channel = info.createChannelFor(basicImpl)
        val path = "/rpc/"
        val serializedChannel = channel.serialized(TestRootInterface)
        lateinit var server: ApplicationEngine
        GlobalScope.launch(Dispatchers.IO) {
            server = embeddedServer(Netty, 8082) {
                routing {
                    serve(path, serializedChannel)
                }
            }.start()
        }
        val client = HttpClient()
        val stub = TestRootInterface.wrap(
            client.asChannel("http://localhost:8082$path").deserialized()
        )
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").rpc("Hello" to "world")
        )
        GlobalScope.launch(Dispatchers.IO) {
            server.stop(10000, 10000)
        }
    }

    val basicImpl: TestRootInterface
        get() = object : Service(), TestRootInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }

            override suspend fun subservice(prefix: String): TestSubInterface {
                return object : Service(), TestSubInterface {
                    override suspend fun rpc(u: Pair<String, String>): String {
                        return "$prefix ${u.first} ${u.second}"
                    }
                }
            }
        }
}
