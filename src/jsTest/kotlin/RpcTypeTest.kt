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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class MyJson(
    val str: String,
    val int: Int,
    val nFloat: Float?
)

interface TestTypesInterface : RpcService {
    suspend fun rpc(u: Pair<String, String>): String = map("/rpc", u)
    suspend fun mapRpc(u: Map<String, MyJson>): Unit = map("/map", u)
    suspend fun returnType(u: Unit): MyJson = map("/return", u)
    suspend fun inputInt(i: Int): Unit = map("/inputInt", i)
    suspend fun inputIntList(i: List<Int>): Unit = map("/inputIntList", i)
    suspend fun outputInt(u: Unit): Int = map("/outputInt", u)
    suspend fun inputIntNullable(i: Int?): Unit = map("/inputIntNullable", i)
    suspend fun outputIntNullable(u: Unit): Int? = map("/outputIntNullable", u)

    class TestTypesInterfaceStub(private val channel: RpcServiceChannel) :
        TestTypesInterface, RpcService by channel

    companion object :
        RpcObject<TestTypesInterface>(TestTypesInterface::class, ::TestTypesInterfaceStub)
}

class RpcTypeTest {

    var lastEndpoint: String? = null
    var lastInput: Any? = null
    var nextReturn: Any? = null

    val channel = object : RpcChannel {
        override suspend fun <I, O> call(
            endpoint: String,
            inputSer: KSerializer<I>,
            outputSer: KSerializer<O>,
            input: I
        ): O {
            try {
                println("\n\n TEST Call $endpoint\n\n")
                lastEndpoint = endpoint
                lastInput = input
                return nextReturn as O
            } catch (t: Throwable) {
                t.printStackTrace()
                throw t
            }
        }

        override suspend fun <I> callBinary(endpoint: String, inputSer: KSerializer<I>, input: I): ByteReadChannel {
            try {
                println("\n\n TEST Call $endpoint\n\n")
                lastEndpoint = endpoint
                lastInput = input
                return nextReturn as ByteReadChannel
            } catch (t: Throwable) {
                t.printStackTrace()
                throw t
            }
        }

        override suspend fun <O> callBinaryInput(
            endpoint: String,
            outputSer: KSerializer<O>,
            input: ByteReadChannel
        ): O {
            try {
                println("\n\n TEST Call $endpoint\n\n")
                lastEndpoint = endpoint
                lastInput = input
                return nextReturn as O
            } catch (t: Throwable) {
                t.printStackTrace()
                throw t
            }
        }

        override suspend fun <I, O : RpcService> callService(
            endpoint: String,
            service: RpcObject<O>,
            inputSer: KSerializer<I>,
            input: I
        ): O {
            error("Not implemented")
        }

        override suspend fun close() {
            error("Not implemented")
        }
    }
    val serialized = channel.serialized(TestTypesInterface)

    @Test
    fun testPairStr() = runBlockingUnit {
        val stub = TestTypesInterface.wrap(serialized.deserialized())
        nextReturn = ""
        stub.rpc("Hello" to "world")
        assertEquals("rpc", lastEndpoint)
        assertEquals("Hello" to "world", lastInput)
    }

    @Test
    fun testMap() = runBlockingUnit {
        val stub = TestTypesInterface.wrap(serialized.deserialized())
        stub.mapRpc(
            mutableMapOf(
                "First" to MyJson("first", 1, null),
                "Second" to MyJson("second", 2, 1.2f),
            )
        )
        assertEquals(
            mutableMapOf(
                "First" to MyJson("first", 1, null),
                "Second" to MyJson("second", 2, 1.2f),
            ),
            lastInput
        )
        assertEquals("map", lastEndpoint)
    }

    @Test
    fun testInputInt() = runBlockingUnit {
        val stub = TestTypesInterface.wrap(serialized.deserialized())
        nextReturn = Unit
        stub.inputInt(42)
        assertEquals(42, lastInput)
        assertEquals("inputInt", lastEndpoint)
    }

    @Test
    fun testInputIntList() = runBlockingUnit {
        val stub = TestTypesInterface.wrap(serialized.deserialized())
        nextReturn = Unit
        stub.inputIntList(listOf(42))
        assertEquals(listOf(42), lastInput)
        assertEquals("inputIntList", lastEndpoint)
    }

    @Test
    fun testInputIntNullable() = runBlockingUnit {
        val stub = TestTypesInterface.wrap(serialized.deserialized())
        nextReturn = Unit

        stub.inputIntNullable(null)

        assertEquals(null, lastInput)
        assertEquals("inputIntNullable", lastEndpoint)
    }

    @Test
    fun testOutputInt() = runBlockingUnit {
        val stub = TestTypesInterface.wrap(serialized.deserialized())
        nextReturn = 42
        assertEquals(42, stub.outputInt(Unit))
        assertEquals("outputInt", lastEndpoint)
    }

    @Test
    fun testOutputIntNullable() = runBlockingUnit {
        val stub = TestTypesInterface.wrap(serialized.deserialized())
        nextReturn = null
        assertEquals(null, stub.outputIntNullable(Unit))
        assertEquals("outputIntNullable", lastEndpoint)
    }

    @Test
    fun testReturnType() = runBlockingUnit {
        val stub = TestTypesInterface.wrap(serialized.deserialized())
        nextReturn = MyJson("second", 2, 1.2f)
        assertEquals(MyJson("second", 2, 1.2f), stub.returnType(Unit))
        assertEquals("return", lastEndpoint)
    }
}
