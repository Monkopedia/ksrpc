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
@file:OptIn(KsrpcInternal::class)

package com.monkopedia.ksrpc

import com.monkopedia.ksrpc.annotation.KsrpcInternal
import com.monkopedia.ksrpc.channels.registerDefault
import com.monkopedia.ksrpc.jsonrpc.asJsonRpcConnection
import io.ktor.utils.io.ByteChannel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonRpcTierCheckTest {

    @Test
    fun registeringHostServiceOnJsonRpcThrows() = runBlockingUnit {
        val inputChannel = ByteChannel()
        val outputChannel = ByteChannel()
        val connection = (inputChannel to outputChannel).asJsonRpcConnection(
            ksrpcEnvironment { }
        )

        val hostService = object : TestRootInterface {
            override suspend fun rpc(u: Pair<String, String>): String = "${u.first} ${u.second}"
            override suspend fun subservice(prefix: String): TestSubInterface =
                error("unreachable")
        }

        val exception = assertFailsWith<IllegalArgumentException> {
            connection.registerDefault(hostService)
        }
        assertTrue(
            exception.message!!.contains("sub-service"),
            "Error message should mention sub-service, got: ${exception.message}"
        )
        assertTrue(
            exception.message!!.contains("JSON-RPC"),
            "Error message should mention JSON-RPC, got: ${exception.message}"
        )
    }

    @Test
    fun registeringSimpleServiceOnJsonRpcSucceeds() = runBlockingUnit {
        val inputChannel = ByteChannel()
        val outputChannel = ByteChannel()
        val connection = (inputChannel to outputChannel).asJsonRpcConnection(
            ksrpcEnvironment { }
        )

        val simpleService = object : TestSubInterface {
            override suspend fun rpc(u: Pair<String, String>): String = "${u.first} ${u.second}"
        }

        // Should not throw — simple services are fine on JSON-RPC
        connection.registerDefault(simpleService)
    }
}
