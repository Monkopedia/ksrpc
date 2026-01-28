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
import com.monkopedia.ksrpc.channels.CallData
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@KsService
interface EndpointNotFoundLegacy : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String
}

class RpcEndpointNotFoundTransportTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val channel: EndpointNotFoundLegacy = object : EndpointNotFoundLegacy {
                override suspend fun rpc(u: Pair<String, String>): String = "${u.first} ${u.second}"
            }
            channel.serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { channel ->
            val response = channel.call("missing", CallData.create("ignored"))
            assertTrue(channel.env.serialization.isError(response))
            val exception = channel.env.serialization.decodeErrorCallData(response)
            val message = exception.message ?: ""
            assertTrue(
                message.contains("Unknown endpoint: missing"),
                message
            )
            assertTrue(
                message.contains("com.monkopedia.ksrpc.EndpointNotFoundLegacy"),
                message
            )
        }
    )

class RpcEndpointNotFoundLocalTest {
    @Test
    fun testFindEndpointThrows() {
        assertFailsWith<RpcEndpointException> {
            rpcObject<EndpointNotFoundLegacy>().findEndpoint("missing")
        }
    }
}

@KsService
interface EndpointNotFoundBase : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String
}

@KsService
interface EndpointNotFoundExtended : RpcService {
    @KsMethod("/rpc")
    suspend fun rpc(u: Pair<String, String>): String

    @KsMethod("/extra")
    suspend fun extra(input: Unit): String
}

class RpcEndpointNotFoundTransportVariationTest :
    RpcFunctionalityTest(
        serializedChannel = {
            val channel: EndpointNotFoundBase = object : EndpointNotFoundBase {
                override suspend fun rpc(u: Pair<String, String>): String = "${u.first} ${u.second}"
            }
            channel.serialized(ksrpcEnvironment { })
        },
        verifyOnChannel = { channel ->
            val stub = channel.toStub<EndpointNotFoundExtended, String>()
            val exception = assertFailsWith<RpcEndpointException> {
                stub.extra(Unit)
            }
            val message = exception.message ?: ""
            assertTrue(message.contains("Unknown endpoint: extra"), message)
            assertTrue(message.contains("EndpointNotFoundBase"), message)
        }
    )
