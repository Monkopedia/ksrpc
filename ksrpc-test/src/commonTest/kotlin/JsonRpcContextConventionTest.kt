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

import com.monkopedia.ksrpc.jsonrpc.JsonRpcContextConvention
import com.monkopedia.ksrpc.jsonrpc.asJsonRpcConnection
import io.ktor.utils.io.close
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tests for #82 — JSON-RPC context convention variants and collision validation.
 */
class JsonRpcContextConventionTest {

    private suspend fun roundTrip(
        convention: JsonRpcContextConvention,
        verify: suspend (ContextService) -> Unit
    ) {
        val (output, input) = createPipe()
        val (so, si) = createPipe()
        GlobalScope.launch(Dispatchers.Default) {
            val connection = (input to so).asJsonRpcConnection(
                ksrpcEnvironment {
                    errorListener = ErrorListener { it.printStackTrace() }
                },
                includeContentHeaders = false,
                contextConvention = convention
            )
            connection.registerDefault(GreetHandler().serialized(ksrpcEnvironment { }))
        }
        try {
            val channel = (si to output).asJsonRpcConnection(
                ksrpcEnvironment {
                    errorListener = ErrorListener { it.printStackTrace() }
                },
                includeContentHeaders = false,
                contextConvention = convention
            )
            val stub = channel.defaultChannel().toStub<ContextService, String>()
            verify(stub)
        } finally {
            try {
                input.cancel(null)
            } catch (_: Throwable) {}
            try {
                si.cancel(null)
            } catch (_: Throwable) {}
            output.close(null)
            so.close(null)
        }
    }

    @Test
    fun rootSiblingsPropagatesContext() = runBlockingUnit {
        roundTrip(JsonRpcContextConvention.RootSiblings) { stub ->
            withContext(AuthToken("jsonrpc-token") + TraceId("jsonrpc-trace")) {
                val result = stub.greet("hello")
                assertEquals("auth=jsonrpc-token,trace=jsonrpc-trace", result)
            }
        }
    }

    @Test
    fun rootFieldPropagatesContext() = runBlockingUnit {
        roundTrip(JsonRpcContextConvention.RootField("ctx")) { stub ->
            withContext(AuthToken("field-token") + TraceId("field-trace")) {
                val result = stub.greet("hello")
                assertEquals("auth=field-token,trace=field-trace", result)
            }
        }
    }

    @Test
    fun inParamsPropagatesContext() = runBlockingUnit {
        roundTrip(JsonRpcContextConvention.InParams("_ctx")) { stub ->
            withContext(AuthToken("params-token") + TraceId("params-trace")) {
                val result = stub.greet("hello")
                assertEquals("auth=params-token,trace=params-trace", result)
            }
        }
    }

    @Test
    fun noneConventionDoesNotPropagateContext() = runBlockingUnit {
        roundTrip(JsonRpcContextConvention.None) { stub ->
            withContext(AuthToken("none-token") + TraceId("none-trace")) {
                val result = stub.greet("hello")
                assertEquals("no-auth", result)
            }
        }
    }

    @Test
    fun rootSiblingsRejectsReservedWireKeyMethod() {
        assertFailsWith<IllegalArgumentException> {
            JsonRpcContextConvention.validate(
                JsonRpcContextConvention.RootSiblings,
                listOf("method")
            )
        }
    }

    @Test
    fun rootSiblingsRejectsReservedWireKeyParams() {
        assertFailsWith<IllegalArgumentException> {
            JsonRpcContextConvention.validate(
                JsonRpcContextConvention.RootSiblings,
                listOf("params")
            )
        }
    }

    @Test
    fun rootSiblingsRejectsReservedWireKeyJsonrpc() {
        assertFailsWith<IllegalArgumentException> {
            JsonRpcContextConvention.validate(
                JsonRpcContextConvention.RootSiblings,
                listOf("x-ok", "jsonrpc")
            )
        }
    }

    @Test
    fun rootSiblingsAcceptsNonReservedKeys() {
        // Should not throw
        JsonRpcContextConvention.validate(
            JsonRpcContextConvention.RootSiblings,
            listOf("x-auth-token", "x-trace-id", "x-custom")
        )
    }

    @Test
    fun rootFieldDoesNotValidateCollisions() {
        // Non-RootSiblings conventions should not throw on reserved keys
        JsonRpcContextConvention.validate(
            JsonRpcContextConvention.RootField("ctx"),
            listOf("method", "params")
        )
    }
}
