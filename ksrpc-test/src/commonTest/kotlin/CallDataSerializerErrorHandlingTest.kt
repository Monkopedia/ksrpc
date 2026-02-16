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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CallDataSerializerErrorHandlingTest {

    @Test
    fun createAndDecodeStandardErrorPayload() {
        val env = ksrpcEnvironment { }
        val callData = env.serialization.createErrorCallData(
            RpcFailure.serializer(),
            RpcFailure("remote stack")
        )

        assertTrue(env.serialization.isError(callData))

        val throwable = env.serialization.decodeErrorCallData(callData)
        assertIs<RpcException>(throwable)
        assertEquals("remote stack", throwable.message)
    }

    @Test
    fun createAndDecodeEndpointNotFoundPayload() {
        val env = ksrpcEnvironment { }
        val callData = env.serialization.createEndpointNotFoundCallData(
            RpcFailure.serializer(),
            RpcFailure("missing endpoint")
        )

        assertTrue(env.serialization.isError(callData))

        val throwable = env.serialization.decodeErrorCallData(callData)
        assertIs<RpcEndpointException>(throwable)
        assertEquals("missing endpoint", throwable.message)
    }

    @Test
    fun decodeErrorCallDataReturnsUnknownPayloadExceptionForNonErrorData() {
        val env = ksrpcEnvironment { }
        val plainData = CallData.create("plain data")

        assertFalse(env.serialization.isError(plainData))

        val throwable = env.serialization.decodeErrorCallData(plainData)
        assertIs<RpcException>(throwable)
        assertEquals("Unknown error payload: plain data", throwable.message)
    }
}
