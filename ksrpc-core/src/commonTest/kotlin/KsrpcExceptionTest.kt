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
import kotlin.test.assertSame

class KsrpcExceptionTest {

    @Test
    fun ksrpcException_storesAllFields() {
        val payload = "details"
        val cause = IllegalStateException("root")
        val exception = KsrpcException(
            code = 42,
            message = "something broke",
            data = payload,
            cause = cause
        )
        assertEquals(42, exception.code)
        assertEquals("something broke", exception.message)
        assertSame(payload, exception.data)
        assertSame(cause, exception.cause)
    }

    @Test
    fun ksrpcException_defaultsDataAndCauseToNull() {
        val exception = KsrpcException(code = 7, message = "no payload")
        assertEquals(7, exception.code)
        assertEquals("no payload", exception.message)
        assertNull(exception.data)
        assertNull(exception.cause)
    }

    @Test
    fun ksrpcException_isRuntimeException() {
        val exception: Throwable = KsrpcException(code = 1, message = "x")
        assertIs<RuntimeException>(exception)
    }
}
