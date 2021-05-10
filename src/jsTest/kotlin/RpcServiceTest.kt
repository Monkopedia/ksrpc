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

import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer

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
                input: I,
            ): O {
                error("Not implemented")
            }

            override suspend fun <I> callBinary(
                endpoint: String,
                inputSer: KSerializer<I>,
                input: I,
            ): ByteReadChannel {
                error("Not implemented")
            }

            override suspend fun <O> callBinaryInput(
                endpoint: String,
                outputSer: KSerializer<O>,
                input: ByteReadChannel,
            ): O {
                error("Not implemented")
            }

            override suspend fun <I, O : RpcService> callService(
                endpoint: String,
                service: RpcObject<O>,
                inputSer: KSerializer<I>,
                input: I,
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
                input: I,
            ): O {
                if (input is Pair<*, *>) {
                    return "${input.first} ${input.second}" as O
                }
                error("Not implemented")
            }

            override suspend fun <I> callBinary(
                endpoint: String,
                inputSer: KSerializer<I>,
                input: I,
            ): ByteReadChannel {
                error("Not implemented")
            }

            override suspend fun <O> callBinaryInput(
                endpoint: String,
                outputSer: KSerializer<O>,
                input: ByteReadChannel,
            ): O {
                error("Not implemented")
            }

            override suspend fun <I, O : RpcService> callService(
                endpoint: String,
                service: RpcObject<O>,
                inputSer: KSerializer<I>,
                input: I,
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
}

internal fun runBlockingUnit(function: suspend () -> Unit) = GlobalScope.promise {
    try {
        function()
    } catch (t: Throwable) {
        t.printStackTrace()
        fail("Caught exception $t")
    }
}
