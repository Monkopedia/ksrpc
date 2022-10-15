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

import com.monkopedia.ksrpc.annotation.KsMethod
import com.monkopedia.ksrpc.annotation.KsService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CompletableDeferred

@KsService
interface TestSubInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String
}

@KsService
interface TestRootInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String

    @KsMethod("/service")
    suspend fun subservice(prefix: String): TestSubInterface
}

private val basicImpl: TestRootInterface
    get() = object : TestRootInterface {
        override suspend fun rpc(u: Pair<String, String>): String {
            return "${u.first} ${u.second}"
        }

        override suspend fun subservice(prefix: String): TestSubInterface {
            return object : TestSubInterface {
                override suspend fun rpc(u: Pair<String, String>): String {
                    return "$prefix ${u.first} ${u.second}"
                }
            }
        }
    }

class RpcSubserviceTest : RpcFunctionalityTest(
    serializedChannel = {
        val channel: TestRootInterface = basicImpl
        channel.serialized(ksrpcEnvironment { })
    },
    verifyOnChannel = { serializedChannel ->
        val stub = serializedChannel.toStub<TestRootInterface>()
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").rpc("Hello" to "world")
        )
    }
) {
    @Test
    fun testCreateInfo() = runBlockingUnit {
        assertNotNull(rpcObject<TestRootInterface>().findEndpoint("rpc"))
    }
}

class RpcSubserviceTwoCallsTest : RpcFunctionalityTest(
    serializedChannel = {
        val channel: TestRootInterface = basicImpl
        channel.serialized(ksrpcEnvironment { })
    },
    verifyOnChannel = { serializedChannel ->
        val stub = serializedChannel.toStub<TestRootInterface>()
        stub.subservice("oh,").rpc("Hello" to "world")
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").rpc("Hello" to "world")
        )
    }
)

private var closeCompletion: CompletableDeferred<Unit>? = null

class RpcSubserviceCloseTest : RpcFunctionalityTest(
    serializedChannel = {
        val channel: TestRootInterface = object : TestRootInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }

            override suspend fun subservice(prefix: String): TestSubInterface {
                return object : TestSubInterface {
                    override suspend fun rpc(u: Pair<String, String>): String {
                        return "$prefix ${u.first} ${u.second}"
                    }

                    override suspend fun close() {
                        closeCompletion?.complete(Unit)
                    }
                }
            }
        }
        channel.serialized(ksrpcEnvironment { })
    },
    verifyOnChannel = { serializedChannel ->
        val stub = serializedChannel.toStub<TestRootInterface>()
        closeCompletion = CompletableDeferred()
        assertEquals(
            "oh, Hello world",
            stub.subservice("oh,").run {
                rpc("Hello" to "world").also {
                    close()
                }
            }
        )
        closeCompletion?.await()
    }
) {
    override fun createEnv(): KsrpcEnvironment {
        return ksrpcEnvironment {
            errorListener = ErrorListener {
                closeCompletion?.completeExceptionally(it)
            }
        }
    }
}
