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
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import junit.framework.Assert.assertEquals
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import junit.framework.Assert.fail
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Test
import java.io.ByteArrayInputStream

interface BinaryInterface : RpcService {
    suspend fun rpc(u: Pair<String, String>)  = mapBinary("/rpc", u)
    suspend fun inputRpc(u: ByteReadChannel): String = mapBinaryInput("/input", u)

    class BinaryInterfaceStub(private val channel: RpcServiceChannel) :
        BinaryInterface, RpcService by channel

    companion object : RpcObject<BinaryInterface>(BinaryInterface::class, ::BinaryInterfaceStub)
}

class BinaryTest {

    @Test
    fun testSerializePassthrough() = runBlockingUnit {
        val info = BinaryInterface.info
        val channel = info.createChannelFor(object : Service(), BinaryInterface {
            override suspend fun rpc(u: Pair<String, String>): ByteReadChannel {
                val str = "${u.first} ${u.second}"
                return str.byteInputStream().toByteReadChannel()
            }
        })
        val serializedChannel = channel.serialized(BinaryInterface)
        val stub = BinaryInterface.wrap(serializedChannel.deserialized())
            val response = stub.rpc("Hello" to "world")
        val str = response.toInputStream().readBytes().decodeToString()
        assertEquals("Hello world", str)
    }

    @Test
    fun testPipePassthrough() = runBlockingUnit {
        val info = BinaryInterface.info
        val channel = info.createChannelFor(object : Service(), BinaryInterface {
            override suspend fun rpc(u: Pair<String, String>): ByteReadChannel {
                val str = "${u.first} ${u.second}"
                return str.byteInputStream().toByteReadChannel()
            }
        })
        channel.servePipe(BinaryInterface) { client ->
            val stub = BinaryInterface.wrap(client.asChannel().deserialized())
            val response = stub.rpc("Hello" to "world")
            val str = response.toInputStream().readBytes().decodeToString()
            assertEquals("Hello world", str)
        }
    }

    @Test
    fun testHttpPath() = runBlockingUnit {
        val path = "/rpc/"
        httpTest(serve = {
            val info = BinaryInterface.info
            val channel = info.createChannelFor(object : Service(), BinaryInterface {
                override suspend fun rpc(u: Pair<String, String>): ByteReadChannel {
                    val str = "${u.first} ${u.second}"
                    return str.byteInputStream().toByteReadChannel()
                }
            })
            val serializedChannel = channel.serialized(
                BinaryInterface,
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
        }, test = {
            val client = HttpClient()
            val stub = BinaryInterface.wrap(client.asChannel("http://localhost:8080$path").deserialized())
            val response = stub.rpc("Hello" to "world")
            val str = response.toInputStream().readBytes().decodeToString()
            assertEquals("Hello world", str)
        })
    }
}

class BinaryInputTest {

    @Test
    fun testSerializePassthrough() = runBlockingUnit {
        val info = BinaryInterface.info
        val channel = info.createChannelFor(object : Service(), BinaryInterface {
            override suspend fun inputRpc(u: ByteReadChannel): String {
                return super.inputRpc(u)
            }
        })
        val serializedChannel = channel.serialized(BinaryInterface)
        val stub = BinaryInterface.wrap(serializedChannel.deserialized())
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
        val info = BinaryInterface.info
        val channel = info.createChannelFor(object : Service(), BinaryInterface {
            override suspend fun inputRpc(u: ByteReadChannel): String {
                return super.inputRpc(u)
            }
        })
        channel.servePipe(BinaryInterface) { client ->
            val stub = BinaryInterface.wrap(client.asChannel().deserialized())
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
        httpTest(serve = {
            val info = BinaryInterface.info
            val channel = info.createChannelFor(object : Service(), BinaryInterface {
                override suspend fun inputRpc(u: ByteReadChannel): String {
                    return super.inputRpc(u)
                }
            })
            val serializedChannel = channel.serialized(
                BinaryInterface,
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
        }, test = {
            val client = HttpClient()
            val stub = BinaryInterface.wrap(client.asChannel("http://localhost:8080$path").deserialized())
            try {
                stub.rpc("Hello" to "world")
                fail("Expected crash")
            } catch (t: Throwable) {
                t.printStackTrace()
                t as RpcException
            }
        })
    }
}
