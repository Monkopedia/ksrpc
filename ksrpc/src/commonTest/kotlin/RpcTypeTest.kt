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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.serialization.Serializable

@Serializable
data class MyJson(
    val str: String,
    val int: Int,
    val nFloat: Float?
)

@KsService
interface TestTypesInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String

    @KsMethod("/map")
    suspend fun mapRpc(u: Map<String, MyJson>)

    @KsMethod("/return")
    suspend fun returnType(u: Unit): MyJson

    @KsMethod("/inputInt")
    suspend fun inputInt(i: Int)

    @KsMethod("/inputIntList")
    suspend fun inputIntList(i: List<Int>)

    @KsMethod("/outputInt")
    suspend fun outputInt(u: Unit): Int

    @KsMethod("/inputIntNullable")
    suspend fun inputIntNullable(i: Int?)

    @KsMethod("/outputIntNullable")
    suspend fun outputIntNullable(u: Unit): Int?
}

class FakeTestTypes : TestTypesInterface {
    var lastInput: AtomicRef<Any?> = atomic(null)
    var nextReturn: AtomicRef<Any?> = atomic(null)
    var lastCall: AtomicRef<String?> = atomic(null)

    override suspend fun rpc(u: Pair<String, String>): String {
        lastInput.value = u
        lastCall.value = "rpc"
        return nextReturn.value as String
    }

    override suspend fun mapRpc(u: Map<String, MyJson>) {
        lastInput.value = u
        lastCall.value = "mapRpc"
        return nextReturn.value as Unit
    }

    override suspend fun returnType(u: Unit): MyJson {
        lastInput.value = u
        lastCall.value = "returnType"
        return nextReturn.value as MyJson
    }

    override suspend fun inputInt(i: Int) {
        lastInput.value = i
        lastCall.value = "inputInt"
        return nextReturn.value as Unit
    }

    override suspend fun inputIntList(i: List<Int>) {
        lastInput.value = i
        lastCall.value = "inputIntList"
        return nextReturn.value as Unit
    }

    override suspend fun outputInt(u: Unit): Int {
        lastInput.value = u
        lastCall.value = "outputInt"
        return nextReturn.value as Int
    }

    override suspend fun inputIntNullable(i: Int?) {
        lastInput.value = i
        lastCall.value = "inputIntNullable"
        return nextReturn.value as Unit
    }

    override suspend fun outputIntNullable(u: Unit): Int? {
        lastInput.value = u
        lastCall.value = "outputIntNullable"
        return nextReturn.value as Int?
    }
}

class RpcTypeTest {
    internal val info = TestTypesInterface
    val service = FakeTestTypes()
    val channel = service

    @Test
    fun testPairStr() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->

            val stub = TestTypesInterface.createStub(client.asChannel())
            service.nextReturn.value = ""
            stub.rpc("Hello" to "world")
            assertEquals("rpc", service.lastCall.value)
            assertEquals("Hello" to "world", service.lastInput.value)
        }
    }

    @Test
    fun testMap() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->

            val stub = TestTypesInterface.createStub(client.asChannel())
            service.nextReturn.value = Unit
            stub.mapRpc(
                mutableMapOf(
                    "First" to MyJson("first", 1, null),
                    "Second" to MyJson("second", 2, 1.2f),
                )
            )
            assertEquals("mapRpc", service.lastCall.value)
            assertEquals(
                mutableMapOf(
                    "First" to MyJson("first", 1, null),
                    "Second" to MyJson("second", 2, 1.2f),
                ),
                service.lastInput.value
            )
        }
    }

    @Test
    fun testInputInt() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->

            val stub = TestTypesInterface.createStub(client.asChannel())
            service.nextReturn.value = Unit
            stub.inputInt(42)
            assertEquals("inputInt", service.lastCall.value)
            assertEquals(42, service.lastInput.value)
        }
    }

    @Test
    fun testInputIntList() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->
            val stub = TestTypesInterface.createStub(client.asChannel())
            service.nextReturn.value = Unit
            service.inputIntList(listOf(42))
            assertEquals("inputIntList", service.lastCall.value)
            assertEquals(listOf(42), service.lastInput.value)
        }
    }

    @Test
    fun testInputIntNullable() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->

            val stub = TestTypesInterface.createStub(client.asChannel())
            service.nextReturn.value = Unit
            service.inputIntNullable(null)
            assertEquals("inputIntNullable", service.lastCall.value)
            assertEquals(null, service.lastInput.value)
        }
    }

    @Test
    fun testOutputInt() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->
            val stub = TestTypesInterface.createStub(client.asChannel())
            service.nextReturn.value = 42
            assertEquals(42, stub.outputInt(Unit))
            assertEquals("outputInt", service.lastCall.value)
            assertEquals(Unit, service.lastInput.value)
        }
    }

    @Test
    fun testOutputIntNullable() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->
            val stub = TestTypesInterface.createStub(client.asChannel())
            service.nextReturn.value = null
            assertEquals(null, stub.outputIntNullable(Unit))
            assertEquals("outputIntNullable", service.lastCall.value)
            assertEquals(Unit, service.lastInput.value)
        }
    }

    @Test
    fun testReturnType() = runBlockingUnit {
        channel.servePipe(TestTypesInterface) { client ->

            val stub = TestTypesInterface.createStub(client.asChannel())
            service.nextReturn.value = MyJson("second", 2, 1.2f)
            assertEquals(MyJson("second", 2, 1.2f), stub.returnType(Unit))
            assertEquals("returnType", service.lastCall.value)
            assertEquals(Unit, service.lastInput.value)
        }
    }
}
