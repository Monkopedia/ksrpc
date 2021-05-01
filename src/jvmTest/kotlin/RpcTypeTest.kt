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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.Serializable
import org.junit.Test
import kotlin.test.assertEquals

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
    internal val info = TestTypesInterface.info
    val service = mockk<TestTypesInterface>(relaxed = true)
    val channel = info.createChannelFor(service)

    @Test
    fun testPairStr() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->

            val stub = TestTypesInterface.wrap(client.asChannel().deserialized())
            coEvery {
                service.rpc(any())
            }.returns("")
            stub.rpc("Hello" to "world")
            coVerify {
                service.rpc("Hello" to "world")
            }
        }
    }

    @Test
    fun testMap() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->

            val stub = TestTypesInterface.wrap(client.asChannel().deserialized())
            stub.mapRpc(
                mutableMapOf(
                    "First" to MyJson("first", 1, null),
                    "Second" to MyJson("second", 2, 1.2f),
                )
            )
            coVerify {
                service.mapRpc(
                    mutableMapOf(
                        "First" to MyJson("first", 1, null),
                        "Second" to MyJson("second", 2, 1.2f),
                    )
                )
            }
        }
    }

    @Test
    fun testInputInt() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->

            val stub = TestTypesInterface.wrap(client.asChannel().deserialized())
            coEvery {
                service.inputInt(any())
            }.returns(Unit)
            stub.inputInt(42)
            coVerify {
                service.inputInt(42)
            }
        }
    }

    @Test
    fun testInputIntList() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->
            val stub = TestTypesInterface.wrap(client.asChannel().deserialized())
            coEvery {
                service.inputIntList(any())
            }.returns(Unit)
            stub.inputIntList(listOf(42))
            coVerify {
                service.inputIntList(listOf(42))
            }
        }
    }

    @Test
    fun testInputIntNullable() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->

            val stub = TestTypesInterface.wrap(client.asChannel().deserialized())
            coEvery {
                service.inputIntNullable(any())
            }.returns(Unit)
            stub.inputIntNullable(null)
            coVerify {
                service.inputIntNullable(null)
            }
        }
    }

    @Test
    fun testOutputInt() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->
            val stub = TestTypesInterface.wrap(client.asChannel().deserialized())
            coEvery {
                service.outputInt(Unit)
            }.returns(42)
            assertEquals(42, stub.outputInt(Unit))
            coVerify {
                service.outputInt(Unit)
            }
        }
    }

    @Test
    fun testOutputIntNullable() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->
            val stub = TestTypesInterface.wrap(client.asChannel().deserialized())
            coEvery {
                service.outputIntNullable(Unit)
            }.returns(null)
            assertEquals(null, stub.outputIntNullable(Unit))
            coVerify {
                service.outputIntNullable(Unit)
            }
        }
    }

    @Test
    fun testReturnType() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->

        val stub = TestTypesInterface.wrap(client.asChannel().deserialized())
        coEvery {
            service.returnType(Unit)
        }.returns(MyJson("second", 2, 1.2f))
        assertEquals(MyJson("second", 2, 1.2f), stub.returnType(Unit))
        coVerify {
            service.returnType(Unit)
        }
        }
    }
}
