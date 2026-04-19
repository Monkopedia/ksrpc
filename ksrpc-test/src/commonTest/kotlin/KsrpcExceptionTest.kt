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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KsrpcExceptionTest {

    @Test
    fun ksrpcExceptionCarriesCodeAndMessage() {
        val exception = KsrpcException(code = -32603, message = "Internal error")

        assertEquals(-32603, exception.code)
        assertEquals("Internal error", exception.message)
        assertNull(exception.data)
        assertNull(exception.cause)
    }

    @Test
    fun ksrpcExceptionCarriesData() {
        val exception = KsrpcException(
            code = -32603,
            message = "Internal error",
            data = """{"detail":"something went wrong"}"""
        )

        assertEquals(-32603, exception.code)
        assertEquals("Internal error", exception.message)
        assertEquals("""{"detail":"something went wrong"}""", exception.data)
    }

    @Test
    fun ksrpcExceptionCarriesCause() {
        val cause = RuntimeException("root cause")
        val exception = KsrpcException(
            code = -32603,
            message = "Internal error",
            cause = cause
        )

        assertEquals(cause, exception.cause)
    }

    @Test
    fun ksrpcExceptionIsRuntimeException() {
        val exception = KsrpcException(code = -1, message = "test")

        assertIs<RuntimeException>(exception)
    }

    @Test
    fun rpcExceptionExtendsKsrpcException() {
        val exception = RpcException("boom")

        assertIs<KsrpcException>(exception)
        assertEquals(-1, exception.code)
        assertEquals("boom", exception.message)
    }

    @Test
    fun rpcEndpointExceptionExtendsKsrpcException() {
        val exception = RpcEndpointException("not found")

        assertIs<KsrpcException>(exception)
        assertEquals(-32601, exception.code)
    }

    @Test
    fun rpcFailureToExceptionReturnsKsrpcException() {
        val failure = RpcFailure("stack trace here")
        val exception = failure.toException()

        assertIs<KsrpcException>(exception)
        assertEquals("stack trace here", exception.message)
    }

    @Test
    fun ksrpcExceptionToStringContainsFields() {
        val exception = KsrpcException(
            code = -32603,
            message = "err",
            data = "payload"
        )
        val str = exception.toString()

        assertTrue(str.contains("-32603"))
        assertTrue(str.contains("err"))
        assertTrue(str.contains("payload"))
    }
}
