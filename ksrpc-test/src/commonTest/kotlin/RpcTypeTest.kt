/*
 * Copyright (C) 2026 Jason Monk <monkopedia@gmail.com>
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

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import com.monkopedia.ksrpc.channels.SerializedService
import kotlin.test.assertEquals
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred

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
    var lastInput: Any? by atomic(null)
    var nextReturn: Any? by atomic(null)
    var lastCall: String? by atomic(null)
    var callComplete: CompletableDeferred<Unit>? by atomic(null)

    override suspend fun rpc(u: Pair<String, String>): String {
        lastInput = u
        lastCall = "rpc"
        callComplete?.complete(Unit)
        return nextReturn as String
    }

    override suspend fun mapRpc(u: Map<String, MyJson>) {
        lastInput = u
        lastCall = "mapRpc"
        callComplete?.complete(Unit)
        return nextReturn as Unit
    }

    override suspend fun returnType(u: Unit): MyJson {
        lastInput = u
        lastCall = "returnType"
        callComplete?.complete(Unit)
        return nextReturn as MyJson
    }

    override suspend fun inputInt(i: Int) {
        lastInput = i
        lastCall = "inputInt"
        callComplete?.complete(Unit)
        return nextReturn as Unit
    }

    override suspend fun inputIntList(i: List<Int>) {
        lastInput = i
        lastCall = "inputIntList"
        callComplete?.complete(Unit)
        return nextReturn as Unit
    }

    override suspend fun outputInt(u: Unit): Int {
        lastInput = u
        lastCall = "outputInt"
        callComplete?.complete(Unit)
        return nextReturn as Int
    }

    override suspend fun inputIntNullable(i: Int?) {
        lastInput = i
        lastCall = "inputIntNullable"
        callComplete?.complete(Unit)
        return nextReturn as Unit
    }

    override suspend fun outputIntNullable(u: Unit): Int? {
        lastInput = u
        lastCall = "outputIntNullable"
        callComplete?.complete(Unit)
        return nextReturn as Int?
    }
}

object RpcTypeTest {

    abstract class RpcTypeFunctionalityTest(
        verifyOnChannel: suspend (SerializedService<String>, FakeTestTypes) -> Unit,
        private val service: FakeTestTypes = FakeTestTypes()
    ) : RpcFunctionalityTest(
        serializedChannel = {
            service.serialized<TestTypesInterface, String>(ksrpcEnvironment { })
        },
        verifyOnChannel = { channel ->
            verifyOnChannel(channel, service)
        }
    )

    class PairStrTest :
        RpcTypeFunctionalityTest(
            verifyOnChannel = { channel, service ->
                val stub = channel.toStub<TestTypesInterface, String>()
                service.nextReturn = ""
                stub.rpc("Hello" to "world")
                assertEquals("rpc", service.lastCall)
                assertEquals("Hello" to "world", service.lastInput)
            }
        )

    class MapTest :
        RpcTypeFunctionalityTest(
            verifyOnChannel = { channel, service ->
                val stub = channel.toStub<TestTypesInterface, String>()
                service.nextReturn = Unit
                stub.mapRpc(
                    mutableMapOf(
                        "First" to MyJson("first", 1, null),
                        "Second" to MyJson("second", 2, 1.2f)
                    )
                )
                assertEquals("mapRpc", service.lastCall)
                assertEquals(
                    mutableMapOf(
                        "First" to MyJson("first", 1, null),
                        "Second" to MyJson("second", 2, 1.2f)
                    ),
                    service.lastInput
                )
            }
        )

    class InputIntTest :
        RpcTypeFunctionalityTest(
            verifyOnChannel = { channel, service ->
                val stub = channel.toStub<TestTypesInterface, String>()
                service.nextReturn = Unit
                stub.inputInt(42)
                assertEquals("inputInt", service.lastCall)
                assertEquals(42, service.lastInput)
            }
        )

    class InputIntListTest :
        RpcTypeFunctionalityTest(
            verifyOnChannel = { channel, service ->
                val stub = channel.toStub<TestTypesInterface, String>()
                service.nextReturn = Unit
                stub.inputIntList(listOf(42))
                assertEquals("inputIntList", service.lastCall)
                assertEquals(listOf(42), service.lastInput)
            }
        )

    class InputIntNullableTest :
        RpcTypeFunctionalityTest(
            verifyOnChannel = { channel, service ->
                val stub = channel.toStub<TestTypesInterface, String>()
                service.nextReturn = Unit
                stub.inputIntNullable(null)
                assertEquals("inputIntNullable", service.lastCall)
                assertEquals(null, service.lastInput)
            }
        )

    class OutputIntTest :
        RpcTypeFunctionalityTest(
            verifyOnChannel = { channel, service ->
                val stub = channel.toStub<TestTypesInterface, String>()
                service.nextReturn = 42
                assertEquals(42, stub.outputInt(Unit))
                assertEquals("outputInt", service.lastCall)
                assertEquals(Unit, service.lastInput)
            }
        )

    class OutputIntNullableTest :
        RpcTypeFunctionalityTest(
            verifyOnChannel = { channel, service ->
                val stub = channel.toStub<TestTypesInterface, String>()
                service.nextReturn = null
                assertEquals(null, stub.outputIntNullable(Unit))
                assertEquals("outputIntNullable", service.lastCall)
                assertEquals(Unit, service.lastInput)
            }
        )

    class ReturnTypeTest :
        RpcTypeFunctionalityTest(
            verifyOnChannel = { channel, service ->
                val stub = channel.toStub<TestTypesInterface, String>()
                service.nextReturn = MyJson("second", 2, 1.2f)
                assertEquals(MyJson("second", 2, 1.2f), stub.returnType(Unit))
                assertEquals("returnType", service.lastCall)
                assertEquals(Unit, service.lastInput)
            }
        )
}
