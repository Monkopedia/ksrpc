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

import com.monkopedia.ksrpc.channels.CallData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The [CallData] hierarchy gained a third variant — [CallData.Error] — and
 * `CallDataSerializer` collapsed to just `createCallData`/`decodeCallData`.
 * The variant *is* the discriminator now (no string prefixes, no
 * `isError(callData)` round-trip through the serializer).
 */
class CallDataSerializerErrorHandlingTest {

    @Test
    fun errorVariantHasErrorFieldsAndPredicate() {
        val error = CallData.Error<String>(
            errorCode = KsrpcException.INTERNAL_ERROR_CODE,
            errorMessage = "boom",
            errorData = null
        )

        assertTrue(error.isError)
        assertEquals(KsrpcException.INTERNAL_ERROR_CODE, error.errorCode)
        assertEquals("boom", error.errorMessage)
        assertNull(error.errorData)
        assertFalse(error.isBinary)
    }

    @Test
    fun serializedVariantReportsNotErrorAndExposesNullErrorFields() {
        val data = CallData.create("hello")
        assertFalse(data.isError)
        assertNull(data.errorCode)
        assertNull(data.errorMessage)
    }

    @Test
    fun errorReadSerializedReturnsErrorDataWhenPresent() {
        val error = CallData.Error(
            errorCode = 100,
            errorMessage = "bad input",
            errorData = "{\"retry\":true}"
        )
        // Error.readSerialized returns the wire-encoded errorData — same shape as
        // a Serialized<T>, so transports can reuse the same machinery for both.
        assertEquals("{\"retry\":true}", error.readSerialized())
    }
}
