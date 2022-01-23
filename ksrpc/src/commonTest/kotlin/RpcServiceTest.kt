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
import kotlin.test.assertNotNull
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.assertTrue

@KsService
interface TestInterface : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String
}

class RpcServiceTest : RpcFunctionalityTest(
    serializedChannel = {
        val channel: TestInterface = object : TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        }
        channel.serialized()
    },
    verifyOnChannel = { serializedChannel ->
        val stub = serializedChannel.toStub<TestInterface>()
        assertEquals(
            "Hello world",
            stub.rpc("Hello" to "world")
        )
    }
) {
    @Test
    fun testCreateInfo() = runBlockingUnit {
        assertNotNull(rpcObject<TestInterface>().findEndpoint("rpc"))
    }

    @Test
    fun testCreateStub() = runBlockingUnit {
        val stub = object : SerializedService {

            override val serialization: StringFormat
                get() = error("Not implemented")

            override suspend fun call(endpoint: String, input: CallData): CallData {
                error("Not implemented")
            }

            override suspend fun close() {
                error("Not implemented")
            }
        }.toStub<TestInterface>()
        assertNotNull(stub)
    }

    @Test
    fun testCallStub() = runBlockingUnit {
        val stub = object : SerializedService {

            override val serialization: StringFormat
                get() = Json

            override suspend fun call(endpoint: String, input: CallData): CallData {
                val input = Json.decodeFromString(
                    PairSerializer(String.serializer(), String.serializer()),
                    input.readSerialized()
                )
                return CallData.create(
                    Json.encodeToString(String.serializer(), "${input.first} ${input.second}")
                )
            }

            override suspend fun close() {
                error("Not implemented")
            }
        }.toStub<TestInterface>()
        assertEquals("Hello world", stub.rpc("Hello" to "world"))
    }

    @Test
    fun testCreateChannel() = runBlockingUnit {
        val channel = object : TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        }.serialized<TestInterface>()
        assertEquals(
            "Hello world",
            Json.decodeFromString(
                String.serializer(),
                channel.call(
                    "rpc",
                    CallData.create(
                        Json.encodeToString(
                            PairSerializer(String.serializer(), String.serializer()),
                            "Hello" to "world"
                        )
                    )
                ).readSerialized()
            )
        )
    }

    @Test
    fun testStubClosesChannel() = runBlockingUnit {
        var hasCalledClose = false
        val service = object : SerializedService {
            override suspend fun close() {
                hasCalledClose = true
            }

            override suspend fun call(endpoint: String, input: CallData): CallData =
                error("Not implemented")

            override val serialization: StringFormat
                get() = error("Not implemented")
        }
        val stub = service.toStub<TestInterface>()
        stub.close()
        assertTrue(hasCalledClose)
    }

    @Test
    fun testCloseChannelCallsService() = runBlockingUnit {
        var hasCalledClose = false
        val service = object : TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String = error("Not implemented")

            override suspend fun close() {
                hasCalledClose = true
            }
        }
        service.serialized<TestInterface>().close()
        assertTrue(hasCalledClose)
    }
}

class RpcServiceTwoCallsTest : RpcFunctionalityTest(
    serializedChannel = {
        val channel: TestInterface = object : TestInterface {
            override suspend fun rpc(u: Pair<String, String>): String {
                return "${u.first} ${u.second}"
            }
        }
        channel.serialized()
    },
    verifyOnChannel = { serializedChannel ->
        val stub = serializedChannel.toStub<TestInterface>()
        stub.rpc("Hello" to "world")
        assertEquals(
            "Hello world",
            stub.rpc("Hello" to "world")
        )
    }
)
